import { OAuth2Client } from 'google-auth-library';

// The fields of a verified Google ID token that login uses
export interface GoogleProfile {
  sub: string;
  email?: string | undefined;
  name?: string | undefined;
}

/**
 * Validates a user's auth token
 * @param idToken google auth token given by the frontend
 * @returns the Google account's id, email and name, null if the token is invalid
 */
export async function validateToken(idToken: string): Promise<GoogleProfile | null> {
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

    const payload = ticket.getPayload();
    if (!payload) {
      return null;
    }
    const profile: GoogleProfile = { sub: payload.sub, email: payload.email, name: payload.name };
    return profile;
  } catch (e)
  {
    console.error(e)
    // TODO: send error information
    return null;
  }
}
