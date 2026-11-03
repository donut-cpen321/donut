// Runs matchmaking.service to find new matches.
import cron, { type ScheduledTask } from 'node-cron';
import { runMatchmaking } from '../matchmaking/matchmaking.service';

const EVERY_10_SECONDS = '*/10 * * * * *';

/**
 * Starts the matchmaking job to find new matches every 10 seconds.
 * @returns Scheduled task for the matchmaking job.
 */
export function startMatchmakingJob(): ScheduledTask {
  return cron.schedule(
    EVERY_10_SECONDS,
    async () => {
      try {
        const matches = await runMatchmaking();
        if (matches.length > 0) console.info(`Matchmaking found ${matches.length} match(es): `, matches);
      } catch (err) {
        console.error('Matchmaking job failed', err);
      }
    },
    // Prevent one run starting while another is still running
    { noOverlap: true },
  );
}
