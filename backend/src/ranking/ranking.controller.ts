import { Router, type Request, type Response } from "express";
import { z } from "zod";
import { requireAuth } from "../auth/requireAuth";
import { leaderboardQuerySchema } from "./ranking.schemas";
import { getLeaderboard } from "./ranking.service";

export const leaderboardRouter = Router();

leaderboardRouter.use(requireAuth);

leaderboardRouter.get("/", async (req: Request, res: Response) => {
  const parsedQuery = leaderboardQuerySchema.safeParse(req.query);

  if (!parsedQuery.success) {
    res.status(400).json({
      error: "Invalid request query",
      details: z.flattenError(parsedQuery.error),
    });
    return;
  }

  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const result = await getLeaderboard(req.userId, parsedQuery.data.limit);
    res.status(200).json(result);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});
