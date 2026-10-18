// Pure Elo math and calculations
import { Sport, Format } from '../generated/prisma/client'
import { GameInfo, PlayerInfo, RatingChange, SportEloParams } from './scoring.types'

const SPORT_PARAMS: Record<Sport, SportEloParams> = {
    TENNIS:     { alpha: 6,  beta: 0.0025, lambda: 1.5, k: 50},
    PICKLEBALL: { alpha: 8,  beta: 0.0025, lambda: 1.0, k: 60},
    BADMINTON:  { alpha: 15, beta: 0.005, lambda: 0.5, k: 32},
};

/**
 * Calculates the probability that player A beats player B.
 *
 * @param playerA_Rating - Rating of player (or team) A
 * @param playerB_Rating - Rating of player (or team) B
 * @returns A value between 0 and 1, where 0.5 means an even match
 */
function calculateExpectedOutcome(
    playerA_Rating: number,
    playerB_Rating: number,
): number {
    const rDelta = playerA_Rating - playerB_Rating;
    return 1 / ( 1 + 10 ** ((-rDelta)/400) );
}

/**
 * Calculates the expected margin of victory for player A over player B.
 * The margin grows with the rating gap but levels off at the sport's
 * maximum (alpha), so huge rating gaps don't predict unrealistic blowouts.
 *
 * @param playerA_Rating - Rating of player (or team) A
 * @param playerB_Rating - Rating of player (or team) B
 * @param sport - Sport being played; selects alpha and beta from SPORT_PARAMS
 * @returns Expected points A wins by, between -alpha and +alpha
 *          (negative means B is expected to win)
 */
function calculateExpectedMargin(
    playerA_Rating: number,
    playerB_Rating: number,
    sport: Sport,
): number {
    const rDelta = playerA_Rating - playerB_Rating;
    const alpha = SPORT_PARAMS[sport].alpha;
    const beta = SPORT_PARAMS[sport].beta;

    return alpha * Math.tanh(beta * rDelta);
}

/**
 * Calculates how much side A's rating changes after a game, combining the
 * win/loss result with how much the margin of victory beat expectations.
 * Side B's change is the negative of this.
 *
 * @param playerA_Rating - Rating of player (or team average) A, who must be team 1
 * @param playerB_Rating - Rating of player (or team average) B, who must be team 2
 * @param game - Sport (selects k and lambda from SPORT_PARAMS) and round scores
 * @returns The amount to add to A's rating (negative if A lost rating)
 */
function calculateRatingUpdate(
    playerA_Rating: number,
    playerB_Rating: number,
    game: GameInfo,
): number {

    const expectedOutcome = calculateExpectedOutcome(playerA_Rating, playerB_Rating);
    const expectedMargin = calculateExpectedMargin(playerA_Rating, playerB_Rating, game.sport);

    const param = SPORT_PARAMS[game.sport];

    let teamATotal = 0;
    let teamBTotal = 0;

    let roundsAWin = 0;
    let roundsBWin = 0;

    for (const round of game.score) {

        if (round.team1Score === round.team2Score) {
            throw new Error(`Round cannot be tied (${round.team1Score}-${round.team2Score})`);
        }

        if(round.team1Score > round.team2Score){
            roundsAWin ++;
        } else {
            roundsBWin ++;
        }

        teamATotal += round.team1Score;
        teamBTotal += round.team2Score;
    }

    if (roundsAWin === roundsBWin) {
        throw new Error(`Match has no winner (${roundsAWin}-${roundsBWin} in rounds)`);
    }

    const margin = teamATotal - teamBTotal;
    const actualOutcome = roundsAWin > roundsBWin ? 1 : 0;   // S_A
    const marginSurprise = margin - expectedMargin;
    const rUpdate = param.k * (actualOutcome - expectedOutcome) + param.lambda * marginSurprise;
 
    return rUpdate;
}

/**
 * Calculates the new rating for every player in a finished game.
 * In doubles, each team is rated by its players' average rating and each
 * player receives half of the team's rating change.
 *
 * @param players - Singles: [A, B] with A on team 1 and B on team 2.
 *                  Doubles: [A, B, C, D] with A/B on team 1 and C/D on team 2.
 * @param game - Sport, format and round scores of the game
 * @returns One RatingChange per player, in the same order as `players`
 * @throws If the player count doesn't match the format or players are on the wrong teams
 */
export function calculateELOChange(
    players: PlayerInfo[],
    game: GameInfo,
): RatingChange[] {

    if(game.score.length === 0){
        throw new Error(`Game has no score information`);
    }

    switch (game.format) {

        // the singles calculation
        case Format.SINGLES: {
            const [playerA, playerB] = players;
            if (!playerA || !playerB || players.length !== 2) {
                throw new Error(`Singles game needs exactly 2 players, got ${players.length}`);
            }

            // A must be team 1 and B must be team 2 so the score totals line up
            if (playerA.team !== 1 || playerB.team !== 2) {
                throw new Error(`Player A (${playerA.userId}) must be on team 1 and player B (${playerB.userId}) on team 2`);
            }

            const rUpdate = calculateRatingUpdate(playerA.rating, playerB.rating, game);
            return [
                {
                    userId: playerA.userId,
                    ratingBefore: playerA.rating,
                    ratingAfter: playerA.rating + rUpdate,
                },
                {
                    userId: playerB.userId,
                    ratingBefore: playerB.rating,
                    ratingAfter: playerB.rating - rUpdate,
                }
            ]

        }

        // the doubles case
        case Format.DOUBLES: {
            const [playerA, playerB, playerC, playerD] = players;
            if (!playerA || !playerB || !playerC || !playerD || players.length !== 4) {
                throw new Error(`Doubles game needs exactly 4 players, got ${players.length}`);
            }

            // A/B must be team 1 and C/D must be team 2 so the score totals line up
            if (playerA.team !== 1 || playerB.team !== 1) {
                throw new Error(`Players A (${playerA.userId}) and B (${playerB.userId}) must be on team 1`);
            }
            if (playerC.team !== 2 || playerD.team !== 2) {
                throw new Error(`Players C (${playerC.userId}) and D (${playerD.userId}) must be on team 2`);
            }
            // calculate elo average
            const teamAB_avg = (playerA.rating + playerB.rating)/2;
            const teamCD_avg = (playerC.rating + playerD.rating)/2;

            // calcualte Team ELO changes
            const teamELOChange = calculateRatingUpdate(
                teamAB_avg, teamCD_avg, game
            )

            // calculate individual player elo changes
            return [
                {
                    userId: playerA.userId,
                    ratingBefore: playerA.rating,
                    ratingAfter: playerA.rating + teamELOChange/2,
                },
                {
                    userId: playerB.userId,
                    ratingBefore: playerB.rating,
                    ratingAfter: playerB.rating + teamELOChange/2,
                },
                {
                    userId: playerC.userId,
                    ratingBefore: playerC.rating,
                    ratingAfter: playerC.rating - teamELOChange/2,
                },
                {
                    userId: playerD.userId,
                    ratingBefore: playerD.rating,
                    ratingAfter: playerD.rating - teamELOChange/2,
                },
            ]
        }
    }
}