import { Router, type Request, type Response } from "express";
import { z } from "zod";
import { requireAuth } from "../auth/requireAuth";
import { Prisma } from "../generated/prisma/client";
import {
  createTournamentSchema,
  tournamentIdBodySchema,
  tournamentIdSchema,
} from "./tournaments.schemas";
import {
  cancelTournament,
  type CancelTournamentError,
  cancelTournamentErrors,
  getTournament,
  getTournamentFeed,
  getUserTournaments,
  joinTournament,
  type JoinTournamentError,
  joinTournamentErrors,
  leaveTournament,
  type LeaveTournamentError,
  leaveTournamentErrors,
  newTournament,
} from "./tournaments.service";

export const tournamentsRouter = Router();

const joinTournamentErrorStatus: Record<JoinTournamentError, number> = {
  [joinTournamentErrors.NotFound]: 404,
  [joinTournamentErrors.NotOpen]: 409,
  [joinTournamentErrors.Full]: 409,
  [joinTournamentErrors.AlreadyRegistered]: 409,
};

const leaveTournamentErrorStatus: Record<LeaveTournamentError, number> = {
  [leaveTournamentErrors.NotFound]: 404,
  [leaveTournamentErrors.NotRegistered]: 404,
  [leaveTournamentErrors.NotOpen]: 409,
  [leaveTournamentErrors.Creator]: 409,
};

const cancelTournamentErrorStatus: Record<CancelTournamentError, number> = {
  [cancelTournamentErrors.NotFound]: 404,
  [cancelTournamentErrors.NotCreator]: 403,
  [cancelTournamentErrors.NotOpen]: 409,
};

tournamentsRouter.use(requireAuth);

tournamentsRouter.post("/", async (req: Request, res: Response) => {
  const parsedBody = createTournamentSchema.safeParse(req.body);

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
    const tournament = await newTournament(parsedBody.data, req.userId);
    res.status(201).json({ tournament });
  } catch (e) {
    if (
      e instanceof Prisma.PrismaClientKnownRequestError &&
      e.code === "P2003"
    ) {
      res.status(404).json({ error: "Court not found" });
      return;
    }
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

tournamentsRouter.get("/", async (req: Request, res: Response) => {
  const parsedTournamentId = tournamentIdSchema.safeParse(
    req.query.tournamentId,
  );

  if (!parsedTournamentId.success) {
    res.status(400).json({
      error: "Invalid request query",
      details: z.flattenError(parsedTournamentId.error),
    });
    return;
  }

  try {
    const tournament = await getTournament(parsedTournamentId.data);
    if (tournament === null) {
      res.status(404).json({ error: "Tournament not found" });
      return;
    }
    res.status(200).json({ tournament });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

tournamentsRouter.get("/feed", async (_req: Request, res: Response) => {
  try {
    const tournaments = await getTournamentFeed();
    res.status(200).json({ tournaments });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

tournamentsRouter.get("/me", async (req: Request, res: Response) => {
  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const tournaments = await getUserTournaments(req.userId);
    res.status(200).json({ tournaments });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

tournamentsRouter.post("/join", async (req: Request, res: Response) => {
  const parsed = tournamentIdBodySchema.safeParse(req.body);

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
    const result = await joinTournament(parsed.data.tournamentId, req.userId);

    if (!result.success) {
      res
        .status(joinTournamentErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
    res.sendStatus(204);
  } catch (e) {
    if (
      e instanceof Prisma.PrismaClientKnownRequestError &&
      e.code === "P2003"
    ) {
      res.status(404).json({ error: "User not found" });
      return;
    }
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

tournamentsRouter.post("/leave", async (req: Request, res: Response) => {
  const parsed = tournamentIdBodySchema.safeParse(req.body);

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
    const result = await leaveTournament(parsed.data.tournamentId, req.userId);

    if (!result.success) {
      res
        .status(leaveTournamentErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
    res.sendStatus(204);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

tournamentsRouter.delete("/", async (req: Request, res: Response) => {
  const parsedTournamentId = tournamentIdSchema.safeParse(
    req.query.tournamentId,
  );

  if (!parsedTournamentId.success) {
    res.status(400).json({
      error: "Invalid request query",
      details: z.flattenError(parsedTournamentId.error),
    });
    return;
  }

  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const result = await cancelTournament(parsedTournamentId.data, req.userId);

    if (!result.success) {
      res
        .status(cancelTournamentErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
    res.sendStatus(204);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});
