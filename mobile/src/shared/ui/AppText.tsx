import type { ReactNode } from "react";
import { StyleSheet, Text, type TextStyle } from "react-native";

import { fontSize, useThemeColors } from "./theme";

type Variant = "title" | "subtitle" | "body" | "small" | "error";

interface Props {
  children: ReactNode;
  variant?: Variant;
  style?: TextStyle;
}

export function AppText({ children, variant = "body", style }: Props) {
  const colors = useThemeColors();
  const color = variant === "small" ? colors.muted : variant === "error" ? colors.danger : colors.text;
  return <Text style={[styles[variant], { color }, style]}>{children}</Text>;
}

const styles = StyleSheet.create({
  title: { fontSize: fontSize.title, fontWeight: "700" },
  subtitle: { fontSize: fontSize.subtitle, fontWeight: "600" },
  body: { fontSize: fontSize.body },
  small: { fontSize: fontSize.small },
  error: { fontSize: fontSize.small },
});
