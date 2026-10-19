// Finds matches and proposes games (via games.service); cancels unaccepted matches at the deadline.
import { Format, Sport, type FormatPreference } from '../generated/prisma/enums';
import { deleteRequests, getCandidates } from './matchmaking.repository';

// Max rating gap inside one match; doubles is wider since teams can balance strong and weak players.
const MAX_RATING_SPREAD: Record<Format, number> = { SINGLES: 200, DOUBLES: 400 };
const PLAYERS_PER_MATCH: Record<Format, number> = { SINGLES: 2, DOUBLES: 4 };

export interface Candidate {
  userId: string;
  sport: Sport;
  format: FormatPreference;
  rating: number;
  createdAt: Date;
}

export interface Match {
  sport: Sport;
  format: Format;
  userIds: string[];
}

/**
 * Finds matches for a list of candidates using a greedy algorithm to find the longest-waiting players. 
 * 
 * @param candidates - List of candidates to find matches for.
 * @returns List of matches found by the matchmaking algorithm.
 */
export function findMatches(candidates: Candidate[]): Match[] {
  // Set of user IDs that have been matched in this run.
  const matchedUserIds = new Set<string>();

  // List of matches found in this run.
  const matches: Match[] = [];

  const formats = Object.values(Format);
  const sports = Object.values(Sport);

  for (const format of formats) {
    const size = PLAYERS_PER_MATCH[format];

    for (const sport of sports) {
      const sportFilter = (c: Candidate) => c.sport === sport;
      const formatFilter = (c: Candidate) => c.format === format || c.format === 'EITHER';
      const matchedFilter = (c: Candidate) => !matchedUserIds.has(c.userId);

      const filter = (c: Candidate) => sportFilter(c) && formatFilter(c) && matchedFilter(c);
      const eligibleCandidates = candidates.filter(filter);

      // Not enough candidates to form a match
      if (eligibleCandidates.length < size) continue;

      // Create the queue with the oldest candidates first
      const queue = eligibleCandidates.sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());

      for (const player of queue) {
        // If user has already been matched
        if (matchedUserIds.has(player.userId)) continue;

        // Find the closest-rated players still waiting
        const partners = queue
          .filter((c) => c !== player && !matchedUserIds.has(c.userId))
          .sort((a, b) => Math.abs(a.rating - player.rating) - Math.abs(b.rating - player.rating))
          .slice(0, size - 1);

        // Not enough players are waiting to form a match
        if (partners.length < size - 1) break;

        const group = [player, ...partners];
        const ratings = group.map((c) => c.rating);

        // If the rating spread is within the allowed range
        if (Math.max(...ratings) - Math.min(...ratings) <= MAX_RATING_SPREAD[format]) {
          // Add the match to the list of matches
          matches.push({ sport, format, userIds: group.map((c) => c.userId) });
          // Add the user IDs to the set of matched user IDs
          group.forEach((c) => matchedUserIds.add(c.userId));
        }
      }
    }
  }

  return matches;
}

/**
 * Finds matches and proposes games (not implemented yet)
 * 
 * @returns A list of matches found by the matchmaking algorithm.
 */
export async function runMatchmaking(): Promise<Match[]> {
  const candidates = await getCandidates();
  const matches = findMatches(candidates);

  // TODO: create a transaction that creates the MATCHED game and deletes the matchmaking requests.
  const matchedUserIds = matches.flatMap((m) => m.userIds);
  if (matchedUserIds.length > 0) await deleteRequests(matchedUserIds);

  return matches;
}
