import { useEffect, useRef, useState } from "react";
import { goalApi } from "../api";
import { todayISO } from "../dates";
import { errorMessage } from "../errors";

const GOAL_EMOJIS = ["🏖️", "🏠", "🚗", "🎁", "💍", "🎓", "💻", "📱", "🛟", "🐯", "✈️", "👶"];

/** Add or edit a savings goal. */
export default function GoalDialog({ goal, initial, onClose, onSaved }) {
  const dialogRef = useRef(null);
  const isEdit = Boolean(goal);
  const start = goal ?? initial ?? {};

  const [form, setForm] = useState(() => ({
    name: start.name ?? "",
    emoji: start.emoji ?? "",
    targetAmount: start.targetAmount != null ? String(start.targetAmount) : "",
    targetDate: start.targetDate ?? "",
  }));
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setSaving(true);
    const payload = {
      name: form.name.trim(),
      emoji: form.emoji || null,
      targetAmount: Number(form.targetAmount),
      targetDate: form.targetDate || null,
    };
    try {
      const res = isEdit ? await goalApi.update(goal.id, payload) : await goalApi.create(payload);
      onSaved(res.data);
    } catch (err) {
      setError(errorMessage(err));
      setSaving(false);
    }
  };

  return (
    <dialog
      ref={dialogRef}
      className="dialog"
      aria-labelledby="goal-dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === dialogRef.current) onClose();
      }}
    >
      <form className="dialog-body" onSubmit={submit}>
        <h2 id="goal-dialog-title">{isEdit ? "Edit goal" : "New savings goal"}</h2>

        <label className="field">
          <span>What are you saving for?</span>
          <input
            className="input"
            value={form.name}
            onChange={set("name")}
            maxLength={60}
            required
            autoFocus
            placeholder="e.g. Summer holiday"
          />
        </label>

        <fieldset className="emoji-picker">
          <legend className="field-label">Icon <span className="muted">(optional)</span></legend>
          <div className="emoji-options">
            {GOAL_EMOJIS.map((emoji) => (
              <label key={emoji}>
                <input
                  type="radio"
                  name="goal-emoji"
                  value={emoji}
                  checked={form.emoji === emoji}
                  onChange={set("emoji")}
                  onClick={() => form.emoji === emoji && setForm((f) => ({ ...f, emoji: "" }))}
                />
                <span aria-hidden="true">{emoji}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <div className="field-row">
          <label className="field">
            <span>Target</span>
            <input
              className="input"
              type="number"
              inputMode="decimal"
              min="1"
              step="0.01"
              value={form.targetAmount}
              onChange={set("targetAmount")}
              required
              placeholder="0.00"
            />
          </label>
          <label className="field">
            <span>By <span className="muted">(optional)</span></span>
            <input className="input" type="date" min={isEdit ? undefined : todayISO()} value={form.targetDate} onChange={set("targetDate")} />
          </label>
        </div>
        <p className="field-hint">With a date, we'll work out how much to put aside each month.</p>

        {error && <p className="error-text" role="alert">{error}</p>}

        <div className="dialog-actions">
          <button type="button" className="btn btn-outline" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn" disabled={saving}>
            {saving ? "Saving…" : isEdit ? "Save changes" : "Create goal"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
