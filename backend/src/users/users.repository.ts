import { Prisma } from '../generated/prisma/client';
import { prisma } from '../db';

const loginUserSelect = {
  id: true,
  username: true,
  name: true,
  email: true,
  createdAt: true,
} satisfies Prisma.UserSelect;


/**
 * Gets a user (if it exists) using their googleSub provided by the token validation
 * @param googleSub google's unique identifier for a user
 * @returns 
 */
export async function getUserByGoogleSub(googleSub: string) {
  // I would like to make this typed (e.g. a user class) but am unfamiliar with ts and prisma so some help would be appreciated
  // TODO: User type/class
  return await prisma.user.findUnique({
    where: { googleSub },
    select: loginUserSelect,
  });
}

/**
 * Creates a new user in the database following the prisma schema.
 * @param userData data required by the database for a new user
 * @returns 
 */
export async function createUser(userData: {
  googleSub: string;
  username: string;
  name: string;
  email: string;
}) {
  return await prisma.user.create({
      data: userData,
      select: loginUserSelect,
  });
}
