import jwt from 'jsonwebtoken';
import {
  hashRefreshToken,
  generateNewRefreshToken,
  signAccessToken,
  verifyAccessToken,
} from '../../src/auth/tokens';

const SECRET = 'test-jwt-secret';

// Functions signAccessToken, verifyAccessToken
describe('Unit: access tokens', () => {
  beforeEach(() => {
    process.env.JWT_SECRET = SECRET;
  });

  // Input: a token signed for user-123
  // Expected output: user-123
  test('Round trip returns the user id', () => {
    expect(verifyAccessToken(signAccessToken('user-123'))).toBe('user-123');
  });

  // Input: a freshly signed token
  // Expected behavior: expires one hour after it was issued
  test('Expires after one hour', () => {
    const token = signAccessToken('user-123');
    const payload = jwt.decode(token) as jwt.JwtPayload;
    const duration = payload.exp! - payload.iat!;

    expect(duration).toBe(60 * 60);
  });

  // Input: a token whose exp is in the past
  // Expected output: null
  test('Rejects an expired token', () => {
    const now_seconds = Math.floor(Date.now() / 1000)
    const payload = { sub: 'user-123', exp: now_seconds - 10 };
    const token = jwt.sign(payload, SECRET);

    expect(verifyAccessToken(token)).toBeNull();
  });

  // Input: a token signed with a different secret
  // Expected output: null
  test('Rejects a token signed with another secret', () => {
    const secret = 'another-secret';
    const payload = { sub: 'user-123' };
    const token = jwt.sign(payload, secret);

    expect(verifyAccessToken(token)).toBeNull();
  });

  // Input: an unsigned token with alg "none"
  // Expected output: null
  test('Rejects an unsigned token', () => {
    const payload = { sub: 'user-123' };
    const header = { alg: 'none', typ: 'JWT' };
    const encodeBase64Url = (part: object) => Buffer.from(JSON.stringify(part)).toString('base64url');

    const token = `${encodeBase64Url(header)}.${encodeBase64Url(payload)}.`;

    expect(verifyAccessToken(token)).toBeNull();
  });

  // Input: a signed token with no subject
  // Expected output: null
  test('Rejects a token without a subject', () => {
    expect(verifyAccessToken(jwt.sign({}, SECRET))).toBeNull();
  });

  // Input: a string that is not a JWT
  // Expected output: null
  test('Rejects non JWT tokens', () => {
    expect(verifyAccessToken('not-a-jwt')).toBeNull();
  });

  // Input: JWT_SECRET is not set
  // Expected behavior: throws instead of treating every token as invalid
  test('Throws when JWT_SECRET is missing', () => {
    delete process.env.JWT_SECRET;

    expect(() => signAccessToken('user-123')).toThrow('JWT_SECRET');
    expect(() => verifyAccessToken('not-a-jwt')).toThrow('JWT_SECRET');
  });
});

// Functions generateNewRefreshToken, hashRefreshToken
describe('Unit: refresh tokens', () => {
  // Input: two newly generated refresh tokens
  // Expected behavior: unique values, the stored hash is not the raw token, expiry 30 days out
  test('Generates a unique token with its hash and expiry', () => {
    const before = Date.now();
    const first = generateNewRefreshToken();
    const second = generateNewRefreshToken();

    // Make sure that the tokens are unique
    expect(first.raw).not.toBe(second.raw);

    // Make sure that the hash is not the raw token
    expect(first.hash).not.toBe(first.raw);
    expect(first.hash).toBe(hashRefreshToken(first.raw));
    expect(first.expiresAt.getTime()).toBeGreaterThanOrEqual(before + 30 * 24 * 60 * 60 * 1000);
    expect(first.expiresAt.getTime()).toBeLessThanOrEqual(Date.now() + 30 * 24 * 60 * 60 * 1000);
  });
});
