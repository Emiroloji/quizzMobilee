import { render, screen } from "@testing-library/react-native";
import type { ReactNode } from "react";

import { QuestionBody } from "./QuestionBody";
import { SAMPLE_QUESTION } from "./samples";

const captured: { html?: string; navigationAllowed?: (request: { url: string }) => boolean } = {};

jest.mock("react-native-webview", () => {
  const { View } = jest.requireActual<typeof import("react-native")>("react-native");
  return {
    WebView: (props: {
      source: { html: string };
      onShouldStartLoadWithRequest: (request: { url: string }) => boolean;
      accessibilityLabel: string;
      children?: ReactNode;
    }) => {
      captured.html = props.source.html;
      captured.navigationAllowed = props.onShouldStartLoadWithRequest;
      return <View accessibilityLabel={props.accessibilityLabel} testID="question-webview" />;
    },
  };
});

describe("QuestionBody", () => {
  beforeEach(async () => {
    await render(<QuestionBody content={SAMPLE_QUESTION} />);
  });

  it("örnek soruyu fontları gömülü, offline KaTeX belgesi olarak verir", () => {
    expect(captured.html).toContain("data:font/woff2;base64,");
    expect(captured.html).toContain('class="katex"');
    expect(captured.html).toContain('class="katex-display"');
    expect(captured.html).not.toMatch(/https?:\/\/[^"']*\.(css|js|woff2?)/);
  });

  it("ekran okuyucuya düz metin verir", () => {
    expect(screen.getByTestId("question-webview").props.accessibilityLabel).toContain("\\lim_{x\\to 1} f(x)");
  });

  it("WebView başka adrese gidemez", () => {
    expect(captured.navigationAllowed?.({ url: "about:blank" })).toBe(true);
    expect(captured.navigationAllowed?.({ url: "https://kotu.example" })).toBe(false);
  });
});
