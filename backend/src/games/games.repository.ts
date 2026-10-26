import { prisma } from "../db";
import { Game } from "../generated/prisma/client";
import { GameIdInput } from "./games.schemas";

// explicit type definition for Codacy

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
export async function fetchGame(gameId: GameIdInput): Promise<Game | null> {
  return (await prisma.game.findUnique({ where: { id: gameId } })) as Game;
}

/**
 * Deletes a game from the database with the given game ID
 * @param gameId the game ID of the game to be deleted
 */
export async function deleteGame(gameId: GameIdInput): Promise<void> {
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
