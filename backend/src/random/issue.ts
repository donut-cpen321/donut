import { prisma } from "../db";

export async function thisFunctionShouldReturnAnyIfCodacyIsBroken() {
  return "this will be any" as any;
}
