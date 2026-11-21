// Games, players and tournaments shaped like the repositories return them, for the live update tests
import type { Game } from "../src/generated/prisma/client";
import { Format, GameSource, GameStatus } from "../src/generated/prisma/enums";
import type { GameDetails } from "../src/games/games.repository";
import type { TournamentDetails } from "../src/tournaments/tournaments.repository";

export const testGameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
export const testTournamentId = "2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d";

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * @returns an open scheduled singles game starting tomorrow, as fetchGame returns it
 */
export function gameRow(overrides: Partial<Game> = {}): Game {
  return {
    id: testGameId,
    format: Format.SINGLES,
    startsAt: new Date(Date.now() + DAY_MS),
    courtId: 1,
    gameSource: GameSource.SCHEDULED,
    status: GameStatus.OPEN,
    createdAt: new Date(),
    ...overrides,
  };
}

/**
 * @param id the player's user ID, also used as their username and name
 * @param team 1 or 2
 * @returns a player as GameDetails lists them
 */
export function testPlayer(id: string, team: number): GameDetails["players"][number] {
  return {
    id,
    username: id,
    name: id,
    photoUrl: null,
    rating: 1500,
    team,
    acceptedAt: new Date(),
    scoreConfirmedAt: null,
    ratingBefore: null,
    ratingAfter: null,
  };
}

/**
 * @returns gameRow with its court, no players, no rounds, no equipment and no tournament, as fetchGameDetails returns it
 */
export function gameDetails(overrides: Partial<GameDetails> = {}): GameDetails {
  return {
    ...gameRow(),
    court: { id: 1, name: "Kitsilano Beach Park", latitude: 49.27, longitude: -123.15 },
    players: [],
    rounds: [],
    tournament: null,
    scoreProposal: null,
    equipment: [],
    ...overrides,
  };
}

/**
 * @param playerIds its players' user IDs
 * @returns a tournament in registration with only the fields the live updates use; the rest comes from the
 * database in the app
 */
export function tournamentDetails(playerIds: string[]): TournamentDetails {
  return {
    id: testTournamentId,
    name: "Fall Cup",
    status: "REGISTRATION",
    players: playerIds.map((id) => ({ id, username: id, name: id, rating: 1500, team: null })),
    matches: [],
  } as unknown as TournamentDetails;
}
