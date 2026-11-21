import { prisma } from "../db";
import { Game, Prisma } from "../generated/prisma/client";
import { GameSource, GameStatus } from "../generated/prisma/enums";
import { applyGameResult } from "../ranking/ranking.service";
import { advanceBracket } from "../tournaments/bracket.repository";
import { type RoundScores, storedRoundsSchema } from "./games.schemas";

export const MATCH_ANSWER_DEADLINE_MS = 30 * 60 * 1000;

// Hours of use added to each piece of equipment in a completed game
export const HOURS_PER_GAME = 1;

// The user fields that are safe to show to other players.
const playerUserSelect = {
  id: true,
  username: true,
  name: true,
  rating: true,
  photoUrl: true,
} satisfies Prisma.UserSelect;

// A game with its court, players, rounds and tournament, as shown on the game screen and the feed
const gameDetailsInclude = {
  court: { select: { id: true, name: true, latitude: true, longitude: true } },
  players: {
    select: {
      team: true,
      acceptedAt: true,
      scoreConfirmedAt: true,
      ratingBefore: true,
      ratingAfter: true,
      user: { select: playerUserSelect },
    },
    orderBy: { team: "asc" },
  },
  rounds: {
    select: { number: true, team1Score: true, team2Score: true },
    orderBy: { number: "asc" },
  },
  scoreProposal: {
    select: { rounds: true, proposedById: true, proposedAt: true },
  },
  tournamentMatch: {
    select: { round: true, tournament: { select: { id: true, name: true } } },
  },
  equipment: {
    select: { equipment: { select: { id: true, userId: true, type: true, name: true } } },
  },
} satisfies Prisma.GameInclude;

type GameDetailsRow = Prisma.GameGetPayload<{
  include: typeof gameDetailsInclude;
}>;

/**
 * Reads the rounds a score proposal stored as JSON
 * @param rounds the stored [team 1 score, team 2 score] of each round
 * @returns the rounds, or an empty list if they are not in that shape
 */
function parseStoredRounds(rounds: Prisma.JsonValue): RoundScores {
  const parsed = storedRoundsSchema.safeParse(rounds);
  return parsed.success ? parsed.data : [];
}

/**
 * Flattens each player's user into the player, like the friends list does, and names the tournament a game is in
 * @param game a game fetched with gameDetailsInclude
 * @returns the game with players as { id, username, name, rating, team, acceptedAt, scoreConfirmedAt, ratingBefore,
 * ratingAfter }, its tournament as { id, name, round }, or null if it is not a tournament game, and its scoreProposal
 * as { rounds, proposedById, proposedAt } with rounds shaped like the game's, or null if no score is waiting,
 * and its equipment as a list of { id, userId, type, name }, where userId is the player using the item.
 * ratingBefore and ratingAfter are null until the game is completed
 */
function toGameDetails({ players, tournamentMatch, scoreProposal, equipment, ...game }: GameDetailsRow) {
  return {
    ...game,
    equipment: equipment.map((item) => item.equipment),
    players: players.map(({ user, team, acceptedAt, scoreConfirmedAt, ratingBefore, ratingAfter }) => ({
      ...user,
      team,
      acceptedAt,
      scoreConfirmedAt,
      ratingBefore,
      ratingAfter,
    })),
    tournament:
      tournamentMatch === null
        ? null
        : { ...tournamentMatch.tournament, round: tournamentMatch.round },
    scoreProposal:
      scoreProposal === null
        ? null
        : {
            ...scoreProposal,
            rounds: parseStoredRounds(scoreProposal.rounds).map(([team1Score, team2Score], index) => ({
              number: index + 1,
              team1Score,
              team2Score,
            })),
          },
  };
}

export type GameDetails = ReturnType<typeof toGameDetails>;

/**
 * The games that players can join: a scheduled game is OPEN until every spot is filled, then BOOKED,
 * and can be joined until it starts
 * @param now the current time
 */
const joinableGameWhere = (now: Date) =>
  ({
    gameSource: GameSource.SCHEDULED,
    status: GameStatus.OPEN,
    startsAt: { gt: now },
  }) satisfies Prisma.GameWhereInput;

/**
 * Locks a game's row until the end of the transaction, so changes to its players happen one at a time.
 * Matches no row if the game does not exist, so callers must check the game themselves.
 * A failed query rejects, which rolls back the transaction and keeps the Prisma error for the caller.
 * @param tx the transaction to hold the lock in
 * @param gameId the game ID of the game to lock
 * @returns the locked row, or no rows if the game does not exist
 */
async function lockGame(tx: Prisma.TransactionClient, gameId: string) {
  return await tx.$queryRaw<{ id: string }[]>`SELECT id FROM games WHERE id = ${gameId}::uuid FOR UPDATE`;
}

/**
 * Creates a game and adds its creator to team 1. Both rows are written in one transaction,
 * so a game is never saved without its creator
 * @param game the parameters of the game being made
 * @param creatorId the user creating the game
 * @returns the created game, along with its ID
 */
export async function createGame(
  game: Omit<Game, "id" | "createdAt">,
  creatorId: string,
) {
  return await prisma.game.create({
    data: {
      ...game,
      players: {
        create: { userId: creatorId, team: 1, acceptedAt: new Date() },
      },
    },
  });
}

/**
 * Fetches a game from database with the respective game ID
 * @param gameId {string} the game ID of the requested game
 * @returns The respective game as a Game or null if the game was not found
 */
export async function fetchGame(gameId: string): Promise<Game | null> {
  return await prisma.game.findUnique({ where: { id: gameId } });
}

/**
 * Fetches a game with its court, players and rounds
 * @param gameId the game ID of the requested game
 * @returns the game details or null if the game was not found
 */
export async function fetchGameDetails(
  gameId: string,
): Promise<GameDetails | null> {
  const game = await prisma.game.findUnique({
    where: { id: gameId },
    include: gameDetailsInclude,
  });

  return game === null ? null : toGameDetails(game);
}

/**
 * Fetches the scheduled games that still have free spots and have not started, soonest first
 * @param now the current time
 * @returns the game details of the feed's games
 */
export async function fetchFeedGames(now: Date): Promise<GameDetails[]> {
  const games = await prisma.game.findMany({
    where: joinableGameWhere(now),
    include: gameDetailsInclude,
    orderBy: { startsAt: "asc" },
  });

  return games.map(toGameDetails);
}

/**
 * Fetches a user's games that are booked, or still open and not started yet, soonest first
 * @param userId the user whose games are requested
 * @param now the current time
 * @returns the game details of the user's games
 */
export async function fetchUserGames(
  userId: string,
  now: Date,
): Promise<GameDetails[]> {
  const games = await prisma.game.findMany({
    where: {
      players: { some: { userId } },
      // An open game past its start time can no longer fill or be accepted
      OR: [
        { status: { in: [GameStatus.BOOKED, GameStatus.IN_PROGRESS] } },
        { status: GameStatus.OPEN, startsAt: { gt: now } },
      ],
    },
    include: gameDetailsInclude,
    orderBy: { startsAt: "asc" },
  });

  return games.map(toGameDetails);
}

/**
 * Fetches a user's completed games, most recent first
 * @param userId the user whose match history is requested
 * @returns the game details of the user's completed games, with each player's rating before and after
 */
export async function fetchUserHistory(userId: string): Promise<GameDetails[]> {
  const games = await prisma.game.findMany({
    where: {
      players: { some: { userId } },
      status: GameStatus.COMPLETED,
    },
    include: gameDetailsInclude,
    orderBy: { startsAt: "desc" },
  });

  return games.map(toGameDetails);
}

/**
 * Deletes a game that is still open, booked or in progress, along with its players, rounds and equipment.
 * Their foreign keys don't cascade, so they are deleted first, in the same transaction.
 * A completed game is kept, since its players' ratings already include its result
 * @param gameId the game ID of the game to be deleted
 * @returns true if the game was deleted, false if it is gone or already completed or cancelled
 */
export async function deleteGame(gameId: string): Promise<boolean> {
  return await prisma.$transaction(async (tx) => {
    // Without the lock, a score reported at the same moment could complete the game after the check
    await lockGame(tx, gameId);

    const deletable = await tx.game.count({
      where: { id: gameId, status: { in: [GameStatus.OPEN, GameStatus.BOOKED, GameStatus.IN_PROGRESS] } },
    });
    if (deletable === 0) {
      return false;
    }

    await tx.gameUser.deleteMany({ where: { gameId } });
    await tx.gameRound.deleteMany({ where: { gameId } });
    await tx.scoreProposal.deleteMany({ where: { gameId } });
    await tx.gameEquipment.deleteMany({ where: { gameId } });
    await tx.game.delete({ where: { id: gameId } });
    return true;
  });
}

/**
 * Marks a matched game as cancelled. The row stays, so its other players can see the match fell through
 * @param gameId the game ID of the matched game
 * @returns true if the game was open, booked or in progress and is now cancelled, false if it was already closed
 */
export async function cancelMatchedGame(gameId: string): Promise<boolean> {
  const { count } = await prisma.game.updateMany({
    where: {
      id: gameId,
      gameSource: GameSource.MATCHED,
      status: { in: [GameStatus.OPEN, GameStatus.BOOKED, GameStatus.IN_PROGRESS] },
    },
    data: { status: GameStatus.CANCELLED },
  });
  return count > 0;
}

/**
 * Updates the given fields of a game with the given game ID
 * @param gameId the game ID of the game to update
 * @param data the fields to change
 * @returns the updated game
 */
export async function updateGame(
  gameId: string,
  data: Partial<Pick<Game, "startsAt" | "courtId">>,
) {
  return await prisma.game.update({ where: { id: gameId }, data });
}

/**
 * Starts a booked game: it is in progress from now on, and its start time becomes now, when it really started.
 * The status is checked in the update itself, so two players starting the game at once can't both start it,
 * and a game completed or cancelled since the caller checked it is left alone
 * @param gameId the game ID of the game to start
 * @param now the current time, the game's new start time
 * @returns the started game, or null if the game is gone or not booked, e.g. already started
 */
export async function startBookedGame(
  gameId: string,
  now: Date,
): Promise<Game | null> {
  const [game] = await prisma.game.updateManyAndReturn({
    where: { id: gameId, status: GameStatus.BOOKED },
    data: { startsAt: now, status: GameStatus.IN_PROGRESS },
  });
  return game ?? null;
}

/**
 * Saves the final rounds of a game in progress, marks it as completed, updates every player's rating and, for a
 * tournament game, moves the winner on through the bracket, replacing any rounds saved during the game. Its score
 * proposal is deleted, since the rounds now hold the score. Each piece of equipment used in the game gets one more
 * game and HOURS_PER_GAME more hours. The caller holds the game's lock
 * @param tx the transaction to complete the game in
 * @param gameId the game ID of the game to complete
 * @param rounds the [team 1 score, team 2 score] of each round, in order
 * @param now the current time
 * @returns the completed game, its rounds and each player's rating change
 * @throws IncompleteTeamsError if either team is missing players; the transaction is rolled back
 */
async function completeGame(tx: Prisma.TransactionClient, gameId: string, rounds: RoundScores, now: Date) {
  const game = await tx.game.update({
    where: { id: gameId },
    data: { status: GameStatus.COMPLETED },
  });

  await tx.scoreProposal.deleteMany({ where: { gameId } });
  await tx.gameRound.deleteMany({ where: { gameId } });
  const savedRounds = await tx.gameRound.createManyAndReturn({
    data: rounds.map(([team1Score, team2Score], index) => ({
      gameId,
      number: index + 1,
      team1Score,
      team2Score,
    })),
  });
  const ratingChanges = await applyGameResult(tx, gameId, game.format, rounds);

  // Once per game, since a game only completes once, under its lock; a rollback undoes it too
  await tx.equipment.updateMany({
    where: { games: { some: { gameId } } },
    data: { gamesUsed: { increment: 1 }, hoursUsed: { increment: HOURS_PER_GAME } },
  });

  if (game.gameSource === GameSource.TOURNAMENT) {
    await advanceBracket(tx, gameId, rounds, now);
  }

  return { game, rounds: savedRounds, ratingChanges };
}

export type CompletedGame = Awaited<ReturnType<typeof completeGame>>;

/**
 * What recordScore did:
 * - "notInProgress": the game is gone, not started, or already completed or cancelled; nothing is saved
 * - "noProposal": confirming, but no score is waiting
 * - "scoreChanged": confirming, but the score waiting is not the one the player saw
 * - "proposed": the score is new or different, so it replaced any score waiting and only its reporter agrees so far
 * - "confirmed": the player agrees with the score waiting, and others still have to
 * - "completed": every player now agrees, so the game is completed with the score
 */
export type RecordScoreResult =
  | { outcome: "notInProgress" | "noProposal" | "scoreChanged" | "proposed" | "confirmed" }
  | { outcome: "completed"; completed: CompletedGame };

// Both are validated lists of [team 1 score, team 2 score] number pairs, so equal JSON means the same score in the
// same order, with no indexing by a variable
const sameRounds = (a: RoundScores, b: RoundScores) => JSON.stringify(a) === JSON.stringify(b);

/**
 * Records that a player agrees with a final score. A score that matches the one waiting confirms it; any other score
 * replaces it, resets everyone's agreement and waits for the other players. Once every player agrees, the game is
 * completed with the score, in the same transaction. Runs under the game's lock, so two players agreeing at once
 * can't both complete the game, or miss completing it
 * @param gameId the game ID of the game in progress
 * @param userId the player, who must be in the game
 * @param rounds the [team 1 score, team 2 score] of each round, in order
 * @param confirming true to only agree with the score waiting, which must equal rounds: a player confirming what
 * they saw never proposes a score by accident
 * @param now the current time
 * @returns what was recorded, see RecordScoreResult
 * @throws IncompleteTeamsError if the game completes but either team is missing players; nothing is saved
 */
export async function recordScore(
  gameId: string,
  userId: string,
  rounds: RoundScores,
  confirming: boolean,
  now: Date,
): Promise<RecordScoreResult> {
  return await prisma.$transaction(async (tx) => {
    await lockGame(tx, gameId);

    const game = await tx.game.findUnique({
      where: { id: gameId },
      select: { status: true, scoreProposal: { select: { rounds: true } } },
    });
    if (game?.status !== GameStatus.IN_PROGRESS) {
      return { outcome: "notInProgress" };
    }

    const waiting = game.scoreProposal === null ? null : parseStoredRounds(game.scoreProposal.rounds);
    const agreesWithWaiting = waiting !== null && sameRounds(waiting, rounds);
    if (confirming && !agreesWithWaiting) {
      return { outcome: waiting === null ? "noProposal" : "scoreChanged" };
    }

    if (agreesWithWaiting) {
      await tx.gameUser.updateMany({
        where: { gameId, userId, scoreConfirmedAt: null },
        data: { scoreConfirmedAt: now },
      });
    } else {
      await tx.scoreProposal.upsert({
        where: { gameId },
        create: { gameId, proposedById: userId, rounds, proposedAt: now },
        update: { proposedById: userId, rounds, proposedAt: now },
      });
      await tx.gameUser.updateMany({ where: { gameId, userId: { not: userId } }, data: { scoreConfirmedAt: null } });
      await tx.gameUser.updateMany({ where: { gameId, userId }, data: { scoreConfirmedAt: now } });
    }

    const unconfirmed = await tx.gameUser.count({ where: { gameId, scoreConfirmedAt: null } });
    if (unconfirmed > 0) {
      return { outcome: agreesWithWaiting ? "confirmed" : "proposed" };
    }

    return { outcome: "completed", completed: await completeGame(tx, gameId, rounds, now) };
  });
}

/**
 * Checks if a specified user is in a given game
 * @param GameUser the user ID and game ID of the requested game user
 * @returns true if the user is in the game, otherwise false
 */
export async function isUserInGame(userId: string, gameId: string) {
  const gameUserCount = await prisma.gameUser.count({
    where: { gameId, userId },
  });

  return !!gameUserCount;
}

export type ReplaceEquipmentResult = "saved" | "closed" | "notInGame" | "equipmentNotFound";

/**
 * Replaces the equipment a player uses in a game. Only the player's own rows change: other players' equipment stays.
 * Runs under the game's lock, so the selection can't change while the game is being completed and counted
 * @param gameId the game ID of the game
 * @param userId the player
 * @param equipmentIds the IDs of the player's equipment to use, without repeats
 * @returns "saved", "closed" if the game is gone, completed or cancelled, "notInGame" if the user is not a player, or
 * "equipmentNotFound" if an item doesn't exist, isn't the user's, or is retired
 */
export async function replaceGameEquipment(
  gameId: string,
  userId: string,
  equipmentIds: number[],
): Promise<ReplaceEquipmentResult> {
  return await prisma.$transaction(async (tx) => {
    await lockGame(tx, gameId);

    const open = await tx.game.count({
      where: { id: gameId, status: { in: [GameStatus.OPEN, GameStatus.BOOKED, GameStatus.IN_PROGRESS] } },
    });
    if (open === 0) {
      return "closed";
    }

    if ((await tx.gameUser.count({ where: { gameId, userId } })) === 0) {
      return "notInGame";
    }

    const owned = await tx.equipment.count({ where: { id: { in: equipmentIds }, userId, retiredAt: null } });
    if (owned !== equipmentIds.length) {
      return "equipmentNotFound";
    }

    await tx.gameEquipment.deleteMany({ where: { gameId, equipment: { userId } } });
    await tx.gameEquipment.createMany({ data: equipmentIds.map((equipmentId) => ({ gameId, equipmentId })) });
    return "saved";
  });
}

export type AddPlayerResult = "added" | "notJoinable" | "teamFull";

/**
 * Adds a user to a team of a game if the game can still be joined and the team has a free spot,
 * and books the game once every spot is filled
 * @param gameId the game ID of the game to join
 * @param userId the user joining the game
 * @param team the team to join
 * @param teamSize the number of players on each team
 * @param now the current time
 * @returns "added", "notJoinable" if the game is gone or no longer open to join, or "teamFull"
 */
export async function addPlayerToTeam(
  gameId: string,
  userId: string,
  team: number,
  teamSize: number,
  now: Date,
): Promise<AddPlayerResult> {
  return await prisma.$transaction(async (tx) => {
    // Without the lock, two players could both take the last spot
    await lockGame(tx, gameId);

    // The game may have been booked, cancelled, completed or moved since the caller checked it
    const joinable = await tx.game.count({
      where: { id: gameId, ...joinableGameWhere(now) },
    });
    if (joinable === 0) {
      return "notJoinable";
    }

    const teamCount = await tx.gameUser.count({ where: { gameId, team } });
    if (teamCount >= teamSize) {
      return "teamFull";
    }

    await tx.gameUser.create({
      data: { gameId, userId, team, acceptedAt: new Date() },
    });

    const playerCount = await tx.gameUser.count({ where: { gameId } });
    if (playerCount >= teamSize * 2) {
      await tx.game.update({
        where: { id: gameId },
        data: { status: GameStatus.BOOKED },
      });
    }

    return "added";
  });
}

export type AcceptMatchResult = "accepted" | "alreadyAccepted" | "booked" | "notPending";

/**
 * Records that a player accepted a matched game, and books the game once every player has
 * @param gameId the game ID of the matched game
 * @param userId the player accepting, who must be in the game
 * @param now the current time
 * @returns "accepted", "alreadyAccepted" if the player had already accepted, "booked" if this was the last answer,
 * or "notPending" if the game is no longer waiting for answers
 */
export async function acceptMatch(
  gameId: string,
  userId: string,
  now: Date,
): Promise<AcceptMatchResult> {
  return await prisma.$transaction(async (tx) => {
    // Without the lock, two last answers could both miss booking the game, or race a cancel
    await lockGame(tx, gameId);

    const pending = await tx.game.count({
      where: {
        id: gameId,
        gameSource: GameSource.MATCHED,
        status: GameStatus.OPEN,
        startsAt: { gt: new Date(now.getTime() + MATCH_ANSWER_DEADLINE_MS) },
      },
    });
    if (pending === 0) {
      return "notPending";
    }

    const { count: newlyAccepted } = await tx.gameUser.updateMany({
      where: { gameId, userId, acceptedAt: null },
      data: { acceptedAt: now },
    });
    if (newlyAccepted === 0) {
      return "alreadyAccepted";
    }

    const unanswered = await tx.gameUser.count({ where: { gameId, acceptedAt: null } });
    if (unanswered > 0) {
      return "accepted";
    }

    await tx.game.update({
      where: { id: gameId },
      data: { status: GameStatus.BOOKED },
    });
    return "booked";
  });
}

/**
 * Deletes a given game user from the database. A full scheduled game goes back to OPEN, so it shows on the feed again
 * @param GameUser the game ID and user ID of the game user to delete
 */
export async function deleteGameUser(gameId: string, userId: string) {
  await prisma.$transaction(async (tx) => {
    await lockGame(tx, gameId);

    await tx.gameUser.delete({
      where: { gameId_userId: { gameId, userId } },
    });

    // A player who leaves takes their equipment out of the game, so it isn't counted when the game completes
    await tx.gameEquipment.deleteMany({ where: { gameId, equipment: { userId } } });

    await tx.game.updateMany({
      where: {
        id: gameId,
        gameSource: GameSource.SCHEDULED,
        status: GameStatus.BOOKED,
      },
      data: { status: GameStatus.OPEN },
    });
  });
}
