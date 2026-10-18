// Pure Elo math and calculations
import { Sport, Format } from '../generated/prisma/client'
import { GameInfo, PlayerInfo, RatingChange, SportEloParams } from './scoring.types'

const SPORT_PARAMS: Record<Sport, SportEloParams> = {
    TENNIS:     { maxExpectedMargin: 6,  marginGrowthRate: 0.0025, marginWeight: 1.5, kFactor: 50},
    PICKLEBALL: { maxExpectedMargin: 8,  marginGrowthRate: 0.0025, marginWeight: 1.0, kFactor: 60},
    BADMINTON:  { maxExpectedMargin: 15, marginGrowthRate: 0.005, marginWeight: 0.5, kFactor: 32},
};

// determine how many points correspond to a given difference in skill
// 400 means the stronger side has 10-to-1 odds of winning (standard Elo scale)
const ELO_SCALE = 400;

/**
 * Calculates the probability that player A beats player B.
 *
 * @param playerARating - Rating of player (or team) A
 * @param playerBRating - Rating of player (or team) B
 * @returns A value between 0 and 1, where 0.5 means an even match
 */
function calculateExpectedOutcome(
    playerARating: number,
    playerBRating: number,
): number {
    const ratingGap = playerARating - playerBRating;
    return 1 / ( 1 + 10 ** ((-ratingGap)/ELO_SCALE) );
}

/**
 * Calculates the expected margin of victory for player A over player B.
 * The margin grows with the rating gap but levels off at the sport's
 * maximum (maxExpectedMargin), so huge rating gaps don't predict unrealistic blowouts.
 *
 * @param playerARating - Rating of player (or team) A
 * @param playerBRating - Rating of player (or team) B
 * @param sport - Sport being played; selects maxExpectedMargin and marginGrowthRate from SPORT_PARAMS
 * @returns Expected points A wins by, between -maxExpectedMargin and +maxExpectedMargin
 *          (negative means B is expected to win)
 */
function calculateExpectedMargin(
    playerARating: number,
    playerBRating: number,
    sport: Sport,
): number {
    const ratingGap = playerARating - playerBRating;
    const { maxExpectedMargin, marginGrowthRate } = SPORT_PARAMS[sport];

    return maxExpectedMargin * Math.tanh(marginGrowthRate * ratingGap);
}

/**
 * Calculates how much side A's rating changes after a game, combining the
 * win/loss result with how much the margin of victory beat expectations.
 * Side B's change is the negative of this.
 *
 * @param playerARating - Rating of player (or team average) A, who must be team 0
 * @param playerBRating - Rating of player (or team average) B, who must be team 1
 * @param game - Sport (selects kFactor and marginWeight from SPORT_PARAMS) and round scores
 * @returns The amount to add to A's rating (negative if A lost rating)
 */
function calculateRatingUpdate(
    playerARating: number,
    playerBRating: number,
    game: GameInfo,
): number {

    const expectedOutcome = calculateExpectedOutcome(playerARating, playerBRating);
    const expectedMargin = calculateExpectedMargin(playerARating, playerBRating, game.sport);

    const { marginWeight, kFactor } = SPORT_PARAMS[game.sport];

    let teamATotal = 0;
    let teamBTotal = 0;

    let teamAWins = 0;
    let teamBWins = 0;

    for (const round of game.rounds) {
        const teamAScore = round[0];
        const teamBScore = round[1];

        for (const value of [teamAScore, teamBScore]) {  
            if (!Number.isInteger(value) || value < 0) {  
                throw new Error(`Round score must be a non-negative integer (${teamAScore}-${teamBScore})`);  
            }  
        }  

        if (teamAScore === teamBScore) {  
            throw new Error(`Round cannot be tied (${teamAScore}-${teamBScore})`);  
        } 

        if(teamAScore > teamBScore){
            teamAWins ++;
        } else {
            teamBWins ++;
        }

        teamATotal += teamAScore;
        teamBTotal += teamBScore;
    }

    if (teamAWins === teamBWins) {
        throw new Error(`Match has no winner (${teamAWins}-${teamBWins} in rounds)`);
    }

    const margin = teamATotal - teamBTotal;
    const actualOutcome = teamAWins > teamBWins ? 1 : 0;   // S_A
    const marginSurprise = margin - expectedMargin;
    const rUpdate = kFactor * (actualOutcome - expectedOutcome) + marginWeight * marginSurprise;
 
    return rUpdate;
}

/**
 * Calculates the new rating for every player in a finished game.
 * In doubles, each team is rated by its players' average rating and each
 * player receives half of the team's rating change.
 *
 * @param players - Players in any order. Singles: one player on each of team 0 and team 1.
 *                  Doubles: two players on each team.
 * @param game - Sport, format and round scores of the game
 * @returns One RatingChange per player: team 0 players first, then team 1
 * @throws If the player count or team split doesn't match the format, or the
 *         rounds are empty, contain an invalid or tied score, or have no winner
 */
export function calculateELOChange(
    players: PlayerInfo[],
    game: GameInfo,
): RatingChange[] {

    if(game.rounds.length === 0){
        throw new Error(`Game has no round score information`);
    }

    // check for duplicated player
    if (new Set(players.map(player => player.userId)).size !== players.length) {
        throw new Error('Players must have unique userId values');
    }

    switch (game.format) {
        case Format.SINGLES: {
            if (players.length !== 2) {
                throw new Error(`Singles game needs exactly 2 players, got ${players.length}`);
            }

            const [playerA] = players.filter(p => p.team === 0);
            const [playerB] = players.filter(p => p.team === 1);

            if (!playerA || !playerB) {
                throw new Error('Singles game needs one player on each team');
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
        case Format.DOUBLES: {
            if (players.length !== 4) {
                throw new Error(`Doubles game needs exactly 4 players, got ${players.length}`);
            }

            const [playerA, playerB] = players.filter(p => p.team === 0);
            const [playerC, playerD] = players.filter(p => p.team === 1);

            if (!playerA || !playerB || !playerC || !playerD) {
                throw new Error('Doubles game needs two players on each team');
            }
            // calculate elo average
            const team0Avg = (playerA.rating + playerB.rating)/2;
            const team1Avg = (playerC.rating + playerD.rating)/2;

            // calculate Team ELO changes
            const teamELOChange = calculateRatingUpdate(
                team0Avg, team1Avg, game
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