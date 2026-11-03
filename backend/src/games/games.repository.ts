import { prisma } from "../db";
import { Game, Prisma } from "../generated/prisma/client";
import { GameSource, GameStatus } from "../generated/prisma/enums";
import { applyGameResult } from "../ranking/ranking.service";
import { advanceBracket } from "../tournaments/bracket.repository";

export const MATCH_ANSWER_DEADLINE_MS = 30 * 60 * 1000;

// The user fields that are safe to show to other players.
const playerUserSelect = {
  id: true,
  username: true,
  name: true,
  rating: true,
} satisfies Prisma.UserSelect;

// A game with its court, players, rounds and tournament, as shown on the game screen and the feed
const gameDetailsInclude = {
  court: { select: { id: true, name: true, latitude: true, longitude: true } },
  players: {
    select: {
      team: true,
      acceptedAt: true,
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
  tournamentMatch: {
    select: { round: true, tournament: { select: { id: true, name: true } } },
  },
} satisfies Prisma.GameInclude;

type GameDetailsRow = Prisma.GameGetPayload<{
  include: typeof gameDetailsInclude;
}>;

/**
 * Flattens each player's user into the player, like the friends list does, and names the tournament a game is in
 * @param game a game fetched with gameDetailsInclude
 * @returns the game with players as { id, username, name, rating, team, acceptedAt, ratingBefore, ratingAfter },
 * and its tournament as { id, name, round }, or null if it is not a tournament game.
 * ratingBefore and ratingAfter are null until the game is completed
 */
function toGameDetails({ players, tournamentMatch, ...game }: GameDetailsRow) {
  return {
    ...game,
    players: players.map(({ user, team, acceptedAt, ratingBefore, ratingAfter }) => ({
      ...user,
      team,
      acceptedAt,
      ratingBefore,
      ratingAfter,
    })),
    tournament:
      tournamentMatch === null
        ? null
        : { ...tournamentMatch.tournament, round: tournamentMatch.round },
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
        { status: GameStatus.BOOKED },
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
 * Deletes a game that is still open or booked, along with its players, rounds and equipment.
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
      where: { id: gameId, status: { in: [GameStatus.OPEN, GameStatus.BOOKED] } },
    });
    if (deletable === 0) {
      return false;
    }

    await tx.gameUser.deleteMany({ where: { gameId } });
    await tx.gameRound.deleteMany({ where: { gameId } });
    await tx.gameEquipment.deleteMany({ where: { gameId } });
    await tx.game.delete({ where: { id: gameId } });
    return true;
  });
}

/**
 * Marks a matched game as cancelled. The row stays, so its other players can see the match fell through
 * @param gameId the game ID of the matched game
 * @returns true if the game was open or booked and is now cancelled, false if it was already closed
 */
export async function cancelMatchedGame(gameId: string): Promise<boolean> {
  const { count } = await prisma.game.updateMany({
    where: {
      id: gameId,
      gameSource: GameSource.MATCHED,
      status: { in: [GameStatus.OPEN, GameStatus.BOOKED] },
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
 * Saves the final rounds of a game, marks it as completed, updates every player's rating and, for a tournament game,
 * moves the winner on through the bracket, in one transaction, replacing any rounds saved during the game
 * @param gameId the game ID of the game to complete
 * @param rounds the [team 1 score, team 2 score] of each round, in order
 * @returns the completed game, its rounds and each player's rating change, or null if the game was already completed or cancelled
 * @throws IncompleteTeamsError if either team is missing players; nothing is saved
 */
export async function completeGame(gameId: string, rounds: [number, number][]) {
  return await prisma.$transaction(async (tx) => {
    // Only complete a game that is still open, so two reports at once can't both save rounds
    const { count } = await tx.game.updateMany({
      where: {
        id: gameId,
        status: { notIn: [GameStatus.COMPLETED, GameStatus.CANCELLED] },
      },
      data: { status: GameStatus.COMPLETED },
    });

    if (count === 0) {
      return null;
    }

    await tx.gameRound.deleteMany({ where: { gameId } });
    const savedRounds = await tx.gameRound.createManyAndReturn({
      data: rounds.map(([team1Score, team2Score], index) => ({
        gameId,
        number: index + 1,
        team1Score,
        team2Score,
      })),
    });
    const game = await tx.game.findUniqueOrThrow({ where: { id: gameId } });
    const ratingChanges = await applyGameResult(tx, gameId, game.format, rounds);

    if (game.gameSource === GameSource.TOURNAMENT) {
      await advanceBracket(tx, gameId, rounds, new Date());
    }

    return { game, rounds: savedRounds, ratingChanges };
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

export type AcceptMatchResult = "accepted" | "booked" | "notPending";

/**
 * Records that a player accepted a matched game, and books the game once every player has
 * @param gameId the game ID of the matched game
 * @param userId the player accepting, who must be in the game
 * @param now the current time
 * @returns "accepted", "booked" if this was the last answer, or "notPending" if the game is no longer waiting for answers
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

    await tx.gameUser.updateMany({
      where: { gameId, userId, acceptedAt: null },
      data: { acceptedAt: now },
    });

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
