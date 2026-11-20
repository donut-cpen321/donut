import request from "supertest";
import {
  createApp,
} from "../../src/app";
import {
  Prisma,
} from "../../src/generated/prisma/client";
import {
  Format,
  GameSource,
  GameStatus,
} from "../../src/generated/prisma/enums";
import {
  fetchGame,
  fetchGameDetails,
  isUserInGame,
  recordScore,
} from "../../src/games/games.repository";
import {
  confirmScore,
  gameErrors,
  reportScore,
} from "../../src/games/games.service";
import {
  pushToUsers,
} from "../../src/users/users.service";
import {
  commonErrors,
  errorBody,
} from "../../src/errors";
import {
  IncompleteTeamsError,
} from "../../src/ranking/ranking.service";
import * as changes from "../../src/events/changes";
import {
  userId,
  authHeader,
  gameDetails,
  editGameId,
  existingGame,
} from "./gamesFixtures";

// Mocked tests of reporting and confirming scores: POST /games/score, /games/score/confirm.
// The fixtures shared by every games.*.test.ts are in gamesFixtures.ts
jest.mock("../../src/games/games.repository", () => ({
  createGame: jest.fn(),
  fetchGame: jest.fn(),
  deleteGame: jest.fn(),
  isUserInGame: jest.fn(),
  deleteGameUser: jest.fn(),
  updateGame: jest.fn(),
  startBookedGame: jest.fn(),
  recordScore: jest.fn(),
  fetchGameDetails: jest.fn(),
  fetchFeedGames: jest.fn(),
  fetchUserGames: jest.fn(),
  fetchUserHistory: jest.fn(),
  addPlayerToTeam: jest.fn(),
  acceptMatch: jest.fn(),
  cancelMatchedGame: jest.fn(),
}));

jest.mock("../../src/external/weather", () => ({
  getForecast: jest.fn(),
}));

jest.mock("../../src/external/fcm", () => ({
  isPushEnabled: jest.fn(),
}));

jest.mock("../../src/friends/friends.repository", () => ({
  fetchFriendship: jest.fn(),
}));

jest.mock("../../src/users/users.service", () => ({
  ...jest.requireActual<typeof import("../../src/users/users.service")>(
    "../../src/users/users.service",
  ),
  pushToUsers: jest.fn(),
}));

const mockedPushToUsers = jest.mocked(pushToUsers);

const mockedFetchGame = jest.mocked(fetchGame);
const mockedFetchGameDetails = jest.mocked(fetchGameDetails);
const mockedIsUserInGame = jest.mocked(isUserInGame);
const mockedRecordScore = jest.mocked(recordScore);

describe.each([
  ["POST /games/score", "/games/score", false],
  ["POST /games/score/confirm", "/games/score/confirm", true],
] as const)("Mocked: %s", (_route, path, confirming) => {
  beforeEach(() => {
    jest.clearAllMocks();
    // Only a game in progress can be scored
    mockedFetchGame.mockResolvedValue(existingGame({ status: GameStatus.IN_PROGRESS }));
    mockedIsUserInGame.mockResolvedValue(true);
  });

  const rounds: [number, number][] = [
    [6, 4],
    [3, 6],
    [7, 5],
  ];
  const validScoreBody = () => ({ gameId: editGameId, rounds });
  const send = (body: object = validScoreBody()) =>
    request(createApp()).post(path).set("Authorization", authHeader).send(body);

  const ratingChanges = [
    { userId, ratingBefore: 1500, ratingAfter: 1512.5 },
    { userId: "other-user", ratingBefore: 1500, ratingAfter: 1487.5 },
  ];

  // Input: a valid score, while another player still has to agree with it
  // Expected status code: 200
  // Expected behavior: the player's agreement is recorded and the game is not completed yet
  // Expected output: { completed: false }
  test("Records the player's agreement and waits for the others", async () => {
    mockedRecordScore.mockResolvedValue({ outcome: confirming ? "confirmed" : "proposed" });

    const response = await send();

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ completed: false });
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, editGameId);
    expect(mockedRecordScore).toHaveBeenCalledWith(editGameId, userId, rounds, confirming, expect.any(Date));
  });

  // Input: a valid score, and this player is the last one to agree
  // Expected status code: 200
  // Expected behavior: the game is completed with the score
  // Expected output: { completed: true, game, rounds, ratingChanges }
  test("Returns the completed game with rating changes once every player agrees", async () => {
    const savedRounds = rounds.map(([team1Score, team2Score], index) => ({
      gameId: editGameId,
      number: index + 1,
      team1Score,
      team2Score,
    }));
    mockedRecordScore.mockResolvedValue({
      outcome: "completed",
      completed: { game: existingGame({ status: GameStatus.COMPLETED }), rounds: savedRounds, ratingChanges },
    });

    const response = await send();

    expect(response.status).toBe(200);
    expect(response.body.completed).toBe(true);
    expect(response.body.game).toMatchObject({ id: editGameId, status: GameStatus.COMPLETED });
    expect(response.body.rounds).toEqual(savedRounds);
    expect(response.body.ratingChanges).toEqual(ratingChanges);
  });

  test("Returns 409 when the teams are not full", async () => {
    mockedRecordScore.mockRejectedValue(new IncompleteTeamsError(Format.SINGLES, 1, 0));

    const response = await send();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.IncompleteTeams));
  });

  // Input: the game was completed or cancelled after the first check, by another player at the same moment
  // Expected status code: 409
  // Expected output: GAME_CLOSED
  test("Returns 409 when the game was closed in the meantime", async () => {
    mockedRecordScore.mockResolvedValue({ outcome: "notInProgress" });

    const response = await send();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.Closed));
  });

  test.each([
    ["missing gameId", { gameId: undefined }],
    ["non-UUID gameId", { gameId: "not-a-uuid" }],
    ["missing rounds", { rounds: undefined }],
    ["rounds that are not an array", { rounds: { team1Score: 6, team2Score: 4 } }],
    [
      "a negative score",
      {
        rounds: [
          [6, 4],
          [-1, 6],
          [6, 2],
        ],
      },
    ],
    ["a non-integer score", { rounds: [[6.5, 4]] }],
    ["a string score", { rounds: [["6", "4"]] }],
    ["a round with one score", { rounds: [[6]] }],
    ["a round with three scores", { rounds: [[6, 4, 2]] }],
    ["userId in the body", { userId: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d" }],
    ["unknown field", { status: GameStatus.COMPLETED }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await send({ ...validScoreBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedFetchGame).not.toHaveBeenCalled();
    expect(mockedRecordScore).not.toHaveBeenCalled();
  });

  test.each([
    ["no rounds", [], "must have at least one round"],
    [
      "a tied round",
      [
        [6, 4],
        [5, 5],
        [6, 2],
      ],
      "round 2 is tied",
    ],
    [
      "no winner",
      [
        [6, 4],
        [4, 6],
      ],
      "must have a winner",
    ],
    // The other group's review: a 99-0 blowout moved ratings by about 440 points
    ["a 99-0 set", [[99, 0], [6, 4]], "round 1: past 6 games a set is won by 2 games, or 7-6"],
    ["a set won 9-3", [[6, 4], [9, 3]], "round 2: past 6 games a set is won by 2 games, or 7-6"],
    ["a set won 8-5", [[5, 8], [6, 4], [8, 5]], "round 1: past 6 games a set is won by 2 games, or 7-6"],
  ])("Rejects a score with %s", async (_description, badRounds, message) => {
    const response = await send({ gameId: editGameId, rounds: badRounds });

    expect(response.status).toBe(400);
    expect(response.body.details.fieldErrors.rounds).toContain(message);
    expect(mockedRecordScore).not.toHaveBeenCalled();
  });

  // Input: sets that can happen in tennis: up to 6 games, won by 2 past 6 (however long), and a 7-6 tiebreak set
  // Expected status code: 200
  // Expected behavior: the score is recorded as given
  test.each([
    ["sets up to 6 games", [[6, 0], [4, 2]]],
    ["a set won 7-5", [[7, 5], [6, 3]]],
    ["a 7-6 tiebreak set", [[7, 6], [3, 6], [6, 4]]],
    ["a long advantage set", [[99, 97], [6, 4]]],
  ])("Accepts a score with %s", async (_description, goodRounds) => {
    mockedRecordScore.mockResolvedValue({ outcome: confirming ? "confirmed" : "proposed" });

    const response = await send({ gameId: editGameId, rounds: goodRounds });

    expect(response.status).toBe(200);
    expect(mockedRecordScore).toHaveBeenCalledWith(editGameId, userId, goodRounds, confirming, expect.any(Date));
  });

  // Input: a valid score, while Postgres aborts the transaction as a deadlock
  // Expected status code: 409
  // Expected behavior: nothing is saved, so the player can try again
  // Expected output: { error: "The score could not be saved, please try again", code: "SCORE_CONFLICT" }
  test("Returns 409 when the transaction is aborted as a deadlock", async () => {
    mockedRecordScore.mockRejectedValue(
      new Prisma.PrismaClientKnownRequestError("Transaction failed due to a write conflict or a deadlock", {
        code: "P2034",
        clientVersion: Prisma.prismaVersion.client,
      }),
    );

    const response = await send();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.ScoreConflict));
  });

  test("Returns 500 when saving the score throws unexpectedly", async () => {
    const consoleError = jest.spyOn(console, "error").mockImplementation(() => undefined);
    mockedRecordScore.mockRejectedValue(new Error("Database unavailable"));

    const response = await send();

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: POST /games/score/confirm", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame({ status: GameStatus.IN_PROGRESS }));
    mockedIsUserInGame.mockResolvedValue(true);
  });

  const confirm = () =>
    request(createApp())
      .post("/games/score/confirm")
      .set("Authorization", authHeader)
      .send({ gameId: editGameId, rounds: [[6, 4]] });

  // Input: confirming before anyone reported a score
  // Expected status code: 409
  // Expected output: NO_SCORE_TO_CONFIRM
  test("Returns 409 when no score was reported", async () => {
    mockedRecordScore.mockResolvedValue({ outcome: "noProposal" });

    const response = await confirm();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.NoScoreToConfirm));
  });

  // Input: confirming a score another player replaced after this player saw it
  // Expected status code: 409
  // Expected behavior: nothing is confirmed; the app shows the new score from the live update
  // Expected output: SCORE_CHANGED
  test("Returns 409 when the score changed since the player saw it", async () => {
    mockedRecordScore.mockResolvedValue({ outcome: "scoreChanged" });

    const response = await confirm();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.ScoreChanged));
  });
});

describe("Mocked: reportScore and confirmScore", () => {
  const opponentId = "opponent-user-id";
  const playedGame = gameDetails({
    id: editGameId,
    status: GameStatus.IN_PROGRESS,
    players: [
      ...gameDetails().players,
      {
        id: opponentId,
        username: "rival",
        name: "Rival Player",
        rating: 1500,
        photoUrl: null,
        team: 2,
        acceptedAt: new Date(),
        scoreConfirmedAt: null,
        ratingBefore: null,
        ratingAfter: null,
      },
    ],
  });
  let changeSpy: jest.SpiedFunction<typeof changes.gameChanged>;

  beforeEach(() => {
    jest.clearAllMocks();
    // Only a game in progress can be scored
    mockedFetchGame.mockResolvedValue(existingGame({ status: GameStatus.IN_PROGRESS }));
    mockedIsUserInGame.mockResolvedValue(true);
    mockedFetchGameDetails.mockResolvedValue(playedGame);
    mockedPushToUsers.mockResolvedValue(1);
    changeSpy = jest.spyOn(changes, "gameChanged");
  });

  afterEach(() => {
    changeSpy.mockRestore();
  });

  const rounds: [number, number][] = [
    [6, 4],
    [6, 3],
  ];

  // Input: games nobody started yet: an open one, a booked one (even past its start time), and a matched game its
  // players never accepted, which can't be started at all (Use Case 3's precondition)
  // Expected behavior: nothing is saved, since a game is scored only once a player started it
  // Expected output: GAME_NOT_STARTED
  test.each([
    ["open", existingGame()],
    ["booked", existingGame({ status: GameStatus.BOOKED, startsAt: new Date(Date.now() - 60 * 60 * 1000) })],
    ["unaccepted matched", existingGame({ gameSource: GameSource.MATCHED, startsAt: new Date(Date.now() - 60_000) })],
  ])("Rejects a score for an %s game nobody started", async (_description, game) => {
    mockedFetchGame.mockResolvedValue(game);

    await expect(reportScore(editGameId, userId, rounds)).rejects.toMatchObject(gameErrors.NotStarted);
    await expect(confirmScore(editGameId, userId, rounds)).rejects.toMatchObject(gameErrors.NotStarted);
    expect(mockedRecordScore).not.toHaveBeenCalled();
  });

  test("Throws Closed and saves nothing when the game is already completed", async () => {
    mockedFetchGame.mockResolvedValue(existingGame({ status: GameStatus.COMPLETED }));

    await expect(reportScore(editGameId, userId, rounds)).rejects.toMatchObject(gameErrors.Closed);
    expect(mockedRecordScore).not.toHaveBeenCalled();
  });

  // Input: a player reports a new score
  // Expected behavior: the game's watchers get the score live, and the other players are asked by push to confirm it
  // Expected output: { completed: false }
  test("Asks the other players to confirm a newly reported score", async () => {
    mockedRecordScore.mockResolvedValue({ outcome: "proposed" });

    await expect(reportScore(editGameId, userId, rounds)).resolves.toEqual({ completed: false });

    expect(mockedRecordScore).toHaveBeenCalledWith(editGameId, userId, rounds, false, expect.any(Date));
    expect(changeSpy).toHaveBeenCalledWith({ gameId: editGameId });
    expect(mockedPushToUsers).toHaveBeenCalledWith([opponentId], {
      title: "Confirm the score",
      body: "Ace Player reported 6-4, 6-3 for your singles game at Kitsilano Beach Park.",
      data: { gameId: editGameId, type: "scoreReported" },
    });
  });

  // Input: a player confirms the score waiting, and another player still has to
  // Expected behavior: the game's watchers see the confirmation live; nobody gets a push
  test("Shows a confirmation live without a push", async () => {
    mockedRecordScore.mockResolvedValue({ outcome: "confirmed" });

    await expect(confirmScore(editGameId, userId, rounds)).resolves.toEqual({ completed: false });

    expect(mockedRecordScore).toHaveBeenCalledWith(editGameId, userId, rounds, true, expect.any(Date));
    expect(changeSpy).toHaveBeenCalledWith({ gameId: editGameId });
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  test("Returns the completed game once the last player confirms", async () => {
    const completed = {
      game: existingGame({ status: GameStatus.COMPLETED }),
      rounds: [
        { gameId: editGameId, number: 1, team1Score: 6, team2Score: 4 },
        { gameId: editGameId, number: 2, team1Score: 6, team2Score: 3 },
      ],
      ratingChanges: [
        { userId, ratingBefore: 1500, ratingAfter: 1520 },
        { userId: opponentId, ratingBefore: 1500, ratingAfter: 1480 },
      ],
    };
    mockedRecordScore.mockResolvedValue({ outcome: "completed", completed });

    await expect(confirmScore(editGameId, userId, rounds)).resolves.toEqual({ completed: true, ...completed });
    expect(changeSpy).toHaveBeenCalledWith({ gameId: editGameId });
  });

  // Input: the push to the other players fails
  // Expected behavior: the error is logged; the score is already saved, so reporting still succeeds
  test("Still reports the score when the push fails", async () => {
    const consoleError = jest.spyOn(console, "error").mockImplementation(() => undefined);
    mockedRecordScore.mockResolvedValue({ outcome: "proposed" });
    mockedPushToUsers.mockRejectedValue(new Error("FCM down"));

    await expect(reportScore(editGameId, userId, rounds)).resolves.toEqual({ completed: false });
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });

  test("Throws IncompleteTeams when the teams are not full", async () => {
    mockedRecordScore.mockRejectedValue(new IncompleteTeamsError(Format.DOUBLES, 2, 1));

    await expect(confirmScore(editGameId, userId, rounds)).rejects.toMatchObject(gameErrors.IncompleteTeams);
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedRecordScore.mockRejectedValue(error);

    await expect(reportScore(editGameId, userId, rounds)).rejects.toBe(error);
    expect(changeSpy).not.toHaveBeenCalled();
  });
});
