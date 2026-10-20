import { NextFunction, Router, type Request, type Response } from "express";
import { z } from "zod";
import { createGameSchema } from "./games.schemas";
import { newGame } from "./games.service";

// Routes: create, delete, update, join, feed, accept, score.
export const gamesRouter = Router();

gamesRouter.use((_req: Request, _res: Response, next: NextFunction) => {
  next();
});

gamesRouter.post("/create", async (req: Request, res: Response) => {
  const parsed = createGameSchema.safeParse(req.body);

  if (!parsed.success) {
    res.status(400).json({
      error: "Invalid request body",
      details: z.flattenError(parsed.error),
    });
    return;
  }

  try {
    const game = await newGame(parsed.data);
    if (game === null) {
      res.status(400).json({ error: "Court ID does not exist" });
      return;
    }
    res.status(201).json({ game });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});
