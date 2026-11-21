import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { errorBody } from "../../src/errors";
import { gameErrors } from "../../src/games/games.service";
import { EquipmentType, Format, GameSource } from "../../src/generated/prisma/enums";
import { userErrors } from "../../src/users/users.service";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// The equipment players use in a game, against a real database: default items are used when a player creates or
// joins a game, PUT /games/equipment changes the selection, and completing the game counts each item's use. Every
// test gets two new users with their own items and games, so the tests don't depend on each other
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

interface ItemBody {
  id: number;
  userId: string;
  gamesUsed: number;
  hoursUsed: number;
}

interface GameBody {
  id: string;
  equipment: ItemBody[];
}

const app = createApp();
let alice: TestUser;
let bob: TestUser;
let courtId: number;

const post = (path: string, user: TestUser, body: object) =>
  request(app).post(path).set("Authorization", user.auth).send(body);
const putEquipment = (user: TestUser, gameId: string, equipmentIds: number[]) =>
  request(app).put("/games/equipment").set("Authorization", user.auth).send({ gameId, equipmentIds });

// Promise chains rather than async helpers: a failed request rejects the promise, which fails the test

/** Adds a racket to the user's equipment; returns its ID */
function addRacket(user: TestUser, isDefault: boolean): Promise<number> {
  return post("/user/equipment", user, { type: EquipmentType.RACKET, name: "Pro Staff", isDefault }).then(
    (response) => {
      expect(response.status).toBe(201);
      return (response.body as { equipment: ItemBody }).equipment.id;
    },
  );
}

/** Alice schedules a singles game tomorrow and Bob joins team 2; returns the game's ID */
function scheduleAndJoin(): Promise<string> {
  const startsAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
  return post("/games", alice, { format: Format.SINGLES, startsAt, courtId, gameSource: GameSource.SCHEDULED })
    .then((response) => {
      expect(response.status).toBe(201);
      const gameId = (response.body as { game: GameBody }).game.id;
      return post("/games/join", bob, { gameId, team: 2 }).then((join) => {
        expect(join.status).toBe(204);
        return gameId;
      });
    });
}

/** The IDs of the items used in the game, sorted */
function equipmentOf(gameId: string): Promise<number[]> {
  return request(app)
    .get("/games")
    .query({ gameId })
    .set("Authorization", alice.auth)
    .then((response) => {
      expect(response.status).toBe(200);
      return (response.body as { game: GameBody }).game.equipment.map(({ id }) => id).sort((a, b) => a - b);
    });
}

beforeAll(async () => {
  courtId = (await prisma.court.findFirstOrThrow({ select: { id: true } })).id;
  // The forecast is not under test; without a key the games skip it instead of calling OpenWeather
  delete process.env.OPENWEATHER_API_KEY;
});

beforeEach(async () => {
  [alice, bob] = (await createTestUsers("equipment", 2)) as [TestUser, TestUser];
});

afterEach(async () => {
  await deleteTestUsers([alice, bob]);
});

afterAll(async () => {
  await prisma.$disconnect();
});

describe("Unmocked: PUT /games/equipment", () => {
  // Interface POST /games, POST /games/join, PUT /games/equipment, GET /games
  // Input: Alice has a default racket and creates a game; Bob, with a racket that isn't his default, joins and picks it
  // Expected status code: 204 for the selection
  // Expected behavior: Alice's default racket is used without her picking it; Bob's pick is added next to it
  // Expected output: both rackets in the game's equipment
  test("Uses default items, and adds the items a player picks", async () => {
    const aliceRacket = await addRacket(alice, true);
    const bobRacket = await addRacket(bob, false);
    const gameId = await scheduleAndJoin();
    expect(await equipmentOf(gameId)).toEqual([aliceRacket]);

    expect((await putEquipment(bob, gameId, [bobRacket])).status).toBe(204);

    expect(await equipmentOf(gameId)).toEqual([aliceRacket, bobRacket].sort((a, b) => a - b));
  });

  // Interface PUT /games/equipment
  // Input: Bob picks Alice's racket, then a stranger (not in the game) picks their own
  // Expected status code: 404, then 403
  // Expected behavior: nothing changes in the game's equipment
  // Expected output: EQUIPMENT_NOT_FOUND, then NOT_IN_GAME
  test("Rejects another player's item, and a user who isn't in the game", async () => {
    const aliceRacket = await addRacket(alice, true);
    const gameId = await scheduleAndJoin();

    const borrowed = await putEquipment(bob, gameId, [aliceRacket]);
    expect(borrowed.status).toBe(404);
    expect(borrowed.body).toEqual(errorBody(userErrors.EquipmentNotFound));

    const [stranger] = await createTestUsers("equipment-stranger", 1);
    if (stranger === undefined) throw new Error("No stranger was created");
    try {
      const strangerRacket = await addRacket(stranger, false);
      const outsider = await putEquipment(stranger, gameId, [strangerRacket]);
      expect(outsider.status).toBe(403);
      expect(outsider.body).toEqual(errorBody(gameErrors.NotInGame));
    } finally {
      await deleteTestUsers([stranger]);
    }
    expect(await equipmentOf(gameId)).toEqual([aliceRacket]);
  });

  // Interface POST /games/start, POST /games/score, POST /games/score/confirm, GET /user/equipment,
  // PUT /games/equipment
  // Input: Alice plays a game with her default racket to the end, then tries to change her equipment
  // Expected status code: 409 for the change after the game
  // Expected behavior: completing the game adds one game and one hour to the racket, once
  // Expected output: gamesUsed 1 and hoursUsed 1, then GAME_CLOSED
  test("Counts each item's use when the game completes, then locks the selection", async () => {
    const aliceRacket = await addRacket(alice, true);
    const gameId = await scheduleAndJoin();
    const rounds = [[6, 3], [6, 4]];

    expect((await post("/games/start", alice, { gameId })).status).toBe(200);
    expect((await post("/games/score", alice, { gameId, rounds })).status).toBe(200);
    expect((await post("/games/score/confirm", bob, { gameId, rounds })).status).toBe(200);

    const items = await request(app).get("/user/equipment").set("Authorization", alice.auth);
    const racket = (items.body as { equipment: ItemBody[] }).equipment.find(({ id }) => id === aliceRacket);
    expect(racket).toMatchObject({ gamesUsed: 1, hoursUsed: 1 });

    const late = await putEquipment(alice, gameId, []);
    expect(late.status).toBe(409);
    expect(late.body).toEqual(errorBody(gameErrors.Closed));
  });
});
