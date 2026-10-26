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

/**
 * Replaces a refresh token with a new one in one transaction, so if saving the new token fails
 * the old one is not deleted and the user can still refresh.
 * 
 * @param oldTokenHash hash of the token sent by the frontend
 * @param newToken hash and expiry of the token that replaces it
 * @returns the token owner's id, null if the old token doesn't exist or expired
 */
export async function rotateRefreshToken(
  oldTokenHash: string,
  newToken: { tokenHash: string; expiresAt: Date },
): Promise<string | null> {
  try {
    return await prisma.$transaction(async (tx) => {
      // Delete the old token
      const oldToken = await tx.refreshToken.delete({
        where: { tokenHash: oldTokenHash },
        select: { userId: true, expiresAt: true },
      });

      // The old token is expired, so it can't be used again
      if (oldToken.expiresAt <= new Date()) {
        return null;
      }

      // Create the new token
      await tx.refreshToken.create({ data: { userId: oldToken.userId, ...newToken } });
      return oldToken.userId;
    });
  } catch (err) {
    // P2025: no token with this hash (never issued, already used, or logged out)
    if (err instanceof Prisma.PrismaClientKnownRequestError && err.code === 'P2025') {
      return null;
    }
    throw err;
  }
}
