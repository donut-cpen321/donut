import request from 'supertest';
import type { TokenPayload } from 'google-auth-library';
import { createApp } from '../../src/app';
import { validateToken } from '../../src/users/helpers/validateToken';
import { createUser, getUserByGoogleSub } from '../../src/users/users.repository';

jest.mock('../../src/users/helpers/validateToken', () => ({
  validateToken: jest.fn(),
}));

jest.mock('../../src/users/users.repository', () => ({
  createUser: jest.fn(),
  getUserByGoogleSub: jest.fn(),
}));

const mockedValidateToken = jest.mocked(validateToken);
const mockedCreateUser = jest.mocked(createUser);
const mockedGetUserByGoogleSub = jest.mocked(getUserByGoogleSub);

describe('Mocked: POST /user/login', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('No Token', async () => {
    const response = await request(createApp())
      .post('/user/login')
      .set('Content-Type', 'application/json')
      .send({ idToken: '' });

    expect(response.status).toBe(400);
    expect(response.body).toEqual({ error: 'A Google ID token is required' });
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

  test('Creates a user with the validated Google profile', async () => {
    const googleProfile: TokenPayload = {
      iss: 'https://accounts.google.com',
      aud: 'test-google-client-id',
      sub: 'google-sub-123',
      email: 'new.user@example.com',
      name: 'New User',
      iat: 1_766_000_000,
      exp: 1_766_003_600,
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
    });
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
    const googleProfile: TokenPayload = {
      iss: 'https://accounts.google.com',
      aud: 'test-google-client-id',
      sub: 'google-sub-123',
      email: 'new.user@example.com',
      name: 'New User',
      iat: 1_766_000_000,
      exp: 1_766_003_600,
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
    });
    expect(mockedValidateToken).toHaveBeenCalledWith('mock-google-token');
    expect(mockedGetUserByGoogleSub).toHaveBeenCalledWith('google-sub-123');
    expect(mockedCreateUser).not.toHaveBeenCalled();
  });
});
