import request from 'supertest';
import type { GoogleProfile } from '../../src/users/helpers/validateToken';
import { createApp } from '../../src/app';
import { createRefreshToken } from '../../src/auth/auth.repository';
import { hashRefreshToken, verifyAccessToken } from '../../src/auth/tokens';
import { validateToken } from '../../src/users/helpers/validateToken';
import { createUser, getUserByGoogleSub } from '../../src/users/users.repository';

jest.mock('../../src/users/helpers/validateToken', () => ({
  validateToken: jest.fn(),
}));

jest.mock('../../src/users/users.repository', () => ({
  createUser: jest.fn(),
  getUserByGoogleSub: jest.fn(),
}));

jest.mock('../../src/auth/auth.repository', () => ({
  createRefreshToken: jest.fn(),
}));

const mockedValidateToken = jest.mocked(validateToken);
const mockedCreateUser = jest.mocked(createUser);
const mockedGetUserByGoogleSub = jest.mocked(getUserByGoogleSub);
const mockedCreateRefreshToken = jest.mocked(createRefreshToken);

// The access token belongs to the user and only the refresh token's hash is stored
function expectTokensIssuedFor(body: unknown, userId: string) {
  const { accessToken, refreshToken } = body as { accessToken: string; refreshToken: string };

  expect(verifyAccessToken(accessToken)).toBe(userId);
  expect(mockedCreateRefreshToken).toHaveBeenCalledWith({
    userId,
    tokenHash: hashRefreshToken(refreshToken),
    expiresAt: expect.any(Date),
  });
}

describe('Mocked: POST /user/login', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    process.env.JWT_SECRET = 'test-jwt-secret';
  });

  test('No Token', async () => {
    const response = await request(createApp())
      .post('/user/login')
      .set('Content-Type', 'application/json')
      .send({ idToken: '' });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('Invalid request body');
    expect(response.body.details.fieldErrors.idToken).toBeDefined();
    expect(mockedValidateToken).not.toHaveBeenCalled();
  });

  test('Missing request body', async () => {
    const response = await request(createApp()).post('/user/login');

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('Invalid request body');
    expect(response.body.details.formErrors.length).toBeGreaterThan(0);
    expect(mockedValidateToken).not.toHaveBeenCalled();
  });

  test('Non-string Token', async () => {
    const response = await request(createApp())
      .post('/user/login')
      .set('Content-Type', 'application/json')
      .send({ idToken: 123 });

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('Invalid request body');
    expect(response.body.details.fieldErrors.idToken).toBeDefined();
    expect(mockedValidateToken).not.toHaveBeenCalled();
  });

  test('Invalid Token', async () => {
    mockedValidateToken.mockResolvedValue(null);

    const response = await request(createApp())
      .post('/user/login')
      .set('Content-Type', 'application/json')
      .send({ idToken: 'invalid-token' });

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Invalid Google ID token' });
    expect(mockedValidateToken).toHaveBeenCalledWith('invalid-token');
    expect(mockedGetUserByGoogleSub).not.toHaveBeenCalled();
  });

  test.each([
    ['missing email', { name: 'New User' }],
    ['blank email', { email: '   ', name: 'New User' }],
    ['missing name', { email: 'new.user@example.com' }],
    ['blank name', { email: 'new.user@example.com', name: '   ' }],
  ])('Rejects a Google profile with %s', async (_description, fields) => {
    mockedValidateToken.mockResolvedValue({
      sub: 'google-sub-123',
      ...fields,
    });
    mockedGetUserByGoogleSub.mockResolvedValue(null);

    const response = await request(createApp())
      .post('/user/login')
      .send({ idToken: 'mock-google-token' });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ error: 'Google profile must include name and email' });
    expect(mockedCreateUser).not.toHaveBeenCalled();
  });

  test('Returns a server error when token verification throws', async () => {
    mockedValidateToken.mockRejectedValue(new Error('Verifier unavailable'));

    const response = await request(createApp())
      .post('/user/login')
      .send({ idToken: 'mock-google-token' });

    expect(response.status).toBe(500);
    expect(mockedGetUserByGoogleSub).not.toHaveBeenCalled();
  });

  test('Returns a server error when looking up the user fails', async () => {
    mockedValidateToken.mockResolvedValue({
      sub: 'google-sub-123',
      email: 'new.user@example.com',
      name: 'New User',
    });
    mockedGetUserByGoogleSub.mockRejectedValue(new Error('Database unavailable'));

    const response = await request(createApp())
      .post('/user/login')
      .send({ idToken: 'mock-google-token' });

    expect(response.status).toBe(500);
    expect(mockedCreateUser).not.toHaveBeenCalled();
  });

  test('Returns a server error when creating a user fails', async () => {
    mockedValidateToken.mockResolvedValue({
      sub: 'google-sub-123',
      email: 'new.user@example.com',
      name: 'New User',
    });
    mockedGetUserByGoogleSub.mockResolvedValue(null);
    mockedCreateUser.mockRejectedValue(new Error('Database unavailable'));

    const response = await request(createApp())
      .post('/user/login')
      .send({ idToken: 'mock-google-token' });

    expect(response.status).toBe(500);
    expect(mockedCreateUser).toHaveBeenCalledTimes(1);
  });

  test('Handles a duplicate-user race during creation as a server error', async () => {
    mockedValidateToken.mockResolvedValue({
      sub: 'google-sub-123',
      email: 'new.user@example.com',
      name: 'New User',
    });
    mockedGetUserByGoogleSub.mockResolvedValue(null);
    mockedCreateUser.mockRejectedValue(new Error('Unique constraint failed'));

    const response = await request(createApp())
      .post('/user/login')
      .send({ idToken: 'mock-google-token' });

    expect(response.status).toBe(500);
    expect(mockedCreateUser).toHaveBeenCalledTimes(1);
  });

  test('Creates a user with the validated Google profile', async () => {
    const googleProfile: GoogleProfile = {
      sub: 'google-sub-123',
      email: 'new.user@example.com',
      name: 'New User',
    };

    mockedValidateToken.mockResolvedValue(googleProfile);
    mockedGetUserByGoogleSub.mockResolvedValue(null);
    mockedCreateUser.mockResolvedValue({
      id: 'user-123',
      username: 'new.user@example.com',
      name: 'New User',
      email: 'new.user@example.com',
      createdAt: new Date('2026-01-01T00:00:00.000Z'),
    });

    const response = await request(createApp())
      .post('/user/login')
      .set('Content-Type', 'application/json')
      .send({ idToken: 'mock-google-token' });

    expect(response.status).toBe(201);
    expect(response.body).toEqual({
      message: 'New user created',
      username: 'new.user@example.com',
      accessToken: expect.any(String),
      refreshToken: expect.any(String),
    });
    expectTokensIssuedFor(response.body, 'user-123');
    expect(mockedValidateToken).toHaveBeenCalledWith('mock-google-token');
    expect(mockedGetUserByGoogleSub).toHaveBeenCalledWith('google-sub-123');
    expect(mockedCreateUser).toHaveBeenCalledWith({
      googleSub: 'google-sub-123',
      username: 'new.user@example.com',
      name: 'New User',
      email: 'new.user@example.com',
    });
  });

  test('Gets existing user with valid google profile', async () => {
    const googleProfile: GoogleProfile = {
      sub: 'google-sub-123',
      email: 'new.user@example.com',
      name: 'New User',
    };

    mockedValidateToken.mockResolvedValue(googleProfile);
    mockedGetUserByGoogleSub.mockResolvedValue({
      id: 'user-123',
      username: 'new.user@example.com',
      name: 'New User',
      email: 'new.user@example.com',
      createdAt: new Date('2026-01-01T00:00:00.000Z')}
    );

    const response = await request(createApp())
      .post('/user/login')
      .set('Content-Type', 'application/json')
      .send({ idToken: 'mock-google-token' });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      message: 'Login successful',
      username: 'new.user@example.com',
      accessToken: expect.any(String),
      refreshToken: expect.any(String),
    });
    expectTokensIssuedFor(response.body, 'user-123');
    expect(mockedValidateToken).toHaveBeenCalledWith('mock-google-token');
    expect(mockedGetUserByGoogleSub).toHaveBeenCalledWith('google-sub-123');
    expect(mockedCreateUser).not.toHaveBeenCalled();
  });
});
