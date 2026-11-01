import { fetchCourts } from "../../src/courts/courts.repository";
import { prisma } from "../../src/db";
import {
  Format,
  GameSource,
  GameStatus,
} from "../../src/generated/prisma/enums";
import {
  addPlayerToTeam,
  deleteGameUser,
  fetchFeedGames,
  fetchGameDetails,
  fetchUserGames,
} from "../../src/games/games.repository";

// A fake Prisma client: transactions run their callback with the same client
jest.mock("../../src/db", () => {
  const client = {
    court: { findMany: jest.fn() },
    game: {
      findUnique: jest.fn(),
      findMany: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn(),
    },
    gameUser: { count: jest.fn(), create: jest.fn(), delete: jest.fn() },
    $queryRaw: jest.fn(),
    $transaction: jest.fn(),
  };
  client.$transaction.mockImplementation(
    (callback: (tx: typeof client) => unknown) => callback(client),
  );
  return { prisma: client };
});

const db = jest.mocked(prisma, { shallow: false });
const gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
const userId = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

const gameRow = () => ({
  id: gameId,
  format: Format.DOUBLES,
  startsAt: new Date("2026-11-03T18:00:00Z"),
  courtId: 1,
  gameSource: GameSource.SCHEDULED,
  status: GameStatus.OPEN,
  createdAt: new Date("2026-11-01T12:00:00Z"),
  court: { id: 1, name: "Kitsilano Beach Park", latitude: 49.27, longitude: -123.15 },
  players: [
    {
      team: 1,
      acceptedAt: new Date("2026-11-01T12:00:00Z"),
      user: { id: userId, username: "ace", name: "Ace Player", rating: 1520 },
    },
  ],
  rounds: [{ number: 1, team1Score: 6, team2Score: 4 }],
});

const flattenedPlayers = [
  {
    id: userId,
    username: "ace",
    name: "Ace Player",
    rating: 1520,
    team: 1,
    acceptedAt: new Date("2026-11-01T12:00:00Z"),
  },
];

// The tagged template given to $queryRaw, as one string
const lockQuery = () => {
  const [strings] = db.$queryRaw.mock.calls[0] as unknown as [TemplateStringsArray, ...unknown[]];
  return strings.join("?");
};

describe("Mocked: fetchCourts", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Selects each court's location, ordered by name", async () => {
    const courts = [{ id: 1, name: "Kitsilano Beach Park", latitude: 49.27, longitude: -123.15 }];
    db.court.findMany.mockResolvedValue(courts as never);

    await expect(fetchCourts()).resolves.toBe(courts);
    expect(db.court.findMany).toHaveBeenCalledWith({
      select: { id: true, name: true, latitude: true, longitude: true },
      orderBy: { name: "asc" },
    });
  });
});

describe("Mocked: fetchGameDetails", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Returns the game with each player's user flattened into the player", async () => {
    db.game.findUnique.mockResolvedValue(gameRow() as never);

    const game = await fetchGameDetails(gameId);

    expect(game).toEqual({ ...gameRow(), players: flattenedPlayers });
    const [args] = db.game.findUnique.mock.calls[0] as [Record<string, unknown>];
    expect(args).toMatchObject({
      where: { id: gameId },
      include: {
        court: expect.any(Object),
        players: expect.any(Object),
        rounds: { orderBy: { number: "asc" } },
      },
    });
  });

  test("Only selects the user fields that are safe to show other players", async () => {
    db.game.findUnique.mockResolvedValue(gameRow() as never);

    await fetchGameDetails(gameId);

    const [args] = db.game.findUnique.mock.calls[0] as [
      { include: { players: { select: { user: { select: Record<string, boolean> } } } } },
    ];
    expect(args.include.players.select.user.select).toEqual({
      id: true,
      username: true,
      name: true,
      rating: true,
    });
  });

  test("Returns null when the game does not exist", async () => {
    db.game.findUnique.mockResolvedValue(null);

    await expect(fetchGameDetails(gameId)).resolves.toBeNull();
  });
});

describe("Mocked: fetchFeedGames", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Fetches open scheduled games that start after now, soonest first", async () => {
    const now = new Date("2026-11-01T12:00:00Z");
    db.game.findMany.mockResolvedValue([gameRow()] as never);

    await expect(fetchFeedGames(now)).resolves.toEqual([
      { ...gameRow(), players: flattenedPlayers },
    ]);
    expect(db.game.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          gameSource: GameSource.SCHEDULED,
          status: GameStatus.OPEN,
          startsAt: { gt: now },
        },
        orderBy: { startsAt: "asc" },
      }),
    );
  });
});

describe("Mocked: fetchUserGames", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Fetches the user's booked games and open games that start after now", async () => {
    const now = new Date("2026-11-01T12:00:00Z");
    db.game.findMany.mockResolvedValue([gameRow()] as never);

    await expect(fetchUserGames(userId, now)).resolves.toEqual([
      { ...gameRow(), players: flattenedPlayers },
    ]);
    expect(db.game.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          players: { some: { userId } },
          OR: [
            { status: GameStatus.BOOKED },
            { status: GameStatus.OPEN, startsAt: { gt: now } },
          ],
        },
        orderBy: { startsAt: "asc" },
      }),
    );
  });
});

describe("Mocked: addPlayerToTeam", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Locks the game before counting the team", async () => {
    db.gameUser.count.mockResolvedValueOnce(0).mockResolvedValueOnce(2);

    await addPlayerToTeam(gameId, userId, 2, 2);

    expect(lockQuery()).toMatch(/SELECT id FROM games WHERE id = \?::uuid FOR UPDATE/);
    expect(db.$queryRaw.mock.calls[0]).toContain(gameId);
    expect(db.$queryRaw.mock.invocationCallOrder[0]).toBeLessThan(
      db.gameUser.count.mock.invocationCallOrder[0] as number,
    );
  });

  test("Adds the player without booking a game that still has free spots", async () => {
    // Team 2 has 1 of 2 players, then the game has 3 of 4
    db.gameUser.count.mockResolvedValueOnce(1).mockResolvedValueOnce(3);

    await expect(addPlayerToTeam(gameId, userId, 2, 2)).resolves.toBe(true);
    expect(db.gameUser.count).toHaveBeenNthCalledWith(1, { where: { gameId, team: 2 } });
    expect(db.gameUser.create).toHaveBeenCalledWith({
      data: { gameId, userId, team: 2, acceptedAt: expect.any(Date) },
    });
    expect(db.game.update).not.toHaveBeenCalled();
  });

  test("Books the game when the player takes the last spot", async () => {
    // Team 2 is empty, then the game has 2 of 2 singles players
    db.gameUser.count.mockResolvedValueOnce(0).mockResolvedValueOnce(2);

    await expect(addPlayerToTeam(gameId, userId, 2, 1)).resolves.toBe(true);
    expect(db.game.update).toHaveBeenCalledWith({
      where: { id: gameId },
      data: { status: GameStatus.BOOKED },
    });
  });

  test("Returns false without adding the player when the team is full", async () => {
    db.gameUser.count.mockResolvedValueOnce(2);

    await expect(addPlayerToTeam(gameId, userId, 1, 2)).resolves.toBe(false);
    expect(db.gameUser.create).not.toHaveBeenCalled();
    expect(db.game.update).not.toHaveBeenCalled();
  });
});

describe("Mocked: deleteGameUser", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Locks the game, removes the player and reopens a booked scheduled game", async () => {
    await deleteGameUser(gameId, userId);

    expect(lockQuery()).toMatch(/FOR UPDATE/);
    expect(db.gameUser.delete).toHaveBeenCalledWith({
      where: { gameId_userId: { gameId, userId } },
    });
    expect(db.game.updateMany).toHaveBeenCalledWith({
      where: {
        id: gameId,
        gameSource: GameSource.SCHEDULED,
        status: GameStatus.BOOKED,
      },
      data: { status: GameStatus.OPEN },
    });
  });

  test("Does not reopen the game when the player was not in it", async () => {
    const error = new Error("Record to delete does not exist");
    db.gameUser.delete.mockRejectedValueOnce(error as never);

    await expect(deleteGameUser(gameId, userId)).rejects.toBe(error);
    expect(db.game.updateMany).not.toHaveBeenCalled();
  });
});
