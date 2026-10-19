// Runs matchmaking.service to find new matches.
import cron, { type ScheduledTask } from 'node-cron';
import { runMatchmaking } from '../matchmaking/matchmaking.service';

const EVERY_30_SECONDS = '*/30 * * * * *';

export function startMatchmakingJob(): ScheduledTask {
  return cron.schedule(
    EVERY_30_SECONDS,
    async () => {
      try {
        const matches = await runMatchmaking();
        if (matches.length > 0) console.log(`Matchmaking found ${matches.length} match(es): `, matches);
      } catch (err) {
        console.error('Matchmaking job failed', err);
      }
    },
    // Prevent one run starting while another is still running
    { noOverlap: true },
  );
}
