import { OAuth2Client } from "google-auth-library";
import { validateToken } from "../../src/users/helpers/validateToken";

// Google's token check is mocked: a real one needs an ID token Google signed for this app
jest.mock("google-auth-library", () => ({ OAuth2Client: jest.fn() }));

const verifyIdToken = jest.fn();
const clientId = "client-id.apps.googleusercontent.com";

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(OAuth2Client).mockImplementation(() => ({ verifyIdToken }) as unknown as OAuth2Client);
  process.env.GOOGLE_CLIENT_ID = clientId;
});

afterAll(() => {
  delete process.env.GOOGLE_CLIENT_ID;
});

// Function validateToken
describe("Mocked: validateToken", () => {
  // Input: a token Google verifies for this app
  // Expected behavior: Google is asked to check it against the app's client ID
  // Expected output: the account's subject, email, name and photo
  test("Returns the profile of a verified token", async () => {
    verifyIdToken.mockResolvedValue({
      getPayload: () => ({ sub: "sub-1", email: "a@example.com", name: "A", picture: "https://example.com/a.png" }),
    });

    await expect(validateToken("id-token")).resolves.toEqual({
      sub: "sub-1",
      email: "a@example.com",
      name: "A",
      picture: "https://example.com/a.png",
    });
    expect(verifyIdToken).toHaveBeenCalledWith({ idToken: "id-token", audience: clientId });
  });

  // Input: a verified token with no payload
  // Expected output: null
  test("Returns null when the token has no payload", async () => {
    verifyIdToken.mockResolvedValue({ getPayload: () => undefined });

    await expect(validateToken("id-token")).resolves.toBeNull();
  });

  // Input: a token Google rejects (expired, wrong audience, bad signature)
  // Expected behavior: the error is logged
  // Expected output: null
  test("Returns null when Google rejects the token", async () => {
    const logged = jest.spyOn(console, "error").mockImplementation(() => undefined);
    verifyIdToken.mockRejectedValue(new Error("Token used too late"));

    await expect(validateToken("id-token")).resolves.toBeNull();
    expect(logged).toHaveBeenCalled();
    logged.mockRestore();
  });

  // Input: no GOOGLE_CLIENT_ID set
  // Expected behavior: Google isn't asked, and the missing setting is logged
  // Expected output: null
  test("Returns null when GOOGLE_CLIENT_ID is missing", async () => {
    delete process.env.GOOGLE_CLIENT_ID;
    const logged = jest.spyOn(console, "error").mockImplementation(() => undefined);

    await expect(validateToken("id-token")).resolves.toBeNull();
    expect(OAuth2Client).not.toHaveBeenCalled();
    logged.mockRestore();
  });
});
