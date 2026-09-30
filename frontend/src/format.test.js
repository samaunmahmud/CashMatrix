import { describe, expect, it } from "vitest";
import { formatMoney } from "./format";

describe("formatMoney", () => {
  it("shows pounds by default, with thousands separators and pence", () => {
    expect(formatMoney(1842.37)).toBe("£1,842.37");
    expect(formatMoney(5)).toBe("£5.00");
  });

  it("treats a missing amount as zero", () => {
    expect(formatMoney(null)).toBe("£0.00");
    expect(formatMoney(undefined)).toBe("£0.00");
  });

  it("uses an account's own currency when it has one", () => {
    expect(formatMoney(12, "USD")).toBe("$12.00");
    expect(formatMoney(12, "EUR")).toBe("€12.00");
    expect(formatMoney(12, null)).toBe("£12.00");
  });
});
