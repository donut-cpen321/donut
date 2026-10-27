import { z } from "zod";
import { Format, GameSource } from "../generated/prisma/enums";

export const createGameSchema = z.strictObject({
  format: z.enum(Format),
  startsAt: z.iso
    .datetime({ offset: true })
    .transform((value: string) => new Date(value))
    .refine((date: Date) => date > new Date(), "must be in the future"),
  courtId: z.int32().positive(),
  gameSource: z.enum(GameSource),
});

export const gameIdSchema = z.uuid();

export const joinGameSchema = z.strictObject({
  gameId: z.uuid(),
  team: z.number().nonnegative().int(),
});

export const createGameUserSchema = z.strictObject({
  gameId: z.uuid(),
  team: z.number().nonnegative().int(),
  accepted: z.boolean().optional(),
});

export const leaveGameSchema = z.strictObject({
  gameId: z.uuid(),
});

export type CreateGameInput = z.infer<typeof createGameSchema>;
export type GameIdInput = z.infer<typeof gameIdSchema>;
export type JoinGameInput = z.infer<typeof joinGameSchema>;
export type CreateGameUserInput = z.infer<typeof createGameUserSchema>;
export type LeaveGameInput = z.infer<typeof leaveGameSchema>;
