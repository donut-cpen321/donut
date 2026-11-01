import { clearForecastCache, getForecast } from "../../src/external/weather";

const latitude = 49.27;
const longitude = -123.15;

// OpenWeather returns one entry every 3 hours
const gameTime = new Date("2026-11-03T18:00:00Z");
const entryAt = (offsetHours: number) =>
  Math.floor(gameTime.getTime() / 1000) + offsetHours * 60 * 60;

const forecastBody = () => ({
  list: [
    {
      dt: entryAt(-3),
      main: { temp: 12.1 },
      weather: [{ description: "few clouds", icon: "02d" }],
      pop: 0.1,
    },
    {
      dt: entryAt(0),
      main: { temp: 10.4 },
      weather: [{ description: "light rain", icon: "10d" }],
      pop: 0.65,
    },
    {
      dt: entryAt(3),
      main: { temp: 8.9 },
      weather: [{ description: "overcast clouds", icon: "04n" }],
    },
  ],
});

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const mockedFetch = jest.fn<Promise<Response>, Parameters<typeof fetch>>();

describe("Mocked: getForecast", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    clearForecastCache();
    global.fetch = mockedFetch as unknown as typeof fetch;
    process.env.OPENWEATHER_API_KEY = "test-openweather-key";
  });

  afterAll(() => {
    delete process.env.OPENWEATHER_API_KEY;
  });

  test("Returns the forecast entry closest to the given time", async () => {
    mockedFetch.mockResolvedValue(jsonResponse(forecastBody()));

    await expect(
      getForecast(latitude, longitude, new Date(gameTime.getTime() + 60 * 60 * 1000)),
    ).resolves.toEqual({
      at: gameTime,
      temperature: 10.4,
      description: "light rain",
      icon: "10d",
      precipitationChance: 0.65,
    });

    const [url] = mockedFetch.mock.calls[0] as [URL];
    expect(url.origin + url.pathname).toBe(
      "https://api.openweathermap.org/data/2.5/forecast",
    );
    expect(url.searchParams.get("lat")).toBe(String(latitude));
    expect(url.searchParams.get("lon")).toBe(String(longitude));
    expect(url.searchParams.get("units")).toBe("metric");
    expect(url.searchParams.get("appid")).toBe("test-openweather-key");
  });

  test("Uses 0 when an entry has no precipitation chance", async () => {
    mockedFetch.mockResolvedValue(jsonResponse(forecastBody()));

    const forecast = await getForecast(
      latitude,
      longitude,
      new Date(entryAt(3) * 1000),
    );

    expect(forecast?.precipitationChance).toBe(0);
  });

  test("Returns null for a time outside the 5-day forecast", async () => {
    mockedFetch.mockResolvedValue(jsonResponse(forecastBody()));

    await expect(
      getForecast(latitude, longitude, new Date(entryAt(5) * 1000)),
    ).resolves.toBeNull();
  });

  test("Returns null without calling OpenWeather when there is no API key", async () => {
    delete process.env.OPENWEATHER_API_KEY;

    await expect(getForecast(latitude, longitude, gameTime)).resolves.toBeNull();
    expect(mockedFetch).not.toHaveBeenCalled();
  });

  test("Reuses the cached forecast for the same location", async () => {
    mockedFetch.mockResolvedValue(jsonResponse(forecastBody()));

    await getForecast(latitude, longitude, gameTime);
    await getForecast(latitude, longitude, new Date(entryAt(3) * 1000));

    expect(mockedFetch).toHaveBeenCalledTimes(1);
  });

  test("Fetches a separate forecast for another location", async () => {
    mockedFetch.mockImplementation(() =>
      Promise.resolve(jsonResponse(forecastBody())),
    );

    await getForecast(latitude, longitude, gameTime);
    await getForecast(49.24, -123.11, gameTime);

    expect(mockedFetch).toHaveBeenCalledTimes(2);
  });

  test("Fetches the forecast again after the cache expires", async () => {
    const now = jest.spyOn(Date, "now").mockReturnValue(gameTime.getTime());
    mockedFetch.mockImplementation(() =>
      Promise.resolve(jsonResponse(forecastBody())),
    );

    await getForecast(latitude, longitude, gameTime);
    now.mockReturnValue(gameTime.getTime() + 11 * 60 * 1000);
    await getForecast(latitude, longitude, gameTime);

    expect(mockedFetch).toHaveBeenCalledTimes(2);
    now.mockRestore();
  });

  test.each([
    ["an error status", () => Promise.resolve(jsonResponse({ message: "Invalid API key" }, 401))],
    ["a response in the wrong shape", () => Promise.resolve(jsonResponse({ list: [{ dt: "soon" }] }))],
    ["a network error", () => Promise.reject(new Error("timeout"))],
  ])("Returns null and retries next time after %s", async (_description, failure) => {
    const consoleError = jest
      .spyOn(console, "error")
      .mockImplementation(() => undefined);
    mockedFetch.mockImplementationOnce(failure);

    await expect(getForecast(latitude, longitude, gameTime)).resolves.toBeNull();
    expect(consoleError).toHaveBeenCalled();

    // The failure is not cached
    mockedFetch.mockResolvedValue(jsonResponse(forecastBody()));
    await expect(getForecast(latitude, longitude, gameTime)).resolves.toMatchObject({
      temperature: 10.4,
    });
    expect(mockedFetch).toHaveBeenCalledTimes(2);
    consoleError.mockRestore();
  });
});
