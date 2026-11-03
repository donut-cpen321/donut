import {
  Format,
  TournamentStatus,
} from "../../src/generated/prisma/enums";
import {
  drawBracket,
  lockTournament,
} from "../../src/tournaments/bracket.repository";
import {
  createTournament,
  fetchFeedTournaments,
  fetchTournamentDetails,
  fetchUserTournaments,
  markTournamentCancelled,
  registerPlayer,
  unregisterPlayer,
} from "../../src/tournaments/tournaments.repository";

// A fake Prisma client: transactions run their callback with the same client.
// Its mocks are plain properties rather than Prisma's methods, so they are safe to pass to expect()
function mockPrismaClient() {
  const client = {
    tournament: {
      create: jest.fn(),
      findMany: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
    },
    tournamentPlayer: {
      count: jest.fn(),
      create: jest.fn(),
      deleteMany: jest.fn(),
    },
    $transaction: jest.fn(),
  };
  client.$transaction.mockImplementation(
    (callback: (tx: typeof client) => unknown) => callback(client),
  );
  return client;
}

jest.mock("../../src/db", () => ({ prisma: mockPrismaClient() }));

jest.mock("../../src/tournaments/bracket.repository", () => ({
  lockTournament: jest.fn(),
  drawBracket: jest.fn(),
}));

const db = jest.requireMock<{ prisma: ReturnType<typeof mockPrismaClient> }>(
  "../../src/db",
).prisma;
const mockedLockTournament = jest.mocked(lockTournament);
const mockedDrawBracket = jest.mocked(drawBracket);

const tournamentId = "9b8c7d6e-5f4a-4b3c-8d2e-1f0a9b8c7d6e";
const creatorId = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
const userId = "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e";
const now = new Date("2026-11-02T12:00:00Z");
const startsAt = new Date("2026-11-08T17:00:00Z");

const user = (id: string, name: string) => ({ id, username: `${name}@example.com`, name, rating: 1500 });

const summaryRow = (overrides: Record<string, unknown> = {}) => ({
  id: tournamentId,
  name: "Sunday Smash",
  format: Format.SINGLES,
  status: TournamentStatus.REGISTRATION,
  startsAt,
  courtId: 3,
  creatorId,
  winningTeam: null,
  createdAt: now,
  court: { id: 3, name: "Kitsilano Beach Park", latitude: 49.27, longitude: -123.15 },
  creator: user(creatorId, "Alice"),
  players: [{ team: null, joinedAt: now, user: user(creatorId, "Alice") }],
  ...overrides,
});

// summaryRow() as the repository returns it: players flattened and the capacity added
const expectedSummary = (capacity = 8) => ({
  ...summaryRow(),
  capacity,
  players: [{ ...user(creatorId, "Alice"), team: null, joinedAt: now }],
});

// When a mock was first called, relative to every other mock
const firstCallOrder = (mock: jest.Mock) => {
  const [order] = mock.mock.invocationCallOrder;
  if (order === undefined) {
    throw new Error("Expected the mock to have been called");
  }
  return order;
};

// A tournament row as registerPlayer, unregisterPlayer and markTournamentCancelled read it under the lock
const lockedTournament = (overrides: Record<string, unknown> = {}) => ({
  id: tournamentId,
  format: Format.SINGLES,
  courtId: 3,
  startsAt,
  status: TournamentStatus.REGISTRATION,
  creatorId,
  ...overrides,
});

beforeEach(() => {
  jest.clearAllMocks();
});

// Interface createTournament(tournament, creatorId)
describe("Mocked: createTournament", () => {
  test("Creates the tournament with its creator registered in the same write", async () => {
    const input = { name: "Sunday Smash", format: Format.SINGLES, startsAt, courtId: 3 };
    const created = { id: tournamentId, ...input };
    db.tournament.create.mockResolvedValue(created);

    await expect(createTournament(input, creatorId)).resolves.toBe(created);
    expect(db.tournament.create).toHaveBeenCalledWith({
      data: { ...input, creatorId, players: { create: { userId: creatorId } } },
    });
  });
});

// Interface fetchFeedTournaments(now)
describe("Mocked: fetchFeedTournaments", () => {
  test("Fetches tournaments open for registration that start after now, soonest first", async () => {
    db.tournament.findMany.mockResolvedValue([summaryRow()]);

    await expect(fetchFeedTournaments(now)).resolves.toEqual([expectedSummary()]);
    expect(db.tournament.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { status: TournamentStatus.REGISTRATION, startsAt: { gt: now } },
        orderBy: { startsAt: "asc" },
      }),
    );
  });

  test("Only selects the user fields that are safe to show other players, in registration order", async () => {
    db.tournament.findMany.mockResolvedValue([]);

    await fetchFeedTournaments(now);

    const [args] = db.tournament.findMany.mock.calls[0] as [
      {
        include: {
          creator: { select: Record<string, boolean> };
          players: { select: { user: { select: Record<string, boolean> } }; orderBy: unknown };
        };
      },
    ];
    const safeFields = { id: true, username: true, name: true, rating: true };
    expect(args.include.creator.select).toEqual(safeFields);
    expect(args.include.players.select.user.select).toEqual(safeFields);
    expect(args.include.players.orderBy).toEqual([{ joinedAt: "asc" }, { userId: "asc" }]);
  });

  test("Gives a doubles tournament a capacity of 16 players", async () => {
    db.tournament.findMany.mockResolvedValue([summaryRow({ format: Format.DOUBLES })]);

    const [tournament] = await fetchFeedTournaments(now);

    expect(tournament?.capacity).toBe(16);
  });
});

// Interface fetchUserTournaments(userId, now)
describe("Mocked: fetchUserTournaments", () => {
  test("Fetches the user's tournaments in progress, open and not started, or completed in the last day", async () => {
    db.tournament.findMany.mockResolvedValue([summaryRow()]);

    await expect(fetchUserTournaments(userId, now)).resolves.toEqual([expectedSummary()]);
    expect(db.tournament.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: {
          players: { some: { userId } },
          OR: [
            { status: TournamentStatus.IN_PROGRESS },
            { status: TournamentStatus.REGISTRATION, startsAt: { gt: now } },
            {
              status: TournamentStatus.COMPLETED,
              startsAt: { gt: new Date("2026-11-01T12:00:00Z") },
            },
          ],
        },
        orderBy: { startsAt: "asc" },
      }),
    );
  });
});

// Interface fetchTournamentDetails(tournamentId)
describe("Mocked: fetchTournamentDetails", () => {
  test("Returns the tournament with its bracket in round and position order", async () => {
    const matches = [
      { round: 1, position: 0, team1: 1, team2: 8, winner: null, game: null },
    ];
    db.tournament.findUnique.mockResolvedValue(summaryRow({ matches }));

    await expect(fetchTournamentDetails(tournamentId)).resolves.toEqual({
      ...expectedSummary(),
      matches,
    });
    const [args] = db.tournament.findUnique.mock.calls[0] as [
      { where: unknown; include: { matches: { orderBy: unknown; select: { game: unknown } } } },
    ];
    expect(args.where).toEqual({ id: tournamentId });
    expect(args.include.matches.orderBy).toEqual([{ round: "asc" }, { position: "asc" }]);
    expect(args.include.matches.select.game).toBeDefined();
  });

  test("Returns null when the tournament does not exist", async () => {
    db.tournament.findUnique.mockResolvedValue(null);

    await expect(fetchTournamentDetails(tournamentId)).resolves.toBeNull();
  });
});

// Interface registerPlayer(tournamentId, userId, now)
describe("Mocked: registerPlayer", () => {
  beforeEach(() => {
    db.tournament.findUnique.mockResolvedValue(lockedTournament());
    // clearAllMocks keeps queued values a test did not use, so start each test from an empty queue
    db.tournamentPlayer.count.mockReset();
    // Not registered yet, then 5 players once registered
    db.tournamentPlayer.count.mockResolvedValueOnce(0).mockResolvedValueOnce(5);
  });

  test("Registers the user, after locking the tournament, without drawing a bracket that is not full", async () => {
    await expect(registerPlayer(tournamentId, userId, now)).resolves.toBe("registered");

    expect(mockedLockTournament).toHaveBeenCalledWith(db, tournamentId);
    expect(firstCallOrder(mockedLockTournament)).toBeLessThan(firstCallOrder(db.tournament.findUnique));
    expect(db.tournamentPlayer.create).toHaveBeenCalledWith({ data: { tournamentId, userId } });
    expect(mockedDrawBracket).not.toHaveBeenCalled();
  });

  test("Draws the bracket in the same transaction when the registration fills the tournament", async () => {
    db.tournamentPlayer.count.mockReset();
    db.tournamentPlayer.count.mockResolvedValueOnce(0).mockResolvedValueOnce(8);

    await expect(registerPlayer(tournamentId, userId, now)).resolves.toBe("drawn");

    expect(mockedDrawBracket).toHaveBeenCalledWith(db, lockedTournament(), now);
    expect(firstCallOrder(db.tournamentPlayer.create)).toBeLessThan(firstCallOrder(mockedDrawBracket));
  });

  test("Waits for 16 players before drawing a doubles bracket", async () => {
    db.tournament.findUnique.mockResolvedValue(lockedTournament({ format: Format.DOUBLES }));
    db.tournamentPlayer.count.mockReset();
    db.tournamentPlayer.count.mockResolvedValueOnce(0).mockResolvedValueOnce(8);

    await expect(registerPlayer(tournamentId, userId, now)).resolves.toBe("registered");
    expect(mockedDrawBracket).not.toHaveBeenCalled();
  });

  test.each([
    ["notFound", null],
    ["full", lockedTournament({ status: TournamentStatus.IN_PROGRESS })],
    ["notOpen", lockedTournament({ status: TournamentStatus.CANCELLED })],
    ["notOpen", lockedTournament({ status: TournamentStatus.COMPLETED })],
    ["notOpen", lockedTournament({ startsAt: now })],
  ])("Returns %s without registering", async (expected, tournament) => {
    db.tournament.findUnique.mockResolvedValue(tournament);

    await expect(registerPlayer(tournamentId, userId, now)).resolves.toBe(expected);
    expect(db.tournamentPlayer.create).not.toHaveBeenCalled();
  });

  test("Returns alreadyRegistered for a user who is already in the tournament", async () => {
    db.tournamentPlayer.count.mockReset();
    db.tournamentPlayer.count.mockResolvedValue(1);

    await expect(registerPlayer(tournamentId, userId, now)).resolves.toBe("alreadyRegistered");
    expect(db.tournamentPlayer.create).not.toHaveBeenCalled();
  });
});

// Interface unregisterPlayer(tournamentId, userId)
describe("Mocked: unregisterPlayer", () => {
  beforeEach(() => {
    db.tournament.findUnique.mockResolvedValue(lockedTournament());
  });

  test("Removes the user after locking the tournament", async () => {
    db.tournamentPlayer.deleteMany.mockResolvedValue({ count: 1 });

    await expect(unregisterPlayer(tournamentId, userId)).resolves.toBe("left");

    expect(firstCallOrder(mockedLockTournament)).toBeLessThan(firstCallOrder(db.tournament.findUnique));
    expect(db.tournamentPlayer.deleteMany).toHaveBeenCalledWith({ where: { tournamentId, userId } });
  });

  test("Returns notRegistered when the user was not in the tournament", async () => {
    db.tournamentPlayer.deleteMany.mockResolvedValue({ count: 0 });

    await expect(unregisterPlayer(tournamentId, userId)).resolves.toBe("notRegistered");
  });

  test.each([
    ["notFound", null, userId],
    ["notOpen", lockedTournament({ status: TournamentStatus.IN_PROGRESS }), userId],
    ["creator", lockedTournament(), creatorId],
  ])("Returns %s without removing anyone", async (expected, tournament, leaver) => {
    db.tournament.findUnique.mockResolvedValue(tournament);

    await expect(unregisterPlayer(tournamentId, leaver)).resolves.toBe(expected);
    expect(db.tournamentPlayer.deleteMany).not.toHaveBeenCalled();
  });
});

// Interface markTournamentCancelled(tournamentId, userId)
describe("Mocked: markTournamentCancelled", () => {
  beforeEach(() => {
    db.tournament.findUnique.mockResolvedValue(lockedTournament());
  });

  test("Marks the tournament as cancelled after locking it", async () => {
    await expect(markTournamentCancelled(tournamentId, creatorId)).resolves.toBe("cancelled");

    expect(firstCallOrder(mockedLockTournament)).toBeLessThan(firstCallOrder(db.tournament.findUnique));
    expect(db.tournament.update).toHaveBeenCalledWith({
      where: { id: tournamentId },
      data: { status: TournamentStatus.CANCELLED },
    });
  });

  test.each([
    ["notFound", null, creatorId],
    ["notCreator", lockedTournament(), userId],
    ["notOpen", lockedTournament({ status: TournamentStatus.IN_PROGRESS }), creatorId],
  ])("Returns %s without cancelling", async (expected, tournament, canceller) => {
    db.tournament.findUnique.mockResolvedValue(tournament);

    await expect(markTournamentCancelled(tournamentId, canceller)).resolves.toBe(expected);
    expect(db.tournament.update).not.toHaveBeenCalled();
  });
});
