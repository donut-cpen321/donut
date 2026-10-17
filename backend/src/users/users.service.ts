// Username rules and onboarding. Calls users.repository.
import { type Request, type Response } from 'express';
import type { ParamsDictionary } from 'express-serve-static-core';
import { validateToken } from './helpers/validateToken';
import { findUserByGoogleSub, createUser } from './users.repository';

type LoginRequestBody = {
  idToken?: unknown;
};

export async function login(
  req: Request<ParamsDictionary, unknown, LoginRequestBody>,
  res: Response,
): Promise<void> {
  if (!req.body) {
    res.status(400).json({ error: 'Request body is required' });
    return;
  }

  const { idToken } = req.body;
  if (typeof idToken !== 'string' || idToken.trim() === '') {
    res.status(400).json({ error: 'A Google ID token is required' });
    return;
  }

  const payload = await validateToken(idToken);
  
  if (payload === null) {
    res.status(401).json({ error: 'Invalid Google ID token' });
    return;
  }

  const existingUser = await findUserByGoogleSub(payload.sub);
  if (existingUser) {
    res.status(200).json({ message: 'Login successful', username: existingUser.username });
    return;
  }

  const email = payload.email?.trim() ?? '';
  const name = payload.name?.trim() ?? '';
  if (email === '' || name === '') {
    res.status(400).json({ error: 'Google profile must include name and email' });
    return;
  }

  const user = await createUser({
    googleSub: payload.sub,
    username: email, // temporary until user sets their username with onboarding
    name,
    email,
  });

  console.log(user);

  if (user !== null) {
    res.status(201).json({ message: 'New user created', username: user.username });
    return;
  }

    res.status(500).json({error: 'Something went wrong, please try again later. (Database update failed)'});
    return;
}