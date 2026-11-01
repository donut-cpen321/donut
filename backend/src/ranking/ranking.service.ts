// New ratings after a game: reads the players' ratings, applies elo.ts, saves via ratings.repository.
import { Format, Prisma } from "../generated/prisma/client";
import { calculateELOChange } from "./elo";
import { PlayerInfo, RatingChange, RoundScore } from "./ranking.types";
import {
  fetchGamePlayerRatingsForUpdate,
  saveRatingChanges,
} from "./ratings.repository";

// Thrown when a game can't be rated because its teams are not full
export class IncompleteTeamsError extends Error {
  constructor(format: Format, team1Size: number, team2Size: number) {
    super(
      `${format} game needs full teams to be rated, got ${team1Size} vs ${team2Size}`,
    );
    this.name = "IncompleteTeamsError";
  }
}

/**
 * @param format the format of the game
 * @returns how many players each team needs
 */
function teamSizeFor(format: Format): number {
  switch (format) {
    case Format.SINGLES:
      return 1;
    case Format.DOUBLES:
      return 2;
  }
}

/**
 * Calculates and saves every player's new rating after a game. Must run inside the
 * transaction that saves the final score, so the score and ratings are saved together or not at all
 * @param tx the transaction the final score is being saved in
 * @param gameId the game ID of the finished game
 * @param format the format of the game
 * @param rounds the [team 1 score, team 2 score] of each round, in order
 * @returns each player's rating before and after the game
 * @throws IncompleteTeamsError if either team is missing players
 */
export async function applyGameResult(
  tx: Prisma.TransactionClient,
  gameId: string,
  format: Format,
  rounds: RoundScore[],
): Promise<RatingChange[]> {
  const players = await fetchGamePlayerRatingsForUpdate(tx, gameId);

  const eloPlayers = players.filter(
    (player): player is PlayerInfo => player.team === 1 || player.team === 2,
  );

  const team1Size = eloPlayers.filter(({ team }) => team === 1).length;
  const team2Size = eloPlayers.filter(({ team }) => team === 2).length;

  const teamSize = teamSizeFor(format);
  if (
    players.length !== eloPlayers.length ||
    team1Size !== teamSize ||
    team2Size !== teamSize
  ) {
    throw new IncompleteTeamsError(format, team1Size, team2Size);
  }

  const changes = calculateELOChange(eloPlayers, { format, scores: rounds });
  await saveRatingChanges(tx, gameId, changes);

  return changes;
}
