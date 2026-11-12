import { sendPush } from '../../src/external/fcm';
import { clearFcmTokens, getFcmTokens } from '../../src/users/users.repository';
import { pushToUsers } from '../../src/users/users.service';

jest.mock('../../src/users/users.repository', () => ({
  getFcmTokens: jest.fn(),
  clearFcmTokens: jest.fn(),
}));

jest.mock('../../src/external/fcm', () => ({
  sendPush: jest.fn(),
}));

const message = { title: 'Match found!', body: 'Your singles game starts in 1 hour.', data: { gameId: 'g1' } };

// Interface pushToUsers(userIds, message)
describe('Mocked: pushToUsers', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(getFcmTokens).mockResolvedValue(['token-a', 'token-b']);
    jest.mocked(sendPush).mockResolvedValue([]);
  });

  // Input: two users who both have a working device token
  // Expected behavior: one push to both devices, nothing cleared
  // Expected output: 2
  test('Sends the push to every user with a token', async () => {
    await expect(pushToUsers(['a', 'b'], message)).resolves.toBe(2);
    expect(getFcmTokens).toHaveBeenCalledWith(['a', 'b']);
    expect(sendPush).toHaveBeenCalledWith(['token-a', 'token-b'], message);
    expect(clearFcmTokens).not.toHaveBeenCalled();
  });

  // Input: FCM says one of the tokens no longer exists
  // Expected behavior: the dead token is removed so it is not used again
  // Expected output: 1, only the working device was reached
  test('Clears the tokens FCM says are dead', async () => {
    jest.mocked(sendPush).mockResolvedValue(['token-b']);

    await expect(pushToUsers(['a', 'b'], message)).resolves.toBe(1);
    expect(clearFcmTokens).toHaveBeenCalledWith(['token-b']);
  });

  // Input: no user has a device token
  // Expected behavior: nothing is cleared
  // Expected output: 0
  test('Reaches no device when no user has a token', async () => {
    jest.mocked(getFcmTokens).mockResolvedValue([]);

    await expect(pushToUsers(['a'], message)).resolves.toBe(0);
    expect(clearFcmTokens).not.toHaveBeenCalled();
  });

  // Input: sending the push throws
  // Expected behavior: the error reaches the caller, which decides whether it matters
  // Expected output: the same error
  test('Passes push errors to the caller', async () => {
    const error = new Error('FCM down');
    jest.mocked(sendPush).mockRejectedValue(error);

    await expect(pushToUsers(['a'], message)).rejects.toBe(error);
  });
});
