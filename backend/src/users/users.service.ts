import { issueTokens } from '../auth/auth.service';
import { validateToken } from './helpers/validateToken';
import {
  createEquipment as createEquipmentRecord,
  createUser,
  getUserByGoogleSub,
  getUserById,
  listEquipment as listEquipmentRecords,
  retireEquipment as retireEquipmentRecord,
  saveFcmToken as saveFcmTokenRecord,
  updateEquipment as updateEquipmentRecord,
} from './users.repository';
import type { CreateEquipmentInput, UpdateEquipmentInput } from './users.schemas';

export const loginErrors = {
  InvalidToken: 'Invalid Google ID token',
  InvalidProfile: 'Google profile must include name and email',
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
 * Validates a Google ID token, finds or creates the user, and issues tokens.
 * @param idToken The user's Google ID token
 * @returns The login result, or a typed error when the token or profile is invalid
 */
export async function login(idToken: string): Promise<LoginResult> {
  const payload = await validateToken(idToken);
 
  if (!payload?.sub) {
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

  const tokens = await issueTokens(newUser.id);
  return {
    success: true,
    isNewUser: true,
    username: newUser.username,
    ...tokens,
  };
}

/**
 * Gets a user's profile, including their rating.
 * @param userId The signed-in user's ID
 * @returns The profile, or null if the user does not exist
 */
export async function getProfile(userId: string) {
  return await getUserById(userId);
}

/**
 * Saves the device the user's push notifications go to.
 * @param userId The signed-in user's ID
 * @param token The device's FCM token
 */
export async function saveFcmToken(userId: string, token: string) {
  await saveFcmTokenRecord(userId, token);
}

/**
 * Lists active equipment belonging to the specified user.
 * @param userId The equipment owner's user ID
 * @returns The user's equipment records with their maintenance dates
 */
export async function getEquipment(userId: string) {
  return await listEquipmentRecords(userId);
}

/**
 * Adds equipment to the signed-in user's inventory.
 * @param userId The equipment owner
 * @param equipment The type, name, default status, and optional next maintenance date
 * @returns The created equipment record
 */
export async function addEquipment(userId: string, equipment: CreateEquipmentInput) {
  return await createEquipmentRecord(userId, {
    type: equipment.type,
    name: equipment.name,
    isDefault: equipment.isDefault ?? false,
    nextMaintenanceAt: equipment.nextMaintenanceAt ?? null,
  });
}

/**
 * Updates fields on the user's active equipment record.
 * @param userId The equipment owner
 * @param equipmentId The equipment record to update
 * @param equipment The validated fields to change
 * @returns The updated equipment, or null if no matching active record exists
 */
export async function editEquipment(
  userId: string,
  equipmentId: number,
  equipment: UpdateEquipmentInput,
) {
  return await updateEquipmentRecord(userId, equipmentId, {
    ...(equipment.type === undefined ? {} : { type: equipment.type }),
    ...(equipment.name === undefined ? {} : { name: equipment.name }),
    ...(equipment.isDefault === undefined ? {} : { isDefault: equipment.isDefault }),
    ...(equipment.nextMaintenanceAt === undefined ? {} : { nextMaintenanceAt: equipment.nextMaintenanceAt }),
  });
}

/**
 * Retires one of the user's active equipment records.
 * @param userId The equipment owner
 * @param equipmentId The equipment record to retire
 * @returns Whether an active equipment record was retired
 */
export async function removeEquipment(userId: string, equipmentId: number) {
  return await retireEquipmentRecord(userId, equipmentId);
}
