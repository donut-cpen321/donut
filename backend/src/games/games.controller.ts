import { NextFunction, Router, type Request, type Response } from "express";
import { z } from "zod";
import { createGameSchema, gameIdSchema } from "./games.schemas";
import { cancelGame, getGame, newGame } from "./games.service";

export const gamesRouter = Router();

const asyncHandler =
  (handler: (req: Request, res: Response) => Promise<void>) =>
  (req: Request, res: Response, next: NextFunction): void => {
    handler(req, res).catch(next);
  };

gamesRouter.use((_req: Request, _res: Response, next: NextFunction) => {
  next();
});

gamesRouter.post(
  "/",
  asyncHandler(async (req: Request, res: Response) => {
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
  }),
);

gamesRouter.get(
  "/",
  asyncHandler(async (req: Request, res: Response) => {
    const parsedGameId = gameIdSchema.safeParse(req.query.gameId);

    if (!parsedGameId.success) {
      res.status(400).json({
        error: "Invalid request query",
        details: z.flattenError(parsedGameId.error),
      });
      return;
    }

    try {
      const game = await getGame(parsedGameId.data);
      if (game === null) {
        res.status(400).json({ error: "Game not found" });
        return;
      }
      res.status(201).json({ game });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Internal server error" });
    }
  }),
);

gamesRouter.delete(
  "/",
  asyncHandler(async (req: Request, res: Response) => {
    const parsedGameId = gameIdSchema.safeParse(req.query.gameId);

    if (!parsedGameId.success) {
      res.status(400).json({
        error: "Invalid request query",
        details: z.flattenError(parsedGameId.error),
      });
      return;
    }

    try {
      await cancelGame(parsedGameId.data);
      res.status(201).json({ success: true });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: "Internal server error" });
    }
  }),
);
