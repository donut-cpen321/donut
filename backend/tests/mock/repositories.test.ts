import { fetchCourts } from "../../src/courts/courts.repository";
import {
  Format,
  GameSource,
  GameStatus,
} from "../../src/generated/prisma/enums";
import {
  addPlayerToTeam,
  completeGame,
  createGame,
  deleteGameUser,
  fetchFeedGames,
  fetchGameDetails,
  fetchUserGames,
  fetchUserHistory,
} from "../../src/games/games.repository";
import { applyGameResult } from "../../src/ranking/ranking.service";
import { advanceBracket } from "../../src/tournaments/bracket.repository";

// A fake Prisma client: transactions run their callback with the same client.
// Its mocks are plain properties rather than Prisma's methods, so they are safe to pass to expect()
function mockPrismaClient() {
  const client = {
    court: { findMany: jest.fn() },
    game: {
      create: jest.fn(),
      count: jest.fn(),
      findUnique: jest.fn(),
      findUniqueOrThrow: jest.fn(),
      findMany: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn(),
    },
    gameUser: { count: jest.fn(), create: jest.fn(), delete: jest.fn() },
    gameRound: { deleteMany: jest.fn(), createManyAndReturn: jest.fn() },
    $queryRaw: jest.fn(),
    $transaction: jest.fn(),
  };
  client.$transaction.mockImplementation(
    (callback: (tx: typeof client) => unknown) => callback(client),
  );
  return client;
}

jest.mock("../../src/db", () => ({ prisma: mockPrismaClient() }));

jest.mock("../../src/ranking/ranking.service", () => ({
  applyGameResult: jest.fn(),
}));

jest.mock("../../src/tournaments/bracket.repository", () => ({
  advanceBracket: jest.fn(),
}));

const mockedApplyGameResult = jest.mocked(applyGameResult);
const mockedAdvanceBracket = jest.mocked(advanceBracket);

const db = jest.requireMock<{ prisma: ReturnType<typeof mockPrismaClient> }>(
  "../../src/db",
).prisma;
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
      ratingBefore: null as number | null,
      ratingAfter: null as number | null,
      user: { id: userId, username: "ace", name: "Ace Player", rating: 1520 },
    },
  ],
  rounds: [{ number: 1, team1Score: 6, team2Score: 4 }],
  tournamentMatch: null as {
    round: number;
    tournament: { id: string; name: string };
  } | null,
});

const flattenedPlayers = [
  {
    id: userId,
    username: "ace",
    name: "Ace Player",
    rating: 1520,
    team: 1,
    acceptedAt: new Date("2026-11-01T12:00:00Z"),
    ratingBefore: null,
    ratingAfter: null,
  },
];

// The game details the repository returns for gameRow(): players flattened, and no tournament
const expectedDetails = () => ({
  ...gameRow(),
  tournamentMatch: undefined,
  players: flattenedPlayers,
  tournament: null,
});

// The tagged template given to $queryRaw, as one string
const lockQuery = () => {
  const [strings] = db.$queryRaw.mock.calls[0] as unknown as [TemplateStringsArray, ...unknown[]];
  return strings.join("?");
};

// When a mock was first called, relative to every other mock; fails the test if it was never called
const firstCallOrder = (mock: jest.Mock) => {
  const [order] = mock.mock.invocationCallOrder;
  if (order === undefined) {
    throw new Error("Expected the mock to have been called");
  }
  return order;
};

describe("Mocked: fetchCourts", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Selects each court's location, ordered by name", async () => {
    const courts = [{ id: 1, name: "Kitsilano Beach Park", latitude: 49.27, longitude: -123.15 }];
    db.court.findMany.mockResolvedValue(courts);

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
    db.game.findUnique.mockResolvedValue(gameRow());

    const game = await fetchGameDetails(gameId);

    expect(game).toEqual(expectedDetails());
    const [args] = db.game.findUnique.mock.calls[0] as [Record<string, unknown>];
    expect(args).toMatchObject({
      where: { id: gameId },
      include: {
        court: expect.any(Object),
        players: expect.any(Object),
        rounds: { orderBy: { number: "asc" } },
        tournamentMatch: {
          select: { round: true, tournament: { select: { id: true, name: true } } },
        },
      },
    });
  });

  test("Names the tournament and round of a tournament game", async () => {
    const tournament = { id: "9b8c7d6e-5f4a-4b3c-8d2e-1f0a9b8c7d6e", name: "Sunday Smash" };
    db.game.findUnique.mockResolvedValue({
      ...gameRow(),
      gameSource: GameSource.TOURNAMENT,
      tournamentMatch: { round: 2, tournament },
    });

    const game = await fetchGameDetails(gameId);

    expect(game?.tournament).toEqual({ ...tournament, round: 2 });
    expect(game).not.toHaveProperty("tournamentMatch");
  });

  test("Only selects the user fields that are safe to show other players", async () => {
    db.game.findUnique.mockResolvedValue(gameRow());

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
    db.game.findMany.mockResolvedValue([gameRow()]);

    await expect(fetchFeedGames(now)).resolves.toEqual([
      expectedDetails(),
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
    db.game.findMany.mockResolvedValue([gameRow()]);

    await expect(fetchUserGames(userId, now)).resolves.toEqual([
      expectedDetails(),
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

describe("Mocked: fetchUserHistory", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Fetches the user's completed games, most recent first, with each player's rating change", async () => {
    const row = gameRow();
    db.game.findMany.mockResolvedValue([
      {
        ...row,
        status: GameStatus.COMPLETED,
        players: row.players.map((player) => ({ ...player, ratingBefore: 1500, ratingAfter: 1520 })),
      },
    ]);

    const [game] = await fetchUserHistory(userId);

    expect(game?.status).toBe(GameStatus.COMPLETED);
    expect(game?.players).toEqual([
      { ...flattenedPlayers[0], ratingBefore: 1500, ratingAfter: 1520 },
    ]);
    expect(db.game.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          players: { some: { userId } },
          status: GameStatus.COMPLETED,
        },
        orderBy: { startsAt: "desc" },
      }),
    );
  });
});

describe("Mocked: createGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Creates the game and its creator on team 1 in one nested write", async () => {
    const game = {
      format: Format.DOUBLES,
      startsAt: new Date("2026-11-03T18:00:00Z"),
      courtId: 1,
      gameSource: GameSource.SCHEDULED,
      status: GameStatus.OPEN,
    };
    const created = { id: gameId, ...game, createdAt: new Date() };
    db.game.create.mockResolvedValue(created);

    await expect(createGame(game, userId)).resolves.toBe(created);
    expect(db.game.create).toHaveBeenCalledTimes(1);
    expect(db.game.create).toHaveBeenCalledWith({
      data: {
        ...game,
        players: {
          create: { userId, team: 1, acceptedAt: expect.any(Date) },
        },
      },
    });
    expect(db.gameUser.create).not.toHaveBeenCalled();
  });

  test("Propagates errors without creating anything else", async () => {
    const error = new Error("Database unavailable");
    db.game.create.mockRejectedValueOnce(error);

    await expect(
      createGame(
        {
          format: Format.SINGLES,
          startsAt: new Date("2026-11-03T18:00:00Z"),
          courtId: 1,
          gameSource: GameSource.SCHEDULED,
          status: GameStatus.OPEN,
        },
        userId,
      ),
    ).rejects.toBe(error);
    expect(db.gameUser.create).not.toHaveBeenCalled();
  });
});

describe("Mocked: addPlayerToTeam", () => {
  const now = new Date("2026-11-01T12:00:00Z");

  beforeEach(() => {
    jest.clearAllMocks();
    // The game is still open to join once locked
    db.game.count.mockResolvedValue(1);
  });

  test("Locks the game before checking it and counting the team", async () => {
    db.gameUser.count.mockResolvedValueOnce(0).mockResolvedValueOnce(2);

    await addPlayerToTeam(gameId, userId, 2, 2, now);

    expect(lockQuery()).toMatch(/SELECT id FROM games WHERE id = \?::uuid FOR UPDATE/);
    expect(db.$queryRaw.mock.calls[0]).toContain(gameId);
    expect(firstCallOrder(db.$queryRaw)).toBeLessThan(firstCallOrder(db.game.count));
    expect(firstCallOrder(db.game.count)).toBeLessThan(firstCallOrder(db.gameUser.count));
  });

  test("Checks that the locked game is still an open scheduled game that has not started", async () => {
    db.gameUser.count.mockResolvedValueOnce(0).mockResolvedValueOnce(1);

    await addPlayerToTeam(gameId, userId, 1, 2, now);

    expect(db.game.count).toHaveBeenCalledWith({
      where: {
        id: gameId,
        gameSource: GameSource.SCHEDULED,
        status: GameStatus.OPEN,
        startsAt: { gt: now },
      },
    });
  });

  test("Returns notJoinable without adding the player when the game changed before the lock", async () => {
    // Booked, cancelled, completed or moved by another request after the caller checked it
    db.game.count.mockResolvedValue(0);

    await expect(addPlayerToTeam(gameId, userId, 1, 2, now)).resolves.toBe(
      "notJoinable",
    );
    expect(db.gameUser.count).not.toHaveBeenCalled();
    expect(db.gameUser.create).not.toHaveBeenCalled();
    expect(db.game.update).not.toHaveBeenCalled();
  });

  test("Propagates a failed lock without changing the game", async () => {
    const error = new Error("Lock timeout");
    db.$queryRaw.mockRejectedValueOnce(error);

    await expect(addPlayerToTeam(gameId, userId, 1, 2, now)).rejects.toBe(error);
    expect(db.game.count).not.toHaveBeenCalled();
    expect(db.gameUser.create).not.toHaveBeenCalled();
    expect(db.game.update).not.toHaveBeenCalled();
  });

  test("Adds the player without booking a game that still has free spots", async () => {
    // Team 2 has 1 of 2 players, then the game has 3 of 4
    db.gameUser.count.mockResolvedValueOnce(1).mockResolvedValueOnce(3);

    await expect(addPlayerToTeam(gameId, userId, 2, 2, now)).resolves.toBe("added");
    expect(db.gameUser.count).toHaveBeenNthCalledWith(1, { where: { gameId, team: 2 } });
    expect(db.gameUser.create).toHaveBeenCalledWith({
      data: { gameId, userId, team: 2, acceptedAt: expect.any(Date) },
    });
    expect(db.game.update).not.toHaveBeenCalled();
  });

  test("Books the game when the player takes the last spot", async () => {
    // Team 2 is empty, then the game has 2 of 2 singles players
    db.gameUser.count.mockResolvedValueOnce(0).mockResolvedValueOnce(2);

    await expect(addPlayerToTeam(gameId, userId, 2, 1, now)).resolves.toBe("added");
    expect(db.game.update).toHaveBeenCalledWith({
      where: { id: gameId },
      data: { status: GameStatus.BOOKED },
    });
  });

  test("Returns teamFull without adding the player when the team is full", async () => {
    db.gameUser.count.mockResolvedValueOnce(2);

    await expect(addPlayerToTeam(gameId, userId, 1, 2, now)).resolves.toBe("teamFull");
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
    db.gameUser.delete.mockRejectedValueOnce(error);

    await expect(deleteGameUser(gameId, userId)).rejects.toBe(error);
    expect(db.game.updateMany).not.toHaveBeenCalled();
  });
});

describe("Mocked: completeGame", () => {
  const rounds: [number, number][] = [
    [6, 4],
    [6, 3],
  ];
  const ratingChanges = [{ userId, ratingBefore: 1500, ratingAfter: 1512 }];

  beforeEach(() => {
    jest.clearAllMocks();
    db.game.updateMany.mockResolvedValue({ count: 1 });
    db.gameRound.createManyAndReturn.mockResolvedValue([]);
    mockedApplyGameResult.mockResolvedValue(ratingChanges);
  });

  test("Rates a tournament game and then moves its winner on in the same transaction", async () => {
    const game = { ...gameRow(), gameSource: GameSource.TOURNAMENT, status: GameStatus.COMPLETED };
    db.game.findUniqueOrThrow.mockResolvedValue(game);

    const result = await completeGame(gameId, rounds);

    expect(result).toEqual({ game, rounds: [], ratingChanges });
    expect(mockedAdvanceBracket).toHaveBeenCalledWith(db, gameId, rounds, expect.any(Date));
    expect(firstCallOrder(mockedApplyGameResult)).toBeLessThan(firstCallOrder(mockedAdvanceBracket));
  });

  test("Does not touch any bracket for a scheduled game", async () => {
    db.game.findUniqueOrThrow.mockResolvedValue({ ...gameRow(), status: GameStatus.COMPLETED });

    await completeGame(gameId, rounds);

    expect(mockedApplyGameResult).toHaveBeenCalledWith(db, gameId, Format.DOUBLES, rounds);
    expect(mockedAdvanceBracket).not.toHaveBeenCalled();
  });

  test("Returns null without rating or advancing when the game is already closed", async () => {
    db.game.updateMany.mockResolvedValue({ count: 0 });

    await expect(completeGame(gameId, rounds)).resolves.toBeNull();
    expect(mockedApplyGameResult).not.toHaveBeenCalled();
    expect(mockedAdvanceBracket).not.toHaveBeenCalled();
  });
});
