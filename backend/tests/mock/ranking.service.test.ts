import { Format, Prisma } from "../../src/generated/prisma/client";
import {
  applyGameResult,
  IncompleteTeamsError,
} from "../../src/ranking/ranking.service";
import {
  fetchGamePlayerRatingsForUpdate,
  saveRatingChanges,
} from "../../src/ranking/ratings.repository";

jest.mock("../../src/ranking/ratings.repository", () => ({
  fetchGamePlayerRatingsForUpdate: jest.fn(),
  saveRatingChanges: jest.fn(),
}));

const mockedFetchPlayers = jest.mocked(fetchGamePlayerRatingsForUpdate);
const mockedSaveRatingChanges = jest.mocked(saveRatingChanges);

// The repository is mocked, so the transaction is only passed through
const tx = {} as Prisma.TransactionClient;
const gameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

// Function applyGameResult
describe("Mocked: applyGameResult", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: equal-rated singles players, team 1 wins 6-4 6-4
  // Expected behavior: the winner gains and the loser loses the same amount
  // Expected output: team 1 listed first, the changes are saved in the given transaction and returned
  test("Rates a singles game with teams 1 and 2 and saves the changes", async () => {
    mockedFetchPlayers.mockResolvedValue([
      { userId: "winner", team: 1, rating: 1500 },
      { userId: "loser", team: 2, rating: 1500 },
    ]);

    const changes = await applyGameResult(tx, gameId, Format.SINGLES, [
      [6, 4],
      [6, 4],
    ]);

    expect(changes).toEqual([
      { userId: "winner", ratingBefore: 1500, ratingAfter: expect.any(Number) },
      { userId: "loser", ratingBefore: 1500, ratingAfter: expect.any(Number) },
    ]);
    const [winner, loser] = changes;
    if (!winner || !loser) {
      throw new Error("Expected a rating change for both players");
    }
    expect(winner.ratingAfter).toBeGreaterThan(1500);
    expect(loser.ratingAfter).toBeCloseTo(3000 - winner.ratingAfter);
    expect(mockedFetchPlayers).toHaveBeenCalledWith(tx, gameId);
    expect(mockedSaveRatingChanges).toHaveBeenCalledWith(tx, gameId, changes);
  });

  // Input: a doubles game with two players on each team, team 2 wins
  // Expected behavior: team 2 players gain, team 1 players lose
  // Expected output: four changes saved
  test("Rates a doubles game", async () => {
    mockedFetchPlayers.mockResolvedValue([
      { userId: "a1", team: 1, rating: 1600 },
      { userId: "a2", team: 1, rating: 1400 },
      { userId: "b1", team: 2, rating: 1500 },
      { userId: "b2", team: 2, rating: 1500 },
    ]);

    const changes = await applyGameResult(tx, gameId, Format.DOUBLES, [
      [3, 6],
      [4, 6],
    ]);

    expect(changes).toHaveLength(4);
    for (const change of changes) {
      const gained = change.ratingAfter > change.ratingBefore;
      expect(gained).toBe(change.userId.startsWith("b"));
    }
    expect(mockedSaveRatingChanges).toHaveBeenCalledTimes(1);
  });

  // Input: games whose teams are missing players or have a player on an unknown team
  // Expected behavior: the game can't be rated
  // Expected output: IncompleteTeamsError, nothing saved
  test.each([
    ["singles with only one player", Format.SINGLES, [{ userId: "a", team: 1, rating: 1500 }]],
    [
      "singles with both players on team 1",
      Format.SINGLES,
      [
        { userId: "a", team: 1, rating: 1500 },
        { userId: "b", team: 1, rating: 1500 },
      ],
    ],
    [
      "doubles with three players",
      Format.DOUBLES,
      [
        { userId: "a", team: 1, rating: 1500 },
        { userId: "b", team: 1, rating: 1500 },
        { userId: "c", team: 2, rating: 1500 },
      ],
    ],
    [
      "a player on team 0",
      Format.SINGLES,
      [
        { userId: "a", team: 0, rating: 1500 },
        { userId: "b", team: 1, rating: 1500 },
        { userId: "c", team: 2, rating: 1500 },
      ],
    ],
  ])("Throws IncompleteTeamsError for %s", async (_description, format, players) => {
    mockedFetchPlayers.mockResolvedValue(players);

    await expect(
      applyGameResult(tx, gameId, format, [[6, 4]]),
    ).rejects.toBeInstanceOf(IncompleteTeamsError);
    expect(mockedSaveRatingChanges).not.toHaveBeenCalled();
  });
});
