import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { GameSource, GameStatus } from "../../src/generated/prisma/enums";
import { startMatchmakingJob } from "../../src/jobs/matchmaking.job";
import { runMatchmaking } from "../../src/matchmaking/matchmaking.service";
import type { EventReader } from "../mock/sseReader";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";
import { StreamTestServer } from "../streamServer";

// Runs matchmaking against a real database: two players queue, one run matches them, and the match must show
// everywhere the app looks for it at once: out of the queue, in both players' games, and on their live Games tab
// stream, which the app follows while searching in case the match push never arrives. Every test gets two new
// players and matches them itself (queueAndMatch), so the tests don't depend on each other or on the order they run
// in. A run matches everyone queued, and its live pushes reach only streams in its own process, so every no-mock
// test that runs matchmaking is in this file, where they run one at a time.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

interface GameBody {
  id: string;
  status: string;
  gameSource: string;
  players: { id: string }[];
}

interface GameListBody {
  games: GameBody[];
}

const app = createApp();
const server = new StreamTestServer();
let users: TestUser[] = [];

// Reykjavik: far from every other queued player, so the run can only match these two with each other
const farAway = { format: "EITHER", latitude: 64.1466, longitude: -21.9426 };

const auth = (index: number) => users.at(index)?.auth ?? "";

const isPendingMatch = ({ gameSource, status }: GameBody) =>
  gameSource === GameSource.MATCHED && status === GameStatus.OPEN;

// Promise chains rather than async helpers: a failed request or stream rejects the promise, which fails the test
// that awaits it

/** The player's open matched game in their games, if any */
function pendingMatchOf(index: number): Promise<GameBody | undefined> {
  return request(app)
    .get("/games/me")
    .set("Authorization", auth(index))
    .then((response) => {
      expect(response.status).toBe(200);
      return (response.body as GameListBody).games.find(isPendingMatch);
    });
}

/** Reads the stream until a "mine" event lists the player's open matched game, and returns it */
function nextMatchOn(events: EventReader): Promise<GameBody> {
  return events.next().then(({ event, data }) => {
    const match = (data as GameListBody).games.find(isPendingMatch);
    return event === "mine" && match ? match : nextMatchOn(events);
  });
}

/** Both players queue at the same spot and a run matches them; returns their pending match's ID */
function queueAndMatch(): Promise<string> {
  const queue = (index: number) => request(app).post("/matchmaking").set("Authorization", auth(index)).send(farAway);
  return queue(0)
    .then(() => queue(1))
    .then(() => runMatchmaking())
    .then(() => pendingMatchOf(0))
    .then((match) => {
      if (match === undefined) throw new Error("The players were not matched");
      return match.id;
    });
}

beforeAll(async () => {
  await server.start();
});

beforeEach(async () => {
  users = await createTestUsers("matchmaking", 2);
});

afterEach(async () => {
  server.stopWatching();
  await deleteTestUsers(users);
});

afterAll(async () => {
  await server.close();
  await prisma.$disconnect();
});

describe("Unmocked: matchmaking", () => {
  // Interface POST /matchmaking, GET /matchmaking/events, GET /games/feed/events, GET /matchmaking, GET /games/me
  // Input: two 1500-rated players queue for either format at the same place; player 1 has the Games stream open
  // Expected status code: 201 for each join, then 404 for each queue lookup
  // Expected behavior: one run matches them into one open matched singles game, takes both out of the queue in
  // the same step, and pushes the game on player 1's live stream
  // Expected output: the same game on the stream and in both players' games
  test("Shows a new match on the live stream, out of the queue and in both players' games", async () => {
    expect((await request(app).post("/matchmaking").set("Authorization", auth(0)).send(farAway)).status).toBe(201);
    expect((await request(app).post("/matchmaking").set("Authorization", auth(1)).send(farAway)).status).toBe(201);
    const events = await server.watch("/games/feed/events", users[0]?.id ?? "");
    const queue = await server.watch("/matchmaking/events", users[0]?.id ?? "");
    // Player 2 searches at the same spot, so player 1's map shows them
    const nearby = await queue.next();
    expect(nearby.event).toBe("nearby");
    expect((nearby.data as { players: unknown[] }).players).toHaveLength(1);

    await runMatchmaking();

    // Out of the queue: the map stops, pushed rather than polled
    expect((await queue.next()).event).toBe("notQueued");

    const streamed = await nextMatchOn(events);
    expect(streamed.players.map(({ id }) => id).sort()).toEqual(users.map(({ id }) => id).sort());

    for (const index of [0, 1]) {
      expect((await request(app).get("/matchmaking").set("Authorization", auth(index))).status).toBe(404);
      expect((await pendingMatchOf(index))?.id).toBe(streamed.id);
    }
  });

  // Interface POST /matchmaking
  // Input: after a match, player 2 tries to queue again while the match waits for answers
  // Expected status code: 403
  // Expected behavior: they stay out of the queue, so a second match can't be made for them
  // Expected output: ALREADY_HAS_MATCH
  test("Keeps a matched player out of the queue while the match waits", async () => {
    await queueAndMatch();

    const response = await request(app).post("/matchmaking").set("Authorization", auth(1)).send(farAway);

    expect(response.status).toBe(403);
    expect((response.body as { code: string }).code).toBe("ALREADY_HAS_MATCH");
    await expect(runMatchmaking()).resolves.not.toContainEqual(
      expect.objectContaining({ userIds: expect.arrayContaining([users[1]?.id]) }),
    );
  });

  // Interface POST /games/accept, GET /games
  // Input: after a match, both players accept it
  // Expected status code: 204 for each
  // Expected behavior: the game is booked once the second player accepts
  // Expected output: status BOOKED
  test("Books the match once both players accept", async () => {
    const matchId = await queueAndMatch();

    for (const index of [0, 1]) {
      const accepted = await request(app)
        .post("/games/accept")
        .set("Authorization", auth(index))
        .send({ gameId: matchId });
      expect(accepted.status).toBe(204);
    }

    const game = await request(app).get("/games").set("Authorization", auth(0)).query({ gameId: matchId });
    expect((game.body as { game: GameBody }).game.status).toBe(GameStatus.BOOKED);
  });
});

describe("Unmocked: matchmaking job", () => {
  // Input: two 1500-rated players queue for singles at the same remote spot; the real job is started and run once
  // now with execute(), instead of waiting for its 10-second schedule
  // Expected behavior: they are matched into one open matched singles game and taken out of the queue
  // Expected output: both players in the same new game, no queue entries left
  test("Matches compatible queued players", async () => {
    const userIds = users.map(({ id }) => id);
    for (const index of [0, 1]) {
      const joined = await request(app)
        .post("/matchmaking")
        .set("Authorization", auth(index))
        .send({ ...farAway, format: "SINGLES" });
      expect(joined.status).toBe(201);
    }

    const task = startMatchmakingJob();
    await task.execute();
    await task.stop();

    expect(await prisma.matchmakingRequest.count({ where: { userId: { in: userIds } } })).toBe(0);
    const games = await prisma.game.findMany({
      where: { players: { some: { userId: { in: userIds } } } },
      select: { format: true, gameSource: true, status: true, players: { select: { userId: true } } },
    });
    expect(games).toHaveLength(1);
    expect(games[0]).toMatchObject({ format: "SINGLES", gameSource: GameSource.MATCHED, status: GameStatus.OPEN });
    expect(games[0]?.players.map(({ userId }) => userId).sort()).toEqual([...userIds].sort());
  });
});
