import request from 'supertest';
import { createApp } from '../../src/app';
import { signAccessToken } from '../../src/auth/tokens';
import { saveFcmToken } from '../../src/users/users.repository';

// /user/me/fcm-token is behind requireAuth, so requests need a valid token
process.env.JWT_SECRET = 'test-jwt-secret';
const userId = '0b6d3a52-6a8e-4c1f-9d2e-1f3a4b5c6d7e';
const authHeader = `Bearer ${signAccessToken(userId)}`;

jest.mock('../../src/users/users.repository', () => ({
  saveFcmToken: jest.fn(),
}));

const mockedSaveFcmToken = jest.mocked(saveFcmToken);

describe('Mocked: PUT /user/me/fcm-token', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // Input: a signed-in user sends their device's FCM token
  // Expected behavior: the token is saved for that user
  // Expected output: 204
  test('Saves the token for the signed-in user', async () => {
    mockedSaveFcmToken.mockResolvedValue();

    const response = await request(createApp())
      .put('/user/me/fcm-token')
      .set('Authorization', authHeader)
      .send({ token: 'device-token' });

    expect(response.status).toBe(204);
    expect(mockedSaveFcmToken).toHaveBeenCalledWith(userId, 'device-token');
  });

  // Input: a signed-in user sends an empty token
  // Expected behavior: the body is rejected before anything is saved
  // Expected output: 400
  test('Returns 400 when the token is empty', async () => {
    const response = await request(createApp())
      .put('/user/me/fcm-token')
      .set('Authorization', authHeader)
      .send({ token: '' });

    expect(response.status).toBe(400);
    expect(mockedSaveFcmToken).not.toHaveBeenCalled();
  });

  // Input: no Authorization header
  // Expected behavior: requireAuth stops the request
  // Expected output: 401, nothing saved
  test('Returns 401 without an access token', async () => {
    const response = await request(createApp())
      .put('/user/me/fcm-token')
      .send({ token: 'device-token' });

    expect(response.status).toBe(401);
    expect(mockedSaveFcmToken).not.toHaveBeenCalled();
  });
});
