import request from "supertest";
import { createApp } from "../../src/app";
import { Prisma } from "../../src/generated/prisma/client";
import {
  Format,
  GameSource,
  GameStatus,
} from "../../src/generated/prisma/enums";
import {
  createGame,
  createGameUser,
  deleteGame,
  deleteGameUser,
  fetchGame,
  isUserInGame,
} from "../../src/games/games.repository";
import {
  cancelGame,
  getGame,
  joinGame,
  leaveGame,
  newGame,
} from "../../src/games/games.service";
import { signAccessToken } from "../../src/auth/tokens";

// Every /games route is behind requireAuth, so requests need a valid token
process.env.JWT_SECRET = "test-jwt-secret";
const userId = "user-123";
const authHeader = `Bearer ${signAccessToken(userId)}`;

jest.mock("../../src/games/games.repository", () => ({
  createGame: jest.fn(),
  fetchGame: jest.fn(),
  deleteGame: jest.fn(),
  isUserInGame: jest.fn(),
  createGameUser: jest.fn(),
  deleteGameUser: jest.fn(),
}));

const mockedCreateGame = jest.mocked(createGame);
const mockedFetchGame = jest.mocked(fetchGame);
const mockedDeleteGame = jest.mocked(deleteGame);
const mockedIsUserInGame = jest.mocked(isUserInGame);
const mockedCreateGameUser = jest.mocked(createGameUser);
const mockedDeleteGameUser = jest.mocked(deleteGameUser);

const futureStartsAt = () =>
  new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();

const validBody = () => ({
  format: Format.SINGLES,
  startsAt: futureStartsAt(),
  courtId: 1,
  gameSource: GameSource.SCHEDULED,
});

describe("Mocked: /games requireAuth", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

  const routes = () => [
    ["POST /games", () => request(createApp()).post("/games").send(validBody())],
    ["GET /games", () => request(createApp()).get("/games").query({ gameId })],
    ["DELETE /games", () => request(createApp()).delete("/games").query({ gameId })],
    ["POST /games/join", () => request(createApp()).post("/games/join").send({ gameId, team: 1 })],
    ["POST /games/leave", () => request(createApp()).post("/games/leave").send({ gameId })],
  ] as const;

  const expectNoRepositoryCalls = () => {
    expect(mockedCreateGame).not.toHaveBeenCalled();
    expect(mockedFetchGame).not.toHaveBeenCalled();
    expect(mockedDeleteGame).not.toHaveBeenCalled();
    expect(mockedIsUserInGame).not.toHaveBeenCalled();
    expect(mockedCreateGameUser).not.toHaveBeenCalled();
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
  };

  test.each(routes())("Rejects %s without an Authorization header", async (_route, send) => {
    const response = await send();

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: "Missing auth token" });
    expectNoRepositoryCalls();
  });

  test.each(routes())("Rejects %s with an invalid token", async (_route, send) => {
    const response = await send().set("Authorization", "Bearer not-a-jwt");

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: "Invalid or expired auth token" });
    expectNoRepositoryCalls();
  });
});

describe("Mocked: POST /games", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Creates a game with OPEN status and adds the creator to team 1", async () => {
    const body = validBody();
    const createdGame = {
      id: "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b",
      ...body,
      startsAt: new Date(body.startsAt),
      status: GameStatus.OPEN,
      createdAt: new Date(),
    };
    mockedCreateGame.mockResolvedValue(createdGame);
    mockedCreateGameUser.mockResolvedValue({
      gameId: createdGame.id,
      userId,
      team: 1,
      acceptedAt: new Date(),
    });

    const response = await request(createApp()).post("/games").set("Authorization", authHeader).send(body);

    expect(response.status).toBe(201);
    expect(response.body.game).toMatchObject({
      id: createdGame.id,
      format: body.format,
      courtId: body.courtId,
      gameSource: body.gameSource,
      status: GameStatus.OPEN,
      startsAt: body.startsAt,
    });
    expect(mockedCreateGame).toHaveBeenCalledWith({
      ...body,
      startsAt: new Date(body.startsAt),
      status: GameStatus.OPEN,
    });
    expect(mockedCreateGameUser).toHaveBeenCalledWith(
      createdGame.id,
      userId,
      1,
      true,
    );
  });

  test("Returns 500 when adding the creator to the game fails", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedCreateGame.mockResolvedValue({
      id: "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b",
      ...validBody(),
      startsAt: new Date(futureStartsAt()),
      status: GameStatus.OPEN,
      createdAt: new Date(),
    });
    mockedCreateGameUser.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .post("/games").set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });

  test.each([
    ["removed sport field", { sport: "TENNIS" }],
    ["invalid format", { format: "TRIPLES" }],
    ["non-ISO startsAt", { startsAt: "tomorrow" }],
    ["startsAt without offset", { startsAt: "2099-01-01T10:00:00" }],
    ["non-integer courtId", { courtId: 1.5 }],
    ["non-positive courtId", { courtId: 0 }],
    ["string courtId", { courtId: "1" }],
    ["invalid gameSource", { gameSource: "MANUAL" }],
    ["unknown field", { status: GameStatus.COMPLETED }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await request(createApp())
      .post("/games").set("Authorization", authHeader)
      .send({ ...validBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request body");
    expect(response.body.details).toBeDefined();
    expect(mockedCreateGame).not.toHaveBeenCalled();
    expect(mockedCreateGameUser).not.toHaveBeenCalled();
  });

  test.each([
    ["in the past", () => new Date(Date.now() - 60 * 1000).toISOString()],
    ["at the current time", () => new Date().toISOString()],
  ])("Rejects a start time %s", async (_description, startsAt) => {
    const response = await request(createApp())
      .post("/games").set("Authorization", authHeader)
      .send({ ...validBody(), startsAt: startsAt() });

    expect(response.status).toBe(400);
    expect(response.body.details.fieldErrors.startsAt).toContain(
      "must be in the future",
    );
    expect(mockedCreateGame).not.toHaveBeenCalled();
  });

  test("Returns 500 when the court does not exist", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedCreateGame.mockRejectedValue(
      new Prisma.PrismaClientKnownRequestError(
        "Foreign key constraint violated",
        {
          code: "P2003",
          clientVersion: Prisma.prismaVersion.client,
        },
      ),
    );

    const response = await request(createApp())
      .post("/games").set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(500);
    consoleError.mockRestore();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedCreateGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .post("/games").set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

describe("Mocked: GET /games", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

  test("Returns the game with the given ID", async () => {
    const startsAt = new Date(futureStartsAt());
    const game = {
      id: gameId,
      format: Format.SINGLES,
      startsAt,
      courtId: 1,
      gameSource: GameSource.SCHEDULED,
      status: GameStatus.OPEN,
      createdAt: new Date(),
    };
    mockedFetchGame.mockResolvedValue(game);

    const response = await request(createApp()).get("/games").set("Authorization", authHeader).query({ gameId });

    expect(response.status).toBe(200);
    expect(response.body.game).toMatchObject({
      id: gameId,
      format: game.format,
      courtId: game.courtId,
      gameSource: game.gameSource,
      status: game.status,
      startsAt: startsAt.toISOString(),
    });
    expect(mockedFetchGame).toHaveBeenCalledWith(gameId);
  });

  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    const response = await request(createApp()).get("/games").set("Authorization", authHeader).query({ gameId });

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ error: "Game not found" });
    expect(mockedFetchGame).toHaveBeenCalledWith(gameId);
  });

  test.each([
    ["a missing gameId", {}],
    ["an empty gameId", { gameId: "" }],
    ["a non-UUID gameId", { gameId: "not-a-uuid" }],
    ["a numeric gameId", { gameId: "123" }],
    ["multiple gameIds", { gameId: [gameId, gameId] }],
  ])("Rejects a query with %s", async (_description, query) => {
    const response = await request(createApp()).get("/games").set("Authorization", authHeader).query(query);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request query");
    expect(response.body.details).toBeDefined();
    expect(mockedFetchGame).not.toHaveBeenCalled();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedFetchGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp()).get("/games").set("Authorization", authHeader).query({ gameId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

describe("Mocked: DELETE /games", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedIsUserInGame.mockResolvedValue(true);
  });

  const gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

  test("Deletes the game with the given ID", async () => {
    mockedDeleteGame.mockResolvedValue();

    const response = await request(createApp())
      .delete("/games").set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(204);
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, gameId);
    expect(mockedDeleteGame).toHaveBeenCalledTimes(1);
    expect(mockedDeleteGame).toHaveBeenCalledWith(gameId);
  });

  test("Returns 403 and keeps the game when the user is not in it", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    const response = await request(createApp())
      .delete("/games").set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(403);
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, gameId);
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  test.each([
    ["a missing gameId", {}],
    ["an empty gameId", { gameId: "" }],
    ["a non-UUID gameId", { gameId: "not-a-uuid" }],
    ["a numeric gameId", { gameId: "123" }],
    ["multiple gameIds", { gameId: [gameId, gameId] }],
  ])("Rejects a query with %s", async (_description, query) => {
    const response = await request(createApp()).delete("/games").set("Authorization", authHeader).query(query);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request query");
    expect(response.body.details).toBeDefined();
    expect(mockedIsUserInGame).not.toHaveBeenCalled();
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  test("Returns 500 when the game does not exist", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedDeleteGame.mockRejectedValue(
      new Prisma.PrismaClientKnownRequestError("Record to delete not found", {
        code: "P2025",
        clientVersion: Prisma.prismaVersion.client,
      }),
    );

    const response = await request(createApp())
      .delete("/games").set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedDeleteGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .delete("/games").set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });

  test("Returns 500 when the membership check throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedIsUserInGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .delete("/games").set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    expect(mockedDeleteGame).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });
});

describe("Mocked: cancelGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const gameId = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

  test("Deletes the game when the user is in it", async () => {
    mockedIsUserInGame.mockResolvedValue(true);
    mockedDeleteGame.mockResolvedValue();

    await expect(cancelGame(gameId, userId)).resolves.toEqual({
      success: true,
    });
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, gameId);
    expect(mockedDeleteGame).toHaveBeenCalledWith(gameId);
  });

  test("Does not delete the game when the user is not in it", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    await expect(cancelGame(gameId, userId)).resolves.toEqual({
      success: false,
      error: "Unauthorized",
    });
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedIsUserInGame.mockResolvedValue(true);
    mockedDeleteGame.mockRejectedValue(error);

    await expect(cancelGame(gameId, userId)).rejects.toBe(error);
  });
});

describe("Mocked: getGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Returns the game from the repository", async () => {
    const gameId = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    const game = {
      id: gameId,
      format: Format.DOUBLES,
      startsAt: new Date(futureStartsAt()),
      courtId: 7,
      gameSource: GameSource.MATCHED,
      status: GameStatus.OPEN,
      createdAt: new Date(),
    };
    mockedFetchGame.mockResolvedValue(game);

    await expect(getGame(gameId)).resolves.toBe(game);
    expect(mockedFetchGame).toHaveBeenCalledWith(gameId);
  });

  test("Returns null when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    await expect(
      getGame("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"),
    ).resolves.toBeNull();
  });
});

describe("Mocked: newGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const input = () => ({
    format: Format.DOUBLES,
    startsAt: new Date(futureStartsAt()),
    courtId: 7,
    gameSource: GameSource.MATCHED,
  });

  test("Creates the game with OPEN status and adds the creator to team 1", async () => {
    const gameInput = input();
    const createdGame = {
      id: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
      ...gameInput,
      status: GameStatus.OPEN,
      createdAt: new Date(),
    };
    mockedCreateGame.mockResolvedValue(createdGame);
    mockedCreateGameUser.mockResolvedValue({
      gameId: createdGame.id,
      userId,
      team: 1,
      acceptedAt: new Date(),
    });

    await expect(newGame(gameInput, userId)).resolves.toBe(createdGame);
    expect(mockedCreateGame).toHaveBeenCalledWith({
      ...gameInput,
      status: GameStatus.OPEN,
    });
    expect(mockedCreateGameUser).toHaveBeenCalledWith(
      createdGame.id,
      userId,
      1,
      true,
    );
  });

  test("Does not add the creator when creating the game fails", async () => {
    const error = new Error("Database unavailable");
    mockedCreateGame.mockRejectedValue(error);

    await expect(newGame(input(), userId)).rejects.toBe(error);
    expect(mockedCreateGameUser).not.toHaveBeenCalled();
  });

  test("Propagates errors from adding the creator", async () => {
    const error = new Error("Database unavailable");
    mockedCreateGame.mockResolvedValue({
      id: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
      ...input(),
      status: GameStatus.OPEN,
      createdAt: new Date(),
    });
    mockedCreateGameUser.mockRejectedValue(error);

    await expect(newGame(input(), userId)).rejects.toBe(error);
  });
});

const prismaError = (code: string) =>
  new Prisma.PrismaClientKnownRequestError(`Prisma error ${code}`, {
    code,
    clientVersion: Prisma.prismaVersion.client,
  });

const joinGameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

describe("Mocked: POST /games/join", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const validJoinBody = () => ({ gameId: joinGameId, team: 1 });

  test("Adds the authenticated user to the game", async () => {
    const body = validJoinBody();
    mockedCreateGameUser.mockResolvedValue({
      ...body,
      userId,
      acceptedAt: new Date(),
    });

    const response = await request(createApp())
      .post("/games/join").set("Authorization", authHeader)
      .send(body);

    expect(response.status).toBe(204);
    expect(response.body).toEqual({});
    expect(mockedCreateGameUser).toHaveBeenCalledTimes(1);
    expect(mockedCreateGameUser).toHaveBeenCalledWith(
      body.gameId,
      userId,
      body.team,
      true,
    );
  });

  test("Accepts team 0", async () => {
    const body = { ...validJoinBody(), team: 0 };
    mockedCreateGameUser.mockResolvedValue({ ...body, userId, acceptedAt: null });

    const response = await request(createApp())
      .post("/games/join").set("Authorization", authHeader)
      .send(body);

    expect(response.status).toBe(204);
    expect(mockedCreateGameUser).toHaveBeenCalledWith(
      body.gameId,
      userId,
      0,
      true,
    );
  });

  test.each([
    ["missing gameId", { gameId: undefined }],
    ["non-UUID gameId", { gameId: "not-a-uuid" }],
    ["missing team", { team: undefined }],
    ["negative team", { team: -1 }],
    ["non-integer team", { team: 1.5 }],
    ["string team", { team: "1" }],
    ["userId in the body", { userId: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d" }],
    ["unknown field", { accepted: false }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await request(createApp())
      .post("/games/join").set("Authorization", authHeader)
      .send({ ...validJoinBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request body");
    expect(response.body.details).toBeDefined();
    expect(mockedCreateGameUser).not.toHaveBeenCalled();
  });

  test("Returns 409 when the user is already in the game", async () => {
    mockedCreateGameUser.mockRejectedValue(prismaError("P2002"));

    const response = await request(createApp())
      .post("/games/join").set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ error: "User already in game" });
  });

  test("Returns 404 when the game or user does not exist", async () => {
    mockedCreateGameUser.mockRejectedValue(prismaError("P2003"));

    const response = await request(createApp())
      .post("/games/join").set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ error: "Game or user not found" });
  });

  test("Returns 500 for other Prisma errors", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedCreateGameUser.mockRejectedValue(prismaError("P2025"));

    const response = await request(createApp())
      .post("/games/join").set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedCreateGameUser.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .post("/games/join").set("Authorization", authHeader)
      .send(validJoinBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

describe("Mocked: POST /games/leave", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const validLeaveBody = () => ({ gameId: joinGameId });

  test("Removes the authenticated user from the game", async () => {
    const body = validLeaveBody();
    mockedDeleteGameUser.mockResolvedValue();

    const response = await request(createApp())
      .post("/games/leave").set("Authorization", authHeader)
      .send(body);

    expect(response.status).toBe(204);
    expect(response.body).toEqual({});
    expect(mockedDeleteGameUser).toHaveBeenCalledTimes(1);
    expect(mockedDeleteGameUser).toHaveBeenCalledWith(body.gameId, userId);
  });

  test.each([
    ["missing gameId", { gameId: undefined }],
    ["non-UUID gameId", { gameId: "not-a-uuid" }],
    ["userId in the body", { userId: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d" }],
    ["unknown field", { team: 1 }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await request(createApp())
      .post("/games/leave").set("Authorization", authHeader)
      .send({ ...validLeaveBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request body");
    expect(response.body.details).toBeDefined();
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
  });

  test("Returns 404 when the user is not in the game", async () => {
    mockedDeleteGameUser.mockRejectedValue(prismaError("P2025"));

    const response = await request(createApp())
      .post("/games/leave").set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ error: "User not in game" });
  });

  test("Returns 500 for other Prisma errors", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedDeleteGameUser.mockRejectedValue(prismaError("P2003"));

    const response = await request(createApp())
      .post("/games/leave").set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedDeleteGameUser.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .post("/games/leave").set("Authorization", authHeader)
      .send(validLeaveBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

describe("Mocked: joinGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Creates an accepted game user by default", async () => {
    const gameUser = { gameId: joinGameId, userId, team: 1, acceptedAt: new Date() };
    mockedCreateGameUser.mockResolvedValue(gameUser);

    await expect(joinGame(joinGameId, userId, 1)).resolves.toBe(gameUser);
    expect(mockedCreateGameUser).toHaveBeenCalledWith(joinGameId, userId, 1, true);
  });

  test("Passes accepted = false through to the repository", async () => {
    const gameUser = { gameId: joinGameId, userId, team: 2, acceptedAt: null };
    mockedCreateGameUser.mockResolvedValue(gameUser);

    await expect(joinGame(joinGameId, userId, 2, false)).resolves.toBe(gameUser);
    expect(mockedCreateGameUser).toHaveBeenCalledWith(joinGameId, userId, 2, false);
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedCreateGameUser.mockRejectedValue(error);

    await expect(joinGame(joinGameId, userId, 1)).rejects.toBe(error);
  });
});

describe("Mocked: leaveGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Deletes the game user through the repository", async () => {
    mockedDeleteGameUser.mockResolvedValue();

    await expect(leaveGame(joinGameId, userId)).resolves.toBeUndefined();
    expect(mockedDeleteGameUser).toHaveBeenCalledWith(joinGameId, userId);
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedDeleteGameUser.mockRejectedValue(error);

    await expect(leaveGame(joinGameId, userId)).rejects.toBe(error);
  });
});
