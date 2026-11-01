import {
  claimMatch,
  getCandidates,
  getMatchedPlayers,
  getRandomCourtId,
} from '../../src/matchmaking/matchmaking.repository';
import {
  assignTeams,
  chooseCourt,
  findMatches,
  Match,
  runMatchmaking,
  type Candidate,
  type CourtLocation,
  type MatchedPlayer,
} from '../../src/matchmaking/matchmaking.service';

jest.mock('../../src/matchmaking/matchmaking.repository', () => ({
  getCandidates: jest.fn(),
  getMatchedPlayers: jest.fn(),
  getRandomCourtId: jest.fn(),
  claimMatch: jest.fn(),
}));

function playerStub(userId: string, overrides: Partial<Candidate> = {}): Candidate {
  return { userId, format: 'SINGLES', rating: 1500, createdAt: new Date(0), ...overrides };
}

function courtStub(id: number, latitude = 0, longitude = 0): CourtLocation {
  return { id, latitude, longitude };
}

function matchedPlayerStub(id: string, rating = 1500, preferredCourts = [courtStub(1)]): MatchedPlayer {
  return { id, rating, preferredCourts };
}

// Interface findMatches(candidates)
describe('findMatches', () => {
  // Input: two singles tennis players with close ratings
  // Expected behavior: they are matched together
  // Expected output: one SINGLES TENNIS match with both users
  test('Matches two users with similar preferences', () => {
    const matches = findMatches([playerStub('a', { rating: 1500 }), playerStub('b', { rating: 1600 })]);

    expect(matches).toEqual([{ format: 'SINGLES', userIds: ['a', 'b'] }]);
  });

  // Input: a SINGLES player and a DOUBLES player
  // Expected behavior: no match
  // Expected output: []
  test('Does not match different formats', () => {
    expect(findMatches([playerStub('a'), playerStub('b', { format: 'DOUBLES' })])).toEqual([]);
  });

  // Input: two singles players whose ratings differ by more than 200
  // Expected behavior: no match
  // Expected output: []
  test('Does not match ratings too far apart', () => {
    expect(findMatches([playerStub('a', { rating: 1200 }), playerStub('b', { rating: 1401 })])).toEqual([]);
  });

  // Input: three players sorted 1000, 1450, 1500
  // Expected behavior: the closest pair is matched, the outlier is left over
  // Expected output: one match with the 1450 and 1500 players
  test('Skips an outlier to match the closest pair', () => {
    const matches = findMatches([
      playerStub('mid', { rating: 1450 }),
      playerStub('low', { rating: 1000 }),
      playerStub('high', { rating: 1500 }),
    ]);

    expect(matches).toEqual([{ format: 'SINGLES', userIds: ['mid', 'high'] }]);
  });

  // Input: three equally rated players who joined at different times
  // Expected behavior: the two who have waited longest are matched
  // Expected output: one match with the oldest and second-oldest requests
  test('Matches the longest-waiting players first', () => {
    const matches = findMatches([
      playerStub('newest', { createdAt: new Date(3) }),
      playerStub('oldest', { createdAt: new Date(1) }),
      playerStub('middle', { createdAt: new Date(2) }),
    ]);

    expect(matches).toEqual([{ format: 'SINGLES', userIds: ['oldest', 'middle'] }]);
  });

  // Input: the oldest request at 1700, then a closer-rated pair at 1500 and 1510
  // Expected behavior: the oldest player is not skipped; they take their closest-rated partner
  // Expected output: one match with the oldest player and the 1510 player
  test('Does not skip the oldest player for a closer pair', () => {
    const matches = findMatches([
      playerStub('oldest', { rating: 1700, createdAt: new Date(1) }),
      playerStub('a', { rating: 1500, createdAt: new Date(2) }),
      playerStub('b', { rating: 1510, createdAt: new Date(3) }),
    ]);

    expect(matches).toEqual([{ format: 'SINGLES', userIds: ['oldest', 'b'] }]);
  });

  // Input: four doubles players spread 1310 to 1690 (380)
  // Expected behavior: doubles allows a spread up to 400, wider than singles
  // Expected output: one DOUBLES match, partners ordered by rating distance to the oldest
  test('Matches a doubles group with a spread up to 400', () => {
    const matches = findMatches([
      playerStub('oldest', { format: 'DOUBLES', rating: 1500, createdAt: new Date(1) }),
      playerStub('a', { format: 'DOUBLES', rating: 1310, createdAt: new Date(2) }),
      playerStub('b', { format: 'DOUBLES', rating: 1320, createdAt: new Date(3) }),
      playerStub('c', { format: 'DOUBLES', rating: 1690, createdAt: new Date(4) }),
    ]);

    expect(matches).toEqual([{ format: 'DOUBLES', userIds: ['oldest', 'b', 'a', 'c'] }]);
  });

  // Input: four doubles players, each within 205 of the oldest, but spread 1295 to 1700 (405)
  // Expected behavior: the whole group's spread must be within 400, not just distance to the oldest
  // Expected output: []
  test('Does not match a doubles group whose spread exceeds 400', () => {
    const matches = findMatches([
      playerStub('oldest', { format: 'DOUBLES', rating: 1500, createdAt: new Date(1) }),
      playerStub('a', { format: 'DOUBLES', rating: 1295, createdAt: new Date(2) }),
      playerStub('b', { format: 'DOUBLES', rating: 1300, createdAt: new Date(3) }),
      playerStub('c', { format: 'DOUBLES', rating: 1700, createdAt: new Date(4) }),
    ]);

    expect(matches).toEqual([]);
  });

  // Input: one SINGLES player and one EITHER player
  // Expected behavior: EITHER fits singles
  // Expected output: one SINGLES match
  test('EITHER fits singles', () => {
    const matches = findMatches([playerStub('a'), playerStub('b', { format: 'EITHER' })]);

    expect(matches).toEqual([{ format: 'SINGLES', userIds: ['a', 'b'] }]);
  });

  // Input: four DOUBLES/EITHER players with close ratings
  // Expected behavior: they form one doubles match
  // Expected output: one DOUBLES match with all four users
  test('Matches four players for doubles', () => {
    const matches = findMatches([
      playerStub('a', { format: 'DOUBLES', rating: 1500 }),
      playerStub('b', { format: 'DOUBLES', rating: 1550 }),
      playerStub('c', { format: 'EITHER', rating: 1600 }),
      playerStub('d', { format: 'DOUBLES', rating: 1650 }),
    ]);

    expect(matches).toEqual([{ format: 'DOUBLES', userIds: ['a', 'b', 'c', 'd'] }]);
  });

  // Input: an EITHER player who fits a singles pair and a doubles group
  // Expected behavior: the EITHER player is used for singles and not reused for doubles
  // Expected output: one SINGLES match; the 3 remaining doubles players are left over
  test('Never matches a user twice', () => {
    const matches = findMatches([
      playerStub('a'),
      playerStub('b', { format: 'EITHER' }),
      playerStub('c', { format: 'DOUBLES' }),
      playerStub('d', { format: 'DOUBLES' }),
      playerStub('e', { format: 'DOUBLES' }),
    ]);

    expect(matches).toEqual([{ format: 'SINGLES', userIds: ['a', 'b'] }]);
  });
});

// Interface runMatchmaking()
describe('runMatchmaking', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  beforeEach(() => {
    jest.mocked(claimMatch).mockReset();
    jest.mocked(getRandomCourtId).mockReset().mockResolvedValue(7);
    jest.mocked(getMatchedPlayers).mockReset().mockImplementation((userIds) =>
      Promise.resolve(userIds.map((id) => matchedPlayerStub(id))),
    );
  });

  // Input: repository returns two compatible candidates and one left over; the claim succeeds
  // Expected behavior: the match is claimed with an open, matched game on the players' shared court, starting in one hour
  // Expected output: one match; claimMatch called with its players and the game
  test('Matches candidates and claims the match', async () => {
    const now = Date.UTC(2026, 0, 1);
    jest.spyOn(Date, 'now').mockReturnValue(now);
    jest.mocked(getCandidates).mockResolvedValue([playerStub('a'), playerStub('b'), playerStub('c', { rating: 2000 })]);
    jest.mocked(claimMatch).mockResolvedValue(true);

    const match = { format: 'SINGLES', userIds: ['a', 'b'] };
    await expect(runMatchmaking()).resolves.toEqual([match]);
    expect(claimMatch).toHaveBeenCalledWith(['a', 'b'], {
      format: 'SINGLES',
      status: 'OPEN',
      startsAt: new Date(now + 60 * 60 * 1000),
      court: { connect: { id: 1 } },
      gameSource: 'MATCHED',
      players: {
        create: [
          { team: 1, user: { connect: { id: 'a' } } },
          { team: 2, user: { connect: { id: 'b' } } },
        ],
      },
    });
    expect(getRandomCourtId).not.toHaveBeenCalled();
  });

  // Input: two compatible candidates, neither with a preferred court
  // Expected behavior: the game is proposed on a random court instead of aborting the run
  // Expected output: one match; claimMatch called with the random court
  test('Falls back to a random court when no player has a preferred court', async () => {
    jest.mocked(getCandidates).mockResolvedValue([playerStub('a'), playerStub('b')]);
    jest.mocked(getMatchedPlayers).mockImplementation((userIds) =>
      Promise.resolve(userIds.map((id) => matchedPlayerStub(id, 1500, []))),
    );
    jest.mocked(claimMatch).mockResolvedValue(true);

    await expect(runMatchmaking()).resolves.toEqual([{ format: 'SINGLES', userIds: ['a', 'b'] }]);
    expect(claimMatch).toHaveBeenCalledWith(['a', 'b'], expect.objectContaining({ court: { connect: { id: 7 } } }));
  });

  // Input: two matches found; the first claim fails because a player left the queue
  // Expected behavior: each match is claimed on its own; a failed claim does not block the others
  // Expected output: only the successfully claimed match
  test('Drops a match whose claim fails and keeps the others', async () => {
    jest.mocked(getCandidates).mockResolvedValue([
      playerStub('a', { createdAt: new Date(1) }),
      playerStub('b', { createdAt: new Date(2) }),
      playerStub('c', { rating: 2000, createdAt: new Date(3) }),
      playerStub('d', { rating: 2000, createdAt: new Date(4) }),
    ]);
    jest.mocked(claimMatch).mockResolvedValueOnce(false).mockResolvedValueOnce(true);

    await expect(runMatchmaking()).resolves.toEqual([
      { format: 'SINGLES', userIds: ['c', 'd'] },
    ]);
    expect(claimMatch).toHaveBeenCalledTimes(2);
  });

  // Input: repository returns candidates that cannot be matched
  // Expected behavior: everyone stays in the queue
  // Expected output: []; claimMatch not called
  test('Leaves the queue untouched when nobody is matched', async () => {
    jest.mocked(getCandidates).mockResolvedValue([playerStub('a'), playerStub('b', { rating: 2000 })]);

    await expect(runMatchmaking()).resolves.toEqual([]);
    expect(claimMatch).not.toHaveBeenCalled();
  });
});

// Interface chooseCourt(players)
describe('chooseCourt', () => {
  // Input: both players list court 2 among their preferred courts
  // Expected behavior: the shared court is picked
  // Expected output: 2
  test('Picks a preferred court shared by every player', () => {
    const players = [
      matchedPlayerStub('a', 1500, [courtStub(1), courtStub(2)]),
      matchedPlayerStub('b', 1500, [courtStub(2), courtStub(3)]),
    ];

    expect(chooseCourt(players)).toBe(2);
  });

  // Input: doubles; three of four players list court 3, nobody else shares a court
  // Expected behavior: the court preferred by the most players is picked
  // Expected output: 3
  test('Picks the most preferred court when no court is shared by everyone', () => {
    const players = [
      matchedPlayerStub('a', 1500, [courtStub(1), courtStub(3)]),
      matchedPlayerStub('b', 1500, [courtStub(3)]),
      matchedPlayerStub('c', 1500, [courtStub(2), courtStub(3)]),
      matchedPlayerStub('d', 1500, [courtStub(4)]),
    ];

    expect(chooseCourt(players)).toBe(3);
  });

  // Input: neither player has a preferred court
  // Expected behavior: no court can be chosen
  // Expected output: undefined
  test('Returns undefined when no player has a preferred court', () => {
    const players = [matchedPlayerStub('a', 1500, []), matchedPlayerStub('b', 1500, [])];

    expect(chooseCourt(players)).toBeUndefined();
  });
});

// Interface assignTeams(match, players)
describe('assignTeams', () => {
  // Input: a singles match
  // Expected behavior: the players are on opposite teams, higher rating first
  // Expected output: team 1 and team 2
  test('Puts singles players on opposite teams', () => {
    const match: Match = { format: 'SINGLES', userIds: ['a', 'b'] };
    const players = [matchedPlayerStub('a', 1400), matchedPlayerStub('b', 1600)];
    const teams = assignTeams(match, players);

    expect(teams).toEqual([{ userId: 'b', team: 1 }, { userId: 'a', team: 2 }]);
  });

  // Input: a doubles match with ratings 1000, 1200, 1400, 1600
  // Expected behavior: the best and worst players play against the middle two
  // Expected output: 1600 and 1000 on team 1, 1400 and 1200 on team 2
  test('Pairs the best and worst doubles players against the middle two', () => {
    const match: Match = { format: 'DOUBLES', userIds: ['a', 'b', 'c', 'd'] };
    const players = [matchedPlayerStub('a', 1000), matchedPlayerStub('b', 1200), matchedPlayerStub('c', 1400), matchedPlayerStub('d', 1600)];
    const teams = assignTeams(match, players);

    expect(teams).toEqual([
      { userId: 'd', team: 1 },
      { userId: 'c', team: 2 },
      { userId: 'b', team: 2 },
      { userId: 'a', team: 1 },
    ]);
  });
});
