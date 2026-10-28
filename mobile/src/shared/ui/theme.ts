import { useColorScheme } from "react-native";

const palette = {
  light: { background: "#ffffff", surface: "#f4f6f8", text: "#1b1d21", muted: "#5b6270", primary: "#2457d6", onPrimary: "#ffffff", danger: "#c42b1c", border: "#d9dde3" },
  dark: { background: "#0f1115", surface: "#1a1d23", text: "#eef0f3", muted: "#a2a9b6", primary: "#7aa2ff", onPrimary: "#0f1115", danger: "#ff7b6e", border: "#2c313a" },
} as const;

export const spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 } as const;
export const radius = { sm: 6, md: 10, lg: 16 } as const;
export const fontSize = { body: 17, small: 14, title: 28, subtitle: 20 } as const;
/** Erişilebilirlik: dokunulabilir öğeler en az 44×44 (kurallar.md §6). */
export const MIN_TOUCH_SIZE = 44;

export type ThemeColors = { [K in keyof (typeof palette)["light"]]: string };

export function useThemeColors(): ThemeColors {
  return useColorScheme() === "dark" ? palette.dark : palette.light;
}
