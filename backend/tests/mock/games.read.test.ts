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
  acceptMatch,
  addPlayerToTeam,
  createGame,
  deleteGame,
  deleteGameUser,
  fetchFeedGames,
  fetchGame,
  fetchGameDetails,
  fetchUserGames,
  fetchUserHistory,
  isUserInGame,
  recordScore,
  startBookedGame,
  updateGame,
} from "../../src/games/games.repository";
import {
  gameErrors,
  getFeed,
  getGame,
  getUserGames,
  getUserHistory,
  newGame,
} from "../../src/games/games.service";
import {
  getForecast,
} from "../../src/external/weather";
import {
  authErrors,
} from "../../src/auth/requireAuth";
import {
  commonErrors,
  errorBody,
} from "../../src/errors";
import {
  userId,
  authHeader,
  futureStartsAt,
  validBody,
  gameDetails,
} from "./gamesFixtures";

// Mocked tests of creating and reading games: POST /games, GET /games, /games/feed, /games/me, /games/history.
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

const mockedCreateGame = jest.mocked(createGame);
const mockedFetchGame = jest.mocked(fetchGame);
const mockedFetchGameDetails = jest.mocked(fetchGameDetails);
const mockedFetchFeedGames = jest.mocked(fetchFeedGames);
const mockedFetchUserGames = jest.mocked(fetchUserGames);
const mockedFetchUserHistory = jest.mocked(fetchUserHistory);
const mockedAddPlayerToTeam = jest.mocked(addPlayerToTeam);
const mockedAcceptMatch = jest.mocked(acceptMatch);
const mockedGetForecast = jest.mocked(getForecast);
const mockedDeleteGame = jest.mocked(deleteGame);
const mockedIsUserInGame = jest.mocked(isUserInGame);
const mockedDeleteGameUser = jest.mocked(deleteGameUser);
const mockedUpdateGame = jest.mocked(updateGame);
const mockedStartBookedGame = jest.mocked(startBookedGame);
const mockedRecordScore = jest.mocked(recordScore);

describe("Mocked: /games requireAuth", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

  const routes = () =>
    [
      [
        "POST /games",
        () => request(createApp()).post("/games").send(validBody()),
      ],
      [
        "GET /games",
        () => request(createApp()).get("/games").query({ gameId }),
      ],
      ["GET /games/feed", () => request(createApp()).get("/games/feed")],
      ["GET /games/me", () => request(createApp()).get("/games/me")],
      ["GET /games/history", () => request(createApp()).get("/games/history")],
      [
        "DELETE /games",
        () => request(createApp()).delete("/games").query({ gameId }),
      ],
      [
        "POST /games/join",
        () =>
          request(createApp()).post("/games/join").send({ gameId, team: 1 }),
      ],
      [
        "POST /games/leave",
        () => request(createApp()).post("/games/leave").send({ gameId }),
      ],
      [
        "POST /games/accept",
        () => request(createApp()).post("/games/accept").send({ gameId }),
      ],
      [
        "POST /games/invite",
        () =>
          request(createApp())
            .post("/games/invite")
            .send({ gameId, userId: gameId }),
      ],
      [
        "PATCH /games/time",
        () =>
          request(createApp())
            .patch("/games/time")
            .send({ gameId, startsAt: futureStartsAt() }),
      ],
      [
        "POST /games/start",
        () => request(createApp()).post("/games/start").send({ gameId }),
      ],
      [
        "PATCH /games/location",
        () =>
          request(createApp())
            .patch("/games/location")
            .send({ gameId, courtId: 2 }),
      ],
      [
        "POST /games/score",
        () =>
          request(createApp())
            .post("/games/score")
            .send({ gameId, rounds: [[6, 4]] }),
      ],
      [
        "POST /games/score/confirm",
        () =>
          request(createApp())
            .post("/games/score/confirm")
            .send({ gameId, rounds: [[6, 4]] }),
      ],
    ] as const;

  const expectNoRepositoryCalls = () => {
    expect(mockedCreateGame).not.toHaveBeenCalled();
    expect(mockedFetchGame).not.toHaveBeenCalled();
    expect(mockedDeleteGame).not.toHaveBeenCalled();
    expect(mockedIsUserInGame).not.toHaveBeenCalled();
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
    expect(mockedUpdateGame).not.toHaveBeenCalled();
    expect(mockedStartBookedGame).not.toHaveBeenCalled();
    expect(mockedRecordScore).not.toHaveBeenCalled();
    expect(mockedFetchGameDetails).not.toHaveBeenCalled();
    expect(mockedFetchFeedGames).not.toHaveBeenCalled();
    expect(mockedFetchUserGames).not.toHaveBeenCalled();
    expect(mockedAddPlayerToTeam).not.toHaveBeenCalled();
    expect(mockedAcceptMatch).not.toHaveBeenCalled();
  };

  test.each(routes())(
    "Rejects %s without an Authorization header",
    async (_route, send) => {
      const response = await send();

      expect(response.status).toBe(401);
      expect(response.body).toEqual(errorBody(authErrors.MissingToken));
      expectNoRepositoryCalls();
    },
  );

  test.each(routes())(
    "Rejects %s with an invalid token",
    async (_route, send) => {
      const response = await send().set("Authorization", "Bearer not-a-jwt");

      expect(response.status).toBe(401);
      expect(response.body).toEqual(errorBody(authErrors.InvalidToken));
      expectNoRepositoryCalls();
    },
  );
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

    const response = await request(createApp())
      .post("/games")
      .set("Authorization", authHeader)
      .send(body);

    expect(response.status).toBe(201);
    expect(response.body.game).toMatchObject({
      id: createdGame.id,
      format: body.format,
      courtId: body.courtId,
      gameSource: body.gameSource,
      status: GameStatus.OPEN,
      startsAt: body.startsAt,
    });
    expect(mockedCreateGame).toHaveBeenCalledWith(
      {
        ...body,
        startsAt: new Date(body.startsAt),
        status: GameStatus.OPEN,
      },
      userId,
    );
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
    ["TOURNAMENT gameSource", { gameSource: GameSource.TOURNAMENT }],
    ["unknown field", { status: GameStatus.COMPLETED }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await request(createApp())
      .post("/games")
      .set("Authorization", authHeader)
      .send({ ...validBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedCreateGame).not.toHaveBeenCalled();
  });

  test.each([
    ["in the past", () => new Date(Date.now() - 60 * 1000).toISOString()],
    ["at the current time", () => new Date().toISOString()],
  ])("Rejects a start time %s", async (_description, startsAt) => {
    const response = await request(createApp())
      .post("/games")
      .set("Authorization", authHeader)
      .send({ ...validBody(), startsAt: startsAt() });

    expect(response.status).toBe(400);
    expect(response.body.details.fieldErrors.startsAt).toContain(
      "must be in the future",
    );
    expect(mockedCreateGame).not.toHaveBeenCalled();
  });

  test("Returns 404 when the court does not exist", async () => {
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
      .post("/games")
      .set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(commonErrors.CourtNotFound));
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedCreateGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .post("/games")
      .set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: GET /games", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

  test("Returns the game with its court, players and rounds", async () => {
    const game = gameDetails({
      status: GameStatus.COMPLETED,
      rounds: [
        { number: 1, team1Score: 6, team2Score: 4 },
        { number: 2, team1Score: 6, team2Score: 3 },
      ],
    });
    mockedFetchGameDetails.mockResolvedValue(game);

    const response = await request(createApp())
      .get("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(200);
    expect(response.body.game).toMatchObject({
      id: gameId,
      format: game.format,
      courtId: game.courtId,
      gameSource: game.gameSource,
      status: game.status,
      startsAt: game.startsAt.toISOString(),
      court: game.court,
      players: [
        {
          id: userId,
          username: "ace",
          name: "Ace Player",
          rating: 1500,
          team: 1,
        },
      ],
      rounds: game.rounds,
      // A completed game is not played anymore, so it has no forecast
      forecast: null,
    });
    expect(mockedFetchGameDetails).toHaveBeenCalledWith(gameId);
    expect(mockedGetForecast).not.toHaveBeenCalled();
  });

  test("Returns an upcoming game with its forecast", async () => {
    const game = gameDetails({ status: GameStatus.BOOKED });
    const forecast = {
      at: game.startsAt,
      temperature: 18.5,
      description: "light rain",
      icon: "10d",
      precipitationChance: 0.4,
    };
    mockedFetchGameDetails.mockResolvedValue(game);
    mockedGetForecast.mockResolvedValue(forecast);

    const response = await request(createApp())
      .get("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(200);
    expect(response.body.game).toMatchObject({
      id: gameId,
      status: GameStatus.BOOKED,
      forecast: { ...forecast, at: forecast.at.toISOString() },
    });
    expect(mockedGetForecast).toHaveBeenCalledWith(
      game.court.latitude,
      game.court.longitude,
      game.startsAt,
    );
  });

  test("Returns the game without a forecast when it cannot be loaded", async () => {
    mockedFetchGameDetails.mockResolvedValue(gameDetails());
    mockedGetForecast.mockResolvedValue(null);

    const response = await request(createApp())
      .get("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(200);
    expect(response.body.game).toMatchObject({ id: gameId, forecast: null });
  });

  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGameDetails.mockResolvedValue(null);

    const response = await request(createApp())
      .get("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
    expect(mockedFetchGameDetails).toHaveBeenCalledWith(gameId);
  });

  test.each([
    ["a missing gameId", {}],
    ["an empty gameId", { gameId: "" }],
    ["a non-UUID gameId", { gameId: "not-a-uuid" }],
    ["a numeric gameId", { gameId: "123" }],
    ["multiple gameIds", { gameId: [gameId, gameId] }],
  ])("Rejects a query with %s", async (_description, query) => {
    const response = await request(createApp())
      .get("/games")
      .set("Authorization", authHeader)
      .query(query);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedFetchGameDetails).not.toHaveBeenCalled();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedFetchGameDetails.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .get("/games")
      .set("Authorization", authHeader)
      .query({ gameId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: GET /games/feed", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Returns the feed's games with their forecast", async () => {
    const singles = gameDetails();
    const doubles = gameDetails({
      id: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
      format: Format.DOUBLES,
      courtId: 2,
      court: { id: 2, name: "Queen Elizabeth Park", latitude: 49.24, longitude: -123.11 },
    });
    const forecast = {
      at: singles.startsAt,
      temperature: 18.5,
      description: "light rain",
      icon: "10d",
      precipitationChance: 0.4,
    };
    mockedFetchFeedGames.mockResolvedValue([singles, doubles]);
    mockedGetForecast.mockResolvedValueOnce(forecast).mockResolvedValueOnce(null);

    const response = await request(createApp())
      .get("/games/feed")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body.games).toHaveLength(2);
    expect(response.body.games[0]).toMatchObject({
      id: singles.id,
      court: singles.court,
      players: [{ id: userId, rating: 1500, team: 1 }],
      forecast: { ...forecast, at: forecast.at.toISOString() },
    });
    // A forecast that cannot be loaded leaves the game in the feed without one
    expect(response.body.games[1]).toMatchObject({
      id: doubles.id,
      forecast: null,
    });
    expect(mockedGetForecast).toHaveBeenNthCalledWith(
      1,
      singles.court.latitude,
      singles.court.longitude,
      singles.startsAt,
    );
    expect(mockedGetForecast).toHaveBeenNthCalledWith(
      2,
      doubles.court.latitude,
      doubles.court.longitude,
      doubles.startsAt,
    );
  });

  test("Returns an empty feed", async () => {
    mockedFetchFeedGames.mockResolvedValue([]);

    const response = await request(createApp())
      .get("/games/feed")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ games: [] });
    expect(mockedGetForecast).not.toHaveBeenCalled();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedFetchFeedGames.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .get("/games/feed")
      .set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: GET /games/me", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Returns the authenticated user's games with their forecast", async () => {
    const booked = gameDetails({ status: GameStatus.BOOKED });
    const open = gameDetails({
      id: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
      courtId: 2,
      court: { id: 2, name: "Queen Elizabeth Park", latitude: 49.24, longitude: -123.11 },
    });
    const forecast = {
      at: booked.startsAt,
      temperature: 18.5,
      description: "light rain",
      icon: "10d",
      precipitationChance: 0.4,
    };
    mockedFetchUserGames.mockResolvedValue([booked, open]);
    mockedGetForecast.mockResolvedValueOnce(forecast).mockResolvedValueOnce(null);

    const response = await request(createApp())
      .get("/games/me")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body.games).toHaveLength(2);
    expect(response.body.games[0]).toMatchObject({
      id: booked.id,
      status: GameStatus.BOOKED,
      court: booked.court,
      players: [{ id: userId, team: 1 }],
      rounds: [],
      forecast: { ...forecast, at: forecast.at.toISOString() },
    });
    // A forecast that cannot be loaded leaves the game in the list without one
    expect(response.body.games[1]).toMatchObject({
      id: open.id,
      forecast: null,
    });
    expect(mockedFetchUserGames).toHaveBeenCalledWith(userId, expect.any(Date));
    expect(mockedGetForecast).toHaveBeenNthCalledWith(
      2,
      open.court.latitude,
      open.court.longitude,
      open.startsAt,
    );
  });

  test("Returns an empty list when the user has no games", async () => {
    mockedFetchUserGames.mockResolvedValue([]);

    const response = await request(createApp())
      .get("/games/me")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ games: [] });
    expect(mockedGetForecast).not.toHaveBeenCalled();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedFetchUserGames.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .get("/games/me")
      .set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: GET /games/history", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Returns the authenticated user's completed games with rating changes", async () => {
    const game = gameDetails({
      status: GameStatus.COMPLETED,
      rounds: [{ number: 1, team1Score: 6, team2Score: 4 }],
      players: [
        {
          id: userId,
          username: "ace",
          name: "Ace Player",
          rating: 1516,
          photoUrl: null,
          team: 1,
          acceptedAt: new Date(),
          scoreConfirmedAt: null,
          ratingBefore: 1500,
          ratingAfter: 1516,
        },
      ],
    });
    mockedFetchUserHistory.mockResolvedValue([game]);

    const response = await request(createApp())
      .get("/games/history")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body.games).toHaveLength(1);
    expect(response.body.games[0]).toMatchObject({
      id: game.id,
      status: GameStatus.COMPLETED,
      players: [{ id: userId, team: 1, ratingBefore: 1500, ratingAfter: 1516 }],
      rounds: [{ number: 1, team1Score: 6, team2Score: 4 }],
    });
    expect(mockedFetchUserHistory).toHaveBeenCalledWith(userId);
  });

  test("Returns an empty list when the user has no completed games", async () => {
    mockedFetchUserHistory.mockResolvedValue([]);

    const response = await request(createApp())
      .get("/games/history")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ games: [] });
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedFetchUserHistory.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .get("/games/history")
      .set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: getUserHistory", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Fetches the user's completed games", async () => {
    const games = [gameDetails({ status: GameStatus.COMPLETED })];
    mockedFetchUserHistory.mockResolvedValue(games);

    await expect(getUserHistory(userId)).resolves.toBe(games);
    expect(mockedFetchUserHistory).toHaveBeenCalledWith(userId);
  });
});

describe("Mocked: getGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Returns the game details from the repository with the game's forecast", async () => {
    const game = gameDetails();
    const forecast = {
      at: game.startsAt,
      temperature: 21,
      description: "clear sky",
      icon: "01d",
      precipitationChance: 0,
    };
    mockedFetchGameDetails.mockResolvedValue(game);
    mockedGetForecast.mockResolvedValue(forecast);

    await expect(getGame(game.id)).resolves.toEqual({ ...game, forecast });
    expect(mockedFetchGameDetails).toHaveBeenCalledWith(game.id);
    expect(mockedGetForecast).toHaveBeenCalledWith(
      game.court.latitude,
      game.court.longitude,
      game.startsAt,
    );
  });

  test.each([GameStatus.COMPLETED, GameStatus.CANCELLED])(
    "Returns a %s game without asking for a forecast",
    async (status) => {
      const game = gameDetails({ status });
      mockedFetchGameDetails.mockResolvedValue(game);

      await expect(getGame(game.id)).resolves.toEqual({
        ...game,
        forecast: null,
      });
      expect(mockedGetForecast).not.toHaveBeenCalled();
    },
  );

  test("Throws NotFound when the game does not exist", async () => {
    mockedFetchGameDetails.mockResolvedValue(null);

    await expect(
      getGame("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"),
    ).rejects.toMatchObject(gameErrors.NotFound);
    expect(mockedGetForecast).not.toHaveBeenCalled();
  });
});

describe("Mocked: getFeed", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Fetches the feed from now and adds each game's forecast", async () => {
    const game = gameDetails();
    const forecast = {
      at: game.startsAt,
      temperature: 21,
      description: "clear sky",
      icon: "01d",
      precipitationChance: 0,
    };
    mockedFetchFeedGames.mockResolvedValue([game]);
    mockedGetForecast.mockResolvedValue(forecast);

    const before = Date.now();
    await expect(getFeed()).resolves.toEqual([{ ...game, forecast }]);

    const [now] = mockedFetchFeedGames.mock.calls[0] as [Date];
    expect(now.getTime()).toBeGreaterThanOrEqual(before);
    expect(now.getTime()).toBeLessThanOrEqual(Date.now());
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedFetchFeedGames.mockRejectedValue(error);

    await expect(getFeed()).rejects.toBe(error);
    expect(mockedGetForecast).not.toHaveBeenCalled();
  });
});

describe("Mocked: getUserGames", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Fetches the user's games from now and adds each game's forecast", async () => {
    const game = gameDetails({ status: GameStatus.BOOKED });
    const forecast = {
      at: game.startsAt,
      temperature: 21,
      description: "clear sky",
      icon: "01d",
      precipitationChance: 0,
    };
    mockedFetchUserGames.mockResolvedValue([game]);
    mockedGetForecast.mockResolvedValue(forecast);

    const before = Date.now();
    await expect(getUserGames(userId)).resolves.toEqual([{ ...game, forecast }]);

    const [calledUserId, now] = mockedFetchUserGames.mock.calls[0] as [
      string,
      Date,
    ];
    expect(calledUserId).toBe(userId);
    expect(now.getTime()).toBeGreaterThanOrEqual(before);
    expect(now.getTime()).toBeLessThanOrEqual(Date.now());
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedFetchUserGames.mockRejectedValue(error);

    await expect(getUserGames(userId)).rejects.toBe(error);
    expect(mockedGetForecast).not.toHaveBeenCalled();
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

    await expect(newGame(gameInput, userId)).resolves.toBe(createdGame);
    expect(mockedCreateGame).toHaveBeenCalledTimes(1);
    expect(mockedCreateGame).toHaveBeenCalledWith(
      { ...gameInput, status: GameStatus.OPEN },
      userId,
    );
  });

  test("Propagates errors from creating the game", async () => {
    const error = new Error("Database unavailable");
    mockedCreateGame.mockRejectedValue(error);

    await expect(newGame(input(), userId)).rejects.toBe(error);
  });
});
