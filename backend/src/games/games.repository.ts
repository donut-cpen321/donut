import { prisma } from "../db";
import { Game } from "../generated/prisma/client";

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
