// Runs matchmaking.service to cancel matches not accepted 30 minutes before start.
import cron, { type ScheduledTask } from 'node-cron';
import { cancelUnansweredMatches } from '../matchmaking/matchmaking.service';

const EVERY_MINUTE = '* * * * *';

/**
 * Starts the job that cancels unanswered matches every minute.
 * @returns Scheduled task for the match deadline job.
 */
export function startMatchDeadlineJob(): ScheduledTask {
  return cron.schedule(
    EVERY_MINUTE,
    async () => {
      try {
        const cancelled = await cancelUnansweredMatches();
        if (cancelled > 0) console.info(`Cancelled ${cancelled} unanswered match(es)`);
      } catch (err) {
        console.error('Match deadline job failed', err);
      }
    },
    // Prevent one run starting while another is still running
    { noOverlap: true },
  );
}
