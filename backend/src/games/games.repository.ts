import { prisma } from "../db";
import { Prisma } from "../generated/prisma/client";
import type { GameSource, GameStatus } from "../generated/prisma/enums";
import type { CreateGameInput } from "./games.schemas";

// explicit type definition for Codacy
type Game = Prisma.GameGetPayload<null>;

export async function createGame(
  game: CreateGameInput & { status: GameStatus; gameSource: GameSource },
): Promise<Game> {
  try {
    return await prisma.game.create({ data: game });
  } catch (e) {
    throw e;
  }
}
