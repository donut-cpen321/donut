import { Format, Sport } from '../../src/generated/prisma/enums';
import { calculateELOChange } from '../../src/scoring/elo';
import { GameInfo, PlayerInfo } from '../../src/scoring/scoring.types';

// Function calculateELOChange
describe('Unit: calculateELOChange', () => {

  // Input: 1800-rated A vs 1500-rated B in tennis singles, A wins 6-3, 6-4
  // Expected behavior: the favorite wins as expected and gains a small amount
  // Expected output: A gains 9.33, B loses 9.33
  test('Singles, A rating > B rating, player A wins', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 1, rating: 1800 },
      { userId: 'player-b', team: 2, rating: 1500 },
    ];
    const game: GameInfo = {
      sport: Sport.TENNIS,
      format: Format.SINGLES,
      score: [{ team1Score: 6, team2Score: 3 }, { team1Score: 6, team2Score: 4 }],
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
      { userId: 'player-a', team: 1, rating: 1500 },
      { userId: 'player-b', team: 2, rating: 1800 },
    ];
    const game: GameInfo = {
      sport: Sport.TENNIS,
      format: Format.SINGLES,
      score: [{ team1Score: 6, team2Score: 4 }, { team1Score: 7, team2Score: 5 }],
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
      { userId: 'player-a', team: 1, rating: 2000 },
      { userId: 'player-b', team: 2, rating: 1400 },
    ];
    const game: GameInfo = {
      sport: Sport.TENNIS,
      format: Format.SINGLES,
      score: [{ team1Score: 7, team2Score: 6 }, { team1Score: 7, team2Score: 6 }],
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
      { userId: 'player-a', team: 1, rating: 1500 },
      { userId: 'player-b', team: 2, rating: 1500 },
    ];
    const game: GameInfo = {
      sport: Sport.PICKLEBALL,
      format: Format.SINGLES,
      score: [{ team1Score: 11, team2Score: 9 }, { team1Score: 11, team2Score: 7 }],
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
      { userId: 'player-a', team: 1, rating: 1500 },
      { userId: 'player-b', team: 2, rating: 1700 },
    ];
    const game: GameInfo = {
      sport: Sport.BADMINTON,
      format: Format.SINGLES,
      score: [
        { team1Score: 21, team2Score: 19 },
        { team1Score: 18, team2Score: 21 },
        { team1Score: 17, team2Score: 21 },
      ],
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
    { userId: 'player-a', team: 1, rating: 1500 },
    { userId: 'player-b', team: 2, rating: 1500 },
  ];

  // Input: the same matchup won 11-9, 11-9 and won 11-2, 11-2
  // Expected behavior: a bigger margin of victory earns more rating
  // Expected output: the blowout winner ends higher than the narrow winner
  test('Bigger winning margin gives a bigger rating gain', () => {
    const narrow = calculateELOChange(players, {
      sport: Sport.PICKLEBALL,
      format: Format.SINGLES,
      score: [{ team1Score: 11, team2Score: 9 }, { team1Score: 11, team2Score: 9 }],
    });
    const blowout = calculateELOChange(players, {
      sport: Sport.PICKLEBALL,
      format: Format.SINGLES,
      score: [{ team1Score: 11, team2Score: 2 }, { team1Score: 11, team2Score: 2 }],
    });

    expect(blowout[0]?.ratingAfter).toBeGreaterThan(narrow[0]?.ratingAfter ?? Infinity);
  });

  // Input: the same 11-9, 11-9 win by a 1500-rated player against a 1300 and a 1700
  // Expected behavior: beating a stronger opponent earns more rating
  // Expected output: the upset winner gains more than the favorite
  test('Beating a stronger opponent gives a bigger rating gain', () => {
    const score = [{ team1Score: 11, team2Score: 9 }, { team1Score: 11, team2Score: 9 }];
    const vsWeaker = calculateELOChange(
      [{ userId: 'player-a', team: 1, rating: 1500 }, { userId: 'player-b', team: 2, rating: 1300 }],
      { sport: Sport.PICKLEBALL, format: Format.SINGLES, score },
    );
    const vsStronger = calculateELOChange(
      [{ userId: 'player-a', team: 1, rating: 1500 }, { userId: 'player-b', team: 2, rating: 1700 }],
      { sport: Sport.PICKLEBALL, format: Format.SINGLES, score },
    );

    expect(vsStronger[0]?.ratingAfter).toBeGreaterThan(vsWeaker[0]?.ratingAfter ?? Infinity);
  });
});

describe('Unit: calculateELOChange — doubles', () => {

  // Input: team 1 (1700, 1600) vs team 2 (1500, 1400) in pickleball doubles, team 1 wins 11-6, 11-8
  // Expected behavior: team Δ = 18.72, each player receives half
  // Expected output: team 1 players gain 9.36, team 2 players lose 9.36
  test('Doubles, team 1 average > team 2 average, team 1 wins', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 1, rating: 1700 },
      { userId: 'player-a2', team: 1, rating: 1600 },
      { userId: 'player-b1', team: 2, rating: 1500 },
      { userId: 'player-b2', team: 2, rating: 1400 },
    ];
    const game: GameInfo = {
      sport: Sport.PICKLEBALL,
      format: Format.DOUBLES,
      score: [{ team1Score: 11, team2Score: 6 }, { team1Score: 11, team2Score: 8 }],
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

  // Input: team 1 (1600, 1500) vs team 2 (1400, 1300) in badminton doubles, team 2 wins 2 rounds to 1
  // Expected behavior: team Δ = -30.52, each player receives half
  // Expected output: team 1 players lose 15.26, team 2 players gain 15.26
  test('Doubles, team 1 average > team 2 average, team 2 wins (upset)', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 1, rating: 1600 },
      { userId: 'player-a2', team: 1, rating: 1500 },
      { userId: 'player-b1', team: 2, rating: 1400 },
      { userId: 'player-b2', team: 2, rating: 1300 },
    ];
    const game: GameInfo = {
      sport: Sport.BADMINTON,
      format: Format.DOUBLES,
      score: [
        { team1Score: 19, team2Score: 21 },
        { team1Score: 21, team2Score: 15 },
        { team1Score: 16, team2Score: 21 },
      ],
    };

    const [a1, a2, b1, b2] = calculateELOChange(players, game);

    expect(a1?.ratingAfter).toBeCloseTo(1600 - 15.2619289112, 5);
    expect(a2?.ratingAfter).toBeCloseTo(1500 - 15.2619289112, 5);
    expect(b1?.ratingAfter).toBeCloseTo(1400 + 15.2619289112, 5);
    expect(b2?.ratingAfter).toBeCloseTo(1300 + 15.2619289112, 5);
  });

  // Input: team 1 (1900, 1300) vs team 2 (1550, 1550) in tennis doubles, team 1 wins 2 rounds to 1
  // Expected behavior: team 1 average is 1600 vs 1550, team Δ = 24.81; teammates move by the same amount regardless of their own rating
  // Expected output: team 1 players gain 12.40, team 2 players lose 12.40
  test('Doubles, uneven teammates move by the same amount', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 1, rating: 1900 },
      { userId: 'player-a2', team: 1, rating: 1300 },
      { userId: 'player-b1', team: 2, rating: 1550 },
      { userId: 'player-b2', team: 2, rating: 1550 },
    ];
    const game: GameInfo = {
      sport: Sport.TENNIS,
      format: Format.DOUBLES,
      score: [
        { team1Score: 6, team2Score: 4 },
        { team1Score: 3, team2Score: 6 },
        { team1Score: 6, team2Score: 2 },
      ],
    };

    const [a1, a2, b1, b2] = calculateELOChange(players, game);

    expect(a1?.ratingAfter).toBeCloseTo(1900 + 12.4038335568, 5);
    expect(a2?.ratingAfter).toBeCloseTo(1300 + 12.4038335568, 5);
    expect(b1?.ratingAfter).toBeCloseTo(1550 - 12.4038335568, 5);
    expect(b2?.ratingAfter).toBeCloseTo(1550 - 12.4038335568, 5);
  });
});

describe('Unit: calculateELOChange — invalid players', () => {
  const validDoublesScore: GameInfo = {
    sport: Sport.PICKLEBALL,
    format: Format.DOUBLES,
    score: [{ team1Score: 11, team2Score: 5 }, { team1Score: 11, team2Score: 7 }],
  };
  const validSinglesScore: GameInfo = {
    sport: Sport.TENNIS,
    format: Format.SINGLES,
    score: [{ team1Score: 6, team2Score: 3 }, { team1Score: 6, team2Score: 4 }],
  };

  // Input: doubles players ordered team 1, team 2, team 1, team 2
  // Expected behavior: rejected because player B is not on team 1
  // Expected output: throws "must be on team 1"
  test('Doubles, players in wrong order (team 1 players not first) throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 1, rating: 1500 },
      { userId: 'player-b1', team: 2, rating: 1500 },
      { userId: 'player-a2', team: 1, rating: 1500 },
      { userId: 'player-b2', team: 2, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validDoublesScore)).toThrow('must be on team 1');
  });

  // Input: doubles players with team 2 listed first
  // Expected behavior: rejected because players A and B are not on team 1
  // Expected output: throws "must be on team 1"
  test('Doubles, team 2 listed before team 1 throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-b1', team: 2, rating: 1500 },
      { userId: 'player-b2', team: 2, rating: 1500 },
      { userId: 'player-a1', team: 1, rating: 1500 },
      { userId: 'player-a2', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validDoublesScore)).toThrow('must be on team 1');
  });

  // Input: three team 1 players and one team 2 player
  // Expected behavior: rejected because player C is not on team 2
  // Expected output: throws "must be on team 2"
  test('Doubles, 3 vs 1 grouping throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 1, rating: 1500 },
      { userId: 'player-a2', team: 1, rating: 1500 },
      { userId: 'player-a3', team: 1, rating: 1500 },
      { userId: 'player-b1', team: 2, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validDoublesScore)).toThrow('must be on team 2');
  });

  // Input: a doubles game with only 2 players
  // Expected behavior: rejected because doubles needs 4 players
  // Expected output: throws "exactly 4 players"
  test('Doubles with only 2 players throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 1, rating: 1500 },
      { userId: 'player-b', team: 2, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validDoublesScore)).toThrow('exactly 4 players');
  });

  // Input: singles players both on team 1
  // Expected behavior: rejected because player B is not on team 2
  // Expected output: throws "must be on team 1 and player B"
  test('Singles with both players on the same team throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a', team: 1, rating: 1500 },
      { userId: 'player-b', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validSinglesScore)).toThrow('must be on team 1 and player B');
  });

  // Input: singles players with team 2 listed first
  // Expected behavior: rejected because the score totals would be credited to the wrong player
  // Expected output: throws "must be on team 1 and player B"
  test('Singles with players swapped (team 2 first) throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-b', team: 2, rating: 1500 },
      { userId: 'player-a', team: 1, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validSinglesScore)).toThrow('must be on team 1 and player B');
  });

  // Input: a singles game with 4 players
  // Expected behavior: rejected because singles needs 2 players
  // Expected output: throws "exactly 2 players"
  test('Singles with 4 players throws', () => {
    const players: PlayerInfo[] = [
      { userId: 'player-a1', team: 1, rating: 1500 },
      { userId: 'player-a2', team: 1, rating: 1500 },
      { userId: 'player-b1', team: 2, rating: 1500 },
      { userId: 'player-b2', team: 2, rating: 1500 },
    ];

    expect(() => calculateELOChange(players, validSinglesScore)).toThrow('exactly 2 players');
  });
});

describe('Unit: calculateELOChange — invalid score', () => {
  const players: PlayerInfo[] = [
    { userId: 'player-a', team: 1, rating: 1500 },
    { userId: 'player-b', team: 2, rating: 1500 },
  ];

  // Input: a singles game with no rounds
  // Expected behavior: rejected because there is nothing to score
  // Expected output: throws "no score information"
  test('Empty score throws', () => {
    const game: GameInfo = { sport: Sport.TENNIS, format: Format.SINGLES, score: [] };

    expect(() => calculateELOChange(players, game)).toThrow('no score information');
  });

  // Input: a tennis game where the second round is 5-5
  // Expected behavior: rejected because rounds cannot tie
  // Expected output: throws "cannot be tied"
  test('A tied round throws', () => {
    const game: GameInfo = {
      sport: Sport.TENNIS,
      format: Format.SINGLES,
      score: [{ team1Score: 6, team2Score: 3 }, { team1Score: 5, team2Score: 5 }],
    };

    expect(() => calculateELOChange(players, game)).toThrow('cannot be tied');
  });

  // Input: a badminton game where each side won one round
  // Expected behavior: rejected because the match has no winner
  // Expected output: throws "no winner"
  test('Both sides winning the same number of rounds throws', () => {
    const game: GameInfo = {
      sport: Sport.BADMINTON,
      format: Format.SINGLES,
      score: [{ team1Score: 21, team2Score: 15 }, { team1Score: 18, team2Score: 21 }],
    };

    expect(() => calculateELOChange(players, game)).toThrow('no winner');
  });
});
