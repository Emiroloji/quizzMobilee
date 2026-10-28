import { createBrowserRouter } from "react-router";

import { AuthCallback, LoginPage } from "@/features/auth";
import { MeSummary } from "@/features/profile";

import { Layout } from "./Layout";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/auth/callback", element: <AuthCallback /> },
  {
    element: <Layout />,
    children: [
      { index: true, element: <MeSummary /> },
      // KaTeX ve gömülü fontlar (~1 MB) yalnızca önizleme açılınca indirilir
      { path: "preview", lazy: async () => ({ Component: (await import("@/features/preview")).PreviewPage }) },
    ],
  },
]);
