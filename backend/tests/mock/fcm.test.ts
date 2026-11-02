import { initializeApp } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
import { env } from '../../src/config/env';
import { sendPush } from '../../src/external/fcm';

jest.mock('../../src/config/env', () => ({ env: { firebase: undefined } }));
jest.mock('firebase-admin/app', () => ({
  cert: jest.fn(),
  initializeApp: jest.fn(),
}));
jest.mock('firebase-admin/messaging', () => ({
  getMessaging: jest.fn(),
}));

const sendEach = jest.fn();
const message = { title: 'Match found!', body: 'Your singles game starts in 1 hour.', data: { gameId: 'g1' } };
const firebaseConfig = { projectId: 'donut', clientEmail: 'fcm@donut.iam', privateKey: 'key' };

// The tests change env.firebase, which the real module marks readonly
const mutableEnv = env as { firebase: typeof firebaseConfig | undefined };

// Interface sendPush(tokens, message)
describe('Mocked: sendPush', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(getMessaging).mockReturnValue({ sendEach } as unknown as ReturnType<typeof getMessaging>);
    mutableEnv.firebase = firebaseConfig;
  });

  // Input: no FIREBASE_* variables in the environment
  // Expected behavior: a warning is logged and nothing is sent
  // Expected output: [], Firebase never initialized
  test('Sends nothing when Firebase is not configured', async () => {
    const consoleWarn = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    mutableEnv.firebase = undefined;

    await expect(sendPush(['t1'], message)).resolves.toEqual([]);
    expect(initializeApp).not.toHaveBeenCalled();
    expect(consoleWarn).toHaveBeenCalled();
    consoleWarn.mockRestore();
  });

  // Input: Firebase configured, no tokens
  // Expected behavior: FCM is not called
  // Expected output: []
  test('Sends nothing when there are no tokens', async () => {
    await expect(sendPush([], message)).resolves.toEqual([]);
    expect(sendEach).not.toHaveBeenCalled();
  });

  // Input: four tokens; FCM accepts one, says two no longer exist, and fails one for another reason
  // Expected behavior: one message per token with the notification and data, sent with high priority
  // Expected output: the two dead tokens, not the one that failed for another reason
  test('Sends to every token and returns the ones FCM says are dead', async () => {
    sendEach.mockResolvedValue({
      responses: [
        { success: true },
        { success: false, error: { code: 'messaging/registration-token-not-registered' } },
        { success: false, error: { code: 'messaging/invalid-registration-token' } },
        { success: false, error: { code: 'messaging/internal-error' } },
      ],
    });

    await expect(sendPush(['ok', 'gone', 'bad', 'flaky'], message)).resolves.toEqual(['gone', 'bad']);
    expect(sendEach).toHaveBeenCalledWith(
      ['ok', 'gone', 'bad', 'flaky'].map((token) => ({
        token,
        notification: { title: message.title, body: message.body },
        data: message.data,
        android: { priority: 'high' },
      })),
    );
  });
});
