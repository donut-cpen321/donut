import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { Format, GameSource, GameStatus } from "../../src/generated/prisma/enums";
import { RatingChange } from "../../src/ranking/ranking.types";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// Reports two singles games between the same two new players against a real database, then reads their stats.
// Checks that games played are counted for the K-factor and that GET /stats summarizes the stored rating changes.
// Every test gets two new players and two games in progress, and plays the games it needs first (playFirstGame,
// playSecondGame), so the tests don't depend on each other or on the order they run in.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

// Response bodies, typed so their numbers aren't `any`; each test has exactly two players and two games
interface ScoreBody {
  ratingChanges: [RatingChange, RatingChange];
}

interface RatingPointBody {
  gameId: string;
  ratingBefore: number;
  ratingAfter: number;
}

interface StatsBody {
  stats: {
    user: { rating: number };
    ratingHistory: [RatingPointBody, RatingPointBody];
    vsOpponentRating: unknown[];
  };
}

type Rounds = [number, number][];

const app = createApp();
let users: TestUser[] = [];
let gameIds: string[] = [];

// Tennis K for a player with 0 and 1 rated games: 250 / (N + 5)^0.4
const kNew = 250 / 5 ** 0.4;
const kOneGame = 250 / 6 ** 0.4;

// Game 1: player 1 wins 6-4 6-3. Game 2: player 1 first reports a wrong score, then player 2 corrects it to their
// 6-2 6-4 win
const firstGame: Rounds = [
  [6, 4],
  [6, 3],
];
const wrongSecondGame: Rounds = [
  [6, 2],
  [6, 4],
];
const secondGame: Rounds = [
  [2, 6],
  [4, 6],
];

const auth = (index: number) => users.at(index)?.auth ?? "";

/**
 * Reports a game's score as the given player
 * @param playerIndex 0 for player 1 (team 1), 1 for player 2 (team 2)
 */
function report(gameIndex: number, playerIndex: number, rounds: Rounds) {
  return request(app)
    .post("/games/score")
    .set("Authorization", auth(playerIndex))
    .send({ gameId: gameIds.at(gameIndex), rounds });
}

/** Confirms the score waiting in a game as the given player */
function confirm(gameIndex: number, playerIndex: number, rounds: Rounds) {
  return request(app)
    .post("/games/score/confirm")
    .set("Authorization", auth(playerIndex))
    .send({ gameId: gameIds.at(gameIndex), rounds });
}

/** Player 1 reports game 1 and player 2 confirms it */
const playFirstGame = () => report(0, 0, firstGame).then(() => confirm(0, 1, firstGame));

/** Player 1 reports a wrong score for game 2, player 2 corrects it, and player 1 confirms the correction */
const playSecondGame = () =>
  report(1, 0, wrongSecondGame)
    .then(() => report(1, 1, secondGame))
    .then(() => confirm(1, 0, secondGame));

beforeEach(async () => {
  users = await createTestUsers("stats", 2);
  const [player1, player2] = users as [TestUser, TestUser];
  const court = await prisma.court.findFirstOrThrow({ select: { id: true } });

  // Two singles games in progress (only a started game can be scored), player 1 on team 1 and player 2 on
  // team 2, a day apart
  gameIds = [];
  for (let index = 0; index < 2; index++) {
    const game = await prisma.game.create({
      data: {
        format: Format.SINGLES,
        status: GameStatus.IN_PROGRESS,
        startsAt: new Date(Date.UTC(2026, 10, 10 + index, 17)),
        courtId: court.id,
        gameSource: GameSource.SCHEDULED,
        players: {
          create: [
            { userId: player1.id, team: 1 },
            { userId: player2.id, team: 2 },
          ],
        },
      },
    });
    gameIds.push(game.id);
  }
});

afterEach(async () => {
  await deleteTestUsers(users);
});

afterAll(async () => {
  await prisma.$disconnect();
});

describe("Unmocked: games played and player stats", () => {
  // Interface POST /games/score, GET /games
  // Input: player 1 reports 6-4 6-3
  // Expected status code: 200
  // Expected behavior: the score waits for player 2: the game stays in progress, with only player 1 agreeing
  // Expected output: { completed: false }, and the game shows the score waiting
  test("Keeps a reported score waiting until the other player confirms it", async () => {
    const response = await report(0, 0, firstGame);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ completed: false });

    const game = await request(app).get("/games").set("Authorization", auth(1)).query({ gameId: gameIds.at(0) });
    expect(game.body.game).toMatchObject({
      status: GameStatus.IN_PROGRESS,
      rounds: [],
      scoreProposal: {
        proposedById: users[0]?.id,
        rounds: [
          { number: 1, team1Score: 6, team2Score: 4 },
          { number: 2, team1Score: 6, team2Score: 3 },
        ],
      },
    });
    const confirmedBy = (game.body.game as { players: { id: string; scoreConfirmedAt: string | null }[] }).players
      .filter(({ scoreConfirmedAt }) => scoreConfirmedAt !== null)
      .map(({ id }) => id);
    expect(confirmedBy).toEqual([users[0]?.id]);
  });

  // Interface POST /games/score/confirm
  // Input: two new 1500-rated players, player 2 confirms player 1's 6-4 6-3 win
  // Expected status code: 200
  // Expected behavior: both players agree, so the game is completed; neither player has a rated game yet, so both
  // use the new-player K
  // Expected output: player 1 gains 131.33 * 0.5 + 1.5 * 5 = 73.16, player 2 loses the same
  test("Rates a first game with the new-player K once it is confirmed", async () => {
    await report(0, 0, firstGame);

    const response = await confirm(0, 1, firstGame);

    expect(response.status).toBe(200);
    expect(response.body.completed).toBe(true);
    const [first, second] = (response.body as ScoreBody).ratingChanges;
    expect(first.ratingAfter).toBeCloseTo(1500 + kNew * 0.5 + 7.5, 5);
    expect(second.ratingAfter).toBeCloseTo(1500 - (kNew * 0.5 + 7.5), 5);
  });

  // Interface POST /games/score, POST /games/score/confirm
  // Input: after game 1, the same players' second game: player 1 reports a wrong score, player 2 corrects it to their
  // 6-2 6-4 win, and player 1 confirms the correction. Confirming the replaced score is refused
  // Expected status code: 200 for each report and the confirmation, 409 for the stale confirmation
  // Expected behavior: the correction replaces the wrong score; the first game now counts, so both use the K for
  // 1 game played
  // Expected output: each rating moves by K(1) * (S - E) + 1.5 * (margin - expected margin)
  test("Rates a corrected second game with the K for one game played", async () => {
    await playFirstGame();
    expect((await report(1, 0, wrongSecondGame)).body).toEqual({ completed: false });
    expect((await report(1, 1, secondGame)).body).toEqual({ completed: false });

    const stale = await confirm(1, 0, wrongSecondGame);
    expect(stale.status).toBe(409);
    expect(stale.body.code).toBe("SCORE_CHANGED");

    const response = await confirm(1, 0, secondGame);

    expect(response.status).toBe(200);
    expect(response.body.completed).toBe(true);
    const [first, second] = (response.body as ScoreBody).ratingChanges;
    const gap = first.ratingBefore - second.ratingBefore;
    const expectedOutcome = 1 / (1 + 10 ** (-gap / 400));
    const expectedMargin = 6 * Math.tanh(0.0025 * gap);
    const marginTerm = 1.5 * (-6 - expectedMargin);
    expect(first.ratingAfter).toBeCloseTo(first.ratingBefore + kOneGame * (0 - expectedOutcome) + marginTerm, 5);
    expect(second.ratingAfter).toBeCloseTo(second.ratingBefore - (kOneGame * (0 - expectedOutcome) + marginTerm), 5);
  });

  // Interface GET /stats
  // Input: after both games, player 1 asks for their own stats
  // Expected status code: 200
  // Expected behavior: both rated games are summarized from player 1's side
  // Expected output: 2 games (1 win), 4 sets (2 won), 18-19 points, the rating history oldest first
  //        and the games in the bands of player 2's rating before each game
  test("Returns the caller's stats from their rated games", async () => {
    await playFirstGame();
    await playSecondGame();

    const response = await request(app).get("/stats").set("Authorization", auth(0));

    expect(response.status).toBe(200);
    const { stats } = response.body as StatsBody;
    const [firstPoint, secondPoint] = stats.ratingHistory;
    expect(stats).toMatchObject({
      user: { id: users[0]?.id },
      sport: "TENNIS",
      gamesPlayed: 2,
      wins: 1,
      losses: 1,
      setsPlayed: 4,
      setsWon: 2,
      setsLost: 2,
      pointsWon: 18,
      pointsLost: 19,
    });
    expect(stats.ratingHistory.map(({ gameId }) => gameId)).toEqual(gameIds);
    expect(firstPoint.ratingBefore).toBe(1500);
    expect(secondPoint.ratingBefore).toBeCloseTo(firstPoint.ratingAfter, 8);
    expect(stats.user.rating).toBeCloseTo(secondPoint.ratingAfter, 8);
    // Player 2 was 1500 before game 1 (band 1500) and about 1426.8 before game 2 (band 1400)
    expect(stats.vsOpponentRating).toEqual([
      { minRating: 1400, maxRating: 1500, games: 1, wins: 0, losses: 1, averageMargin: -6 },
      { minRating: 1500, maxRating: 1600, games: 1, wins: 1, losses: 0, averageMargin: 5 },
    ]);
  });

  // Interface GET /stats
  // Input: after both games, player 1 asks for player 2's stats
  // Expected status code: 200
  // Expected behavior: the same games from player 2's side
  // Expected output: 2 games, 1 win, 19-18 points
  test("Returns another player's stats", async () => {
    await playFirstGame();
    await playSecondGame();

    const response = await request(app)
      .get("/stats")
      .query({ userId: users[1]?.id })
      .set("Authorization", auth(0));

    expect(response.status).toBe(200);
    expect(response.body.stats).toMatchObject({
      user: { id: users[1]?.id },
      gamesPlayed: 2,
      wins: 1,
      pointsWon: 19,
      pointsLost: 18,
    });
  });
});
