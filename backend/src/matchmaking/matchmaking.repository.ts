// Queries for overlapping availabilities, nearby courts, close ratings.
import { prisma } from '../db';
import type { Prisma } from '../generated/prisma/client';
import type { Candidate, CourtLocation, MatchedPlayer, QueueEntry } from './matchmaking.service';
import type { JoinQueueInput } from './matchmaking.schemas';

/**
 * Get all players searching for a game right now and include their rating.
 * @returns List of candidates.
 */
export async function getCandidates(): Promise<Candidate[]> {
  const requests = await prisma.matchmakingRequest.findMany({
    select: {
      userId: true,
      format: true,
      latitude: true,
      longitude: true,
      createdAt: true,
      user: { select: { rating: true } },
    },
  });

  // Map the requests to the Candidate interface.
  const candidates: Candidate[] = requests.map(({ userId, format, latitude, longitude, createdAt, user }) => ({
    userId,
    format,
    latitude,
    longitude,
    createdAt,
    rating: user.rating,
  }));

  return candidates;
}

/**
 * Get the matched players with their rating and preferred courts.
 * @param userIds - The ids of the matched players.
 * @returns List of matched players.
 */
export async function getMatchedPlayers(userIds: string[]): Promise<MatchedPlayer[]> {
  return await prisma.user.findMany({
    where: { id: { in: userIds } },
    select: {
      id: true,
      rating: true,
      preferredCourts: { select: { id: true, latitude: true, longitude: true } },
    },
  });
}

/**
 * Get every court with its location, for matches where no player has a preferred court nearby.
 * @returns List of courts.
 */
export async function getCourts(): Promise<CourtLocation[]> {
  return await prisma.court.findMany({ select: { id: true, latitude: true, longitude: true } });
}

class MatchUnavailableError extends Error {}

/**
 * Claims a match by removing its players from the queue and creating the proposed game.
 *
 * @param userIds - The ids of the matched players.
 * @param game - The game to create for them.
 * @returns true if every player was still waiting and the game was created.
 */
export async function claimMatch(userIds: string[], game: Prisma.GameCreateInput): Promise<boolean> {
  try {
    await prisma.$transaction(async (tx) => {
      const { count } = await tx.matchmakingRequest.deleteMany({
        where: { userId: { in: userIds } },
      });

      // If not all players are still waiting
      if (count !== userIds.length) throw new MatchUnavailableError();

      await tx.game.create({ data: game });
    });
    return true;
  } catch (err) {
    if (err instanceof MatchUnavailableError) return false;
    throw err;
  }
}

/**
 * Puts a player in the queue.
 * @param userId - The player joining.
 * @param input - The format and where they want to play.
 * @returns The new queue entry.
 * @throws Prisma P2002 if the player is already in the queue.
 */
export async function createRequest(userId: string, input: JoinQueueInput): Promise<QueueEntry> {
  return await prisma.matchmakingRequest.create({ 
    data: { userId, ...input }, 
    select: { format: true, createdAt: true, latitude: true, longitude: true } 
  });
}

/**
 * Takes a player out of the queue. Does nothing if they are not in it.
 * @param userId - The player leaving.
 */
export async function deleteRequest(userId: string): Promise<void> {
  await prisma.matchmakingRequest.deleteMany({ where: { userId } });
}

/**
 * @param userId - The player to look up.
 * @returns Their queue entry, or null if they are not waiting.
 */
export async function findRequest(userId: string): Promise<QueueEntry | null> {
  return await prisma.matchmakingRequest.findUnique({ 
    where: { userId }, 
    select: { format: true, createdAt: true, latitude: true, longitude: true } 
  });
}
