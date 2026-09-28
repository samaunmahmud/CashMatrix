/** The category a transaction is filed under: the user's own if they chose one, otherwise the bank's. */
export const categoryOf = (tx) => tx.userCategory || tx.plaidCategory || "Uncategorised";

/** Every category in use, most used first, for pickers and filters. */
export function knownCategories(transactions) {
  const counts = new Map();
  for (const tx of transactions) {
    for (const name of [tx.userCategory, tx.plaidCategory]) {
      if (name) counts.set(name, (counts.get(name) ?? 0) + 1);
    }
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([name]) => name);
}
