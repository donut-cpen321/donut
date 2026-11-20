import { rotateRefreshToken } from "../../src/auth/auth.repository";
import { EquipmentType } from "../../src/generated/prisma/enums";
import { Prisma } from "../../src/generated/prisma/client";
import {
  clearFcmTokens,
  createEquipment,
  createUser,
  getFcmTokens,
  getUserByGoogleSub,
  updateUserPhotoUrl,
} from "../../src/users/users.repository";

// The user and refresh token queries that only a real Google sign-in or a real FCM reply reach, so the no-mock tests
// can't. Prisma is faked; its mocks are plain properties, so they are safe to pass to expect()
function mockPrismaClient() {
  return {
    user: { findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), findMany: jest.fn(), updateMany: jest.fn() },
    equipment: { create: jest.fn() },
    $transaction: jest.fn(),
  };
}

jest.mock("../../src/db", () => ({ prisma: mockPrismaClient() }));

const db = jest.requireMock<{ prisma: ReturnType<typeof mockPrismaClient> }>("../../src/db").prisma;
const userId = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
const loginUser = { id: userId, username: "a@example.com", name: "A", email: "a@example.com", createdAt: new Date() };

beforeEach(() => {
  jest.clearAllMocks();
});

// Function getUserByGoogleSub, createUser, updateUserPhotoUrl
describe("Mocked: users.repository sign-in queries", () => {
  // Input: a Google subject
  // Expected behavior: the user is looked up by it, with only the login fields
  // Expected output: the user
  test("Finds a user by their Google subject", async () => {
    db.user.findUnique.mockResolvedValue(loginUser);

    await expect(getUserByGoogleSub("google-sub")).resolves.toEqual(loginUser);
    expect(db.user.findUnique).toHaveBeenCalledWith(
      expect.objectContaining({ where: { googleSub: "google-sub" }, select: expect.objectContaining({ id: true }) }),
    );
  });

  // Input: a new Google account's profile
  // Expected behavior: the user is created with it
  // Expected output: the new user's login fields
  test("Creates a user from a Google profile", async () => {
    db.user.create.mockResolvedValue(loginUser);
    const profile = { googleSub: "google-sub", username: "a@example.com", name: "A", email: "a@example.com", photoUrl: null };

    await expect(createUser(profile)).resolves.toEqual(loginUser);
    expect(db.user.create).toHaveBeenCalledWith(expect.objectContaining({ data: profile }));
  });

  // Input: a user's new Google photo
  // Expected behavior: the photo URL is saved on the user
  test("Saves a user's photo URL", async () => {
    db.user.update.mockResolvedValue(loginUser);

    await updateUserPhotoUrl(userId, "https://example.com/a.png");

    expect(db.user.update).toHaveBeenCalledWith({ where: { id: userId }, data: { photoUrl: "https://example.com/a.png" } });
  });
});

// Function getFcmTokens, clearFcmTokens
describe("Mocked: users.repository FCM tokens", () => {
  // Input: two users, one of whom has a token
  // Expected output: only the token that exists
  test("Returns the tokens of users who have one", async () => {
    db.user.findMany.mockResolvedValue([{ fcmToken: "token-a" }, { fcmToken: null }]);

    await expect(getFcmTokens([userId, "other"])).resolves.toEqual(["token-a"]);
  });

  // Input: tokens FCM said are dead
  // Expected behavior: they are cleared from whoever has them
  test("Clears dead tokens", async () => {
    db.user.updateMany.mockResolvedValue({ count: 1 });

    await clearFcmTokens(["token-a"]);

    expect(db.user.updateMany).toHaveBeenCalledWith({
      where: { fcmToken: { in: ["token-a"] } },
      data: { fcmToken: null },
    });
  });
});

// Function createEquipment
describe("Mocked: users.repository createEquipment", () => {
  // Input: a racket with no default flag or maintenance date
  // Expected behavior: it is stored as not default and with no maintenance date
  test("Fills in the defaults", async () => {
    db.equipment.create.mockResolvedValue({ id: 1 });

    await createEquipment(userId, { type: EquipmentType.RACKET, name: "Racket" });

    expect(db.equipment.create).toHaveBeenCalledWith({
      data: {
        type: EquipmentType.RACKET,
        name: "Racket",
        isDefault: false,
        nextMaintenanceAt: null,
        user: { connect: { id: userId } },
      },
    });
  });
});

// Function rotateRefreshToken
describe("Mocked: auth.repository rotateRefreshToken", () => {
  const newToken = { tokenHash: "new-hash", expiresAt: new Date(Date.now() + 1000) };

  // Input: the database fails for a reason other than a missing token
  // Expected behavior: the error is passed on, so it isn't mistaken for an invalid token
  // Expected output: the same error
  test("Passes on database errors", async () => {
    const error = new Error("connection lost");
    db.$transaction.mockRejectedValue(error);

    await expect(rotateRefreshToken("old-hash", newToken)).rejects.toBe(error);
  });

  // Input: no token has the hash (Prisma P2025)
  // Expected output: null
  test("Returns null for a token that doesn't exist", async () => {
    db.$transaction.mockRejectedValue(
      new Prisma.PrismaClientKnownRequestError("No record", { code: "P2025", clientVersion: "test" }),
    );

    await expect(rotateRefreshToken("old-hash", newToken)).resolves.toBeNull();
  });
});
