import { prisma } from "../db";
import type { Prisma } from "../generated/prisma/client";
import { TournamentStatus } from "../generated/prisma/enums";
import { tournamentCapacity } from "./bracket";
import { drawBracket, lockTournament } from "./bracket.repository";
import type { CreateTournamentInput } from "./tournaments.schemas";

// A completed tournament stays in its players' list for a day, so they can still see who won
const RECENTLY_COMPLETED_MS = 24 * 60 * 60 * 1000;

// The user fields that are safe to show to other players
const playerUserSelect = {
  id: true,
  username: true,
  name: true,
  rating: true,
} satisfies Prisma.UserSelect;

const courtSelect = {
  id: true,
  name: true,
  latitude: true,
  longitude: true,
} satisfies Prisma.CourtSelect;

// A tournament with its court, creator and players, as shown on the feed and in the user's list
const tournamentSummaryInclude = {
  court: { select: courtSelect },
  creator: { select: playerUserSelect },
  players: {
    select: { team: true, joinedAt: true, user: { select: playerUserSelect } },
    orderBy: [{ joinedAt: "asc" }, { userId: "asc" }],
  },
} satisfies Prisma.TournamentInclude;

// A tournament summary with its bracket, as shown on the tournament screen
const tournamentDetailsInclude = {
  ...tournamentSummaryInclude,
  matches: {
    select: {
      round: true,
      position: true,
      team1: true,
      team2: true,
      winner: true,
      game: {
        select: {
          id: true,
          status: true,
          startsAt: true,
          court: { select: courtSelect },
          rounds: {
            select: { number: true, team1Score: true, team2Score: true },
            orderBy: { number: "asc" },
          },
        },
      },
    },
    orderBy: [{ round: "asc" }, { position: "asc" }],
  },
} satisfies Prisma.TournamentInclude;

type TournamentSummaryRow = Prisma.TournamentGetPayload<{
  include: typeof tournamentSummaryInclude;
}>;

type TournamentDetailsRow = Prisma.TournamentGetPayload<{
  include: typeof tournamentDetailsInclude;
}>;

/**
 * Flattens each player's user into the player, like the games do, and adds how many players fill the bracket
 * @param tournament a tournament fetched with tournamentSummaryInclude or tournamentDetailsInclude
 * @returns the tournament with players as { id, username, name, rating, team, joinedAt } and its capacity
 */
function toTournamentView<T extends TournamentSummaryRow>({
  players,
  ...tournament
}: T) {
  return {
    ...tournament,
    capacity: tournamentCapacity(tournament.format),
    players: players.map(({ user, team, joinedAt }) => ({
      ...user,
      team,
      joinedAt,
    })),
  };
}

export type TournamentSummary = ReturnType<
  typeof toTournamentView<TournamentSummaryRow>
>;
export type TournamentDetails = ReturnType<
  typeof toTournamentView<TournamentDetailsRow>
>;

export type RegisterResult =
  | "registered"
  | "drawn"
  | "notFound"
  | "notOpen"
  | "full"
  | "alreadyRegistered";

export type UnregisterResult =
  | "left"
  | "notFound"
  | "notOpen"
  | "creator"
  | "notRegistered";

export type CancelResult = "cancelled" | "notFound" | "notCreator" | "notOpen";

/**
 * Creates a tournament and registers its creator. Both rows are written together,
 * so a tournament is never saved without its creator
 * @param tournament the name, format, start time and court of the tournament
 * @param creatorId the user creating the tournament
 * @returns the created tournament
 */
export async function createTournament(
  tournament: CreateTournamentInput,
  creatorId: string,
) {
  return await prisma.tournament.create({
    data: {
      ...tournament,
      creatorId,
      players: { create: { userId: creatorId } },
    },
  });
}

/**
 * Fetches the tournaments that are open for registration and have not started, soonest first
 * @param now the current time
 * @returns the summaries of the feed's tournaments
 */
export async function fetchFeedTournaments(
  now: Date,
): Promise<TournamentSummary[]> {
  const tournaments = await prisma.tournament.findMany({
    where: { status: TournamentStatus.REGISTRATION, startsAt: { gt: now } },
    include: tournamentSummaryInclude,
    orderBy: { startsAt: "asc" },
  });

  return tournaments.map((tournament) => toTournamentView(tournament));
}

/**
 * Fetches a user's tournaments that are in progress, still open and not started, or completed
 * and started within the last day, soonest first
 * @param userId the user whose tournaments are requested
 * @param now the current time
 * @returns the summaries of the user's tournaments
 */
export async function fetchUserTournaments(
  userId: string,
  now: Date,
): Promise<TournamentSummary[]> {
  const tournaments = await prisma.tournament.findMany({
    where: {
      players: { some: { userId } },
      OR: [
        { status: TournamentStatus.IN_PROGRESS },
        // A tournament still in registration after its start time never filled
        { status: TournamentStatus.REGISTRATION, startsAt: { gt: now } },
        {
          status: TournamentStatus.COMPLETED,
          startsAt: { gt: new Date(now.getTime() - RECENTLY_COMPLETED_MS) },
        },
      ],
    },
    include: tournamentSummaryInclude,
    orderBy: { startsAt: "asc" },
  });

  return tournaments.map((tournament) => toTournamentView(tournament));
}

/**
 * Fetches a tournament with its court, creator, players and bracket
 * @param tournamentId the ID of the requested tournament
 * @returns the tournament details, or null if the tournament was not found
 */
export async function fetchTournamentDetails(
  tournamentId: string,
): Promise<TournamentDetails | null> {
  const tournament = await prisma.tournament.findUnique({
    where: { id: tournamentId },
    include: tournamentDetailsInclude,
  });

  return tournament === null ? null : toTournamentView(tournament);
}

/**
 * Registers a user for a tournament that is open and has not started. The registration that fills the tournament
 * also draws its bracket, in the same transaction
 * @param tournamentId the ID of the tournament to join
 * @param userId the user registering
 * @param now the current time
 * @returns "registered", "drawn" if this registration filled the tournament, or the reason the user can't register
 */
export async function registerPlayer(
  tournamentId: string,
  userId: string,
  now: Date,
): Promise<RegisterResult> {
  return await prisma.$transaction(async (tx) => {
    // Without the lock, two players could both take the last spot, or a player could leave during the draw
    await lockTournament(tx, tournamentId);

    const tournament = await tx.tournament.findUnique({
      where: { id: tournamentId },
      select: {
        id: true,
        format: true,
        courtId: true,
        startsAt: true,
        status: true,
      },
    });
    if (tournament === null) {
      return "notFound";
    }
    // A tournament is drawn the moment it fills, so one in progress is full
    if (tournament.status === TournamentStatus.IN_PROGRESS) {
      return "full";
    }
    if (
      tournament.status !== TournamentStatus.REGISTRATION ||
      tournament.startsAt <= now
    ) {
      return "notOpen";
    }

    const alreadyRegistered = await tx.tournamentPlayer.count({
      where: { tournamentId, userId },
    });
    if (alreadyRegistered > 0) {
      return "alreadyRegistered";
    }

    await tx.tournamentPlayer.create({ data: { tournamentId, userId } });

    const playerCount = await tx.tournamentPlayer.count({
      where: { tournamentId },
    });
    if (playerCount < tournamentCapacity(tournament.format)) {
      return "registered";
    }

    await drawBracket(tx, tournament, now);
    return "drawn";
  });
}

/**
 * Removes a user from a tournament that is still open for registration. The creator can't leave; they cancel instead
 * @param tournamentId the ID of the tournament to leave
 * @param userId the user leaving
 * @returns "left", or the reason the user can't leave
 */
export async function unregisterPlayer(
  tournamentId: string,
  userId: string,
): Promise<UnregisterResult> {
  return await prisma.$transaction(async (tx) => {
    // The lock keeps a player from leaving while the registration that fills the tournament draws the bracket
    await lockTournament(tx, tournamentId);

    const tournament = await tx.tournament.findUnique({
      where: { id: tournamentId },
      select: { status: true, creatorId: true },
    });
    if (tournament === null) {
      return "notFound";
    }
    if (tournament.status !== TournamentStatus.REGISTRATION) {
      return "notOpen";
    }
    if (tournament.creatorId === userId) {
      return "creator";
    }

    const { count } = await tx.tournamentPlayer.deleteMany({
      where: { tournamentId, userId },
    });
    return count === 0 ? "notRegistered" : "left";
  });
}

/**
 * Cancels a tournament that is still open for registration. Only its creator can cancel it. The tournament is kept,
 * marked as cancelled, so it leaves the feed and nobody can join it
 * @param tournamentId the ID of the tournament to cancel
 * @param userId the user cancelling the tournament
 * @returns "cancelled", or the reason the user can't cancel it
 */
export async function markTournamentCancelled(
  tournamentId: string,
  userId: string,
): Promise<CancelResult> {
  return await prisma.$transaction(async (tx) => {
    // The lock keeps a registration from drawing the bracket of a tournament being cancelled
    await lockTournament(tx, tournamentId);

    const tournament = await tx.tournament.findUnique({
      where: { id: tournamentId },
      select: { status: true, creatorId: true },
    });
    if (tournament === null) {
      return "notFound";
    }
    if (tournament.creatorId !== userId) {
      return "notCreator";
    }
    if (tournament.status !== TournamentStatus.REGISTRATION) {
      return "notOpen";
    }

    await tx.tournament.update({
      where: { id: tournamentId },
      data: { status: TournamentStatus.CANCELLED },
    });
    return "cancelled";
  });
}
