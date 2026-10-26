import request from 'supertest';
import { createApp } from '../../src/app';
import { deleteRefreshTokenByHash } from '../../src/auth/auth.repository';
import { hashRefreshToken } from '../../src/auth/tokens';

jest.mock('../../src/auth/auth.repository', () => ({
  deleteRefreshTokenByHash: jest.fn(),
}));

const mockedDeleteRefreshTokenByHash = jest.mocked(deleteRefreshTokenByHash);

// Interface POST /user/logout
describe('Mocked: POST /user/logout', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: request with no body
  // Expected status code: 400
  // Expected output: { error: "A refresh token is required" }
  test('Rejects a request with no body', async () => {
    const response = await request(createApp()).post('/user/logout');

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ error: 'A refresh token is required' });
    expect(mockedDeleteRefreshTokenByHash).not.toHaveBeenCalled();
  });

  // Input: a refresh token
  // Expected status code: 204
  // Expected behavior: the token's hash is deleted, so it can't be used to refresh again
  test('Deletes the refresh token', async () => {
    const response = await request(createApp()).post('/user/logout').send({ refreshToken: 'some-token' });

    expect(response.status).toBe(204);
    expect(mockedDeleteRefreshTokenByHash).toHaveBeenCalledWith(hashRefreshToken('some-token'));
  });

  // Input: a refresh token while the database is unavailable
  // Expected status code: 500
  test('Returns a server error when the database fails', async () => {
    mockedDeleteRefreshTokenByHash.mockRejectedValue(new Error('Database unavailable'));

    const response = await request(createApp()).post('/user/logout').send({ refreshToken: 'some-token' });

    expect(response.status).toBe(500);
  });
});
