import type { ScheduledTask } from 'node-cron';

import { startMatchDeadlineJob } from '../../src/jobs/matchDeadline.job';
import { cancelUnansweredMatches } from '../../src/matchmaking/matchmaking.service';

jest.mock('../../src/matchmaking/matchmaking.service', () => ({
  cancelUnansweredMatches: jest.fn(),
}));

// Interface startMatchDeadlineJob()
describe('Mocked: startMatchDeadlineJob', () => {
  let task: ScheduledTask;

  beforeEach(() => {
    jest.useFakeTimers();
    jest.mocked(cancelUnansweredMatches).mockReset();
  });

  afterEach(async () => {
    await task.stop();
    jest.useRealTimers();
  });

  // Input: job started, 2 minutes pass
  // Expected behavior: unanswered matches are checked once a minute
  // Expected output: cancelUnansweredMatches called twice
  test('Runs every minute', async () => {
    jest.mocked(cancelUnansweredMatches).mockResolvedValue(0);
    task = startMatchDeadlineJob();

    await jest.advanceTimersByTimeAsync(120_000);

    expect(cancelUnansweredMatches).toHaveBeenCalledTimes(2);
  });

  // Input: a run rejects, then the next tick fires
  // Expected behavior: the error is logged and the job keeps running
  // Expected output: cancelUnansweredMatches called twice, error logged
  test('Keeps running after a failed run', async () => {
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => undefined);
    jest.mocked(cancelUnansweredMatches).mockRejectedValueOnce(new Error('db down')).mockResolvedValue(0);
    task = startMatchDeadlineJob();

    await jest.advanceTimersByTimeAsync(120_000);

    expect(cancelUnansweredMatches).toHaveBeenCalledTimes(2);
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });
});
