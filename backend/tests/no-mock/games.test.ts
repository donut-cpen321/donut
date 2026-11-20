import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { commonErrors, errorBody } from "../../src/errors";
import { gameErrors } from "../../src/games/games.service";
import { Format, FriendshipStatus, GameSource, GameStatus } from "../../src/generated/prisma/enums";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// The game routes that the flow tests (tournaments.test.ts, playerStats.test.ts, matchmaking.test.ts) don't reach,
// against a real database. Every test gets two new users and makes its own games, so the tests don't depend on each
// other or on the order they run in.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

interface GameBody {
  id: string;
  status: string;
  startsAt: string;
  courtId: number;
  players: { id: string; team: number; ratingAfter: number | null }[];
  rounds: { number: number; team1Score: number; team2Score: number }[];
}

const app = createApp();
const HOUR_MS = 60 * 60 * 1000;
let alice: TestUser;
let bob: TestUser;
let courtIds: [number, number];

const inHours = (hours: number) => new Date(Date.now() + hours * HOUR_MS).toISOString();

/** Alice schedules a singles game tomorrow at the first court */
function schedule(by: TestUser = alice) {
  return request(app)
    .post("/games")
    .set("Authorization", by.auth)
    .send({ format: Format.SINGLES, startsAt: inHours(24), courtId: courtIds[0], gameSource: GameSource.SCHEDULED })
    .then((response) => {
      expect(response.status).toBe(201);
      return response.body.game as GameBody;
    });
}

/** A matched singles game tomorrow between Alice (team 1) and Bob (team 2), waiting for both to accept */
function pendingMatch() {
  return prisma.game.create({
    data: {
      format: Format.SINGLES,
      status: GameStatus.OPEN,
      startsAt: new Date(Date.now() + 24 * HOUR_MS),
      courtId: courtIds[0],
      gameSource: GameSource.MATCHED,
      players: { create: [{ userId: alice.id, team: 1 }, { userId: bob.id, team: 2 }] },
    },
  });
}

const post = (path: string, user: TestUser, body: object) =>
  request(app).post(path).set("Authorization", user.auth).send(body);
const patch = (path: string, user: TestUser, body: object) =>
  request(app).patch(path).set("Authorization", user.auth).send(body);

beforeAll(async () => {
  const courts = await prisma.court.findMany({ select: { id: true }, orderBy: { id: "asc" }, take: 2 });
  courtIds = courts.map(({ id }) => id) as [number, number];
  // The forecast is not under test; without a key the games skip it instead of calling OpenWeather
  delete process.env.OPENWEATHER_API_KEY;
});

beforeEach(async () => {
  [alice, bob] = (await createTestUsers("games", 2)) as [TestUser, TestUser];
});

afterEach(async () => {
  await deleteTestUsers([alice, bob]);
});

afterAll(async () => {
  await prisma.$disconnect();
});

describe("Unmocked: POST /games", () => {
  // Input: a singles game tomorrow at a court
  // Expected status code: 201
  // Expected behavior: the game is stored as open, with its creator on team 1
  // Expected output: the new game
  test("Creates an open game with the creator on team 1", async () => {
    const game = await schedule();

    expect(game).toMatchObject({ status: GameStatus.OPEN, courtId: courtIds[0] });
    expect(await prisma.gameUser.findMany({ where: { gameId: game.id }, select: { userId: true, team: true } }))
      .toEqual([{ userId: alice.id, team: 1 }]);
  });

  // Input: a game in the past, and a game at a court that doesn't exist
  // Expected status code: 400, then 404
  // Expected behavior: no game is stored
  // Expected output: INVALID_REQUEST, then COURT_NOT_FOUND
  test("Rejects a game in the past or at an unknown court", async () => {
    const past = await post("/games", alice, {
      format: Format.SINGLES, startsAt: inHours(-1), courtId: courtIds[0], gameSource: GameSource.SCHEDULED,
    });
    const noCourt = await post("/games", alice, {
      format: Format.SINGLES, startsAt: inHours(24), courtId: 2_000_000_000, gameSource: GameSource.SCHEDULED,
    });

    expect(past.status).toBe(400);
    expect(past.body.code).toBe(commonErrors.InvalidRequest.code);
    expect(noCourt.status).toBe(404);
    expect(noCourt.body).toEqual(errorBody(commonErrors.CourtNotFound));
    expect(await prisma.gameUser.count({ where: { userId: alice.id } })).toBe(0);
  });
});

describe("Unmocked: GET /games", () => {
  // Input: the ID of Alice's game, asked for by Bob; then an ID no game has
  // Expected status code: 200, then 404
  // Expected behavior: anyone signed in can see a game
  // Expected output: the game with its court and players, then GAME_NOT_FOUND
  test("Returns a game by ID", async () => {
    const created = await schedule();

    const response = await request(app).get("/games").query({ gameId: created.id }).set("Authorization", bob.auth);
    const missing = await request(app)
      .get("/games")
      .query({ gameId: "00000000-0000-4000-8000-000000000000" })
      .set("Authorization", bob.auth);

    expect(response.status).toBe(200);
    expect(response.body.game).toMatchObject({
      id: created.id,
      court: { id: courtIds[0] },
      players: [{ id: alice.id, team: 1 }],
      forecast: null,
    });
    expect(missing.status).toBe(404);
    expect(missing.body).toEqual(errorBody(gameErrors.NotFound));
  });
});

describe("Unmocked: GET /games/history", () => {
  // Input: Alice schedules a game, Bob joins, Alice starts it and reports 6-3 6-2, Bob confirms
  // Expected status code: 200
  // Expected behavior: the completed game is in both players' history, with its score and rating changes; a game
  // that isn't completed is not
  // Expected output: one game with two rounds, Alice's rating up and Bob's down
  test("Lists a player's completed games with scores and rating changes", async () => {
    const game = await schedule();
    await schedule();
    expect((await post("/games/join", bob, { gameId: game.id, team: 2 })).status).toBe(204);
    expect((await post("/games/start", alice, { gameId: game.id })).status).toBe(200);
    const rounds = [
      [6, 3],
      [6, 2],
    ];
    expect((await post("/games/score", alice, { gameId: game.id, rounds })).status).toBe(200);
    expect((await post("/games/score/confirm", bob, { gameId: game.id, rounds })).status).toBe(200);

    const response = await request(app).get("/games/history").set("Authorization", bob.auth);

    expect(response.status).toBe(200);
    const [played, ...others] = response.body.games as GameBody[];
    expect(others).toEqual([]);
    expect(played).toMatchObject({
      id: game.id,
      status: GameStatus.COMPLETED,
      rounds: [
        { number: 1, team1Score: 6, team2Score: 3 },
        { number: 2, team1Score: 6, team2Score: 2 },
      ],
    });
    const ratingAfter = (user: TestUser) => played?.players.find(({ id }) => id === user.id)?.ratingAfter ?? 1500;
    expect(ratingAfter(alice)).toBeGreaterThan(1500);
    expect(ratingAfter(bob)).toBeLessThan(1500);
  });
});

describe("Unmocked: POST /games/accept", () => {
  // Input: Alice accepts a new match, accepts it again, then Bob accepts
  // Expected status code: 204, 409, 204
  // Expected behavior: the match stays open until both accept, then is booked
  // Expected output: MATCH_ALREADY_ACCEPTED for the second answer from Alice
  test("Books a match once both players accept", async () => {
    const match = await pendingMatch();

    const first = await post("/games/accept", alice, { gameId: match.id });
    const statusAfterFirst = await prisma.game.findUnique({ where: { id: match.id }, select: { status: true } });
    const twice = await post("/games/accept", alice, { gameId: match.id });
    const second = await post("/games/accept", bob, { gameId: match.id });

    expect(first.status).toBe(204);
    expect(statusAfterFirst).toEqual({ status: GameStatus.OPEN });
    expect(twice.status).toBe(409);
    expect(twice.body).toEqual(errorBody(gameErrors.AlreadyAccepted));
    expect(second.status).toBe(204);
    expect(await prisma.game.findUnique({ where: { id: match.id }, select: { status: true } })).toEqual({
      status: GameStatus.BOOKED,
    });
  });

  // Input: Bob accepts Alice's scheduled game, which he is not in
  // Expected status code: 403
  // Expected output: NOT_IN_GAME
  test("Rejects an answer from a user who is not in the game", async () => {
    const game = await schedule();

    const response = await post("/games/accept", bob, { gameId: game.id });

    expect(response.status).toBe(403);
    expect(response.body).toEqual(errorBody(gameErrors.NotInGame));
  });
});

describe("Unmocked: POST /games/invite", () => {
  // Input: Alice invites Bob to her game before and after they are friends
  // Expected status code: 403, then 200
  // Expected behavior: only friends can be invited; with no device to push to, the invite isn't delivered
  // Expected output: NOT_FRIENDS, then { notified: false }
  test("Invites a friend only", async () => {
    const game = await schedule();

    const strangers = await post("/games/invite", alice, { gameId: game.id, userId: bob.id });
    await prisma.friendship.create({
      data: { requesterId: alice.id, addresseeId: bob.id, status: FriendshipStatus.ACCEPTED, acceptedAt: new Date() },
    });
    const friends = await post("/games/invite", alice, { gameId: game.id, userId: bob.id });

    expect(strangers.status).toBe(403);
    expect(strangers.body).toEqual(errorBody(gameErrors.NotFriends));
    expect(friends.status).toBe(200);
    expect(friends.body).toEqual({ notified: false });
  });
});

describe("Unmocked: PATCH /games/time", () => {
  // Input: Alice moves her game to 2 days from now
  // Expected status code: 200
  // Expected behavior: the new start time is stored
  // Expected output: the game with the new start time
  test("Moves a scheduled game", async () => {
    const game = await schedule();
    const startsAt = inHours(48);

    const response = await patch("/games/time", alice, { gameId: game.id, startsAt });

    expect(response.status).toBe(200);
    expect(response.body.game.startsAt).toBe(startsAt);
    const stored = await prisma.game.findUnique({ where: { id: game.id }, select: { startsAt: true } });
    expect(stored?.startsAt.toISOString()).toBe(startsAt);
  });

  // Input: Alice moves a matched game, and Bob moves Alice's scheduled game
  // Expected status code: 409, then 403
  // Expected behavior: neither game moves
  // Expected output: MATCHED_NOT_MOVABLE, then NOT_IN_GAME
  test("Refuses to move a matched game or someone else's game", async () => {
    const match = await pendingMatch();
    const game = await schedule();

    const matched = await patch("/games/time", alice, { gameId: match.id, startsAt: inHours(48) });
    const notMine = await patch("/games/time", bob, { gameId: game.id, startsAt: inHours(48) });

    expect(matched.status).toBe(409);
    expect(matched.body).toEqual(errorBody(gameErrors.MatchedNotMovable));
    expect(notMine.status).toBe(403);
    expect(notMine.body).toEqual(errorBody(gameErrors.NotInGame));
  });
});

describe("Unmocked: PATCH /games/location", () => {
  // Input: Alice moves her game to another court, then to a court that doesn't exist
  // Expected status code: 200, then 404
  // Expected behavior: the first move is stored; the second changes nothing
  // Expected output: the game at the new court, then COURT_NOT_FOUND
  test("Moves a scheduled game to another court", async () => {
    const game = await schedule();

    const moved = await patch("/games/location", alice, { gameId: game.id, courtId: courtIds[1] });
    const noCourt = await patch("/games/location", alice, { gameId: game.id, courtId: 2_000_000_000 });

    expect(moved.status).toBe(200);
    expect(moved.body.game).toMatchObject({ id: game.id, courtId: courtIds[1] });
    expect(noCourt.status).toBe(404);
    expect(noCourt.body).toEqual(errorBody(commonErrors.CourtNotFound));
    expect(await prisma.game.findUnique({ where: { id: game.id }, select: { courtId: true } })).toEqual({
      courtId: courtIds[1],
    });
  });
});
