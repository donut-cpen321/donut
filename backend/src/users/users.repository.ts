import { Prisma } from '../generated/prisma/client';
import { prisma } from '../db';

const loginUserSelect = {
  id: true,
  username: true,
  name: true,
  email: true,
  createdAt: true,
} satisfies Prisma.UserSelect;

// The signed-in user's own profile
const profileUserSelect = {
  ...loginUserSelect,
  rating: true,
} satisfies Prisma.UserSelect;

type LoginUser = Prisma.UserGetPayload<{ select: typeof loginUserSelect }>;
type ProfileUser = Prisma.UserGetPayload<{ select: typeof profileUserSelect }>;
type NewUserData = Pick<Prisma.UserCreateInput, 'googleSub' | 'username' | 'name' | 'email'>;


/**
 * Finds a user by the Google subject from a validated ID token.
 * @param googleSub Google's stable identifier for the user
 * @returns The user's login fields, or null if no matching user exists
 */
export async function getUserByGoogleSub(googleSub: string): Promise<LoginUser | null> {
  return await prisma.user.findUnique({
    where: { googleSub },
    select: loginUserSelect,
  });
}

/**
 * Creates a user with the provided profile data.
 * @param userData The Google subject and profile fields required to create a user
 * @returns The created user's login fields
 */
export async function createUser(userData: NewUserData): Promise<LoginUser> {
  return await prisma.user.create({
      data: userData,
      select: loginUserSelect,
  });
}

/**
 * Gets a user's own profile, including their rating
 * @param userId the user's id
 * @returns the user, or null if they do not exist
 */
export async function getUserById(userId: string): Promise<ProfileUser | null> {
  return await prisma.user.findUnique({
    where: { id: userId },
    select: profileUserSelect,
  });
}

/**
 * Lists a user's equipment that has not been retired.
 * @param userId The owner of the equipment
 * @returns Active equipment, including its optional next maintenance date
 */
export async function listEquipment(userId: string) {
  return await prisma.equipment.findMany({
    where: { userId, retiredAt: null },
    orderBy: [{ isDefault: 'desc' }, { createdAt: 'asc' }],
  });
}

/**
 * Creates an equipment record for the specified user.
 * @param userId The owner of the equipment
 * @param data The equipment type, name, default status, and optional maintenance date
 * @returns The created equipment record
 */
export async function createEquipment(
  userId: string,
  data: Pick<Prisma.EquipmentCreateInput, 'type' | 'name' | 'isDefault' | 'nextMaintenanceAt'>,
) {
  return await prisma.equipment.create({
    data: {
      ...data,
      isDefault: data.isDefault ?? false,
      nextMaintenanceAt: data.nextMaintenanceAt ?? null,
      user: { connect: { id: userId } },
    },
  });
}

/**
 * Updates equipment only when it belongs to the user and has not been retired.
 * @param userId The equipment owner
 * @param equipmentId The equipment record to update
 * @param data The fields to update
 * @returns The updated equipment, or null if no matching active record exists
 */
export async function updateEquipment(
  userId: string,
  equipmentId: number,
  data: Prisma.EquipmentUpdateManyMutationInput,
) {
  const { count } = await prisma.equipment.updateMany({
    where: { id: equipmentId, userId, retiredAt: null },
    data,
  });

  if (count === 0) {
    return null;
  }

  return await prisma.equipment.findFirst({
    where: { id: equipmentId, userId, retiredAt: null },
  });
}

/**
 * Marks owned equipment as retired without deleting its record or game history.
 * @param userId The equipment owner
 * @param equipmentId The equipment record to retire
 * @returns Whether an active equipment record was retired
 */
export async function retireEquipment(userId: string, equipmentId: number) {
  const { count } = await prisma.equipment.updateMany({
    where: { id: equipmentId, userId, retiredAt: null },
    data: { retiredAt: new Date(), isDefault: false },
  });

  return count > 0;
}
