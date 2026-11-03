import { Router, type Request, type Response } from "express";
import { z } from "zod";
import { requireAuth } from "../auth/requireAuth";
import {
  acceptMatchSchema,
  changeGameLocationSchema,
  changeGameTimeSchema,
  createGameSchema,
  gameIdSchema,
  inviteFriendSchema,
  joinGameSchema,
  leaveGameSchema,
  reportScoreSchema,
} from "./games.schemas";
import { Prisma } from "../generated/prisma/client";
import {
  acceptMatch,
  type AcceptMatchError,
  acceptMatchErrors,
  cancelGame,
  type CancelGameError,
  cancelGameErrors,
  changeGameLocation,
  changeGameTime,
  type GameEditError,
  gameEditErrors,
  getFeed,
  getGame,
  getUserGames,
  getUserHistory,
  inviteFriend,
  type InviteFriendError,
  inviteFriendErrors,
  joinGame,
  type JoinGameError,
  joinGameErrors,
  leaveGame,
  type LeaveGameError,
  leaveGameErrors,
  newGame,
  reportScore,
} from "./games.service";

export const gamesRouter = Router();

const gameEditErrorStatus: Record<GameEditError, number> = {
  [gameEditErrors.NotFound]: 404,
  [gameEditErrors.NotInGame]: 403,
  [gameEditErrors.Closed]: 409,
  [gameEditErrors.IncompleteTeams]: 409,
  [gameEditErrors.Matched]: 409,
};

const joinGameErrorStatus: Record<JoinGameError, number> = {
  [joinGameErrors.NotFound]: 404,
  [joinGameErrors.NotJoinable]: 409,
  [joinGameErrors.TeamFull]: 409,
};

const leaveGameErrorStatus: Record<LeaveGameError, number> = {
  [leaveGameErrors.NotFound]: 404,
  [leaveGameErrors.TournamentGame]: 409,
  [leaveGameErrors.Closed]: 409,
  [leaveGameErrors.TooLate]: 409,
  [leaveGameErrors.Matched]: 409,
};

const cancelGameErrorStatus: Record<CancelGameError, number> = {
  [cancelGameErrors.NotFound]: 404,
  [cancelGameErrors.NotInGame]: 403,
  [cancelGameErrors.TournamentGame]: 409,
  [cancelGameErrors.Closed]: 409,
};

const acceptMatchErrorStatus: Record<AcceptMatchError, number> = {
  [acceptMatchErrors.NotFound]: 404,
  [acceptMatchErrors.NotInGame]: 403,
  [acceptMatchErrors.NotPending]: 409,
};

const inviteFriendErrorStatus: Record<InviteFriendError, number> = {
  [inviteFriendErrors.NotFound]: 404,
  [inviteFriendErrors.NotInGame]: 403,
  [inviteFriendErrors.NotJoinable]: 409,
  [inviteFriendErrors.NotFriends]: 403,
  [inviteFriendErrors.AlreadyInGame]: 409,
};

gamesRouter.use(requireAuth);

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
    const game = await getGame(parsedGameId.data);
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

gamesRouter.get("/feed", async (_req: Request, res: Response) => {
  try {
    const games = await getFeed();
    res.status(200).json({ games });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

gamesRouter.get("/me", async (req: Request, res: Response) => {
  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const games = await getUserGames(req.userId);
    res.status(200).json({ games });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

gamesRouter.get("/history", async (req: Request, res: Response) => {
  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const games = await getUserHistory(req.userId);
    res.status(200).json({ games });
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
    const result = await cancelGame(parsedGameId.data, req.userId);

    if (!result.success) {
      res
        .status(cancelGameErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
    res.sendStatus(204);
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
    const result = await joinGame(gameId, req.userId, team);

    if (!result.success) {
      res
        .status(joinGameErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
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
    const result = await leaveGame(gameId, req.userId);

    if (!result.success) {
      res
        .status(leaveGameErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
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

gamesRouter.post("/accept", async (req: Request, res: Response) => {
  const parsed = acceptMatchSchema.safeParse(req.body);

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
    const result = await acceptMatch(parsed.data.gameId, req.userId);

    if (!result.success) {
      res
        .status(acceptMatchErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
    res.sendStatus(204);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

gamesRouter.post("/invite", async (req: Request, res: Response) => {
  const parsed = inviteFriendSchema.safeParse(req.body);

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
    const { gameId, userId } = parsed.data;
    const result = await inviteFriend(gameId, req.userId, userId);

    if (!result.success) {
      res
        .status(inviteFriendErrorStatus[result.error])
        .json({ error: result.error });
      return;
    }
    res.status(200).json({ notified: result.notified });
  } catch (e) {
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
    res.status(200).json({
      game: result.game,
      rounds: result.rounds,
      ratingChanges: result.ratingChanges,
    });
  } catch (e) {
    // Two games sharing a player finished at the same moment and Postgres aborted one of them
    if (
      e instanceof Prisma.PrismaClientKnownRequestError &&
      e.code === "P2034"
    ) {
      res
        .status(409)
        .json({ error: "The score could not be saved, please try again" });
      return;
    }
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});
