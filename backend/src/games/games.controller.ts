import { NextFunction, Router, type Request, type Response } from "express";
import { z } from "zod";
import { requireAuth } from "../auth/requireAuth";
import {
  changeGameLocationSchema,
  changeGameTimeSchema,
  createGameSchema,
  gameIdSchema,
  joinGameSchema,
  leaveGameSchema,
  reportScoreSchema,
} from "./games.schemas";
import { Prisma } from "../generated/prisma/client";
import {
  cancelGame,
  changeGameLocation,
  changeGameTime,
  type GameEditError,
  gameEditErrors,
  getGame,
  joinGame,
  leaveGame,
  newGame,
  reportScore,
} from "./games.service";

export const gamesRouter = Router();

const gameEditErrorStatus: Record<GameEditError, number> = {
  [gameEditErrors.NotFound]: 404,
  [gameEditErrors.NotInGame]: 403,
  [gameEditErrors.Closed]: 409,
};

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

gamesRouter.patch("/time", async (req: Request, res: Response) => {
  const parsed = changeGameTimeSchema.safeParse(req.body);

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
    const { gameId, startsAt } = parsed.data;
    const result = await changeGameTime(gameId, req.userId, startsAt);

    if (!result.success) {
      res
        .status(gameEditErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
    res.status(200).json({ game: result.game });
  } catch (e) {
    if (
      e instanceof Prisma.PrismaClientKnownRequestError &&
      e.code === "P2025"
    ) {
      res.status(404).json({ error: "Game not found" });
      return;
    }
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

gamesRouter.patch("/location", async (req: Request, res: Response) => {
  const parsed = changeGameLocationSchema.safeParse(req.body);

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
    const { gameId, courtId } = parsed.data;
    const result = await changeGameLocation(gameId, req.userId, courtId);

    if (!result.success) {
      res
        .status(gameEditErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
    res.status(200).json({ game: result.game });
  } catch (e) {
    if (e instanceof Prisma.PrismaClientKnownRequestError) {
      if (e.code === "P2003") {
        res.status(404).json({ error: "Court not found" });
        return;
      }
      if (e.code === "P2025") {
        res.status(404).json({ error: "Game not found" });
        return;
      }
    }
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

gamesRouter.post("/score", async (req: Request, res: Response) => {
  const parsed = reportScoreSchema.safeParse(req.body);

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
    const { gameId, rounds } = parsed.data;
    const result = await reportScore(gameId, req.userId, rounds);

    if (!result.success) {
      res
        .status(gameEditErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
    res.status(200).json({ game: result.game, rounds: result.rounds });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});
