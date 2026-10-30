import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AuthProvider, useAuth } from "./AuthProvider";

const { userManager } = vi.hoisted(() => ({
  userManager: {
    getUser: vi.fn(() => Promise.resolve(null)),
    removeUser: vi.fn(() => Promise.resolve()),
    signoutRedirect: vi.fn(() => Promise.resolve()),
    signinRedirect: vi.fn(() => Promise.resolve()),
    signinRedirectCallback: vi.fn(() => Promise.resolve()),
  },
}));

vi.mock("../api/oidc", () => ({
  userManager,
  currentOidcSession: () => Promise.resolve({ accessToken: "t", expiresAt: Date.now() + 3_600_000 }),
}));
vi.mock("@/shared/api", () => ({ queryClient: { clear: vi.fn() }, setTokenProvider: vi.fn() }));

function Probe() {
  const { state, signOut } = useAuth();
  return (
    <button type="button" onClick={() => void signOut()}>
      {state.status}
    </button>
  );
}

describe("AuthProvider (oidc)", () => {
  afterEach(cleanup);

  it("çıkışta yerel oturumu temizler ve Logto oturumunu da kapatır (signoutRedirect)", async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByText("signedIn")).toBeTruthy());

    fireEvent.click(screen.getByText("signedIn"));

    await waitFor(() => expect(userManager.signoutRedirect).toHaveBeenCalledOnce());
    expect(screen.getByText("signedOut")).toBeTruthy();
  });
});
