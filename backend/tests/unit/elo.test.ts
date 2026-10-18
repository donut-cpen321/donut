import { Format, Sport } from '../../src/generated/prisma/enums';
import { calculateELOChange } from '../../src/scoring/elo';
import { GameInfo, PlayerInfo, RoundScore } from '../../src/scoring/scoring.types';

// Function calculateELOChange
describe('Unit: calculateELOChange', () => {

  // Input: 1800-rated A vs 1500-rated B in tennis singles, A wins 6-3, 6-4
  // Expected behavior: the favorite wins as expected and gains a small amount
  // Expected output: A gains 9.33, B loses 9.33
  test('Singles, A rating > B rating, player A wins', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 0, rating: 1800 },
      { userId: 'player-b', team: 1, rating: 1500 },
    ];
    const game: GameInfo = {
      sport: Sport.TENNIS,
      format: Format.SINGLES,
      scores: [[6, 3], [6, 4]],
    };

    const [changeA, changeB] = calculateELOChange(players, game);

    expect(changeA?.userId).toBe('player-a');
    expect(changeA?.ratingBefore).toBe(1800);
    expect(changeA?.ratingAfter).toBeCloseTo(1800 + 9.3326372886, 5);

    expect(changeB?.userId).toBe('player-b');
    expect(changeB?.ratingBefore).toBe(1500);
    expect(changeB?.ratingAfter).toBeCloseTo(1500 - 9.3326372886, 5);
  });

  // Input: 1500-rated A vs 1800-rated B in tennis singles, A wins 6-4, 7-5
  // Expected behavior: the underdog wins and gains a large amount
  // Expected output: A gains 54.17, B loses 54.17
  test('Singles, A rating < B rating, player A wins (upset)', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 0, rating: 1500 },
      { userId: 'player-b', team: 1, rating: 1800 },
    ];
    const game: GameInfo = {
      sport: Sport.TENNIS,
      format: Format.SINGLES,
      scores: [[6, 4], [7, 5]],
    };

    const [changeA, changeB] = calculateELOChange(players, game);

    expect(changeA?.userId).toBe('player-a');
    expect(changeA?.ratingBefore).toBe(1500);
    expect(changeA?.ratingAfter).toBeCloseTo(1500 + 54.1673627109, 5);

    expect(changeB?.userId).toBe('player-b');
    expect(changeB?.ratingBefore).toBe(1800);
    expect(changeB?.ratingAfter).toBeCloseTo(1800 - 54.1673627109, 5);
  });

  // Input: 2000-rated A vs 1400-rated B in tennis singles, A wins 7-6, 7-6
  // Expected behavior: the heavy favorite won by less than expected, so the margin term outweighs the win
  // Expected output: A loses 3.61, B gains 3.61
  test('Singles, A rating >> B rating, A wins narrowly and loses rating', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 0, rating: 2000 },
      { userId: 'player-b', team: 1, rating: 1400 },
    ];
    const game: GameInfo = {
      sport: Sport.TENNIS,
      format: Format.SINGLES,
      scores: [[7, 6], [7, 6]],
    };

    const [changeA, changeB] = calculateELOChange(players, game);

    expect(changeA?.ratingAfter).toBeLessThan(2000);
    expect(changeA?.ratingAfter).toBeCloseTo(2000 - 3.6136627812, 5);
    expect(changeB?.ratingAfter).toBeCloseTo(1400 + 3.6136627812, 5);
  });

  // Input: two 1500-rated players in pickleball singles, A wins 11-9, 11-7
  // Expected behavior: E_A = 0.5 and E_MOV = 0, so Δ = 60 * 0.5 + 1.0 * 6 = 36
  // Expected output: A = 1536, B = 1464
  test('Singles, equal ratings, player A wins', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 0, rating: 1500 },
      { userId: 'player-b', team: 1, rating: 1500 },
    ];
    const game: GameInfo = {
      sport: Sport.PICKLEBALL,
      format: Format.SINGLES,
      scores: [[11, 9], [11, 7]],
    };

    const [changeA, changeB] = calculateELOChange(players, game);

    expect(changeA?.ratingAfter).toBeCloseTo(1536, 5);
    expect(changeB?.ratingAfter).toBeCloseTo(1464, 5);
  });

  // Input: 1500-rated A vs 1700-rated B in badminton singles, B wins 2 rounds to 1
  // Expected behavior: the favorite wins as expected and gains a small amount
  // Expected output: A loses 4.48, B gains 4.48
  test('Singles, A rating < B rating, player B wins in three rounds', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 0, rating: 1500 },
      { userId: 'player-b', team: 1, rating: 1700 },
    ];
    const game: GameInfo = {
      sport: Sport.BADMINTON,
      format: Format.SINGLES,
      scores: [[21, 19], [18, 21], [17, 21]],
    };

    const [changeA, changeB] = calculateELOChange(players, game);

    expect(changeA?.userId).toBe('player-a');
    expect(changeA?.ratingAfter).toBeCloseTo(1500 - 4.4761421776, 5);

    expect(changeB?.userId).toBe('player-b');
    expect(changeB?.ratingAfter).toBeCloseTo(1700 + 4.4761421776, 5);
  });
});

describe('Unit: calculateELOChange — behavior', () => {
  const players: PlayerInfo[] = [
    { userId: 'player-a', team: 0, rating: 1500 },
    { userId: 'player-b', team: 1, rating: 1500 },
  ];

  // Input: the same matchup won 11-9, 11-9 and won 11-2, 11-2
  // Expected behavior: a bigger margin of victory earns more rating
  // Expected output: the blowout winner ends higher than the narrow winner
  test('Bigger winning margin gives a bigger rating gain', () => {
    const narrow = calculateELOChange(players, {
      sport: Sport.PICKLEBALL,
      format: Format.SINGLES,
      scores: [[11, 9], [11, 9]],
    });
    const blowout = calculateELOChange(players, {
      sport: Sport.PICKLEBALL,
      format: Format.SINGLES,
      scores: [[11, 2], [11, 2]],
    });

    expect(blowout[0]?.ratingAfter).toBeGreaterThan(narrow[0]?.ratingAfter ?? Infinity);
  });

  // Input: the same 11-9, 11-9 win by a 1500-rated player against a 1300 and a 1700
  // Expected behavior: beating a stronger opponent earns more rating
  // Expected output: the upset winner gains more than the favorite
  test('Beating a stronger opponent gives a bigger rating gain', () => {
    const game: GameInfo = { sport: Sport.PICKLEBALL, format: Format.SINGLES, scores: [[11, 9], [11, 9]] };
    const vsWeaker = calculateELOChange(
      [{ userId: 'player-a', team: 0, rating: 1500 }, { userId: 'player-b', team: 1, rating: 1300 }],
      game,
    );
    const vsStronger = calculateELOChange(
      [{ userId: 'player-a', team: 0, rating: 1500 }, { userId: 'player-b', team: 1, rating: 1700 }],
      game,
    );

    expect(vsStronger[0]?.ratingAfter).toBeGreaterThan(vsWeaker[0]?.ratingAfter ?? Infinity);
  });

  // Input: the same singles game with the team 1 player listed first
  // Expected behavior: player order does not affect the result; output lists team 0 first
  // Expected output: same rating changes as with team 0 listed first
  test('Singles, player order does not matter', () => {
    const game: GameInfo = { sport: Sport.TENNIS, format: Format.SINGLES, scores: [[6, 3], [6, 4]] };
    const playerA: PlayerInfo = { userId: 'player-a', team: 0, rating: 1800 };
    const playerB: PlayerInfo = { userId: 'player-b', team: 1, rating: 1500 };

    const inOrder = calculateELOChange([playerA, playerB], game);
    const swapped = calculateELOChange([playerB, playerA], game);

    expect(swapped).toEqual(inOrder);
  });

  // Input: the same doubles game with players listed as A B A B, B A B A, B B A A and A B B A
  // Expected behavior: player order does not affect the result
  // Expected output: each player gets the same rating change in every order
  test('Doubles, player order does not matter', () => {
    const game: GameInfo = { sport: Sport.PICKLEBALL, format: Format.DOUBLES, scores: [[11, 6], [11, 8]] };
    const a1: PlayerInfo = { userId: 'player-a1', team: 0, rating: 1700 };
    const a2: PlayerInfo = { userId: 'player-a2', team: 0, rating: 1600 };
    const b1: PlayerInfo = { userId: 'player-b1', team: 1, rating: 1500 };
    const b2: PlayerInfo = { userId: 'player-b2', team: 1, rating: 1400 };

    const inOrder = calculateELOChange([a1, a2, b1, b2], game);
    const byUserId = (changes: typeof inOrder) =>
      [...changes].sort((x, y) => x.userId.localeCompare(y.userId));

    const shuffledOrders = [
      [a1, b1, a2, b2],   // A B A B
      [b2, a1, b1, a2],   // B A B A
      [b1, b2, a1, a2],   // B B A A
      [a1, b1, b2, a2],   // A B B A
    ];
    for (const order of shuffledOrders) {
      expect(byUserId(calculateELOChange(order, game))).toEqual(byUserId(inOrder));
    }
  });
});

describe('Unit: calculateELOChange — doubles', () => {

  // Input: team 0 (1700, 1600) vs team 1 (1500, 1400) in pickleball doubles, team 0 wins 11-6, 11-8
  // Expected behavior: team Δ = 18.72, each player receives half
  // Expected output: team 0 players gain 9.36, team 1 players lose 9.36
  test('Doubles, team 0 average > team 1 average, team 0 wins', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 0, rating: 1700 },
      { userId: 'player-a2', team: 0, rating: 1600 },
      { userId: 'player-b1', team: 1, rating: 1500 },
      { userId: 'player-b2', team: 1, rating: 1400 },
    ];
    const game: GameInfo = {
      sport: Sport.PICKLEBALL,
      format: Format.DOUBLES,
      scores: [[11, 6], [11, 8]],
    };

    const [a1, a2, b1, b2] = calculateELOChange(players, game);

    expect(a1?.userId).toBe('player-a1');
    expect(a1?.ratingAfter).toBeCloseTo(1700 + 9.3591235715, 5);
    expect(a2?.userId).toBe('player-a2');
    expect(a2?.ratingAfter).toBeCloseTo(1600 + 9.3591235715, 5);
    expect(b1?.userId).toBe('player-b1');
    expect(b1?.ratingAfter).toBeCloseTo(1500 - 9.3591235715, 5);
    expect(b2?.userId).toBe('player-b2');
    expect(b2?.ratingAfter).toBeCloseTo(1400 - 9.3591235715, 5);
  });

  // Input: team 0 (1600, 1500) vs team 1 (1400, 1300) in badminton doubles, team 1 wins 2 rounds to 1
  // Expected behavior: team Δ = -30.52, each player receives half
  // Expected output: team 0 players lose 15.26, team 1 players gain 15.26
  test('Doubles, team 0 average > team 1 average, team 1 wins (upset)', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 0, rating: 1600 },
      { userId: 'player-a2', team: 0, rating: 1500 },
      { userId: 'player-b1', team: 1, rating: 1400 },
      { userId: 'player-b2', team: 1, rating: 1300 },
    ];
    const game: GameInfo = {
      sport: Sport.BADMINTON,
      format: Format.DOUBLES,
      scores: [[19, 21], [21, 15], [16, 21]],
    };

    const [a1, a2, b1, b2] = calculateELOChange(players, game);

    expect(a1?.ratingAfter).toBeCloseTo(1600 - 15.2619289112, 5);
    expect(a2?.ratingAfter).toBeCloseTo(1500 - 15.2619289112, 5);
    expect(b1?.ratingAfter).toBeCloseTo(1400 + 15.2619289112, 5);
    expect(b2?.ratingAfter).toBeCloseTo(1300 + 15.2619289112, 5);
  });

  // Input: team 0 (1900, 1300) vs team 1 (1550, 1550) in tennis doubles, team 0 wins 2 rounds to 1
  // Expected behavior: team 0 average is 1600 vs 1550, team Δ = 24.81; teammates move by the same amount regardless of their own rating
  // Expected output: team 0 players gain 12.40, team 1 players lose 12.40
  test('Doubles, uneven teammates move by the same amount', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 0, rating: 1900 },
      { userId: 'player-a2', team: 0, rating: 1300 },
      { userId: 'player-b1', team: 1, rating: 1550 },
      { userId: 'player-b2', team: 1, rating: 1550 },
    ];
    const game: GameInfo = {
      sport: Sport.TENNIS,
      format: Format.DOUBLES,
      scores: [[6, 4], [3, 6], [6, 2]],
    };

    const [a1, a2, b1, b2] = calculateELOChange(players, game);

    expect(a1?.ratingAfter).toBeCloseTo(1900 + 12.4038335568, 5);
    expect(a2?.ratingAfter).toBeCloseTo(1300 + 12.4038335568, 5);
    expect(b1?.ratingAfter).toBeCloseTo(1550 - 12.4038335568, 5);
    expect(b2?.ratingAfter).toBeCloseTo(1550 - 12.4038335568, 5);
  });
});

describe('Unit: calculateELOChange — invalid players', () => {
  const validDoublesGame: GameInfo = {
    sport: Sport.PICKLEBALL,
    format: Format.DOUBLES,
    scores: [[11, 5], [11, 7]],
  };
  const validSinglesGame: GameInfo = {
    sport: Sport.TENNIS,
    format: Format.SINGLES,
    scores: [[6, 3], [6, 4]],
  };

  // Input: three team 0 players and one team 1 player
  // Expected behavior: rejected because each team needs two players
  // Expected output: throws "two players on each team"
  test('Doubles, 3 vs 1 grouping throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 0, rating: 1500 },
      { userId: 'player-a2', team: 0, rating: 1500 },
      { userId: 'player-a3', team: 0, rating: 1500 },
      { userId: 'player-b1', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validDoublesGame)).toThrow('two players on each team');
  });

  // Input: a doubles game with only 2 players
  // Expected behavior: rejected because doubles needs 4 players
  // Expected output: throws "exactly 4 players"
  test('Doubles with only 2 players throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 0, rating: 1500 },
      { userId: 'player-b', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validDoublesGame)).toThrow('exactly 4 players');
  });

  // Input: a doubles game with 5 players (3 on team 0)
  // Expected behavior: rejected instead of silently ignoring the extra player
  // Expected output: throws "exactly 4 players"
  test('Doubles with 5 players throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 0, rating: 1500 },
      { userId: 'player-a2', team: 0, rating: 1500 },
      { userId: 'player-a3', team: 0, rating: 1500 },
      { userId: 'player-b1', team: 1, rating: 1500 },
      { userId: 'player-b2', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validDoublesGame)).toThrow('exactly 4 players');
  });

  // Input: singles players both on team 0
  // Expected behavior: rejected because team 1 has no player
  // Expected output: throws "one player on each team"
  test('Singles with both players on the same team throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 0, rating: 1500 },
      { userId: 'player-b', team: 0, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validSinglesGame)).toThrow('one player on each team');
  });

  // Input: a singles game where the same user is listed on both teams
  // Expected behavior: rejected because one user would get two opposite rating changes
  // Expected output: throws "unique userId"
  test('Same user on both teams throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 0, rating: 1500 },
      { userId: 'player-a', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validSinglesGame)).toThrow('unique userId');
  });

  // Input: a singles game where one player's rating is NaN, Infinity or -Infinity
  // Expected behavior: rejected instead of producing NaN or infinite ratings
  // Expected output: throws "not finite" for each
  test.each([NaN, Infinity, -Infinity])('Singles with a rating of %p throws', (badRating) => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 0, rating: badRating },
      { userId: 'player-b', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validSinglesGame)).toThrow('not finite');
  });

  // Input: a doubles game where one player's rating is NaN, Infinity or -Infinity
  // Expected behavior: rejected instead of turning the team average and every rating into NaN or infinity
  // Expected output: throws "not finite" for each
  test.each([NaN, Infinity, -Infinity])('Doubles with a rating of %p throws', (badRating) => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 0, rating: 1500 },
      { userId: 'player-a2', team: 0, rating: badRating },
      { userId: 'player-b1', team: 1, rating: 1500 },
      { userId: 'player-b2', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validDoublesGame)).toThrow('not finite');
  });

  // Input: a singles game with 4 players
  // Expected behavior: rejected because singles needs 2 players
  // Expected output: throws "exactly 2 players"
  test('Singles with 4 players throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 0, rating: 1500 },
      { userId: 'player-a2', team: 0, rating: 1500 },
      { userId: 'player-b1', team: 1, rating: 1500 },
      { userId: 'player-b2', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validSinglesGame)).toThrow('exactly 2 players');
  });
});

describe('Unit: calculateELOChange — invalid scores', () => {
  const players: PlayerInfo[] = [
    { userId: 'player-a', team: 0, rating: 1500 },
    { userId: 'player-b', team: 1, rating: 1500 },
  ];

  // Input: a singles game with a sport that isn't in the enum (e.g. bad data from a request)
  // Expected behavior: rejected instead of looking up parameters that don't exist
  // Expected output: throws "Unsupported sport"
  test.each(['GOLF', '__proto__'])('Unsupported sport %p throws', (badSport) => {
    const game: GameInfo = {
      sport: badSport as Sport,   // bypasses the type check to simulate bad runtime data
      format: Format.SINGLES,
      scores: [[6, 3], [6, 4]],
    };

    expect(() => calculateELOChange(players, game)).toThrow('Unsupported sport');
  });

  // Input: a singles game with no rounds
  // Expected behavior: rejected because there is nothing to score
  // Expected output: throws "no round score information"
  test('Empty scores throws', () => {
    const game: GameInfo = { sport: Sport.TENNIS, format: Format.SINGLES, scores: [] };

    expect(() => calculateELOChange(players, game)).toThrow('no round score information');
  });

  // Input: a tennis game where the second round is 5-5
  // Expected behavior: rejected because rounds cannot tie
  // Expected output: throws "cannot be tied"
  test('A tied round throws', () => {
    const game: GameInfo = { sport: Sport.TENNIS, format: Format.SINGLES, scores: [[6, 3], [5, 5]] };

    expect(() => calculateELOChange(players, game)).toThrow('cannot be tied');
  });

  // Input: a badminton game where each side won one round
  // Expected behavior: rejected because the match has no winner
  // Expected output: throws "no winner"
  test('Both sides winning the same number of rounds throws', () => {
    const game: GameInfo = { sport: Sport.BADMINTON, format: Format.SINGLES, scores: [[21, 15], [18, 21]] };

    expect(() => calculateELOChange(players, game)).toThrow('no winner');
  });

  // Input: rounds containing NaN, a negative score, and a decimal score
  // Expected behavior: rejected instead of producing NaN or meaningless ratings
  // Expected output: throws "non-negative integer" for each
  const invalidRounds: [string, RoundScore[]][] = [
    ['NaN', [[6, 3], [NaN, 4]]],
    ['negative', [[6, 3], [-1, 4]]],
    ['decimal', [[6, 3], [6.5, 4]]],
  ];
  test.each(invalidRounds)('Round with a %s score throws', (_label, scores) => {
    const game: GameInfo = { sport: Sport.TENNIS, format: Format.SINGLES, scores };

    expect(() => calculateELOChange(players, game)).toThrow('non-negative integer');
  });
});
