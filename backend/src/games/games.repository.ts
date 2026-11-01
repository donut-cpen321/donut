import { prisma } from "../db";
import { Game, Prisma } from "../generated/prisma/client";
import { GameSource, GameStatus } from "../generated/prisma/enums";
import { applyGameResult } from "../ranking/ranking.service";

// The user fields that are safe to show to other players.
const playerUserSelect = {
  id: true,
  username: true,
  name: true,
  rating: true,
} satisfies Prisma.UserSelect;

// A game with its court, players and rounds, as shown on the game screen and the feed
const gameDetailsInclude = {
  court: { select: { id: true, name: true, latitude: true, longitude: true } },
  players: {
    select: { team: true, acceptedAt: true, user: { select: playerUserSelect } },
    orderBy: { team: "asc" },
  },
  rounds: {
    select: { number: true, team1Score: true, team2Score: true },
    orderBy: { number: "asc" },
  },
} satisfies Prisma.GameInclude;

type GameDetailsRow = Prisma.GameGetPayload<{
  include: typeof gameDetailsInclude;
}>;

/**
 * Flattens each player's user into the player, like the friends list does
 * @param game a game fetched with gameDetailsInclude
 * @returns the game with players as { id, username, name, rating, team, acceptedAt }
 */
function toGameDetails({ players, ...game }: GameDetailsRow) {
  return {
    ...game,
    players: players.map(({ user, team, acceptedAt }) => ({
      ...user,
      team,
      acceptedAt,
    })),
  };
}

export type GameDetails = ReturnType<typeof toGameDetails>;

/**
 * Locks a game's row until the end of the transaction, so changes to its players happen one at a time
 * @param tx the transaction to hold the lock in
 * @param gameId the game ID of the game to lock
 */
async function lockGame(tx: Prisma.TransactionClient, gameId: string) {
  await tx.$queryRaw`SELECT id FROM games WHERE id = ${gameId}::uuid FOR UPDATE`;
}

/**
 * Creates a game row using the given game information
 * @param game the paramaters of the game being made
 * @returns the created game, along with its ID
 */
export async function createGame(game: Omit<Game, "id" | "createdAt">) {
  return await prisma.game.create({ data: game });
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
    // A scheduled game is OPEN until every spot is filled, then BOOKED
    where: {
      gameSource: GameSource.SCHEDULED,
      status: GameStatus.OPEN,
      startsAt: { gt: now },
    },
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
 * Deletes a game from the database with the given game ID
 * @param gameId the game ID of the game to be deleted
 */
export async function deleteGame(gameId: string): Promise<void> {
  await prisma.game.delete({ where: { id: gameId } });
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
 * Saves the final rounds of a game, marks it as completed and updates every player's rating
 * in one transaction, replacing any rounds saved during the game
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

/*
 * Creates a givne game user in the database
 * @param GameUser the game ID, user ID, and team number of the game user to create
 * @returns the created game user
 */
export async function createGameUser(
  gameId: string,
  userId: string,
  team: number,
  accepted = true,
) {
  return await prisma.gameUser.create({
    data: { gameId, userId, team, acceptedAt: accepted ? new Date() : null },
  });
}

/**
 * Adds a user to a team of a game if the team has a free spot, and books the game once every spot is filled
 * @param gameId the game ID of the game to join
 * @param userId the user joining the game
 * @param team the team to join
 * @param teamSize the number of players on each team
 * @returns true if the user was added, false if the team is full
 */
export async function addPlayerToTeam(
  gameId: string,
  userId: string,
  team: number,
  teamSize: number,
): Promise<boolean> {
  return await prisma.$transaction(async (tx) => {
    // Without the lock, two players could both take the last spot
    await lockGame(tx, gameId);

    const teamCount = await tx.gameUser.count({ where: { gameId, team } });
    if (teamCount >= teamSize) {
      return false;
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

    return true;
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
