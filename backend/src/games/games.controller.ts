import { NextFunction, Router, type Request, type Response } from "express";
import { z } from "zod";
import { createGameSchema, gameIdSchema } from "./games.schemas";
import { cancelGame, getGame, newGame } from "./games.service";
import { requireAuth } from "../auth/requireAuth";

export const gamesRouter = Router();

gamesRouter.use((req: Request, res: Response, next: NextFunction) => {
  requireAuth(req, res, next);
});

gamesRouter.post("/", async (req: Request, res: Response) => {
  const parsedBody = createGameSchema.safeParse(req.body);

  if (!parsedBody.success) {
    res.status(400).json({
      error: "Invalid request body",
      details: z.flattenError(parsedBody.error),
    });
    return;
  }

  try {
    const game = await newGame(parsedBody.data);
    res.status(201).json({ game });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

gamesRouter.get("/", async (req: Request, res: Response) => {
  const parsedGameId = gameIdSchema.safeParse(req.query.gameId);

  if (!parsedGameId.success) {
    res.status(400).json({
      error: "Invalid request query",
      details: z.flattenError(parsedGameId.error),
    });
    return;
  }

  try {
    const game = await getGame(parsedGameId.data as string);
    if (game === null) {
      res.status(404).json({ error: "Game not found" });
      return;
    }
    res.status(200).json({ game });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

gamesRouter.delete("/", async (req: Request, res: Response) => {
  const parsedGameId = gameIdSchema.safeParse(req.query.gameId);

  if (!parsedGameId.success) {
    res.status(400).json({
      error: "Invalid request query",
      details: z.flattenError(parsedGameId.error),
    });
    return;
  }

  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const { success: cancelGameSuccess } = await cancelGame(
      parsedGameId.data as string,
      req.userId,
    );

    if (cancelGameSuccess) {
      res.sendStatus(204);
    } else {
      res.sendStatus(403);
    }
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});
