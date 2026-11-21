import request from "supertest";
import { createApp } from "../../src/app";
import type { Equipment } from "../../src/generated/prisma/client";
import { EquipmentType, GameStatus } from "../../src/generated/prisma/enums";
import {
  addPlayerToTeam,
  createGame,
  fetchGame,
  isUserInGame,
  replaceGameEquipment,
} from "../../src/games/games.repository";
import { gameErrors, joinGame, newGame, setGameEquipment } from "../../src/games/games.service";
import { getEquipment, userErrors } from "../../src/users/users.service";
import { authErrors } from "../../src/auth/requireAuth";
import { commonErrors, errorBody } from "../../src/errors";
import * as changes from "../../src/events/changes";
import {
  authHeader,
  editGameId,
  existingGame,
  joinableGame,
  joinGameId,
  userId,
  validBody,
} from "./gamesFixtures";

// Mocked tests of the equipment a player uses in a game: PUT /games/equipment, and the default equipment a player
// starts with when they create or join a game. The fixtures shared by every games.*.test.ts are in gamesFixtures.ts
jest.mock("../../src/games/games.repository", () => ({
  addPlayerToTeam: jest.fn(),
  createGame: jest.fn(),
  fetchGame: jest.fn(),
  isUserInGame: jest.fn(),
  replaceGameEquipment: jest.fn(),
}));

jest.mock("../../src/users/users.service", () => ({
  ...jest.requireActual<typeof import("../../src/users/users.service")>(
    "../../src/users/users.service",
  ),
  getEquipment: jest.fn(),
}));

const mockedAddPlayerToTeam = jest.mocked(addPlayerToTeam);
const mockedCreateGame = jest.mocked(createGame);
const mockedFetchGame = jest.mocked(fetchGame);
const mockedIsUserInGame = jest.mocked(isUserInGame);
const mockedReplaceGameEquipment = jest.mocked(replaceGameEquipment);
const mockedGetEquipment = jest.mocked(getEquipment);

describe("Mocked: PUT /games/equipment", () => {
  let changeSpy: jest.SpiedFunction<typeof changes.gameChanged>;

  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame());
    mockedIsUserInGame.mockResolvedValue(true);
    mockedReplaceGameEquipment.mockResolvedValue("saved");
    changeSpy = jest.spyOn(changes, "gameChanged");
  });

  afterEach(() => {
    changeSpy.mockRestore();
  });

  const validEquipmentBody = () => ({ gameId: editGameId, equipmentIds: [7, 9] });

  const putEquipment = (body: object = validEquipmentBody()) =>
    request(createApp())
      .put("/games/equipment")
      .set("Authorization", authHeader)
      .send(body);

  // Input: a player of an open game picks two of their items
  // Expected status code: 204
  // Expected behavior: the selection is saved for the authenticated user, and the game's watchers are told
  test("Saves the authenticated user's equipment for the game", async () => {
    const response = await putEquipment();

    expect(response.status).toBe(204);
    expect(response.body).toEqual({});
    expect(mockedIsUserInGame).toHaveBeenCalledWith(userId, editGameId);
    expect(mockedReplaceGameEquipment).toHaveBeenCalledTimes(1);
    expect(mockedReplaceGameEquipment).toHaveBeenCalledWith(editGameId, userId, [7, 9]);
    expect(changeSpy).toHaveBeenCalledWith({ gameId: editGameId });
  });

  test("Clears the user's equipment when given an empty list", async () => {
    const response = await putEquipment({ gameId: editGameId, equipmentIds: [] });

    expect(response.status).toBe(204);
    expect(mockedReplaceGameEquipment).toHaveBeenCalledWith(editGameId, userId, []);
  });

  test.each([GameStatus.OPEN, GameStatus.BOOKED, GameStatus.IN_PROGRESS])(
    "Saves the equipment of a %s game",
    async (status) => {
      mockedFetchGame.mockResolvedValue(existingGame({ status }));

      const response = await putEquipment();

      expect(response.status).toBe(204);
      expect(mockedReplaceGameEquipment).toHaveBeenCalledTimes(1);
    },
  );

  test("Requires authentication", async () => {
    const response = await request(createApp()).put("/games/equipment").send(validEquipmentBody());

    expect(response.status).toBe(401);
    expect(response.body).toEqual(errorBody(authErrors.MissingToken));
    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
  });

  test.each([
    ["missing gameId", { gameId: undefined }],
    ["non-UUID gameId", { gameId: "not-a-uuid" }],
    ["missing equipmentIds", { equipmentIds: undefined }],
    ["equipmentIds that is not a list", { equipmentIds: 7 }],
    ["string item IDs", { equipmentIds: ["7"] }],
    ["item ID 0", { equipmentIds: [0] }],
    ["negative item ID", { equipmentIds: [-1] }],
    ["non-integer item ID", { equipmentIds: [1.5] }],
    ["a repeated item", { equipmentIds: [7, 7] }],
    ["more than 20 items", { equipmentIds: Array.from({ length: 21 }, (_, index) => index + 1) }],
    ["userId in the body", { userId: "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d" }],
    ["unknown field", { equipment: [] }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await putEquipment({ ...validEquipmentBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(response.body.details).toBeDefined();
    expect(mockedFetchGame).not.toHaveBeenCalled();
    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
  });

  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    const response = await putEquipment();

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
  });

  test("Returns 403 when the user is not in the game", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    const response = await putEquipment();

    expect(response.status).toBe(403);
    expect(response.body).toEqual(errorBody(gameErrors.NotInGame));
    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
  });

  test.each([GameStatus.COMPLETED, GameStatus.CANCELLED])("Returns 409 for a %s game", async (status) => {
    mockedFetchGame.mockResolvedValue(existingGame({ status }));

    const response = await putEquipment();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.Closed));
    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
  });

  // Input: the repository finds a problem once it holds the game's lock, after the checks above passed
  // Expected behavior: the same errors as the checks, and the game's watchers are not told
  test.each([
    ["closed", gameErrors.Closed],
    ["notInGame", gameErrors.NotInGame],
    ["equipmentNotFound", userErrors.EquipmentNotFound],
  ] as const)("Maps the repository's %s result to its error", async (result, error) => {
    mockedReplaceGameEquipment.mockResolvedValue(result);

    const response = await putEquipment();

    expect(response.status).toBe(error.status);
    expect(response.body).toEqual(errorBody(error));
    expect(changeSpy).not.toHaveBeenCalled();
  });

  test("Returns 500 when the repository fails", async () => {
    const consoleError = jest.spyOn(console, "error").mockImplementation(() => undefined);
    mockedReplaceGameEquipment.mockRejectedValue(new Error("Database unavailable"));

    const response = await putEquipment();

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    expect(changeSpy).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });
});

describe("Mocked: setGameEquipment", () => {
  let changeSpy: jest.SpiedFunction<typeof changes.gameChanged>;

  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame());
    mockedIsUserInGame.mockResolvedValue(true);
    mockedReplaceGameEquipment.mockResolvedValue("saved");
    changeSpy = jest.spyOn(changes, "gameChanged");
  });

  afterEach(() => {
    changeSpy.mockRestore();
  });

  test("Saves the equipment and tells the game's watchers", async () => {
    await expect(setGameEquipment(editGameId, userId, [7])).resolves.toBeUndefined();
    expect(mockedReplaceGameEquipment).toHaveBeenCalledWith(editGameId, userId, [7]);
    expect(changeSpy).toHaveBeenCalledWith({ gameId: editGameId });
  });

  test("Throws NotFound for a game that does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    await expect(setGameEquipment(editGameId, userId, [7])).rejects.toMatchObject(gameErrors.NotFound);
    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
  });

  test("Throws NotInGame for a user who is not a player", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    await expect(setGameEquipment(editGameId, userId, [7])).rejects.toMatchObject(gameErrors.NotInGame);
    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
  });

  test("Throws Closed for a completed game, so equipment can't change after it was counted", async () => {
    mockedFetchGame.mockResolvedValue(existingGame({ status: GameStatus.COMPLETED }));

    await expect(setGameEquipment(editGameId, userId, [7])).rejects.toMatchObject(gameErrors.Closed);
    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
  });

  test.each([
    ["closed", gameErrors.Closed],
    ["notInGame", gameErrors.NotInGame],
    ["equipmentNotFound", userErrors.EquipmentNotFound],
  ] as const)("Throws the error for the repository's %s result without telling anyone", async (result, error) => {
    mockedReplaceGameEquipment.mockResolvedValue(result);

    await expect(setGameEquipment(editGameId, userId, [7])).rejects.toMatchObject(error);
    expect(changeSpy).not.toHaveBeenCalled();
  });
});

describe("Mocked: default equipment in a new or joined game", () => {
  // A new game as the service gets it, once the request body is parsed
  const gameInput = () => ({ ...validBody(), startsAt: new Date(validBody().startsAt) });

  // One of the user's items
  const item = (id: number, isDefault: boolean): Equipment => ({
    id,
    userId,
    type: EquipmentType.RACKET,
    name: `Racket ${id}`,
    isDefault,
    createdAt: new Date(),
    nextMaintenanceAt: null,
    retiredAt: null,
    hoursUsed: 0,
    gamesUsed: 0,
  });

  beforeEach(() => {
    jest.clearAllMocks();
    mockedCreateGame.mockResolvedValue(existingGame());
    mockedFetchGame.mockResolvedValue(joinableGame());
    mockedAddPlayerToTeam.mockResolvedValue("added");
    mockedReplaceGameEquipment.mockResolvedValue("saved");
  });

  // Input: the creator has two default items and one that isn't
  // Expected behavior: only the default items are used in the new game
  test("Uses the creator's default items in a new game", async () => {
    mockedGetEquipment.mockResolvedValue([item(1, true), item(2, false), item(3, true)]);

    await newGame(gameInput(), userId);

    expect(mockedReplaceGameEquipment).toHaveBeenCalledWith(editGameId, userId, [1, 3]);
  });

  // Input: a player joins team 2 with one default item
  // Expected behavior: their default item is used in the game they joined
  test("Uses a joining player's default items", async () => {
    mockedGetEquipment.mockResolvedValue([item(4, true)]);

    await joinGame(joinGameId, userId, 2);

    expect(mockedReplaceGameEquipment).toHaveBeenCalledWith(joinGameId, userId, [4]);
  });

  // Input: the creator has no default items
  // Expected behavior: nothing is saved for them
  test("Leaves the game's equipment alone without default items", async () => {
    mockedGetEquipment.mockResolvedValue([item(2, false)]);

    await newGame(gameInput(), userId);

    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
  });

  // Input: loading the creator's equipment fails
  // Expected behavior: the game is still created, and the failure is logged
  test("Still creates the game when the equipment can't be loaded", async () => {
    const consoleError = jest.spyOn(console, "error").mockImplementation(() => undefined);
    mockedGetEquipment.mockRejectedValue(new Error("Database unavailable"));

    const game = existingGame();
    mockedCreateGame.mockResolvedValue(game);

    await expect(newGame(gameInput(), userId)).resolves.toBe(game);

    expect(consoleError).toHaveBeenCalled();
    expect(mockedReplaceGameEquipment).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });
});
