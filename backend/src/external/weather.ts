// OpenWeather: forecast for a game.
import { z } from "zod";

const FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";

// The free forecast has one entry every 3 hours for the next 5 days
const FORECAST_STEP_MS = 3 * 60 * 60 * 1000;
const CACHE_TTL_MS = 10 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 2000;

const forecastResponseSchema = z.object({
  list: z.array(
    z.object({
      dt: z.number(),
      main: z.object({ temp: z.number() }),
      weather: z
        .array(z.object({ description: z.string(), icon: z.string() }))
        .min(1),
      pop: z.number().optional(),
    }),
  ),
});

type ForecastEntry = z.infer<typeof forecastResponseSchema>["list"][number];

export interface Forecast {
  /** The time the forecast is for, the closest 3-hour step to the game */
  at: Date;
  /** Degrees Celsius */
  temperature: number;
  description: string;
  /** OpenWeather icon code, e.g. "10d" */
  icon: string;
  /** Probability of precipitation, from 0 to 1 */
  precipitationChance: number;
}

// One forecast request per court location every CACHE_TTL_MS, shared by every feed request
const cache = new Map<string, { expiresAt: number; entries: Promise<ForecastEntry[]> }>();

/**
 * Fetches the 5-day forecast for a location from OpenWeather
 * @param latitude the latitude of the location
 * @param longitude the longitude of the location
 * @param apiKey the OpenWeather API key
 * @returns the forecast entries, one every 3 hours
 */
async function fetchForecastEntries(
  latitude: number,
  longitude: number,
  apiKey: string,
): Promise<ForecastEntry[]> {
  const url = new URL(FORECAST_URL);
  url.searchParams.set("lat", String(latitude));
  url.searchParams.set("lon", String(longitude));
  url.searchParams.set("units", "metric");
  url.searchParams.set("appid", apiKey);

  const response = await fetch(url, {
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (!response.ok) {
    throw new Error(`OpenWeather responded with ${response.status}`);
  }

  return forecastResponseSchema.parse(await response.json()).list;
}

/**
 * Gets the forecast for a location at a given time
 * @param latitude the latitude of the location
 * @param longitude the longitude of the location
 * @param at the time the forecast is for
 * @returns the forecast, or null if there is no API key, the time is outside the
 * 5-day forecast, or OpenWeather cannot be reached
 */
export async function getForecast(
  latitude: number,
  longitude: number,
  at: Date,
): Promise<Forecast | null> {
  const apiKey = process.env.OPENWEATHER_API_KEY;
  if (!apiKey) {
    return null;
  }

  const key = `${latitude},${longitude}`;
  let cached = cache.get(key);
  if (cached === undefined || cached.expiresAt <= Date.now()) {
    cached = {
      expiresAt: Date.now() + CACHE_TTL_MS,
      entries: fetchForecastEntries(latitude, longitude, apiKey),
    };
    cache.set(key, cached);
  }

  let entries: ForecastEntry[];
  try {
    entries = await cached.entries;
  } catch (e) {
    // Try again on the next request instead of caching the failure
    if (cache.get(key) === cached) {
      cache.delete(key);
    }
    console.error(e);
    return null;
  }

  // The closest entry, as long as it is within half a step of the game
  let closest: ForecastEntry | undefined;
  for (const entry of entries) {
    const distance = Math.abs(entry.dt * 1000 - at.getTime());
    if (
      distance <= FORECAST_STEP_MS / 2 &&
      (closest === undefined ||
        distance < Math.abs(closest.dt * 1000 - at.getTime()))
    ) {
      closest = entry;
    }
  }

  if (closest === undefined) {
    return null;
  }

  // weather has at least one item, checked by forecastResponseSchema
  const [weather] = closest.weather as [ForecastEntry["weather"][number]];
  return {
    at: new Date(closest.dt * 1000),
    temperature: closest.main.temp,
    description: weather.description,
    icon: weather.icon,
    precipitationChance: closest.pop ?? 0,
  };
}

/**
 * Empties the forecast cache, so tests start without cached forecasts
 */
export function clearForecastCache(): void {
  cache.clear();
}
