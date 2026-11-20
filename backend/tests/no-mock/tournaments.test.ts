import { randomUUID } from "node:crypto";
import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { errorBody } from "../../src/errors";
import { gameErrors } from "../../src/games/games.service";
import {
  Format,
  GameSource,
  GameStatus,
  TournamentStatus,
} from "../../src/generated/prisma/enums";
import { ROUND_INTERVAL_MS } from "../../src/tournaments/bracket";
import { tournamentErrors } from "../../src/tournaments/tournaments.service";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// Runs the tournament flow against a real database: registration, the bracket draw, the games it books, and the
// score reports that move winners on. Every test gets 9 new players and builds its own tournament up to the stage
// it checks, through the API (createTournament, fillTournament, playQuarterfinals), so the tests don't depend on
// each other or on the order they run in.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

interface Player {
  id: string;
  team: number | null;
}

interface Match {
  round: number;
  position: number;
  team1: number | null;
  team2: number | null;
  winner: number | null;
  game: { id: string; status: string; startsAt: string } | null;
}

interface Details {
  status: string;
  winningTeam: number | null;
  players: Player[];
  matches: Match[];
}

const app = createApp();
const startsAt = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000);
let courtId: number;
let users: TestUser[] = [];
let tournamentIds: string[] = [];

const straightSetsForTeam1: [number, number][] = [
  [6, 3],
  [6, 4],
];
const threeSetsForTeam2: [number, number][] = [
  [6, 4],
  [3, 6],
  [2, 6],
];

const user = (index: number) => {
  const found = users.at(index);
  if (found === undefined) throw new Error(`No test user ${index}`);
  return found;
};

/** Fetches a tournament through the API */
function fetchDetails(tournamentId: string): Promise<Details> {
  return request(app)
    .get("/tournaments")
    .set("Authorization", user(0).auth)
    .query({ tournamentId })
    .then((response) => {
      expect(response.status).toBe(200);
      return response.body.tournament as Details;
    });
}

/** Finds the match at a round and position of the bracket */
function matchAt(matches: Match[], round: number, position: number): Match {
  const match = matches.find((m) => m.round === round && m.position === position);
  if (match === undefined) {
    throw new Error(`No match at round ${round}, position ${position}`);
  }
  return match;
}

/** The auth header of a player on the team with the given seed */
function authOfSeed(players: Player[], seed: number | null): string {
  const player = players.find(({ team }) => team === seed);
  const found = users.find(({ id }) => id === player?.id);
  if (found === undefined) {
    throw new Error(`No test user on seed ${String(seed)}`);
  }
  return found.auth;
}

/**
 * Starts a bracket match, reports its score as its team 1 player, and confirms it as its team 2 player, which
 * completes it (the tournament is singles). Only a started game can be scored; a match started earlier answers the
 * start with 409, which is fine here
 * @returns the response to the confirmation, the one that completes the game, or to the report if it failed. A
 * promise chain rather than an async helper: a failed request rejects it, which fails the test awaiting it
 */
function reportMatch(players: Player[], match: Match, rounds: [number, number][]): Promise<request.Response> {
  const reporter = authOfSeed(players, match.team1);
  const body = { gameId: match.game?.id, rounds };
  const confirm = () =>
    request(app).post("/games/score/confirm").set("Authorization", authOfSeed(players, match.team2)).send(body);
  return request(app)
    .post("/games/start")
    .set("Authorization", reporter)
    .send({ gameId: match.game?.id })
    .then(() => request(app).post("/games/score").set("Authorization", reporter).send(body))
    .then((reported) => (reported.status === 200 ? confirm() : reported));
}

const join = (joiner: TestUser, tournamentId: string) =>
  request(app).post("/tournaments/join").set("Authorization", joiner.auth).send({ tournamentId });

/**
 * Creates a tournament starting in 2 days at the test court
 * @returns its ID and name
 */
function createTournament(creator = user(0), format: Format = Format.SINGLES) {
  const name = `Test ${format} ${randomUUID().slice(0, 8)}`;
  return request(app)
    .post("/tournaments")
    .set("Authorization", creator.auth)
    .send({ name, format, startsAt: startsAt.toISOString(), courtId })
    .then((created) => {
      expect(created.status).toBe(201);
      const id = (created.body as { tournament: { id: string } }).tournament.id;
      tournamentIds.push(id);
      return { id, name };
    });
}

/** Players 2-8 join the tournament one after another, which fills it and draws the bracket */
function fillTournament(tournamentId: string, next = 1): Promise<Details> {
  if (next === 8) return fetchDetails(tournamentId);
  return join(user(next), tournamentId).then((joined) => {
    expect(joined.status).toBe(204);
    return fillTournament(tournamentId, next + 1);
  });
}

/** A full singles tournament whose bracket is drawn, created by player 1 */
const drawnTournament = () =>
  createTournament().then(({ id, name }) => fillTournament(id).then((details) => ({ id, name, details })));

/**
 * Plays the quarterfinals: the two feeding semifinal 0 at the same time (seeds 1 and 4 win), then seed 6 beats
 * seed 3 in three sets and seed 2 beats seed 7
 * @returns the responses to the four reports, in bracket order
 */
function playQuarterfinals({ players, matches }: Details) {
  const [qf0, qf1, qf2, qf3] = [0, 1, 2, 3].map((position) => matchAt(matches, 1, position)) as [
    Match,
    Match,
    Match,
    Match,
  ];
  return Promise.all([
    reportMatch(players, qf0, straightSetsForTeam1),
    reportMatch(players, qf1, straightSetsForTeam1),
  ]).then((concurrent) =>
    reportMatch(players, qf2, threeSetsForTeam2).then((third) =>
      reportMatch(players, qf3, straightSetsForTeam1).then((fourth) => [...concurrent, third, fourth]),
    ),
  );
}

beforeAll(async () => {
  // The forecast is not under test; without a key the feed skips it instead of calling OpenWeather
  delete process.env.OPENWEATHER_API_KEY;
  courtId = (await prisma.court.findFirstOrThrow({ select: { id: true } })).id;
});

beforeEach(async () => {
  // 9 players rated 2000, 1990, ..., 1920: 8 fill a singles bracket and the 9th arrives too late
  users = await createTestUsers("tournament", 9, (index) => 2000 - index * 10);
  tournamentIds = [];
});

afterEach(async () => {
  // Deleting a tournament deletes its players and bracket; its games and the users are deleted after
  await prisma.tournament.deleteMany({ where: { id: { in: tournamentIds } } });
  await deleteTestUsers(users);
});

afterAll(async () => {
  await prisma.$disconnect();
});

describe("Unmocked: tournament flow", () => {
  // Interface POST /tournaments, GET /tournaments/feed
  // Input: player 1 creates a singles tournament
  // Expected status code: 201, then 200
  // Expected behavior: the creator is registered, and the tournament is on the feed with 1 of 8 players
  // Expected output: the tournament on the feed, with no forecast
  test("Lists a new tournament on the feed with its creator registered", async () => {
    const { id } = await createTournament();

    const feed = await request(app).get("/tournaments/feed").set("Authorization", user(1).auth);

    expect(feed.status).toBe(200);
    const listed = feed.body.tournaments.find((tournament: { id: string }) => tournament.id === id);
    expect(listed).toMatchObject({
      status: TournamentStatus.REGISTRATION,
      capacity: 8,
      forecast: null,
      players: [{ id: user(0).id, team: null }],
    });
  });

  // Interface POST /tournaments/join, POST /tournaments/leave
  // Input: players 2-6 join, the creator and player 6 try to leave, player 2 joins twice
  // Expected status code: 204 for each join and for player 6 leaving; 409 for the creator and the second join
  // Expected behavior: 6 players are registered
  test("Registers players, lets them leave, and keeps the creator in", async () => {
    const { id } = await createTournament();
    for (const joiner of users.slice(1, 6)) {
      expect((await join(joiner, id)).status).toBe(204);
    }

    const creatorLeaves = await request(app)
      .post("/tournaments/leave")
      .set("Authorization", user(0).auth)
      .send({ tournamentId: id });
    expect(creatorLeaves.status).toBe(409);
    expect(creatorLeaves.body).toEqual(errorBody(tournamentErrors.CreatorCannotLeave));

    const playerLeaves = await request(app)
      .post("/tournaments/leave")
      .set("Authorization", user(5).auth)
      .send({ tournamentId: id });
    expect(playerLeaves.status).toBe(204);
    expect((await join(user(5), id)).status).toBe(204);

    const again = await join(user(1), id);
    expect(again.status).toBe(409);
    expect(again.body).toEqual(errorBody(tournamentErrors.AlreadyRegistered));

    expect(await prisma.tournamentPlayer.count({ where: { tournamentId: id } })).toBe(6);
  });

  // Interface POST /tournaments/join
  // Input: players 2-7 join, then players 8 and 9 race for the last spot
  // Expected status code: exactly one 204 and one 409
  // Expected behavior: the tournament never has more than 8 players, and the winner's registration draws the bracket
  // Expected output: { error: "Tournament is full", code: "TOURNAMENT_FULL" } for the player who lost the race
  test("Gives the last spot to exactly one of two players joining at once", async () => {
    const { id } = await createTournament();
    for (const joiner of users.slice(1, 7)) {
      expect((await join(joiner, id)).status).toBe(204);
    }

    const race = await Promise.all([join(user(7), id), join(user(8), id)]);

    expect(race.map(({ status }) => status).sort()).toEqual([204, 409]);
    expect(race.find(({ status }) => status === 409)?.body).toEqual(errorBody(tournamentErrors.Full));
    expect(await prisma.tournamentPlayer.count({ where: { tournamentId: id } })).toBe(8);
  });

  // Interface GET /tournaments, GET /games/feed, GET /games/me, GET /tournaments/feed
  // Input: players 2-8 fill player 1's tournament
  // Expected status code: 200
  // Expected behavior: teams are seeded by rating, 4 quarterfinals (1v8, 4v5, 3v6, 2v7) are booked as tournament
  // games, they never appear on the games feed, and the tournament leaves the tournament feed
  // Expected output: the drawn bracket, and each player's quarterfinal labelled in their own games
  test("Draws the bracket into booked tournament games that stay off the games feed", async () => {
    const { id, name, details } = await drawnTournament();
    expect(details.status).toBe(TournamentStatus.IN_PROGRESS);

    // Seeds follow the ratings: the creator (2000) is seed 1
    const seeded = [...details.players].sort((a, b) => (a.team ?? 0) - (b.team ?? 0));
    expect(seeded.map(({ team }) => team)).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    expect(seeded[0]?.id).toBe(user(0).id);
    const ratings = await prisma.user.findMany({
      where: { id: { in: seeded.map((player) => player.id) } },
      select: { id: true, rating: true },
    });
    const ratingOf = new Map(ratings.map((rated) => [rated.id, rated.rating]));
    const seededRatings = seeded.map((player) => ratingOf.get(player.id) ?? 0);
    expect(seededRatings).toEqual([...seededRatings].sort((a, b) => b - a));

    expect(details.matches).toHaveLength(7);
    const quarterfinals = details.matches.filter(({ round }) => round === 1);
    expect(quarterfinals.map(({ team1, team2 }) => [team1, team2])).toEqual([[1, 8], [4, 5], [3, 6], [2, 7]]);
    expect(details.matches.filter(({ game }) => game !== null)).toHaveLength(4);

    const gameIds = quarterfinals.map(({ game }) => game?.id ?? "");
    const games = await prisma.game.findMany({ where: { id: { in: gameIds } } });
    expect(games).toHaveLength(4);
    for (const game of games) {
      expect(game).toMatchObject({
        gameSource: GameSource.TOURNAMENT,
        status: GameStatus.BOOKED,
        courtId,
        startsAt,
      });
    }

    const gamesFeed = await request(app).get("/games/feed").set("Authorization", user(8).auth);
    expect(gamesFeed.status).toBe(200);
    const feedGames = gamesFeed.body.games as { id: string; gameSource: string }[];
    const feedIds = feedGames.map((game) => game.id);
    for (const gameId of gameIds) {
      expect(feedIds).not.toContain(gameId);
    }
    expect(feedGames.map(({ gameSource }) => gameSource)).not.toContain(GameSource.TOURNAMENT);

    const tournamentFeed = await request(app).get("/tournaments/feed").set("Authorization", user(8).auth);
    expect(tournamentFeed.body.tournaments.map((listed: { id: string }) => listed.id)).not.toContain(id);

    const myGames = await request(app).get("/games/me").set("Authorization", user(0).auth);
    expect(myGames.body.games).toContainEqual(
      expect.objectContaining({
        id: quarterfinals[0]?.game?.id,
        gameSource: GameSource.TOURNAMENT,
        tournament: { id, name, round: 1 },
      }),
    );
  });

  // Interface POST /games/join, POST /games/leave, DELETE /games, POST /tournaments/leave
  // Input: a player outside the bracket joins a quarterfinal; a quarterfinal player leaves and deletes it
  // Expected status code: 409 for each
  // Expected behavior: the bracket's games and players are unchanged
  test("Keeps the bracket's games and players fixed", async () => {
    const { id, details } = await drawnTournament();
    const gameId = matchAt(details.matches, 1, 0).game?.id ?? "";
    const playerAuth = authOfSeed(details.players, 1);

    const joinGame = await request(app)
      .post("/games/join")
      .set("Authorization", user(8).auth)
      .send({ gameId, team: 1 });
    expect(joinGame.status).toBe(409);

    const leaveGame = await request(app).post("/games/leave").set("Authorization", playerAuth).send({ gameId });
    expect(leaveGame.status).toBe(409);
    expect(leaveGame.body).toEqual(errorBody(gameErrors.TournamentNotLeavable));

    const deleteGame = await request(app).delete("/games").set("Authorization", playerAuth).query({ gameId });
    expect(deleteGame.status).toBe(409);
    expect(deleteGame.body).toEqual(errorBody(gameErrors.TournamentNotCancellable));

    const leaveTournament = await request(app)
      .post("/tournaments/leave")
      .set("Authorization", playerAuth)
      .send({ tournamentId: id });
    expect(leaveTournament.status).toBe(409);

    expect(await prisma.gameUser.count({ where: { gameId } })).toBe(2);
  });

  // Interface POST /games/start
  // Input: a player outside the bracket starts a quarterfinal, then one of its players starts it twice
  // Expected status code: 403, then 200, then 409
  // Expected behavior: only the quarterfinal's start time moves to now; the other quarterfinals keep the
  // tournament's start
  // Expected output: the started game, then { error: "Game has already started", code: "GAME_ALREADY_STARTED" }
  test("Lets a quarterfinal's players start it early without moving the rest of the bracket", async () => {
    const { id, details } = await drawnTournament();
    const quarterfinal = matchAt(details.matches, 1, 0);
    const gameId = quarterfinal.game?.id ?? "";
    const playerAuth = authOfSeed(details.players, quarterfinal.team1);
    const startGame = (auth: string) =>
      request(app).post("/games/start").set("Authorization", auth).send({ gameId });

    const byOutsider = await startGame(user(8).auth);
    expect(byOutsider.status).toBe(403);

    const before = Date.now();
    const started = await startGame(playerAuth);
    expect(started.status).toBe(200);
    expect(started.body.game).toMatchObject({ id: gameId, status: GameStatus.IN_PROGRESS });
    const { startsAt: startedAtIso } = started.body.game as { startsAt: string };
    const startedAt = new Date(startedAtIso).getTime();
    expect(startedAt).toBeGreaterThanOrEqual(before);
    expect(startedAt).toBeLessThanOrEqual(Date.now());

    const again = await startGame(playerAuth);
    expect(again.status).toBe(409);
    expect(again.body).toEqual(errorBody(gameErrors.AlreadyStarted));

    const others = await prisma.game.findMany({
      where: { tournamentMatch: { tournamentId: id, round: 1 }, id: { not: gameId } },
      select: { startsAt: true },
    });
    expect(others).toHaveLength(3);
    expect(others.every((game) => game.startsAt.getTime() === startsAt.getTime())).toBe(true);
  });

  // Interface POST /games/score
  // Input: the two quarterfinals feeding semifinal 0 are reported at the same time; then the other two. Each is
  // started first, so they all start before the tournament's start time
  // Expected status code: 200 for each report
  // Expected behavior: ratings change, and each semifinal is booked exactly once, an hour after the tournament's
  // start (not the quarterfinals' early starts), with the winners on the right sides (1 v 4 and 6 v 2)
  test("Books each semifinal once, even when both of its quarterfinals are reported at once", async () => {
    const { id, details } = await drawnTournament();

    const reports = await playQuarterfinals(details);

    expect(reports.map(({ status }) => status)).toEqual([200, 200, 200, 200]);
    expect(reports[0].body.ratingChanges).toHaveLength(2);
    const after = await fetchDetails(id);
    const semifinal0 = matchAt(after.matches, 2, 0);
    const semifinal1 = matchAt(after.matches, 2, 1);
    expect([semifinal0.team1, semifinal0.team2]).toEqual([1, 4]);
    expect([semifinal1.team1, semifinal1.team2]).toEqual([6, 2]);
    expect(semifinal0.game).toMatchObject({
      status: GameStatus.BOOKED,
      startsAt: new Date(startsAt.getTime() + ROUND_INTERVAL_MS).toISOString(),
    });

    const semifinalGames = await prisma.game.count({
      where: {
        gameSource: GameSource.TOURNAMENT,
        tournamentMatch: { tournamentId: id, round: 2 },
      },
    });
    expect(semifinalGames).toBe(2);

    const creator = await prisma.user.findUniqueOrThrow({ where: { id: user(0).id } });
    expect(creator.rating).not.toBe(2000);
  });

  // Interface POST /games/score, GET /tournaments/me
  // Input: after the quarterfinals, both semifinals are reported (seeds 1 and 2 win), then the final, won by seed 2
  // Expected status code: 200
  // Expected behavior: the final is booked two hours after the start, and reporting it completes the tournament
  // Expected output: the completed tournament with seed 2 as the champion, still in the creator's tournaments
  test("Crowns the champion when the final is reported", async () => {
    const { id, details } = await drawnTournament();
    await playQuarterfinals(details);
    const semifinals = await fetchDetails(id);

    expect((await reportMatch(semifinals.players, matchAt(semifinals.matches, 2, 0), straightSetsForTeam1)).status)
      .toBe(200);
    expect((await reportMatch(semifinals.players, matchAt(semifinals.matches, 2, 1), threeSetsForTeam2)).status)
      .toBe(200);

    const beforeFinal = await fetchDetails(id);
    const final = matchAt(beforeFinal.matches, 3, 0);
    expect([final.team1, final.team2]).toEqual([1, 2]);
    expect(final.game?.startsAt).toBe(new Date(startsAt.getTime() + 2 * ROUND_INTERVAL_MS).toISOString());

    expect((await reportMatch(beforeFinal.players, final, threeSetsForTeam2)).status).toBe(200);

    const completed = await fetchDetails(id);
    expect(completed.status).toBe(TournamentStatus.COMPLETED);
    expect(completed.winningTeam).toBe(2);
    expect(completed.matches.every(({ winner }) => winner !== null)).toBe(true);

    const mine = await request(app).get("/tournaments/me").set("Authorization", user(0).auth);
    expect(mine.body.tournaments).toContainEqual(
      expect.objectContaining({ id, status: TournamentStatus.COMPLETED, winningTeam: 2 }),
    );
  });

  // Interface DELETE /tournaments
  // Input: a doubles tournament cancelled first by a player who did not create it, then by its creator
  // Expected status code: 403, then 204
  // Expected behavior: the tournament is marked as cancelled, leaves the feed and can no longer be joined
  test("Lets only the creator cancel a tournament in registration", async () => {
    const { id } = await createTournament(user(1), Format.DOUBLES);
    expect((await join(user(2), id)).status).toBe(204);

    const byPlayer = await request(app)
      .delete("/tournaments")
      .set("Authorization", user(2).auth)
      .query({ tournamentId: id });
    expect(byPlayer.status).toBe(403);

    const byCreator = await request(app)
      .delete("/tournaments")
      .set("Authorization", user(1).auth)
      .query({ tournamentId: id });
    expect(byCreator.status).toBe(204);

    const feed = await request(app).get("/tournaments/feed").set("Authorization", user(3).auth);
    expect(feed.body.tournaments.map((listed: { id: string }) => listed.id)).not.toContain(id);
    const lateJoin = await join(user(3), id);
    expect(lateJoin.status).toBe(409);
    expect(lateJoin.body).toEqual(errorBody(tournamentErrors.NotOpen));
  });
});
