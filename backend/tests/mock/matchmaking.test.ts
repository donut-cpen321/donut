import request from "supertest";
import { createApp } from "../../src/app";
import { signAccessToken } from "../../src/auth/tokens";
import { Prisma } from "../../src/generated/prisma/client";
import {
  createRequest,
  deleteRequest,
  findRequest,
  hasActiveMatch,
} from "../../src/matchmaking/matchmaking.repository";

process.env.JWT_SECRET = "test-jwt-secret";
const userId = "user-123";
const authHeader = `Bearer ${signAccessToken(userId)}`;

jest.mock("../../src/matchmaking/matchmaking.repository", () => ({
  createRequest: jest.fn(),
  deleteRequest: jest.fn(),
  findRequest: jest.fn(),
  hasActiveMatch: jest.fn(),
}));

const mockedCreateRequest = jest.mocked(createRequest);
const mockedDeleteRequest = jest.mocked(deleteRequest);
const mockedFindRequest = jest.mocked(findRequest);
const mockedHasActiveMatch = jest.mocked(hasActiveMatch);

// A player at UBC
const validBody = () => ({ format: "EITHER", latitude: 49.2606, longitude: -123.246 });

const queuedRequest = () => ({
  format: "EITHER" as const,
  latitude: 49.2606,
  longitude: -123.246,
  createdAt: new Date("2026-11-01T12:00:00.000Z"),
});

const prismaError = (code: string) =>
  new Prisma.PrismaClientKnownRequestError("Prisma error", {
    code,
    clientVersion: Prisma.prismaVersion.client,
  });

const silenceConsoleError = () =>
  jest.spyOn(console, "error").mockImplementation(() => undefined);

// Interface POST /matchmaking, DELETE /matchmaking, GET /matchmaking
describe("Mocked: /matchmaking requireAuth", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const routes = () => [
    ["POST /matchmaking", () => request(createApp()).post("/matchmaking").send(validBody())],
    ["DELETE /matchmaking", () => request(createApp()).delete("/matchmaking")],
    ["GET /matchmaking", () => request(createApp()).get("/matchmaking")],
  ] as const;

  // Input: a request without an Authorization header
  // Expected status code: 401
  // Expected output: missing token error, the queue is not touched
  test.each(routes())("Rejects %s without an Authorization header", async (_route, send) => {
    const response = await send();

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: "Missing auth token" });
    expect(mockedCreateRequest).not.toHaveBeenCalled();
    expect(mockedDeleteRequest).not.toHaveBeenCalled();
    expect(mockedFindRequest).not.toHaveBeenCalled();
  });
});

// Interface POST /matchmaking
describe("Mocked: POST /matchmaking", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedHasActiveMatch.mockResolvedValue(false);
  });

  // Input: a player who already has a matched game coming up
  // Expected status code: 403
  // Expected output: { error: "Already has a match" }, the player is not queued
  test("Rejects a player who already has a match", async () => {
    mockedHasActiveMatch.mockResolvedValue(true);

    const response = await request(createApp())
      .post("/matchmaking")
      .set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(403);
    expect(response.body).toEqual({ error: "Already has a match" });
    expect(mockedHasActiveMatch).toHaveBeenCalledWith(userId, expect.any(Date));
    expect(mockedCreateRequest).not.toHaveBeenCalled();
  });

  // Input: format and location of a player not in the queue
  // Expected status code: 201
  // Expected output: the new queue entry, saved for the caller
  test("Puts the player in the queue", async () => {
    mockedCreateRequest.mockResolvedValue(queuedRequest());

    const response = await request(createApp())
      .post("/matchmaking")
      .set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(201);
    expect(response.body).toEqual({
      request: { ...validBody(), createdAt: "2026-11-01T12:00:00.000Z" },
    });
    expect(mockedCreateRequest).toHaveBeenCalledWith(userId, validBody());
  });

  // Input: bodies with a bad format, an impossible location, a missing field or an extra field
  // Expected status code: 400
  // Expected output: invalid body error, the queue is not touched
  test.each([
    ["an unknown format", { ...validBody(), format: "TRIPLES" }],
    ["a latitude above 90", { ...validBody(), latitude: 91 }],
    ["a longitude below -180", { ...validBody(), longitude: -181 }],
    ["a missing location", { format: "SINGLES" }],
    ["an extra field", { ...validBody(), courtId: 1 }],
  ])("Rejects %s", async (_case, body) => {
    const response = await request(createApp())
      .post("/matchmaking")
      .set("Authorization", authHeader)
      .send(body);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe("Invalid request body");
    expect(mockedCreateRequest).not.toHaveBeenCalled();
  });

  // Input: a player who is already in the queue (unique userId violated)
  // Expected status code: 409
  // Expected output: already in queue error, so they have to leave first
  test("Rejects a player already in the queue", async () => {
    mockedCreateRequest.mockRejectedValue(prismaError("P2002"));

    const response = await request(createApp())
      .post("/matchmaking")
      .set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(409);
    expect(response.body).toEqual({ error: "Already in the queue" });
  });

  // Input: the database fails
  // Expected status code: 500
  // Expected output: internal server error, logged
  test("Returns 500 when the database fails", async () => {
    const consoleError = silenceConsoleError();
    mockedCreateRequest.mockRejectedValue(new Error("db down"));

    const response = await request(createApp())
      .post("/matchmaking")
      .set("Authorization", authHeader)
      .send(validBody());

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });
});

// Interface DELETE /matchmaking
describe("Mocked: DELETE /matchmaking", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: the caller leaves the queue (whether or not they were in it)
  // Expected status code: 204
  // Expected output: the caller's queue entry is deleted
  test("Takes the player out of the queue", async () => {
    mockedDeleteRequest.mockResolvedValue(undefined);

    const response = await request(createApp())
      .delete("/matchmaking")
      .set("Authorization", authHeader);

    expect(response.status).toBe(204);
    expect(mockedDeleteRequest).toHaveBeenCalledWith(userId);
  });

  // Input: the database fails
  // Expected status code: 500
  // Expected output: internal server error, logged
  test("Returns 500 when the database fails", async () => {
    const consoleError = silenceConsoleError();
    mockedDeleteRequest.mockRejectedValue(new Error("db down"));

    const response = await request(createApp())
      .delete("/matchmaking")
      .set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

// Interface GET /matchmaking
describe("Mocked: GET /matchmaking", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: a player waiting in the queue
  // Expected status code: 200
  // Expected output: their queue entry
  test("Returns the caller's queue entry", async () => {
    mockedFindRequest.mockResolvedValue(queuedRequest());

    const response = await request(createApp())
      .get("/matchmaking")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      request: { ...validBody(), createdAt: "2026-11-01T12:00:00.000Z" },
    });
    expect(mockedFindRequest).toHaveBeenCalledWith(userId);
  });

  // Input: a player not in the queue (never joined, left, or already matched)
  // Expected status code: 404
  // Expected output: not in queue error
  test("Returns 404 when the player is not in the queue", async () => {
    mockedFindRequest.mockResolvedValue(null);

    const response = await request(createApp())
      .get("/matchmaking")
      .set("Authorization", authHeader);

    expect(response.status).toBe(404);
    expect(response.body).toEqual({ error: "Not in the queue" });
  });

  // Input: the database fails
  // Expected status code: 500
  // Expected output: internal server error, logged
  test("Returns 500 when the database fails", async () => {
    const consoleError = silenceConsoleError();
    mockedFindRequest.mockRejectedValue(new Error("db down"));

    const response = await request(createApp())
      .get("/matchmaking")
      .set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});
