import { z } from 'zod';
import { EquipmentType } from '../generated/prisma/enums';

export const loginSchema = z.object({
  idToken: z.string().min(1),
});

export const refreshTokenSchema = z.object({
  refreshToken: z.string().min(1),
});

export const equipmentIdSchema = z.coerce.number().int().positive();

export const createEquipmentSchema = z.strictObject({
  type: z.enum(EquipmentType),
  name: z.string().trim().min(1),
  isDefault: z.boolean().optional(),
});

export const updateEquipmentSchema = z.strictObject({
  type: z.enum(EquipmentType).optional(),
  name: z.string().trim().min(1).optional(),
  isDefault: z.boolean().optional(),
}).refine((equipment) => Object.keys(equipment).length > 0);

export type CreateEquipmentInput = z.infer<typeof createEquipmentSchema>;
export type UpdateEquipmentInput = z.infer<typeof updateEquipmentSchema>;