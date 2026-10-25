import { createHash, randomBytes } from 'node:crypto';
import jwt from 'jsonwebtoken';

// 1 hour
const ACCESS_TOKEN_TTL_SECONDS = 60 * 60;
// 30 days
const REFRESH_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * Gets the JWT secret from the environment variables
 * @returns the JWT secret
 */
function getJwtSecret(): string {
  const secret = process.env.JWT_SECRET;
  if (!secret) {
    throw new Error('Missing env.JWT_SECRET, please add a random secret to .env');
  }
  return secret;
}

/**
 * Signs an access token that the frontend sends as `Authorization: Bearer <token>`
 * @param userId databse id for the user
 * @returns the signed JWT
 */
export function signAccessToken(userId: string): string {
  const secret = getJwtSecret();
  
  // Builds a JWT token: header (alg) + payload (sub + iat + exp) + signature
  return jwt.sign({}, secret, {
    subject: userId,
    expiresIn: ACCESS_TOKEN_TTL_SECONDS,
    algorithm: 'HS256',
  });
}

/**
 * Verifies an access token's signature and expiry
 * @param token the JWT sent by the frontend
 * @returns the user id the token was issued for, null if the token is invalid or expired
 */
export function verifyAccessToken(token: string): string | null {
  const secret = getJwtSecret();

  try {
    const payload = jwt.verify(token, secret, { algorithms: ['HS256'] });
    return typeof payload === 'object' && payload.sub ? payload.sub : null;
  } catch {
    return null;
  }
}

/**
 * Hashes a refresh token to store in the database
 * @param raw the refresh token given to the frontend
 * @returns hex SHA-256 hash
 */
export function hashRefreshToken(raw: string): string {
  return createHash('sha256').update(raw).digest('hex');
}

/**
 * Generates a new random refresh token
 * @returns the raw token for the frontend
 */
export function generateNewRefreshToken(): { raw: string; hash: string; expiresAt: Date } {
  const raw = randomBytes(32).toString('base64url');
  return {
    raw,
    hash: hashRefreshToken(raw),
    expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_MS),
  };
}
