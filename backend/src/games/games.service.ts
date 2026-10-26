import { GameStatus } from "../generated/prisma/enums";
import { createGame, deleteGame, fetchGame } from "./games.repository";
import type { CreateGameInput, GameIdInput } from "./games.schemas";

/**
 * Creates a game using the given game information
 * @param game the game to be created
 * @returns the created game
 */
export async function newGame(game: CreateGameInput) {
  return await createGame({
    ...game,
    status: GameStatus.OPEN,
  });
}

/**
 * Gets a game with the given game ID
 * @param gameId the game ID of the requested game
 * @returns a game or null if the game is not found
 */
export async function getGame(gameId: GameIdInput) {
  return await fetchGame(gameId);
}

/**
 * Cancels a game with the given game ID
 * @param gameId the game ID of the game to be deleted
 */
export async function cancelGame(gameId: GameIdInput) {
  await deleteGame(gameId);
}
