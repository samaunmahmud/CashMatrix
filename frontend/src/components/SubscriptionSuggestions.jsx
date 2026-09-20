import { useState } from "react";
import { subscriptionApi } from "../api";
import { formatMoney } from "../format";
import { shortDate } from "../dates";
import Avatar from "./Avatar";
import { RepeatIcon } from "./Icons";

const EVERY = { WEEKLY: "week", MONTHLY: "month", YEARLY: "year" };

/**
 * Recurring charges found in the user's transactions that aren't on their calendar yet,
 * each with a one-tap way to add it (so it gets reminders) or wave it away.
 */
export default function SubscriptionSuggestions({ suggestions, currency, onChanged }) {
  const [busyKey, setBusyKey] = useState(null);
  const [error, setError] = useState("");

  if (suggestions.length === 0) return null;

  const act = async (key, action) => {
    setBusyKey(key);
    setError("");
    try {
      await action(key);
      await onChanged();
    } catch (err) {
      setError(err.response?.data?.error || "Something went wrong. Please try again.");
    } finally {
      setBusyKey(null);
    }
  };

  return (
    <section className="card suggestions" aria-labelledby="suggestions-title">
      <div className="card-header">
        <h2 id="suggestions-title">Recurring payments we spotted</h2>
        <span className="badge"><RepeatIcon size={13} /> {suggestions.length}</span>
      </div>
      <p className="muted suggestions-intro">
        Add them to your calendar and we'll remind you before each one.
      </p>

      {error && <p className="error-text" role="alert">{error}</p>}

      <ul className="suggestion-list">
        {suggestions.map((s) => (
          <li key={s.key} className="suggestion">
            <Avatar name={s.name} />
            <div className="suggestion-body">
              <strong>{s.name}</strong>
              <span className="muted">
                {formatMoney(s.amount, currency)} every {EVERY[s.recurrence]} · next {shortDate(s.nextExpected)}
              </span>
              <span className="suggestion-seen">Seen {s.occurrences} times</span>
            </div>
            <div className="suggestion-actions">
              <button
                type="button"
                className="btn btn-sm"
                disabled={busyKey === s.key}
                onClick={() => act(s.key, subscriptionApi.accept)}
                aria-label={`Add ${s.name} to calendar`}
              >
                Add
              </button>
              <button
                type="button"
                className="btn btn-outline btn-sm"
                disabled={busyKey === s.key}
                onClick={() => act(s.key, subscriptionApi.dismiss)}
                aria-label={`Dismiss ${s.name}`}
              >
                Not now
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
