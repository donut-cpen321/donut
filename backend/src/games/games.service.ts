import { GameStatus } from "../generated/prisma/enums";
import { createGame, deleteGame, fetchGame } from "./games.repository";
import { GameInput } from "./games.types";

/**
 * Creates a game using the given game information
 * @param game the game to be created
 * @returns the created game
 */
export async function newGame(game: Omit<GameInput, "status">) {
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
export async function getGame(gameId: string) {
  return await fetchGame(gameId);
}

/**
 * Cancels a game with the given game ID
 * @param gameId the game ID of the game to be deleted
 */
export async function cancelGame(gameId: string) {
  await deleteGame(gameId);
}
