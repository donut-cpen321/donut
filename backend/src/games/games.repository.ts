import { prisma } from "../db";
import { Prisma } from "../generated/prisma/client";
import type { GameSource, GameStatus } from "../generated/prisma/enums";
import type { CreateGameInput } from "./games.schemas";

// explicit type definition for Codacy
type Game = Prisma.GameGetPayload<null>;

/**
 * Creates a game row using the given game information
 * @param game the paramaters of the game being made
 * @returns the created game, along with its ID
 */
export async function createGame(
  game: CreateGameInput & { status: GameStatus; gameSource: GameSource },
): Promise<Game> {
  try {
    return await prisma.game.create({ data: game });
  } catch (e) {
    throw e;
  }
}

/**
 * Fetches a game from database with the respective game ID
 * @param gameId {string} the game ID of the requested game
 * @returns The respective game as a Game or null if the game was not found
 */
export async function fetchGame(gameId: string): Promise<Game | null> {
  try {
    return await prisma.game.findUnique({ where: { id: gameId } });
  } catch (e) {
    throw e;
  }
}

/**
 * Deletes a game from the database with the given game ID
 * @param gameId the game ID of the game to be deleted
 */
export async function deleteGame(gameId: string): Promise<void> {
  try {
    await prisma.game.delete({ where: { id: gameId } });
  } catch (e) {
    throw e;
  }
}
