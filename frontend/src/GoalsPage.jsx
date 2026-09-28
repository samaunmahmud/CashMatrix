import { useEffect, useState } from "react";
import { accountApi, goalApi } from "./api";
import { useNotifications } from "./NotificationsContext";
import GoalDialog from "./components/GoalDialog";
import GoalMoneyDialog from "./components/GoalMoneyDialog";
import GoalRing from "./components/GoalRing";
import { MinusIcon, PencilIcon, PlusIcon, TrashIcon } from "./components/Icons";
import Toast from "./components/Toast";
import { mediumDate } from "./dates";
import { formatMoney } from "./format";
import "./styles/goals.css";

const IDEAS = [
  { name: "Rainy day fund", emoji: "🛟", targetAmount: 1000 },
  { name: "Summer holiday", emoji: "🏖️", targetAmount: 1500 },
  { name: "New phone", emoji: "📱", targetAmount: 800 },
];

/** "Save £158.34 a month to reach it by 1 Feb 2027", or where it stands otherwise. */
function planLine(goal, currency) {
  if (goal.status === "REACHED") return "Goal reached. Well done!";
  if (goal.status === "PAST_DATE") return `The date (${mediumDate(goal.targetDate)}) has passed. Pick a new one?`;
  if (goal.monthlyNeeded != null) {
    return `Save ${formatMoney(goal.monthlyNeeded, currency)} a month to reach it by ${mediumDate(goal.targetDate)}`;
  }
  return `${formatMoney(goal.remaining, currency)} to go`;
}

export default function GoalsPage() {
  const [goals, setGoals] = useState(null); // null while loading
  const [error, setError] = useState("");
  const [currency, setCurrency] = useState();
  const [dialog, setDialog] = useState(null); // { goal } to edit, { initial } to add
  const [money, setMoney] = useState(null); // { goal, mode }
  const [message, setMessage] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const { refreshUnread } = useNotifications();

  useEffect(() => {
    let active = true;
    goalApi.list()
      .then((res) => {
        if (!active) return;
        setGoals(res.data);
        setError("");
      })
      .catch(() => active && setError("We couldn't load your goals."));
    return () => {
      active = false;
    };
  }, [reloadKey]);

  useEffect(() => {
    accountApi.list()
      .then((res) => setCurrency(res.data.find((a) => a.currency)?.currency))
      .catch(() => {});
  }, []);

  const replace = (updated) => setGoals((list) => list.map((g) => (g.id === updated.id ? updated : g)));

  const remove = async (goal) => {
    if (!window.confirm(`Delete your “${goal.name}” goal? Its history goes too.`)) return;
    try {
      await goalApi.remove(goal.id);
      setGoals((list) => list.filter((g) => g.id !== goal.id));
    } catch {
      setError("Couldn't delete that goal. Please try again.");
    }
  };

  const list = goals ?? [];
  const totalSaved = list.reduce((sum, g) => sum + g.savedAmount, 0);
  const totalTarget = list.reduce((sum, g) => sum + g.targetAmount, 0);
  const reached = list.filter((g) => g.status === "REACHED").length;
  const monthly = list.reduce((sum, g) => sum + (g.monthlyNeeded ?? 0), 0);

  return (
    <div className="goals-page">
      <div className="page-head">
        <div>
          <h1>Savings goals</h1>
          <p className="muted">Put money aside for the things that matter and watch each goal fill up.</p>
        </div>
        <button type="button" className="btn" onClick={() => setDialog({})}>
          <PlusIcon size={18} /> New goal
        </button>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          <span>{error}</span>
          <button type="button" className="link-button" onClick={() => setReloadKey((k) => k + 1)}>Try again</button>
        </div>
      )}

      {goals === null && !error ? (
        <section className="card" aria-busy="true"><p className="empty-state">Loading…</p></section>
      ) : goals && goals.length === 0 ? (
        <section className="card goals-empty">
          <span className="goals-empty-art" aria-hidden="true">🐯</span>
          <h2>Start your first goal</h2>
          <p className="muted">A holiday, a deposit, a rainy day fund. Give it a target and, if you like, a date, and we'll tell you how much to save each month.</p>
          <div className="goal-ideas">
            {IDEAS.map((idea) => (
              <button key={idea.name} type="button" className="goal-idea" onClick={() => setDialog({ initial: idea })}>
                <span aria-hidden="true">{idea.emoji}</span> {idea.name}
              </button>
            ))}
          </div>
          <button type="button" className="btn" onClick={() => setDialog({})}>Create a goal</button>
        </section>
      ) : goals && (
        <>
          <section className="card goals-summary" aria-label="All goals">
            <div>
              <p className="muted">Saved across your goals</p>
              <p className="goals-summary-amount">{formatMoney(totalSaved, currency)}</p>
              <p className="muted">of {formatMoney(totalTarget, currency)}</p>
            </div>
            <dl className="goals-summary-stats">
              <div><dt>Goals</dt><dd>{list.length}</dd></div>
              <div><dt>Reached</dt><dd>{reached}</dd></div>
              {monthly > 0 && <div><dt>To save a month</dt><dd>{formatMoney(monthly, currency)}</dd></div>}
            </dl>
          </section>

          <ul className="goal-grid">
            {list.map((goal) => (
              <li key={goal.id} className={`card goal-card goal-${goal.status.toLowerCase().replace("_", "-")}`}>
                <div className="goal-top">
                  <GoalRing percent={goal.percentSaved} emoji={goal.emoji} reached={goal.status === "REACHED"} />
                  <div className="goal-heading">
                    <h2>{goal.name}</h2>
                    <p className="goal-figures">
                      <strong>{formatMoney(goal.savedAmount, currency)}</strong>
                      <span className="muted"> of {formatMoney(goal.targetAmount, currency)}</span>
                    </p>
                    <p className="goal-percent">{goal.percentSaved}% saved</p>
                  </div>
                  <div className="goal-menu">
                    <button type="button" className="icon-btn" onClick={() => setDialog({ goal })} aria-label={`Edit ${goal.name}`}>
                      <PencilIcon size={18} />
                    </button>
                    <button type="button" className="icon-btn icon-btn-danger" onClick={() => remove(goal)} aria-label={`Delete ${goal.name}`}>
                      <TrashIcon size={18} />
                    </button>
                  </div>
                </div>

                <div className="goal-bar" aria-hidden="true"><span style={{ width: `${goal.percentSaved}%` }} /></div>
                <p className="goal-plan">{planLine(goal, currency)}</p>

                <div className="goal-actions">
                  <button type="button" className="btn btn-sm" onClick={() => setMoney({ goal, mode: "add" })}>
                    <PlusIcon size={16} /> Add money
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    onClick={() => setMoney({ goal, mode: "take" })}
                    disabled={goal.savedAmount <= 0}
                  >
                    <MinusIcon size={16} /> Take out
                  </button>
                </div>

                {goal.recent.length > 0 && (
                  <details className="goal-history">
                    <summary>Recent activity</summary>
                    <ul>
                      {goal.recent.map((c) => (
                        <li key={c.id}>
                          <span className="muted">{mediumDate(c.madeOn)}</span>
                          <span className={c.amount > 0 ? "tx-in" : ""}>
                            {c.amount > 0 ? "+" : "−"}{formatMoney(Math.abs(c.amount), currency)}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </details>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      {dialog && (
        <GoalDialog
          goal={dialog.goal}
          initial={dialog.initial}
          onClose={() => setDialog(null)}
          onSaved={(saved) => {
            setDialog(null);
            if (dialog.goal) replace(saved);
            else setGoals((list) => [...(list ?? []), saved]);
            refreshUnread();
          }}
        />
      )}

      {money && (
        <GoalMoneyDialog
          goal={money.goal}
          mode={money.mode}
          currency={currency}
          onClose={() => setMoney(null)}
          onSaved={(saved, amount) => {
            setMoney(null);
            replace(saved);
            refreshUnread();
            setMessage(
              saved.status === "REACHED" && money.goal.status !== "REACHED"
                ? `🎉 You reached your ${saved.name} goal!`
                : amount > 0
                  ? `Added ${formatMoney(amount, currency)} to ${saved.name}`
                  : `Took ${formatMoney(-amount, currency)} out of ${saved.name}`
            );
          }}
        />
      )}

      <Toast message={message} onDone={() => setMessage("")} />
    </div>
  );
}
