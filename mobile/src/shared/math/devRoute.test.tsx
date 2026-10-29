import { render, screen } from "@testing-library/react-native";

import SampleQuestionRoute from "../../../app/dev/question-body";

jest.mock("expo-router", () => ({
  Redirect: ({ href }: { href: string }) => {
    const { Text: MockText } = jest.requireActual<typeof import("react-native")>("react-native");
    return <MockText testID="redirect">{href}</MockText>;
  },
}));
jest.mock("./QuestionBody", () => {
  const { Text: MockText } = jest.requireActual<typeof import("react-native")>("react-native");
  return { QuestionBody: () => <MockText testID="question-body" /> };
});

describe("geliştirici örnek soru ekranı", () => {
  const original = __DEV__;
  afterEach(() => {
    Reflect.set(globalThis, "__DEV__", original);
  });

  it("geliştirmede örnek soruyu gösterir", async () => {
    await render(<SampleQuestionRoute />);
    expect(screen.queryByTestId("question-body")).not.toBeNull();
  });

  it("release build'de deep link ile açılamaz, ana sayfaya yönlendirir", async () => {
    Reflect.set(globalThis, "__DEV__", false);
    await render(<SampleQuestionRoute />);
    expect(screen.getByTestId("redirect").props.children).toBe("/");
    expect(screen.queryByTestId("question-body")).toBeNull();
  });
});
