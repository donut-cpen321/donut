// Firebase Cloud Messaging: push notifications.
import { cert, initializeApp } from 'firebase-admin/app';
import { getMessaging, type Messaging } from 'firebase-admin/messaging';
import { env } from '../config/env';

// FCM errors meaning the token will never work again (app uninstalled, token deleted on logout)
const DEAD_TOKEN_ERRORS = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

export interface PushMessage {
  title: string;
  body: string;
  data: Record<string, string>;
}

let messaging: Messaging | undefined;
let warnedNotConfigured = false;

function getClient(): Messaging | undefined {
  if (!env.firebase) return undefined;
  if (!messaging) {
    const app = initializeApp({ credential: cert(env.firebase) });
    messaging = getMessaging(app);
  }
  return messaging;
}

/**
 * @returns whether the FIREBASE_* credentials are set, so pushes are actually sent
 */
export function isPushEnabled(): boolean {
  return env.firebase !== undefined;
}

/**
 * Sends one push notification to every token. The Firebase SDK retries brief FCM outages on its own.
 *
 * @param tokens - The devices' FCM tokens.
 * @param message - The notification and the data the app reads when it is tapped.
 * @returns The tokens FCM says no longer exist, so they can be removed.
 */
export async function sendPush(tokens: string[], message: PushMessage): Promise<string[]> {
  const client = getClient();
  if (!client) {
    if (!warnedNotConfigured) console.warn('FIREBASE_* not set, push notifications are off');
    warnedNotConfigured = true;
    return [];
  }
  if (tokens.length === 0) return [];

  const { responses } = await client.sendEach(
    tokens.map((token) => ({
      token,
      notification: { title: message.title, body: message.body },
      data: message.data,
      android: { priority: 'high' },
    })),
  );
  return tokens.filter((_, i) => DEAD_TOKEN_ERRORS.has(responses[i]?.error?.code ?? ''));
}
