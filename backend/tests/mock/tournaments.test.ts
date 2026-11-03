import request from "supertest";
import { createApp } from "../../src/app";
import { signAccessToken } from "../../src/auth/tokens";
import { getForecast } from "../../src/external/weather";
import { Prisma } from "../../src/generated/prisma/client";
import {
  Format,
  TournamentStatus,
} from "../../src/generated/prisma/enums";
import {
  createTournament,
  fetchFeedTournaments,
  fetchTournamentDetails,
  fetchUserTournaments,
  markTournamentCancelled,
  registerPlayer,
  type TournamentDetails,
  type TournamentSummary,
  unregisterPlayer,
} from "../../src/tournaments/tournaments.repository";
import {
  cancelTournamentErrors,
  joinTournamentErrors,
  leaveTournamentErrors,
} from "../../src/tournaments/tournaments.service";

// Every /tournaments route is behind requireAuth, so requests need a valid token
process.env.JWT_SECRET = "test-jwt-secret";
const userId = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
const authHeader = `Bearer ${signAccessToken(userId)}`;
const tournamentId = "9b8c7d6e-5f4a-4b3c-8d2e-1f0a9b8c7d6e";

jest.mock("../../src/tournaments/tournaments.repository", () => ({
  createTournament: jest.fn(),
  fetchFeedTournaments: jest.fn(),
  fetchUserTournaments: jest.fn(),
  fetchTournamentDetails: jest.fn(),
  registerPlayer: jest.fn(),
  unregisterPlayer: jest.fn(),
  markTournamentCancelled: jest.fn(),
}));

jest.mock("../../src/external/weather", () => ({
  getForecast: jest.fn(),
}));

const mockedCreateTournament = jest.mocked(createTournament);
const mockedFetchFeedTournaments = jest.mocked(fetchFeedTournaments);
const mockedFetchUserTournaments = jest.mocked(fetchUserTournaments);
const mockedFetchTournamentDetails = jest.mocked(fetchTournamentDetails);
const mockedRegisterPlayer = jest.mocked(registerPlayer);
const mockedUnregisterPlayer = jest.mocked(unregisterPlayer);
const mockedMarkTournamentCancelled = jest.mocked(markTournamentCancelled);
const mockedGetForecast = jest.mocked(getForecast);

const futureStartsAt = () =>
  new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();

const validBody = () => ({
  name: "Sunday Smash",
  format: Format.SINGLES,
  startsAt: futureStartsAt(),
  courtId: 3,
});

const summary = (overrides: Partial<TournamentSummary> = {}): TournamentSummary => ({
  id: tournamentId,
  name: "Sunday Smash",
  format: Format.SINGLES,
  status: TournamentStatus.REGISTRATION,
  startsAt: new Date(futureStartsAt()),
  courtId: 3,
  creatorId: userId,
  winningTeam: null,
  createdAt: new Date(),
  capacity: 8,
  court: { id: 3, name: "Kitsilano Beach Park", latitude: 49.27, longitude: -123.15 },
  creator: { id: userId, username: "alice@example.com", name: "Alice", rating: 1500 },
  players: [
    {
      id: userId,
      username: "alice@example.com",
      name: "Alice",
      rating: 1500,
      team: null,
      joinedAt: new Date(),
    },
  ],
  ...overrides,
});

const details = (): TournamentDetails => ({
  ...summary({ status: TournamentStatus.IN_PROGRESS }),
  matches: [
    {
      round: 1,
      position: 0,
      team1: 1,
      team2: 8,
      winner: null,
      game: null,
    },
  ],
});

// Silences the console.error the controllers log for unexpected errors
const silenceConsoleError = () =>
  jest.spyOn(console, "error").mockImplementation(() => undefined);

const unexpectedError = () => new Error("Database unavailable");

const prismaError = (code: string) =>
  new Prisma.PrismaClientKnownRequestError("Foreign key constraint violated", {
    code,
    clientVersion: Prisma.prismaVersion.client,
  });

beforeEach(() => {
  jest.clearAllMocks();
});

describe("Mocked: /tournaments requireAuth", () => {
  const routes = () =>
    [
      ["POST /tournaments", () => request(createApp()).post("/tournaments").send(validBody())],
      ["GET /tournaments", () => request(createApp()).get("/tournaments").query({ tournamentId })],
      ["GET /tournaments/feed", () => request(createApp()).get("/tournaments/feed")],
      ["GET /tournaments/me", () => request(createApp()).get("/tournaments/me")],
      ["POST /tournaments/join", () => request(createApp()).post("/tournaments/join").send({ tournamentId })],
      ["POST /tournaments/leave", () => request(createApp()).post("/tournaments/leave").send({ tournamentId })],
      ["DELETE /tournaments", () => request(createApp()).delete("/tournaments").query({ tournamentId })],
    ] as const;

  // Input: a request to each route without an Authorization header
  // Expected status code: 401
  // Expected behavior: the repository is never called
  // Expected output: { error: "Missing auth token" }
  test.each(routes())("%s rejects a request without a token", async (_route, send) => {
    const response = await send();

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: "Missing auth token" });
    expect(mockedCreateTournament).not.toHaveBeenCalled();
    expect(mockedFetchTournamentDetails).not.toHaveBeenCalled();
    expect(mockedFetchFeedTournaments).not.toHaveBeenCalled();
    expect(mockedFetchUserTournaments).not.toHaveBeenCalled();
    expect(mockedRegisterPlayer).not.toHaveBeenCalled();
    expect(mockedUnregisterPlayer).not.toHaveBeenCalled();
    expect(mockedMarkTournamentCancelled).not.toHaveBeenCalled();
  });
});

// Interface POST /tournaments
describe("Mocked: POST /tournaments", () => {
  // Input: a valid name, format, future start time and court
  // Expected status code: 201
  // Expected behavior: the tournament is created with the caller as its creator
  // Expected output: { tournament } with the created tournament
  test("Creates a tournament owned by the caller", async () => {
    const body = validBody();
    const created = {
      id: tournamentId,
      ...body,
      startsAt: new Date(body.startsAt),
      status: TournamentStatus.REGISTRATION,
      creatorId: userId,
      winningTeam: null,
      createdAt: new Date(),
    };
    mockedCreateTournament.mockResolvedValue(created);

    const response = await request(createApp())
      .post("/tournaments")
      .set("Authorization", authHeader)
      .send(body);

    expect(response.status).toBe(201);
    expect(response.body.tournament).toMatchObject({
      id: tournamentId,
      name: body.name,
      status: TournamentStatus.REGISTRATION,
      creatorId: userId,
    });
    expect(mockedCreateTournament).toHaveBeenCalledWith(
      { ...body, startsAt: new Date(body.startsAt) },
      userId,
    );
  });

  // Input: a name with spaces around it
  // Expected status code: 201
  // Expected behavior: the name is saved without the spaces
  test("Trims the tournament name", async () => {
    mockedCreateTournament.mockResolvedValue({ id: tournamentId } as never);

    await request(createApp())
      .post("/tournaments")
      .set("Authorization", authHeader)
      .send({ ...validBody(), name: "  Sunday Smash  " });

    expect(mockedCreateTournament).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Sunday Smash" }),
      userId,
    );
  });

  // Input: an invalid body
  // Expected status code: 400
  // Expected behavior: nothing is created
  // Expected output: { error: "Invalid request body", details }
  test.each([
    ["an empty name", { name: "" }],
    ["a blank name", { name: "   " }],
    ["a name over 50 characters", { name: "x".repeat(51) }],
    ["a missing name", { name: undefined }],
    ["an invalid format", { format: "TRIPLES" }],
    ["a start time in the past", { startsAt: new Date(Date.now() - 60 * 1000).toISOString() }],
    ["a start time without an offset", { startsAt: "2099-01-01T10:00:00" }],
    ["a non-positive courtId", { courtId: 0 }],
    ["an unknown field", { status: TournamentStatus.IN_PROGRESS }],
  ])("Rejects a body with %s", async (_description, overrides) => {
    const response = await request(createApp())
      .post("/tournaments")
      .set("Authorization", authHeader)
      .send({ ...validBody(), ...overrides });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request body");
    expect(response.body.details).toBeDefined();
    expect(mockedCreateTournament).not.toHaveBeenCalled();
  });

  // Input: a court ID that does not exist
  // Expected status code: 404
  // Expected output: { error: "Court not found" }
  test("Returns 404 when the court does not exist", async () => {
    mockedCreateTournament.mockRejectedValue(prismaError("P2003"));

    const response = await request(createApp())
      .post("/tournaments")
      .set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ error: "Court not found" });
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = silenceConsoleError();
    mockedCreateTournament.mockRejectedValue(unexpectedError());

    const response = await request(createApp())
      .post("/tournaments")
      .set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

// Interface GET /tournaments?tournamentId=
describe("Mocked: GET /tournaments", () => {
  // Input: the ID of a tournament in progress
  // Expected status code: 200
  // Expected output: { tournament } with its players and bracket
  test("Returns the tournament with its bracket", async () => {
    mockedFetchTournamentDetails.mockResolvedValue(details());

    const response = await request(createApp())
      .get("/tournaments")
      .set("Authorization", authHeader)
      .query({ tournamentId });

    expect(response.status).toBe(200);
    expect(response.body.tournament).toMatchObject({
      id: tournamentId,
      status: TournamentStatus.IN_PROGRESS,
      capacity: 8,
      matches: [{ round: 1, position: 0, team1: 1, team2: 8, winner: null, game: null }],
    });
    expect(mockedFetchTournamentDetails).toHaveBeenCalledWith(tournamentId);
  });

  // Input: the ID of a tournament that does not exist
  // Expected status code: 404
  // Expected output: { error: "Tournament not found" }
  test("Returns 404 when the tournament does not exist", async () => {
    mockedFetchTournamentDetails.mockResolvedValue(null);

    const response = await request(createApp())
      .get("/tournaments")
      .set("Authorization", authHeader)
      .query({ tournamentId });

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ error: "Tournament not found" });
  });

  // Input: a missing or malformed tournamentId
  // Expected status code: 400
  // Expected output: { error: "Invalid request query", details }
  test.each([
    ["a missing tournamentId", {}],
    ["a non-UUID tournamentId", { tournamentId: "not-a-uuid" }],
    ["multiple tournamentIds", { tournamentId: [tournamentId, tournamentId] }],
  ])("Rejects a query with %s", async (_description, query) => {
    const response = await request(createApp())
      .get("/tournaments")
      .set("Authorization", authHeader)
      .query(query);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request query");
    expect(mockedFetchTournamentDetails).not.toHaveBeenCalled();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = silenceConsoleError();
    mockedFetchTournamentDetails.mockRejectedValue(unexpectedError());

    const response = await request(createApp())
      .get("/tournaments")
      .set("Authorization", authHeader)
      .query({ tournamentId });

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

// Interface GET /tournaments/feed
describe("Mocked: GET /tournaments/feed", () => {
  // Input: two open tournaments; the second one's forecast cannot be loaded
  // Expected status code: 200
  // Expected output: { tournaments } with each tournament's forecast, or null
  test("Returns the open tournaments with their forecast", async () => {
    const first = summary();
    const second = summary({ id: "1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e", format: Format.DOUBLES, capacity: 16 });
    const forecast = {
      at: first.startsAt,
      temperature: 11.4,
      description: "light rain",
      icon: "10d",
      precipitationChance: 0.4,
    };
    mockedFetchFeedTournaments.mockResolvedValue([first, second]);
    mockedGetForecast.mockResolvedValueOnce(forecast).mockResolvedValueOnce(null);

    const response = await request(createApp())
      .get("/tournaments/feed")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body.tournaments).toHaveLength(2);
    expect(response.body.tournaments[0]).toMatchObject({
      id: first.id,
      capacity: 8,
      forecast: { ...forecast, at: forecast.at.toISOString() },
    });
    expect(response.body.tournaments[1]).toMatchObject({ id: second.id, capacity: 16, forecast: null });
    expect(mockedGetForecast).toHaveBeenNthCalledWith(1, 49.27, -123.15, first.startsAt);
    expect(mockedFetchFeedTournaments).toHaveBeenCalledWith(expect.any(Date));
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = silenceConsoleError();
    mockedFetchFeedTournaments.mockRejectedValue(unexpectedError());

    const response = await request(createApp())
      .get("/tournaments/feed")
      .set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

// Interface GET /tournaments/me
describe("Mocked: GET /tournaments/me", () => {
  // Input: a caller registered for one tournament
  // Expected status code: 200
  // Expected output: { tournaments } with the caller's tournaments
  test("Returns the caller's tournaments", async () => {
    mockedFetchUserTournaments.mockResolvedValue([summary()]);

    const response = await request(createApp())
      .get("/tournaments/me")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body.tournaments).toHaveLength(1);
    expect(response.body.tournaments[0]).not.toHaveProperty("forecast");
    expect(mockedFetchUserTournaments).toHaveBeenCalledWith(userId, expect.any(Date));
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = silenceConsoleError();
    mockedFetchUserTournaments.mockRejectedValue(unexpectedError());

    const response = await request(createApp())
      .get("/tournaments/me")
      .set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

// Interface POST /tournaments/join
describe("Mocked: POST /tournaments/join", () => {
  const join = (body: unknown = { tournamentId }) =>
    request(createApp())
      .post("/tournaments/join")
      .set("Authorization", authHeader)
      .send(body as object);

  // Input: an open tournament with free spots, or the registration that fills it
  // Expected status code: 204
  // Expected behavior: the caller is registered (and the bracket drawn when it fills)
  test.each(["registered", "drawn"] as const)("Returns 204 when the repository reports %s", async (result) => {
    mockedRegisterPlayer.mockResolvedValue(result);

    const response = await join();

    expect(response.status).toBe(204);
    expect(mockedRegisterPlayer).toHaveBeenCalledWith(tournamentId, userId, expect.any(Date));
  });

  // Input: a tournament the caller cannot join
  // Expected status code: 404 or 409
  // Expected output: { error } with the reason
  test.each([
    ["notFound", 404, joinTournamentErrors.NotFound],
    ["notOpen", 409, joinTournamentErrors.NotOpen],
    ["full", 409, joinTournamentErrors.Full],
    ["alreadyRegistered", 409, joinTournamentErrors.AlreadyRegistered],
  ] as const)("Returns the error for %s", async (result, status, error) => {
    mockedRegisterPlayer.mockResolvedValue(result);

    const response = await join();

    expect(response.status).toBe(status);
    expect(response.body).toEqual({ error });
  });

  // Input: a missing, malformed or extended body
  // Expected status code: 400
  test.each([
    ["a missing tournamentId", {}],
    ["a non-UUID tournamentId", { tournamentId: "nope" }],
    ["an unknown field", { tournamentId, userId }],
  ])("Rejects a body with %s", async (_description, body) => {
    const response = await join(body);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request body");
    expect(mockedRegisterPlayer).not.toHaveBeenCalled();
  });

  // Input: a caller whose user row no longer exists
  // Expected status code: 404
  // Expected output: { error: "User not found" }
  test("Returns 404 when the caller's user does not exist", async () => {
    mockedRegisterPlayer.mockRejectedValue(prismaError("P2003"));

    const response = await join();

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ error: "User not found" });
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = silenceConsoleError();
    mockedRegisterPlayer.mockRejectedValue(unexpectedError());

    const response = await join();

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

// Interface POST /tournaments/leave
describe("Mocked: POST /tournaments/leave", () => {
  const leave = (body: unknown = { tournamentId }) =>
    request(createApp())
      .post("/tournaments/leave")
      .set("Authorization", authHeader)
      .send(body as object);

  // Input: an open tournament the caller is registered for
  // Expected status code: 204
  // Expected behavior: the caller is removed from the tournament
  test("Removes the caller from the tournament", async () => {
    mockedUnregisterPlayer.mockResolvedValue("left");

    const response = await leave();

    expect(response.status).toBe(204);
    expect(mockedUnregisterPlayer).toHaveBeenCalledWith(tournamentId, userId);
  });

  // Input: a tournament the caller cannot leave
  // Expected status code: 404 or 409
  // Expected output: { error } with the reason
  test.each([
    ["notFound", 404, leaveTournamentErrors.NotFound],
    ["notRegistered", 404, leaveTournamentErrors.NotRegistered],
    ["notOpen", 409, leaveTournamentErrors.NotOpen],
    ["creator", 409, leaveTournamentErrors.Creator],
  ] as const)("Returns the error for %s", async (result, status, error) => {
    mockedUnregisterPlayer.mockResolvedValue(result);

    const response = await leave();

    expect(response.status).toBe(status);
    expect(response.body).toEqual({ error });
  });

  test("Rejects a body without a tournamentId", async () => {
    const response = await leave({});

    expect(response.status).toBe(400);
    expect(mockedUnregisterPlayer).not.toHaveBeenCalled();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = silenceConsoleError();
    mockedUnregisterPlayer.mockRejectedValue(unexpectedError());

    const response = await leave();

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

// Interface DELETE /tournaments?tournamentId=
describe("Mocked: DELETE /tournaments", () => {
  const cancel = (query: object = { tournamentId }) =>
    request(createApp())
      .delete("/tournaments")
      .set("Authorization", authHeader)
      .query(query);

  // Input: an open tournament created by the caller
  // Expected status code: 204
  // Expected behavior: the tournament is marked as cancelled
  test("Cancels the caller's tournament", async () => {
    mockedMarkTournamentCancelled.mockResolvedValue("cancelled");

    const response = await cancel();

    expect(response.status).toBe(204);
    expect(mockedMarkTournamentCancelled).toHaveBeenCalledWith(tournamentId, userId);
  });

  // Input: a tournament the caller cannot cancel
  // Expected status code: 403, 404 or 409
  // Expected output: { error } with the reason
  test.each([
    ["notFound", 404, cancelTournamentErrors.NotFound],
    ["notCreator", 403, cancelTournamentErrors.NotCreator],
    ["notOpen", 409, cancelTournamentErrors.NotOpen],
  ] as const)("Returns the error for %s", async (result, status, error) => {
    mockedMarkTournamentCancelled.mockResolvedValue(result);

    const response = await cancel();

    expect(response.status).toBe(status);
    expect(response.body).toEqual({ error });
  });

  test("Rejects a query without a valid tournamentId", async () => {
    const response = await cancel({ tournamentId: "nope" });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request query");
    expect(mockedMarkTournamentCancelled).not.toHaveBeenCalled();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = silenceConsoleError();
    mockedMarkTournamentCancelled.mockRejectedValue(unexpectedError());

    const response = await cancel();

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});
