import request from "supertest";
import {
  createApp,
} from "../../src/app";
import {
  Game,
} from "../../src/generated/prisma/client";
import {
  GameSource,
  GameStatus,
} from "../../src/generated/prisma/enums";
import {
  fetchGame,
  isUserInGame,
  recordScore,
  startBookedGame,
  updateGame,
} from "../../src/games/games.repository";
import {
  changeGameLocation,
  changeGameTime,
  gameErrors,
  startGame,
} from "../../src/games/games.service";
import {
  commonErrors,
  errorBody,
} from "../../src/errors";
import * as changes from "../../src/events/changes";
import {
  userId,
  authHeader,
  futureStartsAt,
  prismaError,
  editGameId,
  existingGame,
} from "./gamesFixtures";

// Mocked tests of changing and starting games: PATCH /games/time, /games/location, POST /games/start.
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

const mockedFetchGame = jest.mocked(fetchGame);
const mockedIsUserInGame = jest.mocked(isUserInGame);
const mockedUpdateGame = jest.mocked(updateGame);
const mockedStartBookedGame = jest.mocked(startBookedGame);
const mockedRecordScore = jest.mocked(recordScore);

// Every edit route runs the same game, membership and status checks before changing anything
describe.each([
  [
    "PATCH /games/time",
    () =>
      request(createApp())
        .patch("/games/time")
        .set("Authorization", authHeader)
        .send({ gameId: editGameId, startsAt: futureStartsAt() }),
  ],
  [
    "PATCH /games/location",
    () =>
      request(createApp())
        .patch("/games/location")
        .set("Authorization", authHeader)
        .send({ gameId: editGameId, courtId: 2 }),
  ],
  [
    "POST /games/score",
    () =>
      request(createApp())
        .post("/games/score")
        .set("Authorization", authHeader)
        .send({ gameId: editGameId, rounds: [[6, 4]] }),
  ],
  [
    "POST /games/score/confirm",
    () =>
      request(createApp())
        .post("/games/score/confirm")
        .set("Authorization", authHeader)
        .send({ gameId: editGameId, rounds: [[6, 4]] }),
  ],
] as const)("Mocked: %s edit checks", (_route, send) => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame());
    mockedIsUserInGame.mockResolvedValue(true);
  });

  const expectNoChanges = () => {
    expect(mockedUpdateGame).not.toHaveBeenCalled();
    expect(mockedRecordScore).not.toHaveBeenCalled();
  };

  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    const response = await send();

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
    expect(mockedIsUserInGame).not.toHaveBeenCalled();
    expectNoChanges();
  });

  test("Returns 403 when the user is not in the game", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    const response = await send();

    expect(response.status).toBe(403);
    expect(response.body).toEqual(errorBody(gameErrors.NotInGame));
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, editGameId);
    expectNoChanges();
  });

  test.each([GameStatus.COMPLETED, GameStatus.CANCELLED])(
    "Returns 409 when the game is %s",
    async (status) => {
      mockedFetchGame.mockResolvedValue(existingGame({ status }));

      const response = await send();

      expect(response.status).toBe(409);
      expect(response.body).toEqual(errorBody(gameErrors.Closed));
      expectNoChanges();
    },
  );

  test("Returns 500 when fetching the game throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedFetchGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await send();

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    expectNoChanges();
    consoleError.mockRestore();
  });
});

describe("Mocked: PATCH /games/time", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame());
    mockedIsUserInGame.mockResolvedValue(true);
  });

  const validTimeBody = () => ({
    gameId: editGameId,
    startsAt: futureStartsAt(),
  });

  test.each([GameStatus.OPEN, GameStatus.BOOKED])(
    "Moves a %s game to the new start time",
    async (status) => {
      const body = validTimeBody();
      mockedFetchGame.mockResolvedValue(existingGame({ status }));
      mockedUpdateGame.mockResolvedValue(
        existingGame({ status, startsAt: new Date(body.startsAt) }),
      );

      const response = await request(createApp())
        .patch("/games/time")
        .set("Authorization", authHeader)
        .send(body);

      expect(response.status).toBe(200);
      expect(response.body.game).toMatchObject({
        id: editGameId,
        status,
        startsAt: body.startsAt,
      });
      expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, editGameId);
      expect(mockedUpdateGame).toHaveBeenCalledTimes(1);
      expect(mockedUpdateGame).toHaveBeenCalledWith(editGameId, {
        startsAt: new Date(body.startsAt),
      });
    },
  );

  test.each([
    ["missing gameId", { gameId: undefined }],
    ["non-UUID gameId", { gameId: "not-a-uuid" }],
    ["missing startsAt", { startsAt: undefined }],
    ["non-ISO startsAt", { startsAt: "tomorrow" }],
    ["startsAt without offset", { startsAt: "2099-01-01T10:00:00" }],
    [
      "startsAt in the past",
      { startsAt: new Date(Date.now() - 60 * 1000).toISOString() },
    ],
    ["userId in the body", { userId: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d" }],
    ["unknown field", { courtId: 2 }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await request(createApp())
      .patch("/games/time")
      .set("Authorization", authHeader)
      .send({ ...validTimeBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedFetchGame).not.toHaveBeenCalled();
    expect(mockedUpdateGame).not.toHaveBeenCalled();
  });

  test("Returns 404 when the game is deleted before the update", async () => {
    mockedUpdateGame.mockRejectedValue(prismaError("P2025"));

    const response = await request(createApp())
      .patch("/games/time")
      .set("Authorization", authHeader)
      .send(validTimeBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
  });

  test("Returns 500 when the update throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedUpdateGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .patch("/games/time")
      .set("Authorization", authHeader)
      .send(validTimeBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: POST /games/start", () => {
  const bookedGame = (overrides: Partial<Game> = {}) =>
    existingGame({ status: GameStatus.BOOKED, ...overrides });

  const start = (body: object = { gameId: editGameId }) =>
    request(createApp())
      .post("/games/start")
      .set("Authorization", authHeader)
      .send(body);

  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(bookedGame());
    mockedIsUserInGame.mockResolvedValue(true);
  });

  // Input: a player starts a booked scheduled, matched or tournament game that starts tomorrow
  // Expected status code: 200
  // Expected behavior: the game's start time moves to now, checked against the time the game was checked at
  // Expected output: the started game
  test.each([GameSource.SCHEDULED, GameSource.MATCHED, GameSource.TOURNAMENT])(
    "Starts a booked %s game now",
    async (gameSource) => {
      mockedFetchGame.mockResolvedValue(bookedGame({ gameSource }));
      const before = Date.now();
      mockedStartBookedGame.mockImplementation((_gameId, now) =>
        Promise.resolve(bookedGame({ gameSource, startsAt: now })),
      );

      const response = await start();

      expect(response.status).toBe(200);
      expect(response.body.game).toMatchObject({
        id: editGameId,
        status: GameStatus.BOOKED,
        gameSource,
      });
      expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, editGameId);
      expect(mockedStartBookedGame).toHaveBeenCalledTimes(1);
      const [startedId, now] = mockedStartBookedGame.mock.calls[0] ?? [];
      expect(startedId).toBe(editGameId);
      expect(now?.getTime()).toBeGreaterThanOrEqual(before);
      expect(now?.getTime()).toBeLessThanOrEqual(Date.now());
      expect(response.body.game.startsAt).toBe(now?.toISOString());
    },
  );

  test.each([
    ["missing gameId", {}],
    ["non-UUID gameId", { gameId: "not-a-uuid" }],
    ["unknown field", { gameId: editGameId, startsAt: futureStartsAt() }],
  ])("Rejects a body with %s", async (_description, body) => {
    const response = await start(body);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedFetchGame).not.toHaveBeenCalled();
    expect(mockedStartBookedGame).not.toHaveBeenCalled();
  });

  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    const response = await start();

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
    expect(mockedIsUserInGame).not.toHaveBeenCalled();
    expect(mockedStartBookedGame).not.toHaveBeenCalled();
  });

  test("Returns 403 when the user is not in the game", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    const response = await start();

    expect(response.status).toBe(403);
    expect(response.body).toEqual(errorBody(gameErrors.NotInGame));
    expect(mockedStartBookedGame).not.toHaveBeenCalled();
  });

  // Input: a booked game whose start time passed a minute ago, which nobody started
  // Expected status code: 200
  // Expected behavior: games never start on their own, so it can still be started, late
  // Expected output: the started game
  test("Starts a booked game whose start time has passed", async () => {
    mockedFetchGame.mockResolvedValue(bookedGame({ startsAt: new Date(Date.now() - 60 * 1000) }));
    mockedStartBookedGame.mockImplementation((_gameId, now) =>
      Promise.resolve(bookedGame({ status: GameStatus.IN_PROGRESS, startsAt: now })),
    );

    const response = await start();

    expect(response.status).toBe(200);
    expect(response.body.game).toMatchObject({ id: editGameId, status: GameStatus.IN_PROGRESS });
    expect(mockedStartBookedGame).toHaveBeenCalledTimes(1);
  });

  // Input: a player starts a booked game
  // Expected behavior: the start is announced with when it started, so its players stop showing as playing to
  // their friends once the playing window runs out
  // Expected output: gameChanged with the game and its new start time
  test("Announces when the game started", async () => {
    const gameChanged = jest.spyOn(changes, "gameChanged");
    mockedStartBookedGame.mockImplementation((_gameId, now) =>
      Promise.resolve(bookedGame({ status: GameStatus.IN_PROGRESS, startsAt: now })),
    );

    await start();

    const [startedAt] = mockedStartBookedGame.mock.calls.map(([, now]) => now);
    expect(gameChanged).toHaveBeenCalledWith({ gameId: editGameId, startedAt });
    gameChanged.mockRestore();
  });

  // Input: games that can't start: closed, still open (free spots or unanswered match), or already in progress
  // Expected status code: 409
  // Expected behavior: the start time is not changed
  // Expected output: the reason, e.g. { error: "Game has already started", code: "GAME_ALREADY_STARTED" }
  test.each([
    ["completed", { status: GameStatus.COMPLETED }, gameErrors.Closed],
    ["cancelled", { status: GameStatus.CANCELLED }, gameErrors.Closed],
    ["open scheduled", { status: GameStatus.OPEN }, gameErrors.NotBooked],
    [
      "unanswered matched",
      { status: GameStatus.OPEN, gameSource: GameSource.MATCHED },
      gameErrors.NotBooked,
    ],
    ["in progress", { status: GameStatus.IN_PROGRESS }, gameErrors.AlreadyStarted],
  ] as const)("Returns 409 for a %s game", async (_description, overrides, error) => {
    mockedFetchGame.mockResolvedValue(bookedGame(overrides));

    const response = await start();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(error));
    expect(mockedStartBookedGame).not.toHaveBeenCalled();
  });

  // Input: two players tap Start now at once, and the other one's update lands first
  // Expected status code: 409
  // Expected output: { error: "Game has already started", code: "GAME_ALREADY_STARTED" }
  test("Returns 409 when another player starts the game first", async () => {
    mockedStartBookedGame.mockResolvedValue(null);
    mockedFetchGame
      .mockResolvedValueOnce(bookedGame())
      .mockResolvedValueOnce(bookedGame({ status: GameStatus.IN_PROGRESS, startsAt: new Date() }));

    const response = await start();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.AlreadyStarted));
  });

  test("Returns 409 when the game is completed before the update", async () => {
    mockedStartBookedGame.mockResolvedValue(null);
    mockedFetchGame
      .mockResolvedValueOnce(bookedGame())
      .mockResolvedValueOnce(bookedGame({ status: GameStatus.COMPLETED }));

    const response = await start();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.Closed));
  });

  test("Returns 404 when the game is deleted before the update", async () => {
    mockedStartBookedGame.mockResolvedValue(null);
    mockedFetchGame.mockResolvedValueOnce(bookedGame()).mockResolvedValueOnce(null);

    const response = await start();

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
  });

  test("Returns 500 when the update throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedStartBookedGame.mockRejectedValue(new Error("Database unavailable"));

    const response = await start();

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: startGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame({ status: GameStatus.BOOKED }));
    mockedIsUserInGame.mockResolvedValue(true);
  });

  test("Returns the started game", async () => {
    const started = existingGame({ status: GameStatus.BOOKED, startsAt: new Date() });
    mockedStartBookedGame.mockResolvedValue(started);

    await expect(startGame(editGameId, userId)).resolves.toEqual(started);
  });

  test("Throws when the game is not booked", async () => {
    mockedFetchGame.mockResolvedValue(existingGame({ status: GameStatus.OPEN }));

    await expect(startGame(editGameId, userId)).rejects.toMatchObject(gameErrors.NotBooked);
    expect(mockedStartBookedGame).not.toHaveBeenCalled();
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedStartBookedGame.mockRejectedValue(error);

    await expect(startGame(editGameId, userId)).rejects.toBe(error);
  });
});

describe("Mocked: PATCH /games/location", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame());
    mockedIsUserInGame.mockResolvedValue(true);
  });

  const validLocationBody = () => ({ gameId: editGameId, courtId: 2 });

  test("Moves the game to the new court", async () => {
    mockedUpdateGame.mockResolvedValue(existingGame({ courtId: 2 }));

    const response = await request(createApp())
      .patch("/games/location")
      .set("Authorization", authHeader)
      .send(validLocationBody());

    expect(response.status).toBe(200);
    expect(response.body.game).toMatchObject({ id: editGameId, courtId: 2 });
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, editGameId);
    expect(mockedUpdateGame).toHaveBeenCalledTimes(1);
    expect(mockedUpdateGame).toHaveBeenCalledWith(editGameId, { courtId: 2 });
  });

  test.each([
    ["missing gameId", { gameId: undefined }],
    ["non-UUID gameId", { gameId: "not-a-uuid" }],
    ["missing courtId", { courtId: undefined }],
    ["non-integer courtId", { courtId: 1.5 }],
    ["non-positive courtId", { courtId: 0 }],
    ["string courtId", { courtId: "2" }],
    ["userId in the body", { userId: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d" }],
    ["unknown field", { startsAt: futureStartsAt() }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await request(createApp())
      .patch("/games/location")
      .set("Authorization", authHeader)
      .send({ ...validLocationBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedFetchGame).not.toHaveBeenCalled();
    expect(mockedUpdateGame).not.toHaveBeenCalled();
  });

  test("Returns 404 when the court does not exist", async () => {
    mockedUpdateGame.mockRejectedValue(prismaError("P2003"));

    const response = await request(createApp())
      .patch("/games/location")
      .set("Authorization", authHeader)
      .send(validLocationBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(commonErrors.CourtNotFound));
  });

  test("Returns 404 when the game is deleted before the update", async () => {
    mockedUpdateGame.mockRejectedValue(prismaError("P2025"));

    const response = await request(createApp())
      .patch("/games/location")
      .set("Authorization", authHeader)
      .send(validLocationBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
  });

  test("Returns 500 for other Prisma errors", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    // A code neither the service nor errorHandler's fallbacks map, e.g. a failed raw query
    mockedUpdateGame.mockRejectedValue(prismaError("P2010"));

    const response = await request(createApp())
      .patch("/games/location")
      .set("Authorization", authHeader)
      .send(validLocationBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

describe("Mocked: changeGameTime", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame());
    mockedIsUserInGame.mockResolvedValue(true);
  });

  const startsAt = new Date(futureStartsAt());

  test("Updates the start time when the user can edit the game", async () => {
    const updatedGame = existingGame({ startsAt });
    mockedUpdateGame.mockResolvedValue(updatedGame);

    await expect(changeGameTime(editGameId, userId, startsAt)).resolves.toEqual(updatedGame);
    expect(mockedUpdateGame).toHaveBeenCalledWith(editGameId, { startsAt });
  });

  test("Throws the reason and does not update when the user cannot edit the game", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    await expect(changeGameTime(editGameId, userId, startsAt)).rejects.toMatchObject(gameErrors.NotInGame);
    expect(mockedUpdateGame).not.toHaveBeenCalled();
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedUpdateGame.mockRejectedValue(error);

    await expect(changeGameTime(editGameId, userId, startsAt)).rejects.toBe(
      error,
    );
  });
});

describe("Mocked: changeGameLocation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame());
    mockedIsUserInGame.mockResolvedValue(true);
  });

  test("Updates the court when the user can edit the game", async () => {
    const updatedGame = existingGame({ courtId: 2 });
    mockedUpdateGame.mockResolvedValue(updatedGame);

    await expect(changeGameLocation(editGameId, userId, 2)).resolves.toEqual(updatedGame);
    expect(mockedUpdateGame).toHaveBeenCalledWith(editGameId, { courtId: 2 });
  });

  test("Throws the reason and does not update when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    await expect(changeGameLocation(editGameId, userId, 2)).rejects.toMatchObject(gameErrors.NotFound);
    expect(mockedUpdateGame).not.toHaveBeenCalled();
  });

  test("Propagates repository errors", async () => {
    const error = new Error("Database unavailable");
    mockedUpdateGame.mockRejectedValue(error);

    await expect(changeGameLocation(editGameId, userId, 2)).rejects.toBe(error);
  });
});

describe("Mocked: moving a game in progress", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame({ status: GameStatus.IN_PROGRESS, startsAt: new Date() }));
    mockedIsUserInGame.mockResolvedValue(true);
  });

  // Input: a player tries to move a game that is being played to another time or court
  // Expected status code: 409
  // Expected behavior: the game keeps its time and court, as the app's hidden buttons say
  // Expected output: { error: "Game has already started", code: "GAME_ALREADY_STARTED" }
  test.each([
    ["PATCH /games/time", { startsAt: futureStartsAt() }, "/games/time"],
    ["PATCH /games/location", { courtId: 2 }, "/games/location"],
  ] as const)("%s rejects a game in progress", async (_route, change, path) => {
    const response = await request(createApp())
      .patch(path)
      .set("Authorization", authHeader)
      .send({ gameId: editGameId, ...change });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.AlreadyStarted));
    expect(mockedUpdateGame).not.toHaveBeenCalled();
  });
});
