import { z } from "zod";

export const friendUserIdSchema = z.uuid();

export const friendRequestSchema = z.strictObject({
  userId: z.uuid(),
});

export type FriendUserIdInput = z.infer<typeof friendUserIdSchema>;
export type FriendRequestInput = z.infer<typeof friendRequestSchema>;
