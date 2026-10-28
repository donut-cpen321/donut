import { NextFunction, Router, type Request, type Response } from "express";
import { z } from "zod";
import { requireAuth } from "../auth/requireAuth";
import { Prisma } from "../generated/prisma/client";
import { friendRequestSchema, friendUserIdSchema } from "./friends.schemas";
import {
  acceptRequest,
  FriendRequestError,
  getFriendRequests,
  getFriends,
  removeFriend,
  sendFriendRequest,
} from "./friends.service";

const friendRequestErrorResponses = {
  SELF_REQUEST: { status: 400, message: "Cannot friend yourself" },
  ALREADY_FRIENDS: { status: 409, message: "Already friends" },
  REQUEST_ALREADY_SENT: { status: 409, message: "Request already sent" },
} satisfies Record<FriendRequestError, { status: number; message: string }>;

export const friendsRouter = Router();

friendsRouter.use((req: Request, res: Response, next: NextFunction) => {
  requireAuth(req, res, next);
});

friendsRouter.get("/", async (req: Request, res: Response) => {
  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const friends = await getFriends(req.userId);
    res.status(200).json({ friends });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

friendsRouter.delete("/", async (req: Request, res: Response) => {
  const parsedUserId = friendUserIdSchema.safeParse(req.query.userId);

  if (!parsedUserId.success) {
    res.status(400).json({
      error: "Invalid request query",
      details: z.flattenError(parsedUserId.error),
    });
    return;
  }

  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const removed = await removeFriend(req.userId, parsedUserId.data);

    if (removed) {
      res.sendStatus(204);
    } else {
      res.status(404).json({ error: "Friendship not found" });
    }
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

friendsRouter.get("/requests", async (req: Request, res: Response) => {
  if (!req.userId) {
    res.sendStatus(401);
    return;
  }

  try {
    const requests = await getFriendRequests(req.userId);
    res.status(200).json(requests);
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

friendsRouter.post("/requests", async (req: Request, res: Response) => {
  const parsed = friendRequestSchema.safeParse(req.body);

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
    const result = await sendFriendRequest(req.userId, parsed.data.userId);

    if (result.success) {
      res.status(201).json({ status: result.status });
    } else {
      const { status, message } = friendRequestErrorResponses[result.error];
      res.status(status).json({ error: message, code: result.error });
    }
  } catch (e) {
    if (e instanceof Prisma.PrismaClientKnownRequestError) {
      // Both users sent a request at the same time
      if (e.code === "P2002") {
        res.status(409).json({ error: "Request already exists" });
        return;
      }
      if (e.code === "P2003") {
        res.status(404).json({ error: "User not found" });
        return;
      }
    }
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});

friendsRouter.post("/requests/accept", async (req: Request, res: Response) => {
  const parsed = friendRequestSchema.safeParse(req.body);

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
    const accepted = await acceptRequest(req.userId, parsed.data.userId);

    if (accepted) {
      res.sendStatus(204);
    } else {
      res.status(404).json({ error: "Friend request not found" });
    }
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: "Internal server error" });
  }
});
