import request from 'supertest';
import { createApp } from '../../src/app';
import { rotateRefreshToken } from '../../src/auth/auth.repository';
import { hashRefreshToken, verifyAccessToken } from '../../src/auth/tokens';

jest.mock('../../src/auth/auth.repository', () => ({
  rotateRefreshToken: jest.fn(),
}));

const mockedRotateRefreshToken = jest.mocked(rotateRefreshToken);

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
    expect(mockedRotateRefreshToken).not.toHaveBeenCalled();
  });

  // Input: body with an empty refreshToken
  // Expected status code: 400
  // Expected output: { error: "A refresh token is required" }
  test('Rejects an empty refresh token', async () => {
    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: '' });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ error: 'A refresh token is required' });
    expect(mockedRotateRefreshToken).not.toHaveBeenCalled();
  });

  // Input: body where refreshToken is a number instead of a string
  // Expected status code: 400
  // Expected output: { error: "A refresh token is required" }
  test('Rejects a non-string refresh token', async () => {
    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: 123 });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ error: 'A refresh token is required' });
    expect(mockedRotateRefreshToken).not.toHaveBeenCalled();
  });

  // Input: a refresh token the database rejects (never issued, already used, logged out, or expired)
  // Expected status code: 401
  // Expected output: { error: "Invalid or expired refresh token" }
  test('Rejects an unknown or expired refresh token', async () => {
    mockedRotateRefreshToken.mockResolvedValue(null);

    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: 'unknown' });

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Invalid or expired refresh token' });
    expect(mockedRotateRefreshToken).toHaveBeenCalledWith(hashRefreshToken('unknown'), {
      tokenHash: expect.any(String),
      expiresAt: expect.any(Date),
    });
  });

  // Input: a valid refresh token for user-123
  // Expected status code: 200
  // Expected behavior: the old token is replaced by the new one in the database
  // Expected output: { accessToken, refreshToken } for the same user, with a new refresh token
  test('Issues a new token pair for a valid refresh token', async () => {
    mockedRotateRefreshToken.mockResolvedValue('user-123');

    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: 'old-token' });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      accessToken: expect.any(String),
      refreshToken: expect.any(String),
    });
    const { accessToken, refreshToken } = response.body as { accessToken: string; refreshToken: string };
    expect(refreshToken).not.toBe('old-token');
    expect(verifyAccessToken(accessToken)).toBe('user-123');
    expect(mockedRotateRefreshToken).toHaveBeenCalledWith(hashRefreshToken('old-token'), {
      tokenHash: hashRefreshToken(refreshToken),
      expiresAt: expect.any(Date),
    });
  });

  // Input: a refresh token while the database is unavailable
  // Expected status code: 500
  test('Returns a server error when the database fails', async () => {
    mockedRotateRefreshToken.mockRejectedValue(new Error('Database unavailable'));

    const response = await request(createApp()).post('/user/refresh').send({ refreshToken: 'some-token' });

    expect(response.status).toBe(500);
  });
});
