import { calculateELOChange } from "../../src/ranking/elo";
import type { RoundScore } from "../../src/ranking/ranking.types";

// NFR: rating accuracy for new players. Simulates new players of known true level who start at 1500 and play
// rated tennis singles against established opponents, the way matchmaking pairs them (within 200 of the new
// player's current rating). Each game is played out game by game: the new player wins each tennis game with the
// probability that makes their match-win chance equal the Elo expectation from the true ratings, so the scores
// don't come from the rating formula itself. A fixed seed keeps every run identical.

const START_RATING = 1500;
// Singles matchmaking only pairs players within this rating spread (matchmaking.service.ts), so a rating within it
// of the player's true level puts them with players of their level
const MATCHMAKING_SPREAD = 200;
const PLAYERS = 2000;
const SEED = 321;
const ESTABLISHED_GAMES_PLAYED = 50;

interface SimulationResult {
  // |rating - true level| of each player, ascending
  errors: number[];
  // Mean rating of the players, after the given number of games
  meanRating: number;
}

/**
 * @returns a seeded PRNG of numbers in [0, 1) (mulberry32), so the simulation is the same every run
 */
function seededRandom(seed: number): () => number {
  let state = seed;
  return () => {
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const setOver = (a: number, b: number) => (a >= 6 && a - b >= 2) || a === 7;

/**
 * @param gameWin the chance of winning each tennis game
 * @returns the chance of winning a set to 6 by 2, with 6-6 decided by a tiebreak counted as one game
 */
function setWinChance(gameWin: number): number {
  const memo = new Map<string, number>();
  const fromScore = (a: number, b: number): number => {
    if (setOver(a, b)) return 1;
    if (setOver(b, a)) return 0;
    const key = `${a}-${b}`;
    const known = memo.get(key);
    if (known !== undefined) return known;
    const chance = gameWin * fromScore(a + 1, b) + (1 - gameWin) * fromScore(a, b + 1);
    memo.set(key, chance);
    return chance;
  };
  return fromScore(0, 0);
}

/**
 * @param ratingGap the new player's true rating minus the opponent's
 * @returns the chance of winning each game that makes winning a best-of-3-sets match as likely as Elo expects
 */
function gameWinChance(ratingGap: number): number {
  const expected = 1 / (1 + 10 ** (-ratingGap / 400));
  let low = 0;
  let high = 1;
  for (let step = 0; step < 50; step++) {
    const middle = (low + high) / 2;
    const set = setWinChance(middle);
    if (set * set * (3 - 2 * set) < expected) {
      low = middle;
    } else {
      high = middle;
    }
  }
  return (low + high) / 2;
}

/**
 * Plays a best-of-3-sets match game by game
 * @returns each set's [new player's games, opponent's games]
 */
function playMatch(gameWin: number, random: () => number): RoundScore[] {
  const sets: RoundScore[] = [];
  let setsWon = 0;
  let setsLost = 0;
  while (setsWon < 2 && setsLost < 2) {
    let a = 0;
    let b = 0;
    while (!setOver(a, b) && !setOver(b, a)) {
      if (random() < gameWin) a++;
      else b++;
    }
    sets.push([a, b]);
    if (a > b) setsWon++;
    else setsLost++;
  }
  return sets;
}

/**
 * Plays every simulated new player's first games
 * @param games how many rated games each player plays
 * @param trueLevel the true level of player `index`
 */
function simulate(games: number, trueLevel: (index: number, random: () => number) => number): SimulationResult {
  const random = seededRandom(SEED);
  const errors: number[] = [];
  let ratingSum = 0;
  for (let index = 0; index < PLAYERS; index++) {
    const truth = trueLevel(index, random);
    let rating = START_RATING;
    for (let played = 0; played < games; played++) {
      // An established opponent, rated at their true level, within the matchmaking spread of the new player
      const opponent = rating + (random() * 2 - 1) * MATCHMAKING_SPREAD;
      const scores = playMatch(gameWinChance(truth - opponent), random);
      const [newPlayer] = calculateELOChange(
        [
          { userId: "new", team: 1, rating, gamesPlayed: played },
          { userId: "established", team: 2, rating: opponent, gamesPlayed: ESTABLISHED_GAMES_PLAYED },
        ],
        { sport: "TENNIS", format: "SINGLES", scores },
      );
      rating = newPlayer?.ratingAfter ?? Number.NaN;
    }
    errors.push(Math.abs(rating - truth));
    ratingSum += rating;
  }
  errors.sort((a, b) => a - b);
  return { errors, meanRating: ratingSum / PLAYERS };
}

const share = (errors: number[], limit: number) => errors.filter((error) => error <= limit).length / errors.length;
const mean = (values: number[]) => values.reduce((sum, value) => sum + value, 0) / values.length;

// True levels spread evenly over 1100-1900, within 400 of where every player starts
const spreadLevel = (_index: number, random: () => number) => 1100 + random() * 800;

describe("NFR: rating accuracy for new players", () => {
  // Input: 2000 simulated new players with true levels between 1100 and 1900, starting at 1500, each playing 5 rated
  // singles games against established opponents within 200 of their current rating
  // Expected behavior: most new players are placed with players of their level after 5 games
  // Expected output: at least 80% are rated within 200 (the singles matchmaking spread) of their true level
  test("Rates 80% of new players within 200 of their true level after 5 games", () => {
    const before = simulate(0, spreadLevel);
    const after = simulate(5, spreadLevel);
    const within = share(after.errors, MATCHMAKING_SPREAD);

    console.info(
      `Rating accuracy after 5 games: ${(within * 100).toFixed(1)}% within ${MATCHMAKING_SPREAD} ` +
        `(${(share(before.errors, MATCHMAKING_SPREAD) * 100).toFixed(1)}% at the start), ` +
        `mean error ${mean(after.errors).toFixed(0)} (${mean(before.errors).toFixed(0)} at the start)`,
    );
    expect(within).toBeGreaterThanOrEqual(0.8);
  });

  // Input: the same players after 0, 5 and 10 games
  // Expected behavior: ratings keep converging as players play more, rather than drifting
  // Expected output: the mean error after 5 games is at most 60% of the starting error, and lower again after 10
  test("Keeps reducing the rating error as new players play more", () => {
    const start = mean(simulate(0, spreadLevel).errors);
    const fiveGames = mean(simulate(5, spreadLevel).errors);
    const tenGames = mean(simulate(10, spreadLevel).errors);

    expect(fiveGames).toBeLessThanOrEqual(start * 0.6);
    expect(tenGames).toBeLessThan(fiveGames);
  });

  // Input: 2000 new players at each end of the range: true level 1100, and true level 1900
  // Expected behavior: ratings move toward the true level from the very start, both down and up
  // Expected output: after 5 games the average 1900 player is rated above 1700 and the average 1100 player below
  // 1300, each within 200 of their true level
  test.each([1100, 1900])("Moves the average new player of level %i within 200 of it in 5 games", (level) => {
    const { meanRating } = simulate(5, () => level);

    expect(Math.abs(meanRating - level)).toBeLessThanOrEqual(MATCHMAKING_SPREAD);
  });
});
