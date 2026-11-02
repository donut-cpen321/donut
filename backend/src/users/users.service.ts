import { issueTokens } from '../auth/auth.service';
import { validateToken } from './helpers/validateToken';
import {
  createEquipment as createEquipmentRecord,
  createUser,
  getUserByGoogleSub,
  getUserById,
  listEquipment as listEquipmentRecords,
  retireEquipment as retireEquipmentRecord,
  updateEquipment as updateEquipmentRecord,
} from './users.repository';
import type { CreateEquipmentInput, UpdateEquipmentInput } from './users.schemas';

export const loginErrors = {
  InvalidToken: 'Invalid Google ID token',
  InvalidProfile: 'Google profile must include name and email',
  UserCreationFailed: 'Something went wrong, please try again later. (Database update failed)',
} as const;

export type LoginError = (typeof loginErrors)[keyof typeof loginErrors];

export type LoginResult =
  | { success: false; error: LoginError }
  | {
      success: true;
      isNewUser: boolean;
      username: string;
      accessToken: string;
      refreshToken: string;
    };

/**
 * Logs the user into the backend and pulls the user's information from the database
 * or creates a new user if it does not exist
 * @param idToken the user's Google login token
 * @returns the login result or a reason the login could not be completed
 */
export async function login(idToken: string): Promise<LoginResult> {
  const payload = await validateToken(idToken);
 
  if (!payload || !payload.sub) {
    return { success: false, error: loginErrors.InvalidToken };
  } 

  const existingUser = await getUserByGoogleSub(payload.sub);
  if (existingUser) {
    const tokens = await issueTokens(existingUser.id);
    return {
      success: true,
      isNewUser: false,
      username: existingUser.username,
      ...tokens,
    };
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
    return { success: false, error: loginErrors.InvalidProfile };
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
    return { success: false, error: loginErrors.UserCreationFailed };
  }

  const tokens = await issueTokens(newUser.id);
  return {
    success: true,
    isNewUser: true,
    username: newUser.username,
    ...tokens,
  };
}

/**
 * Gets the signed-in user's own profile, including their rating
 * @param userId the signed-in user's id
 * @returns the user, or null if they do not exist
 */
export async function getProfile(userId: string) {
  return await getUserById(userId);
}

export async function getEquipment(userId: string) {
  return await listEquipmentRecords(userId);
}

export async function addEquipment(userId: string, equipment: CreateEquipmentInput) {
  return await createEquipmentRecord(userId, {
    type: equipment.type,
    name: equipment.name,
    isDefault: equipment.isDefault ?? false,
  });
}

export async function editEquipment(
  userId: string,
  equipmentId: number,
  equipment: UpdateEquipmentInput,
) {
  return await updateEquipmentRecord(userId, equipmentId, {
    ...(equipment.type === undefined ? {} : { type: equipment.type }),
    ...(equipment.name === undefined ? {} : { name: equipment.name }),
    ...(equipment.isDefault === undefined ? {} : { isDefault: equipment.isDefault }),
  });
}

export async function removeEquipment(userId: string, equipmentId: number) {
  return await retireEquipmentRecord(userId, equipmentId);
}
