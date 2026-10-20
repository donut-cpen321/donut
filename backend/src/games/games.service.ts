// Game status and game score. After a score, calls scoring.service.

import { GameStatus } from "../generated/prisma/enums";
import { createGame } from "./games.repository";
import type { CreateGameInput } from "./games.schemas";

// Returns null when the court does not exist.
export async function newGame(input: CreateGameInput) {
  return await createGame({
    ...input,
    status: GameStatus.OPEN,
  });
}
