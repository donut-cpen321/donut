// Prisma queries for users, courts and availabilities. Only file in users/ that imports db.
import { Prisma } from '../generated/prisma/client';
import { prisma } from '../db';

const loginUserSelect = {
  id: true,
  username: true,
  name: true,
  email: true,
  createdAt: true,
} satisfies Prisma.UserSelect;


/* 
 * Finds a user using their googleSub provided by the token validation
 */
export async function findUserByGoogleSub(googleSub: string) {
  return prisma.user.findUnique({
    where: { googleSub },
    select: loginUserSelect,
  });
}

/* 
 * Creates a new user in the database following the prisma schema.
 */
export async function createUser(input: {
  googleSub: string;
  username: string;
  name: string;
  email: string;
}) {
  try {
    return await prisma.user.create({
      data: input,
      select: loginUserSelect,
    });
  } catch (error) {
    return null;
  }
}
