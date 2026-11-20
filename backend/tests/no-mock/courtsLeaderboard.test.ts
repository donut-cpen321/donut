import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// GET /courts and GET /leaderboard against a real database. The tests only read, apart from their own users, so
// they don't depend on each other or on the order they run in.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

interface CourtBody {
  id: number;
  name: string;
  latitude: number;
  longitude: number;
}

interface EntryBody {
  id: string;
  rating: number;
  rank: number;
}

interface LeaderboardBody {
  leaderboard: EntryBody[];
  me: EntryBody | null;
}

const app = createApp();
let users: TestUser[] = [];

afterEach(async () => {
  await deleteTestUsers(users);
  users = [];
});

afterAll(async () => {
  await prisma.$disconnect();
});

describe("Unmocked: GET /courts", () => {
  // Input: a signed-in user asks for the courts
  // Expected status code: 200
  // Expected behavior: every court in the database is returned, by name
  // Expected output: the seeded Vancouver courts with their coordinates, in name order
  test("Lists every court by name", async () => {
    users = await createTestUsers("courts", 1);

    const response = await request(app).get("/courts").set("Authorization", users[0]?.auth ?? "");

    expect(response.status).toBe(200);
    const courts = response.body.courts as CourtBody[];
    // In the database's name order
    const stored = await prisma.court.findMany({ select: { id: true }, orderBy: { name: "asc" } });
    expect(courts.map(({ id }) => id)).toEqual(stored.map(({ id }) => id));
    for (const court of courts) {
      expect(court).toEqual({
        id: expect.any(Number),
        name: expect.any(String),
        latitude: expect.any(Number),
        longitude: expect.any(Number),
      });
    }
  });
});

describe("Unmocked: GET /leaderboard", () => {
  // Input: three new players rated far above anyone else (900002, 900001, 900000) ask for the top 3
  // Expected status code: 200
  // Expected behavior: players are ranked by rating, and the caller's own rank is computed the same way
  // Expected output: the three players in rating order with ranks 1-3, and the caller's entry at rank 2
  test("Ranks players by rating and includes the caller", async () => {
    users = await createTestUsers("leaderboard", 3, (index) => 900_002 - index);

    const response = await request(app)
      .get("/leaderboard")
      .query({ limit: 3 })
      .set("Authorization", users[1]?.auth ?? "");

    expect(response.status).toBe(200);
    const { leaderboard, me } = response.body as LeaderboardBody;
    expect(leaderboard.map(({ id, rank }) => ({ id, rank }))).toEqual(
      users.map(({ id }, index) => ({ id, rank: index + 1 })),
    );
    expect(me).toMatchObject({ id: users[1]?.id, rating: 900_001, rank: 2 });
  });

  // Input: a new 1500-rated player asks for the top 1
  // Expected status code: 200
  // Expected behavior: the caller isn't in the top list, but still gets their own rank
  // Expected output: one entry that isn't the caller, and the caller's rank below it
  test("Returns the caller's rank when they are outside the top list", async () => {
    users = await createTestUsers("leaderboard", 1);

    const response = await request(app)
      .get("/leaderboard")
      .query({ limit: 1 })
      .set("Authorization", users[0]?.auth ?? "");

    expect(response.status).toBe(200);
    const { leaderboard, me } = response.body as LeaderboardBody;
    expect(leaderboard).toHaveLength(1);
    expect(leaderboard[0]?.id).not.toBe(users[0]?.id);
    expect(me?.id).toBe(users[0]?.id);
    expect(me?.rank).toBeGreaterThan(1);
  });

  // Input: a valid access token for a user who has since been deleted
  // Expected status code: 200
  // Expected behavior: the leaderboard still loads, with no entry for the caller
  // Expected output: me: null
  test("Returns no entry for a caller who no longer exists", async () => {
    users = await createTestUsers("leaderboard", 1);
    const [deleted] = users as [TestUser];
    await prisma.user.delete({ where: { id: deleted.id } });

    const response = await request(app).get("/leaderboard").query({ limit: 1 }).set("Authorization", deleted.auth);

    expect(response.status).toBe(200);
    expect((response.body as LeaderboardBody).me).toBeNull();
  });

  // Input: a limit above the maximum of 100
  // Expected status code: 400
  // Expected output: INVALID_REQUEST
  test("Rejects a limit above 100", async () => {
    users = await createTestUsers("leaderboard", 1);

    const response = await request(app)
      .get("/leaderboard")
      .query({ limit: 101 })
      .set("Authorization", users[0]?.auth ?? "");

    expect(response.status).toBe(400);
    expect(response.body.code).toBe("INVALID_REQUEST");
  });
});
