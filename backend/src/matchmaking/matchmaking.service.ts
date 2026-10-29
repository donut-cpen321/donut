// Finds matches and proposes games (via games.service); cancels unaccepted matches at the deadline.
import { GameSource, GameStatus, type Format, type FormatPreference } from '../generated/prisma/enums';
import { claimMatch, getCandidates, getMatchedPlayers } from './matchmaking.repository';

// Matched games start one hour after the match is found
const MATCH_START_DELAY_MS = 60 * 60 * 1000;

interface FormatRule {
  format: Format;
  size: number;
  maxRatingSpread: number;
}

export interface Candidate {
  userId: string;
  format: FormatPreference;
  rating: number;
  createdAt: Date;
}

export interface Match {
  format: Format;
  userIds: string[];
}

export interface CourtLocation {
  id: number;
  latitude: number;
  longitude: number;
}

export interface MatchedPlayer {
  id: string;
  rating: number;
  preferredCourts: CourtLocation[];
}

const FORMAT_RULES: FormatRule[] = [
  { format: 'SINGLES', size: 2, maxRatingSpread: 200 },
  { format: 'DOUBLES', size: 4, maxRatingSpread: 400 },
];

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

  for (const formatRule of FORMAT_RULES) {
    const formatFilter = (c: Candidate) => c.format === formatRule.format || c.format === 'EITHER';
    const matchedFilter = (c: Candidate) => !matchedUserIds.has(c.userId);

    const filter = (c: Candidate) => formatFilter(c) && matchedFilter(c);
    const eligibleCandidates = candidates.filter(filter);

    // Not enough candidates to form a match
    if (eligibleCandidates.length < formatRule.size) continue;

    // Create the queue with the oldest candidates first
    const queue = eligibleCandidates.sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());

    for (const player of queue) {
      // If user has already been matched
      if (matchedUserIds.has(player.userId)) continue;

      // Find the closest-rated players still waiting
      const partners = queue
        .filter((c) => c !== player && !matchedUserIds.has(c.userId))
        .sort((a, b) => Math.abs(a.rating - player.rating) - Math.abs(b.rating - player.rating))
        .slice(0, formatRule.size - 1);

      // Not enough players are waiting to form a match
      if (partners.length < formatRule.size - 1) break;

      const group = [player, ...partners];
      const ratings = group.map((c) => c.rating);

      // If the rating spread is within the allowed range
      if (Math.max(...ratings) - Math.min(...ratings) <= formatRule.maxRatingSpread) {
        // Add the match to the list of matches
        matches.push({ format: formatRule.format, userIds: group.map((c) => c.userId) });
        // Add the user IDs to the set of matched user IDs
        group.forEach((c) => matchedUserIds.add(c.userId));
      }
    }
  }

  return matches;
}

/**
 * Picks the court that appears in the most players' preferred courts.
 *
 * @param players - The matched players with their preferred courts.
 * @returns The id of the chosen court.
 */
export function chooseCourt(players: MatchedPlayer[]): number {
  const votes = new Map<number, number>();
  const preferredCourts = players.flatMap((player) => player.preferredCourts);
  for (const court of preferredCourts) {
    votes.set(court.id, (votes.get(court.id) ?? 0) + 1);
  }

  const [mostPreferred] = [...votes].sort(([, a], [, b]) => b - a);
  if (!mostPreferred) throw new Error('Matched players have no preferred courts');
  return mostPreferred[0];
}

/**
 * Puts singles players on opposite teams. For doubles, the highest- and
 * lowest-rated players play against the middle two.
 *
 * @param match - The match being claimed.
 * @param players - The matched players with their ratings.
 * @returns Each player's team.
 */
export function assignTeams(match: Match, players: MatchedPlayer[]): { userId: string; team: number }[] {
  const playersSortedByRating = [...players].sort((a, b) => b.rating - a.rating);
  const lastIndex = playersSortedByRating.length - 1;

  return playersSortedByRating.map((player, index) => {
    if (match.format === 'SINGLES') {
      return { userId: player.id, team: index + 1 };
    }

    // First (best) and last (worst) players play on team 1, the rest on team 2
    const team = (index === 0 || index === lastIndex) ? 1 : 2;
    return { userId: player.id, team };
  });
}

/**
 * Finds matches and proposes a game for each one.
 * 
 * @returns A list of the matches that were claimed.
 */
export async function runMatchmaking(): Promise<Match[]> {
  const candidates = await getCandidates();
  const matches = findMatches(candidates);
  if (matches.length === 0) return [];

  // Claim each match on its own
  const claimedMatches: Match[] = [];
  for (const match of matches) {
    const players = await getMatchedPlayers(match.userIds);
    const courtId = chooseCourt(players);
    const startTime = new Date(Date.now() + MATCH_START_DELAY_MS);
    const teams = assignTeams(match, players);
    
    const game = {
      format: match.format,
      status: GameStatus.OPEN,
      startsAt: startTime,
      courtId,
      gameSource: GameSource.MATCHED,
      players: { create: teams },
    };

    if (await claimMatch(match.userIds, game)) claimedMatches.push(match);
  }

  return claimedMatches;
}
