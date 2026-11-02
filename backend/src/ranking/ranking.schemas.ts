import { z } from "zod";

export const LEADERBOARD_DEFAULT_LIMIT = 50;
export const LEADERBOARD_MAX_LIMIT = 100;

export const leaderboardQuerySchema = z.strictObject({
  limit: z.coerce
    .number()
    .int()
    .min(1)
    .max(LEADERBOARD_MAX_LIMIT)
    .default(LEADERBOARD_DEFAULT_LIMIT),
});

export type LeaderboardQueryInput = z.infer<typeof leaderboardQuerySchema>;
