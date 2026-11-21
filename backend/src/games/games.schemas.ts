import { z } from "zod";
import { Format, GameSource } from "../generated/prisma/enums";

export const startsAtSchema = z.iso
  .datetime({ offset: true })
  .transform((value: string) => new Date(value))
  .refine((date: Date) => date > new Date(), "must be in the future");

export const courtIdSchema = z.int32().positive();

// [team 1 score, team 2 score] of one round (a set in tennis)
const roundScoreSchema = z.tuple([
  z.number().nonnegative().int(),
  z.number().nonnegative().int(),
]);

// The rounds of a score waiting for confirmation, as stored in score_proposals.rounds
export const storedRoundsSchema = z.array(roundScoreSchema);
export type RoundScores = z.infer<typeof storedRoundsSchema>;

export const createGameSchema = z.strictObject({
  format: z.enum(Format),
  startsAt: startsAtSchema,
  courtId: courtIdSchema,
  // Tournament games are only created by their tournament's bracket
  gameSource: z.enum([GameSource.SCHEDULED, GameSource.MATCHED]),
});

export const gameIdSchema = z.uuid();

export const joinGameSchema = z.strictObject({
  gameId: z.uuid(),
  team: z.union([z.literal(1), z.literal(2)]),
});

export const createGameUserSchema = z.strictObject({
  gameId: z.uuid(),
  team: z.number().nonnegative().int(),
  accepted: z.boolean().optional(),
});

export const leaveGameSchema = z.strictObject({
  gameId: z.uuid(),
});

// More than a player could bring to a court; keeps the request small
const MAX_GAME_EQUIPMENT = 20;

export const setGameEquipmentSchema = z.strictObject({
  gameId: z.uuid(),
  // The caller's equipment used in the game. It replaces what they picked before, so an empty list clears it
  equipmentIds: z
    .array(z.int32().positive())
    .max(MAX_GAME_EQUIPMENT)
    .refine((ids) => new Set(ids).size === ids.length, "must not repeat an item"),
});

export const acceptMatchSchema = z.strictObject({
  gameId: z.uuid(),
});

export const inviteFriendSchema = z.strictObject({
  gameId: z.uuid(),
  userId: z.uuid(),
});

export const startGameSchema = z.strictObject({
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

// A set is won at 6 games. Past 6, it is won by exactly 2 games, or 7-6 after a tiebreak
const SET_GAMES = 6;

/**
 * Checks that a set's score could happen in tennis: any score up to 6 games, and past 6 a 2-game margin or 7-6.
 * This also bounds each set's games difference, which the rating's margin of victory counts: 99-97 counts like 8-6,
 * and 99-0 can't be entered
 * @param round the [team 1 score, team 2 score] of one set, not tied
 * @returns whether the set is allowed
 */
function isLegalSet([team1, team2]: [number, number]): boolean {
  const winner = Math.max(team1, team2);
  const loser = Math.min(team1, team2);
  const wonByTwo = winner - loser === 2;
  const tiebreak = winner === SET_GAMES + 1 && loser === SET_GAMES;
  return winner <= SET_GAMES || wonByTwo || tiebreak;
}

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

      const illegalRound = rounds.findIndex((round) => !isLegalSet(round));
      if (illegalRound !== -1) {
        ctx.addIssue({
          code: "custom",
          message: `round ${illegalRound + 1}: past 6 games a set is won by 2 games, or 7-6`,
        });
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
export type SetGameEquipmentInput = z.infer<typeof setGameEquipmentSchema>;
export type AcceptMatchInput = z.infer<typeof acceptMatchSchema>;
export type InviteFriendInput = z.infer<typeof inviteFriendSchema>;
export type StartGameInput = z.infer<typeof startGameSchema>;
export type ChangeGameTimeInput = z.infer<typeof changeGameTimeSchema>;
export type ChangeGameLocationInput = z.infer<typeof changeGameLocationSchema>;
export type ReportScoreInput = z.infer<typeof reportScoreSchema>;

// Confirming sends back the score the player saw, so a score changed in the meantime is not confirmed by accident
export const confirmScoreSchema = reportScoreSchema;
export type ConfirmScoreInput = ReportScoreInput;
