import { PrismaPg } from '@prisma/adapter-pg';
import { env } from './config/env';
import { PrismaClient } from './generated/prisma/client';

// The Prisma client, only used by repositories.
export const prisma = new PrismaClient({
  adapter: new PrismaPg({ connectionString: env.databaseUrl }),
});
