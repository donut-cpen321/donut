import { prisma } from "../db";

/**
 * Fetches every court, ordered by name
 * @returns the courts with their location
 */
export async function fetchCourts() {
  return await prisma.court.findMany({
    select: { id: true, name: true, latitude: true, longitude: true },
    orderBy: { name: "asc" },
  });
}
