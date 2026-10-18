import request from 'supertest';
import { createApp } from '../../src/app';

// Interface POST /user/login
describe('Mocked: POST /user/login', () => {
    // Input: POST /user/login with no token
    // Expected status code: 400
    // Expected behavior: request is rejected; no state is changed
    // Expected output: { error: "A Google ID token is required" }
    test('No Token', async () => {

    const response = await request(createApp())
        .post('/user/login')
        .set('Content-Type', 'application/json')
        .send({ idToken: '' });
    expect(response.status).toBe(400);
    expect(response.body).toEqual({ error: 'A Google ID token is required' });
    });
});

// Interface POST /user/login
describe('Mocked: POST /user/login', () => {
    // Input: POST /user/login with invalid token
    // Expected status code: 401
    // Expected behavior: request is rejected; no state is changed
    // Expected output: { error: 'Invalid Google ID token' }
    test('Invalid Token', async () => {

    const response = await request(createApp())
        .post('/user/login')
        .set('Content-Type', 'application/json')
        .send({ idToken: 'abcdefg' });
    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'Invalid Google ID token' });
    });
});

