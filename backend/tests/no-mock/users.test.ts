import request from "supertest";
import { createApp } from "../../src/app";
import { issueTokens, refreshTokenErrors } from "../../src/auth/auth.service";
import { hashRefreshToken } from "../../src/auth/tokens";
import { prisma } from "../../src/db";
import { commonErrors, errorBody } from "../../src/errors";
import { EquipmentType } from "../../src/generated/prisma/enums";
import { userErrors } from "../../src/users/users.service";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// The user routes against a real database. Every test gets a new user, so the tests don't depend on each other or
// on the order they run in.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

interface TokensBody {
  accessToken: string;
  refreshToken: string;
}

interface EquipmentBody {
  id: number;
  type: string;
  name: string;
  isDefault: boolean;
}

const app = createApp();
let user: TestUser;

beforeEach(async () => {
  [user] = (await createTestUsers("users", 1)) as [TestUser];
});

afterEach(async () => {
  await deleteTestUsers([user]);
});

afterAll(async () => {
  await prisma.$disconnect();
});

describe("Unmocked: POST /user/login", () => {
  // Input: a Google ID token, with no GOOGLE_CLIENT_ID to check it against
  // Expected status code: 401
  // Expected behavior: the token can't be verified, so no tokens are issued, and the missing setting is logged
  // Expected output: INVALID_GOOGLE_TOKEN
  test("Rejects a Google token it can't verify", async () => {
    const clientId = process.env.GOOGLE_CLIENT_ID;
    delete process.env.GOOGLE_CLIENT_ID;
    // Keeps the expected log out of the test output
    const logged = jest.spyOn(console, "error").mockImplementation(() => undefined);

    const response = await request(app).post("/user/login").send({ idToken: "not-a-google-token" });

    if (clientId !== undefined) process.env.GOOGLE_CLIENT_ID = clientId;
    expect(logged).toHaveBeenCalledWith(expect.stringContaining("GOOGLE_CLIENT_ID"));
    logged.mockRestore();
    expect(response.status).toBe(401);
    expect(response.body).toEqual(errorBody(userErrors.InvalidGoogleToken));
  });

  // Input: a body with no idToken
  // Expected status code: 400
  // Expected behavior: the request is rejected before Google is asked
  // Expected output: INVALID_REQUEST
  test("Rejects a login with no token", async () => {
    const response = await request(app).post("/user/login").send({});

    expect(response.status).toBe(400);
    expect(response.body.code).toBe(commonErrors.InvalidRequest.code);
  });
});

describe("Unmocked: POST /user/refresh", () => {
  // Input: the refresh token issued at sign-in
  // Expected status code: 200, then 401 when the same token is used again
  // Expected behavior: the token is replaced in the database: the old hash is gone and the new one is stored
  // Expected output: a new access token and refresh token
  test("Trades a refresh token for new tokens once", async () => {
    const issued = await issueTokens(user.id);

    const response = await request(app).post("/user/refresh").send({ refreshToken: issued.refreshToken });

    expect(response.status).toBe(200);
    const tokens = response.body as TokensBody;
    expect(tokens.refreshToken).not.toBe(issued.refreshToken);
    expect(await prisma.refreshToken.findMany({ where: { userId: user.id }, select: { tokenHash: true } })).toEqual([
      { tokenHash: hashRefreshToken(tokens.refreshToken) },
    ]);
    const me = await request(app).get("/user/me").set("Authorization", `Bearer ${tokens.accessToken}`);
    expect(me.body.user.id).toBe(user.id);

    const reused = await request(app).post("/user/refresh").send({ refreshToken: issued.refreshToken });
    expect(reused.status).toBe(401);
    expect(reused.body).toEqual(errorBody(refreshTokenErrors.Invalid));
  });

  // Input: a refresh token whose expiry has passed
  // Expected status code: 401
  // Expected behavior: the expired token is deleted, and no new one is stored
  // Expected output: INVALID_REFRESH_TOKEN
  test("Rejects an expired refresh token", async () => {
    const raw = "expired-refresh-token-" + user.id;
    await prisma.refreshToken.create({
      data: { userId: user.id, tokenHash: hashRefreshToken(raw), expiresAt: new Date(Date.now() - 1000) },
    });

    const response = await request(app).post("/user/refresh").send({ refreshToken: raw });

    expect(response.status).toBe(401);
    expect(response.body).toEqual(errorBody(refreshTokenErrors.Invalid));
    expect(await prisma.refreshToken.count({ where: { userId: user.id } })).toBe(0);
  });
});

describe("Unmocked: POST /user/logout", () => {
  // Input: the refresh token of one of the user's two devices
  // Expected status code: 204, and 401 when that token is used to refresh afterwards
  // Expected behavior: only that device's token is deleted; the other device stays signed in
  // Expected output: an empty body
  test("Signs out one device", async () => {
    const phone = await issueTokens(user.id);
    const tablet = await issueTokens(user.id);

    const response = await request(app).post("/user/logout").send({ refreshToken: phone.refreshToken });

    expect(response.status).toBe(204);
    expect((await request(app).post("/user/refresh").send({ refreshToken: phone.refreshToken })).status).toBe(401);
    expect((await request(app).post("/user/refresh").send({ refreshToken: tablet.refreshToken })).status).toBe(200);
  });
});

describe("Unmocked: GET /user/me", () => {
  // Input: the signed-in user's access token
  // Expected status code: 200
  // Expected behavior: the profile is read from the database
  // Expected output: the user's ID, name and starting rating of 1500
  test("Returns the signed-in user's profile", async () => {
    const response = await request(app).get("/user/me").set("Authorization", user.auth);

    expect(response.status).toBe(200);
    expect(response.body.user).toMatchObject({ id: user.id, name: "users player 1", rating: 1500 });
  });

  // Input: a valid access token for a user who has since been deleted
  // Expected status code: 404
  // Expected behavior: nothing is returned
  // Expected output: USER_NOT_FOUND
  test("Returns 404 for a deleted user", async () => {
    await prisma.user.delete({ where: { id: user.id } });

    const response = await request(app).get("/user/me").set("Authorization", user.auth);

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(commonErrors.UserNotFound));
  });
});

describe("Unmocked: PUT /user/me/fcm-token", () => {
  // Input: a device token, then a newer one
  // Expected status code: 204 each time
  // Expected behavior: the newer token replaces the first in the database
  // Expected output: an empty body
  test("Saves the device that receives push notifications", async () => {
    const first = await request(app).put("/user/me/fcm-token").set("Authorization", user.auth).send({ token: "a" });
    const second = await request(app).put("/user/me/fcm-token").set("Authorization", user.auth).send({ token: "b" });

    expect(first.status).toBe(204);
    expect(second.status).toBe(204);
    expect(await prisma.user.findUnique({ where: { id: user.id }, select: { fcmToken: true } })).toEqual({
      fcmToken: "b",
    });
  });
});

describe("Unmocked: /user/equipment", () => {
  const add = (body: object) => request(app).post("/user/equipment").set("Authorization", user.auth).send(body);
  const list = () => request(app).get("/user/equipment").set("Authorization", user.auth);

  // Interface POST /user/equipment, GET /user/equipment
  // Input: a racket set as default, then a pair of shoes
  // Expected status code: 201 for each, then 200
  // Expected behavior: both are stored for the user
  // Expected output: the list holds both, with the racket as default
  test("Adds equipment and lists it", async () => {
    const racket = await add({ type: EquipmentType.RACKET, name: "Pro Staff", isDefault: true });
    const shoes = await add({ type: EquipmentType.SHOES, name: "Court shoes" });
    expect(racket.status).toBe(201);
    expect(shoes.status).toBe(201);

    const response = await list();

    expect(response.status).toBe(200);
    const equipment = response.body.equipment as EquipmentBody[];
    expect(equipment).toHaveLength(2);
    expect(equipment).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ name: "Pro Staff", type: EquipmentType.RACKET, isDefault: true }),
        expect.objectContaining({ name: "Court shoes", type: EquipmentType.SHOES, isDefault: false }),
      ]),
    );
  });

  // Interface PATCH /user/equipment/:equipmentId
  // Input: rename a racket, then edit another user's racket
  // Expected status code: 200, then 404
  // Expected behavior: only the owner can edit; the other user's racket keeps its name
  // Expected output: the renamed racket, then EQUIPMENT_NOT_FOUND
  test("Edits the user's own equipment only", async () => {
    const created = (await add({ type: EquipmentType.RACKET, name: "Old name" })).body.equipment as EquipmentBody;
    const [other] = (await createTestUsers("users-other", 1)) as [TestUser];

    const renamed = await request(app)
      .patch(`/user/equipment/${created.id}`)
      .set("Authorization", user.auth)
      .send({ name: "New name" });
    const byOther = await request(app)
      .patch(`/user/equipment/${created.id}`)
      .set("Authorization", other.auth)
      .send({ name: "Stolen" });
    await deleteTestUsers([other]);

    expect(renamed.status).toBe(200);
    expect(renamed.body.equipment).toMatchObject({ id: created.id, name: "New name" });
    expect(byOther.status).toBe(404);
    expect(byOther.body).toEqual(errorBody(userErrors.EquipmentNotFound));
  });

  // Interface PATCH /user/equipment/:equipmentId
  // Input: change only the type of a racket to "other"
  // Expected status code: 200
  // Expected behavior: the fields not sent keep their values
  // Expected output: the same name and default flag, with the new type
  test("Changes only the fields sent", async () => {
    const created = (await add({ type: EquipmentType.RACKET, name: "Kept", isDefault: true })).body
      .equipment as EquipmentBody;

    const response = await request(app)
      .patch(`/user/equipment/${created.id}`)
      .set("Authorization", user.auth)
      .send({ type: EquipmentType.OTHER });

    expect(response.status).toBe(200);
    expect(response.body.equipment).toMatchObject({ type: EquipmentType.OTHER, name: "Kept", isDefault: true });
  });

  // Interface DELETE /user/equipment/:equipmentId
  // Input: delete a racket, then delete it again
  // Expected status code: 204, then 404
  // Expected behavior: the racket is retired (kept for past games) and no longer listed
  // Expected output: an empty list
  test("Retires equipment so it is no longer listed", async () => {
    const created = (await add({ type: EquipmentType.RACKET, name: "Retired" })).body.equipment as EquipmentBody;

    const removed = await request(app).delete(`/user/equipment/${created.id}`).set("Authorization", user.auth);
    const again = await request(app).delete(`/user/equipment/${created.id}`).set("Authorization", user.auth);

    expect(removed.status).toBe(204);
    expect(again.status).toBe(404);
    expect((await list()).body.equipment).toEqual([]);
    const stored = await prisma.equipment.findUnique({ where: { id: created.id }, select: { retiredAt: true } });
    expect(stored?.retiredAt).toBeInstanceOf(Date);
  });
});

describe("Unmocked: /user/me/preferred-courts and GET /user/:userId/photo", () => {
  // Interface PUT, GET, DELETE /user/me/preferred-courts
  // Input: add a court, list, remove it, list again
  // Expected status code: 204, 200, 204, 200
  // Expected behavior: the court is saved as preferred, then removed
  // Expected output: the court in the first list, an empty second list
  test("Adds and removes a preferred court", async () => {
    const court = await prisma.court.findFirstOrThrow({ select: { id: true, name: true } });
    const path = `/user/me/preferred-courts/${court.id}`;

    expect((await request(app).put(path).set("Authorization", user.auth)).status).toBe(204);
    const listed = await request(app).get("/user/me/preferred-courts").set("Authorization", user.auth);
    expect((await request(app).delete(path).set("Authorization", user.auth)).status).toBe(204);
    const after = await request(app).get("/user/me/preferred-courts").set("Authorization", user.auth);

    expect(listed.status).toBe(200);
    expect(listed.body.preferredCourts).toEqual([expect.objectContaining({ id: court.id, name: court.name })]);
    expect(after.body.preferredCourts).toEqual([]);
  });

  // Interface PUT, DELETE /user/me/preferred-courts
  // Input: a court ID no court has
  // Expected status code: 404 for both
  // Expected output: COURT_NOT_FOUND
  test("Rejects a preferred court that doesn't exist", async () => {
    const path = "/user/me/preferred-courts/2000000000";

    const added = await request(app).put(path).set("Authorization", user.auth);
    const removed = await request(app).delete(path).set("Authorization", user.auth);

    expect(added.status).toBe(404);
    expect(added.body).toEqual(errorBody(commonErrors.CourtNotFound));
    expect(removed.status).toBe(404);
    expect(removed.body).toEqual(errorBody(commonErrors.CourtNotFound));
  });

  // Interface GET /user/me/preferred-courts
  // Input: a valid access token for a user who has since been deleted
  // Expected status code: 404
  // Expected output: USER_NOT_FOUND
  test("Returns 404 for the preferred courts of a deleted user", async () => {
    await prisma.user.delete({ where: { id: user.id } });

    const response = await request(app).get("/user/me/preferred-courts").set("Authorization", user.auth);

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(commonErrors.UserNotFound));
  });

  // Interface GET /user/:userId/photo
  // Input: a user with a photo URL
  // Expected status code: 200
  // Expected output: the stored photo URL
  test("Returns a user's photo URL", async () => {
    await prisma.user.update({ where: { id: user.id }, data: { photoUrl: "https://example.com/me.png" } });

    const response = await request(app).get(`/user/${user.id}/photo`).set("Authorization", user.auth);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ photoUrl: "https://example.com/me.png" });
  });
});
