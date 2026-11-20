import request from "supertest";
import {
  createApp,
} from "../../src/app";
import {
  FriendshipStatus,
  GameSource,
  GameStatus,
} from "../../src/generated/prisma/enums";
import {
  acceptMatch,
  cancelMatchedGame,
  deleteGame,
  deleteGameUser,
  fetchGame,
  fetchGameDetails,
  isUserInGame,
  updateGame,
} from "../../src/games/games.repository";
import {
  gameErrors,
} from "../../src/games/games.service";
import {
  isPushEnabled,
} from "../../src/external/fcm";
import {
  fetchFriendship,
} from "../../src/friends/friends.repository";
import {
  pushToUsers,
} from "../../src/users/users.service";
import {
  commonErrors,
  errorBody,
} from "../../src/errors";
import {
  userId,
  authHeader,
  futureStartsAt,
  gameDetails,
  joinGameId,
  joinableGame,
  editGameId,
  existingGame,
} from "./gamesFixtures";

// Mocked tests of matched games and invites: POST /games/accept, cancelling a match, POST /games/invite.
// The fixtures shared by every games.*.test.ts are in gamesFixtures.ts
jest.mock("../../src/games/games.repository", () => ({
  createGame: jest.fn(),
  fetchGame: jest.fn(),
  deleteGame: jest.fn(),
  isUserInGame: jest.fn(),
  deleteGameUser: jest.fn(),
  updateGame: jest.fn(),
  startBookedGame: jest.fn(),
  recordScore: jest.fn(),
  fetchGameDetails: jest.fn(),
  fetchFeedGames: jest.fn(),
  fetchUserGames: jest.fn(),
  fetchUserHistory: jest.fn(),
  addPlayerToTeam: jest.fn(),
  acceptMatch: jest.fn(),
  cancelMatchedGame: jest.fn(),
}));

jest.mock("../../src/external/weather", () => ({
  getForecast: jest.fn(),
}));

jest.mock("../../src/external/fcm", () => ({
  isPushEnabled: jest.fn(),
}));

jest.mock("../../src/friends/friends.repository", () => ({
  fetchFriendship: jest.fn(),
}));

jest.mock("../../src/users/users.service", () => ({
  ...jest.requireActual<typeof import("../../src/users/users.service")>(
    "../../src/users/users.service",
  ),
  pushToUsers: jest.fn(),
}));

const mockedIsPushEnabled = jest.mocked(isPushEnabled);
const mockedFetchFriendship = jest.mocked(fetchFriendship);
const mockedPushToUsers = jest.mocked(pushToUsers);

const mockedFetchGame = jest.mocked(fetchGame);
const mockedFetchGameDetails = jest.mocked(fetchGameDetails);
const mockedAcceptMatch = jest.mocked(acceptMatch);
const mockedCancelMatchedGame = jest.mocked(cancelMatchedGame);
const mockedDeleteGame = jest.mocked(deleteGame);
const mockedIsUserInGame = jest.mocked(isUserInGame);
const mockedDeleteGameUser = jest.mocked(deleteGameUser);
const mockedUpdateGame = jest.mocked(updateGame);

describe("Mocked: POST /games/accept", () => {
  const opponentId = "opponent-user-id";
  const opponent = {
    id: opponentId,
    username: "rival",
    name: "Rival Player",
    rating: 1500,
    photoUrl: null,
    team: 2,
    acceptedAt: null,
    scoreConfirmedAt: null,
    ratingBefore: null,
    ratingAfter: null,
  };
  const matchedGame = gameDetails({
    id: joinGameId,
    gameSource: GameSource.MATCHED,
    players: [...gameDetails().players, opponent],
  });

  const accept = () =>
    request(createApp())
      .post("/games/accept")
      .set("Authorization", authHeader)
      .send({ gameId: joinGameId });

  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(joinableGame({ gameSource: GameSource.MATCHED }));
    mockedIsUserInGame.mockResolvedValue(true);
    mockedAcceptMatch.mockResolvedValue("accepted");
    mockedFetchGameDetails.mockResolvedValue(matchedGame);
    mockedPushToUsers.mockResolvedValue(1);
  });

  // Input: a player accepts while the other player still hasn't answered
  // Expected status code: 204
  // Expected behavior: only the other player gets a push naming who accepted, which opens the game
  // Expected output: empty body
  test("Notifies the other players that the player accepted", async () => {
    const response = await accept();

    expect(response.status).toBe(204);
    expect(mockedPushToUsers).toHaveBeenCalledWith([opponentId], {
      title: "Match accepted",
      body: "Ace Player accepted your singles game at Kitsilano Beach Park.",
      data: { gameId: joinGameId },
    });
  });

  // Input: the last player accepts, so the game is booked
  // Expected status code: 204
  // Expected behavior: the other players get a push saying the game is booked
  // Expected output: empty body
  test("Notifies the other players that the game is booked when the last player accepts", async () => {
    mockedAcceptMatch.mockResolvedValue("booked");

    const response = await accept();

    expect(response.status).toBe(204);
    expect(mockedPushToUsers).toHaveBeenCalledWith([opponentId], {
      title: "Match confirmed!",
      body: "Everyone accepted. Your singles game at Kitsilano Beach Park is booked.",
      data: { gameId: joinGameId },
    });
  });

  // Input: a player accepts, and the game is gone by the time the push is prepared
  // Expected status code: 204
  // Expected behavior: the acceptance is saved, and no push is sent
  // Expected output: empty body
  test("Sends no push when the accepted match is already gone", async () => {
    mockedFetchGameDetails.mockResolvedValue(null);

    const response = await accept();

    expect(response.status).toBe(204);
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: a player accepts a match they already accepted, e.g. a retried request
  // Expected status code: 409
  // Expected behavior: the request is rejected and no second push is sent
  // Expected output: { error: "Match already accepted", code: "MATCH_ALREADY_ACCEPTED" }
  test("Returns 409 when the player already accepted the match", async () => {
    mockedAcceptMatch.mockResolvedValue("alreadyAccepted");

    const response = await accept();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.AlreadyAccepted));
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: sending the push fails
  // Expected status code: 204
  // Expected behavior: the error is logged, the acceptance still succeeds
  // Expected output: empty body
  test("Still accepts the match when the push fails", async () => {
    mockedPushToUsers.mockRejectedValue(new Error("FCM unavailable"));
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);

    const response = await accept();

    expect(response.status).toBe(204);
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });

  // Input: a player of a matched game waiting for answers accepts it
  // Expected status code: 204
  // Expected behavior: the acceptance is recorded for the authenticated user
  // Expected output: empty body
  test("Accepts the match for the authenticated user", async () => {
    const response = await request(createApp())
      .post("/games/accept")
      .set("Authorization", authHeader)
      .send({ gameId: joinGameId });

    expect(response.status).toBe(204);
    expect(mockedAcceptMatch).toHaveBeenCalledWith(joinGameId, userId, expect.any(Date));
  });

  // Input: a gameId that is not a UUID
  // Expected status code: 400
  // Expected behavior: nothing is recorded
  // Expected output: the validation error
  test("Rejects a body with a non-UUID gameId", async () => {
    const response = await request(createApp())
      .post("/games/accept")
      .set("Authorization", authHeader)
      .send({ gameId: "nope" });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(mockedAcceptMatch).not.toHaveBeenCalled();
  });

  // Input: the game does not exist
  // Expected status code: 404
  // Expected behavior: nothing is recorded
  // Expected output: { error: "Game not found", code: "GAME_NOT_FOUND" }
  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGame.mockResolvedValue(null);

    const response = await request(createApp())
      .post("/games/accept")
      .set("Authorization", authHeader)
      .send({ gameId: joinGameId });

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
    expect(mockedAcceptMatch).not.toHaveBeenCalled();
  });

  // Input: the user is not a player of the game
  // Expected status code: 403
  // Expected behavior: nothing is recorded
  // Expected output: { error: "User not in game", code: "NOT_IN_GAME" }
  test("Returns 403 when the user is not in the game", async () => {
    mockedIsUserInGame.mockResolvedValue(false);

    const response = await request(createApp())
      .post("/games/accept")
      .set("Authorization", authHeader)
      .send({ gameId: joinGameId });

    expect(response.status).toBe(403);
    expect(response.body).toEqual(errorBody(gameErrors.NotInGame));
    expect(mockedAcceptMatch).not.toHaveBeenCalled();
  });

  // Input: the match was cancelled, booked or passed its answer cutoff
  // Expected status code: 409
  // Expected behavior: nothing is recorded
  // Expected output: { error: "Match is no longer waiting for answers", code: "MATCH_NOT_PENDING" }
  test("Returns 409 when the match is no longer waiting for answers", async () => {
    mockedAcceptMatch.mockResolvedValue("notPending");

    const response = await request(createApp())
      .post("/games/accept")
      .set("Authorization", authHeader)
      .send({ gameId: joinGameId });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.NotPending));
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });
});

describe("Mocked: matched games can be cancelled but not left or moved", () => {
  const opponentId = "opponent-user-id";
  const opponent = {
    id: opponentId,
    username: "rival",
    name: "Rival Player",
    rating: 1500,
    photoUrl: null,
    team: 2,
    acceptedAt: null,
    scoreConfirmedAt: null,
    ratingBefore: null,
    ratingAfter: null,
  };
  const matchedGame = (status: GameStatus = GameStatus.OPEN) =>
    gameDetails({
      id: editGameId,
      gameSource: GameSource.MATCHED,
      status,
      players: [...gameDetails().players, opponent],
    });

  const cancel = () =>
    request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId: editGameId });

  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGame.mockResolvedValue(existingGame({ gameSource: GameSource.MATCHED }));
    mockedIsUserInGame.mockResolvedValue(true);
    mockedCancelMatchedGame.mockResolvedValue(true);
    mockedFetchGameDetails.mockResolvedValue(matchedGame());
    mockedPushToUsers.mockResolvedValue(1);
  });

  // Input: a player cancels a matched game still waiting for answers
  // Expected status code: 204
  // Expected behavior: only the other player gets a push naming who cancelled, which opens the app
  // Expected output: empty body
  test("Notifies the other players that the player cancelled the match", async () => {
    const response = await cancel();

    expect(response.status).toBe(204);
    expect(mockedPushToUsers).toHaveBeenCalledTimes(1);
    expect(mockedPushToUsers).toHaveBeenCalledWith([opponentId], {
      title: "Match cancelled",
      body: "Ace Player cancelled your singles game at Kitsilano Beach Park.",
      data: { gameId: editGameId },
    });
  });

  // Input: a player cancels a matched game, and the game is gone by the time the push is prepared
  // Expected status code: 204
  // Expected behavior: there is nobody left to tell, so no push is sent
  // Expected output: empty body
  test("Sends no push when the cancelled match is already gone", async () => {
    mockedFetchGameDetails.mockResolvedValue(null);

    const response = await cancel();

    expect(response.status).toBe(204);
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: a player cancels a matched game every player already accepted
  // Expected status code: 204
  // Expected behavior: the other player is still told it fell through
  // Expected output: empty body
  test("Notifies the other players when a booked match is cancelled", async () => {
    mockedFetchGameDetails.mockResolvedValue(matchedGame(GameStatus.BOOKED));

    const response = await cancel();

    expect(response.status).toBe(204);
    expect(mockedPushToUsers).toHaveBeenCalledWith([opponentId], {
      title: "Match cancelled",
      body: "Ace Player cancelled your singles game at Kitsilano Beach Park.",
      data: { gameId: editGameId },
    });
  });

  // Input: sending the push fails
  // Expected status code: 204
  // Expected behavior: the error is logged, the match is still cancelled
  // Expected output: empty body
  test("Still cancels the match when the push fails", async () => {
    mockedPushToUsers.mockRejectedValue(new Error("FCM unavailable"));
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);

    const response = await cancel();

    expect(response.status).toBe(204);
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });

  // Input: a player cancels a scheduled game
  // Expected status code: 204
  // Expected behavior: the game is deleted and nobody gets a match push
  // Expected output: empty body
  test("Sends nothing when cancelling a scheduled game", async () => {
    mockedFetchGame.mockResolvedValue(existingGame({ gameSource: GameSource.SCHEDULED }));
    mockedDeleteGame.mockResolvedValue(true);

    const response = await cancel();

    expect(response.status).toBe(204);
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: a player cancels a matched game that is already closed
  // Expected status code: 409
  // Expected behavior: nobody is notified
  // Expected output: { error: "Game is already completed or cancelled", code: "GAME_CLOSED" }
  test("Sends nothing when the matched game was already closed", async () => {
    mockedCancelMatchedGame.mockResolvedValue(false);

    const response = await cancel();

    expect(response.status).toBe(409);
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: a player cancels a matched game that is still open
  // Expected status code: 204
  // Expected behavior: the game is marked cancelled, not deleted
  // Expected output: empty body
  test("DELETE /games marks a matched game cancelled instead of deleting it", async () => {
    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId: editGameId });

    expect(response.status).toBe(204);
    expect(mockedCancelMatchedGame).toHaveBeenCalledWith(editGameId);
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  // Input: a player cancels a matched game that is already completed or cancelled
  // Expected status code: 409
  // Expected behavior: nothing changes
  // Expected output: { error: "Game is already completed or cancelled", code: "GAME_CLOSED" }
  test("DELETE /games returns 409 when the matched game is already closed", async () => {
    mockedCancelMatchedGame.mockResolvedValue(false);

    const response = await request(createApp())
      .delete("/games")
      .set("Authorization", authHeader)
      .query({ gameId: editGameId });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.Closed));
    expect(mockedDeleteGame).not.toHaveBeenCalled();
  });

  // Input: a player tries to leave a matched game
  // Expected status code: 409
  // Expected behavior: the player stays in the game
  // Expected output: { error: "Matched games can't be left", code: "MATCHED_NOT_LEAVABLE" }
  test("POST /games/leave rejects a matched game", async () => {
    const response = await request(createApp())
      .post("/games/leave")
      .set("Authorization", authHeader)
      .send({ gameId: editGameId });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.MatchedNotLeavable));
    expect(mockedDeleteGameUser).not.toHaveBeenCalled();
  });

  // Input: a player tries to move a matched game to another time
  // Expected status code: 409
  // Expected behavior: the game keeps its time
  // Expected output: { error: "Matched games can't be moved", code: "MATCHED_NOT_MOVABLE" }
  test("PATCH /games/time rejects a matched game", async () => {
    const response = await request(createApp())
      .patch("/games/time")
      .set("Authorization", authHeader)
      .send({ gameId: editGameId, startsAt: futureStartsAt() });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.MatchedNotMovable));
    expect(mockedUpdateGame).not.toHaveBeenCalled();
  });

  // Input: a player tries to move a matched game to another court
  // Expected status code: 409
  // Expected behavior: the game keeps its court
  // Expected output: { error: "Matched games can't be moved", code: "MATCHED_NOT_MOVABLE" }
  test("PATCH /games/location rejects a matched game", async () => {
    const response = await request(createApp())
      .patch("/games/location")
      .set("Authorization", authHeader)
      .send({ gameId: editGameId, courtId: 2 });

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.MatchedNotMovable));
    expect(mockedUpdateGame).not.toHaveBeenCalled();
  });
});

describe("Mocked: POST /games/invite", () => {
  const inviteGameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
  const friendId = "0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e";

  const friendship = (status: FriendshipStatus) => ({
    requesterId: userId,
    addresseeId: friendId,
    status,
    createdAt: new Date(),
    acceptedAt: status === FriendshipStatus.ACCEPTED ? new Date() : null,
  });

  const invite = (body: object = { gameId: inviteGameId, userId: friendId }) =>
    request(createApp())
      .post("/games/invite")
      .set("Authorization", authHeader)
      .send(body);

  beforeEach(() => {
    jest.clearAllMocks();
    mockedFetchGameDetails.mockResolvedValue(gameDetails());
    mockedFetchFriendship.mockResolvedValue(friendship(FriendshipStatus.ACCEPTED));
    mockedIsPushEnabled.mockReturnValue(true);
    mockedPushToUsers.mockResolvedValue(1);
  });

  // Input: the FIREBASE_* credentials are not set
  // Expected status code: 200
  // Expected behavior: no push is attempted, and the app is told the friend was not notified
  // Expected output: { notified: false }
  test("Returns notified false when push notifications are not configured", async () => {
    mockedIsPushEnabled.mockReturnValue(false);
    const consoleWarn = jest
      .spyOn(console, "warn")
      .mockImplementation(() => undefined);

    const response = await invite();

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ notified: false });
    expect(mockedPushToUsers).not.toHaveBeenCalled();
    consoleWarn.mockRestore();
  });

  // Input: a player of an open scheduled game invites a friend who has a push token
  // Expected status code: 200
  // Expected behavior: the friend gets a push that opens the game
  // Expected output: { notified: true }
  test("Sends the friend a push notification for the game", async () => {
    const response = await invite();

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ notified: true });
    expect(mockedPushToUsers).toHaveBeenCalledWith([friendId], {
      title: "Game invite",
      body: "Ace Player invited you to a singles game at Kitsilano Beach Park.",
      data: { gameId: inviteGameId, type: "gameInvite" },
    });
  });

  // Input: the push reaches no device, because the friend has no token or FCM says it no longer exists
  // Expected status code: 200
  // Expected behavior: the app is told the friend was not notified
  // Expected output: { notified: false }
  test("Returns notified false when the push reaches no device", async () => {
    mockedPushToUsers.mockResolvedValue(0);

    const response = await invite();

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ notified: false });
  });

  // Input: a userId that is not a UUID
  // Expected status code: 400
  // Expected behavior: nothing is sent
  // Expected output: the validation error
  test("Rejects a body with a non-UUID userId", async () => {
    const response = await invite({ gameId: inviteGameId, userId: "nope" });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe(commonErrors.InvalidRequest.message);
    expect(mockedFetchGameDetails).not.toHaveBeenCalled();
  });

  // Input: the game does not exist
  // Expected status code: 404
  // Expected behavior: nothing is sent
  // Expected output: { error: "Game not found", code: "GAME_NOT_FOUND" }
  test("Returns 404 when the game does not exist", async () => {
    mockedFetchGameDetails.mockResolvedValue(null);

    const response = await invite();

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(gameErrors.NotFound));
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: the inviter is not a player of the game
  // Expected status code: 403
  // Expected behavior: nothing is sent
  // Expected output: { error: "User not in game", code: "NOT_IN_GAME" }
  test("Returns 403 when the inviter is not in the game", async () => {
    mockedFetchGameDetails.mockResolvedValue(gameDetails({ players: [] }));

    const response = await invite();

    expect(response.status).toBe(403);
    expect(response.body).toEqual(errorBody(gameErrors.NotInGame));
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: the game is matched, booked or already started
  // Expected status code: 409
  // Expected behavior: nothing is sent
  // Expected output: { error: "Game is not open to join", code: "NOT_JOINABLE" }
  test.each([
    ["a matched game", { gameSource: GameSource.MATCHED }],
    ["a booked game", { status: GameStatus.BOOKED }],
    ["a game that started", { startsAt: new Date(Date.now() - 60 * 1000) }],
  ])("Returns 409 for %s", async (_name, overrides) => {
    mockedFetchGameDetails.mockResolvedValue(gameDetails(overrides));

    const response = await invite();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.NotJoinable));
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: the invited user is already a player of the game
  // Expected status code: 409
  // Expected behavior: nothing is sent
  // Expected output: { error: "User already in game", code: "ALREADY_IN_GAME" }
  test("Returns 409 when the friend is already in the game", async () => {
    const { players } = gameDetails();
    mockedFetchGameDetails.mockResolvedValue(
      gameDetails({
        players: [
          ...players,
          ...players.map((player) => ({ ...player, id: friendId, team: 2 })),
        ],
      }),
    );

    const response = await invite();

    expect(response.status).toBe(409);
    expect(response.body).toEqual(errorBody(gameErrors.AlreadyInGame));
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: the users are not friends, or the request is still pending
  // Expected status code: 403
  // Expected behavior: nothing is sent
  // Expected output: { error: "Users are not friends", code: "NOT_FRIENDS" }
  test.each([
    ["no friendship", null],
    ["a pending request", friendship(FriendshipStatus.PENDING)],
  ])("Returns 403 with %s", async (_name, existing) => {
    mockedFetchFriendship.mockResolvedValue(existing);

    const response = await invite();

    expect(response.status).toBe(403);
    expect(response.body).toEqual(errorBody(gameErrors.NotFriends));
    expect(mockedPushToUsers).not.toHaveBeenCalled();
  });

  // Input: sending the push fails
  // Expected status code: 500
  // Expected behavior: the error is logged
  // Expected output: { error: "Internal server error", code: "INTERNAL_ERROR" }
  test("Returns 500 when the push cannot be sent", async () => {
    mockedPushToUsers.mockRejectedValue(new Error("FCM down"));
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);

    const response = await invite();

    expect(response.status).toBe(500);
    expect(response.body).toEqual(errorBody(commonErrors.Internal));
    consoleError.mockRestore();
  });
});
