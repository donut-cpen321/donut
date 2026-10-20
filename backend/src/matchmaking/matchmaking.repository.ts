// Queries for overlapping availabilities, nearby courts, close ratings.
import { prisma } from '../db';
import type { Candidate, Match } from './matchmaking.service';

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
  const candidates: Candidate[] = requests.map(({ userId, sport, format, createdAt, userRating }) => ({
    userId,
    sport,
    format,
    createdAt,
    rating: userRating.rating,
  }));
  
  return candidates;
}

class MatchUnavailableError extends Error {}

/**
 * Claims a match by removing its players from the queue.
 *
 * @param match - The match to claim.
 * @returns true if every player was still waiting and is now out of the queue.
 */
export async function claimMatch(match: Match): Promise<boolean> {
  try {
    await prisma.$transaction(async (tx) => {
      const { count } = await tx.matchmakingRequest.deleteMany({
        where: { userId: { in: match.userIds }, sport: match.sport },
      });

      // If not all players are still waiting
      if (count !== match.userIds.length) throw new MatchUnavailableError();
      
      // TODO: create the MATCHED game here.
    });
    return true;
  } catch (err) {
    if (err instanceof MatchUnavailableError) return false;
    throw err;
  }
}
