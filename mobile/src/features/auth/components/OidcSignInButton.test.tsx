import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import { OidcSignInButton } from "./OidcSignInButton";

const mockPromptAsync = jest.fn();
const mockCompleteSignIn = jest.fn();

jest.mock("expo-auth-session", () => ({
  useAuthRequest: () => [{ codeVerifier: "v" }, null, mockPromptAsync],
}));
jest.mock("../api/oidc", () => ({
  discovery: () => ({}),
  exchangeCode: jest.fn(),
  redirectUri: "soruhavuzu://auth/callback",
  SCOPES: ["openid"],
}));
jest.mock("../hooks/AuthProvider", () => ({ useAuth: () => ({ completeSignIn: mockCompleteSignIn }) }));

async function press(onError: jest.Mock) {
  await render(<OidcSignInButton issuer="https://auth.example/oidc" clientId="c" resource="r" label="Giriş" onError={onError} />);
  await fireEvent.press(screen.getByRole("button", { name: "Giriş" }));
}

describe("OidcSignInButton", () => {
  beforeEach(() => jest.clearAllMocks());

  it("Logto hata döndürürse (result.type = error) kullanıcıya hatayı bildirir", async () => {
    mockPromptAsync.mockResolvedValue({ type: "error", error: new Error("access_denied") });
    const onError = jest.fn();
    await press(onError);
    await waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(mockCompleteSignIn).not.toHaveBeenCalled();
  });

  it("kullanıcı vazgeçerse (cancel/dismiss) hata göstermez", async () => {
    mockPromptAsync.mockResolvedValue({ type: "cancel" });
    const onError = jest.fn();
    await press(onError);
    await waitFor(() => expect(mockPromptAsync).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByRole("button", { name: "Giriş" })).toBeTruthy());
    expect(onError).not.toHaveBeenCalled();
  });
});
