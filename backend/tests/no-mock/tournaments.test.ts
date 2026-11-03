import { randomUUID } from "node:crypto";
import request from "supertest";
import { createApp } from "../../src/app";
import { signAccessToken } from "../../src/auth/tokens";
import { prisma } from "../../src/db";
import {
  Format,
  GameSource,
  GameStatus,
  TournamentStatus,
} from "../../src/generated/prisma/enums";
import { ROUND_INTERVAL_MS } from "../../src/tournaments/bracket";

// Runs the whole tournament flow against a real database: registration, the bracket draw, the games it books,
// and the score reports that move winners on. The tests in this file run in order and share one tournament.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

interface TestUser {
  id: string;
  auth: string;
}

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

// Unique per run, so the test never collides with existing users
const run = randomUUID().slice(0, 8);
const app = createApp();
const users: TestUser[] = [];
const tournamentIds: string[] = [];
let courtId: number;
let tournamentId: string;
const startsAt = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000);

const straightSetsForTeam1: [number, number][] = [
  [6, 3],
  [6, 4],
];
const threeSetsForTeam2: [number, number][] = [
  [6, 4],
  [3, 6],
  [2, 6],
];

/** Fetches the shared tournament through the API */
async function fetchDetails() {
  const response = await request(app)
    .get("/tournaments")
    .set("Authorization", users[0]?.auth ?? "")
    .query({ tournamentId });
  expect(response.status).toBe(200);
  return response.body.tournament as {
    status: string;
    winningTeam: number | null;
    players: Player[];
    matches: Match[];
  };
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
  const user = users.find(({ id }) => id === player?.id);
  if (user === undefined) {
    throw new Error(`No test user on seed ${String(seed)}`);
  }
  return user.auth;
}

/** Reports the score of a bracket match as one of its own players */
async function reportMatch(players: Player[], match: Match, rounds: [number, number][]) {
  return await request(app)
    .post("/games/score")
    .set("Authorization", authOfSeed(players, match.team1))
    .send({ gameId: match.game?.id, rounds });
}

const join = (user: TestUser | undefined, id = tournamentId) =>
  request(app)
    .post("/tournaments/join")
    .set("Authorization", user?.auth ?? "")
    .send({ tournamentId: id });

beforeAll(async () => {
  // The forecast is not under test; without a key the feed skips it instead of calling OpenWeather
  delete process.env.OPENWEATHER_API_KEY;

  const court = await prisma.court.findFirstOrThrow({ select: { id: true } });
  courtId = court.id;

  // 9 players rated 2000, 1990, ..., 1920: 8 fill a singles bracket and the 9th arrives too late
  for (let index = 0; index < 9; index++) {
    const user = await prisma.user.create({
      data: {
        googleSub: `tournament-test-${run}-${index}`,
        username: `tournament-${run}-${index}@example.com`,
        name: `Player ${index + 1}`,
        email: `tournament-${run}-${index}@example.com`,
        rating: 2000 - index * 10,
      },
    });
    users.push({ id: user.id, auth: `Bearer ${signAccessToken(user.id)}` });
  }
});

afterAll(async () => {
  const userIds = users.map(({ id }) => id);
  // Deleting a tournament deletes its players and bracket; its games and the users are deleted after
  await prisma.tournament.deleteMany({ where: { id: { in: tournamentIds } } });
  const games = await prisma.game.findMany({
    where: { players: { some: { userId: { in: userIds } } } },
    select: { id: true },
  });
  const gameIds = games.map(({ id }) => id);
  await prisma.gameRound.deleteMany({ where: { gameId: { in: gameIds } } });
  await prisma.gameUser.deleteMany({ where: { gameId: { in: gameIds } } });
  await prisma.game.deleteMany({ where: { id: { in: gameIds } } });
  await prisma.user.deleteMany({ where: { id: { in: userIds } } });
  await prisma.$disconnect();
});

describe("Unmocked: tournament flow", () => {
  // Interface POST /tournaments, GET /tournaments/feed
  // Input: player 1 creates a singles tournament
  // Expected status code: 201, then 200
  // Expected behavior: the creator is registered, and the tournament is on the feed with 1 of 8 players
  // Expected output: the tournament on the feed, with no forecast
  test("Lists a new tournament on the feed with its creator registered", async () => {
    const created = await request(app)
      .post("/tournaments")
      .set("Authorization", users[0]?.auth ?? "")
      .send({ name: `Test Open ${run}`, format: Format.SINGLES, startsAt: startsAt.toISOString(), courtId });
    expect(created.status).toBe(201);
    tournamentId = created.body.tournament.id;
    tournamentIds.push(tournamentId);

    const feed = await request(app).get("/tournaments/feed").set("Authorization", users[1]?.auth ?? "");

    expect(feed.status).toBe(200);
    const listed = feed.body.tournaments.find(({ id }: { id: string }) => id === tournamentId);
    expect(listed).toMatchObject({
      status: TournamentStatus.REGISTRATION,
      capacity: 8,
      forecast: null,
      players: [{ id: users[0]?.id, team: null }],
    });
  });

  // Interface POST /tournaments/join, POST /tournaments/leave
  // Input: players 2-6 join, the creator and player 6 try to leave, player 2 joins twice
  // Expected status code: 204 for each join and for player 6 leaving; 409 for the creator and the second join
  // Expected behavior: 6 players are registered
  test("Registers players, lets them leave, and keeps the creator in", async () => {
    for (const user of users.slice(1, 6)) {
      expect((await join(user)).status).toBe(204);
    }

    const creatorLeaves = await request(app)
      .post("/tournaments/leave")
      .set("Authorization", users[0]?.auth ?? "")
      .send({ tournamentId });
    expect(creatorLeaves.status).toBe(409);
    expect(creatorLeaves.body).toEqual({
      error: "The creator cannot leave the tournament, cancel it instead",
    });

    const playerLeaves = await request(app)
      .post("/tournaments/leave")
      .set("Authorization", users[5]?.auth ?? "")
      .send({ tournamentId });
    expect(playerLeaves.status).toBe(204);
    expect((await join(users[5])).status).toBe(204);

    const again = await join(users[1]);
    expect(again.status).toBe(409);
    expect(again.body).toEqual({ error: "User already registered" });

    expect(await prisma.tournamentPlayer.count({ where: { tournamentId } })).toBe(6);
  });

  // Interface POST /tournaments/join
  // Input: player 7 joins, then players 8 and 9 race for the last spot
  // Expected status code: exactly one 204 and one 409
  // Expected behavior: the tournament never has more than 8 players, and the winner's registration draws the bracket
  // Expected output: { error: "Tournament is full" } for the player who lost the race
  test("Gives the last spot to exactly one of two players joining at once", async () => {
    expect((await join(users[6])).status).toBe(204);

    const race = await Promise.all([join(users[7]), join(users[8])]);

    expect(race.map(({ status }) => status).sort()).toEqual([204, 409]);
    expect(race.find(({ status }) => status === 409)?.body).toEqual({ error: "Tournament is full" });
    expect(await prisma.tournamentPlayer.count({ where: { tournamentId } })).toBe(8);
  });

  // Interface GET /tournaments, GET /games/feed, GET /games/me, GET /tournaments/feed
  // Input: the full tournament
  // Expected status code: 200
  // Expected behavior: teams are seeded by rating, 4 quarterfinals (1v8, 4v5, 3v6, 2v7) are booked as tournament
  // games, they never appear on the games feed, and the tournament leaves the tournament feed
  // Expected output: the drawn bracket, and each player's quarterfinal labelled in their own games
  test("Draws the bracket into booked tournament games that stay off the games feed", async () => {
    const details = await fetchDetails();
    expect(details.status).toBe(TournamentStatus.IN_PROGRESS);

    // Seeds follow the ratings: the creator (2000) is seed 1
    const seeded = [...details.players].sort((a, b) => (a.team ?? 0) - (b.team ?? 0));
    expect(seeded.map(({ team }) => team)).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    expect(seeded[0]?.id).toBe(users[0]?.id);
    const ratings = await prisma.user.findMany({
      where: { id: { in: seeded.map(({ id }) => id) } },
      select: { id: true, rating: true },
    });
    const ratingOf = new Map(ratings.map(({ id, rating }) => [id, rating]));
    const seededRatings = seeded.map(({ id }) => ratingOf.get(id) ?? 0);
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

    const gamesFeed = await request(app).get("/games/feed").set("Authorization", users[8]?.auth ?? "");
    expect(gamesFeed.status).toBe(200);
    const feedGames = gamesFeed.body.games as { id: string; gameSource: string }[];
    const feedIds = feedGames.map(({ id }) => id);
    for (const gameId of gameIds) {
      expect(feedIds).not.toContain(gameId);
    }
    expect(feedGames.map(({ gameSource }) => gameSource)).not.toContain(GameSource.TOURNAMENT);

    const tournamentFeed = await request(app).get("/tournaments/feed").set("Authorization", users[8]?.auth ?? "");
    expect(tournamentFeed.body.tournaments.map(({ id }: { id: string }) => id)).not.toContain(tournamentId);

    const myGames = await request(app).get("/games/me").set("Authorization", users[0]?.auth ?? "");
    expect(myGames.body.games).toContainEqual(
      expect.objectContaining({
        id: quarterfinals[0]?.game?.id,
        gameSource: GameSource.TOURNAMENT,
        tournament: { id: tournamentId, name: `Test Open ${run}`, round: 1 },
      }),
    );
  });

  // Interface POST /games/join, POST /games/leave, DELETE /games, POST /tournaments/leave
  // Input: a player outside the bracket joins a quarterfinal; a quarterfinal player leaves and deletes it
  // Expected status code: 409 for each
  // Expected behavior: the bracket's games and players are unchanged
  test("Keeps the bracket's games and players fixed", async () => {
    const details = await fetchDetails();
    const gameId = matchAt(details.matches, 1, 0).game?.id ?? "";
    const outsider = users.find(({ id }) => !details.players.some((player) => player.id === id));
    const playerAuth = authOfSeed(details.players, 1);

    const joinGame = await request(app)
      .post("/games/join")
      .set("Authorization", outsider?.auth ?? "")
      .send({ gameId, team: 1 });
    expect(joinGame.status).toBe(409);

    const leaveGame = await request(app).post("/games/leave").set("Authorization", playerAuth).send({ gameId });
    expect(leaveGame.status).toBe(409);
    expect(leaveGame.body).toEqual({ error: "Players cannot leave a tournament game" });

    const deleteGame = await request(app).delete("/games").set("Authorization", playerAuth).query({ gameId });
    expect(deleteGame.status).toBe(409);
    expect(deleteGame.body).toEqual({ error: "Tournament games cannot be cancelled" });

    const leaveTournament = await request(app)
      .post("/tournaments/leave")
      .set("Authorization", playerAuth)
      .send({ tournamentId });
    expect(leaveTournament.status).toBe(409);

    expect(await prisma.gameUser.count({ where: { gameId } })).toBe(2);
  });

  // Interface POST /games/score
  // Input: the two quarterfinals feeding semifinal 0 are reported at the same time; then the other two
  // Expected status code: 200 for each report
  // Expected behavior: ratings change, and each semifinal is booked exactly once, an hour after the start, with
  // the winners on the right sides (1 v 4 and 6 v 2)
  test("Books each semifinal once, even when both of its quarterfinals are reported at once", async () => {
    const details = await fetchDetails();
    const [qf0, qf1, qf2, qf3] = [0, 1, 2, 3].map((position) => matchAt(details.matches, 1, position));
    if (!qf0 || !qf1 || !qf2 || !qf3) {
      throw new Error("Missing quarterfinal");
    }

    const concurrent = await Promise.all([
      reportMatch(details.players, qf0, straightSetsForTeam1),
      reportMatch(details.players, qf1, straightSetsForTeam1),
    ]);
    expect(concurrent.map(({ status }) => status)).toEqual([200, 200]);
    expect(concurrent[0].body.ratingChanges).toHaveLength(2);

    expect((await reportMatch(details.players, qf2, threeSetsForTeam2)).status).toBe(200);
    expect((await reportMatch(details.players, qf3, straightSetsForTeam1)).status).toBe(200);

    const after = await fetchDetails();
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
        tournamentMatch: { tournamentId, round: 2 },
      },
    });
    expect(semifinalGames).toBe(2);

    const creator = await prisma.user.findUniqueOrThrow({ where: { id: users[0]?.id ?? "" } });
    expect(creator.rating).not.toBe(2000);
  });

  // Interface POST /games/score, GET /tournaments/me
  // Input: both semifinals are reported (seeds 1 and 2 win), then the final, won by seed 2
  // Expected status code: 200
  // Expected behavior: the final is booked two hours after the start, and reporting it completes the tournament
  // Expected output: the completed tournament with seed 2 as the champion, still in the creator's tournaments
  test("Crowns the champion when the final is reported", async () => {
    const details = await fetchDetails();
    expect((await reportMatch(details.players, matchAt(details.matches, 2, 0), straightSetsForTeam1)).status).toBe(200);
    expect((await reportMatch(details.players, matchAt(details.matches, 2, 1), threeSetsForTeam2)).status).toBe(200);

    const beforeFinal = await fetchDetails();
    const final = matchAt(beforeFinal.matches, 3, 0);
    expect([final.team1, final.team2]).toEqual([1, 2]);
    expect(final.game?.startsAt).toBe(new Date(startsAt.getTime() + 2 * ROUND_INTERVAL_MS).toISOString());

    expect((await reportMatch(beforeFinal.players, final, threeSetsForTeam2)).status).toBe(200);

    const completed = await fetchDetails();
    expect(completed.status).toBe(TournamentStatus.COMPLETED);
    expect(completed.winningTeam).toBe(2);
    expect(completed.matches.every(({ winner }) => winner !== null)).toBe(true);

    const mine = await request(app).get("/tournaments/me").set("Authorization", users[0]?.auth ?? "");
    expect(mine.body.tournaments).toContainEqual(
      expect.objectContaining({ id: tournamentId, status: TournamentStatus.COMPLETED, winningTeam: 2 }),
    );
  });

  // Interface DELETE /tournaments
  // Input: a doubles tournament cancelled first by a player who did not create it, then by its creator
  // Expected status code: 403, then 204
  // Expected behavior: the tournament is marked as cancelled, leaves the feed and can no longer be joined
  test("Lets only the creator cancel a tournament in registration", async () => {
    const created = await request(app)
      .post("/tournaments")
      .set("Authorization", users[1]?.auth ?? "")
      .send({ name: `Test Doubles ${run}`, format: Format.DOUBLES, startsAt: startsAt.toISOString(), courtId });
    expect(created.status).toBe(201);
    const doublesId: string = created.body.tournament.id;
    tournamentIds.push(doublesId);
    expect((await join(users[2], doublesId)).status).toBe(204);

    const byPlayer = await request(app)
      .delete("/tournaments")
      .set("Authorization", users[2]?.auth ?? "")
      .query({ tournamentId: doublesId });
    expect(byPlayer.status).toBe(403);

    const byCreator = await request(app)
      .delete("/tournaments")
      .set("Authorization", users[1]?.auth ?? "")
      .query({ tournamentId: doublesId });
    expect(byCreator.status).toBe(204);

    const feed = await request(app).get("/tournaments/feed").set("Authorization", users[3]?.auth ?? "");
    expect(feed.body.tournaments.map(({ id }: { id: string }) => id)).not.toContain(doublesId);
    const lateJoin = await join(users[3], doublesId);
    expect(lateJoin.status).toBe(409);
    expect(lateJoin.body).toEqual({ error: "Tournament is not open for registration" });
  });
});
