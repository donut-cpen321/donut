// Games, players and requests shared by the mocked /games tests (games.*.test.ts)
import { Game, Prisma } from "../../src/generated/prisma/client";
import { Format, GameSource, GameStatus } from "../../src/generated/prisma/enums";
import type { GameDetails } from "../../src/games/games.repository";
import { signAccessToken } from "../../src/auth/tokens";

// Every /games route is behind requireAuth, so requests need a valid token
process.env.JWT_SECRET = "test-jwt-secret";
export const userId = "user-123";
export const authHeader = `Bearer ${signAccessToken(userId)}`;

export const futureStartsAt = () =>
  new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();

export const validBody = () => ({
  format: Format.SINGLES,
  startsAt: futureStartsAt(),
  courtId: 1,
  gameSource: GameSource.SCHEDULED,
});

export const gameDetails = (overrides: Partial<GameDetails> = {}): GameDetails => ({
  id: "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b",
  format: Format.SINGLES,
  startsAt: new Date(futureStartsAt()),
  courtId: 1,
  gameSource: GameSource.SCHEDULED,
  status: GameStatus.OPEN,
  createdAt: new Date(),
  court: { id: 1, name: "Kitsilano Beach Park", latitude: 49.27, longitude: -123.15 },
  players: [
    {
      id: userId,
      username: "ace",
      name: "Ace Player",
      rating: 1500,
      photoUrl: null,
      team: 1,
      acceptedAt: new Date(),
      scoreConfirmedAt: null,
      ratingBefore: null,
      ratingAfter: null,
    },
  ],
  rounds: [],
  equipment: [],
  tournament: null,
  scoreProposal: null,
  ...overrides,
});

export const prismaError = (code: string) =>
  new Prisma.PrismaClientKnownRequestError(`Prisma error ${code}`, {
    code,
    clientVersion: Prisma.prismaVersion.client,
  });

export const joinGameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

// A scheduled game that players can still join
export const joinableGame = (overrides: Partial<Game> = {}): Game => ({
  id: joinGameId,
  format: Format.SINGLES,
  startsAt: new Date(futureStartsAt()),
  courtId: 1,
  gameSource: GameSource.SCHEDULED,
  status: GameStatus.OPEN,
  createdAt: new Date(),
  ...overrides,
});

export const editGameId = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

export const existingGame = (overrides: Partial<Game> = {}): Game => ({
  id: editGameId,
  format: Format.SINGLES,
  startsAt: new Date(futureStartsAt()),
  courtId: 1,
  gameSource: GameSource.SCHEDULED,
  status: GameStatus.OPEN,
  createdAt: new Date(),
  ...overrides,
});
