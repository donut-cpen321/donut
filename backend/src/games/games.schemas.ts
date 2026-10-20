import { z } from "zod";
import { Format, GameSource, Sport } from "../generated/prisma/enums";

export const createGameSchema = z.strictObject({
  sport: z.enum(Sport),
  format: z.enum(Format),
  startsAt: z.iso
    .datetime({ offset: true })
    .transform((value) => new Date(value))
    .refine((date) => date > new Date(), "must be in the future"),
  courtId: z.int32().positive(),
  gameSource: z.enum(GameSource),
});

export type CreateGameInput = z.infer<typeof createGameSchema>;
