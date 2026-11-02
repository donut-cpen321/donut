import { z } from "zod";
import { FormatPreference } from "../generated/prisma/enums";

export const joinQueueSchema = z.strictObject({
  format: z.enum(FormatPreference),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
});

export type JoinQueueInput = z.infer<typeof joinQueueSchema>;
