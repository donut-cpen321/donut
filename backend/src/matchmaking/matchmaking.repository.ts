// Queries for overlapping availabilities, nearby courts, close ratings.
import { prisma } from '../db';
import type { Candidate } from './matchmaking.service';

/**
 * Get all players searching for a game right now and include their rating.
 * @returns List of candidates.
 */
export async function getCandidates(): Promise<Candidate[]> {
  const requests = await prisma.matchmakingRequest.findMany({
    select: {
      userId: true,
      sport: true,
      format: true,
      createdAt: true,
      userRating: { select: { rating: true } },
    },
  });

  // Map the requests to the Candidate interface.
  return requests.map(({ userId, sport, format, createdAt, userRating }) => ({
    userId,
    sport,
    format,
    createdAt,
    rating: userRating.rating,
  }));
}

/**
 * Remove players that have been already matched.
 * 
 * @param userIds - List of user IDs to delete.
 * @returns void
 */
export async function deleteRequests(userIds: string[]): Promise<void> {
  await prisma.matchmakingRequest.deleteMany({ where: { userId: { in: userIds } } });
}
