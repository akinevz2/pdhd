import { describe, expect, it } from "vitest";

import { sanitizeFolderSummaryContent } from "./assistantActions";

describe("sanitizeFolderSummaryContent", () => {
  it("removes internal evidence scaffolding from folder summaries", () => {
    const raw = [
      "=== folder entries (recursive) ===",
      "src",
      "",
      "=== sampled file contents (evidence only) ===",
      "...(truncated)",
      "Polished summary text.",
    ].join("\n");

    expect(sanitizeFolderSummaryContent(raw)).toBe(
      "src\n\nPolished summary text.",
    );
  });

  it("leaves normal content unchanged", () => {
    const raw = "Folder summary text with no scaffolding.";

    expect(sanitizeFolderSummaryContent(raw)).toBe(raw);
  });
});