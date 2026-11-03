import { isPushEnabled, sendPush } from "../external/fcm";
import { getForecast } from "../external/weather";
import { fetchFriendship } from "../friends/friends.repository";
import {
  Format,
  FriendshipStatus,
  GameSource,
  GameStatus,
} from "../generated/prisma/enums";
import { IncompleteTeamsError } from "../ranking/ranking.service";
import { clearFcmTokens, getFcmTokens } from "../users/users.repository";
import {
  acceptMatch as recordAcceptance,
  addPlayerToTeam,
  cancelMatchedGame,
  completeGame,
  createGame,
  deleteGame,
  deleteGameUser,
  fetchFeedGames,
  fetchGame,
  fetchGameDetails,
  fetchUserGames,
  fetchUserHistory,
  isUserInGame,
  updateGame,
} from "./games.repository";
import {
  CreateGameInput,
  GameIdInput,
  ReportScoreInput,
} from "./games.schemas";

export const gameEditErrors = {
  NotFound: "Game not found",
  NotInGame: "User not in game",
  Closed: "Game is already completed or cancelled",
  IncompleteTeams: "Game does not have full teams",
  Matched: "Matched games can't be moved",
} as const;

export type GameEditError =
  (typeof gameEditErrors)[keyof typeof gameEditErrors];

export const joinGameErrors = {
  NotFound: "Game not found",
  NotJoinable: "Game is not open to join",
  TeamFull: "Team is full",
} as const;

export type JoinGameError =
  (typeof joinGameErrors)[keyof typeof joinGameErrors];

export const leaveGameErrors = {
  NotFound: "Game not found",
  TournamentGame: "Players cannot leave a tournament game",
  Closed: "Game is already completed or cancelled",
  TooLate: "Game starts in less than 30 minutes",
  Matched: "Matched games can't be left",
} as const;

export type LeaveGameError =
  (typeof leaveGameErrors)[keyof typeof leaveGameErrors];

export const acceptMatchErrors = {
  NotFound: "Game not found",
  NotInGame: "User not in game",
  NotPending: "Match is no longer waiting for answers",
} as const;

export type AcceptMatchError =
  (typeof acceptMatchErrors)[keyof typeof acceptMatchErrors];

export const cancelGameErrors = {
  NotFound: "Game not found",
  NotInGame: "User not in game",
  TournamentGame: "Tournament games cannot be cancelled",
  Closed: "Game is already completed or cancelled",
} as const;

export type CancelGameError =
  (typeof cancelGameErrors)[keyof typeof cancelGameErrors];

export const inviteFriendErrors = {
  NotFound: "Game not found",
  NotInGame: "User not in game",
  NotJoinable: "Game is not open to join",
  NotFriends: "Users are not friends",
  AlreadyInGame: "User already in game",
} as const;

export type InviteFriendError =
  (typeof inviteFriendErrors)[keyof typeof inviteFriendErrors];

const teamSize: Record<Format, number> = {
  [Format.SINGLES]: 1,
  [Format.DOUBLES]: 2,
};

// Players can't leave a game that starts in less than 30 minutes
const LEAVE_DEADLINE_MS = 30 * 60 * 1000;

/**
 * Creates a game using the given game information and adds the given user to the game on team 1
 * @param game the game to be created
 * @param userId the user to be added to the game
 * @returns the created game
 */
export async function newGame(game: CreateGameInput, userId: string) {
  return await createGame({ ...game, status: GameStatus.OPEN }, userId);
}

/**
 * Gets a game with the given game ID
 * @param gameId the game ID of the requested game
 * @returns the game with its court, players and rounds, or null if the game is not found
 */
export async function getGame(gameId: GameIdInput) {
  return await fetchGameDetails(gameId);
}

/**
 * Gets the scheduled games that players can join, with the weather forecast at their court and start time
 * @returns the feed's games, soonest first. A game's forecast is null if it cannot be loaded
 */
export async function getFeed() {
  const games = await fetchFeedGames(new Date());

  return await Promise.all(
    games.map(async (game) => ({
      ...game,
      forecast: await getForecast(
        game.court.latitude,
        game.court.longitude,
        game.startsAt,
      ),
    })),
  );
}

/**
 * Gets a user's upcoming and current games
 * @param userId the user whose games are requested
 * @returns the user's booked games, and open games that have not started, soonest first
 */
export async function getUserGames(userId: string) {
  return await fetchUserGames(userId, new Date());
}

/**
 * Gets a user's match history
 * @param userId the user whose match history is requested
 * @returns the user's completed games with their scores and rating changes, most recent first
 */
export async function getUserHistory(userId: string) {
  return await fetchUserHistory(userId);
}

/**
 * Cancels a game with the given game ID: a scheduled game is deleted, a matched game is marked cancelled.
 * Tournament games can't be cancelled, since the bracket depends on them
 * @param gameId the game ID of the game to be cancelled
 * @param userId the user cancelling the game, who must be in it
 * @returns success, or the reason the game cannot be cancelled
 */
export async function cancelGame(gameId: GameIdInput, userId: string) {
  const game = await fetchGame(gameId);
  if (game === null) {
    return { success: false, error: cancelGameErrors.NotFound } as const;
  }

  if (game.gameSource === GameSource.TOURNAMENT) {
    return { success: false, error: cancelGameErrors.TournamentGame } as const;
  }

  if (!(await isUserInGame(userId, gameId))) {
    return { success: false, error: cancelGameErrors.NotInGame } as const;
  }

  if (game.gameSource === GameSource.MATCHED) {
    if (!(await cancelMatchedGame(gameId))) {
      return { success: false, error: cancelGameErrors.Closed } as const;
    }
    return { success: true } as const;
  }

  await deleteGame(gameId);
  return { success: true } as const;
}

/**
 * Adds a given user to a team of an open scheduled game that has not started
 * @param gameId the game ID of the game to join
 * @param userId the user joining the game
 * @param team the team to join, 1 or 2
 * @returns success, or the reason the user cannot join the game
 */
export async function joinGame(gameId: string, userId: string, team: number) {
  const game = await fetchGame(gameId);
  if (game === null) {
    return { success: false, error: joinGameErrors.NotFound } as const;
  }

  const now = new Date();
  // Matched games get their players from matchmaking, not the feed
  if (
    game.gameSource !== GameSource.SCHEDULED ||
    game.status !== GameStatus.OPEN ||
    game.startsAt <= now
  ) {
    return { success: false, error: joinGameErrors.NotJoinable } as const;
  }

  // The repository checks the game again once it holds the lock, in case it changed since
  const result = await addPlayerToTeam(
    gameId,
    userId,
    team,
    teamSize[game.format],
    now,
  );
  if (result === "notJoinable") {
    return { success: false, error: joinGameErrors.NotJoinable } as const;
  }
  if (result === "teamFull") {
    return { success: false, error: joinGameErrors.TeamFull } as const;
  }

  return { success: true } as const;
}

/**
 * Invites a friend to a scheduled game that can still be joined, by sending them a push notification.
 * The friend joins the game the usual way, so nothing is saved
 * @param gameId the game ID of the game to invite to
 * @param inviterId the user inviting, who must be in the game
 * @param friendId the friend being invited, who must not be in the game
 * @returns whether the friend's device was notified, or the reason the friend cannot be invited
 */
export async function inviteFriend(
  gameId: string,
  inviterId: string,
  friendId: string,
) {
  const game = await fetchGameDetails(gameId);
  if (game === null) {
    return { success: false, error: inviteFriendErrors.NotFound } as const;
  }

  const inviter = game.players.find((player) => player.id === inviterId);
  if (inviter === undefined) {
    return { success: false, error: inviteFriendErrors.NotInGame } as const;
  }

  // Only games the friend could join from the feed: matched and tournament games pick their own players
  if (
    game.gameSource !== GameSource.SCHEDULED ||
    game.status !== GameStatus.OPEN ||
    game.startsAt <= new Date()
  ) {
    return { success: false, error: inviteFriendErrors.NotJoinable } as const;
  }

  if (game.players.some((player) => player.id === friendId)) {
    return { success: false, error: inviteFriendErrors.AlreadyInGame } as const;
  }

  const friendship = await fetchFriendship(inviterId, friendId);
  if (friendship?.status !== FriendshipStatus.ACCEPTED) {
    return { success: false, error: inviteFriendErrors.NotFriends } as const;
  }

  // Without Firebase credentials sendPush skips every token, which would look like a delivered push
  if (!isPushEnabled()) {
    console.warn("FIREBASE_* not set, the game invite was not sent");
    return { success: true, notified: false } as const;
  }

  const tokens = await getFcmTokens([friendId]);
  if (tokens.length === 0) {
    return { success: true, notified: false } as const;
  }

  const deadTokens = await sendPush(tokens, {
    title: "Game invite",
    body: `${inviter.name} invited you to a ${game.format.toLowerCase()} game at ${game.court.name}.`,
    data: { gameId, type: "gameInvite" },
  });
  if (deadTokens.length > 0) {
    await clearFcmTokens(deadTokens);
  }

  return { success: true, notified: deadTokens.length < tokens.length } as const;
}

/**
 * Removes a given user from a given game, unless it is a tournament or matched game, the game is closed or it starts
 * in less than 30 minutes
 * @param gameId the game ID of the game to leave
 * @param userId the user leaving the game
 * @returns success, or the reason the user cannot leave the game
 */
export async function leaveGame(gameId: string, userId: string) {
  const game = await fetchGame(gameId);
  if (game === null) {
    return { success: false, error: leaveGameErrors.NotFound } as const;
  }

  // Leaving would leave a team short in the bracket
  if (game.gameSource === GameSource.TOURNAMENT) {
    return { success: false, error: leaveGameErrors.TournamentGame } as const;
  }

  // Matched games keep the players matchmaking picked
  if (game.gameSource === GameSource.MATCHED) {
    return { success: false, error: leaveGameErrors.Matched } as const;
  }

  if (
    game.status === GameStatus.COMPLETED ||
    game.status === GameStatus.CANCELLED
  ) {
    return { success: false, error: leaveGameErrors.Closed } as const;
  }

  if (game.startsAt.getTime() - Date.now() < LEAVE_DEADLINE_MS) {
    return { success: false, error: leaveGameErrors.TooLate } as const;
  }

  await deleteGameUser(gameId, userId);
  return { success: true } as const;
}

/**
 * Accepts a matched game for one of its players. The game is booked once every player has accepted
 * @param gameId the game ID of the matched game
 * @param userId the player accepting
 * @returns success, or the reason the match cannot be accepted
 */
export async function acceptMatch(gameId: string, userId: string) {
  const game = await fetchGame(gameId);
  if (game === null) {
    return { success: false, error: acceptMatchErrors.NotFound } as const;
  }

  if (!(await isUserInGame(userId, gameId))) {
    return { success: false, error: acceptMatchErrors.NotInGame } as const;
  }

  const result = await recordAcceptance(gameId, userId, new Date());
  if (result === "notPending") {
    return { success: false, error: acceptMatchErrors.NotPending } as const;
  }

  return { success: true } as const;
}

/**
 * Checks that a game exists, that the given user is in it, and that it is not completed or cancelled
 * @param gameId the game ID of the game to edit
 * @param userId the user editing the game
 * @param isMove true when changing the time or court, which matched games don't allow
 * @returns the reason the user cannot edit the game, or null if they can
 */
async function checkCanEditGame(
  gameId: string,
  userId: string,
  isMove = false,
): Promise<GameEditError | null> {
  const game = await fetchGame(gameId);
  if (game === null) {
    return gameEditErrors.NotFound;
  }

  if (!(await isUserInGame(userId, gameId))) {
    return gameEditErrors.NotInGame;
  }

  if (
    game.status === GameStatus.COMPLETED ||
    game.status === GameStatus.CANCELLED
  ) {
    return gameEditErrors.Closed;
  }

  // Matched players accepted this time and court
  if (isMove && game.gameSource === GameSource.MATCHED) {
    return gameEditErrors.Matched;
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
  const error = await checkCanEditGame(gameId, userId, true);
  if (error) {
    return { success: false, error } as const;
  }

  return {
    success: true,
    game: await updateGame(gameId, { startsAt }),
  } as const;
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
  const error = await checkCanEditGame(gameId, userId, true);
  if (error) {
    return { success: false, error } as const;
  }

  return {
    success: true,
    game: await updateGame(gameId, { courtId }),
  } as const;
}

/**
 * Reports the final score of a game: saves its rounds, marks it as completed and updates every player's rating
 * @param gameId the game ID of the game the score is for
 * @param userId the user reporting the score, who must be in the game
 * @param rounds the [team 1 score, team 2 score] of each round, in order
 * @returns the completed game, its rounds and each player's rating change, or the reason the score cannot be reported
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

  let completed;
  try {
    completed = await completeGame(gameId, rounds);
  } catch (e) {
    if (e instanceof IncompleteTeamsError) {
      return { success: false, error: gameEditErrors.IncompleteTeams } as const;
    }
    throw e;
  }
  if (completed === null) {
    // Another report completed the game after the check above
    return { success: false, error: gameEditErrors.Closed } as const;
  }

  return { success: true, ...completed } as const;
}
