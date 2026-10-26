import { NextFunction, Router, type Request, type Response } from "express";
import { z } from "zod";
import { requireAuth } from "../auth/requireAuth";
import {
  createGameSchema,
  gameIdSchema,
  joinGameSchema,
  leaveGameSchema,
} from "./games.schemas";
import { Prisma } from "../generated/prisma/client";
import {
  cancelGame,
  getGame,
  joinGame,
  leaveGame,
  newGame,
} from "./games.service";

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

  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const game = await newGame(parsedBody.data, req.userId);
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

gamesRouter.post("/join", async (req: Request, res: Response) => {
  const parsed = joinGameSchema.safeParse(req.body);

  if (!parsed.success) {
    res.status(400).json({
      error: "Invalid request body",
      details: z.flattenError(parsed.error),
    });
    return;
  }

  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const { gameId, team } = parsed.data;
    await joinGame(gameId, req.userId, team);
    res.sendStatus(204);
  } catch (e) {
    if (e instanceof Prisma.PrismaClientKnownRequestError) {
      if (e.code === "P2002") {
        res.status(409).json({ error: "User already in game" });
        return;
      }
      if (e.code === "P2003") {
        res.status(404).json({ error: "Game or user not found" });
        return;
      }
    }
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

gamesRouter.post("/leave", async (req: Request, res: Response) => {
  const parsed = leaveGameSchema.safeParse(req.body);

  if (!parsed.success) {
    res.status(400).json({
      error: "Invalid request body",
      details: z.flattenError(parsed.error),
    });
    return;
  }

  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const { gameId } = parsed.data;
    await leaveGame(gameId, req.userId);
    res.sendStatus(204);
  } catch (e) {
    if (
      e instanceof Prisma.PrismaClientKnownRequestError &&
      e.code === "P2025"
    ) {
      res.status(404).json({ error: "User not in game" });
      return;
    }
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});
