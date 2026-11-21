import { fetchCourts } from "../../src/courts/courts.repository";
import {
  cancelMatchesStartingBefore,
  hasActiveMatch,
} from "../../src/matchmaking/matchmaking.repository";
import {
  Format,
  GameSource,
  GameStatus,
} from "../../src/generated/prisma/enums";
import {
  HOURS_PER_GAME,
  acceptMatch,
  addPlayerToTeam,
  cancelMatchedGame,
  createGame,
  deleteGame,
  deleteGameUser,
  fetchFeedGames,
  fetchGameDetails,
  fetchUserGames,
  fetchUserHistory,
  recordScore,
  replaceGameEquipment,
  startBookedGame,
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
      updateManyAndReturn: jest.fn(),
      delete: jest.fn(),
    },
    gameUser: {
      count: jest.fn(),
      create: jest.fn(),
      delete: jest.fn(),
      deleteMany: jest.fn(),
      updateMany: jest.fn(),
    },
    gameRound: { deleteMany: jest.fn(), createManyAndReturn: jest.fn() },
    scoreProposal: { upsert: jest.fn(), deleteMany: jest.fn() },
    gameEquipment: { deleteMany: jest.fn(), createMany: jest.fn() },
    equipment: { count: jest.fn(), updateMany: jest.fn() },
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
      scoreConfirmedAt: null as Date | null,
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
  scoreProposal: null as { rounds: unknown; proposedById: string; proposedAt: Date } | null,
  // Each item is wrapped in its game_equipment row, as Prisma returns it
  equipment: [] as { equipment: { id: number; userId: string; type: string; name: string } }[],
});

const flattenedPlayers = [
  {
    id: userId,
    username: "ace",
    name: "Ace Player",
    rating: 1520,
    team: 1,
    acceptedAt: new Date("2026-11-01T12:00:00Z"),
    scoreConfirmedAt: null,
    ratingBefore: null,
    ratingAfter: null,
  },
];

// The game details the repository returns for gameRow(): players flattened, no equipment and no tournament
const expectedDetails = () => ({
  ...gameRow(),
  tournamentMatch: undefined,
  players: flattenedPlayers,
  tournament: null,
  scoreProposal: null,
  equipment: [],
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

  // Input: a game whose players picked equipment, each item wrapped in its game_equipment row
  // Expected behavior: the items come back as a flat list that says which player uses each one
  test("Lists the equipment used in the game, each with its player's user ID", async () => {
    const racket = { id: 7, userId, type: "RACKET", name: "Training racket" };
    const shoes = { id: 9, userId: "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", type: "SHOES", name: "Court shoes" };
    db.game.findUnique.mockResolvedValue({ ...gameRow(), equipment: [{ equipment: racket }, { equipment: shoes }] });

    const game = await fetchGameDetails(gameId);

    expect(game?.equipment).toEqual([racket, shoes]);
    const [args] = db.game.findUnique.mock.calls[0] as [Record<string, unknown>];
    expect(args).toMatchObject({
      include: {
        equipment: { select: { equipment: { select: { id: true, userId: true, type: true, name: true } } } },
      },
    });
  });

  // Input: a game whose score waits for confirmation, stored as [team 1, team 2] pairs
  // Expected behavior: the rounds come back shaped like the game's own rounds, numbered from 1
  test("Returns the score waiting for confirmation with numbered rounds", async () => {
    const proposedAt = new Date("2026-11-03T19:30:00Z");
    db.game.findUnique.mockResolvedValue({
      ...gameRow(),
      scoreProposal: { rounds: [[6, 4], [3, 6], [7, 5]], proposedById: userId, proposedAt },
    });

    const game = await fetchGameDetails(gameId);

    expect(game?.scoreProposal).toEqual({
      proposedById: userId,
      proposedAt,
      rounds: [
        { number: 1, team1Score: 6, team2Score: 4 },
        { number: 2, team1Score: 3, team2Score: 6 },
        { number: 3, team1Score: 7, team2Score: 5 },
      ],
    });
  });

  test("Returns no rounds for a stored score that is not a list of pairs", async () => {
    db.game.findUnique.mockResolvedValue({
      ...gameRow(),
      scoreProposal: { rounds: { team1: 6 }, proposedById: userId, proposedAt: new Date() },
    });

    const game = await fetchGameDetails(gameId);

    expect(game?.scoreProposal?.rounds).toEqual([]);
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
      photoUrl: true,
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
            { status: { in: [GameStatus.BOOKED, GameStatus.IN_PROGRESS] } },
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

describe("Mocked: deleteGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: an open, booked or started game with players (and possibly rounds, a score waiting, or equipment)
  // Expected behavior: the game is locked, then its rows in the tables that point at it are deleted before the game,
  // in one transaction
  // Expected output: true
  test("Deletes the game's players, rounds, score waiting and equipment before the game", async () => {
    const order: string[] = [];
    db.game.count.mockResolvedValueOnce(1);
    db.gameUser.deleteMany.mockImplementationOnce(() => Promise.resolve(order.push("players")));
    db.gameRound.deleteMany.mockImplementationOnce(() => Promise.resolve(order.push("rounds")));
    db.scoreProposal.deleteMany.mockImplementationOnce(() => Promise.resolve(order.push("score")));
    db.gameEquipment.deleteMany.mockImplementationOnce(() => Promise.resolve(order.push("equipment")));
    db.game.delete.mockImplementationOnce(() => Promise.resolve(order.push("game")));

    await expect(deleteGame(gameId)).resolves.toBe(true);

    expect(lockQuery()).toMatch(/FOR UPDATE/);
    expect(db.game.count).toHaveBeenCalledWith({
      where: { id: gameId, status: { in: [GameStatus.OPEN, GameStatus.BOOKED, GameStatus.IN_PROGRESS] } },
    });
    expect(db.$transaction).toHaveBeenCalledTimes(1);
    expect(db.gameUser.deleteMany).toHaveBeenCalledWith({ where: { gameId } });
    expect(db.gameRound.deleteMany).toHaveBeenCalledWith({ where: { gameId } });
    expect(db.scoreProposal.deleteMany).toHaveBeenCalledWith({ where: { gameId } });
    expect(db.gameEquipment.deleteMany).toHaveBeenCalledWith({ where: { gameId } });
    expect(db.game.delete).toHaveBeenCalledWith({ where: { id: gameId } });
    expect(order).toEqual(["players", "rounds", "score", "equipment", "game"]);
  });

  // Input: a game that is completed or cancelled, or no longer exists
  // Expected behavior: nothing is deleted, so a completed game keeps its score and rating changes
  // Expected output: false
  test("Keeps a completed or cancelled game", async () => {
    db.game.count.mockResolvedValueOnce(0);

    await expect(deleteGame(gameId)).resolves.toBe(false);

    expect(db.gameUser.deleteMany).not.toHaveBeenCalled();
    expect(db.gameRound.deleteMany).not.toHaveBeenCalled();
    expect(db.gameEquipment.deleteMany).not.toHaveBeenCalled();
    expect(db.game.delete).not.toHaveBeenCalled();
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

  // Input: a player leaves a game where they picked equipment
  // Expected behavior: only their own equipment is taken out of the game, so it isn't counted when the game completes
  test("Takes the leaving player's equipment out of the game", async () => {
    await deleteGameUser(gameId, userId);

    expect(db.gameEquipment.deleteMany).toHaveBeenCalledTimes(1);
    expect(db.gameEquipment.deleteMany).toHaveBeenCalledWith({ where: { gameId, equipment: { userId } } });
  });

  test("Does not reopen the game when the player was not in it", async () => {
    const error = new Error("Record to delete does not exist");
    db.gameUser.delete.mockRejectedValueOnce(error);

    await expect(deleteGameUser(gameId, userId)).rejects.toBe(error);
    expect(db.game.updateMany).not.toHaveBeenCalled();
    expect(db.gameEquipment.deleteMany).not.toHaveBeenCalled();
  });
});

describe("Mocked: recordScore", () => {
  const rounds: [number, number][] = [
    [6, 4],
    [6, 3],
  ];
  const now = new Date("2026-11-03T19:30:00Z");
  const ratingChanges = [{ userId, ratingBefore: 1500, ratingAfter: 1512 }];

  // The locked game, in progress, with the given score waiting (or none)
  const inProgress = (waiting: [number, number][] | null) => ({
    status: GameStatus.IN_PROGRESS,
    scoreProposal: waiting === null ? null : { rounds: waiting },
  });

  beforeEach(() => {
    jest.clearAllMocks();
    db.game.findUnique.mockResolvedValue(inProgress(null));
    db.gameUser.updateMany.mockResolvedValue({ count: 1 });
    // Another player still has to agree
    db.gameUser.count.mockResolvedValue(1);
    db.game.update.mockResolvedValue({ ...gameRow(), status: GameStatus.COMPLETED });
    db.gameRound.createManyAndReturn.mockResolvedValue([]);
    mockedApplyGameResult.mockResolvedValue(ratingChanges);
  });

  // Input: a game that is gone, or not in progress (not started, completed or cancelled), once locked
  // Expected behavior: nothing is written
  // Expected output: "notInProgress"
  test.each([
    ["gone", null],
    ["completed", { status: GameStatus.COMPLETED, scoreProposal: null }],
    ["booked", { status: GameStatus.BOOKED, scoreProposal: null }],
  ])("Saves nothing for a game that is %s", async (_description, game) => {
    db.game.findUnique.mockResolvedValue(game);

    await expect(recordScore(gameId, userId, rounds, false, now)).resolves.toEqual({ outcome: "notInProgress" });
    expect(lockQuery()).toMatch(/FOR UPDATE/);
    expect(db.scoreProposal.upsert).not.toHaveBeenCalled();
    expect(db.gameUser.updateMany).not.toHaveBeenCalled();
  });

  // Input: the first score reported for a game, with another player still to agree
  // Expected behavior: the score is saved as waiting, every other player's agreement is reset, the reporter agrees
  // Expected output: "proposed"
  test("Saves a new score as waiting, with only its reporter agreeing", async () => {
    await expect(recordScore(gameId, userId, rounds, false, now)).resolves.toEqual({ outcome: "proposed" });

    expect(db.scoreProposal.upsert).toHaveBeenCalledWith({
      where: { gameId },
      create: { gameId, proposedById: userId, rounds, proposedAt: now },
      update: { proposedById: userId, rounds, proposedAt: now },
    });
    expect(db.gameUser.updateMany).toHaveBeenCalledWith({
      where: { gameId, userId: { not: userId } },
      data: { scoreConfirmedAt: null },
    });
    expect(db.gameUser.updateMany).toHaveBeenCalledWith({ where: { gameId, userId }, data: { scoreConfirmedAt: now } });
    expect(db.game.update).not.toHaveBeenCalled();
    // The equipment is only counted once the game completes
    expect(db.equipment.updateMany).not.toHaveBeenCalled();
  });

  // Input: a score different from the one waiting
  // Expected behavior: it replaces the waiting score and everyone else has to agree again
  // Expected output: "proposed"
  test("Replaces a different score waiting and resets the others' agreement", async () => {
    db.game.findUnique.mockResolvedValue(inProgress([[6, 4], [3, 6], [6, 2]]));

    await expect(recordScore(gameId, userId, rounds, false, now)).resolves.toEqual({ outcome: "proposed" });
    expect(db.scoreProposal.upsert).toHaveBeenCalledTimes(1);
    expect(db.gameUser.updateMany).toHaveBeenCalledWith({
      where: { gameId, userId: { not: userId } },
      data: { scoreConfirmedAt: null },
    });
  });

  // Input: the score waiting, reported or confirmed again, while another player still has to agree
  // Expected behavior: only this player's agreement is saved; the score and the others' agreement stay
  // Expected output: "confirmed"
  test.each([
    ["reporting", false],
    ["confirming", true],
  ])("Agrees with the score waiting when %s the same score", async (_description, confirming) => {
    db.game.findUnique.mockResolvedValue(inProgress(rounds));

    await expect(recordScore(gameId, userId, rounds, confirming, now)).resolves.toEqual({ outcome: "confirmed" });
    expect(db.scoreProposal.upsert).not.toHaveBeenCalled();
    expect(db.gameUser.updateMany).toHaveBeenCalledTimes(1);
    expect(db.gameUser.updateMany).toHaveBeenCalledWith({
      where: { gameId, userId, scoreConfirmedAt: null },
      data: { scoreConfirmedAt: now },
    });
    expect(db.gameUser.count).toHaveBeenCalledWith({ where: { gameId, scoreConfirmedAt: null } });
  });

  // Input: confirming when no score is waiting, or a score different from the one waiting
  // Expected behavior: nothing is written, so a player never proposes a score by confirming a stale one
  // Expected output: "noProposal" or "scoreChanged"
  test.each([
    ["no score is waiting", null, "noProposal"],
    ["a different score is waiting", [[7, 5]] as [number, number][], "scoreChanged"],
  ])("Saves nothing when confirming and %s", async (_description, waiting, outcome) => {
    db.game.findUnique.mockResolvedValue(inProgress(waiting));

    await expect(recordScore(gameId, userId, rounds, true, now)).resolves.toEqual({ outcome });
    expect(db.scoreProposal.upsert).not.toHaveBeenCalled();
    expect(db.gameUser.updateMany).not.toHaveBeenCalled();
  });

  // Input: the last player agrees with the score waiting
  // Expected behavior: in the same transaction the game is completed, the score waiting is deleted, the rounds are
  // saved and the ratings updated
  // Expected output: "completed" with the completed game, its rounds and rating changes
  test("Completes the game once the last player agrees", async () => {
    db.game.findUnique.mockResolvedValue(inProgress(rounds));
    db.gameUser.count.mockResolvedValue(0);
    const savedRounds = [{ gameId, number: 1, team1Score: 6, team2Score: 4 }];
    db.gameRound.createManyAndReturn.mockResolvedValue(savedRounds);

    const result = await recordScore(gameId, userId, rounds, true, now);

    expect(result).toEqual({
      outcome: "completed",
      completed: { game: { ...gameRow(), status: GameStatus.COMPLETED }, rounds: savedRounds, ratingChanges },
    });
    expect(db.game.update).toHaveBeenCalledWith({ where: { id: gameId }, data: { status: GameStatus.COMPLETED } });
    expect(db.scoreProposal.deleteMany).toHaveBeenCalledWith({ where: { gameId } });
    expect(db.gameRound.deleteMany).toHaveBeenCalledWith({ where: { gameId } });
    expect(mockedApplyGameResult).toHaveBeenCalledWith(db, gameId, Format.DOUBLES, rounds);
    expect(db.equipment.updateMany).toHaveBeenCalledTimes(1);
    expect(db.equipment.updateMany).toHaveBeenCalledWith({
      where: { games: { some: { gameId } } },
      data: { gamesUsed: { increment: 1 }, hoursUsed: { increment: HOURS_PER_GAME } },
    });
    expect(mockedAdvanceBracket).not.toHaveBeenCalled();
    expect(db.$transaction).toHaveBeenCalledTimes(1);
  });

  test("Rates a tournament game and then moves its winner on in the same transaction", async () => {
    db.game.findUnique.mockResolvedValue(inProgress(rounds));
    db.gameUser.count.mockResolvedValue(0);
    db.game.update.mockResolvedValue({ ...gameRow(), gameSource: GameSource.TOURNAMENT });

    await recordScore(gameId, userId, rounds, true, now);

    expect(mockedAdvanceBracket).toHaveBeenCalledWith(db, gameId, rounds, now);
    expect(firstCallOrder(mockedApplyGameResult)).toBeLessThan(firstCallOrder(mockedAdvanceBracket));
  });

  // Input: the last player agrees, but the game's teams aren't full, so the rating update throws
  // Expected behavior: the error propagates out of the transaction, so the equipment counts roll back with it
  test("Does not count equipment when the game cannot be completed", async () => {
    db.game.findUnique.mockResolvedValue(inProgress(rounds));
    db.gameUser.count.mockResolvedValue(0);
    const error = new Error("Teams are not full");
    mockedApplyGameResult.mockRejectedValueOnce(error);

    await expect(recordScore(gameId, userId, rounds, true, now)).rejects.toBe(error);
    expect(db.equipment.updateMany).not.toHaveBeenCalled();
  });
});

describe("Mocked: replaceGameEquipment", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // The game is open once locked, the user is in it, and every item they pick is theirs and active
    db.game.count.mockResolvedValue(1);
    db.gameUser.count.mockResolvedValue(1);
    db.equipment.count.mockResolvedValue(2);
  });

  // Input: two of the user's active items
  // Expected behavior: the game is locked and checked, the user's earlier picks are deleted and the new ones saved,
  // in one transaction, without touching other players' equipment
  // Expected output: "saved"
  test("Replaces the user's equipment in the game under the game's lock", async () => {
    const order: string[] = [];
    db.gameEquipment.deleteMany.mockImplementationOnce(() => Promise.resolve(order.push("delete")));
    db.gameEquipment.createMany.mockImplementationOnce(() => Promise.resolve(order.push("create")));

    await expect(replaceGameEquipment(gameId, userId, [7, 9])).resolves.toBe("saved");

    expect(lockQuery()).toMatch(/SELECT id FROM games WHERE id = \?::uuid FOR UPDATE/);
    expect(firstCallOrder(db.$queryRaw)).toBeLessThan(firstCallOrder(db.game.count));
    expect(db.game.count).toHaveBeenCalledWith({
      where: { id: gameId, status: { in: [GameStatus.OPEN, GameStatus.BOOKED, GameStatus.IN_PROGRESS] } },
    });
    expect(db.gameUser.count).toHaveBeenCalledWith({ where: { gameId, userId } });
    expect(db.equipment.count).toHaveBeenCalledWith({ where: { id: { in: [7, 9] }, userId, retiredAt: null } });
    expect(db.gameEquipment.deleteMany).toHaveBeenCalledWith({ where: { gameId, equipment: { userId } } });
    expect(db.gameEquipment.createMany).toHaveBeenCalledWith({
      data: [
        { gameId, equipmentId: 7 },
        { gameId, equipmentId: 9 },
      ],
    });
    expect(order).toEqual(["delete", "create"]);
    expect(db.$transaction).toHaveBeenCalledTimes(1);
  });

  // Input: an empty list
  // Expected behavior: the user's earlier picks are deleted and nothing is added
  // Expected output: "saved"
  test("Clears the user's equipment when given an empty list", async () => {
    db.equipment.count.mockResolvedValue(0);

    await expect(replaceGameEquipment(gameId, userId, [])).resolves.toBe("saved");
    expect(db.gameEquipment.deleteMany).toHaveBeenCalledWith({ where: { gameId, equipment: { userId } } });
    expect(db.gameEquipment.createMany).toHaveBeenCalledWith({ data: [] });
  });

  // Input: a game that is gone, completed or cancelled once locked
  // Expected behavior: nothing is written, so a completed game's equipment can't change after it was counted
  // Expected output: "closed"
  test("Saves nothing for a game that is gone, completed or cancelled", async () => {
    db.game.count.mockResolvedValue(0);

    await expect(replaceGameEquipment(gameId, userId, [7, 9])).resolves.toBe("closed");
    expect(db.gameUser.count).not.toHaveBeenCalled();
    expect(db.gameEquipment.deleteMany).not.toHaveBeenCalled();
    expect(db.gameEquipment.createMany).not.toHaveBeenCalled();
  });

  // Input: a user who is not a player in the game
  // Expected behavior: nothing is written
  // Expected output: "notInGame"
  test("Saves nothing for a user who is not in the game", async () => {
    db.gameUser.count.mockResolvedValue(0);

    await expect(replaceGameEquipment(gameId, userId, [7, 9])).resolves.toBe("notInGame");
    expect(db.equipment.count).not.toHaveBeenCalled();
    expect(db.gameEquipment.deleteMany).not.toHaveBeenCalled();
    expect(db.gameEquipment.createMany).not.toHaveBeenCalled();
  });

  // Input: an item that doesn't exist, belongs to another user, or is retired, so fewer items match than were given
  // Expected behavior: nothing is written, so the user's earlier picks stay
  // Expected output: "equipmentNotFound"
  test("Saves nothing when an item is not the user's active equipment", async () => {
    db.equipment.count.mockResolvedValue(1);

    await expect(replaceGameEquipment(gameId, userId, [7, 9])).resolves.toBe("equipmentNotFound");
    expect(db.gameEquipment.deleteMany).not.toHaveBeenCalled();
    expect(db.gameEquipment.createMany).not.toHaveBeenCalled();
  });

  test("Propagates a failed lock without changing anything", async () => {
    const error = new Error("Lock timeout");
    db.$queryRaw.mockRejectedValueOnce(error);

    await expect(replaceGameEquipment(gameId, userId, [7, 9])).rejects.toBe(error);
    expect(db.game.count).not.toHaveBeenCalled();
    expect(db.gameEquipment.deleteMany).not.toHaveBeenCalled();
  });
});

describe("Mocked: acceptMatch", () => {
  const now = new Date("2026-11-02T12:00:00Z");

  beforeEach(() => {
    jest.clearAllMocks();
    // The game is still a matched game waiting for answers once locked
    db.game.count.mockResolvedValue(1);
    // The player hadn't accepted yet
    db.gameUser.updateMany.mockResolvedValue({ count: 1 });
  });

  // Input: the locked game is not a matched open game starting more than 30 minutes from now
  // Expected behavior: the game is locked and checked, nothing is written
  // Expected output: "notPending"
  test("Returns notPending when the game is not a matched open game before the cutoff", async () => {
    db.game.count.mockResolvedValue(0);

    await expect(acceptMatch(gameId, userId, now)).resolves.toBe("notPending");
    expect(lockQuery()).toMatch(/SELECT id FROM games WHERE id = \?::uuid FOR UPDATE/);
    expect(db.game.count).toHaveBeenCalledWith({
      where: {
        id: gameId,
        gameSource: GameSource.MATCHED,
        status: GameStatus.OPEN,
        startsAt: { gt: new Date("2026-11-02T12:30:00Z") },
      },
    });
    expect(db.gameUser.updateMany).not.toHaveBeenCalled();
  });

  // Input: another player still hasn't accepted
  // Expected behavior: the player's acceptance is saved, the game stays open
  // Expected output: "accepted"
  test("Records the acceptance and keeps the game open while others haven't answered", async () => {
    db.gameUser.count.mockResolvedValue(1);

    await expect(acceptMatch(gameId, userId, now)).resolves.toBe("accepted");
    expect(db.gameUser.updateMany).toHaveBeenCalledWith({
      where: { gameId, userId, acceptedAt: null },
      data: { acceptedAt: now },
    });
    expect(db.gameUser.count).toHaveBeenCalledWith({ where: { gameId, acceptedAt: null } });
    expect(db.game.update).not.toHaveBeenCalled();
  });

  // Input: the player already accepted this match
  // Expected behavior: nothing else is checked or written
  // Expected output: "alreadyAccepted"
  test("Returns alreadyAccepted when the player had already accepted", async () => {
    db.gameUser.updateMany.mockResolvedValue({ count: 0 });

    await expect(acceptMatch(gameId, userId, now)).resolves.toBe("alreadyAccepted");
    expect(db.gameUser.count).not.toHaveBeenCalled();
    expect(db.game.update).not.toHaveBeenCalled();
  });

  // Input: every other player already accepted
  // Expected behavior: the game is booked
  // Expected output: "booked"
  test("Books the game when the last player accepts", async () => {
    db.gameUser.count.mockResolvedValue(0);

    await expect(acceptMatch(gameId, userId, now)).resolves.toBe("booked");
    expect(db.game.update).toHaveBeenCalledWith({
      where: { id: gameId },
      data: { status: GameStatus.BOOKED },
    });
  });
});

describe("Mocked: cancelMatchedGame", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: a matched game that is open or booked
  // Expected behavior: the game is marked cancelled, the row stays
  // Expected output: true
  test("Cancels an open, booked or in-progress matched game", async () => {
    db.game.updateMany.mockResolvedValue({ count: 1 });

    await expect(cancelMatchedGame(gameId)).resolves.toBe(true);
    expect(db.game.updateMany).toHaveBeenCalledWith({
      where: {
        id: gameId,
        gameSource: GameSource.MATCHED,
        status: { in: [GameStatus.OPEN, GameStatus.BOOKED, GameStatus.IN_PROGRESS] },
      },
      data: { status: GameStatus.CANCELLED },
    });
  });

  // Input: a matched game that is already completed or cancelled
  // Expected behavior: nothing changes
  // Expected output: false
  test("Returns false when the game is already closed", async () => {
    db.game.updateMany.mockResolvedValue({ count: 0 });

    await expect(cancelMatchedGame(gameId)).resolves.toBe(false);
  });
});

describe("Mocked: startBookedGame", () => {
  const now = new Date("2026-11-02T12:00:00Z");

  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: a booked game that has not started, before or after its start time
  // Expected behavior: it is in progress and its start time becomes now, only if it is still booked
  // Expected output: the started game
  test("Marks a booked game in progress and moves its start time to now", async () => {
    const started = { ...gameRow(), status: GameStatus.IN_PROGRESS, startsAt: now };
    db.game.updateManyAndReturn.mockResolvedValue([started]);

    await expect(startBookedGame(gameId, now)).resolves.toBe(started);
    expect(db.game.updateManyAndReturn).toHaveBeenCalledWith({
      where: { id: gameId, status: GameStatus.BOOKED },
      data: { startsAt: now, status: GameStatus.IN_PROGRESS },
    });
  });

  // Input: a game that is gone, not booked, or already started
  // Expected behavior: nothing changes
  // Expected output: null
  test("Returns null when no game can be started", async () => {
    db.game.updateManyAndReturn.mockResolvedValue([]);

    await expect(startBookedGame(gameId, now)).resolves.toBeNull();
  });
});

describe("Mocked: hasActiveMatch", () => {
  const now = new Date("2026-11-02T12:00:00Z");

  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: the player is in a matched game that is open, or booked and not started
  // Expected behavior: counts only those matched games of this player
  // Expected output: true
  test("Finds a matched game that is waiting for answers or booked and not started", async () => {
    db.game.count.mockResolvedValue(1);

    await expect(hasActiveMatch(userId, now)).resolves.toBe(true);
    expect(db.game.count).toHaveBeenCalledWith({
      where: {
        gameSource: GameSource.MATCHED,
        players: { some: { userId } },
        OR: [
          { status: GameStatus.OPEN },
          { status: GameStatus.BOOKED, startsAt: { gt: now } },
        ],
      },
    });
  });
});

describe("Mocked: cancelMatchesStartingBefore", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: a cutoff time, two matched games still waiting for answers start before it
  // Expected behavior: only open matched games starting at or before the cutoff are cancelled,
  // then their format, court and players are looked up so the players can be told
  // Expected output: the two cancelled matches
  test("Cancels open matched games starting at or before the cutoff and returns them", async () => {
    const cutoff = new Date("2026-11-02T12:30:00Z");
    const matches = [
      { id: "g1", format: Format.SINGLES, court: { name: "Kitsilano Beach Park" }, players: [{ userId: "u1" }, { userId: "u2" }] },
      { id: "g2", format: Format.SINGLES, court: { name: "UBC Tennis Centre" }, players: [{ userId: "u3" }, { userId: "u4" }] },
    ];
    db.game.updateManyAndReturn.mockResolvedValue([{ id: "g1" }, { id: "g2" }]);
    db.game.findMany.mockResolvedValue(matches);

    await expect(cancelMatchesStartingBefore(cutoff)).resolves.toEqual(matches);
    expect(db.game.updateManyAndReturn).toHaveBeenCalledWith({
      where: {
        gameSource: GameSource.MATCHED,
        status: GameStatus.OPEN,
        startsAt: { lte: cutoff },
      },
      data: { status: GameStatus.CANCELLED },
      select: { id: true },
    });
    expect(db.game.findMany).toHaveBeenCalledWith({
      where: { id: { in: ["g1", "g2"] } },
      select: {
        id: true,
        format: true,
        court: { select: { name: true } },
        players: { select: { userId: true } },
      },
    });
  });

  // Input: a cutoff time, no matched game waiting for answers starts before it
  // Expected behavior: nothing is cancelled and nothing is looked up
  // Expected output: []
  test("Returns no matches and skips the lookup when nothing is due", async () => {
    db.game.updateManyAndReturn.mockResolvedValue([]);

    await expect(cancelMatchesStartingBefore(new Date("2026-11-02T12:30:00Z"))).resolves.toEqual([]);
    expect(db.game.findMany).not.toHaveBeenCalled();
  });
});
