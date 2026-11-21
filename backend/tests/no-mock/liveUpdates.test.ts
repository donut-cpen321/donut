import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { Format, GameSource, GameStatus } from "../../src/generated/prisma/enums";
import type { EventReader, StreamedEvent } from "../mock/sseReader";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";
import { StreamTestServer } from "../streamServer";

// The live update streams against a real database: a change made through the API reaches the streams that show it,
// pushed rather than polled. Every test gets two new users and makes its own game, tournament or friend request
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(30_000);

interface PlayerBody {
  id: string;
}

interface GameBody {
  id: string;
  status: string;
  players: PlayerBody[];
}

interface TournamentBody {
  id: string;
  players: PlayerBody[];
}

interface ListsBody {
  games: GameBody[];
  tournaments: TournamentBody[];
}

const app = createApp();
const server = new StreamTestServer();
let users: TestUser[] = [];
let tournamentIds: string[] = [];
let courtId: number;

const auth = (index: number) => users.at(index)?.auth ?? "";
const userId = (index: number) => users.at(index)?.id ?? "";
const inDays = (days: number) => new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString();
const playerIds = (players: PlayerBody[]) => players.map(({ id }) => id).sort();

// Promise chains rather than async helpers: a failed request or stream rejects the promise, which fails the test

/** Reads the stream until an event with this name whose data passes the check, and returns its data */
function nextEvent(events: EventReader, name: string, check: (data: unknown) => boolean): Promise<unknown> {
  return events.next().then(({ event, data }: StreamedEvent) =>
    event === name && check(data) ? data : nextEvent(events, name, check),
  );
}

/** User 1 schedules a singles game tomorrow; returns its ID */
function createGame(): Promise<string> {
  return request(app)
    .post("/games")
    .set("Authorization", auth(0))
    .send({ format: Format.SINGLES, startsAt: inDays(1), courtId, gameSource: GameSource.SCHEDULED })
    .then((response) => {
      expect(response.status).toBe(201);
      return (response.body as { game: GameBody }).game.id;
    });
}

/** User 1 creates a singles tournament in 2 days; returns its ID */
function createTournament(): Promise<string> {
  return request(app)
    .post("/tournaments")
    .set("Authorization", auth(0))
    .send({ name: "Live test", format: Format.SINGLES, startsAt: inDays(2), courtId })
    .then((response) => {
      expect(response.status).toBe(201);
      const { id } = (response.body as { tournament: TournamentBody }).tournament;
      tournamentIds.push(id);
      return id;
    });
}

beforeAll(async () => {
  await server.start();
  courtId = (await prisma.court.findFirstOrThrow({ select: { id: true } })).id;
});

beforeEach(async () => {
  users = await createTestUsers("live", 2);
  tournamentIds = [];
});

afterEach(async () => {
  server.stopWatching();
  // Deleting a tournament deletes its players and bracket; the users and their games are deleted after
  await prisma.tournament.deleteMany({ where: { id: { in: tournamentIds } } });
  await deleteTestUsers(users);
});

afterAll(async () => {
  await server.close();
  await prisma.$disconnect();
});

describe("Unmocked: GET /games/events", () => {
  // Interface GET /games/events, POST /games/join, DELETE /games
  // Input: user 2 watches user 1's new singles game, joins it, then user 1 cancels it
  // Expected status code: 200 (the stream opens), 204 for the join and the cancel
  // Expected behavior: the game is sent when the stream opens, again once user 2 has joined, and its deletion last
  // Expected output: gameUpdated with 1 player, gameUpdated with both players and BOOKED, then gameDeleted
  test("Pushes the game when it opens, when a player joins and when it is cancelled", async () => {
    const gameId = await createGame();
    const events = await server.watch(`/games/events?gameId=${gameId}`, userId(1));

    const opened = (await nextEvent(events, "gameUpdated", () => true)) as { game: GameBody };
    expect(playerIds(opened.game.players)).toEqual([userId(0)]);

    const join = await request(app).post("/games/join").set("Authorization", auth(1)).send({ gameId, team: 2 });
    expect(join.status).toBe(204);
    const joined = (await nextEvent(events, "gameUpdated", (data) =>
      (data as { game: GameBody }).game.players.length === 2,
    )) as { game: GameBody };
    expect(playerIds(joined.game.players)).toEqual([userId(0), userId(1)].sort());
    expect(joined.game.status).toBe(GameStatus.BOOKED);

    const cancel = await request(app).delete("/games").query({ gameId }).set("Authorization", auth(0));
    expect(cancel.status).toBe(204);
    expect(await nextEvent(events, "gameDeleted", () => true)).toEqual({ gameId });
  });

  // Interface GET /games/events
  // Input: a game ID that isn't a UUID
  // Expected status code: 400
  // Expected behavior: no stream is opened
  // Expected output: INVALID_REQUEST
  test("Rejects an invalid game ID", async () => {
    const response = await request(app).get("/games/events").query({ gameId: "nope" }).set("Authorization", auth(0));
    expect(response.status).toBe(400);
    expect((response.body as { code: string }).code).toBe("INVALID_REQUEST");
  });
});

describe("Unmocked: GET /games/feed/events", () => {
  // Interface GET /games/feed/events, POST /games, POST /games/join
  // Input: user 2 has the Games tab open; user 1 schedules a game, then user 2 joins it
  // Expected status code: 200 (the stream opens), 201 for the game, 204 for the join
  // Expected behavior: the new game is pushed on the feed, and once user 2 joins it is pushed in their games
  // Expected output: a feed event listing the game, then a mine event listing it
  test("Pushes a new game on the feed and a joined game in the user's games", async () => {
    const events = await server.watch("/games/feed/events", userId(1));
    const gameId = await createGame();

    const feed = (await nextEvent(events, "feed", (data) =>
      (data as ListsBody).games.some(({ id }) => id === gameId),
    )) as ListsBody;
    expect(feed.games.find(({ id }) => id === gameId)?.status).toBe(GameStatus.OPEN);

    const join = await request(app).post("/games/join").set("Authorization", auth(1)).send({ gameId, team: 2 });
    expect(join.status).toBe(204);
    const mine = (await nextEvent(events, "mine", (data) =>
      (data as ListsBody).games.some(({ id }) => id === gameId),
    )) as ListsBody;
    expect(playerIds(mine.games.find(({ id }) => id === gameId)?.players ?? [])).toEqual(
      [userId(0), userId(1)].sort(),
    );
  });
});

describe("Unmocked: GET /tournaments/events", () => {
  // Interface GET /tournaments/events, POST /tournaments/join
  // Input: user 2 watches user 1's new tournament, then registers for it
  // Expected status code: 200 (the stream opens), 204 for the registration
  // Expected behavior: the tournament is sent when the stream opens, and again once user 2 has registered
  // Expected output: tournamentUpdated with 1 player, then with both
  test("Pushes the tournament when it opens and when a player registers", async () => {
    const tournamentId = await createTournament();
    const events = await server.watch(`/tournaments/events?tournamentId=${tournamentId}`, userId(1));

    const opened = (await nextEvent(events, "tournamentUpdated", () => true)) as { tournament: TournamentBody };
    expect(playerIds(opened.tournament.players)).toEqual([userId(0)]);

    const join = await request(app).post("/tournaments/join").set("Authorization", auth(1)).send({ tournamentId });
    expect(join.status).toBe(204);
    const joined = (await nextEvent(events, "tournamentUpdated", (data) =>
      (data as { tournament: TournamentBody }).tournament.players.length === 2,
    )) as { tournament: TournamentBody };
    expect(playerIds(joined.tournament.players)).toEqual([userId(0), userId(1)].sort());
  });

  // Interface GET /tournaments/events
  // Input: a tournament ID that isn't a UUID
  // Expected status code: 400
  // Expected behavior: no stream is opened
  // Expected output: INVALID_REQUEST
  test("Rejects an invalid tournament ID", async () => {
    const response = await request(app)
      .get("/tournaments/events")
      .query({ tournamentId: "nope" })
      .set("Authorization", auth(0));
    expect(response.status).toBe(400);
    expect((response.body as { code: string }).code).toBe("INVALID_REQUEST");
  });
});

describe("Unmocked: GET /friends/events", () => {
  // Interface GET /friends/events, POST /friends/requests, POST /friends/requests/accept
  // Input: user 1 has the friends page open; user 2 sends them a request, then user 1 accepts it
  // Expected status code: 200 (the stream opens), 201 for the request, 204 for the accept
  // Expected behavior: the page is sent when it opens, the request is pushed when it arrives, and the new friend
  // once it is accepted
  // Expected output: requests with user 2 incoming, then friends with user 2
  test("Pushes an incoming request and the new friend once accepted", async () => {
    const events = await server.watch("/friends/events", userId(0));
    expect((await events.next()).event).toBe("friends");
    expect(await events.next()).toEqual({ event: "requests", data: { incoming: [], outgoing: [] } });

    const send = await request(app).post("/friends/requests").set("Authorization", auth(1)).send({ userId: userId(0) });
    expect(send.status).toBe(201);
    const requests = (await nextEvent(events, "requests", (data) =>
      (data as { incoming: PlayerBody[] }).incoming.length > 0,
    )) as { incoming: PlayerBody[] };
    expect(playerIds(requests.incoming)).toEqual([userId(1)]);

    const accept = await request(app)
      .post("/friends/requests/accept")
      .set("Authorization", auth(0))
      .send({ userId: userId(1) });
    expect(accept.status).toBe(204);
    const friends = (await nextEvent(events, "friends", (data) =>
      (data as { friends: PlayerBody[] }).friends.length > 0,
    )) as { friends: PlayerBody[] };
    expect(playerIds(friends.friends)).toEqual([userId(1)]);
  });
});
