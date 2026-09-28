import { useCallback, useState } from "react";
import TransactionDialog from "../components/TransactionDialog";
import Toast from "../components/Toast";

/**
 * Opening a transaction to re-file it, for any page that lists transactions. After a save the
 * one transaction is swapped in place; when a retailer rule changed others too, the page reloads.
 *
 * @returns `open(tx)` for the rows' onSelect, and `ui` to render (the dialog and its confirmation)
 */
export default function useTransactionEditing({ setTransactions, reload, currency, categories, accountNameOf }) {
  const [editing, setEditing] = useState(null);
  const [message, setMessage] = useState("");
  const clearMessage = useCallback(() => setMessage(""), []);

  const onSaved = (updated, alsoUpdated, retailer) => {
    setEditing(null);
    setTransactions((list) => list?.map((tx) => (tx.id === updated.id ? { ...tx, ...updated } : tx)));
    if (alsoUpdated > 0) reload();
    const category = updated.userCategory || updated.plaidCategory || "Uncategorised";
    setMessage(
      retailer
        ? `${retailer} will always be filed under ${category}` +
            (alsoUpdated > 0 ? ` (${alsoUpdated} other transaction${alsoUpdated === 1 ? "" : "s"} updated)` : "")
        : "Transaction updated"
    );
  };

  const ui = (
    <>
      {editing && (
        <TransactionDialog
          transaction={editing}
          currency={currency}
          categories={categories}
          accountName={accountNameOf?.(editing)}
          onClose={() => setEditing(null)}
          onSaved={onSaved}
        />
      )}
      <Toast message={message} onDone={clearMessage} />
    </>
  );

  return { open: setEditing, ui };
}
