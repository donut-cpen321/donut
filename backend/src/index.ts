import { createApp } from './app';
import { env } from './config/env';
import { prisma } from './db';
import { startMatchmakingJob } from './jobs/matchmaking.job';
import { startMatchDeadlineJob } from './jobs/matchDeadline.job';

const app = createApp();

const server = app.listen(env.port, () => {
  console.info(`Server listening on port ${env.port}`);
});

const matchmakingJob = startMatchmakingJob();
const matchDeadlineJob = startMatchDeadlineJob();

// Stops everything that keeps the process alive, so Node exits on its own once it is done
async function shutdown(): Promise<void> {
  await matchmakingJob.stop();
  await matchDeadlineJob.stop();
  await new Promise<void>((resolve, reject) => {
    server.close((err) => {
      if (err) {
        reject(err);
      } else {
        resolve();
      }
    });
  });
  await prisma.$disconnect();
}

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.once(signal, () => {
    shutdown().catch((err: unknown) => {
      console.error('Shutdown failed', err);
      process.exitCode = 1;
    });
  });
}
