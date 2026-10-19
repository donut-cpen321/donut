import type { ScheduledTask } from 'node-cron';

import { startMatchmakingJob } from '../../src/jobs/matchmaking.job';
import { runMatchmaking } from '../../src/matchmaking/matchmaking.service';

jest.mock('../../src/matchmaking/matchmaking.service', () => ({
  runMatchmaking: jest.fn(),
}));

// Interface startMatchmakingJob()
describe('Mocked: startMatchmakingJob', () => {
  let task: ScheduledTask;

  beforeEach(() => {
    jest.useFakeTimers();
    jest.mocked(runMatchmaking).mockReset();
  });

  afterEach(() => {
    void task.stop();
    jest.useRealTimers();
  });

  // Input: job started, 60 seconds pass
  // Expected behavior: matchmaking runs once every 30 seconds
  // Expected output: runMatchmaking called twice
  test('Runs every 30 seconds', async () => {
    jest.mocked(runMatchmaking).mockResolvedValue([]);
    task = startMatchmakingJob();

    await jest.advanceTimersByTimeAsync(60_000);

    expect(runMatchmaking).toHaveBeenCalledTimes(2);
  });

  // Input: first run never finishes, another tick fires
  // Expected behavior: the second tick is skipped instead of overlapping
  // Expected output: runMatchmaking called once
  test('Skips a tick while the previous run is still going', async () => {
    const consoleWarn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    jest.mocked(runMatchmaking).mockReturnValue(new Promise(() => {}));
    task = startMatchmakingJob();

    await jest.advanceTimersByTimeAsync(60_000);

    expect(runMatchmaking).toHaveBeenCalledTimes(1);
    expect(consoleWarn).toHaveBeenCalled(); // node-cron will log that an overlap occurred
    consoleWarn.mockRestore();
  });

  // Input: a run rejects, then the next tick fires
  // Expected behavior: the error is logged and the job keeps running
  // Expected output: runMatchmaking called twice, error logged
  test('Keeps running after a failed run', async () => {
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {});
    jest.mocked(runMatchmaking).mockRejectedValueOnce(new Error('db down')).mockResolvedValue([]);
    task = startMatchmakingJob();

    await jest.advanceTimersByTimeAsync(60_000);

    expect(runMatchmaking).toHaveBeenCalledTimes(2);
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });
});
