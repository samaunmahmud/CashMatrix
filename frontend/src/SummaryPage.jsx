import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { accountApi, insightsApi } from "./api";
import Avatar from "./components/Avatar";
import BudgetMeter from "./components/BudgetMeter";
import { ChevronLeftIcon, ChevronRightIcon } from "./components/Icons";
import { addMonths, mediumDate, monthISO, monthLabel } from "./dates";
import { formatMoney } from "./format";
import "./styles/budgets.css";
import "./styles/summary.css";

const isMonth = (value) => /^\d{4}-\d{2}$/.test(value ?? "");

/** "Your September": a look back at one month. */
export default function SummaryPage() {
  const thisMonth = monthISO();
  const [params, setParams] = useSearchParams();
  const month = isMonth(params.get("month")) && params.get("month") <= thisMonth ? params.get("month") : addMonths(thisMonth, -1);
  // Kept with the month it is for, so switching months shows "Loading…" rather than the old month.
  const [loaded, setLoaded] = useState({ month: null, data: null });
  const [error, setError] = useState(false);
  const [currency, setCurrency] = useState();

  useEffect(() => {
    let active = true;
    insightsApi.summary(month)
      .then((res) => {
        if (!active) return;
        setLoaded({ month, data: res.data });
        setError(false);
      })
      .catch(() => active && setError(true));
    return () => {
      active = false;
    };
  }, [month]);

  useEffect(() => {
    accountApi.list()
      .then((res) => setCurrency(res.data.find((a) => a.currency)?.currency))
      .catch(() => {});
  }, []);

  const summary = loaded.month === month ? loaded.data : null;
  const go = (delta) => setParams({ month: addMonths(month, delta) }, { replace: true });
  const name = monthLabel(month).split(" ")[0];
  const money = (amount) => formatMoney(amount, currency);
  const txLink = (extra = "") => `/transactions?month=${month}${extra}`;

  return (
    <div className="summary-page">
      <div className="month-switcher" role="group" aria-label="Month">
        <button type="button" className="icon-btn" onClick={() => go(-1)} aria-label="Previous month">
          <ChevronLeftIcon size={20} />
        </button>
        <h1 aria-live="polite">Your {monthLabel(month)}</h1>
        <button type="button" className="icon-btn" onClick={() => go(1)} disabled={month >= thisMonth} aria-label="Next month">
          <ChevronRightIcon size={20} />
        </button>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          <span>We couldn't load this month.</span>
        </div>
      )}

      {!summary && !error ? (
        <section className="card" aria-busy="true"><p className="empty-state">Loading…</p></section>
      ) : summary && summary.transactionCount === 0 ? (
        <section className="card"><p className="empty-state">Nothing came in or went out in {name}.</p></section>
      ) : summary && (
        <>
          <section className="summary-hero" aria-label="Spending">
            <p className="summary-hero-label">{summary.complete ? `You spent in ${name}` : `Spent so far in ${name}`}</p>
            <p className="summary-hero-amount">{money(summary.spent)}</p>
            {summary.changePercent != null && (
              <p className={`summary-change ${summary.changePercent > 0 ? "summary-change-up" : "summary-change-down"}`}>
                {summary.changePercent === 0
                  ? `The same as ${monthLabel(addMonths(month, -1)).split(" ")[0]}`
                  : `${Math.abs(summary.changePercent)}% ${summary.changePercent > 0 ? "more" : "less"} than ${monthLabel(addMonths(month, -1)).split(" ")[0]} (${money(summary.previousSpent)})`}
              </p>
            )}
          </section>

          <dl className="summary-tiles">
            <div className="card">
              <dt>Money in</dt>
              <dd className="tx-in">{money(summary.moneyIn)}</dd>
            </div>
            <div className="card">
              <dt>{summary.net >= 0 ? "Left over" : "Overspent"}</dt>
              <dd className={summary.net >= 0 ? "tx-in" : "summary-negative"}>{money(Math.abs(summary.net))}</dd>
            </div>
            <div className="card">
              <dt>A day on average</dt>
              <dd>{money(summary.dailyAverage)}</dd>
            </div>
            <div className="card">
              <dt>Saved to goals</dt>
              <dd>{money(summary.savedToGoals)}</dd>
            </div>
          </dl>

          <div className="summary-grid">
            <section className="card" aria-labelledby="summary-categories-title">
              <div className="card-header">
                <h2 id="summary-categories-title">Where it went</h2>
                <Link to={txLink("&direction=out")} className="see-all">All spending</Link>
              </div>
              {summary.topCategories.length === 0 ? (
                <p className="empty-state">No spending this month.</p>
              ) : (
                <ul className="summary-bars">
                  {summary.topCategories.map((c) => (
                    <li key={c.category}>
                      <Link to={txLink(`&direction=out&category=${encodeURIComponent(c.category)}`)} className="summary-bar-row">
                        <span className="summary-bar-top">
                          <span className="summary-bar-name">{c.category}</span>
                          <span className="summary-bar-figure">{money(c.amount)} <span className="muted">{c.share}%</span></span>
                        </span>
                        <span className="summary-bar-track" aria-hidden="true">
                          <span style={{ width: `${Math.max(c.share, 2)}%` }} />
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="card" aria-labelledby="summary-biggest-title">
              <div className="card-header">
                <h2 id="summary-biggest-title">Biggest purchases</h2>
              </div>
              <ul className="summary-purchases">
                {summary.biggestPurchases.map((p, i) => (
                  <li key={`${p.merchant}-${p.date}-${i}`}>
                    <Avatar name={p.merchant} size={36} />
                    <span className="summary-purchase-text">
                      <strong>{p.merchant}</strong>
                      <span className="muted">{p.category} · {mediumDate(p.date)}</span>
                    </span>
                    <span className="summary-purchase-amount">{money(p.amount)}</span>
                  </li>
                ))}
              </ul>
            </section>
          </div>

          {summary.budgets.length > 0 && (
            <section className="card" aria-labelledby="summary-budgets-title">
              <div className="card-header">
                <h2 id="summary-budgets-title">Budgets</h2>
                <span className="muted">
                  {summary.budgets.filter((b) => b.status !== "OVER").length} of {summary.budgets.length} kept
                </span>
              </div>
              <ul className="glance-list">
                {summary.budgets.map((b) => (
                  <li key={b.category}>
                    <BudgetMeter
                      currency={currency}
                      budget={{
                        category: b.category,
                        spent: b.spent,
                        monthlyLimit: b.monthlyLimit,
                        remaining: b.monthlyLimit - b.spent,
                        percentUsed: Math.round((b.spent / b.monthlyLimit) * 100),
                        status: b.status,
                      }}
                    />
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  );
}
