// Queries for overlapping availabilities, nearby courts, close ratings.
import { randomInt } from 'node:crypto';
import { prisma } from '../db';
import type { Prisma } from '../generated/prisma/client';
import type { Candidate, MatchedPlayer } from './matchmaking.service';

/**
 * Get all players searching for a game right now and include their rating.
 * @returns List of candidates.
 */
export async function getCandidates(): Promise<Candidate[]> {
  const requests = await prisma.matchmakingRequest.findMany({
    select: {
      userId: true,
      format: true,
      createdAt: true,
      user: { select: { rating: true } },
    },
  });

  // Map the requests to the Candidate interface.
  const candidates: Candidate[] = requests.map(({ userId, format, createdAt, user }) => ({
    userId,
    format,
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
 * Get a random court, for matches where no player has a preferred court.
 * @returns The id of the court.
 */
export async function getRandomCourtId(): Promise<number> {
  const count = await prisma.court.count();
  const court = await prisma.court.findFirstOrThrow({ select: { id: true }, skip: randomInt(count) });
  return court.id;
}

class MatchUnavailableError extends Error {}

/**
 * Claims a match by removing its players from the queue and creating the proposed game.
 *
 * @param userIds - The ids of the matched players.
 * @param game - The game to create for them.
 * @returns true if every player was still waiting and the game was created.
 */
export async function claimMatch(userIds: string[], game: Prisma.GameUncheckedCreateInput): Promise<boolean> {
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
