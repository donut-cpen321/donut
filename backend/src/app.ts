import express, { type Express } from "express";
import { usersRouter } from "./users/users.controller";
import { gamesRouter } from "./games/games.controller";

export function createApp(): Express {
  const app = express();

  app.use(express.json());

  app.use("/user", usersRouter);

  app.use("/games", gamesRouter);

  app.get("/health", (_req, res) => {
    res.json({ status: "ok" });
  });

  app.use((_req, res) => {
    res.status(404).json({ error: "Not Found" });
  });

  return app;
}
