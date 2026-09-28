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
    planOn: Boolean(start.plan),
    planAmount: start.plan ? String(start.plan.amount) : "",
    planFrequency: start.plan?.frequency ?? "MONTHLY",
    planStartDate: start.plan?.startDate ?? todayISO(),
    planAutoRecord: start.plan?.autoRecord ?? false,
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
      ...(form.planOn && {
        planAmount: Number(form.planAmount),
        planFrequency: form.planFrequency,
        planStartDate: form.planStartDate || null,
        planAutoRecord: form.planAutoRecord,
      }),
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

        <label className="check-row">
          <input type="checkbox" checked={form.planOn} onChange={(e) => setForm((f) => ({ ...f, planOn: e.target.checked }))} />
          <span>
            <strong>Save a regular amount</strong>
            <span className="field-hint">It goes on your calendar, and we'll show when you'll reach your goal.</span>
          </span>
        </label>

        {form.planOn && (
          <div className="plan-fields">
            <div className="field-row">
              <label className="field">
                <span>Amount</span>
                <input
                  className="input"
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  value={form.planAmount}
                  onChange={set("planAmount")}
                  required
                  placeholder="0.00"
                />
              </label>
              <label className="field">
                <span>First saving</span>
                <input className="input" type="date" value={form.planStartDate} onChange={set("planStartDate")} required />
              </label>
            </div>
            <fieldset className="segmented">
              <legend className="field-label">How often</legend>
              <div className="segmented-options">
                {[["WEEKLY", "Every week"], ["MONTHLY", "Every month"]].map(([value, label]) => (
                  <label key={value}>
                    <input type="radio" name="plan-frequency" value={value} checked={form.planFrequency === value} onChange={set("planFrequency")} />
                    <span>{label}</span>
                  </label>
                ))}
              </div>
            </fieldset>
            <label className="check-row">
              <input
                type="checkbox"
                checked={form.planAutoRecord}
                onChange={(e) => setForm((f) => ({ ...f, planAutoRecord: e.target.checked }))}
              />
              <span>
                <strong>I've set up a standing order</strong>
                <span className="field-hint">
                  We'll add the money to this goal on each saving day. Leave this off and we'll remind you to put it aside instead.
                </span>
              </span>
            </label>
          </div>
        )}

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
