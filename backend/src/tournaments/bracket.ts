// Tournament bracket rules: forming and seeding the teams, the bracket's slots, and where a winner goes next.
// Pure functions only; bracket.repository.ts saves the results.
import { Format } from "../generated/prisma/enums";
import type { RoundScore } from "../ranking/ranking.types";

export const BRACKET_TEAMS = 8;
export const FINAL_ROUND = 3;

// Each round starts an hour after the previous one
export const ROUND_INTERVAL_MS = 60 * 60 * 1000;

// The seeds of each quarterfinal in bracket order, so the top two seeds can only meet in the final
export const QUARTERFINAL_SEEDS = [
  [1, 8],
  [4, 5],
  [3, 6],
  [2, 7],
] as const;

export interface Entrant {
  userId: string;
  rating: number;
  joinedAt: Date;
}

export interface BracketSlot {
  round: number;
  position: number;
  team1: number | null;
  team2: number | null;
}

export interface NextSlot {
  round: number;
  position: number;
  side: 1 | 2;
}

/**
 * @param format the format of the tournament
 * @returns how many players each team has
 */
export function teamSizeOf(format: Format): number {
  switch (format) {
    case Format.SINGLES:
      return 1;
    case Format.DOUBLES:
      return 2;
  }
}

/**
 * @param format the format of the tournament
 * @returns how many players fill the bracket: 8 in singles, 16 in doubles
 */
export function tournamentCapacity(format: Format): number {
  return BRACKET_TEAMS * teamSizeOf(format);
}

/**
 * Orders entrants from the highest rating down; the earlier registration, then the user ID, breaks ties
 * so the order never depends on how the database returned the rows
 */
function byRating(a: Entrant, b: Entrant): number {
  return (
    b.rating - a.rating ||
    a.joinedAt.getTime() - b.joinedAt.getTime() ||
    a.userId.localeCompare(b.userId)
  );
}

/**
 * @param team the players of a team
 * @returns the team's average rating
 */
function averageRating(team: Entrant[]): number {
  return team.reduce((sum, { rating }) => sum + rating, 0) / team.length;
}

/**
 * Pairs the strongest player with the weakest, the second strongest with the second weakest, and so on,
 * so every doubles team has about the same average rating
 * @param ranked the entrants, highest rating first
 * @returns the pairs, in the order they were formed
 */
function balancedPairs(ranked: Entrant[]): Entrant[][] {
  const remaining = [...ranked];
  const pairs: Entrant[][] = [];
  while (remaining.length > 0) {
    const strongest = remaining.shift();
    const weakest = remaining.pop();
    if (strongest && weakest) {
      pairs.push([strongest, weakest]);
    }
  }
  return pairs;
}

/**
 * Forms the bracket's 8 teams and seeds them by rating. In singles every player is a team; in doubles the players
 * are paired into balanced teams. Teams are seeded by average rating, highest first
 * @param entrants every registered player
 * @param format the format of the tournament
 * @returns each team's user IDs, top seed first, so index 0 is seed 1
 * @throws Error if the entrants do not fill the bracket exactly
 */
export function formTeams(entrants: Entrant[], format: Format): string[][] {
  const capacity = tournamentCapacity(format);
  if (entrants.length !== capacity) {
    throw new Error(
      `A ${format} bracket needs ${capacity} players, got ${entrants.length}`,
    );
  }

  const ranked = [...entrants].sort(byRating);
  const teams =
    format === Format.SINGLES
      ? ranked.map((entrant) => [entrant])
      : balancedPairs(ranked);

  // The sort is stable, so equal averages keep the pairing order
  return teams
    .sort((a, b) => averageRating(b) - averageRating(a))
    .map((team) => team.map(({ userId }) => userId));
}

/**
 * Lists every slot of the bracket: the quarterfinals with their seeds, then the semifinals and the final,
 * whose teams are not known until the matches before them are decided
 * @returns the 7 slots, ordered by round and position
 */
export function bracketSlots(): BracketSlot[] {
  const slots: BracketSlot[] = QUARTERFINAL_SEEDS.map(
    ([team1, team2], position) => ({ round: 1, position, team1, team2 }),
  );

  for (let round = 2; round <= FINAL_ROUND; round++) {
    const matchCount = BRACKET_TEAMS / 2 ** round;
    for (let position = 0; position < matchCount; position++) {
      slots.push({ round, position, team1: null, team2: null });
    }
  }

  return slots;
}

/**
 * Finds where the winner of a match plays next: the winners of positions 0 and 1 meet in position 0
 * of the next round, the winners of 2 and 3 in position 1, and so on
 * @param round the round of the decided match
 * @param position the position of the decided match in its round
 * @returns the next match and the side the winner plays on, or null after the final
 */
export function nextSlot(round: number, position: number): NextSlot | null {
  if (round >= FINAL_ROUND) {
    return null;
  }

  return {
    round: round + 1,
    position: Math.floor(position / 2),
    side: position % 2 === 0 ? 1 : 2,
  };
}

/**
 * Decides which team won a game, the same way the score is validated: the team that won more rounds
 * @param rounds the [team 1 score, team 2 score] of each round
 * @returns 1 if team 1 won, otherwise 2
 */
export function winningSide(rounds: RoundScore[]): 1 | 2 {
  const team1Wins = rounds.filter(([team1, team2]) => team1 > team2).length;
  return team1Wins * 2 > rounds.length ? 1 : 2;
}

/**
 * Schedules a round: the quarterfinals at the tournament's start, each later round an hour after the previous one.
 * A round whose time has already passed (because earlier matches ran late) starts now
 * @param startsAt the start of the tournament
 * @param round the round to schedule, 1 for the quarterfinals
 * @param now the current time
 * @returns the start time of the round's games
 */
export function roundStartsAt(startsAt: Date, round: number, now: Date): Date {
  const scheduled = startsAt.getTime() + (round - 1) * ROUND_INTERVAL_MS;
  return new Date(Math.max(scheduled, now.getTime()));
}
