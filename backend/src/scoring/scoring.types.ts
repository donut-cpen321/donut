// interface for elo calculation
import { Sport, Format } from '../generated/prisma/client';

export interface RoundScore {
    team1Score: number;
    team2Score: number;
}

export interface PlayerInfo {
    userId: string;
    team: 1 | 2;
    rating: number;
}

export interface GameInfo {
    sport: Sport;
    format: Format;
    score: RoundScore[];
}

export interface RatingChange {
    userId: string;
    ratingBefore: number;
    ratingAfter: number;
}

export interface SportEloParams {
    alpha: number;   // max expected margin
    beta: number;    // how fast expected margin grows with rating gap
    lambda: number;  // weight of margin surprise
    k: number;
}
