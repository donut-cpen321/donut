// Queries for games, game_users and game_rounds.

import { prisma } from "../db";
import { Prisma } from "../generated/prisma/client";
import type { GameSource, GameStatus } from "../generated/prisma/enums";
import type { CreateGameInput } from "./games.schemas";

// courtId is the only foreign key on games, so this code means the court does not exist.
const FOREIGN_KEY_VIOLATION = "P2003";

// Returns null when the court does not exist.
export async function createGame(
  game: CreateGameInput & { status: GameStatus; gameSource: GameSource },
) {
  try {
    return await prisma.game.create({ data: game });
  } catch (e) {
    if (
      e instanceof Prisma.PrismaClientKnownRequestError &&
      e.code === FOREIGN_KEY_VIOLATION
    ) {
      return null;
    }
    throw e;
  }
}
