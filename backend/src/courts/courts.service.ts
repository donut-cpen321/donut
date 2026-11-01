import { fetchCourts } from "./courts.repository";

/**
 * Gets every court, to show as markers on the map
 * @returns the courts with their location
 */
export async function getCourts() {
  return await fetchCourts();
}
