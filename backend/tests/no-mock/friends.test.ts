import request from "supertest";
import { createApp } from "../../src/app";
import { prisma } from "../../src/db";
import { commonErrors, errorBody } from "../../src/errors";
import { friendErrors } from "../../src/friends/friends.service";
import { Format, FriendshipStatus, GameSource, GameStatus } from "../../src/generated/prisma/enums";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// The friends routes against a real database. Every test gets three new users with no friendships, so the tests
// don't depend on each other or on the order they run in.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

interface FriendBody {
  id: string;
  playing: { gameId: string } | null;
}

const app = createApp();
let alice: TestUser;
let bob: TestUser;
let carol: TestUser;

const sendRequest = (from: TestUser, to: TestUser) =>
  request(app).post("/friends/requests").set("Authorization", from.auth).send({ userId: to.id });
const accept = (by: TestUser, from: TestUser) =>
  request(app).post("/friends/requests/accept").set("Authorization", by.auth).send({ userId: from.id });
const friendIds = (user: TestUser) =>
  request(app)
    .get("/friends")
    .set("Authorization", user.auth)
    .then((response) => (response.body.friends as FriendBody[]).map(({ id }) => id));

beforeEach(async () => {
  [alice, bob, carol] = (await createTestUsers("friends", 3)) as [TestUser, TestUser, TestUser];
});

afterEach(async () => {
  await deleteTestUsers([alice, bob, carol]);
});

afterAll(async () => {
  await prisma.$disconnect();
});

describe("Unmocked: POST /friends/requests", () => {
  // Input: Alice sends Bob a request, then sends it again
  // Expected status code: 201, then 409
  // Expected behavior: one pending friendship is stored
  // Expected output: { status: "PENDING" }, then REQUEST_ALREADY_SENT
  test("Stores a pending request once", async () => {
    const sent = await sendRequest(alice, bob);
    const again = await sendRequest(alice, bob);

    expect(sent.status).toBe(201);
    expect(sent.body).toEqual({ status: FriendshipStatus.PENDING });
    expect(again.status).toBe(409);
    expect(again.body).toEqual(errorBody(friendErrors.RequestAlreadySent));
    expect(await prisma.friendship.count({ where: { requesterId: alice.id, addresseeId: bob.id } })).toBe(1);
  });

  // Input: Alice sends Bob a request, then Bob sends Alice one
  // Expected status code: 201 each time
  // Expected behavior: Bob's request accepts Alice's, so they are friends
  // Expected output: { status: "ACCEPTED" }
  test("Accepts the other user's request when sent back", async () => {
    await sendRequest(alice, bob);

    const back = await sendRequest(bob, alice);

    expect(back.status).toBe(201);
    expect(back.body).toEqual({ status: FriendshipStatus.ACCEPTED });
    expect(await friendIds(alice)).toEqual([bob.id]);
  });

  // Input: Alice sends herself a request, and one to a user who doesn't exist
  // Expected status code: 400, then 404
  // Expected behavior: nothing is stored
  // Expected output: SELF_REQUEST, then USER_NOT_FOUND
  test("Rejects requests to yourself and to unknown users", async () => {
    const self = await sendRequest(alice, alice);
    const unknown = await request(app)
      .post("/friends/requests")
      .set("Authorization", alice.auth)
      .send({ userId: "00000000-0000-4000-8000-000000000000" });

    expect(self.status).toBe(400);
    expect(self.body).toEqual(errorBody(friendErrors.SelfRequest));
    expect(unknown.status).toBe(404);
    expect(unknown.body).toEqual(errorBody(commonErrors.UserNotFound));
    expect(await prisma.friendship.count({ where: { requesterId: alice.id } })).toBe(0);
  });
});

describe("Unmocked: GET /friends/requests", () => {
  // Input: Alice sends requests to Bob and Carol
  // Expected status code: 200
  // Expected behavior: each side sees the requests from their side
  // Expected output: Alice has 2 outgoing and no incoming; Bob has Alice incoming
  test("Lists incoming and outgoing requests", async () => {
    await sendRequest(alice, bob);
    await sendRequest(alice, carol);

    const mine = await request(app).get("/friends/requests").set("Authorization", alice.auth);
    const bobs = await request(app).get("/friends/requests").set("Authorization", bob.auth);

    expect(mine.status).toBe(200);
    expect(mine.body.incoming).toEqual([]);
    expect((mine.body.outgoing as { id: string }[]).map(({ id }) => id).sort()).toEqual([bob.id, carol.id].sort());
    expect(bobs.body.incoming).toEqual([expect.objectContaining({ id: alice.id, name: "friends player 1" })]);
    expect(bobs.body.outgoing).toEqual([]);
  });
});

describe("Unmocked: POST /friends/requests/accept", () => {
  // Input: Bob accepts Alice's request; Carol accepts a request that was never sent
  // Expected status code: 204, then 404
  // Expected behavior: Alice and Bob become friends, and the request is no longer pending
  // Expected output: an empty body, then FRIEND_REQUEST_NOT_FOUND
  test("Accepts a pending request only", async () => {
    await sendRequest(alice, bob);

    const accepted = await accept(bob, alice);
    const notSent = await accept(carol, alice);

    expect(accepted.status).toBe(204);
    expect(notSent.status).toBe(404);
    expect(notSent.body).toEqual(errorBody(friendErrors.RequestNotFound));
    expect((await request(app).get("/friends/requests").set("Authorization", bob.auth)).body.incoming).toEqual([]);
    expect(await friendIds(bob)).toEqual([alice.id]);
  });
});

describe("Unmocked: GET /friends", () => {
  // Input: Alice is friends with Bob and Carol, and Bob is playing a game that started just now
  // Expected status code: 200
  // Expected behavior: each friend shows whether they are playing
  // Expected output: Bob with his game, Carol with playing: null
  test("Lists friends and the game each is playing", async () => {
    await sendRequest(alice, bob);
    await accept(bob, alice);
    await sendRequest(carol, alice);
    await accept(alice, carol);
    const court = await prisma.court.findFirstOrThrow({ select: { id: true } });
    const game = await prisma.game.create({
      data: {
        format: Format.SINGLES,
        status: GameStatus.IN_PROGRESS,
        startsAt: new Date(),
        courtId: court.id,
        gameSource: GameSource.SCHEDULED,
        players: { create: [{ userId: bob.id, team: 1 }] },
      },
    });

    const response = await request(app).get("/friends").set("Authorization", alice.auth);

    expect(response.status).toBe(200);
    const friends = response.body.friends as FriendBody[];
    expect(friends).toHaveLength(2);
    expect(friends.find(({ id }) => id === bob.id)?.playing).toMatchObject({ gameId: game.id });
    expect(friends.find(({ id }) => id === carol.id)?.playing).toBeNull();
  });
});

describe("Unmocked: DELETE /friends", () => {
  // Input: Alice removes Bob, then removes him again
  // Expected status code: 204, then 404
  // Expected behavior: the friendship is deleted for both
  // Expected output: an empty body, then FRIENDSHIP_NOT_FOUND
  test("Removes a friend for both users", async () => {
    await sendRequest(alice, bob);
    await accept(bob, alice);

    const removed = await request(app).delete("/friends").query({ userId: bob.id }).set("Authorization", alice.auth);
    const again = await request(app).delete("/friends").query({ userId: bob.id }).set("Authorization", alice.auth);

    expect(removed.status).toBe(204);
    expect(again.status).toBe(404);
    expect(again.body).toEqual(errorBody(friendErrors.FriendshipNotFound));
    expect(await friendIds(alice)).toEqual([]);
    expect(await friendIds(bob)).toEqual([]);
  });
});
