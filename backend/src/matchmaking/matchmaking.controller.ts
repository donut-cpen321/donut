import { Router, type Request, type Response } from "express";
import { z } from "zod";
import { requireAuth } from "../auth/requireAuth";
import { Prisma } from "../generated/prisma/client";
import { joinQueueSchema } from "./matchmaking.schemas";
import { ActiveMatchError, getQueueEntry, joinQueue, leaveQueue } from "./matchmaking.service";

export const matchmakingRouter = Router();

matchmakingRouter.use(requireAuth);

matchmakingRouter.post("/", async (req: Request, res: Response) => {
  const parsedBody = joinQueueSchema.safeParse(req.body);

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
    const request = await joinQueue(req.userId, parsedBody.data);
    res.status(201).json({ request });
  } catch (e) {
    // 403, not 409: the app treats 409 as already being in the queue
    if (e instanceof ActiveMatchError) {
      res.status(403).json({ error: "Already has a match" });
      return;
    }
    // userId is the queue's primary key, so a second entry for the same player fails
    if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === "P2002") {
      res.status(409).json({ error: "Already in the queue" });
      return;
    }
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

matchmakingRouter.delete("/", async (req: Request, res: Response) => {
  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    await leaveQueue(req.userId);
    res.sendStatus(204);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

matchmakingRouter.get("/", async (req: Request, res: Response) => {
  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const request = await getQueueEntry(req.userId);
    if (request === null) {
      res.status(404).json({ error: "Not in the queue" });
      return;
    }
    res.status(200).json({ request });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});
