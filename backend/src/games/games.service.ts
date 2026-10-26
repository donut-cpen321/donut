import { GameStatus } from "../generated/prisma/enums";
import {
  createGame,
  createGameUser,
  deleteGame,
  deleteGameUser,
  fetchGame,
  isUserInGame,
} from "./games.repository";
import { CreateGameInput, GameIdInput } from "./games.schemas";

/**
 * Creates a game using the given game information and adds the given user to the game on team 1
 * @param game the game to be created
 * @param userId the user to be added to the game
 * @returns the created game
 */
export async function newGame(game: CreateGameInput, userId: string) {
  const newGame = await createGame({
    ...game,
    status: GameStatus.OPEN,
  });

  await joinGame(newGame.id, userId, 1);

  return newGame;
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
export async function cancelGame(gameId: GameIdInput, userId: string) {
  const canDelete = await isUserInGame(userId, gameId);
  if (canDelete) {
    await deleteGame(gameId);
    return { success: true };
  } else {
    return { success: false, error: "Unauthorized" };
  }
}

/**
 * Adds a given user to a given game
 * @param gameUser the given game ID and the given user ID
 * @returns the new game user
 */
export async function joinGame(
  gameId: string,
  userId: string,
  team: number,
  accepted = true,
) {
  return await createGameUser(gameId, userId, team, accepted);
}

/**
 * Removes a given user from a given game
 * @param gameUser the user ID of the user to remove, and the game ID of the game to remove the user from
 */
export async function leaveGame(gameId: string, userId: string) {
  await deleteGameUser(gameId, userId);
}
