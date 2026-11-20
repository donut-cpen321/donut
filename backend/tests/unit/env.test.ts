import type { env as Env } from "../../src/config/env";

// The environment is read when config/env.ts is first imported, so each test imports a fresh copy with its own
// variables. dotenv is turned off so a local backend/.env can't fill in what a test leaves out.
jest.mock("dotenv/config", () => ({}));

const saved = { ...process.env };

/**
 * @param vars the variables to set; undefined removes one
 * @returns config/env.ts's env, read with those variables
 */
function loadEnv(vars: Record<string, string | undefined>): typeof Env {
  const merged: Record<string, string | undefined> = {
    ...saved,
    DATABASE_URL: "postgresql://localhost/test",
    PORT: undefined,
    ...vars,
  };
  process.env = Object.fromEntries(
    Object.entries(merged).filter((entry): entry is [string, string] => entry[1] !== undefined),
  );
  let loaded: typeof Env | undefined;
  jest.isolateModules(() => {
    loaded = jest.requireActual<{ env: typeof Env }>("../../src/config/env").env;
  });
  if (loaded === undefined) throw new Error("config/env.ts did not load");
  return loaded;
}

afterEach(() => {
  process.env = { ...saved };
});

// Module config/env.ts
describe("Unit: config/env", () => {
  // Input: no PORT, then PORT=8080
  // Expected output: port 3000, then 8080
  test("Uses PORT, or 3000 when it isn't set", () => {
    expect(loadEnv({ PORT: undefined }).port).toBe(3000);
    expect(loadEnv({ PORT: "" }).port).toBe(3000);
    expect(loadEnv({ PORT: "8080" }).port).toBe(8080);
  });

  // Input: PORT that isn't a number, and one out of range
  // Expected output: the server refuses to start
  test.each(["abc", "0", "70000"])("Rejects PORT=%s", (port) => {
    expect(() => loadEnv({ PORT: port })).toThrow(`Invalid PORT: ${port}`);
  });

  // Input: no DATABASE_URL, or an empty one
  // Expected output: the server refuses to start
  test.each([undefined, ""])("Rejects a missing DATABASE_URL (%p)", (url) => {
    expect(() => loadEnv({ DATABASE_URL: url })).toThrow("Missing DATABASE_URL");
  });

  // Input: all three FIREBASE_* variables, with the private key's newlines escaped as in a .env file
  // Expected output: the Firebase credentials, with real newlines in the key
  test("Reads the Firebase credentials when all are set", () => {
    const loaded = loadEnv({
      FIREBASE_PROJECT_ID: "project",
      FIREBASE_CLIENT_EMAIL: "push@project.iam.gserviceaccount.com",
      FIREBASE_PRIVATE_KEY: "line1\\nline2",
    });

    expect(loaded.firebase).toEqual({
      projectId: "project",
      clientEmail: "push@project.iam.gserviceaccount.com",
      privateKey: "line1\nline2",
    });
  });

  // Input: only one of the FIREBASE_* variables
  // Expected output: no Firebase credentials, so pushes are off
  test("Turns pushes off unless every Firebase variable is set", () => {
    const loaded = loadEnv({
      FIREBASE_PROJECT_ID: "project",
      FIREBASE_CLIENT_EMAIL: undefined,
      FIREBASE_PRIVATE_KEY: undefined,
    });

    expect(loaded.firebase).toBeUndefined();
  });
});
