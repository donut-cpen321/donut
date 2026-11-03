import { getForecast } from "../external/weather";
import {
  type CancelResult,
  createTournament,
  fetchFeedTournaments,
  fetchTournamentDetails,
  fetchUserTournaments,
  markTournamentCancelled,
  type RegisterResult,
  registerPlayer,
  type UnregisterResult,
  unregisterPlayer,
} from "./tournaments.repository";
import type { CreateTournamentInput } from "./tournaments.schemas";

export const joinTournamentErrors = {
  NotFound: "Tournament not found",
  NotOpen: "Tournament is not open for registration",
  Full: "Tournament is full",
  AlreadyRegistered: "User already registered",
} as const;

export type JoinTournamentError =
  (typeof joinTournamentErrors)[keyof typeof joinTournamentErrors];

export const leaveTournamentErrors = {
  NotFound: "Tournament not found",
  NotRegistered: "User not registered",
  NotOpen: "Tournament is not open for registration",
  Creator: "The creator cannot leave the tournament, cancel it instead",
} as const;

export type LeaveTournamentError =
  (typeof leaveTournamentErrors)[keyof typeof leaveTournamentErrors];

export const cancelTournamentErrors = {
  NotFound: "Tournament not found",
  NotCreator: "Only the creator can cancel the tournament",
  NotOpen: "Tournament is not open for registration",
} as const;

export type CancelTournamentError =
  (typeof cancelTournamentErrors)[keyof typeof cancelTournamentErrors];

/**
 * @param result why the repository did not register the user
 * @returns the matching join error
 */
function toJoinError(
  result: Exclude<RegisterResult, "registered" | "drawn">,
): JoinTournamentError {
  switch (result) {
    case "notFound":
      return joinTournamentErrors.NotFound;
    case "notOpen":
      return joinTournamentErrors.NotOpen;
    case "full":
      return joinTournamentErrors.Full;
    case "alreadyRegistered":
      return joinTournamentErrors.AlreadyRegistered;
  }
}

/**
 * @param result why the repository did not remove the user
 * @returns the matching leave error
 */
function toLeaveError(
  result: Exclude<UnregisterResult, "left">,
): LeaveTournamentError {
  switch (result) {
    case "notFound":
      return leaveTournamentErrors.NotFound;
    case "notOpen":
      return leaveTournamentErrors.NotOpen;
    case "creator":
      return leaveTournamentErrors.Creator;
    case "notRegistered":
      return leaveTournamentErrors.NotRegistered;
  }
}

/**
 * @param result why the repository did not cancel the tournament
 * @returns the matching cancel error
 */
function toCancelError(
  result: Exclude<CancelResult, "cancelled">,
): CancelTournamentError {
  switch (result) {
    case "notFound":
      return cancelTournamentErrors.NotFound;
    case "notCreator":
      return cancelTournamentErrors.NotCreator;
    case "notOpen":
      return cancelTournamentErrors.NotOpen;
  }
}

/**
 * Creates a tournament and registers its creator as its first player
 * @param tournament the tournament to be created
 * @param userId the user creating the tournament
 * @returns the created tournament
 */
export async function newTournament(
  tournament: CreateTournamentInput,
  userId: string,
) {
  return await createTournament(tournament, userId);
}

/**
 * Gets the tournaments that players can register for, with the weather forecast at their court and start time
 * @returns the feed's tournaments, soonest first. A tournament's forecast is null if it cannot be loaded
 */
export async function getTournamentFeed() {
  const tournaments = await fetchFeedTournaments(new Date());

  return await Promise.all(
    tournaments.map(async (tournament) => ({
      ...tournament,
      forecast: await getForecast(
        tournament.court.latitude,
        tournament.court.longitude,
        tournament.startsAt,
      ),
    })),
  );
}

/**
 * Gets the tournaments a user is registered for that are open, in progress or recently completed
 * @param userId the user whose tournaments are requested
 * @returns the user's tournaments, soonest first
 */
export async function getUserTournaments(userId: string) {
  return await fetchUserTournaments(userId, new Date());
}

/**
 * Gets a tournament with its players and bracket
 * @param tournamentId the ID of the requested tournament
 * @returns the tournament details, or null if the tournament is not found
 */
export async function getTournament(tournamentId: string) {
  return await fetchTournamentDetails(tournamentId);
}

/**
 * Registers a user for a tournament. The registration that fills the tournament draws its bracket
 * and books the quarterfinals
 * @param tournamentId the ID of the tournament to join
 * @param userId the user registering
 * @returns success, or the reason the user cannot register
 */
export async function joinTournament(tournamentId: string, userId: string) {
  const result = await registerPlayer(tournamentId, userId, new Date());
  if (result === "registered" || result === "drawn") {
    return { success: true } as const;
  }
  return { success: false, error: toJoinError(result) } as const;
}

/**
 * Removes a user from a tournament that is still open for registration
 * @param tournamentId the ID of the tournament to leave
 * @param userId the user leaving
 * @returns success, or the reason the user cannot leave
 */
export async function leaveTournament(tournamentId: string, userId: string) {
  const result = await unregisterPlayer(tournamentId, userId);
  if (result === "left") {
    return { success: true } as const;
  }
  return { success: false, error: toLeaveError(result) } as const;
}

/**
 * Cancels a tournament that is still open for registration; only its creator can
 * @param tournamentId the ID of the tournament to cancel
 * @param userId the user cancelling the tournament
 * @returns success, or the reason the user cannot cancel it
 */
export async function cancelTournament(tournamentId: string, userId: string) {
  const result = await markTournamentCancelled(tournamentId, userId);
  if (result === "cancelled") {
    return { success: true } as const;
  }
  return { success: false, error: toCancelError(result) } as const;
}
