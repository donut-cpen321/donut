import { createApp } from "../../src/app";
import { friendStreams } from "../../src/friends/friends.events";
import { gameListStreams, gameStreams } from "../../src/games/games.events";
import { startMatchDeadlineJob } from "../../src/jobs/matchDeadline.job";
import { startMatchmakingJob } from "../../src/jobs/matchmaking.job";
import { queueStreams } from "../../src/matchmaking/matchmaking.events";
import { tournamentStreams } from "../../src/tournaments/tournaments.events";

// The server's entry point: the app, the two jobs and the database are mocked, so starting it opens no port, and
// its signal handlers are captured instead of being added to the test process. It starts once, in beforeAll; each
// test then sends it a signal.
jest.mock("../../src/app", () => ({ createApp: jest.fn() }));
jest.mock("../../src/jobs/matchmaking.job", () => ({ startMatchmakingJob: jest.fn() }));
jest.mock("../../src/jobs/matchDeadline.job", () => ({ startMatchDeadlineJob: jest.fn() }));
jest.mock("../../src/db", () => ({ prisma: { $disconnect: jest.fn() } }));

type Handler = () => void;

const close = jest.fn();
const listen = jest.fn();
const matchmakingStop = jest.fn();
const deadlineStop = jest.fn();
const handlers = new Map<string, Handler>();
const streams = [gameStreams, friendStreams, gameListStreams, tournamentStreams, queueStreams];
let closeAll: jest.SpyInstance[] = [];
const db = jest.requireMock<{ prisma: { $disconnect: jest.Mock } }>("../../src/db").prisma;

// Lets the shutdown, which the signal handler starts without waiting for it, finish
const settle = () => new Promise((resolve) => setImmediate(resolve));

beforeAll(() => {
  listen.mockImplementation((_port: number, ready: () => void) => {
    ready();
    return { close };
  });
  jest.mocked(createApp).mockReturnValue({ listen } as unknown as ReturnType<typeof createApp>);
  jest.mocked(startMatchmakingJob).mockReturnValue({ stop: matchmakingStop } as never);
  jest.mocked(startMatchDeadlineJob).mockReturnValue({ stop: deadlineStop } as never);
  const once = jest.spyOn(process, "once").mockImplementation((signal: string | symbol, handler: Handler) => {
    handlers.set(String(signal), handler);
    return process;
  });
  const info = jest.spyOn(console, "info").mockImplementation(() => undefined);
  jest.requireActual("../../src/index");
  once.mockRestore();
  info.mockRestore();
});

beforeEach(() => {
  matchmakingStop.mockClear();
  deadlineStop.mockClear();
  close.mockReset();
  db.$disconnect.mockClear();
  closeAll = streams.map((stream) => jest.spyOn(stream, "closeAll"));
});

afterEach(() => {
  jest.restoreAllMocks();
  process.exitCode = undefined;
});

// Module index.ts
describe("Mocked: server entry point", () => {
  // Input: the server starts
  // Expected behavior: the app listens on PORT, both jobs start, and SIGINT and SIGTERM get a handler
  test("Starts the app and both jobs", () => {
    expect(listen).toHaveBeenCalledWith(expect.any(Number), expect.any(Function));
    expect(startMatchmakingJob).toHaveBeenCalledTimes(1);
    expect(startMatchDeadlineJob).toHaveBeenCalledTimes(1);
    expect([...handlers.keys()]).toEqual(["SIGINT", "SIGTERM"]);
  });

  // Input: SIGTERM, and the server closes cleanly
  // Expected behavior: the jobs stop, every event stream closes, then the server and the database
  // Expected output: no error, exit code unset
  test("Shuts down everything on SIGTERM", async () => {
    close.mockImplementation((done: (err?: Error) => void) => {
      done();
    });

    handlers.get("SIGTERM")?.();
    await settle();

    expect(matchmakingStop).toHaveBeenCalled();
    expect(deadlineStop).toHaveBeenCalled();
    for (const spy of closeAll) expect(spy).toHaveBeenCalled();
    expect(close).toHaveBeenCalled();
    expect(db.$disconnect).toHaveBeenCalled();
    expect(process.exitCode).toBeUndefined();
  });

  // Input: SIGINT, and the server fails to close
  // Expected behavior: the failure is logged, the database is left, and the process will exit with code 1
  test("Reports a failed shutdown", async () => {
    const logged = jest.spyOn(console, "error").mockImplementation(() => undefined);
    close.mockImplementation((done: (err?: Error) => void) => {
      done(new Error("close failed"));
    });

    handlers.get("SIGINT")?.();
    await settle();

    expect(logged).toHaveBeenCalledWith("Shutdown failed", expect.any(Error));
    expect(db.$disconnect).not.toHaveBeenCalled();
    expect(process.exitCode).toBe(1);
  });
});
