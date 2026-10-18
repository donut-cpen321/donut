import { OAuth2Client, type TokenPayload } from 'google-auth-library';


/**
 * Validates a user's auth token
 * @param idToken google auth token given by the frontend
 * @returns payload with user information provided by google, null if an error occured
 */
export async function validateToken(idToken: string): Promise<TokenPayload | undefined | null> {
  const googleClientId = process.env.GOOGLE_CLIENT_ID;

  if (!googleClientId) {
    console.error("Missing env.GOOGLE_CLIENT_ID, please add the google cloud console ID to .env");
    return null;
  }

  const client = new OAuth2Client(googleClientId);

  try {
    const ticket = await client.verifyIdToken({
      idToken,
      audience: googleClientId,
    });

    return ticket.getPayload() ?? null;
  } catch {
    // TODO: send error information
    return null;
  }
}