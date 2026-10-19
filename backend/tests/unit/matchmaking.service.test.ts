import { deleteRequests, getCandidates } from '../../src/matchmaking/matchmaking.repository';
import {
  findMatches,
  runMatchmaking,
  type Candidate,
} from '../../src/matchmaking/matchmaking.service';

jest.mock('../../src/matchmaking/matchmaking.repository', () => ({
  getCandidates: jest.fn(),
  deleteRequests: jest.fn(),
}));

function playerStub(userId: string, overrides: Partial<Candidate> = {}): Candidate {
  return { userId, sport: 'TENNIS', format: 'SINGLES', rating: 1500, createdAt: new Date(0), ...overrides };
}

// Interface findMatches(candidates)
describe('findMatches', () => {
  // Input: two singles tennis players with close ratings
  // Expected behavior: they are matched together
  // Expected output: one SINGLES TENNIS match with both users
  test('Matches two users with similar preferences', () => {
    const matches = findMatches([playerStub('a', { rating: 1500 }), playerStub('b', { rating: 1600 })]);

    expect(matches).toEqual([{ sport: 'TENNIS', format: 'SINGLES', userIds: ['a', 'b'] }]);
  });

  // Input: two singles players in different sports
  // Expected behavior: no match
  // Expected output: []
  test('Does not match different sports', () => {
    expect(findMatches([playerStub('a'), playerStub('b', { sport: 'BADMINTON' })])).toEqual([]);
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

    expect(matches).toEqual([{ sport: 'TENNIS', format: 'SINGLES', userIds: ['mid', 'high'] }]);
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

    expect(matches).toEqual([{ sport: 'TENNIS', format: 'SINGLES', userIds: ['oldest', 'middle'] }]);
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

    expect(matches).toEqual([{ sport: 'TENNIS', format: 'SINGLES', userIds: ['oldest', 'b'] }]);
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

    expect(matches).toEqual([{ sport: 'TENNIS', format: 'DOUBLES', userIds: ['oldest', 'b', 'a', 'c'] }]);
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

    expect(matches).toEqual([{ sport: 'TENNIS', format: 'SINGLES', userIds: ['a', 'b'] }]);
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

    expect(matches).toEqual([{ sport: 'TENNIS', format: 'DOUBLES', userIds: ['a', 'b', 'c', 'd'] }]);
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

    expect(matches).toEqual([{ sport: 'TENNIS', format: 'SINGLES', userIds: ['a', 'b'] }]);
  });
});

// Interface runMatchmaking()
describe('runMatchmaking', () => {
  beforeEach(() => {
    jest.mocked(deleteRequests).mockReset();
  });

  // Input: repository returns two compatible candidates and one left over
  // Expected behavior: candidates are matched and only the matched players leave the queue
  // Expected output: one match; deleteRequests called with the matched user ids
  test('Matches candidates and removes them from the queue', async () => {
    jest.mocked(getCandidates).mockResolvedValue([playerStub('a'), playerStub('b'), playerStub('c', { sport: 'BADMINTON' })]);

    await expect(runMatchmaking()).resolves.toEqual([
      { sport: 'TENNIS', format: 'SINGLES', userIds: ['a', 'b'] },
    ]);
    expect(deleteRequests).toHaveBeenCalledWith(['a', 'b']);
  });

  // Input: repository returns candidates that cannot be matched
  // Expected behavior: everyone stays in the queue
  // Expected output: []; deleteRequests not called
  test('Leaves the queue untouched when nobody is matched', async () => {
    jest.mocked(getCandidates).mockResolvedValue([playerStub('a'), playerStub('b', { sport: 'BADMINTON' })]);

    await expect(runMatchmaking()).resolves.toEqual([]);
    expect(deleteRequests).not.toHaveBeenCalled();
  });
});
