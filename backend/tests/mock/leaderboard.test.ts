import request from "supertest";
import { createApp } from "../../src/app";
import { signAccessToken } from "../../src/auth/tokens";
import {
  fetchLeaderboard,
  fetchLeaderboardUser,
} from "../../src/ranking/ratings.repository";

// /leaderboard is behind requireAuth, so requests need a valid token
process.env.JWT_SECRET = "test-jwt-secret";
const userId = "0b6d3a52-6a8e-4c1f-9d2e-1f3a4b5c6d7e";
const authHeader = `Bearer ${signAccessToken(userId)}`;

jest.mock("../../src/ranking/ratings.repository", () => ({
  fetchLeaderboard: jest.fn(),
  fetchLeaderboardUser: jest.fn(),
  fetchGamePlayerRatingsForUpdate: jest.fn(),
  saveRatingChanges: jest.fn(),
}));

const mockedFetchLeaderboard = jest.mocked(fetchLeaderboard);
const mockedFetchLeaderboardUser = jest.mocked(fetchLeaderboardUser);

const player = (id: string, username: string, rating: number) => ({
  id,
  username,
  name: username,
  rating,
});

describe("Mocked: GET /leaderboard", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: three players already in leaderboard order, the caller is 7th overall
  // Expected behavior: ranks follow the repository order, ratings keep their decimals
  // Expected output: 200 with ranked entries and the caller's own entry
  test("Returns the ranked top players and the caller's rank", async () => {
    mockedFetchLeaderboard.mockResolvedValue([
      player("u1", "alice", 1612.4),
      player("u2", "bob", 1612.1),
      player("u3", "carol", 1500),
    ]);
    const me = player(userId, "dave", 1490.75);
    mockedFetchLeaderboardUser.mockResolvedValue({ user: me, rank: 7 });

    const response = await request(createApp())
      .get("/leaderboard")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      leaderboard: [
        { ...player("u1", "alice", 1612.4), rank: 1 },
        { ...player("u2", "bob", 1612.1), rank: 2 },
        { ...player("u3", "carol", 1500), rank: 3 },
      ],
      me: { ...me, rank: 7 },
    });
    expect(mockedFetchLeaderboard).toHaveBeenCalledWith(50);
    expect(mockedFetchLeaderboardUser).toHaveBeenCalledWith(userId);
  });

  // Input: limit=10
  // Expected behavior: the limit is passed to the repository
  // Expected output: 200
  test("Uses the given limit", async () => {
    mockedFetchLeaderboard.mockResolvedValue([]);
    mockedFetchLeaderboardUser.mockResolvedValue(null);

    const response = await request(createApp())
      .get("/leaderboard")
      .query({ limit: 10 })
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ leaderboard: [], me: null });
    expect(mockedFetchLeaderboard).toHaveBeenCalledWith(10);
  });

  // Input: invalid limit values or unknown query parameters
  // Expected behavior: rejected before reaching the repository
  // Expected output: 400
  test.each([
    ["limit=0", { limit: 0 }],
    ["limit above the maximum", { limit: 101 }],
    ["a non-integer limit", { limit: 2.5 }],
    ["a non-numeric limit", { limit: "ten" }],
    ["an unknown parameter", { sport: "TENNIS" }],
  ])("Rejects %s", async (_description, query) => {
    const response = await request(createApp())
      .get("/leaderboard")
      .query(query)
      .set("Authorization", authHeader);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request query");
    expect(mockedFetchLeaderboard).not.toHaveBeenCalled();
  });

  // Input: no Authorization header
  // Expected behavior: requireAuth rejects the request
  // Expected output: 401
  test("Rejects a request without a token", async () => {
    const response = await request(createApp()).get("/leaderboard");

    expect(response.status).toBe(401);
    expect(mockedFetchLeaderboard).not.toHaveBeenCalled();
  });

  // Input: the repository throws
  // Expected behavior: the error is logged
  // Expected output: 500
  test("Returns 500 when the database fails", async () => {
    const consoleError = jest.spyOn(console, "error").mockImplementation();
    mockedFetchLeaderboard.mockRejectedValue(new Error("database down"));
    mockedFetchLeaderboardUser.mockResolvedValue(null);

    const response = await request(createApp())
      .get("/leaderboard")
      .set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});
