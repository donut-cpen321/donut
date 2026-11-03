// Bracket writes that run inside a caller's transaction: drawing the bracket when a tournament fills, and moving
// a winner on when a tournament game's score is reported. It never imports the Prisma client itself, so the games
// repository can use it without an import cycle.
import type { Prisma, Tournament } from "../generated/prisma/client";
import {
  GameSource,
  GameStatus,
  TournamentStatus,
} from "../generated/prisma/enums";
import type { RoundScore } from "../ranking/ranking.types";
import {
  bracketSlots,
  formTeams,
  nextSlot,
  roundStartsAt,
  winningSide,
} from "./bracket";

export type BracketTournament = Pick<
  Tournament,
  "id" | "format" | "courtId" | "startsAt"
>;

/**
 * Locks a tournament's row until the end of the transaction, so registrations, cancellation and bracket changes
 * happen one at a time. Matches no row if the tournament does not exist, so callers must check it themselves
 * @param tx the transaction to hold the lock in
 * @param tournamentId the ID of the tournament to lock
 * @returns the locked row, or no rows if the tournament does not exist
 */
export async function lockTournament(
  tx: Prisma.TransactionClient,
  tournamentId: string,
) {
  return await tx.$queryRaw<{ id: string }[]>`SELECT id FROM tournaments WHERE id = ${tournamentId}::uuid FOR UPDATE`;
}

/**
 * Creates a booked tournament game with both teams already in it
 * @param tx the transaction to create the game in
 * @param tournament the tournament the game belongs to
 * @param teams the user IDs of game team 1 and game team 2
 * @param startsAt the start time of the game
 * @param now the current time, saved as every player's acceptance
 * @returns the ID of the new game
 */
async function createBracketGame(
  tx: Prisma.TransactionClient,
  tournament: BracketTournament,
  teams: [string[], string[]],
  startsAt: Date,
  now: Date,
): Promise<string> {
  const [team1, team2] = teams;
  const game = await tx.game.create({
    data: {
      format: tournament.format,
      status: GameStatus.BOOKED,
      gameSource: GameSource.TOURNAMENT,
      startsAt,
      courtId: tournament.courtId,
      players: {
        create: [
          ...team1.map((userId) => ({ userId, team: 1, acceptedAt: now })),
          ...team2.map((userId) => ({ userId, team: 2, acceptedAt: now })),
        ],
      },
    },
    select: { id: true },
  });

  return game.id;
}

/**
 * @param tx the transaction to read in
 * @param tournamentId the tournament the team plays in
 * @param seed the team's seed
 * @returns the user IDs of the team's players
 */
async function fetchTeamMembers(
  tx: Prisma.TransactionClient,
  tournamentId: string,
  seed: number,
): Promise<string[]> {
  const players = await tx.tournamentPlayer.findMany({
    where: { tournamentId, team: seed },
    select: { userId: true },
    orderBy: { userId: "asc" },
  });

  return players.map(({ userId }) => userId);
}

/**
 * Draws the bracket of a full tournament: forms and seeds the teams by rating, saves the bracket's 7 slots, books the
 * 4 quarterfinal games at the tournament's court and start time, and marks the tournament as in progress.
 * The caller must hold the tournament's lock
 * @param tx the transaction the last registration is being saved in
 * @param tournament the tournament to draw
 * @param now the current time
 * @throws Error if the registered players do not fill the bracket; the caller's transaction rolls back
 */
export async function drawBracket(
  tx: Prisma.TransactionClient,
  tournament: BracketTournament,
  now: Date,
): Promise<void> {
  const entrants = await tx.tournamentPlayer.findMany({
    where: { tournamentId: tournament.id },
    select: { userId: true, joinedAt: true, user: { select: { rating: true } } },
  });

  const teams = formTeams(
    entrants.map(({ userId, joinedAt, user }) => ({
      userId,
      joinedAt,
      rating: user.rating,
    })),
    tournament.format,
  );

  const membersBySeed = new Map<number, string[]>();
  for (const [index, userIds] of teams.entries()) {
    const seed = index + 1;
    membersBySeed.set(seed, userIds);
    await tx.tournamentPlayer.updateMany({
      where: { tournamentId: tournament.id, userId: { in: userIds } },
      data: { team: seed },
    });
  }

  const matches: Prisma.TournamentMatchCreateManyInput[] = [];
  for (const slot of bracketSlots()) {
    let gameId: string | null = null;
    if (slot.team1 !== null && slot.team2 !== null) {
      gameId = await createBracketGame(
        tx,
        tournament,
        [membersBySeed.get(slot.team1) ?? [], membersBySeed.get(slot.team2) ?? []],
        roundStartsAt(tournament.startsAt, slot.round, now),
        now,
      );
    }
    matches.push({ tournamentId: tournament.id, ...slot, gameId });
  }

  await tx.tournamentMatch.createMany({ data: matches });
  await tx.tournament.update({
    where: { id: tournament.id },
    data: { status: TournamentStatus.IN_PROGRESS },
  });
}

/**
 * Moves the winner of a reported tournament game on through the bracket: records the winner, and either crowns the
 * champion after the final or puts the winner into their next match. Once both teams of that match are known, its
 * game is booked at the round's time. Does nothing for a game that is not in a bracket.
 * Must run in the transaction that saves the final score, so the score and the bracket change together. The
 * tournament's lock makes two matches finishing at once take turns, so the second one sees the first one's winner
 * and exactly one game is booked for the match they feed
 * @param tx the transaction the final score is being saved in
 * @param gameId the game ID of the reported game
 * @param rounds the [team 1 score, team 2 score] of each round
 * @param now the current time
 */
export async function advanceBracket(
  tx: Prisma.TransactionClient,
  gameId: string,
  rounds: RoundScore[],
  now: Date,
): Promise<void> {
  const match = await tx.tournamentMatch.findUnique({ where: { gameId } });
  if (match === null || match.winner !== null) {
    return;
  }

  const { tournamentId } = match;
  await lockTournament(tx, tournamentId);

  const winner = winningSide(rounds) === 1 ? match.team1 : match.team2;
  await tx.tournamentMatch.update({
    where: {
      tournamentId_round_position: {
        tournamentId,
        round: match.round,
        position: match.position,
      },
    },
    data: { winner },
  });

  const next = nextSlot(match.round, match.position);
  if (next === null) {
    await tx.tournament.update({
      where: { id: tournamentId },
      data: { status: TournamentStatus.COMPLETED, winningTeam: winner },
    });
    return;
  }

  const nextKey = {
    tournamentId_round_position: {
      tournamentId,
      round: next.round,
      position: next.position,
    },
  };
  // Read under the lock, so a match decided just before this one is already in the row
  const nextMatch = await tx.tournamentMatch.update({
    where: nextKey,
    data: next.side === 1 ? { team1: winner } : { team2: winner },
  });
  if (
    nextMatch.team1 === null ||
    nextMatch.team2 === null ||
    nextMatch.gameId !== null
  ) {
    return;
  }

  const tournament = await tx.tournament.findUniqueOrThrow({
    where: { id: tournamentId },
    select: { id: true, format: true, courtId: true, startsAt: true },
  });
  const nextGameId = await createBracketGame(
    tx,
    tournament,
    [
      await fetchTeamMembers(tx, tournamentId, nextMatch.team1),
      await fetchTeamMembers(tx, tournamentId, nextMatch.team2),
    ],
    roundStartsAt(tournament.startsAt, next.round, now),
    now,
  );
  await tx.tournamentMatch.update({
    where: nextKey,
    data: { gameId: nextGameId },
  });
}
