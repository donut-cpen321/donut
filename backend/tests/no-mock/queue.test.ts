import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { errorBody } from "../../src/errors";
import { Format, GameSource, GameStatus } from "../../src/generated/prisma/enums";
import { claimMatch } from "../../src/matchmaking/matchmaking.repository";
import { matchmakingErrors } from "../../src/matchmaking/matchmaking.service";
import { saveRatingChanges } from "../../src/ranking/ratings.repository";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// Joining and leaving the matchmaking queue, and the database steps that must roll back when they fail, against a
// real database. Every test gets new users, so the tests don't depend on each other or on the order they run in.
// They don't run matchmaking, so they can't match another test file's players.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

const app = createApp();
// Tristan da Cunha: no other test queues players near it
const spot = { format: "SINGLES", latitude: -37.07, longitude: -12.31 };
let users: TestUser[] = [];

const firstUser = () => (users as [TestUser])[0];
const queued = (user: TestUser) => prisma.matchmakingRequest.count({ where: { userId: user.id } });

beforeEach(async () => {
  users = await createTestUsers("queue", 2);
});

afterEach(async () => {
  await deleteTestUsers(users);
});

afterAll(async () => {
  await prisma.$disconnect();
});

describe("Unmocked: POST, GET, DELETE /matchmaking and GET /matchmaking/nearby", () => {
  // Input: player 1 joins the queue, looks it up, and looks for players nearby after player 2 joins at the same spot;
  // then joins again
  // Expected status code: 201, 200, 200, then 409
  // Expected behavior: one queue entry is stored for player 1
  // Expected output: the entry as sent, player 2 on the map, then ALREADY_IN_QUEUE
  test("Queues a player once and shows the other players searching nearby", async () => {
    const auth = users[0]?.auth ?? "";
    const joined = await request(app).post("/matchmaking").set("Authorization", auth).send(spot);
    const entry = await request(app).get("/matchmaking").set("Authorization", auth);
    await request(app).post("/matchmaking").set("Authorization", users[1]?.auth ?? "").send(spot);
    const nearby = await request(app).get("/matchmaking/nearby").set("Authorization", auth);
    const again = await request(app).post("/matchmaking").set("Authorization", auth).send(spot);

    expect(joined.status).toBe(201);
    expect(joined.body.request).toMatchObject(spot);
    expect(entry.status).toBe(200);
    expect(entry.body.request).toMatchObject(spot);
    expect(nearby.status).toBe(200);
    expect(nearby.body.players).toHaveLength(1);
    expect(again.status).toBe(409);
    expect(again.body).toEqual(errorBody(matchmakingErrors.AlreadyQueued));
    expect(await queued(firstUser())).toBe(1);
  });

  // Input: a queued player leaves, looks up the queue, and leaves again
  // Expected status code: 204, 404, then 204
  // Expected behavior: the entry is deleted; leaving when not queued does nothing
  // Expected output: NOT_IN_QUEUE for the lookup
  test("Takes a player out of the queue", async () => {
    const auth = users[0]?.auth ?? "";
    await request(app).post("/matchmaking").set("Authorization", auth).send(spot);

    const left = await request(app).delete("/matchmaking").set("Authorization", auth);
    const entry = await request(app).get("/matchmaking").set("Authorization", auth);
    const again = await request(app).delete("/matchmaking").set("Authorization", auth);

    expect(left.status).toBe(204);
    expect(entry.status).toBe(404);
    expect(entry.body).toEqual(errorBody(matchmakingErrors.NotQueued));
    expect(again.status).toBe(204);
    expect(await queued(firstUser())).toBe(0);
  });
});

// Function claimMatch, saveRatingChanges
describe("Unmocked: database steps that roll back", () => {
  const userIds = () => users.map(({ id }) => id);
  const matchGame = (courtId: number) => ({
    format: Format.SINGLES,
    status: GameStatus.OPEN,
    startsAt: new Date(Date.now() + 60 * 60 * 1000),
    gameSource: GameSource.MATCHED,
    court: { connect: { id: courtId } },
    players: { create: users.map(({ id }, index) => ({ team: index + 1, user: { connect: { id } } })) },
  });

  // Input: a match of two players, only one of whom is still queued
  // Expected behavior: no game is made, and the player still queued stays queued
  // Expected output: null
  test("Doesn't claim a match when a player left the queue", async () => {
    await request(app).post("/matchmaking").set("Authorization", users[0]?.auth ?? "").send(spot);
    const court = await prisma.court.findFirstOrThrow({ select: { id: true } });

    await expect(claimMatch(userIds(), matchGame(court.id))).resolves.toBeNull();
    expect(await queued(firstUser())).toBe(1);
    expect(await prisma.gameUser.count({ where: { userId: { in: userIds() } } })).toBe(0);
  });

  // Input: a match of two queued players whose game can't be created (its court doesn't exist)
  // Expected behavior: the error is passed on, and both players are back in the queue
  // Expected output: a rejected promise
  test("Puts the players back in the queue when the game can't be created", async () => {
    for (const user of users) {
      await request(app).post("/matchmaking").set("Authorization", user.auth).send(spot);
    }

    await expect(claimMatch(userIds(), matchGame(2_000_000_000))).rejects.toThrow();
    for (const user of users) {
      expect(await queued(user)).toBe(1);
    }
  });

  // Input: a rating change for a player who isn't in the game, after one for a player who is
  // Expected behavior: the error names the player, and the first player's new rating is rolled back too
  // Expected output: a rejected transaction; player 1 still rated 1500
  test("Rolls back every rating when one can't be saved", async () => {
    const [player, outsider] = users as [TestUser, TestUser];
    const court = await prisma.court.findFirstOrThrow({ select: { id: true } });
    const game = await prisma.game.create({
      data: {
        format: Format.SINGLES,
        status: GameStatus.IN_PROGRESS,
        startsAt: new Date(),
        courtId: court.id,
        gameSource: GameSource.SCHEDULED,
        players: { create: [{ userId: player.id, team: 1 }] },
      },
    });
    const changes = users.map(({ id }) => ({ userId: id, ratingBefore: 1500, ratingAfter: 1600 }));

    await expect(prisma.$transaction((tx) => saveRatingChanges(tx, game.id, changes))).rejects.toThrow(
      `Failed to save rating change for user ${outsider.id} in game ${game.id}`,
    );
    expect(await prisma.user.findUnique({ where: { id: player.id }, select: { rating: true } })).toEqual({
      rating: 1500,
    });
  });
});
