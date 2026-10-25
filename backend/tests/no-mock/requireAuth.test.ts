import express from 'express';
import jwt from 'jsonwebtoken';
import request from 'supertest';
import { requireAuth } from '../../src/auth/requireAuth';
import { signAccessToken } from '../../src/auth/tokens';

const SECRET = 'test-jwt-secret';

// A protected route that echoes who the middleware says is calling
function createProtectedApp() {
  const app = express();
  app.get('/protected', requireAuth, (req, res) => {
    res.json({ userId: req.userId });
  });
  return app;
}

// Middleware requireAuth
describe('Unmocked: requireAuth', () => {
  beforeEach(() => {
    process.env.JWT_SECRET = SECRET;
  });

  // Input: valid access token for user-123 in the Authorization header
  // Expected status code: 200
  // Expected behavior: the route handler runs and sees the user id
  // Expected output: { userId: "user-123" }
  test('Lets a valid token through', async () => {
    const app = createProtectedApp();
    const token = signAccessToken('user-123');
    const response = await request(app).get('/protected').set('Authorization', `Bearer ${token}`);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ userId: 'user-123' });
  });

  // Input: no Authorization header
  // Expected status code: 401
  // Expected output: { error: "Missing auth token" }
  test('Rejects a request with no Authorization header', async () => {
    const app = createProtectedApp();
    const response = await request(app).get('/protected');

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Missing auth token' });
  });

  // Input: an Authorization header using the Basic scheme
  // Expected status code: 401
  // Expected output: { error: "Missing auth token" }
  test('Rejects a Basic auth header', async () => {
    const app = createProtectedApp();
    const token = 'Basic dXNlcjpwYXNz';
    const response = await request(app).get('/protected').set('Authorization', token);

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Missing auth token' });
  });

  // Input: a Bearer header with nothing after the scheme
  // Expected status code: 401
  // Expected output: { error: "Missing auth token" }
  test('Rejects a Bearer header with no token', async () => {
    const app = createProtectedApp();
    const token = 'Bearer ';
    const response = await request(app).get('/protected').set('Authorization', token);

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Missing auth token' });
  }); 

  // Input: Authorization: Bearer not-a-jwt
  // Expected status code: 401
  // Expected output: { error: "Invalid or expired auth token" }
  test('Rejects a garbage token', async () => {
    const app = createProtectedApp();
    const token = 'Bearer not-a-jwt';
    const response = await request(app).get('/protected').set('Authorization', token);

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Invalid or expired auth token' });
  });

  // Input: a Bearer token whose exp is in the past
  // Expected status code: 401
  // Expected output: { error: "Invalid or expired auth token" }
  test('Rejects an expired token', async () => {
    const app = createProtectedApp();
    const past_seconds = Math.floor(Date.now() / 1000) - 10;
    const token = jwt.sign({ sub: 'user-123', exp: past_seconds }, SECRET);
    const response = await request(app).get('/protected').set('Authorization', `Bearer ${token}`);

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Invalid or expired auth token' });
  });

  // Input: a Bearer token signed with a different secret
  // Expected status code: 401
  // Expected output: { error: "Invalid or expired auth token" }
  test('Rejects a token signed with another secret', async () => {
    const app = createProtectedApp();
    const token = jwt.sign({ sub: 'user-123' }, 'another-secret');
    const response = await request(app).get('/protected').set('Authorization', `Bearer ${token}`);

    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Invalid or expired auth token' });
  });
});
