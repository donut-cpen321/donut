import { GameSource, GameStatus, type Format, type FormatPreference } from '../generated/prisma/enums';
import {
  cancelMatchesStartingBefore,
  claimMatch,
  createRequest,
  deleteRequest,
  findRequest,
  getCandidates,
  getCourts,
  getMatchedPlayers,
  hasActiveMatch,
} from './matchmaking.repository';
import type { JoinQueueInput } from './matchmaking.schemas';
import { distanceKm, midpoint, type Location } from './geo';
import { pushToUsers } from '../users/users.service';
import { MATCH_ANSWER_DEADLINE_MS } from '../games/games.repository';

const MATCH_START_DELAY_MS = 60 * 60 * 1000;
export const MAX_DISTANCE_KM = 10;

interface FormatRule {
  format: Format;
  size: number;
  maxRatingSpread: number;
}

export interface Candidate extends Location {
  userId: string;
  format: FormatPreference;
  rating: number;
  createdAt: Date;
}

export interface Match {
  format: Format;
  userIds: string[];
}

export interface CourtLocation extends Location {
  id: number;
}

export interface QueueEntry extends Location {
  format: FormatPreference;
  createdAt: Date;
}

export interface ClaimedGame {
  id: string;
  court: { name: string };
}

export interface MatchedPlayer {
  id: string;
  rating: number;
  preferredCourts: CourtLocation[];
}

export class ActiveMatchError extends Error {}

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

      // Find the closest-rated players still waiting near this player
      const partners = queue
        .filter((c) => c !== player && !matchedUserIds.has(c.userId))
        .filter((c) => distanceKm(c, player) <= MAX_DISTANCE_KM)
        .sort((a, b) => Math.abs(a.rating - player.rating) - Math.abs(b.rating - player.rating))
        .slice(0, formatRule.size - 1);

      // Not enough players are waiting near this player, others may still match
      if (partners.length < formatRule.size - 1) continue;

      const group = [player, ...partners];
      const ratings = group.map((c) => c.rating);

      const ratingSpread = Math.max(...ratings) - Math.min(...ratings);
      if (ratingSpread <= formatRule.maxRatingSpread && isWithinDistance(group)) {
        matches.push({ format: formatRule.format, userIds: group.map((c) => c.userId) });
        group.forEach((c) => matchedUserIds.add(c.userId));
      }
    }
  }

  return matches;
}

/**
 * Checks that every pair of players is at most MAX_DISTANCE_KM apart.
 */
function isWithinDistance(group: Location[]): boolean {
  return group.every((a) => group.every((b) => distanceKm(a, b) <= MAX_DISTANCE_KM));
}

/**
 * Picks the court that appears in the most players' preferred courts, among the courts
 * at most MAX_DISTANCE_KM from every player. A tie goes to the court nearest the players.
 *
 * @param players - The matched players with their preferred courts.
 * @param locations - Where each player wants to play.
 * @returns The id of the chosen court, or undefined if no player has a preferred court close enough.
 */
export function chooseCourt(players: MatchedPlayer[], locations: Location[]): number | undefined {
  const center = midpoint(locations);
  const isCloseToEveryone = (court: CourtLocation) =>
    locations.every((location) => distanceKm(location, court) <= MAX_DISTANCE_KM);

  const votes = new Map<number, { court: CourtLocation; count: number }>();
  const preferredCourts = players.flatMap((player) => player.preferredCourts).filter(isCloseToEveryone);
  for (const court of preferredCourts) {
    votes.set(court.id, { court, count: (votes.get(court.id)?.count ?? 0) + 1 });
  }

  const [mostPreferred] = [...votes.values()].sort(
    (a, b) => b.count - a.count || distanceKm(center, a.court) - distanceKm(center, b.court),
  );
  return mostPreferred?.court.id;
}

/**
 * Picks the court nearest the middle of the players, for matches without a close preferred court.
 *
 * @param courts - Every court.
 * @param locations - Where each player wants to play.
 * @returns The id of the nearest court, or undefined if there are no courts.
 */
export function nearestCourt(courts: CourtLocation[], locations: Location[]): number | undefined {
  const center = midpoint(locations);
  const [nearest] = [...courts].sort((a, b) => distanceKm(center, a) - distanceKm(center, b));
  return nearest?.id;
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

  const locationsById = new Map(candidates.map((c) => [c.userId, c]));
  let courts: CourtLocation[] | undefined;

  // Claim each match on its own
  const claimedMatches: Match[] = [];
  for (const match of matches) {
    const players = await getMatchedPlayers(match.userIds);
    const locations = match.userIds.flatMap((id) => locationsById.get(id) ?? []);
    let courtId = chooseCourt(players, locations);
    if (courtId === undefined) {
      courts ??= await getCourts();
      courtId = nearestCourt(courts, locations);
    }
    // No courts in the database at all
    if (courtId === undefined) continue;
    const startTime = new Date(Date.now() + MATCH_START_DELAY_MS);
    const teams = assignTeams(match, players);
    const playersData = teams.map(({ userId, team }) => ({ team, user: { connect: { id: userId } } }));
    
    const game = {
      format: match.format,
      status: GameStatus.OPEN,
      startsAt: startTime,
      court: { connect: { id: courtId } },
      gameSource: GameSource.MATCHED,
      players: {
        create: playersData,
      },
    };

    const claimedGame = await claimMatch(match.userIds, game);
    if (!claimedGame) continue;
    claimedMatches.push(match);
    await notifyMatch(match, claimedGame);
  }

  return claimedMatches;
}

/**
 * Sends every player in a new match a push notification.
 *
 * @param match - The matched players and format.
 * @param game - The game created for them.
 */
async function notifyMatch(match: Match, game: ClaimedGame): Promise<void> {
  try {
    await pushToUsers(match.userIds, {
      title: 'Match found!',
      body: `Your ${match.format.toLowerCase()} game at ${game.court.name} starts in 1 hour. Open Donut to accept.`,
      data: { gameId: game.id },
    });
  } catch (err) {
    console.error(`Could not notify the players of game ${game.id}`, err);
  }
}

/**
 * Cancels the matches whose players didn't all accept by 30 minutes before the start.
 *
 * @param now - The current time.
 * @returns The number of matches cancelled.
 */
export async function cancelUnansweredMatches(now = new Date()): Promise<number> {
  return await cancelMatchesStartingBefore(new Date(now.getTime() + MATCH_ANSWER_DEADLINE_MS));
}

/**
 * Puts a player in the queue to be matched by the next matchmaking run.
 *
 * @param userId - The player joining.
 * @param input - The format and where they want to play.
 * @returns The new queue entry.
 * @throws Prisma P2002 if the player is already in the queue; they have to leave first.
 * @throws ActiveMatchError if the player has a matched game waiting for answers, or booked and not started.
 */
export async function joinQueue(userId: string, input: JoinQueueInput): Promise<QueueEntry> {
  if (await hasActiveMatch(userId, new Date())) throw new ActiveMatchError();
  return await createRequest(userId, input);
}

/**
 * Takes a player out of the queue. Leaving when not in it is not an error.
 *
 * @param userId - The player leaving.
 */
export async function leaveQueue(userId: string): Promise<void> {
  await deleteRequest(userId);
}

/**
 * @param userId - The player to look up.
 * @returns Their queue entry, or null once they left or were matched.
 */
export async function getQueueEntry(userId: string): Promise<QueueEntry | null> {
  return await findRequest(userId);
}
