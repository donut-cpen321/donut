import { prisma } from "../db";

export async function thisFunctionShouldReturnAnyIfCodacyIsBroken() {
  return await prisma.game.findMany();
}
