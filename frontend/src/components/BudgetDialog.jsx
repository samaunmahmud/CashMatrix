import { useEffect, useRef, useState } from "react";
import { budgetApi } from "../api";

function errorMessage(err) {
  const data = err.response?.data;
  if (data?.error) return data.error;
  if (data && typeof data === "object") return Object.values(data).join(" ");
  return "Couldn't save. Please try again.";
}

/**
 * Add or edit a monthly budget. The category box offers the categories the user
 * actually spends in, but any name can be typed.
 */
export default function BudgetDialog({ budget, initial, categories = [], onClose, onSaved }) {
  const dialogRef = useRef(null);
  const isEdit = Boolean(budget);

  const [form, setForm] = useState(() => ({
    category: budget?.category ?? initial?.category ?? "",
    monthlyLimit: String(budget?.monthlyLimit ?? initial?.suggestedLimit ?? ""),
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
    const payload = { category: form.category.trim(), monthlyLimit: Number(form.monthlyLimit) };
    try {
      if (isEdit) await budgetApi.update(budget.id, payload);
      else await budgetApi.create(payload);
      onSaved();
    } catch (err) {
      setError(errorMessage(err));
      setSaving(false);
    }
  };

  return (
    <dialog
      ref={dialogRef}
      className="dialog"
      aria-labelledby="budget-dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === dialogRef.current) onClose(); // click on the backdrop
      }}
    >
      <form className="dialog-body" onSubmit={submit}>
        <h2 id="budget-dialog-title">{isEdit ? "Edit budget" : "New budget"}</h2>

        <label className="field">
          <span>Category</span>
          <input
            className="input"
            value={form.category}
            onChange={set("category")}
            list="budget-categories"
            maxLength={60}
            required
            autoFocus={!initial}
            placeholder="e.g. Groceries"
          />
          <datalist id="budget-categories">
            {categories.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>
          <span className="field-hint">Matches the category shown on each transaction.</span>
        </label>

        <label className="field">
          <span>Monthly limit</span>
          <input
            className="input"
            type="number"
            inputMode="decimal"
            min="0.01"
            step="0.01"
            value={form.monthlyLimit}
            onChange={set("monthlyLimit")}
            required
            autoFocus={Boolean(initial)}
            placeholder="0.00"
          />
          <span className="field-hint">We'll warn you at 80% and if you go over.</span>
        </label>

        {error && <p className="error-text" role="alert">{error}</p>}

        <div className="dialog-actions">
          <button type="button" className="btn btn-outline" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn" disabled={saving}>
            {saving ? "Saving…" : isEdit ? "Save changes" : "Add budget"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
