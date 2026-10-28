import { prisma } from "../db";
import { Game } from "../generated/prisma/client";
import { GameStatus } from "../generated/prisma/enums";

/**
 * Creates a game row using the given game information
 * @param game the paramaters of the game being made
 * @returns the created game, along with its ID
 */
export async function createGame(game: Omit<Game, "id" | "createdAt">) {
  return (await prisma.game.create({ data: game })) as Game;
}

/**
 * Fetches a game from database with the respective game ID
 * @param gameId {string} the game ID of the requested game
 * @returns The respective game as a Game or null if the game was not found
 */
export async function fetchGame(gameId: string): Promise<Game | null> {
  return (await prisma.game.findUnique({ where: { id: gameId } })) as Game;
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
  return (await prisma.game.update({ where: { id: gameId }, data })) as Game;
}

/**
 * Saves the final rounds of a game and marks it as completed in one transaction,
 * replacing any rounds saved during the game
 * @param gameId the game ID of the game to complete
 * @param rounds the [team 1 score, team 2 score] of each round, in order
 * @returns the completed game and its rounds, or null if the game was already completed or cancelled
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
    const game = (await tx.game.findUniqueOrThrow({ where: { id: gameId } })) as Game;

    return { game, rounds: savedRounds };
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
 * Deletes a given game user from the database
 * @param GameUser the game ID and user ID of the game user to delete
 */
export async function deleteGameUser(gameId: string, userId: string) {
  await prisma.gameUser.delete({
    where: { gameId_userId: { gameId, userId } },
  });
}
