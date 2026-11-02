import { distanceKm, midpoint, type Location } from '../../src/matchmaking/geo';

const UBC: Location = { latitude: 49.2606, longitude: -123.246 }; // UBC campus
const SURREY: Location = { latitude: 49.1913, longitude: -122.849 }; // ~30 km from UBC

// Interface distanceKm(a, b)
describe('distanceKm', () => {
  // Input: the same point twice
  // Expected output: 0
  test('Is zero for the same point', () => {
    expect(distanceKm(UBC, UBC)).toBe(0);
  });

  // Input: two points one degree of latitude apart
  // Expected output: ~111.19 km, the length of one degree on Earth
  test('Measures one degree of latitude as ~111 km', () => {
    expect(distanceKm({ latitude: 0, longitude: 0 }, { latitude: 1, longitude: 0 })).toBeCloseTo(111.19, 1);
  });

  // Input: UBC and Surrey
  // Expected output: ~30 km, whichever point comes first
  test('Is the same in both directions', () => {
    expect(distanceKm(UBC, SURREY)).toBeCloseTo(distanceKm(SURREY, UBC), 6);
    expect(distanceKm(UBC, SURREY)).toBeCloseTo(29.5, 0);
  });
});

// Interface midpoint(locations)
describe('midpoint', () => {
  // Input: UBC and Surrey
  // Expected output: the average of their latitudes and longitudes
  test('Averages the points', () => {
    expect(midpoint([UBC, SURREY])).toEqual({ latitude: (49.2606 + 49.1913) / 2, longitude: (-123.246 + -122.849) / 2 });
  });

  // Input: a single point
  // Expected output: that same point
  test('Is the point itself for one point', () => {
    expect(midpoint([UBC])).toEqual(UBC);
  });
});
