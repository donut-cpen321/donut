// interface for elo calculation
import { Format } from '../generated/prisma/client';

export type RoundScore = [number, number];   // [team 1 score, team 2 score]

export interface PlayerInfo {
    userId: string;
    team: 1 | 2;
    rating: number;
}

export interface GameInfo {
    format: Format;
    scores: RoundScore[];
}

export interface RatingChange {
    userId: string;
    ratingBefore: number;
    ratingAfter: number;
}

export interface EloParams {
    kFactor: number;            // K: how much the win/loss result moves ratings
    maxExpectedMargin: number;  // α: cap on the predicted margin of victory
    marginGrowthRate: number;   // β: how fast the predicted margin grows with the rating gap
    marginWeight: number;       // λ: weight of the margin surprise in the update
}
