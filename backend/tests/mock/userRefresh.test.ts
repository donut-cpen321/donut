import request from 'supertest';
import { createApp } from '../../src/app';
import { deleteRefreshTokenByHash, createRefreshToken } from '../../src/auth/auth.repository';
import { hashRefreshToken, verifyAccessToken } from '../../src/auth/tokens';

jest.mock('../../src/auth/auth.repository', () => ({
  deleteRefreshTokenByHash: jest.fn(),
  createRefreshToken: jest.fn(),
}));

const mockedDeleteRefreshTokenByHash = jest.mocked(deleteRefreshTokenByHash);
const mockedCreateRefreshToken = jest.mocked(createRefreshToken);

// Interface POST /user/refresh
describe('Mocked: POST /user/refresh', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    process.env.JWT_SECRET = 'test-jwt-secret';
  });

  // Input: request with no body
  // Expected status code: 400
  // Expected output: { error: "A refresh token is required" }
  test('Rejects a request with no body', async () => {
    const response = await request(createApp()).post('/user/refresh');

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ error: 'A refresh token is required' });
    expect(mockedDeleteRefreshTokenByHash).not.toHaveBeenCalled();
  });

  // Input: body with an empty refreshToken
  // Expected status code: 400
  // Expected output: { error: "A refresh token is required" }
  test('Rejects an empty refresh token', async () => {
    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: '' });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ error: 'A refresh token is required' });
    expect(mockedDeleteRefreshTokenByHash).not.toHaveBeenCalled();
  });

  // Input: body where refreshToken is a number instead of a string
  // Expected status code: 400
  // Expected output: { error: "A refresh token is required" }
  test('Rejects a non-string refresh token', async () => {
    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: 123 });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ error: 'A refresh token is required' });
    expect(mockedDeleteRefreshTokenByHash).not.toHaveBeenCalled();
  });

  // Input: a refresh token that is not in the database (never issued, already used, or logged out)
  // Expected status code: 401
  // Expected behavior: no new tokens are issued
  // Expected output: { error: "Invalid or expired refresh token" }
  test('Rejects an unknown refresh token', async () => {
    mockedDeleteRefreshTokenByHash.mockResolvedValue(null);

    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: 'unknown' });

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Invalid or expired refresh token' });
    expect(mockedDeleteRefreshTokenByHash).toHaveBeenCalledWith(hashRefreshToken('unknown'));
    expect(mockedCreateRefreshToken).not.toHaveBeenCalled();
  });

  // Input: a refresh token whose expiry has passed
  // Expected status code: 401
  // Expected behavior: the token is still consumed, no new tokens are issued
  // Expected output: { error: "Invalid or expired refresh token" }
  test('Rejects an expired refresh token', async () => {
    mockedDeleteRefreshTokenByHash.mockResolvedValue({
      userId: 'user-123',
      expiresAt: new Date(Date.now() - 1000),
    });

    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: 'expired' });

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Invalid or expired refresh token' });
    expect(mockedCreateRefreshToken).not.toHaveBeenCalled();
  });

  // Input: a valid refresh token for user-123
  // Expected status code: 200
  // Expected behavior: the old token is consumed and a new pair is issued for the same user
  // Expected output: { accessToken, refreshToken } with a new refresh token
  test('Issues a new token pair for a valid refresh token', async () => {
    // 1 hour from now
    const expiresAt = new Date(Date.now() + 60 * 60 * 1000);  
    mockedDeleteRefreshTokenByHash.mockResolvedValue({ userId: 'user-123', expiresAt });

    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: 'old-token' });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      accessToken: expect.any(String),
      refreshToken: expect.any(String),
    });
    expect(response.body.refreshToken).not.toBe('old-token');
    expect(verifyAccessToken(response.body.accessToken)).toBe('user-123');
    expect(mockedDeleteRefreshTokenByHash).toHaveBeenCalledWith(hashRefreshToken('old-token'));
    expect(mockedCreateRefreshToken).toHaveBeenCalledWith({
      userId: 'user-123',
      tokenHash: hashRefreshToken(response.body.refreshToken),
      expiresAt: expect.any(Date),
    });
  });

  // Input: a refresh token while the database is unavailable
  // Expected status code: 500
  test('Returns a server error when the database fails', async () => {
    mockedDeleteRefreshTokenByHash.mockRejectedValue(new Error('Database unavailable'));

    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: 'some-token' });

    expect(response.status).toBe(500);
    expect(mockedCreateRefreshToken).not.toHaveBeenCalled();
  });
});
