import { useEffect, useRef, useState } from "react";
import { transactionApi } from "../api";
import { mediumDate } from "../dates";
import { errorMessage } from "../errors";
import { formatMoney } from "../format";
import Avatar from "./Avatar";

/**
 * One transaction up close: re-file it under another category, add a note, and optionally
 * file everything from the same retailer the same way from now on.
 */
export default function TransactionDialog({ transaction: tx, currency, categories = [], accountName, onClose, onSaved }) {
  const dialogRef = useRef(null);
  const [category, setCategory] = useState(tx.userCategory ?? "");
  const [note, setNote] = useState(tx.note ?? "");
  const [applyToRetailer, setApplyToRetailer] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);

  const retailer = tx.retailer || tx.merchant || tx.name;
  const bankCategory = tx.plaidCategory || "Uncategorised";

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setSaving(true);
    try {
      const res = await transactionApi.update(tx.id, { category: category.trim(), note: note.trim(), applyToRetailer });
      onSaved(res.data.transaction, res.data.alsoUpdated, applyToRetailer ? retailer : null);
    } catch (err) {
      setError(errorMessage(err));
      setSaving(false);
    }
  };

  return (
    <dialog
      ref={dialogRef}
      className="dialog"
      aria-labelledby="tx-dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === dialogRef.current) onClose(); // click on the backdrop
      }}
    >
      <form className="dialog-body" onSubmit={submit}>
        <div className="tx-detail">
          <Avatar name={tx.merchant || tx.name} size={52} />
          <div className="tx-detail-text">
            <h2 id="tx-dialog-title">{tx.merchant || tx.name}</h2>
            <p className="muted">
              {mediumDate(tx.transactionDate)}
              {accountName && <> · {accountName}</>}
              {tx.pending && <> · Pending</>}
            </p>
          </div>
          <p className={`tx-detail-amount${tx.amount < 0 ? " tx-in" : ""}`}>
            {tx.amount < 0 ? "+" : "−"}
            {formatMoney(Math.abs(tx.amount), currency)}
          </p>
        </div>
        {tx.name !== (tx.merchant || tx.name) && (
          <p className="tx-detail-raw"><span className="muted">On your statement:</span> {tx.name}</p>
        )}

        <label className="field">
          <span>Category</span>
          <input
            className="input"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            list="tx-categories"
            maxLength={60}
            placeholder={bankCategory}
          />
          <datalist id="tx-categories">
            {categories.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>
          <span className="field-hint">
            Your bank calls it <strong>{bankCategory}</strong>. Leave empty to use that.
          </span>
        </label>

        <label className="check-row">
          <input type="checkbox" checked={applyToRetailer} onChange={(e) => setApplyToRetailer(e.target.checked)} />
          <span>
            <strong>Always use this for {retailer}</strong>
            <span className="field-hint">Re-files your other {retailer} transactions too, and new ones as they arrive.</span>
          </span>
        </label>

        <label className="field">
          <span>Note</span>
          <textarea
            className="input"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={200}
            rows={2}
            placeholder="What was it for?"
          />
        </label>

        {error && <p className="error-text" role="alert">{error}</p>}

        <div className="dialog-actions">
          <button type="button" className="btn btn-outline" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn" disabled={saving}>{saving ? "Saving…" : "Save"}</button>
        </div>
      </form>
    </dialog>
  );
}
