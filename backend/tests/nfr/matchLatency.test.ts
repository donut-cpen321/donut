import type { ScheduledTask } from "node-cron";
import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { startMatchmakingJob } from "../../src/jobs/matchmaking.job";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// NFR: match found latency. The real matchmaking job runs on its 10-second schedule, as in the app, against a real
// database. Compatible players join the queue through the API, and each one polls GET /matchmaking, as the app does
// while searching, until they are out of the queue with a match. Each player's latency runs from the moment the last
// player joined to the moment they see the match.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(120_000);

const PLAYERS = 20;
const LIMIT_MS = 60_000;
const POLL_MS = 100;

const app = createApp();
let users: TestUser[] = [];
let task: ScheduledTask | undefined;

// Heard Island: no other test queues players near it, so they can only be matched with each other
const spot = { format: "SINGLES", latitude: -53.1, longitude: 73.5 };

/**
 * Polls the player's queue entry until it is gone, which happens when they are matched
 * @param since when the latency started
 * @returns the player's latency in milliseconds
 * @throws Error if the player is still queued after the limit, or the queue lookup fails
 */
function waitForMatch(user: TestUser, since: number): Promise<number> {
  return request(app)
    .get("/matchmaking")
    .set("Authorization", user.auth)
    .then((response) => {
      const elapsed = performance.now() - since;
      if (response.status === 404) return elapsed;
      if (response.status !== 200 || elapsed > LIMIT_MS * 1.5) {
        throw new Error(`Still queued after ${elapsed.toFixed(0)} ms (status ${response.status})`);
      }
      return new Promise<number>((resolve, reject) => {
        setTimeout(() => {
          waitForMatch(user, since).then(resolve, reject);
        }, POLL_MS);
      });
    });
}

afterAll(async () => {
  await task?.stop();
  await deleteTestUsers(users);
  await prisma.$disconnect();
});

describe("NFR: match found latency", () => {
  // Input: 20 players in 10 rating pairs (1200, 1210; 1500, 1510; ...) queue for singles at the same spot while the
  // matchmaking job runs every 10 seconds
  // Expected behavior: every player is matched with a compatible player into a pending matched game
  // Expected output: 95% of players see their match within 60 seconds of the last one joining
  test("Matches 95% of compatible queued players within 60 seconds", async () => {
    users = await createTestUsers("nfr-match", PLAYERS, (index) => 1200 + Math.floor(index / 2) * 300 + (index % 2) * 10);
    task = startMatchmakingJob();

    for (const user of users) {
      const joined = await request(app).post("/matchmaking").set("Authorization", user.auth).send(spot);
      expect(joined.status).toBe(201);
    }
    const lastJoined = performance.now();
    const latencies = await Promise.all(users.map((user) => waitForMatch(user, lastJoined)));

    latencies.sort((a, b) => a - b);
    const p95 = latencies[Math.ceil(0.95 * latencies.length) - 1] ?? Number.NaN;
    console.info(
      `Match found latency for ${PLAYERS} players: min ${((latencies[0] ?? 0) / 1000).toFixed(1)} s, ` +
        `p95 ${(p95 / 1000).toFixed(1)} s, max ${((latencies.at(-1) ?? 0) / 1000).toFixed(1)} s`,
    );
    expect(p95).toBeLessThanOrEqual(LIMIT_MS);

    // Each player is in exactly one pending match with one other player
    const games = await prisma.game.findMany({
      where: { players: { some: { userId: { in: users.map(({ id }) => id) } } } },
      select: { players: { select: { userId: true } } },
    });
    expect(games).toHaveLength(PLAYERS / 2);
    expect(games.every(({ players }) => players.length === 2)).toBe(true);
  });
});
