import { type Request, type Response } from 'express';
import { createRefreshToken, deleteRefreshTokenByHash } from './auth.repository';
import { generateNewRefreshToken, hashRefreshToken, signAccessToken } from './tokens';

/**
 * Reads the refresh token from the request body
 * @param req body sent by the frontend, so refreshToken can be any JSON value
 * @returns the token, null if it is missing or not a string
 */
function getRefreshToken(req: Request): string | null {
  const refreshToken: unknown = req.body?.refreshToken;
  return typeof refreshToken === 'string' && refreshToken ? refreshToken : null;
}

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
 * @param req contains the refresh token given at login or by the last refresh
 * @param res
 */
export async function refreshTokens(req: Request, res: Response): Promise<void> {
  const refreshToken = getRefreshToken(req);
  if (!refreshToken) {
    res.status(400).json({ error: 'A refresh token is required' });
    return;
  }

  const oldRefreshTokenHash = hashRefreshToken(refreshToken);
  const oldRefreshToken = await deleteRefreshTokenByHash(oldRefreshTokenHash);
  if (!oldRefreshToken || oldRefreshToken.expiresAt <= new Date()) {
    res.status(401).json({ error: 'Invalid or expired refresh token' });
    return;
  }

  const newTokens = await issueTokens(oldRefreshToken.userId);
  res.status(200).json(newTokens);
}

/**
 * Logs the user out on this device by deleting its refresh token
 * @param req contains the refresh token to delete
 * @param res 204 even if the token was already gone
 */
export async function logout(req: Request, res: Response): Promise<void> {
  const refreshToken = getRefreshToken(req);
  if (!refreshToken) {
    res.status(400).json({ error: 'A refresh token is required' });
    return;
  }

  const refreshTokenHash = hashRefreshToken(refreshToken);
  await deleteRefreshTokenByHash(refreshTokenHash);
  res.status(204).send();
}
