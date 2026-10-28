import { z } from "zod";
import { Format, GameSource } from "../generated/prisma/enums";

const startsAtSchema = z.iso
  .datetime({ offset: true })
  .transform((value: string) => new Date(value))
  .refine((date: Date) => date > new Date(), "must be in the future");

const courtIdSchema = z.int32().positive();

// [team 1 score, team 2 score] of one round (a set in tennis)
const roundScoreSchema = z.tuple([
  z.number().nonnegative().int(),
  z.number().nonnegative().int(),
]);

export const createGameSchema = z.strictObject({
  format: z.enum(Format),
  startsAt: startsAtSchema,
  courtId: courtIdSchema,
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

export const changeGameTimeSchema = z.strictObject({
  gameId: z.uuid(),
  startsAt: startsAtSchema,
});

export const changeGameLocationSchema = z.strictObject({
  gameId: z.uuid(),
  courtId: courtIdSchema,
});

export const reportScoreSchema = z.strictObject({
  gameId: z.uuid(),
  rounds: z
    .array(roundScoreSchema)
    .min(1, "must have at least one round")
    .superRefine((rounds, ctx) => {
      const tiedRound = rounds.findIndex(([team1, team2]) => team1 === team2);
      if (tiedRound !== -1) {
        ctx.addIssue({ code: "custom", message: `round ${tiedRound + 1} is tied` });
        return;
      }

      // The winner is the team that won more rounds
      const team1Wins = rounds.filter(([team1, team2]) => team1 > team2).length;
      if (team1Wins * 2 === rounds.length) {
        ctx.addIssue({ code: "custom", message: "must have a winner" });
      }
    }),
});

export type CreateGameInput = z.infer<typeof createGameSchema>;
export type GameIdInput = z.infer<typeof gameIdSchema>;
export type JoinGameInput = z.infer<typeof joinGameSchema>;
export type CreateGameUserInput = z.infer<typeof createGameUserSchema>;
export type LeaveGameInput = z.infer<typeof leaveGameSchema>;
export type ChangeGameTimeInput = z.infer<typeof changeGameTimeSchema>;
export type ChangeGameLocationInput = z.infer<typeof changeGameLocationSchema>;
export type ReportScoreInput = z.infer<typeof reportScoreSchema>;
