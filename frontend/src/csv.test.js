import { describe, expect, it } from "vitest";
import { transactionsCsv } from "./csv";

const tx = (overrides) => ({
  transactionDate: "2026-09-20",
  name: "TESCO STORES 2041",
  merchant: "Tesco",
  plaidCategory: "Groceries",
  accountName: "Current Account",
  amount: 12.5,
  pending: false,
  ...overrides,
});

const lines = (csv) => csv.replace(/^\uFEFF/, "").trimEnd().split("\r\n");

describe("transactionsCsv", () => {
  it("starts with a byte order mark and a header, and ends each line with CRLF", () => {
    const csv = transactionsCsv([]);
    expect(csv.startsWith("\uFEFF")).toBe(true);
    expect(csv.endsWith("\r\n")).toBe(true);
    expect(lines(csv)[0]).toBe("Date,Merchant,Description,Category,Account,Amount,Pending,Note");
  });

  it("shows money out as negative and money in as positive", () => {
    const [, out, into] = lines(transactionsCsv([tx({ amount: 12.5 }), tx({ amount: -2650, name: "Salary", merchant: "" })]));
    expect(out).toBe("2026-09-20,Tesco,TESCO STORES 2041,Groceries,Current Account,-12.50,No,");
    expect(into.split(",")[5]).toBe("2650.00");
    expect(into.split(",")[1]).toBe("Salary");
  });

  it("prefers the user's own category and keeps notes", () => {
    const [, row] = lines(transactionsCsv([tx({ userCategory: "Treats", note: "Birthday cake", pending: true })]));
    expect(row).toBe("2026-09-20,Tesco,TESCO STORES 2041,Treats,Current Account,-12.50,Yes,Birthday cake");
  });

  it("quotes commas, quotes and line breaks", () => {
    const [, row] = lines(transactionsCsv([tx({ note: 'Said "thanks", then left' })]));
    expect(row.endsWith(',"Said ""thanks"", then left"')).toBe(true);
  });

  it("stops text being run as a spreadsheet formula but leaves numbers alone", () => {
    const [, row] = lines(transactionsCsv([tx({ name: "=HYPERLINK(\"x\")", merchant: "@SUM", note: "+44 call" })]));
    const cells = row.split(",");
    expect(cells[1]).toBe("'@SUM");
    expect(cells[2]).toBe(`"'=HYPERLINK(""x"")"`);
    expect(cells[5]).toBe("-12.50");
    expect(cells[7]).toBe("'+44 call");
  });
});
