// Users for the tests that run against a real database (no-mock and NFR): each test file creates its own users with
// a run-unique name, so files can run at the same time and on a database with other data, and deletes them and
// everything they made when it is done
import { randomUUID } from "node:crypto";
import { signAccessToken } from "../src/auth/tokens";
import { prisma } from "../src/db";

export interface TestUser {
  id: string;
  // "Bearer <access token>"
  auth: string;
}

/**
 * Creates users in the database, signed in with access tokens signed with JWT_SECRET
 * @param label a word naming the test, part of each user's unique name
 * @param count how many users to create
 * @param rating each user's rating, by index (1500 when not given)
 * @returns the users, in order
 */
export function createTestUsers(
  label: string,
  count: number,
  rating: (index: number) => number = () => 1500,
): Promise<TestUser[]> {
  const run = randomUUID().slice(0, 8);
  return Promise.all(
    Array.from({ length: count }, (_, index) =>
      prisma.user
        .create({
          data: {
            googleSub: `${label}-test-${run}-${index}`,
            username: `${label}-${run}-${index}@example.com`,
            name: `${label} player ${index + 1}`,
            email: `${label}-${run}-${index}@example.com`,
            rating: rating(index),
          },
          select: { id: true },
        })
        .then(({ id }) => ({ id, auth: `Bearer ${signAccessToken(id)}` })),
    ),
  );
}

/**
 * Deletes users and everything they made: their games (with every row of those games), queue entries, equipment,
 * friendships, refresh tokens and preferred courts
 * @param users the users to delete
 */
export function deleteTestUsers(users: TestUser[]): Promise<void> {
  const userIds = users.map(({ id }) => id);
  return prisma.game
    .findMany({ where: { players: { some: { userId: { in: userIds } } } }, select: { id: true } })
    .then((games) => {
      const gameIds = games.map(({ id }) => id);
      return prisma.$transaction([
        prisma.gameRound.deleteMany({ where: { gameId: { in: gameIds } } }),
        prisma.scoreProposal.deleteMany({ where: { gameId: { in: gameIds } } }),
        prisma.gameEquipment.deleteMany({ where: { gameId: { in: gameIds } } }),
        prisma.gameUser.deleteMany({ where: { gameId: { in: gameIds } } }),
        prisma.game.deleteMany({ where: { id: { in: gameIds } } }),
        prisma.equipment.deleteMany({ where: { userId: { in: userIds } } }),
        prisma.matchmakingRequest.deleteMany({ where: { userId: { in: userIds } } }),
        // Friendships, refresh tokens and preferred courts are deleted with their users
        prisma.user.deleteMany({ where: { id: { in: userIds } } }),
      ]);
    })
    .then(() => undefined);
}
