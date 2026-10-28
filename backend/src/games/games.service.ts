import { GameStatus } from "../generated/prisma/enums";
import {
  completeGame,
  createGame,
  createGameUser,
  deleteGame,
  deleteGameUser,
  fetchGame,
  isUserInGame,
  updateGame,
} from "./games.repository";
import { CreateGameInput, GameIdInput, ReportScoreInput } from "./games.schemas";

export const GameEditError = {
  NotFound: "Game not found",
  NotInGame: "User not in game",
  Closed: "Game is already completed or cancelled",
} as const;

export type GameEditError = (typeof GameEditError)[keyof typeof GameEditError];

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

/**
 * Checks that a game exists, that the given user is in it, and that it is not completed or cancelled
 * @param gameId the game ID of the game to edit
 * @param userId the user editing the game
 * @returns the reason the user cannot edit the game, or null if they can
 */
async function checkCanEditGame(
  gameId: string,
  userId: string,
): Promise<GameEditError | null> {
  const game = await fetchGame(gameId);
  if (game === null) {
    return GameEditError.NotFound;
  }

  if (!(await isUserInGame(userId, gameId))) {
    return GameEditError.NotInGame;
  }

  if (
    game.status === GameStatus.COMPLETED ||
    game.status === GameStatus.CANCELLED
  ) {
    return GameEditError.Closed;
  }

  return null;
}

/**
 * Moves a game to a new start time
 * @param gameId the game ID of the game to change
 * @param userId the user changing the game, who must be in it
 * @param startsAt the new start time
 * @returns the updated game, or the reason the game cannot be changed
 */
export async function changeGameTime(
  gameId: string,
  userId: string,
  startsAt: Date,
) {
  const error = await checkCanEditGame(gameId, userId);
  if (error) {
    return { success: false, error } as const;
  }

  return { success: true, game: await updateGame(gameId, { startsAt }) } as const;
}

/**
 * Moves a game to a different court
 * @param gameId the game ID of the game to change
 * @param userId the user changing the game, who must be in it
 * @param courtId the ID of the new court
 * @returns the updated game, or the reason the game cannot be changed
 */
export async function changeGameLocation(
  gameId: string,
  userId: string,
  courtId: number,
) {
  const error = await checkCanEditGame(gameId, userId);
  if (error) {
    return { success: false, error } as const;
  }

  return { success: true, game: await updateGame(gameId, { courtId }) } as const;
}

/**
 * Reports the final score of a game: saves its rounds and marks it as completed
 * @param gameId the game ID of the game the score is for
 * @param userId the user reporting the score, who must be in the game
 * @param rounds the [team 1 score, team 2 score] of each round, in order
 * @returns the completed game and its rounds, or the reason the score cannot be reported
 */
export async function reportScore(
  gameId: string,
  userId: string,
  rounds: ReportScoreInput["rounds"],
) {
  const error = await checkCanEditGame(gameId, userId);
  if (error) {
    return { success: false, error } as const;
  }

  const completed = await completeGame(gameId, rounds);
  if (completed === null) {
    // Another report completed the game after the check above
    return { success: false, error: GameEditError.Closed } as const;
  }

  return { success: true, ...completed } as const;
}
