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
  const { idToken } = req.body;
  if (typeof idToken !== 'string' || idToken.trim() === '') {
    res.status(400).json({ message: 'A Google ID token is required' });
    return;
  }

  const payload = await validateToken(idToken);
  
  if (payload === null) {
    res.status(401).json({ message: 'Invalid Google ID token' });
    return;
  }

  const existingUser = await findUserByGoogleSub(payload.sub);
  if (existingUser) {
    res.status(200).json({ message: 'Login successful', user: existingUser });
    return;
  }

  const user = await createUser({
    googleSub: payload.sub,
    username: payload.email, // temporary until user sets their username with onboarding
    name: payload.name,
    email: payload.email,
  });

  console.log(user);

  if (user !== null) {
    res.status(201).json({ message: 'New user created', user: user });
    return;
  }

    res.status(500).json({message: 'Something went wrong, please try again later. (Database update failed)'});
    return;
}