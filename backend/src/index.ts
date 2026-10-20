import { createApp } from './app';
import { env } from './config/env';
import { startMatchmakingJob } from './jobs/matchmaking.job';

const app = createApp();

const server = app.listen(env.port, () => {
  console.info(`Server listening on port ${env.port}`);
});

const matchmakingJob = startMatchmakingJob();

async function shutdown(): Promise<void> {
  await matchmakingJob.stop();
  server.close(() => {
    process.exit(0);
  });
}

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    shutdown().catch((err: unknown) => {
      console.error('Shutdown failed', err);
    });
  });
}
