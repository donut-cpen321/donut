import { FriendshipStatus } from "../generated/prisma/enums";
import {
  acceptFriendRequest,
  createFriendRequest,
  deleteFriendship,
  fetchFriendRequests,
  fetchFriends,
  fetchFriendship,
} from "./friends.repository";

export type FriendRequestError =
  | "SELF_REQUEST"
  | "ALREADY_FRIENDS"
  | "REQUEST_ALREADY_SENT";

/**
 * Sends a friend request. If the other user already sent one to this user, it is accepted instead.
 * @param userId the user sending the request
 * @param otherUserId the user receiving the request
 * @returns the outcome of the request
 */
export async function sendFriendRequest(userId: string, otherUserId: string) {
  if (userId === otherUserId) {
    return { success: false, error: "SELF_REQUEST" } as const;
  }

  const existing = await fetchFriendship(userId, otherUserId);

  if (existing === null) {
    await createFriendRequest(userId, otherUserId);
    return { success: true, status: FriendshipStatus.PENDING } as const;
  }

  if (existing.status === FriendshipStatus.ACCEPTED) {
    return { success: false, error: "ALREADY_FRIENDS" } as const;
  }

  if (existing.requesterId === userId) {
    return { success: false, error: "REQUEST_ALREADY_SENT" } as const;
  }

  await acceptFriendRequest(otherUserId, userId);
  return { success: true, status: FriendshipStatus.ACCEPTED } as const;
}

/**
 * Accepts a pending friend request sent to this user
 * @param userId the user accepting the request
 * @param requesterId the user who sent the request
 * @returns true if a pending request was accepted, otherwise false
 */
export async function acceptRequest(userId: string, requesterId: string) {
  return await acceptFriendRequest(requesterId, userId);
}

/**
 * Removes a friend, or declines or cancels a pending request, between two users
 * @param userId the user removing the friendship
 * @param otherUserId the other user of the friendship
 * @returns true if a friendship or request was removed, otherwise false
 */
export async function removeFriend(userId: string, otherUserId: string) {
  return await deleteFriendship(userId, otherUserId);
}

/**
 * Gets the accepted friends of a user
 * @param userId the user whose friends are requested
 * @returns the friends of the user
 */
export async function getFriends(userId: string) {
  return await fetchFriends(userId);
}

/**
 * Gets the pending friend requests a user has received and sent
 * @param userId the user whose requests are requested
 * @returns the incoming and outgoing requests
 */
export async function getFriendRequests(userId: string) {
  return await fetchFriendRequests(userId);
}
