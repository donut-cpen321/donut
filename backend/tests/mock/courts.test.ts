import request from "supertest";
import { createApp } from "../../src/app";
import { signAccessToken } from "../../src/auth/tokens";
import { fetchCourts } from "../../src/courts/courts.repository";
import { getCourts } from "../../src/courts/courts.service";

// Every /courts route is behind requireAuth, so requests need a valid token
process.env.JWT_SECRET = "test-jwt-secret";
const authHeader = `Bearer ${signAccessToken("user-123")}`;

jest.mock("../../src/courts/courts.repository", () => ({
  fetchCourts: jest.fn(),
}));

const mockedFetchCourts = jest.mocked(fetchCourts);

const courts = [
  { id: 2, name: "Kitsilano Beach Park", latitude: 49.27, longitude: -123.15 },
  { id: 1, name: "Queen Elizabeth Park", latitude: 49.24, longitude: -123.11 },
];

describe("Mocked: GET /courts", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Returns every court with its location", async () => {
    mockedFetchCourts.mockResolvedValue(courts);

    const response = await request(createApp())
      .get("/courts")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ courts });
    expect(mockedFetchCourts).toHaveBeenCalledTimes(1);
  });

  test("Returns an empty list when there are no courts", async () => {
    mockedFetchCourts.mockResolvedValue([]);

    const response = await request(createApp())
      .get("/courts")
      .set("Authorization", authHeader);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ courts: [] });
  });

  test("Rejects a request without an Authorization header", async () => {
    const response = await request(createApp()).get("/courts");

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: "Missing auth token" });
    expect(mockedFetchCourts).not.toHaveBeenCalled();
  });

  test("Rejects a request with an invalid token", async () => {
    const response = await request(createApp())
      .get("/courts")
      .set("Authorization", "Bearer not-a-jwt");

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: "Invalid or expired auth token" });
    expect(mockedFetchCourts).not.toHaveBeenCalled();
  });

  test("Returns 500 when the repository throws unexpectedly", async () => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedFetchCourts.mockRejectedValue(new Error("Database unavailable"));

    const response = await request(createApp())
      .get("/courts")
      .set("Authorization", authHeader);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: "Internal server error" });
    consoleError.mockRestore();
  });
});

describe("Mocked: getCourts", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("Returns the courts from the repository", async () => {
    mockedFetchCourts.mockResolvedValue(courts);

    await expect(getCourts()).resolves.toBe(courts);
  });
});
