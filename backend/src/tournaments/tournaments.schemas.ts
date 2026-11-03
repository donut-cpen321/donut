import { z } from "zod";
import { Format } from "../generated/prisma/enums";
import { courtIdSchema, startsAtSchema } from "../games/games.schemas";

export const TOURNAMENT_NAME_MAX_LENGTH = 50;

export const createTournamentSchema = z.strictObject({
  name: z
    .string()
    .trim()
    .min(1, "must not be empty")
    .max(TOURNAMENT_NAME_MAX_LENGTH),
  format: z.enum(Format),
  startsAt: startsAtSchema,
  courtId: courtIdSchema,
});

export const tournamentIdSchema = z.uuid();

export const tournamentIdBodySchema = z.strictObject({
  tournamentId: z.uuid(),
});

export type CreateTournamentInput = z.infer<typeof createTournamentSchema>;
export type TournamentIdInput = z.infer<typeof tournamentIdSchema>;
export type TournamentIdBodyInput = z.infer<typeof tournamentIdBodySchema>;
