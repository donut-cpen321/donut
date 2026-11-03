import type { Prisma } from "../../src/generated/prisma/client";
import {
  Format,
  GameSource,
  GameStatus,
  TournamentStatus,
} from "../../src/generated/prisma/enums";
import {
  advanceBracket,
  drawBracket,
  lockTournament,
} from "../../src/tournaments/bracket.repository";
import { ROUND_INTERVAL_MS } from "../../src/tournaments/bracket";

const tournamentId = "9b8c7d6e-5f4a-4b3c-8d2e-1f0a9b8c7d6e";
const startsAt = new Date("2026-11-08T17:00:00Z");
const beforeStart = new Date("2026-11-08T09:00:00Z");
const tournament = { id: tournamentId, format: Format.SINGLES, courtId: 3, startsAt };

// A fake transaction client. Its mocks are plain properties, so they are safe to pass to expect()
function mockTx() {
  return {
    $queryRaw: jest.fn(),
    tournament: { update: jest.fn(), findUniqueOrThrow: jest.fn() },
    tournamentPlayer: { findMany: jest.fn(), updateMany: jest.fn() },
    tournamentMatch: { createMany: jest.fn(), findUnique: jest.fn(), update: jest.fn() },
    game: { create: jest.fn() },
  };
}

let tx: ReturnType<typeof mockTx>;
const asTx = () => tx as unknown as Prisma.TransactionClient;

// Registered players u1..uN, u1 rated highest
const registrations = (count: number) =>
  Array.from({ length: count }, (_, index) => ({
    userId: `u${index + 1}`,
    joinedAt: new Date("2026-11-01T12:00:00Z"),
    user: { rating: 2000 - index * 10 },
  }));

// Each game.create call returns game-1, game-2, ...
const numberGames = () => {
  let created = 0;
  tx.game.create.mockImplementation(() => {
    created += 1;
    return Promise.resolve({ id: `game-${created}` });
  });
};

// The nested players of the n-th game created, as [userId, team] pairs
const playersOfGame = (call: number) => {
  const [args] = tx.game.create.mock.calls.at(call) as [
    { data: { players: { create: { userId: string; team: number }[] } } },
  ];
  return args.data.players.create.map(({ userId, team }) => [userId, team]);
};

// When a mock was first called, relative to every other mock
const firstCallOrder = (mock: jest.Mock) => {
  const [order] = mock.mock.invocationCallOrder;
  if (order === undefined) {
    throw new Error("Expected the mock to have been called");
  }
  return order;
};

beforeEach(() => {
  tx = mockTx();
});

// Interface lockTournament(tx, tournamentId)
describe("Mocked: lockTournament", () => {
  test("Locks the tournament's row for the rest of the transaction", async () => {
    tx.$queryRaw.mockResolvedValue([{ id: tournamentId }]);

    await expect(lockTournament(asTx(), tournamentId)).resolves.toEqual([{ id: tournamentId }]);
    const [strings, id] = tx.$queryRaw.mock.calls[0] as [TemplateStringsArray, string];
    expect(strings.join("?")).toBe("SELECT id FROM tournaments WHERE id = ?::uuid FOR UPDATE");
    expect(id).toBe(tournamentId);
  });
});

// Interface drawBracket(tx, tournament, now)
describe("Mocked: drawBracket", () => {
  // Input: a full singles tournament of 8 players with distinct ratings
  // Expected behavior: seeds 1-8 by rating, books the 4 quarterfinals (1v8, 4v5, 3v6, 2v7) at the tournament's
  // court and start time, saves the 7 slots and marks the tournament in progress
  test("Seeds a singles bracket by rating and books the quarterfinals", async () => {
    tx.tournamentPlayer.findMany.mockResolvedValue(registrations(8).reverse());
    numberGames();

    await drawBracket(asTx(), tournament, beforeStart);

    expect(tx.tournamentPlayer.updateMany).toHaveBeenCalledTimes(8);
    expect(tx.tournamentPlayer.updateMany).toHaveBeenCalledWith({
      where: { tournamentId, userId: { in: ["u1"] } },
      data: { team: 1 },
    });
    expect(tx.tournamentPlayer.updateMany).toHaveBeenCalledWith({
      where: { tournamentId, userId: { in: ["u8"] } },
      data: { team: 8 },
    });

    expect(tx.game.create).toHaveBeenCalledTimes(4);
    expect(tx.game.create).toHaveBeenCalledWith({
      data: expect.objectContaining({
        format: Format.SINGLES,
        status: GameStatus.BOOKED,
        gameSource: GameSource.TOURNAMENT,
        startsAt,
        courtId: 3,
      }),
      select: { id: true },
    });
    expect(playersOfGame(0)).toEqual([["u1", 1], ["u8", 2]]);
    expect(playersOfGame(1)).toEqual([["u4", 1], ["u5", 2]]);
    expect(playersOfGame(2)).toEqual([["u3", 1], ["u6", 2]]);
    expect(playersOfGame(3)).toEqual([["u2", 1], ["u7", 2]]);

    expect(tx.tournamentMatch.createMany).toHaveBeenCalledWith({
      data: [
        { tournamentId, round: 1, position: 0, team1: 1, team2: 8, gameId: "game-1" },
        { tournamentId, round: 1, position: 1, team1: 4, team2: 5, gameId: "game-2" },
        { tournamentId, round: 1, position: 2, team1: 3, team2: 6, gameId: "game-3" },
        { tournamentId, round: 1, position: 3, team1: 2, team2: 7, gameId: "game-4" },
        { tournamentId, round: 2, position: 0, team1: null, team2: null, gameId: null },
        { tournamentId, round: 2, position: 1, team1: null, team2: null, gameId: null },
        { tournamentId, round: 3, position: 0, team1: null, team2: null, gameId: null },
      ],
    });
    expect(tx.tournament.update).toHaveBeenCalledWith({
      where: { id: tournamentId },
      data: { status: TournamentStatus.IN_PROGRESS },
    });
  });

  // Input: a full doubles tournament of 16 players
  // Expected behavior: every quarterfinal has 2 players per team, the top seed being the best and worst players
  test("Books doubles quarterfinals with balanced two-player teams", async () => {
    tx.tournamentPlayer.findMany.mockResolvedValue(registrations(16));
    numberGames();

    await drawBracket(asTx(), { ...tournament, format: Format.DOUBLES }, beforeStart);

    expect(tx.game.create).toHaveBeenCalledTimes(4);
    for (let call = 0; call < 4; call++) {
      const teams = playersOfGame(call).map(([, team]) => team);
      expect(teams).toEqual([1, 1, 2, 2]);
    }
    expect(tx.tournamentPlayer.updateMany).toHaveBeenCalledWith({
      where: { tournamentId, userId: { in: ["u1", "u16"] } },
      data: { team: 1 },
    });
  });

  // Input: a singles tournament with only 7 registered players
  // Expected behavior: throws before anything is saved, so the caller's transaction rolls back
  test("Saves nothing when the bracket is not full", async () => {
    tx.tournamentPlayer.findMany.mockResolvedValue(registrations(7));

    await expect(drawBracket(asTx(), tournament, beforeStart)).rejects.toThrow(/bracket needs/);
    expect(tx.tournamentPlayer.updateMany).not.toHaveBeenCalled();
    expect(tx.game.create).not.toHaveBeenCalled();
    expect(tx.tournamentMatch.createMany).not.toHaveBeenCalled();
  });
});

// Interface advanceBracket(tx, gameId, rounds, now)
describe("Mocked: advanceBracket", () => {
  const team1Wins: [number, number][] = [
    [6, 4],
    [6, 3],
  ];
  const team2Wins: [number, number][] = [
    [4, 6],
    [6, 3],
    [2, 6],
  ];

  const match = (overrides: Record<string, unknown> = {}) => ({
    tournamentId,
    round: 1,
    position: 0,
    team1: 1,
    team2: 8,
    winner: null,
    gameId: "game-1",
    ...overrides,
  });

  const key = (round: number, position: number) => ({
    tournamentId_round_position: { tournamentId, round, position },
  });

  // Input: a game that is not in any bracket
  // Expected behavior: nothing is locked or changed
  test("Does nothing for a game outside a bracket", async () => {
    tx.tournamentMatch.findUnique.mockResolvedValue(null);

    await advanceBracket(asTx(), "game-x", team1Wins, beforeStart);

    expect(tx.tournamentMatch.findUnique).toHaveBeenCalledWith({ where: { gameId: "game-x" } });
    expect(tx.$queryRaw).not.toHaveBeenCalled();
    expect(tx.tournamentMatch.update).not.toHaveBeenCalled();
  });

  // Input: a match whose winner is already recorded
  // Expected behavior: nothing is changed
  test("Does nothing for a match that is already decided", async () => {
    tx.tournamentMatch.findUnique.mockResolvedValue(match({ winner: 1 }));

    await advanceBracket(asTx(), "game-1", team1Wins, beforeStart);

    expect(tx.tournamentMatch.update).not.toHaveBeenCalled();
  });

  // Input: the first quarterfinal (1v8) won by team 1, before the other quarterfinal feeding its semifinal
  // Expected behavior: locks the tournament first, records seed 1 as the winner and puts it on side 1 of
  // semifinal 0, without booking a game, since its opponent is not known yet
  test("Moves the first feeder's winner into the semifinal without booking it", async () => {
    tx.tournamentMatch.findUnique.mockResolvedValue(match());
    tx.tournamentMatch.update
      .mockResolvedValueOnce(match({ winner: 1 }))
      .mockResolvedValueOnce(match({ round: 2, team1: 1, team2: null, gameId: null }));

    await advanceBracket(asTx(), "game-1", team1Wins, beforeStart);

    expect(firstCallOrder(tx.$queryRaw)).toBeLessThan(firstCallOrder(tx.tournamentMatch.update));
    expect(tx.tournamentMatch.update).toHaveBeenNthCalledWith(1, {
      where: key(1, 0),
      data: { winner: 1 },
    });
    expect(tx.tournamentMatch.update).toHaveBeenNthCalledWith(2, {
      where: key(2, 0),
      data: { team1: 1 },
    });
    expect(tx.game.create).not.toHaveBeenCalled();
  });

  // Input: the second quarterfinal (4v5) won by team 2 (seed 5), after seed 1 already reached the semifinal
  // Expected behavior: seed 5 goes on side 2, and the semifinal 1v5 is booked an hour after the start and linked
  test("Books the semifinal once the second feeder is decided", async () => {
    tx.tournamentMatch.findUnique.mockResolvedValue(match({ position: 1, team1: 4, team2: 5, gameId: "game-2" }));
    tx.tournamentMatch.update
      .mockResolvedValueOnce(match({ position: 1, winner: 5 }))
      .mockResolvedValueOnce(match({ round: 2, team1: 1, team2: 5, gameId: null }))
      .mockResolvedValueOnce(match({ round: 2, team1: 1, team2: 5, gameId: "game-sf" }));
    tx.tournament.findUniqueOrThrow.mockResolvedValue(tournament);
    tx.tournamentPlayer.findMany
      .mockResolvedValueOnce([{ userId: "u1" }])
      .mockResolvedValueOnce([{ userId: "u5" }]);
    tx.game.create.mockResolvedValue({ id: "game-sf" });

    await advanceBracket(asTx(), "game-2", team2Wins, beforeStart);

    expect(tx.tournamentMatch.update).toHaveBeenNthCalledWith(2, {
      where: key(2, 0),
      data: { team2: 5 },
    });
    expect(tx.tournamentPlayer.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { tournamentId, team: 1 } }),
    );
    expect(tx.tournamentPlayer.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { tournamentId, team: 5 } }),
    );
    expect(tx.game.create).toHaveBeenCalledWith({
      data: expect.objectContaining({
        status: GameStatus.BOOKED,
        gameSource: GameSource.TOURNAMENT,
        startsAt: new Date(startsAt.getTime() + ROUND_INTERVAL_MS),
        courtId: 3,
      }),
      select: { id: true },
    });
    expect(playersOfGame(0)).toEqual([["u1", 1], ["u5", 2]]);
    expect(tx.tournamentMatch.update).toHaveBeenNthCalledWith(3, {
      where: key(2, 0),
      data: { gameId: "game-sf" },
    });
  });

  // Input: the second feeder decided after the semifinal's scheduled time
  // Expected behavior: the semifinal is booked to start now instead of in the past
  test("Books a late semifinal to start now", async () => {
    const late = new Date(startsAt.getTime() + 2 * ROUND_INTERVAL_MS);
    tx.tournamentMatch.findUnique.mockResolvedValue(match({ position: 1, team1: 4, team2: 5 }));
    tx.tournamentMatch.update
      .mockResolvedValueOnce(match({ position: 1, winner: 4 }))
      .mockResolvedValueOnce(match({ round: 2, team1: 1, team2: 4, gameId: null }));
    tx.tournament.findUniqueOrThrow.mockResolvedValue(tournament);
    tx.tournamentPlayer.findMany.mockResolvedValue([]);
    tx.game.create.mockResolvedValue({ id: "game-sf" });

    await advanceBracket(asTx(), "game-2", team1Wins, late);

    expect(tx.game.create).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ startsAt: late }) }),
    );
  });

  // Input: a semifinal whose next match already has its game
  // Expected behavior: no second game is booked for the same match
  test("Never books a second game for a match", async () => {
    tx.tournamentMatch.findUnique.mockResolvedValue(match({ round: 2, position: 1, team1: 3, team2: 2 }));
    tx.tournamentMatch.update
      .mockResolvedValueOnce(match({ round: 2, position: 1, winner: 2 }))
      .mockResolvedValueOnce(match({ round: 3, team1: 1, team2: 2, gameId: "game-final" }));

    await advanceBracket(asTx(), "game-sf2", team2Wins, beforeStart);

    expect(tx.tournamentMatch.update).toHaveBeenNthCalledWith(2, {
      where: key(3, 0),
      data: { team2: 2 },
    });
    expect(tx.game.create).not.toHaveBeenCalled();
  });

  // Input: the final (1v2) won by team 2
  // Expected behavior: seed 2 is recorded as the winner and the champion, and the tournament is completed
  test("Completes the tournament when the final is reported", async () => {
    tx.tournamentMatch.findUnique.mockResolvedValue(match({ round: 3, team1: 1, team2: 2, gameId: "game-final" }));
    tx.tournamentMatch.update.mockResolvedValue(match({ round: 3, winner: 2 }));

    await advanceBracket(asTx(), "game-final", team2Wins, beforeStart);

    expect(tx.tournamentMatch.update).toHaveBeenCalledTimes(1);
    expect(tx.tournamentMatch.update).toHaveBeenCalledWith({ where: key(3, 0), data: { winner: 2 } });
    expect(tx.tournament.update).toHaveBeenCalledWith({
      where: { id: tournamentId },
      data: { status: TournamentStatus.COMPLETED, winningTeam: 2 },
    });
    expect(tx.game.create).not.toHaveBeenCalled();
  });
});
