import { prisma } from "../db";
import { Prisma } from "../generated/prisma/client";
import { FriendshipStatus } from "../generated/prisma/enums";

// The user fields that are safe to show to other players.
const friendUserSelect = {
  id: true,
  username: true,
  name: true,
  rating: true,
} satisfies Prisma.UserSelect;

/**
 * Builds a filter matching the friendship between two users in either direction
 * @param userId one user of the pair
 * @param otherUserId the other user of the pair
 */
function pairWhere(userId: string, otherUserId: string) {
  return {
    OR: [
      { requesterId: userId, addresseeId: otherUserId },
      { requesterId: otherUserId, addresseeId: userId },
    ],
  } satisfies Prisma.FriendshipWhereInput;
}

/**
 * Fetches the friendship between two users, regardless of who sent the request
 * @param userId one user of the pair
 * @param otherUserId the other user of the pair
 * @returns the friendship or null if there is none
 */
export async function fetchFriendship(userId: string, otherUserId: string) {
  return await prisma.friendship.findFirst({
    where: pairWhere(userId, otherUserId),
  });
}

/**
 * Creates a pending friend request
 * @param requesterId the user sending the request
 * @param addresseeId the user receiving the request
 * @returns the created friendship
 */
export async function createFriendRequest(
  requesterId: string,
  addresseeId: string,
) {
  return await prisma.friendship.create({
    data: { requesterId, addresseeId },
  });
}

/**
 * Accepts a pending friend request
 * @param requesterId the user who sent the request
 * @param addresseeId the user who received the request
 * @returns true if a pending request was accepted, otherwise false
 */
export async function acceptFriendRequest(
  requesterId: string,
  addresseeId: string,
) {
  const { count } = await prisma.friendship.updateMany({
    where: { requesterId, addresseeId, status: FriendshipStatus.PENDING },
    data: { status: FriendshipStatus.ACCEPTED, acceptedAt: new Date() },
  });

  return count > 0;
}

/**
 * Deletes the friendship or friend request between two users, in either direction
 * @param userId one user of the pair
 * @param otherUserId the other user of the pair
 * @returns true if a row was deleted, otherwise false
 */
export async function deleteFriendship(userId: string, otherUserId: string) {
  const { count } = await prisma.friendship.deleteMany({
    where: pairWhere(userId, otherUserId),
  });

  return count > 0;
}

/**
 * Fetches the accepted friends of a user
 * @param userId the user whose friends are requested
 * @returns the friends of the user
 */
export async function fetchFriends(userId: string) {
  const friendships = await prisma.friendship.findMany({
    where: {
      status: FriendshipStatus.ACCEPTED,
      OR: [{ requesterId: userId }, { addresseeId: userId }],
    },
    select: {
      acceptedAt: true,
      requester: { select: friendUserSelect },
      addressee: { select: friendUserSelect },
    },
    orderBy: { acceptedAt: "desc" },
  });

  return friendships.map(({ acceptedAt, requester, addressee }) => ({
    ...(requester.id === userId ? addressee : requester),
    friendsSince: acceptedAt,
  }));
}

/**
 * Fetches the pending friend requests a user has received and sent
 * @param userId the user whose requests are requested
 * @returns the incoming and outgoing requests
 */
export async function fetchFriendRequests(userId: string) {
  const [incoming, outgoing] = await Promise.all([
    prisma.friendship.findMany({
      where: { addresseeId: userId, status: FriendshipStatus.PENDING },
      select: { createdAt: true, requester: { select: friendUserSelect } },
      orderBy: { createdAt: "desc" },
    }),
    prisma.friendship.findMany({
      where: { requesterId: userId, status: FriendshipStatus.PENDING },
      select: { createdAt: true, addressee: { select: friendUserSelect } },
      orderBy: { createdAt: "desc" },
    }),
  ]);

  return {
    incoming: incoming.map(({ createdAt, requester }) => ({
      ...requester,
      requestedAt: createdAt,
    })),
    outgoing: outgoing.map(({ createdAt, addressee }) => ({
      ...addressee,
      requestedAt: createdAt,
    })),
  };
}
