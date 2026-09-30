import { describe, expect, it } from "vitest";
import { addDays, addMonths, isISODate, monthGrid, monthLabel, parseISO, toISO } from "./dates";

describe("dates", () => {
  it("round-trips a calendar date without shifting it", () => {
    expect(toISO(parseISO("2026-03-29"))).toBe("2026-03-29"); // the day the clocks go forward
    expect(toISO(parseISO("2026-10-25"))).toBe("2026-10-25"); // and back
  });

  it("adds days across month and year ends", () => {
    expect(addDays("2026-01-31", 1)).toBe("2026-02-01");
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
    expect(addDays("2028-03-01", -1)).toBe("2028-02-29");
  });

  it("adds months across the year end", () => {
    expect(addMonths("2026-12", 1)).toBe("2027-01");
    expect(addMonths("2026-01", -1)).toBe("2025-12");
  });

  it("builds a six-week, Monday-first month grid", () => {
    const grid = monthGrid(2026, 8); // September 2026 starts on a Tuesday
    expect(grid).toHaveLength(42);
    expect(grid[0]).toBe("2026-08-31");
    expect(grid[1]).toBe("2026-09-01");
  });

  it("recognises yyyy-MM-dd strings only", () => {
    expect(isISODate("2026-09-30")).toBe(true);
    expect(isISODate("30/09/2026")).toBe(false);
    expect(isISODate(undefined)).toBe(false);
  });

  it("names months in British English", () => {
    expect(monthLabel("2026-09")).toBe("September 2026");
  });
});
