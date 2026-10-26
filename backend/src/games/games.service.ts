import { GameStatus } from "../generated/prisma/enums";
import { createGame } from "./games.repository";
import type { CreateGameInput } from "./games.schemas";

export async function newGame(input: CreateGameInput) {
  return await createGame({
    ...input,
    status: GameStatus.OPEN,
  });
}
