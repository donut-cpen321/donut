import { createRefreshToken, deleteRefreshTokenByHash, rotateRefreshToken } from './auth.repository';
import { generateNewRefreshToken, hashRefreshToken, signAccessToken } from './tokens';

/**
 * Issues a new access token and refresh token for a user and stores the refresh token's hash
 * @param userId database id for the user
 * @returns the tokens to send to the frontend
 */
export async function issueTokens(userId: string): Promise<{ accessToken: string; refreshToken: string }> {
  const refreshToken = generateNewRefreshToken();
  const accessToken = signAccessToken(userId);
  await createRefreshToken({ userId, tokenHash: refreshToken.hash, expiresAt: refreshToken.expiresAt });

  return { accessToken, refreshToken: refreshToken.raw };
}

/**
 * Trades a refresh token for a new access token and refresh token.
 * The old refresh token is deleted, so each one only works once.
 * @param refreshToken the token given at login or by the last refresh
 * @returns the replacement tokens, or null if the supplied token is invalid
 */
export async function refreshTokens(refreshToken: string) {
  const oldRefreshTokenHash = hashRefreshToken(refreshToken);
  const newRefreshToken = generateNewRefreshToken();
  const userId = await rotateRefreshToken(oldRefreshTokenHash, {
    tokenHash: newRefreshToken.hash,
    expiresAt: newRefreshToken.expiresAt,
  });
  if (!userId) {
    return null;
  }

  const accessToken = signAccessToken(userId);
  return { accessToken, refreshToken: newRefreshToken.raw };
}

/**
 * Logs the user out on this device by deleting its refresh token
 * @param refreshToken the token to delete
 */
export async function logout(refreshToken: string): Promise<void> {
  const refreshTokenHash = hashRefreshToken(refreshToken);
  await deleteRefreshTokenByHash(refreshTokenHash);
}
