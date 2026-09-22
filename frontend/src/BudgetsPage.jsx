import { useEffect, useState } from "react";
import { accountApi, budgetApi } from "./api";
import BudgetDialog from "./components/BudgetDialog";
import BudgetMeter from "./components/BudgetMeter";
import { ChevronLeftIcon, ChevronRightIcon, PencilIcon, PlusIcon, TrashIcon } from "./components/Icons";
import { addMonths, monthISO, monthLabel } from "./dates";
import { formatMoney } from "./format";
import "./styles/budgets.css";

export default function BudgetsPage() {
  const thisMonth = monthISO();
  const [month, setMonth] = useState(thisMonth);
  const [overview, setOverview] = useState(null); // null while loading
  const [error, setError] = useState("");
  const [currency, setCurrency] = useState();
  const [dialog, setDialog] = useState(null); // { budget } to edit, { initial } to add
  const [reloadKey, setReloadKey] = useState(0);
  const reload = () => setReloadKey((key) => key + 1);

  useEffect(() => {
    let active = true;
    budgetApi.overview(month)
      .then((res) => {
        if (!active) return;
        setOverview(res.data);
        setError("");
      })
      .catch(() => active && setError("We couldn't load your budgets."));
    return () => {
      active = false;
    };
  }, [month, reloadKey]);

  useEffect(() => {
    accountApi.list()
      .then((res) => setCurrency(res.data.find((a) => a.currency)?.currency))
      .catch(() => {});
  }, []);

  const remove = async (budget) => {
    if (!window.confirm(`Delete your ${budget.category} budget?`)) return;
    try {
      await budgetApi.remove(budget.id);
      reload();
    } catch {
      setError("Couldn't delete that budget. Please try again.");
    }
  };

  const budgets = overview?.budgets ?? [];
  const suggestions = overview?.suggestions ?? [];
  const left = overview ? overview.totalLimit - overview.totalSpent : 0;
  const perDay = overview?.currentMonth && overview.daysLeft > 0 && left > 0 ? left / overview.daysLeft : null;

  return (
    <div className="budgets-page">
      <div className="page-head">
        <div>
          <h1>Budgets</h1>
          <p className="muted">Set a monthly limit for the things you spend most on.</p>
        </div>
        <button type="button" className="btn" onClick={() => setDialog({})}>
          <PlusIcon size={18} /> New budget
        </button>
      </div>

      <div className="month-switcher" role="group" aria-label="Month">
        <button
          type="button"
          className="icon-btn"
          onClick={() => setMonth((m) => addMonths(m, -1))}
          aria-label="Previous month"
        >
          <ChevronLeftIcon size={20} />
        </button>
        <h2 aria-live="polite">{monthLabel(month)}</h2>
        <button
          type="button"
          className="icon-btn"
          onClick={() => setMonth((m) => addMonths(m, 1))}
          disabled={month >= thisMonth}
          aria-label="Next month"
        >
          <ChevronRightIcon size={20} />
        </button>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          <span>{error}</span>
          <button type="button" className="link-button" onClick={reload}>Try again</button>
        </div>
      )}

      {overview === null && !error ? (
        <section className="card" aria-busy="true"><p className="empty-state">Loading…</p></section>
      ) : overview && budgets.length === 0 ? (
        <section className="card budgets-empty">
          <h2>No budgets yet</h2>
          <p className="muted">
            Pick a category and a monthly limit. We'll show how much is left and warn you at 80% and if you go over.
          </p>
          <button type="button" className="btn" onClick={() => setDialog({})}>Create your first budget</button>
        </section>
      ) : overview && (
        <>
          <section className="card budget-summary" aria-label="This month in total">
            <div className="budget-summary-figures">
              <div>
                <p className="muted">Spent across your budgets</p>
                <p className="budget-summary-amount">{formatMoney(overview.totalSpent, currency)}</p>
                <p className="muted">of {formatMoney(overview.totalLimit, currency)}</p>
              </div>
              <div className="budget-summary-side">
                {left >= 0 ? (
                  <p><strong>{formatMoney(left, currency)}</strong> left</p>
                ) : (
                  <p className="budget-over-text"><strong>{formatMoney(-left, currency)}</strong> over</p>
                )}
                {overview.currentMonth && (
                  <p className="muted">
                    {overview.daysLeft} day{overview.daysLeft === 1 ? "" : "s"} to go
                    {perDay != null && <> · about {formatMoney(perDay, currency)} a day</>}
                  </p>
                )}
              </div>
            </div>
            <BudgetMeter
              compact
              currency={currency}
              budget={{
                category: "All budgets",
                spent: overview.totalSpent,
                monthlyLimit: overview.totalLimit,
                remaining: left,
                percentUsed: overview.totalLimit > 0 ? Math.round((overview.totalSpent / overview.totalLimit) * 100) : 0,
                status: left < 0 ? "OVER" : overview.totalSpent >= overview.totalLimit * 0.8 ? "NEAR_LIMIT" : "ON_TRACK",
              }}
            />
          </section>

          <section className="card" aria-labelledby="budget-list-title">
            <div className="card-header">
              <h2 id="budget-list-title">Your budgets</h2>
              <span className="muted">{budgets.length}</span>
            </div>
            <ul className="budget-list">
              {budgets.map((budget) => (
                <li key={budget.id} className="budget-row">
                  <BudgetMeter budget={budget} currency={currency} />
                  <div className="budget-actions">
                    <button
                      type="button"
                      className="icon-btn"
                      onClick={() => setDialog({ budget })}
                      aria-label={`Edit ${budget.category} budget`}
                    >
                      <PencilIcon size={18} />
                    </button>
                    <button
                      type="button"
                      className="icon-btn icon-btn-danger"
                      onClick={() => remove(budget)}
                      aria-label={`Delete ${budget.category} budget`}
                    >
                      <TrashIcon size={18} />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}

      {suggestions.length > 0 && (
        <section className="card" aria-labelledby="budget-ideas-title">
          <div className="card-header">
            <h2 id="budget-ideas-title">Ideas from your spending</h2>
          </div>
          <p className="muted budget-ideas-intro">Based on your typical month recently.</p>
          <ul className="budget-ideas">
            {suggestions.map((s) => (
              <li key={s.category}>
                <span className="budget-idea-name">{s.category}</span>
                <span className="muted">about {formatMoney(s.suggestedLimit, currency)} a month</span>
                <button
                  type="button"
                  className="btn btn-outline btn-sm"
                  onClick={() => setDialog({ initial: s })}
                  aria-label={`Set a budget for ${s.category}`}
                >
                  Set budget
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {dialog && (
        <BudgetDialog
          budget={dialog.budget}
          initial={dialog.initial}
          categories={suggestions.map((s) => s.category)}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null);
            reload();
          }}
        />
      )}
    </div>
  );
}
