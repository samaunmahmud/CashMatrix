import { useEffect, useRef, useState } from "react";
import { calendarApi } from "../api";

const TYPES = [
  ["PAYMENT", "Payment"],
  ["SUBSCRIPTION", "Subscription"],
  ["TASK", "Task"],
];

const REPEATS = [
  ["NONE", "Doesn't repeat"],
  ["WEEKLY", "Every week"],
  ["MONTHLY", "Every month"],
  ["YEARLY", "Every year"],
];

const REMINDERS = [
  [0, "On the day"],
  [1, "1 day before"],
  [2, "2 days before"],
  [3, "3 days before"],
  [7, "1 week before"],
  [14, "2 weeks before"],
];

function reminderOptions(current) {
  // An item saved with a value outside the presets (say 5 days) must still show it.
  return REMINDERS.some(([days]) => days === current)
    ? REMINDERS
    : [...REMINDERS, [current, `${current} days before`]].sort((a, b) => a[0] - b[0]);
}

function errorMessage(err) {
  const data = err.response?.data;
  if (data?.error) return data.error;
  if (data && typeof data === "object") return Object.values(data).join(" ");
  return "Couldn't save. Please try again.";
}

/**
 * Add or edit a calendar item. Rendered only while open, so it always starts
 * from fresh form state. Built on the native <dialog> element, which gives us
 * focus trapping and Escape-to-close.
 */
export default function EventDialog({ event, defaultDate, onClose, onSaved }) {
  const dialogRef = useRef(null);
  const isEdit = Boolean(event);

  const [form, setForm] = useState(() => ({
    title: event?.title ?? "",
    type: event?.type ?? "PAYMENT",
    amount: event?.amount != null ? String(event.amount) : "",
    startDate: event?.startDate ?? defaultDate,
    recurrence: event?.recurrence ?? "NONE",
    remindDaysBefore: event?.remindDaysBefore ?? 1,
    description: event?.description ?? "",
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
      title: form.title.trim(),
      description: form.description.trim() || null,
      type: form.type,
      amount: form.type !== "TASK" && form.amount !== "" ? Number(form.amount) : null,
      startDate: form.startDate,
      recurrence: form.recurrence,
      remindDaysBefore: Number(form.remindDaysBefore),
    };

    try {
      const { data } = isEdit
        ? await calendarApi.update(event.id, payload)
        : await calendarApi.create(payload);
      onSaved(data, isEdit);
    } catch (err) {
      setError(errorMessage(err));
      setSaving(false);
    }
  };

  const repeats = form.recurrence !== "NONE";

  return (
    <dialog
      ref={dialogRef}
      className="dialog"
      aria-labelledby="event-dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === dialogRef.current) onClose(); // click on the backdrop
      }}
    >
      <form className="dialog-body" onSubmit={submit}>
        <h2 id="event-dialog-title">{isEdit ? "Edit item" : "Add to calendar"}</h2>

        <fieldset className="segmented">
          <legend className="field-label">Type</legend>
          <div className="segmented-options">
            {TYPES.map(([value, label]) => (
              <label key={value}>
                <input
                  type="radio"
                  name="type"
                  value={value}
                  checked={form.type === value}
                  onChange={set("type")}
                />
                <span>{label}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <label className="field">
          <span>Title</span>
          <input
            className="input"
            value={form.title}
            onChange={set("title")}
            maxLength={120}
            required
            autoFocus
            placeholder={form.type === "TASK" ? "e.g. Renew passport" : "e.g. Netflix"}
          />
        </label>

        <div className="field-row">
          {form.type !== "TASK" && (
            <label className="field">
              <span>Amount</span>
              <input
                className="input"
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                value={form.amount}
                onChange={set("amount")}
                placeholder="0.00"
              />
            </label>
          )}
          <label className="field">
            <span>{repeats ? "Starts on" : "Date"}</span>
            <input className="input" type="date" value={form.startDate} onChange={set("startDate")} required />
          </label>
        </div>

        <div className="field-row">
          <label className="field">
            <span>Repeats</span>
            <select className="input" value={form.recurrence} onChange={set("recurrence")}>
              {REPEATS.map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Remind me</span>
            <select className="input" value={form.remindDaysBefore} onChange={set("remindDaysBefore")}>
              {reminderOptions(Number(form.remindDaysBefore)).map(([days, label]) => (
                <option key={days} value={days}>{label}</option>
              ))}
            </select>
          </label>
        </div>

        {isEdit && repeats && (
          <p className="field-hint">
            Changing the date or how often it repeats restarts this item from the new date.
          </p>
        )}

        <label className="field">
          <span>Notes (optional)</span>
          <textarea className="input" value={form.description} onChange={set("description")} maxLength={1000} />
        </label>

        {error && <p className="error-text" role="alert">{error}</p>}

        <div className="dialog-actions">
          <button type="button" className="btn btn-outline" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn" disabled={saving}>
            {saving ? "Saving…" : isEdit ? "Save changes" : "Add"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
