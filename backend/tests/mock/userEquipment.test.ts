import request from 'supertest';
import { createApp } from '../../src/app';
import { authErrors } from '../../src/auth/requireAuth';
import { signAccessToken } from '../../src/auth/tokens';
import { commonErrors, errorBody } from '../../src/errors';
import type { Equipment } from '../../src/generated/prisma/client';
import {
  createEquipment,
  listEquipment,
  retireEquipment,
  updateEquipment,
} from '../../src/users/users.repository';
import { userErrors } from '../../src/users/users.service';

jest.mock('../../src/users/users.repository', () => ({
  createEquipment: jest.fn(),
  createUser: jest.fn(),
  getUserByGoogleSub: jest.fn(),
  getUserById: jest.fn(),
  listEquipment: jest.fn(),
  retireEquipment: jest.fn(),
  updateEquipment: jest.fn(),
}));

const mockedCreateEquipment = jest.mocked(createEquipment);
const mockedListEquipment = jest.mocked(listEquipment);
const mockedRetireEquipment = jest.mocked(retireEquipment);
const mockedUpdateEquipment = jest.mocked(updateEquipment);

const userId = 'user-123';

function authorizationHeader() {
  return `Bearer ${signAccessToken(userId)}`;
}

describe('Mocked: /user/equipment', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    process.env.JWT_SECRET = 'test-jwt-secret';
  });

  test('Lists the signed-in user equipment', async () => {
    mockedListEquipment.mockResolvedValue([{
      id: 1,
      userId,
      type: 'RACKET',
      name: 'Training racket',
      isDefault: false,
      createdAt: new Date('2026-01-01T00:00:00.000Z'),
      nextMaintenanceAt: new Date('2026-10-01T00:00:00.000Z'),
      retiredAt: null,
      hoursUsed: 0,
      gamesUsed: 0,
    }]);

    const response = await request(createApp())
      .get('/user/equipment')
      .set('Authorization', authorizationHeader());

    expect(response.status).toBe(200);
    expect(response.body.equipment).toHaveLength(1);
    expect(response.body.equipment[0].nextMaintenanceAt).toBe('2026-10-01T00:00:00.000Z');
    expect(mockedListEquipment).toHaveBeenCalledWith(userId);
  });

  test('Requires authentication', async () => {
    const response = await request(createApp()).get('/user/equipment');

    expect(response.status).toBe(401);
    expect(response.body).toEqual(errorBody(authErrors.MissingToken));
    expect(mockedListEquipment).not.toHaveBeenCalled();
  });

  test('Creates equipment for the signed-in user', async () => {
    const equipment: Equipment = {
      id: 1,
      userId,
      type: 'RACKET',
      name: 'Training racket',
      isDefault: false,
      createdAt: new Date('2026-01-01T00:00:00.000Z'),
      nextMaintenanceAt: new Date('2026-10-01T00:00:00.000Z'),
      retiredAt: null,
      hoursUsed: 0,
      gamesUsed: 0,
    };
    mockedCreateEquipment.mockResolvedValue(equipment);

    const response = await request(createApp())
      .post('/user/equipment')
      .set('Authorization', authorizationHeader())
      .send({
        type: 'RACKET',
        name: 'Training racket',
        nextMaintenanceAt: '2026-10-01T00:00:00.000Z',
      });

    expect(response.status).toBe(201);
    expect(response.body.equipment.name).toBe('Training racket');
    expect(mockedCreateEquipment).toHaveBeenCalledWith(userId, {
      type: 'RACKET',
      name: 'Training racket',
      isDefault: false,
      nextMaintenanceAt: new Date('2026-10-01T00:00:00.000Z'),
    });
  });

  test('Rejects an invalid equipment body', async () => {
    const response = await request(createApp())
      .post('/user/equipment')
      .set('Authorization', authorizationHeader())
      .send({ type: 'UNKNOWN', name: '' });

    expect(response.status).toBe(400);
    expect(response.body).toMatchObject(errorBody(commonErrors.InvalidRequest));
    expect(mockedCreateEquipment).not.toHaveBeenCalled();
  });

  test('Updates owned equipment', async () => {
    mockedUpdateEquipment.mockResolvedValue({
      id: 1,
      userId,
      type: 'RACKET',
      name: 'Competition racket',
      isDefault: true,
      createdAt: new Date('2026-01-01T00:00:00.000Z'),
      nextMaintenanceAt: null,
      retiredAt: null,
      hoursUsed: 0,
      gamesUsed: 0,
    });

    const response = await request(createApp())
      .patch('/user/equipment/1')
      .set('Authorization', authorizationHeader())
      .send({
        name: 'Competition racket',
        isDefault: true,
        nextMaintenanceAt: '2026-12-01T00:00:00.000Z',
      });

    expect(response.status).toBe(200);
    expect(response.body.equipment.name).toBe('Competition racket');
    expect(mockedUpdateEquipment).toHaveBeenCalledWith(userId, 1, {
      name: 'Competition racket',
      isDefault: true,
      nextMaintenanceAt: new Date('2026-12-01T00:00:00.000Z'),
    });
  });

  test('Returns not found when equipment is not owned by the user', async () => {
    mockedUpdateEquipment.mockResolvedValue(null);

    const response = await request(createApp())
      .patch('/user/equipment/1')
      .set('Authorization', authorizationHeader())
      .send({ name: 'Other racket' });

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(userErrors.EquipmentNotFound));
  });

  test('Retires owned equipment', async () => {
    mockedRetireEquipment.mockResolvedValue(true);

    const response = await request(createApp())
      .delete('/user/equipment/1')
      .set('Authorization', authorizationHeader());

    expect(response.status).toBe(204);
    expect(mockedRetireEquipment).toHaveBeenCalledWith(userId, 1);
  });

  test('Returns not found when retiring equipment the user does not own', async () => {
    mockedRetireEquipment.mockResolvedValue(false);

    const response = await request(createApp())
      .delete('/user/equipment/1')
      .set('Authorization', authorizationHeader());

    expect(response.status).toBe(404);
    expect(response.body).toEqual(errorBody(userErrors.EquipmentNotFound));
  });

  test('Rejects an invalid equipment ID', async () => {
    const response = await request(createApp())
      .delete('/user/equipment/abc')
      .set('Authorization', authorizationHeader());

    expect(response.status).toBe(400);
    expect(response.body).toMatchObject(errorBody(commonErrors.InvalidRequest));
    expect(mockedRetireEquipment).not.toHaveBeenCalled();
  });
});