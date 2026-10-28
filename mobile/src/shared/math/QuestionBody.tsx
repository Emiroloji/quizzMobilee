import { type Content, renderDocument, toPlainText } from "@soruhavuzu/content-schema";
import { useMemo, useState } from "react";
import { StyleSheet } from "react-native";
import { WebView, type WebViewMessageEvent } from "react-native-webview";

import { fontSize, useThemeColors } from "../ui";
import { assetUrl } from "./assetUrl";

interface Props {
  content: Content;
}

// İçerik yüksekliğini ölçüp native tarafa bildirir; CSP'ye tabi değildir (native enjeksiyon).
const MEASURE_HEIGHT = `
  (function () {
    function post() { window.ReactNativeWebView.postMessage(String(document.documentElement.scrollHeight)); }
    new ResizeObserver(post).observe(document.body);
    post();
  })();
  true;
`;

/**
 * LaTeX'in render edildiği TEK yer (kurallar.md A12). İçerik content-schema'nın renderDocument'ı ile
 * fontları gömülü bağımsız HTML'e çevrilir; ağ gerekmez (offline). WebView başka adrese gidemez.
 */
export function QuestionBody({ content }: Props) {
  const colors = useThemeColors();
  const [height, setHeight] = useState(1);
  const html = useMemo(
    () =>
      renderDocument(content, {
        assetUrl,
        theme: { text: colors.text, background: "transparent", fontSizePx: fontSize.body },
      }),
    [content, colors.text],
  );
  const label = useMemo(() => toPlainText(content), [content]);

  const onMessage = (event: WebViewMessageEvent) => {
    const next = Number(event.nativeEvent.data);
    if (Number.isFinite(next) && next > 0) setHeight(Math.ceil(next));
  };

  return (
    <WebView
      accessible
      accessibilityLabel={label}
      source={{ html }}
      originWhitelist={["about:blank"]}
      onShouldStartLoadWithRequest={(request) => request.url === "about:blank"}
      injectedJavaScript={MEASURE_HEIGHT}
      onMessage={onMessage}
      scrollEnabled={false}
      setSupportMultipleWindows={false}
      allowFileAccess={false}
      javaScriptCanOpenWindowsAutomatically={false}
      style={[styles.webview, { height }]}
    />
  );
}

const styles = StyleSheet.create({
  webview: { width: "100%", backgroundColor: "transparent" },
});
