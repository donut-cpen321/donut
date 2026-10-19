import { createApp } from './app';
import { env } from './config/env';
import { startMatchmakingJob } from './jobs/matchmaking.job';

const app = createApp();

const server = app.listen(env.port, () => {
  console.log(`Server listening on port ${env.port}`);
});

const matchmakingJob = startMatchmakingJob();

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    void matchmakingJob.stop();
    server.close(() => {
      process.exit(0);
    });
  });
}
