// interface for elo calculation
import { Sport, Format } from '../generated/prisma/client';

export type RoundScore = [number, number];   // [team 0 score, team 1 score]

export interface PlayerInfo {
    userId: string;
    team: 0 | 1;
    rating: number;
}

export interface GameInfo {
    sport: Sport;
    format: Format;
    scores: RoundScore[];
}

export interface RatingChange {
    userId: string;
    ratingBefore: number;
    ratingAfter: number;
}

export interface SportEloParams {
    kFactor: number;            // K: how much the win/loss result moves ratings
    maxExpectedMargin: number;  // α: cap on the predicted margin of victory
    marginGrowthRate: number;   // β: how fast the predicted margin grows with the rating gap
    marginWeight: number;       // λ: weight of the margin surprise in the update
}
