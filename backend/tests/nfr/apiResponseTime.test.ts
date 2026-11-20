import { once } from "node:events";
import type { Server } from "node:http";
import type { AddressInfo } from "node:net";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { Format, GameSource, GameStatus } from "../../src/generated/prisma/enums";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// NFR: API response time. 50 users use the app at the same time against a real server and database, over HTTP: in
// pairs, they open the feed, schedule a game and join it, accept a match, start the game, and report and confirm the
// score. Every request is timed from sending it to reading the whole response.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(180_000);

const CONCURRENT_USERS = 50;
const ROUNDS = 4;
const LIMIT_MS = 500;
const HOUR_MS = 60 * 60 * 1000;

interface Timing {
  endpoint: string;
  ms: number;
}

let server: Server;
let baseUrl = "";
let users: TestUser[] = [];
let courtId = 0;
const timings: Timing[] = [];

/**
 * Sends a request as a user and times it
 * @returns the response body (null when empty)
 * @throws Error if the status isn't the expected one, so a failed request can't pass as a fast one
 */
function timed(user: TestUser, method: string, path: string, body: object | undefined, status: number) {
  const started = performance.now();
  return fetch(`${baseUrl}${path}`, {
    method,
    headers: { Authorization: user.auth, "Content-Type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
    .then((response) =>
      response.text().then((text) => {
        timings.push({ endpoint: `${method} ${path.split("?")[0] ?? path}`, ms: performance.now() - started });
        if (response.status !== status) {
          throw new Error(`${method} ${path} returned ${response.status}, expected ${status}: ${text}`);
        }
        return text === "" ? null : (JSON.parse(text) as unknown);
      }),
    );
}

/** A matched singles game between the pair, waiting for both to accept, as matchmaking makes it */
function pendingMatch(host: TestUser, guest: TestUser) {
  return prisma.game
    .create({
      data: {
        format: Format.SINGLES,
        status: GameStatus.OPEN,
        startsAt: new Date(Date.now() + 2 * HOUR_MS),
        courtId,
        gameSource: GameSource.MATCHED,
        players: { create: [{ userId: host.id, team: 1 }, { userId: guest.id, team: 2 }] },
      },
      select: { id: true },
    })
    .then(({ id }) => id);
}

/** One pair's turn: every request the NFR names, with the two players acting at the same time where they can */
function playRound(host: TestUser, guest: TestUser, round: number) {
  const rounds = [
    [6, round % 5],
    [6, 4],
  ];
  return Promise.all([
    timed(host, "GET", "/games/feed", undefined, 200),
    timed(guest, "GET", "/games/feed", undefined, 200),
    pendingMatch(host, guest),
  ])
    .then(([, , matchId]) =>
      Promise.all([
        timed(host, "POST", "/games/accept", { gameId: matchId }, 204),
        timed(guest, "POST", "/games/accept", { gameId: matchId }, 204),
        timed(host, "POST", "/games", {
          format: Format.SINGLES,
          startsAt: new Date(Date.now() + 24 * HOUR_MS).toISOString(),
          courtId,
          gameSource: GameSource.SCHEDULED,
        }, 201),
      ]),
    )
    .then(([, , created]) => {
      const gameId = (created as { game: { id: string } }).game.id;
      return timed(guest, "POST", "/games/join", { gameId, team: 2 }, 204)
        .then(() => timed(host, "POST", "/games/start", { gameId }, 200))
        .then(() => timed(host, "POST", "/games/score", { gameId, rounds }, 200))
        .then(() => timed(guest, "POST", "/games/score/confirm", { gameId, rounds }, 200))
        .then(() => Promise.all([
          timed(host, "GET", "/games/me", undefined, 200),
          timed(guest, "GET", "/games/history", undefined, 200),
        ]));
    });
}

/** Plays every round for one pair, one after another */
function playRounds(host: TestUser, guest: TestUser, round = 0): Promise<void> {
  if (round === ROUNDS) return Promise.resolve();
  return playRound(host, guest, round).then(() => playRounds(host, guest, round + 1));
}

const percentile = (values: number[], fraction: number) => {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil(fraction * sorted.length) - 1)] ?? Number.NaN;
};

beforeAll(async () => {
  // The forecast is not under test; without a key the feed skips it instead of calling OpenWeather
  delete process.env.OPENWEATHER_API_KEY;
  courtId = (await prisma.court.findFirstOrThrow({ select: { id: true } })).id;
  users = await createTestUsers("nfr-api", CONCURRENT_USERS);
  server = createApp().listen(0);
  await once(server, "listening");
  baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  // Opens the database connections before the timed requests, as a running server already has them
  const [first] = users as [TestUser];
  await timed(first, "GET", "/games/feed", undefined, 200);
  timings.length = 0;
});

afterAll(async () => {
  server.close();
  await deleteTestUsers(users);
  await prisma.$disconnect();
});

describe("NFR: API response time", () => {
  // Input: 50 users in 25 pairs, all at once; each pair plays 4 rounds of: both open the feed, both accept a match,
  // one schedules a game and the other joins it, they start it, one reports the score and the other confirms it,
  // then they open their games and history
  // Expected behavior: every request succeeds while all 50 users are active
  // Expected output: the 95th percentile of all response times is at most 500 ms
  test("Answers 95% of requests within 500 ms with 50 concurrent users", async () => {
    const pairs = Array.from(
      { length: CONCURRENT_USERS / 2 },
      (_, index) => users.slice(2 * index, 2 * index + 2) as [TestUser, TestUser],
    );

    await Promise.all(pairs.map(([host, guest]) => playRounds(host, guest)));

    const all = timings.map(({ ms }) => ms);
    const byEndpoint = new Map<string, number[]>();
    for (const { endpoint, ms } of timings) {
      byEndpoint.set(endpoint, [...(byEndpoint.get(endpoint) ?? []), ms]);
    }
    const rows = [...byEndpoint].map(
      ([endpoint, values]) =>
        `${endpoint.padEnd(26)} n=${String(values.length).padStart(4)}  ` +
        `p50 ${percentile(values, 0.5).toFixed(0).padStart(4)} ms  p95 ${percentile(values, 0.95).toFixed(0).padStart(4)} ms`,
    );
    console.info(
      [
        `${all.length} requests from ${CONCURRENT_USERS} concurrent users`,
        ...rows,
        `${"All".padEnd(26)} n=${String(all.length).padStart(4)}  p50 ${percentile(all, 0.5).toFixed(0).padStart(4)} ms  ` +
          `p95 ${percentile(all, 0.95).toFixed(0).padStart(4)} ms  max ${Math.max(...all).toFixed(0)} ms`,
      ].join("\n"),
    );
    expect(all).toHaveLength((CONCURRENT_USERS / 2) * ROUNDS * 11);
    expect(percentile(all, 0.95)).toBeLessThanOrEqual(LIMIT_MS);
  });
});
