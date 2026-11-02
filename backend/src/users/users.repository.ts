import { Prisma } from '../generated/prisma/client';
import { prisma } from '../db';

const loginUserSelect = {
  id: true,
  username: true,
  name: true,
  email: true,
  createdAt: true,
} satisfies Prisma.UserSelect;

// The signed-in user's own profile
const profileUserSelect = {
  ...loginUserSelect,
  rating: true,
} satisfies Prisma.UserSelect;

type LoginUser = Prisma.UserGetPayload<{ select: typeof loginUserSelect }>;
type ProfileUser = Prisma.UserGetPayload<{ select: typeof profileUserSelect }>;
type NewUserData = Pick<Prisma.UserCreateInput, 'googleSub' | 'username' | 'name' | 'email'>;


/**
 * Gets a user (if it exists) using their googleSub provided by the token validation
 * @param googleSub google's unique identifier for a user
 * @returns 
 */
export async function getUserByGoogleSub(googleSub: string): Promise<LoginUser | null> {
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
export async function createUser(userData: NewUserData): Promise<LoginUser> {
  return await prisma.user.create({
      data: userData,
      select: loginUserSelect,
  });
}

/**
 * Gets a user's own profile, including their rating
 * @param userId the user's id
 * @returns the user, or null if they do not exist
 */
export async function getUserById(userId: string): Promise<ProfileUser | null> {
  return await prisma.user.findUnique({
    where: { id: userId },
    select: profileUserSelect,
  });
}
