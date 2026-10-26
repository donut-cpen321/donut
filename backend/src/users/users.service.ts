import { type Request, type Response } from 'express';
import { issueTokens } from '../auth/auth.service';
import { validateToken } from './helpers/validateToken';
import { getUserByGoogleSub, createUser } from './users.repository';

/**
 * Logs the user into the backend and pulls the user's information from the database
 * or creates a new user if it does not exist
 * @param req contains the user's google login token
 * @param res 
 */
export async function login(req: Request, res: Response): Promise<void> {
  if (!req.body) {
    res.status(400).json({ error: 'Request body is required' });
    return;
  }

  const idToken: unknown = req.body.idToken;
  if (typeof idToken !== 'string' || !idToken) {
    res.status(400).json({ error: 'A Google ID token is required' });
    return;
  }

  const payload = await validateToken(idToken);
 
  if (!payload || !payload.sub) {
    res.status(401).json({ error: 'Invalid Google ID token' });
    return;
  } 

  const existingUser = await getUserByGoogleSub(payload.sub);
  if (existingUser) {
    const tokens = await issueTokens(existingUser.id);
    res.status(200).json({ message: 'Login successful', username: existingUser.username, ...tokens });
    return;
  }

   /*
   * Google guarantees the following will be returned with a payload:
   * googleSub
   * email
   * firstName
   * I have no idea why everything tells me that these may be null when they should not be, but 
   * I will guard against it anyways
   */

  const email = payload.email?.trim() ?? '';
  const name = payload.name?.trim() ?? '';
  if (!email || !name) {
    res.status(400).json({ error: 'Google profile must include name and email' });
    return;
  }

  // ai thinks that email may not be a unique indentifier, but google literally guarantees that all emails are unique...
  // it should be fine for now to use email as the username as a temporary measure
  const newUser = await createUser({
    googleSub: payload.sub,
    username: email, // temporary until user sets their username with onboarding
    name,
    email,
  });

  if (!newUser) {
    res.status(500).json({error: 'Something went wrong, please try again later. (Database update failed)'});
    return;
  }

  const tokens = await issueTokens(newUser.id);
  res.status(201).json({ message: 'New user created', username: newUser.username, ...tokens });
  return;
}