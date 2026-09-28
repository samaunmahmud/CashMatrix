import { useEffect, useRef, useState } from "react";
import { goalApi } from "../api";
import { errorMessage } from "../errors";
import { formatMoney } from "../format";

const QUICK = [10, 25, 50, 100];

/** Record money put towards a goal, or taken back out. */
export default function GoalMoneyDialog({ goal, mode: initialMode = "add", currency, onClose, onSaved }) {
  const dialogRef = useRef(null);
  const [mode, setMode] = useState(initialMode);
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setSaving(true);
    const value = Number(amount);
    try {
      const res = await goalApi.contribute(goal.id, mode === "add" ? value : -value);
      onSaved(res.data, mode === "add" ? value : -value);
    } catch (err) {
      setError(errorMessage(err));
      setSaving(false);
    }
  };

  const quick = mode === "add" && goal.remaining > 0 ? [...QUICK.filter((q) => q < goal.remaining), goal.remaining] : QUICK;

  return (
    <dialog
      ref={dialogRef}
      className="dialog dialog-narrow"
      aria-labelledby="goal-money-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === dialogRef.current) onClose();
      }}
    >
      <form className="dialog-body" onSubmit={submit}>
        <h2 id="goal-money-title">{goal.emoji ? `${goal.emoji} ` : ""}{goal.name}</h2>
        <p className="muted goal-money-status">
          {formatMoney(goal.savedAmount, currency)} saved of {formatMoney(goal.targetAmount, currency)}
        </p>

        <fieldset className="segmented">
          <legend className="sr-only">Add or take out</legend>
          <div className="segmented-options">
            {[["add", "Add money"], ["take", "Take out"]].map(([value, label]) => (
              <label key={value}>
                <input type="radio" name="goal-money-mode" value={value} checked={mode === value} onChange={() => setMode(value)} />
                <span>{label}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <label className="field">
          <span>Amount</span>
          <input
            className="input input-large"
            type="number"
            inputMode="decimal"
            min="0.01"
            max={mode === "take" ? goal.savedAmount : undefined}
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
            autoFocus
            placeholder="0.00"
          />
        </label>
        <div className="quick-amounts" role="group" aria-label="Quick amounts">
          {(mode === "take" ? [...QUICK.filter((q) => q < goal.savedAmount), goal.savedAmount].filter((q) => q > 0) : quick).map((q) => (
            <button key={q} type="button" className="chip-button" onClick={() => setAmount(String(q))}>
              {q === goal.remaining && mode === "add" ? `All ${formatMoney(q, currency)}` : q === goal.savedAmount && mode === "take" ? `All ${formatMoney(q, currency)}` : formatMoney(q, currency)}
            </button>
          ))}
        </div>
        <p className="field-hint">
          CashMatrix doesn't move money. Put it aside in your savings account, then record it here.
        </p>

        {error && <p className="error-text" role="alert">{error}</p>}

        <div className="dialog-actions">
          <button type="button" className="btn btn-outline" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn" disabled={saving}>
            {saving ? "Saving…" : mode === "add" ? "Add" : "Take out"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
