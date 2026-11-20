import type { ScheduledTask } from "node-cron";
import { prisma } from "../../src/db";
import { Format, GameSource, GameStatus } from "../../src/generated/prisma/enums";
import { startMatchDeadlineJob } from "../../src/jobs/matchDeadline.job";
import { createTestUsers, deleteTestUsers, type TestUser } from "../realDb";

// The match deadline job against a real database. The test starts the real job and runs it once now with
// execute(), instead of waiting for its schedule, with its own new users. The matchmaking job's test is in
// matchmaking.test.ts, with the other tests that run matchmaking.
process.env.JWT_SECRET = "test-jwt-secret";
jest.setTimeout(60_000);

const MINUTE_MS = 60 * 1000;
let users: TestUser[] = [];
let task: ScheduledTask | undefined;

afterEach(async () => {
  await task?.stop();
  task = undefined;
  await deleteTestUsers(users);
  users = [];
});

afterAll(async () => {
  await prisma.$disconnect();
});

/** A matched singles game between the two users, starting in the given number of minutes, that nobody accepted */
function unansweredMatch(startsInMinutes: number, courtId: number) {
  return prisma.game.create({
    data: {
      format: Format.SINGLES,
      status: GameStatus.OPEN,
      startsAt: new Date(Date.now() + startsInMinutes * MINUTE_MS),
      courtId,
      gameSource: GameSource.MATCHED,
      players: { create: users.map(({ id }, index) => ({ userId: id, team: index + 1 })) },
    },
    select: { id: true },
  });
}

const statusOf = (gameId: string) =>
  prisma.game.findUniqueOrThrow({ where: { id: gameId }, select: { status: true } }).then(({ status }) => status);

describe("Unmocked: match deadline job", () => {
  // Input: two matches nobody accepted, one starting in 20 minutes and one tomorrow; the job runs
  // Expected behavior: the match past its answer deadline (30 minutes before the start) is cancelled; the other
  // still waits for answers
  // Expected output: CANCELLED, then OPEN
  test("Cancels matches not accepted 30 minutes before they start", async () => {
    users = await createTestUsers("deadline", 2);
    const court = await prisma.court.findFirstOrThrow({ select: { id: true } });
    const soon = await unansweredMatch(20, court.id);
    const tomorrow = await unansweredMatch(24 * 60, court.id);

    task = startMatchDeadlineJob();
    await task.execute();

    expect(await statusOf(soon.id)).toBe(GameStatus.CANCELLED);
    expect(await statusOf(tomorrow.id)).toBe(GameStatus.OPEN);
  });
});
