import request from "supertest";
import { createApp } from "../../src/app";
import { Prisma } from "../../src/generated/prisma/client";
import { Format, FriendshipStatus } from "../../src/generated/prisma/enums";
import {
  acceptFriendRequest,
  createFriendRequest,
  deleteFriendship,
  fetchFriendRequests,
  fetchFriends,
  fetchFriendship,
  fetchGamesInProgress,
} from "../../src/friends/friends.repository";
import { signAccessToken } from "../../src/auth/tokens";
import { authErrors } from "../../src/auth/requireAuth";
import { commonErrors, errorBody } from "../../src/errors";
import { friendErrors } from "../../src/friends/friends.service";

// Every /friends route is behind requireAuth, so requests need a valid token
process.env.JWT_SECRET = "test-jwt-secret";
const userId = "0b6d3a52-6a8e-4c1f-9d2e-1f3a4b5c6d7e";
const otherUserId = "9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4";
const authHeader = `Bearer ${signAccessToken(userId)}`;

jest.mock("../../src/friends/friends.repository", () => ({
  fetchFriendship: jest.fn(),
  createFriendRequest: jest.fn(),
  acceptFriendRequest: jest.fn(),
  deleteFriendship: jest.fn(),
  fetchFriends: jest.fn(),
  fetchFriendRequests: jest.fn(),
  fetchGamesInProgress: jest.fn(() => Promise.resolve([])),
}));

const mockedFetchFriendship = jest.mocked(fetchFriendship);
const mockedCreateFriendRequest = jest.mocked(createFriendRequest);
const mockedAcceptFriendRequest = jest.mocked(acceptFriendRequest);
const mockedDeleteFriendship = jest.mocked(deleteFriendship);
const mockedFetchFriends = jest.mocked(fetchFriends);
const mockedFetchFriendRequests = jest.mocked(fetchFriendRequests);
const mockedFetchGamesInProgress = jest.mocked(fetchGamesInProgress);

const friendship = (
  requesterId: string,
  addresseeId: string,
  status: FriendshipStatus,
) => ({
  requesterId,
  addresseeId,
  status,
  createdAt: new Date(),
  acceptedAt: status === FriendshipStatus.ACCEPTED ? new Date() : null,
});

const prismaError = (code: string) =>
  new Prisma.PrismaClientKnownRequestError("Prisma error", {
    code,
    clientVersion: Prisma.prismaVersion.client,
  });

const silenceConsoleError = () =>
  jest.spyOn(console, "error").mockImplementation(() => undefined);

// Interface GET /friends, DELETE /friends, GET /friends/requests, POST /friends/requests, POST /friends/requests/accept
describe("Mocked: /friends requireAuth", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const routes = () => [
    ["GET /friends", () => request(createApp()).get("/friends")],
    ["DELETE /friends", () => request(createApp()).delete("/friends").query({ userId: otherUserId })],
    ["GET /friends/requests", () => request(createApp()).get("/friends/requests")],
    ["POST /friends/requests", () => request(createApp()).post("/friends/requests").send({ userId: otherUserId })],
    ["POST /friends/requests/accept", () => request(createApp()).post("/friends/requests/accept").send({ userId: otherUserId })],
  ] as const;

  const expectNoRepositoryCalls = () => {
    expect(mockedFetchFriendship).not.toHaveBeenCalled();
    expect(mockedCreateFriendRequest).not.toHaveBeenCalled();
    expect(mockedAcceptFriendRequest).not.toHaveBeenCalled();
    expect(mockedDeleteFriendship).not.toHaveBeenCalled();
    expect(mockedFetchFriends).not.toHaveBeenCalled();
    expect(mockedFetchFriendRequests).not.toHaveBeenCalled();
  };

  // Input: request to each route with no Authorization header
  // Expected status code: 401
  // Expected behavior: the database is never touched
  // Expected output: { error: "Missing auth token", code: "MISSING_AUTH_TOKEN" }
  test.each(routes())("Rejects %s without an Authorization header", async (_route, send) => {
    const response = await send();

    expect(response.status).toBe(401);
    expect(response.body).toEqual(errorBody(authErrors.MissingToken));
    expectNoRepositoryCalls();
  });

  // Input: request to each route with a malformed bearer token
  // Expected status code: 401
  // Expected behavior: the database is never touched
  // Expected output: { error: "Invalid or expired auth token", code: "INVALID_AUTH_TOKEN" }
  test.each(routes())("Rejects %s with an invalid token", async (_route, send) => {
    const response = await send().set("Authorization", "Bearer not-a-jwt");

    expect(response.status).toBe(401);
    expect(response.body).toEqual(errorBody(authErrors.InvalidToken));
    expectNoRepositoryCalls();
  });
});

// Interface GET /friends
describe("Mocked: GET /friends", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // clearAllMocks keeps return values, so a game in progress set by another test would show up here
    mockedFetchGamesInProgress.mockResolvedValue([]);
  });

  // Input: authenticated caller with one accepted friend
  // Expected status code: 200
  // Expected output: { friends: [{ id, username, name, rating, friendsSince }] } with friendsSince as an ISO string
  test("Returns the caller's friends", async () => {
    const friendsSince = new Date("2026-10-01T12:00:00.000Z");
    mockedFetchFriends.mockResolvedValue([
      { id: otherUserId, username: "rally", name: "Rally Partner", photoUrl: null, rating: 1520, friendsSince },
    ]);

    const response = await request(createApp()).get("/friends").set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      friends: [
        {
          id: otherUserId,
          username: "rally",
          name: "Rally Partner",
          photoUrl: null,
          rating: 1520,
          friendsSince: friendsSince.toISOString(),
          playing: null,
        },
      ],
    });
    expect(mockedFetchFriends).toHaveBeenCalledWith(userId);
  });

  // Input: two friends, one of them in a game in progress
  // Expected status code: 200
  // Expected behavior: games are looked up for both friends, only those started in the last 3 hours
  // Expected output: the playing friend has their game's ID, format, start time and court; the other has null
  test("Returns the game each friend is playing", async () => {
    const thirdUserId = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f";
    const friendsSince = new Date("2026-10-01T12:00:00.000Z");
    const startedAt = new Date("2026-11-19T20:00:00.000Z");
    mockedFetchFriends.mockResolvedValue([
      { id: otherUserId, username: "rally", name: "Rally Partner", photoUrl: null, rating: 1520, friendsSince },
      { id: thirdUserId, username: "lob", name: "Lob Friend", photoUrl: null, rating: 1480, friendsSince },
    ]);
    mockedFetchGamesInProgress.mockResolvedValue([
      {
        userId: otherUserId,
        game: { id: "game-1", format: Format.SINGLES, startsAt: startedAt, court: { name: "Kitsilano Beach Park" } },
      },
    ]);

    const before = Date.now();
    const response = await request(createApp()).get("/friends").set("Authorization", authHeader);

    expect(response.status).toBe(200);
    const { friends } = response.body as { friends: { id: string; playing: unknown }[] };
    expect(friends.map((friend) => [friend.id, friend.playing])).toEqual([
      [
        otherUserId,
        { gameId: "game-1", format: "SINGLES", startedAt: startedAt.toISOString(), courtName: "Kitsilano Beach Park" },
      ],
      [thirdUserId, null],
    ]);
    const after = Date.now();
    const threeHours = 3 * 60 * 60 * 1000;
    const [ids, startedSince] = mockedFetchGamesInProgress.mock.calls[0] ?? [];
    expect(ids).toEqual([otherUserId, thirdUserId]);
    expect(startedSince?.getTime()).toBeGreaterThanOrEqual(before - threeHours);
    expect(startedSince?.getTime()).toBeLessThanOrEqual(after - threeHours);
  });

  // Input: authenticated caller with no accepted friends
  // Expected status code: 200
  // Expected output: { friends: [] }
  test("Returns an empty list when the caller has no friends", async () => {
    mockedFetchFriends.mockResolvedValue([]);

    const response = await request(createApp()).get("/friends").set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ friends: [] });
  });

  // Input: valid request while the database is unavailable
  // Expected status code: 500
  // Expected output: { error: "Internal server error", code: "INTERNAL_ERROR" }
  test("Returns 500 when the database fails", async () => {
    const consoleError = silenceConsoleError();
    mockedFetchFriends.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp()).get("/friends").set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

// Interface GET /friends/requests
describe("Mocked: GET /friends/requests", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: authenticated caller with one incoming and one outgoing pending request
  // Expected status code: 200
  // Expected output: { incoming: [...], outgoing: [...] }, each entry with requestedAt as an ISO string
  test("Returns incoming and outgoing pending requests", async () => {
    const requestedAt = new Date("2026-10-20T08:30:00.000Z");
    const thirdUserId = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f";
    mockedFetchFriendRequests.mockResolvedValue({
      incoming: [{ id: otherUserId, username: "rally", name: "Rally Partner", photoUrl: null, rating: 1520, requestedAt }],
      outgoing: [{ id: thirdUserId, username: "lob", name: "Lob Master", photoUrl: null, rating: 1480, requestedAt }],
    });

    const response = await request(createApp()).get("/friends/requests").set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      incoming: [
        { id: otherUserId, username: "rally", name: "Rally Partner", photoUrl: null, rating: 1520, requestedAt: requestedAt.toISOString() },
      ],
      outgoing: [
        { id: thirdUserId, username: "lob", name: "Lob Master", photoUrl: null, rating: 1480, requestedAt: requestedAt.toISOString() },
      ],
    });
    expect(mockedFetchFriendRequests).toHaveBeenCalledWith(userId);
  });

  // Input: valid request while the database is unavailable
  // Expected status code: 500
  // Expected output: { error: "Internal server error", code: "INTERNAL_ERROR" }
  test("Returns 500 when the database fails", async () => {
    const consoleError = silenceConsoleError();
    mockedFetchFriendRequests.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp()).get("/friends/requests").set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

// Interface POST /friends/requests
describe("Mocked: POST /friends/requests", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const send = (body: object) =>
    request(createApp()).post("/friends/requests").set("Authorization", authHeader).send(body);

  // Input: { userId } of a user the caller has no friendship with
  // Expected status code: 201
  // Expected behavior: a new pending request is created from the caller to the user
  // Expected output: { status: "PENDING" }
  test("Sends a pending request when the users have no friendship", async () => {
    mockedFetchFriendship.mockResolvedValue(null);
    mockedCreateFriendRequest.mockResolvedValue(
      friendship(userId, otherUserId, FriendshipStatus.PENDING),
    );

    const response = await send({ userId: otherUserId });

    expect(response.status).toBe(201);
    expect(response.body).toEqual({ status: FriendshipStatus.PENDING });
    expect(mockedCreateFriendRequest).toHaveBeenCalledWith(userId, otherUserId);
    expect(mockedAcceptFriendRequest).not.toHaveBeenCalled();
  });

  // Input: { userId } of a user who already sent the caller a pending request
  // Expected status code: 201
  // Expected behavior: their request is accepted and no new request is created
  // Expected output: { status: "ACCEPTED" }
  test("Accepts the other user's pending request instead of sending a new one", async () => {
    mockedFetchFriendship.mockResolvedValue(
      friendship(otherUserId, userId, FriendshipStatus.PENDING),
    );
    mockedAcceptFriendRequest.mockResolvedValue(true);

    const response = await send({ userId: otherUserId });

    expect(response.status).toBe(201);
    expect(response.body).toEqual({ status: FriendshipStatus.ACCEPTED });
    expect(mockedAcceptFriendRequest).toHaveBeenCalledWith(otherUserId, userId);
    expect(mockedCreateFriendRequest).not.toHaveBeenCalled();
  });

  // Input: { userId } of the caller
  // Expected status code: 400
  // Expected behavior: nothing is written to the database
  // Expected output: { error: "Cannot friend yourself", code: "SELF_REQUEST" }
  test("Rejects a request to yourself", async () => {
    const response = await send({ userId });

    expect(response.status).toBe(400);
    expect(response.body).toEqual(errorBody(friendErrors.SelfRequest));
    expect(mockedCreateFriendRequest).not.toHaveBeenCalled();
    expect(mockedAcceptFriendRequest).not.toHaveBeenCalled();
  });

  // Input: { userId } of a user the caller is already friends with, or has already sent a request to
  // Expected status code: 409
  // Expected behavior: nothing is written to the database
  // Expected output: { error, code: "ALREADY_FRIENDS" | "REQUEST_ALREADY_SENT" }
  test.each([
    ["the users are already friends", userId, otherUserId, FriendshipStatus.ACCEPTED, friendErrors.AlreadyFriends],
    ["they became friends from the other side", otherUserId, userId, FriendshipStatus.ACCEPTED, friendErrors.AlreadyFriends],
    ["the caller already sent a request", userId, otherUserId, FriendshipStatus.PENDING, friendErrors.RequestAlreadySent],
  ])("Returns 409 when %s", async (_description, requesterId, addresseeId, status, error) => {
    mockedFetchFriendship.mockResolvedValue(friendship(requesterId, addresseeId, status));

    const response = await send({ userId: otherUserId });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(error));
    expect(mockedCreateFriendRequest).not.toHaveBeenCalled();
    expect(mockedAcceptFriendRequest).not.toHaveBeenCalled();
  });

  // Input: { userId } while the other user's request is inserted at the same moment
  // Expected status code: 409
  // Expected output: { error: "Request already exists", code: "REQUEST_ALREADY_EXISTS" }
  test("Returns 409 when both users send a request at the same time", async () => {
    mockedFetchFriendship.mockResolvedValue(null);
    mockedCreateFriendRequest.mockRejectedValue(prismaError("P2002"));

    const response = await send({ userId: otherUserId });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(friendErrors.RequestAlreadyExists));
  });

  // Input: { userId } of a user that does not exist
  // Expected status code: 404
  // Expected output: { error: "User not found", code: "USER_NOT_FOUND" }
  test("Returns 404 when the other user does not exist", async () => {
    mockedFetchFriendship.mockResolvedValue(null);
    mockedCreateFriendRequest.mockRejectedValue(prismaError("P2003"));

    const response = await send({ userId: otherUserId });

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(commonErrors.UserNotFound));
  });

  // Input: body with a missing, invalid, or unexpected field
  // Expected status code: 400
  // Expected behavior: the database is never touched
  // Expected output: { error: "Invalid request", code: "INVALID_REQUEST", details }
  test.each([
    ["no userId", {}],
    ["a non-UUID userId", { userId: "not-a-uuid" }],
    ["a numeric userId", { userId: 42 }],
    ["an unknown field", { userId: otherUserId, status: "ACCEPTED" }],
  ])("Rejects a body with %s", async (_description, body) => {
    const response = await send(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual(errorBody(commonErrors.InvalidRequest, expect.anything()));
    expect(mockedFetchFriendship).not.toHaveBeenCalled();
    expect(mockedCreateFriendRequest).not.toHaveBeenCalled();
  });

  // Input: valid request while the database is unavailable
  // Expected status code: 500
  // Expected output: { error: "Internal server error", code: "INTERNAL_ERROR" }
  test("Returns 500 when the database fails", async () => {
    const consoleError = silenceConsoleError();
    mockedFetchFriendship.mockRejectedValue(new Error("Database unavailable"));

    const response = await send({ userId: otherUserId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

// Interface POST /friends/requests/accept
describe("Mocked: POST /friends/requests/accept", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const accept = (body: object) =>
    request(createApp()).post("/friends/requests/accept").set("Authorization", authHeader).send(body);

  // Input: { userId } of a user who sent the caller a pending request
  // Expected status code: 204
  // Expected behavior: the request is accepted with the caller as the addressee
  test("Accepts a pending request sent to the caller", async () => {
    mockedAcceptFriendRequest.mockResolvedValue(true);

    const response = await accept({ userId: otherUserId });

    expect(response.status).toBe(204);
    // Only the addressee can accept: the other user is the requester, the caller the addressee
    expect(mockedAcceptFriendRequest).toHaveBeenCalledWith(otherUserId, userId);
  });

  // Input: { userId } of a user with no pending request to the caller
  // Expected status code: 404
  // Expected output: { error: "Friend request not found", code: "FRIEND_REQUEST_NOT_FOUND" }
  test("Returns 404 when there is no pending request from that user", async () => {
    mockedAcceptFriendRequest.mockResolvedValue(false);

    const response = await accept({ userId: otherUserId });

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(friendErrors.RequestNotFound));
  });

  // Input: body with a missing, invalid, or unexpected field
  // Expected status code: 400
  // Expected behavior: the database is never touched
  // Expected output: { error: "Invalid request", code: "INVALID_REQUEST", details }
  test.each([
    ["no userId", {}],
    ["a non-UUID userId", { userId: "not-a-uuid" }],
    ["an unknown field", { userId: otherUserId, extra: true }],
  ])("Rejects a body with %s", async (_description, body) => {
    const response = await accept(body);

    expect(response.status).toBe(400);
    expect(response.body).toEqual(errorBody(commonErrors.InvalidRequest, expect.anything()));
    expect(mockedAcceptFriendRequest).not.toHaveBeenCalled();
  });

  // Input: valid request while the database is unavailable
  // Expected status code: 500
  // Expected output: { error: "Internal server error", code: "INTERNAL_ERROR" }
  test("Returns 500 when the database fails", async () => {
    const consoleError = silenceConsoleError();
    mockedAcceptFriendRequest.mockRejectedValue(new Error("Database unavailable"));

    const response = await accept({ userId: otherUserId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});

// Interface DELETE /friends
describe("Mocked: DELETE /friends", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const remove = (query: object) =>
    request(createApp()).delete("/friends").set("Authorization", authHeader).query(query);

  // Input: ?userId= of a friend, or of a user with a pending request either way
  // Expected status code: 204
  // Expected behavior: the friendship or request between the two users is deleted
  test("Removes the friendship or request between the caller and the user", async () => {
    mockedDeleteFriendship.mockResolvedValue(true);

    const response = await remove({ userId: otherUserId });

    expect(response.status).toBe(204);
    expect(mockedDeleteFriendship).toHaveBeenCalledWith(userId, otherUserId);
  });

  // Input: ?userId= of a user with no friendship or request with the caller
  // Expected status code: 404
  // Expected output: { error: "Friendship not found", code: "FRIENDSHIP_NOT_FOUND" }
  test("Returns 404 when there is nothing to remove", async () => {
    mockedDeleteFriendship.mockResolvedValue(false);

    const response = await remove({ userId: otherUserId });

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(friendErrors.FriendshipNotFound));
  });

  // Input: missing or non-UUID userId query
  // Expected status code: 400
  // Expected behavior: the database is never touched
  // Expected output: { error: "Invalid request", code: "INVALID_REQUEST", details }
  test.each([
    ["no userId", {}],
    ["a non-UUID userId", { userId: "not-a-uuid" }],
  ])("Rejects a query with %s", async (_description, query) => {
    const response = await remove(query);

    expect(response.status).toBe(400);
    expect(response.body).toEqual(errorBody(commonErrors.InvalidRequest, expect.anything()));
    expect(mockedDeleteFriendship).not.toHaveBeenCalled();
  });

  // Input: valid request while the database is unavailable
  // Expected status code: 500
  // Expected output: { error: "Internal server error", code: "INTERNAL_ERROR" }
  test("Returns 500 when the database fails", async () => {
    const consoleError = silenceConsoleError();
    mockedDeleteFriendship.mockRejectedValue(new Error("Database unavailable"));

    const response = await remove({ userId: otherUserId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});
