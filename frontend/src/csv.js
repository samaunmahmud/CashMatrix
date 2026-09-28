import { categoryOf } from "./categories";

// Quotes a value when it needs it, and stops spreadsheet apps treating text that starts with
// = + - @ as a formula (a statement description is not something to run).
function cell(value) {
  let text = value == null ? "" : String(value);
  if (/^[=+\-@\t\r]/.test(text) && !/^-?\d+(\.\d+)?$/.test(text)) text = `'${text}`;
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/**
 * Transactions as CSV, the way a spreadsheet expects a statement: money in positive and
 * money out negative (the opposite of the bank feed's own sign).
 */
export function transactionsCsv(transactions) {
  const header = ["Date", "Merchant", "Description", "Category", "Account", "Amount", "Pending", "Note"];
  const rows = transactions.map((tx) => [
    tx.transactionDate,
    tx.merchant || tx.name,
    tx.name,
    categoryOf(tx),
    tx.accountName ?? "",
    (-tx.amount).toFixed(2),
    tx.pending ? "Yes" : "No",
    tx.note ?? "",
  ]);
  // The byte order mark makes Excel read the file as UTF-8, so "£" and accents survive.
  return "﻿" + [header, ...rows].map((row) => row.map(cell).join(",")).join("\r\n") + "\r\n";
}

export function downloadFile(filename, text, type = "text/csv;charset=utf-8") {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
