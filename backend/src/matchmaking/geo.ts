// Pure geometry on latitude/longitude points
const EARTH_RADIUS_KM = 6371;

export interface Location {
  latitude: number;
  longitude: number;
}

/**
 * Straight-line distance between two points on Earth (haversine formula).
 *
 * @param a - The first point.
 * @param b - The second point.
 * @returns The distance in kilometres.
 */
export function distanceKm(a: Location, b: Location): number {
  const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
  const dLat = toRadians(b.latitude - a.latitude);
  const dLng = toRadians(b.longitude - a.longitude);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(a.latitude)) * Math.cos(toRadians(b.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
}

/**
 * Plain average of the points. Close enough for points a few km apart.
 *
 * @param locations - The points, at least one.
 * @returns The point in the middle.
 */
export function midpoint(locations: Location[]): Location {
  const average = (values: number[]) => values.reduce((sum, value) => sum + value, 0) / values.length;
  return {
    latitude: average(locations.map((l) => l.latitude)),
    longitude: average(locations.map((l) => l.longitude)),
  };
}
