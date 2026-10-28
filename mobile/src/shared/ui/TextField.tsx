import { StyleSheet, TextInput, View } from "react-native";

import { AppText } from "./AppText";
import { fontSize, MIN_TOUCH_SIZE, radius, spacing, useThemeColors } from "./theme";

interface Props {
  label: string;
  hint?: string;
  value: string;
  onChangeText: (value: string) => void;
}

export function TextField({ label, hint, value, onChangeText }: Props) {
  const colors = useThemeColors();
  return (
    <View style={styles.container}>
      <AppText variant="small">{label}</AppText>
      <TextInput
        accessibilityLabel={label}
        accessibilityHint={hint}
        autoCapitalize="none"
        autoCorrect={false}
        value={value}
        onChangeText={onChangeText}
        style={[styles.input, { color: colors.text, borderColor: colors.border, backgroundColor: colors.surface }]}
      />
      {hint ? <AppText variant="small">{hint}</AppText> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.xs },
  input: {
    minHeight: MIN_TOUCH_SIZE,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    fontSize: fontSize.body,
  },
});
