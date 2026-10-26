import { Prisma } from '../generated/prisma/client';
import { prisma } from '../db';

type NewRefreshTokenData = Pick<Prisma.RefreshTokenUncheckedCreateInput, 'userId' | 'tokenHash' | 'expiresAt'>;

/**
 * Creates a new refresh token for a user
 * @param tokenData the user, the token's hash and when it expires
 */
export async function createRefreshToken(tokenData: NewRefreshTokenData): Promise<void> {
  await prisma.refreshToken.create({ data: tokenData });
}

/**
 * Deletes a refresh token if it exists (logout)
 * @param tokenHash hash of the token sent by the frontend
 * @returns the token's owner and expiry, null if no such token exists
 */
export async function deleteRefreshTokenByHash(
  tokenHash: string,
): Promise<{ userId: string; expiresAt: Date } | null> {
  try {
    return await prisma.refreshToken.delete({
      where: { tokenHash },
      select: { userId: true, expiresAt: true },
    });
  } catch (err) {
    // If the token doesn't exist, return null
    if (err instanceof Prisma.PrismaClientKnownRequestError && err.code === 'P2025') {
      return null;
    }
    throw err;
  }
}
