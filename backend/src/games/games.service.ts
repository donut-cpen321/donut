import { AppError, commonErrors, type ErrorDefinition, mapPrismaErrors } from "../errors";
import { isPushEnabled } from "../external/fcm";
import { getForecast } from "../external/weather";
import { fetchFriendship } from "../friends/friends.repository";
import {
  Format,
  FriendshipStatus,
  GameSource,
  GameStatus,
} from "../generated/prisma/enums";
import type { Game } from "../generated/prisma/client";
import { IncompleteTeamsError } from "../ranking/ranking.service";
import { getEquipment, pushToUsers, userErrors } from "../users/users.service";
import {
  acceptMatch as recordAcceptance,
  addPlayerToTeam,
  cancelMatchedGame,
  createGame,
  deleteGame,
  deleteGameUser,
  fetchFeedGames,
  fetchGame,
  fetchGameDetails,
  fetchUserGames,
  fetchUserHistory,
  type CompletedGame,
  type GameDetails,
  isUserInGame,
  recordScore,
  replaceGameEquipment,
  startBookedGame,
  updateGame,
} from "./games.repository";
import {
  ConfirmScoreInput,
  CreateGameInput,
  GameIdInput,
  ReportScoreInput,
  type RoundScores,
} from "./games.schemas";
import { gameChanged } from "../events/changes";
import { anyoneWatchingPlayers } from "../events/watchers";

export const gameErrors = {
  NotFound: { status: 404, code: "GAME_NOT_FOUND", message: "Game not found" },
  NotInGame: { status: 403, code: "NOT_IN_GAME", message: "User not in game" },
  // Leaving a game the user is not in
  PlayerNotFound: { status: 404, code: "PLAYER_NOT_FOUND", message: "User not in game" },
  GameOrUserNotFound: { status: 404, code: "GAME_OR_USER_NOT_FOUND", message: "Game or user not found" },
  Closed: { status: 409, code: "GAME_CLOSED", message: "Game is already completed or cancelled" },
  IncompleteTeams: { status: 409, code: "INCOMPLETE_TEAMS", message: "Game does not have full teams" },
  MatchedNotMovable: { status: 409, code: "MATCHED_NOT_MOVABLE", message: "Matched games can't be moved" },
  MatchedNotLeavable: { status: 409, code: "MATCHED_NOT_LEAVABLE", message: "Matched games can't be left" },
  TournamentNotLeavable: {
    status: 409,
    code: "TOURNAMENT_NOT_LEAVABLE",
    message: "Players cannot leave a tournament game",
  },
  TournamentNotCancellable: {
    status: 409,
    code: "TOURNAMENT_NOT_CANCELLABLE",
    message: "Tournament games cannot be cancelled",
  },
  NotJoinable: { status: 409, code: "NOT_JOINABLE", message: "Game is not open to join" },
  TeamFull: { status: 409, code: "TEAM_FULL", message: "Team is full" },
  AlreadyInGame: { status: 409, code: "ALREADY_IN_GAME", message: "User already in game" },
  TooLate: { status: 409, code: "LEAVE_TOO_LATE", message: "Game starts in less than 30 minutes" },
  NotPending: { status: 409, code: "MATCH_NOT_PENDING", message: "Match is no longer waiting for answers" },
  AlreadyAccepted: { status: 409, code: "MATCH_ALREADY_ACCEPTED", message: "Match already accepted" },
  NotBooked: {
    status: 409,
    code: "GAME_NOT_BOOKED",
    message: "Only a booked game can start: every spot must be filled and every player must accept",
  },
  AlreadyStarted: { status: 409, code: "GAME_ALREADY_STARTED", message: "Game has already started" },
  NotStarted: { status: 409, code: "GAME_NOT_STARTED", message: "Start the game before reporting its score" },
  NotFriends: { status: 403, code: "NOT_FRIENDS", message: "Users are not friends" },
  // Two games sharing a player finished at the same moment and Postgres aborted one of them
  ScoreConflict: { status: 409, code: "SCORE_CONFLICT", message: "The score could not be saved, please try again" },
  NoScoreToConfirm: { status: 409, code: "NO_SCORE_TO_CONFIRM", message: "No score has been reported yet" },
  // Another player reported a different score after this player saw it
  ScoreChanged: { status: 409, code: "SCORE_CHANGED", message: "The score was changed, check it again" },
} satisfies Record<string, ErrorDefinition>;

const teamSize: Record<Format, number> = {
  [Format.SINGLES]: 1,
  [Format.DOUBLES]: 2,
};

// Players can't leave a game that starts in less than 30 minutes
const LEAVE_DEADLINE_MS = 30 * 60 * 1000;

/**
 * Starts a player off with their default equipment in a game they created or joined, so its use is counted without
 * them picking it. A failure is logged, since the game itself is already saved and they can pick equipment later
 * @param gameId the game ID of the game
 * @param userId the player
 */
async function useDefaultEquipment(gameId: string, userId: string): Promise<void> {
  try {
    const defaultIds = (await getEquipment(userId)).filter((item) => item.isDefault).map((item) => item.id);
    if (defaultIds.length > 0) {
      await replaceGameEquipment(gameId, userId, defaultIds);
    }
  } catch (err) {
    console.error(`Could not add the default equipment of user ${userId} to game ${gameId}`, err);
  }
}

/**
 * Creates a game using the given game information and adds the given user to the game on team 1
 * @param game the game to be created
 * @param userId the user to be added to the game
 * @returns the created game
 * @throws AppError if the court does not exist
 */
export async function newGame(game: CreateGameInput, userId: string) {
  const created = await mapPrismaErrors(createGame({ ...game, status: GameStatus.OPEN }, userId), {
    P2003: commonErrors.CourtNotFound,
  });
  await useDefaultEquipment(created.id, userId);
  // On the feed, and in the creator's games
  gameChanged({ gameId: created.id });
  return created;
}

/**
 * Adds the weather forecast at a game's court and start time. A completed or cancelled game is not
 * played anymore, so it gets no forecast and OpenWeather is not asked
 * @param game the game with its court
 * @returns the game with its forecast, which is null if the game is closed or the forecast cannot be loaded
 */
export async function withForecast(game: GameDetails) {
  const isClosed =
    game.status === GameStatus.COMPLETED ||
    game.status === GameStatus.CANCELLED;

  return {
    ...game,
    forecast: isClosed
      ? null
      : await getForecast(
          game.court.latitude,
          game.court.longitude,
          game.startsAt,
        ),
  };
}

/**
 * Gets a game with the given game ID, with the weather forecast at its court and start time
 * @param gameId the game ID of the requested game
 * @returns the game with its court, players, rounds and forecast
 * @throws AppError if the game is not found
 */
export async function getGame(gameId: GameIdInput) {
  const game = await fetchGameDetails(gameId);
  if (game === null) {
    throw new AppError(gameErrors.NotFound);
  }
  return await withForecast(game);
}


/**
 * Gets the scheduled games that players can join, with the weather forecast at their court and start time
 * @returns the feed's games, soonest first. A game's forecast is null if it cannot be loaded
 */
export async function getFeed() {
  const games = await fetchFeedGames(new Date());
  return await Promise.all(games.map(withForecast));
}

/**
 * Gets a user's upcoming and current games, with the weather forecast at their court and start time
 * @param userId the user whose games are requested
 * @returns the user's booked games, and open games that have not started, soonest first.
 * A game's forecast is null if it cannot be loaded
 */
export async function getUserGames(userId: string) {
  const games = await fetchUserGames(userId, new Date());
  return await Promise.all(games.map(withForecast));
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
 * Tournament games can't be cancelled, since the bracket depends on them, and neither can completed games,
 * since their players' ratings already include the result
 * @param gameId the game ID of the game to be cancelled
 * @param userId the user cancelling the game, who must be in it
 * @throws AppError if the game is not found, is a tournament game, is closed, or the user is not in it
 */
export async function cancelGame(gameId: GameIdInput, userId: string): Promise<void> {
  const game = await fetchGame(gameId);
  if (game === null) {
    throw new AppError(gameErrors.NotFound);
  }

  if (game.gameSource === GameSource.TOURNAMENT) {
    throw new AppError(gameErrors.TournamentNotCancellable);
  }

  if (!(await isUserInGame(userId, gameId))) {
    throw new AppError(gameErrors.NotInGame);
  }

  if (game.gameSource === GameSource.MATCHED) {
    const matchCancelled = await cancelMatchedGame(gameId);
    if (!matchCancelled) {
      throw new AppError(gameErrors.Closed);
    }
    gameChanged({ gameId });
    await notifyMatchCancelled(gameId, userId);
    return;
  }

  // Its players are deleted with it, so whoever must hear about it (their friends, their game lists) is found
  // first. Only looked up when someone has one of those open
  const playerIds =
    anyoneWatchingPlayers()
      ? ((await fetchGameDetails(gameId))?.players.map((player) => player.id) ?? [])
      : [];

  // A completed scheduled game is kept: its ratings were already applied
  if (!(await deleteGame(gameId))) {
    throw new AppError(gameErrors.Closed);
  }
  gameChanged({ gameId, playerIds, onFeed: true });
}

/**
 * Adds a given user to a team of an open scheduled game that has not started
 * @param gameId the game ID of the game to join
 * @param userId the user joining the game
 * @param team the team to join, 1 or 2
 * @throws AppError if the game is not found or not joinable, the team is full, or the user is already in it
 */
export async function joinGame(gameId: string, userId: string, team: number): Promise<void> {
  const game = await fetchGame(gameId);
  if (game === null) {
    throw new AppError(gameErrors.NotFound);
  }

  const now = new Date();
  // Matched games get their players from matchmaking, not the feed
  if (
    game.gameSource !== GameSource.SCHEDULED ||
    game.status !== GameStatus.OPEN ||
    game.startsAt <= now
  ) {
    throw new AppError(gameErrors.NotJoinable);
  }

  // The repository checks the game again once it holds the lock, in case it changed since
  const result = await mapPrismaErrors(
    addPlayerToTeam(gameId, userId, team, teamSize[game.format], now),
    { P2002: gameErrors.AlreadyInGame, P2003: gameErrors.GameOrUserNotFound },
  );
  if (result === "added") {
    await useDefaultEquipment(gameId, userId);
    gameChanged({ gameId });
    return;
  }
  throw new AppError(result === "teamFull" ? gameErrors.TeamFull : gameErrors.NotJoinable);
}

/**
 * Invites a friend to a scheduled game that can still be joined, by sending them a push notification.
 * The friend joins the game the usual way, so nothing is saved
 * @param gameId the game ID of the game to invite to
 * @param inviterId the user inviting, who must be in the game
 * @param friendId the friend being invited, who must not be in the game
 * @returns whether the friend's device was notified
 * @throws AppError if the game is not found or not joinable, the inviter is not in it, the friend already is,
 * or they are not friends
 */
export async function inviteFriend(
  gameId: string,
  inviterId: string,
  friendId: string,
) {
  const game = await fetchGameDetails(gameId);
  if (game === null) {
    throw new AppError(gameErrors.NotFound);
  }

  const inviter = game.players.find((player) => player.id === inviterId);
  if (inviter === undefined) {
    throw new AppError(gameErrors.NotInGame);
  }

  // Only games the friend could join from the feed: matched and tournament games pick their own players
  if (
    game.gameSource !== GameSource.SCHEDULED ||
    game.status !== GameStatus.OPEN ||
    game.startsAt <= new Date()
  ) {
    throw new AppError(gameErrors.NotJoinable);
  }

  if (game.players.some((player) => player.id === friendId)) {
    throw new AppError(gameErrors.AlreadyInGame);
  }

  const friendship = await fetchFriendship(inviterId, friendId);
  if (friendship?.status !== FriendshipStatus.ACCEPTED) {
    throw new AppError(gameErrors.NotFriends);
  }

  // Without Firebase credentials pushToUsers counts skipped tokens as reached, which would look like a delivered push
  if (!isPushEnabled()) {
    console.warn("FIREBASE_* not set, the game invite was not sent");
    return { notified: false };
  }

  const reached = await pushToUsers([friendId], {
    title: "Game invite",
    body: `${inviter.name} invited you to a ${game.format.toLowerCase()} game at ${game.court.name}.`,
    data: { gameId, type: "gameInvite" },
  });

  return { notified: reached > 0 };
}

/**
 * Removes a given user from a given game, unless it is a tournament or matched game, the game is closed or it starts
 * in less than 30 minutes
 * @param gameId the game ID of the game to leave
 * @param userId the user leaving the game
 * @throws AppError if the game is not found, can't be left, or the user is not in it
 */
export async function leaveGame(gameId: string, userId: string): Promise<void> {
  const game = await fetchGame(gameId);
  if (game === null) {
    throw new AppError(gameErrors.NotFound);
  }

  // Leaving would leave a team short in the bracket
  if (game.gameSource === GameSource.TOURNAMENT) {
    throw new AppError(gameErrors.TournamentNotLeavable);
  }

  // Matched games keep the players matchmaking picked
  if (game.gameSource === GameSource.MATCHED) {
    throw new AppError(gameErrors.MatchedNotLeavable);
  }

  if (
    game.status === GameStatus.COMPLETED ||
    game.status === GameStatus.CANCELLED
  ) {
    throw new AppError(gameErrors.Closed);
  }

  if (game.startsAt.getTime() - Date.now() < LEAVE_DEADLINE_MS) {
    throw new AppError(gameErrors.TooLate);
  }

  await mapPrismaErrors(deleteGameUser(gameId, userId), { P2025: gameErrors.PlayerNotFound });
  gameChanged({ gameId });
}

/**
 * Sets which of their own equipment a player uses in a game, replacing what they picked before. Each item's games and
 * hours used are counted when the game completes, so the selection can change until then
 * @param gameId the game ID of the game
 * @param userId the player, who must be in the game
 * @param equipmentIds the IDs of the player's active equipment to use; empty to use none
 * @throws AppError if the game is not found, closed, or the user is not in it, or an item is not the user's active equipment
 */
export async function setGameEquipment(gameId: string, userId: string, equipmentIds: number[]): Promise<void> {
  await checkCanEditGame(gameId, userId);

  // The repository checks the game again once it holds the lock, in case it changed since
  const result = await replaceGameEquipment(gameId, userId, equipmentIds);
  if (result === "closed") {
    throw new AppError(gameErrors.Closed);
  }
  if (result === "notInGame") {
    throw new AppError(gameErrors.NotInGame);
  }
  if (result === "equipmentNotFound") {
    throw new AppError(userErrors.EquipmentNotFound);
  }

  gameChanged({ gameId });
}

/**
 * Accepts a matched game for one of its players. The game is booked once every player has accepted
 * @param gameId the game ID of the matched game
 * @param userId the player accepting
 * @throws AppError if the game is not found, the user is not in it, or the match is not waiting for their answer
 */
export async function acceptMatch(gameId: string, userId: string): Promise<void> {
  const game = await fetchGame(gameId);
  if (game === null) {
    throw new AppError(gameErrors.NotFound);
  }

  if (!(await isUserInGame(userId, gameId))) {
    throw new AppError(gameErrors.NotInGame);
  }

  const result = await recordAcceptance(gameId, userId, new Date());
  if (result === "notPending") {
    throw new AppError(gameErrors.NotPending);
  }

  if (result === "alreadyAccepted") {
    throw new AppError(gameErrors.AlreadyAccepted);
  }

  gameChanged({ gameId });
  await notifyMatchAccepted(gameId, userId, result === "booked");
}

/**
 * Tells the other players of a matched game that a player accepted it, or that the game is booked
 * once every player has. A failed push is logged, since the acceptance is already saved
 * @param gameId the game ID of the matched game
 * @param userId the player who accepted
 * @param booked true if this was the last answer and the game is now booked
 */
async function notifyMatchAccepted(gameId: string, userId: string, booked: boolean) {
  try {
    const audience = await fetchMatchAudience(gameId, userId);
    if (!audience) {
      return;
    }

    const { actorName, otherIds, where } = audience;
    const messageTitle = booked ? "Match confirmed!" : "Match accepted";

    let messageBody: string;
    if (booked) {
      messageBody = `Everyone accepted. Your ${where} is booked.`;
    } else {
      messageBody = `${actorName} accepted your ${where}.`;
    }

    await pushToUsers(otherIds, {
      title: messageTitle,
      body: messageBody,
      data: { gameId },
    });
  } catch (err) {
    console.error(`Could not notify the players of game ${gameId} about an acceptance`, err);
  }
}

/**
 * Tells the other players of a matched game that a player cancelled it.
 * A failed push is logged, since the game is already cancelled
 * @param gameId the game ID of the matched game
 * @param userId the player who cancelled
 */
async function notifyMatchCancelled(gameId: string, userId: string) {
  try {
    const audience = await fetchMatchAudience(gameId, userId);
    if (!audience) {
      return;
    }

    await pushToUsers(audience.otherIds, {
      title: "Match cancelled",
      body: `${audience.actorName} cancelled your ${audience.where}.`,
      data: { gameId },
    });
  } catch (err) {
    console.error(`Could not notify the players of game ${gameId} about a cancellation`, err);
  }
}

/**
 * Looks up who to tell about a player's answer to a matched game
 * @param gameId the game ID of the matched game
 * @param userId the player who answered
 * @returns the player's name, the other players' IDs and e.g. "singles game at <court>",
 * or null if the game or the player is gone
 */
async function fetchMatchAudience(gameId: string, userId: string) {
  const game = await fetchGameDetails(gameId);
  const actor = game?.players.find((player) => player.id === userId);
  if (!game || !actor) {
    return null;
  }

  return {
    actorName: actor.name,
    otherIds: game.players.map((player) => player.id).filter((id) => id !== userId),
    where: `${game.format.toLowerCase()} game at ${game.court.name}`,
  };
}

/**
 * Checks that a game exists, that the given user is in it, and that it is not completed or cancelled
 * @param gameId the game ID of the game to edit
 * @param userId the user editing the game
 * @param isMove true when changing the time or court, which matched games don't allow
 * @returns the game
 * @throws AppError with the reason the user cannot edit the game
 */
async function checkCanEditGame(
  gameId: string,
  userId: string,
  isMove = false,
): Promise<Game> {
  const game = await fetchGame(gameId);
  if (game === null) {
    throw new AppError(gameErrors.NotFound);
  }

  if (!(await isUserInGame(userId, gameId))) throw new AppError(gameErrors.NotInGame);

  if (
    game.status === GameStatus.COMPLETED ||
    game.status === GameStatus.CANCELLED
  ) {
    throw new AppError(gameErrors.Closed);
  }

  // Matched players accepted this time and court
  if (isMove && game.gameSource === GameSource.MATCHED) {
    throw new AppError(gameErrors.MatchedNotMovable);
  }

  // A game being played can't move: a new start time would leave it in progress but starting later
  if (isMove && game.status === GameStatus.IN_PROGRESS) {
    throw new AppError(gameErrors.AlreadyStarted);
  }

  return game;
}

/**
 * Moves a game to a new start time
 * @param gameId the game ID of the game to change
 * @param userId the user changing the game, who must be in it
 * @param startsAt the new start time
 * @returns the updated game
 * @throws AppError with the reason the game cannot be changed
 */
export async function changeGameTime(
  gameId: string,
  userId: string,
  startsAt: Date,
) {
  await checkCanEditGame(gameId, userId, true);
  const game = await mapPrismaErrors(updateGame(gameId, { startsAt }), { P2025: gameErrors.NotFound });
  gameChanged({ gameId });
  return game;
}

/**
 * Decides whether a game can start: it must be booked, so its players are known and every one of them
 * has accepted it, and not started yet
 * @param game the game to start
 * @returns the reason the game cannot start, or null if it can
 */
function whyGameCannotStart(game: Game): ErrorDefinition | null {
  if (
    game.status === GameStatus.COMPLETED ||
    game.status === GameStatus.CANCELLED
  ) {
    return gameErrors.Closed;
  }

  if (game.status === GameStatus.IN_PROGRESS) {
    return gameErrors.AlreadyStarted;
  }

  // An open game still has free spots, or is a match waiting for answers
  if (game.status !== GameStatus.BOOKED) {
    return gameErrors.NotBooked;
  }

  return null;
}

/**
 * Starts a booked game when a player taps Start: it is in progress from now on, and its start time becomes now.
 * Games never start on their own, so this works early or late. Scheduled, matched and tournament games can all
 * start, since a booked game's players are known and have accepted it. A tournament's later rounds are timed from
 * the tournament's start, so they don't move
 * @param gameId the game ID of the game to start
 * @param userId the user starting the game, who must be in it
 * @returns the started game
 * @throws AppError with the reason the game cannot start
 */
export async function startGame(gameId: string, userId: string) {
  const game = await fetchGame(gameId);
  if (game === null) {
    throw new AppError(gameErrors.NotFound);
  }

  if (!(await isUserInGame(userId, gameId))) {
    throw new AppError(gameErrors.NotInGame);
  }

  const error = whyGameCannotStart(game);
  if (error) {
    throw new AppError(error);
  }

  const started = await startBookedGame(gameId, new Date());
  if (started === null) {
    // Another player started it, or it was completed, cancelled or deleted, after the check above
    const current = await fetchGame(gameId);
    const reason =
      current === null
        ? gameErrors.NotFound
        : (whyGameCannotStart(current) ?? gameErrors.AlreadyStarted);
    throw new AppError(reason);
  }

  // startedAt: its players stop showing as playing to their friends a while after it
  gameChanged({ gameId, startedAt: started.startsAt });
  return started;
}

/**
 * Moves a game to a different court
 * @param gameId the game ID of the game to change
 * @param userId the user changing the game, who must be in it
 * @param courtId the ID of the new court
 * @returns the updated game
 * @throws AppError with the reason the game cannot be changed
 */
export async function changeGameLocation(
  gameId: string,
  userId: string,
  courtId: number,
) {
  await checkCanEditGame(gameId, userId, true);
  const game = await mapPrismaErrors(updateGame(gameId, { courtId }), {
    P2003: commonErrors.CourtNotFound,
    P2025: gameErrors.NotFound,
  });
  gameChanged({ gameId });
  return game;
}

/**
 * What reporting or confirming a score did: completed is true once every player agreed, and then comes with the
 * completed game, its rounds and each player's rating change
 */
export type ScoreResult = { completed: false } | ({ completed: true } & CompletedGame);

/**
 * Reports the final score of a game in progress. It waits for every other player to confirm it: the game is only
 * completed, and ratings updated, once every player agrees. A score different from the one waiting replaces it, and
 * everyone has to agree again; the same score counts as confirming it
 * @param gameId the game ID of the game the score is for
 * @param userId the user reporting the score, who must be in the game
 * @param rounds the [team 1 score, team 2 score] of each round, in order
 * @returns whether the game is completed, with its result if it is
 * @throws AppError with the reason the score cannot be reported
 */
export async function reportScore(
  gameId: string,
  userId: string,
  rounds: ReportScoreInput["rounds"],
): Promise<ScoreResult> {
  return await saveScore(gameId, userId, rounds, false);
}

/**
 * Agrees with the score waiting for confirmation. The last player to agree completes the game and updates every
 * player's rating. Confirming twice is fine
 * @param gameId the game ID of the game the score is for
 * @param userId the user confirming the score, who must be in the game
 * @param rounds the score the user saw, which must still be the one waiting
 * @returns whether the game is completed, with its result if it is
 * @throws AppError with the reason the score cannot be confirmed
 */
export async function confirmScore(
  gameId: string,
  userId: string,
  rounds: ConfirmScoreInput["rounds"],
): Promise<ScoreResult> {
  return await saveScore(gameId, userId, rounds, true);
}

/**
 * Records a player's agreement with a score, see reportScore and confirmScore
 * @param confirming true to only confirm the score waiting
 */
async function saveScore(
  gameId: string,
  userId: string,
  rounds: RoundScores,
  confirming: boolean,
): Promise<ScoreResult> {
  const game = await checkCanEditGame(gameId, userId);

  // Only a game a player started can be scored, like the app's Report score button
  if (game.status !== GameStatus.IN_PROGRESS) {
    throw new AppError(gameErrors.NotStarted);
  }

  let result;
  try {
    result = await mapPrismaErrors(recordScore(gameId, userId, rounds, confirming, new Date()), {
      P2034: gameErrors.ScoreConflict,
    });
  } catch (e) {
    if (e instanceof IncompleteTeamsError) {
      throw new AppError(gameErrors.IncompleteTeams);
    }
    throw e;
  }

  switch (result.outcome) {
    case "notInProgress":
      // Another player's confirmation completed the game, or it was cancelled, after the check above
      throw new AppError(gameErrors.Closed);
    case "noProposal":
      throw new AppError(gameErrors.NoScoreToConfirm);
    case "scoreChanged":
      throw new AppError(gameErrors.ScoreChanged);
    case "proposed":
      gameChanged({ gameId });
      await notifyScoreReported(gameId, userId, rounds);
      return { completed: false };
    case "confirmed":
      gameChanged({ gameId });
      return { completed: false };
    case "completed":
      gameChanged({ gameId });
      return { completed: true, ...result.completed };
  }
}

/**
 * Asks the other players of a game to confirm the score a player reported. A failed push is logged, since the score
 * is already saved and shows on the game screen
 * @param gameId the game ID of the game
 * @param userId the player who reported the score
 * @param rounds the reported score
 */
async function notifyScoreReported(gameId: string, userId: string, rounds: RoundScores) {
  try {
    const audience = await fetchMatchAudience(gameId, userId);
    if (!audience) {
      return;
    }

    const score = rounds.map(([team1, team2]) => `${team1}-${team2}`).join(", ");
    await pushToUsers(audience.otherIds, {
      title: "Confirm the score",
      body: `${audience.actorName} reported ${score} for your ${audience.where}.`,
      data: { gameId, type: "scoreReported" },
    });
  } catch (err) {
    console.error(`Could not ask the players of game ${gameId} to confirm the score`, err);
  }
}
