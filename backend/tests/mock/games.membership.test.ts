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
  addPlayerToTeam,
  deleteGame,
  deleteGameUser,
  fetchGame,
  isUserInGame,
} from "../../src/games/games.repository";
import {
  cancelGame,
  gameErrors,
  joinGame,
  leaveGame,
} from "../../src/games/games.service";
import {
  commonErrors,
  errorBody,
} from "../../src/errors";
import {
  userId,
  authHeader,
  prismaError,
  joinGameId,
  joinableGame,
  existingGame,
} from "./gamesFixtures";

// Mocked tests of joining, leaving and cancelling games: POST /games/join, /games/leave, DELETE /games.
// The fixtures shared by every games.*.test.ts are in gamesFixtures.ts
jest.mock("../../src/games/games.repository", () => ({
  replaceGameEquipment: jest.fn(),
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
  // No default equipment, so creating or joining a game doesn't look any up
  getEquipment: jest.fn(() => Promise.resolve([])),
  pushToUsers: jest.fn(),
}));

const mockedFetchGame = jest.mocked(fetchGame);
const mockedAddPlayerToTeam = jest.mocked(addPlayerToTeam);
const mockedDeleteGame = jest.mocked(deleteGame);
const mockedIsUserInGame = jest.mocked(isUserInGame);
const mockedDeleteGameUser = jest.mocked(deleteGameUser);

describe("Mocked: DELETE /games", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(joinableGame());
    mockedIsUserInGame.mockResolvedValue(true);
    mockedFetchGame.mockResolvedValue(existingGame());
  });

  const gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

  test("Deletes the game with the given ID", async () => {
    mockedDeleteGame.mockResolvedValue(true);

    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(204);
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, gameId);
    expect(mockedDeleteGame).toHaveBeenCalledTimes(1);
    expect(mockedDeleteGame).toHaveBeenCalledWith(gameId);
  });

  // Input: a scheduled game that is already completed (or cancelled)
  // Expected status code: 409
  // Expected behavior: the game, its score and its rating changes are kept
  // Expected output: { error: "Game is already completed or cancelled", code: "GAME_CLOSED" }
  test("Returns 409 and keeps a completed scheduled game", async () => {
    mockedFetchGame.mockResolvedValue(existingGame({ status: GameStatus.COMPLETED }));
    mockedDeleteGame.mockResolvedValue(false);

    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.Closed));
  });

  test("Returns 403 and keeps the game when the user is not in it", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(403);
    expect(response.body).toEqual(errorBody(gameErrors.NotInGame));
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, gameId);
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  // Input: the ID of a game that does not exist
  // Expected status code: 404
  // Expected behavior: nothing is deleted
  // Expected output: { error: "Game not found", code: "GAME_NOT_FOUND" }
  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  // Input: the ID of a booked tournament game the caller plays in
  // Expected status code: 409
  // Expected behavior: the game is kept, since the bracket depends on it
  // Expected output: { error: "Tournament games cannot be cancelled", code: "TOURNAMENT_NOT_CANCELLABLE" }
  test("Returns 409 and keeps a tournament game", async () => {
    mockedFetchGame.mockResolvedValue(
      joinableGame({
        gameSource: GameSource.TOURNAMENT,
        status: GameStatus.BOOKED,
      }),
    );

    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.TournamentNotCancellable));
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  test.each([
    ["a missing gameId", {}],
    ["an empty gameId", { gameId: "" }],
    ["a non-UUID gameId", { gameId: "not-a-uuid" }],
    ["a numeric gameId", { gameId: "123" }],
    ["multiple gameIds", { gameId: [gameId, gameId] }],
  ])("Rejects a query with %s", async (_description, query) => {
    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query(query);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedFetchGame).not.toHaveBeenCalled();
    expect(mockedIsUserInGame).not.toHaveBeenCalled();
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  test("Returns 404 when the game is deleted before the delete", async () => {
    mockedDeleteGame.mockRejectedValue(
      new Prisma.PrismaClientKnownRequestError("Record to delete not found", {
        code: "P2025",
        clientVersion: Prisma.prismaVersion.client,
      }),
    );

    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(404);
    // The service maps no Prisma error here, so errorHandler's generic fallback answers
    expect(response.body).toEqual({ error: "Not found", code: "NOT_FOUND" });
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedDeleteGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });

  test("Returns 500 when the membership check throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedIsUserInGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    expect(mockedDeleteGame).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });
});

describe("Mocked: cancelGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(joinableGame());
  });

  const gameId = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

  test("Throws NotFound without deleting when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    await expect(cancelGame(gameId, userId)).rejects.toMatchObject(gameErrors.NotFound);
    expect(mockedIsUserInGame).not.toHaveBeenCalled();
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  test("Throws TournamentNotCancellable without deleting a tournament game", async () => {
    mockedFetchGame.mockResolvedValue(
      joinableGame({ gameSource: GameSource.TOURNAMENT }),
    );
    mockedIsUserInGame.mockResolvedValue(true);

    await expect(cancelGame(gameId, userId)).rejects.toMatchObject(gameErrors.TournamentNotCancellable);
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  test("Deletes the game when the user is in it", async () => {
    mockedIsUserInGame.mockResolvedValue(true);
    mockedFetchGame.mockResolvedValue(existingGame({ id: gameId }));
    mockedDeleteGame.mockResolvedValue(true);

    await expect(cancelGame(gameId, userId)).resolves.toBeUndefined();
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, gameId);
    expect(mockedDeleteGame).toHaveBeenCalledWith(gameId);
  });

  test("Throws Closed when the scheduled game is already completed or cancelled", async () => {
    mockedIsUserInGame.mockResolvedValue(true);
    mockedFetchGame.mockResolvedValue(existingGame({ id: gameId, status: GameStatus.COMPLETED }));
    mockedDeleteGame.mockResolvedValue(false);

    await expect(cancelGame(gameId, userId)).rejects.toMatchObject(gameErrors.Closed);
  });

  test("Does not delete the game when the user is not in it", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    await expect(cancelGame(gameId, userId)).rejects.toMatchObject(gameErrors.NotInGame);
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedIsUserInGame.mockResolvedValue(true);
    mockedFetchGame.mockResolvedValue(existingGame({ id: gameId }));
    mockedDeleteGame.mockRejectedValue(error);

    await expect(cancelGame(gameId, userId)).rejects.toBe(error);
  });
});

describe("Mocked: POST /games/join", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(joinableGame());
    mockedAddPlayerToTeam.mockResolvedValue("added");
  });

  const validJoinBody = () => ({ gameId: joinGameId, team: 1 });

  test("Adds the authenticated user to the game", async () => {
    const body = validJoinBody();

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send(body);

    expect(response.status).toBe(204);
    expect(response.body).toEqual({});
    expect(mockedAddPlayerToTeam).toHaveBeenCalledTimes(1);
    expect(mockedAddPlayerToTeam).toHaveBeenCalledWith(
      body.gameId,
      userId,
      body.team,
      1,
      expect.any(Date),
    );
  });

  test("Joins team 2 of a doubles game with two spots per team", async () => {
    mockedFetchGame.mockResolvedValue(
      joinableGame({ format: Format.DOUBLES }),
    );

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send({ ...validJoinBody(), team: 2 });

    expect(response.status).toBe(204);
    expect(mockedAddPlayerToTeam).toHaveBeenCalledWith(
      joinGameId,
      userId,
      2,
      2,
      expect.any(Date),
    );
  });

  test.each([
    ["missing gameId", { gameId: undefined }],
    ["non-UUID gameId", { gameId: "not-a-uuid" }],
    ["missing team", { team: undefined }],
    ["team 0", { team: 0 }],
    ["team 3", { team: 3 }],
    ["negative team", { team: -1 }],
    ["non-integer team", { team: 1.5 }],
    ["string team", { team: "1" }],
    ["userId in the body", { userId: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d" }],
    ["unknown field", { accepted: false }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send({ ...validJoinBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedFetchGame).not.toHaveBeenCalled();
    expect(mockedAddPlayerToTeam).not.toHaveBeenCalled();
  });

  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
    expect(mockedAddPlayerToTeam).not.toHaveBeenCalled();
  });

  test.each([
    ["a matched game", { gameSource: GameSource.MATCHED }],
    ["a booked game", { status: GameStatus.BOOKED }],
    ["a completed game", { status: GameStatus.COMPLETED }],
    ["a cancelled game", { status: GameStatus.CANCELLED }],
    ["a game that has started", { startsAt: new Date(Date.now() - 60 * 1000) }],
  ])("Returns 409 for %s", async (_description, overrides) => {
    mockedFetchGame.mockResolvedValue(joinableGame(overrides));

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.NotJoinable));
    expect(mockedAddPlayerToTeam).not.toHaveBeenCalled();
  });

  test("Returns 409 when the team is full", async () => {
    mockedAddPlayerToTeam.mockResolvedValue("teamFull");

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.TeamFull));
  });

  test("Returns 409 when the game stops being joinable before the player is added", async () => {
    mockedAddPlayerToTeam.mockResolvedValue("notJoinable");

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.NotJoinable));
  });

  test("Returns 409 when the user is already in the game", async () => {
    mockedAddPlayerToTeam.mockRejectedValue(prismaError("P2002"));

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.AlreadyInGame));
  });

  test("Returns 404 when the user does not exist", async () => {
    mockedAddPlayerToTeam.mockRejectedValue(prismaError("P2003"));

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.GameOrUserNotFound));
  });

  test("Returns 500 for other Prisma errors", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    // A code neither the service nor errorHandler's fallbacks map, e.g. a failed raw query
    mockedAddPlayerToTeam.mockRejectedValue(prismaError("P2010"));

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedFetchGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .post("/games/join")
      .set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    expect(mockedAddPlayerToTeam).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });
});

describe("Mocked: POST /games/leave", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(joinableGame());
    mockedDeleteGameUser.mockResolvedValue();
  });

  const validLeaveBody = () => ({ gameId: joinGameId });

  test("Removes the authenticated user from the game", async () => {
    const body = validLeaveBody();

    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send(body);

    expect(response.status).toBe(204);
    expect(response.body).toEqual({});
    expect(mockedDeleteGameUser).toHaveBeenCalledTimes(1);
    expect(mockedDeleteGameUser).toHaveBeenCalledWith(body.gameId, userId);
  });

  // Input: the ID of a tournament game the caller plays in
  // Expected status code: 409
  // Expected behavior: the caller stays in the game, so the bracket keeps full teams
  // Expected output: { error: "Players cannot leave a tournament game", code: "TOURNAMENT_NOT_LEAVABLE" }
  test("Returns 409 and keeps the user in a tournament game", async () => {
    mockedFetchGame.mockResolvedValue(
      joinableGame({
        gameSource: GameSource.TOURNAMENT,
        status: GameStatus.BOOKED,
      }),
    );

    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.TournamentNotLeavable));
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
  });

  test("Lets the user leave a booked game 31 minutes before it starts", async () => {
    mockedFetchGame.mockResolvedValue(
      joinableGame({
        status: GameStatus.BOOKED,
        startsAt: new Date(Date.now() + 31 * 60 * 1000),
      }),
    );

    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(204);
    expect(mockedDeleteGameUser).toHaveBeenCalledWith(joinGameId, userId);
  });

  test.each([
    ["missing gameId", { gameId: undefined }],
    ["non-UUID gameId", { gameId: "not-a-uuid" }],
    ["userId in the body", { userId: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d" }],
    ["unknown field", { team: 1 }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send({ ...validLeaveBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
  });

  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
  });

  test.each([GameStatus.COMPLETED, GameStatus.CANCELLED])(
    "Returns 409 when the game is %s",
    async (status) => {
      mockedFetchGame.mockResolvedValue(joinableGame({ status }));

      const response = await request(createApp())
        .post("/games/leave")
        .set("Authorization", authHeader)
        .send(validLeaveBody());

      expect(response.status).toBe(409);
      expect(response.body).toEqual(errorBody(gameErrors.Closed));
      expect(mockedDeleteGameUser).not.toHaveBeenCalled();
    },
  );

  test.each([
    ["starts in 29 minutes", 29 * 60 * 1000],
    ["has already started", -60 * 1000],
  ])("Returns 409 when the game %s", async (_description, offsetMs) => {
    mockedFetchGame.mockResolvedValue(
      joinableGame({ startsAt: new Date(Date.now() + offsetMs) }),
    );

    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.TooLate));
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
  });

  test("Returns 404 when the user is not in the game", async () => {
    mockedDeleteGameUser.mockRejectedValue(prismaError("P2025"));

    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.PlayerNotFound));
  });

  test("Returns 500 for other Prisma errors", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    // A code neither the service nor errorHandler's fallbacks map, e.g. a failed raw query
    mockedDeleteGameUser.mockRejectedValue(prismaError("P2010"));

    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedDeleteGameUser.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: joinGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Adds the user to a singles team with one spot", async () => {
    mockedFetchGame.mockResolvedValue(joinableGame());
    mockedAddPlayerToTeam.mockResolvedValue("added");

    await expect(joinGame(joinGameId, userId, 2)).resolves.toBeUndefined();
    expect(mockedFetchGame).toHaveBeenCalledWith(joinGameId);
    expect(mockedAddPlayerToTeam).toHaveBeenCalledWith(
      joinGameId,
      userId,
      2,
      1,
      expect.any(Date),
    );
  });

  test("Throws TeamFull when the repository finds no free spot", async () => {
    mockedFetchGame.mockResolvedValue(joinableGame({ format: Format.DOUBLES }));
    mockedAddPlayerToTeam.mockResolvedValue("teamFull");

    await expect(joinGame(joinGameId, userId, 1)).rejects.toMatchObject(gameErrors.TeamFull);
  });

  test("Throws NotJoinable when the repository finds the game changed after it was checked", async () => {
    mockedFetchGame.mockResolvedValue(joinableGame());
    mockedAddPlayerToTeam.mockResolvedValue("notJoinable");

    await expect(joinGame(joinGameId, userId, 1)).rejects.toMatchObject(gameErrors.NotJoinable);
  });

  test("Passes the repository the same time it checked the start time against", async () => {
    const game = joinableGame({ startsAt: new Date(Date.now() + 60 * 1000) });
    mockedFetchGame.mockResolvedValue(game);
    mockedAddPlayerToTeam.mockResolvedValue("added");
    const before = Date.now();

    await joinGame(joinGameId, userId, 1);

    const [, , , , now] = mockedAddPlayerToTeam.mock.calls[0] as [
      string,
      string,
      number,
      number,
      Date,
    ];
    expect(now.getTime()).toBeGreaterThanOrEqual(before);
    expect(now.getTime()).toBeLessThanOrEqual(Date.now());
    expect(now.getTime()).toBeLessThan(game.startsAt.getTime());
  });

  test("Throws NotFound without adding the user when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    await expect(joinGame(joinGameId, userId, 1)).rejects.toMatchObject(gameErrors.NotFound);
    expect(mockedAddPlayerToTeam).not.toHaveBeenCalled();
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedFetchGame.mockResolvedValue(joinableGame());
    mockedAddPlayerToTeam.mockRejectedValue(error);

    await expect(joinGame(joinGameId, userId, 1)).rejects.toBe(error);
  });
});

describe("Mocked: leaveGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Deletes the game user through the repository", async () => {
    mockedFetchGame.mockResolvedValue(joinableGame());
    mockedDeleteGameUser.mockResolvedValue();

    await expect(leaveGame(joinGameId, userId)).resolves.toBeUndefined();
    expect(mockedDeleteGameUser).toHaveBeenCalledWith(joinGameId, userId);
  });

  test("Throws TournamentNotLeavable without deleting when the game is in a tournament", async () => {
    mockedFetchGame.mockResolvedValue(
      joinableGame({ gameSource: GameSource.TOURNAMENT }),
    );

    await expect(leaveGame(joinGameId, userId)).rejects.toMatchObject(gameErrors.TournamentNotLeavable);
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
  });

  test("Throws TooLate without deleting when the game starts in less than 30 minutes", async () => {
    mockedFetchGame.mockResolvedValue(
      joinableGame({ startsAt: new Date(Date.now() + 10 * 60 * 1000) }),
    );

    await expect(leaveGame(joinGameId, userId)).rejects.toMatchObject(gameErrors.TooLate);
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedFetchGame.mockResolvedValue(joinableGame());
    mockedDeleteGameUser.mockRejectedValue(error);

    await expect(leaveGame(joinGameId, userId)).rejects.toBe(error);
  });
});
