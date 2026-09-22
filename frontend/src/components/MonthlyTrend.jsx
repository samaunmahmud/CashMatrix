import { useState } from "react";
import { formatMoney } from "../format";
import { monthLabel } from "../dates";

const HEIGHT = 170;
const TOP = 22; // room for the value label above the tallest column
const BAR = 24;

/** Round the axis up to a clean step (0 / 250 / 500…) so the gridlines read easily. */
function niceMax(value) {
  if (value <= 0) return 100;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => value / s <= 4) ?? 10 * magnitude;
  return Math.ceil(value / step) * step;
}

function paceText(insights, currency) {
  const { spentSoFar, spentSameTimeLastMonth } = insights;
  if (spentSameTimeLastMonth == null) return null;
  const diff = spentSoFar - spentSameTimeLastMonth;
  if (Math.abs(diff) < 0.5) return "About the same as this time last month.";
  return diff < 0
    ? `${formatMoney(-diff, currency)} less than this time last month.`
    : `${formatMoney(diff, currency)} more than this time last month.`;
}

/** The categories that moved most between last month and this one. */
function biggestChanges(categories) {
  return [...categories]
    .map((c) => ({ ...c, change: c.thisMonth - c.lastMonth }))
    .filter((c) => Math.abs(c.change) >= 1)
    .sort((a, b) => Math.abs(b.change) - Math.abs(a.change))
    .slice(0, 3);
}

/**
 * Spending per month as columns, one colour, the current month lighter because it isn't
 * finished. Hover or focus a column for its figures; a table carries the same numbers
 * for screen readers.
 */
export default function MonthlyTrend({ insights, currency }) {
  const [active, setActive] = useState(null);
  const months = insights.months;
  const max = niceMax(Math.max(...months.map((m) => m.spent)));
  const ticks = [0, max / 2, max];
  const scale = (v) => (v / max) * (HEIGHT - TOP);

  const shown = active ?? months.length - 1;
  const focus = months[shown];
  const changes = biggestChanges(insights.categories);
  const pace = paceText(insights, currency);

  return (
    <div className="trend">
      <div className="trend-readout" aria-live="polite">
        <span className="trend-readout-month">
          {monthLabel(focus.month)}
          {focus.partial && <span className="muted"> · {shown === months.length - 1 ? "so far" : "part month"}</span>}
        </span>
        <span className="trend-readout-value">{formatMoney(focus.spent, currency)} spent</span>
        {focus.moneyIn > 0 && <span className="muted">{formatMoney(focus.moneyIn, currency)} in</span>}
      </div>

      <div className="trend-plot" style={{ height: HEIGHT }} onMouseLeave={() => setActive(null)}>
        {ticks.map((t) => (
          <div key={t} className="trend-grid" style={{ bottom: scale(t) }}>
            <span>{formatMoney(t, currency).replace(/\.00$/, "")}</span>
          </div>
        ))}
        <div className="trend-columns" aria-hidden="true">
          {months.map((m, i) => (
            <div
              key={m.month}
              className={`trend-slot${i === shown ? " trend-slot-active" : ""}`}
              onMouseEnter={() => setActive(i)}
              onClick={() => setActive(i)}
            >
              {i === months.length - 1 && (
                <span className="trend-cap" style={{ bottom: scale(m.spent) + 4 }}>
                  {formatMoney(m.spent, currency)}
                </span>
              )}
              <span
                className={`trend-bar${m.partial ? " trend-bar-partial" : ""}`}
                style={{ height: Math.max(scale(m.spent), m.spent > 0 ? 3 : 0), width: BAR }}
              />
            </div>
          ))}
        </div>
      </div>
      <div className="trend-labels" role="group" aria-label="Choose a month">
        {months.map((m, i) => (
          <button
            key={m.month}
            type="button"
            className={i === shown ? "trend-label trend-label-active" : "trend-label"}
            aria-pressed={i === shown}
            aria-label={monthLabel(m.month)}
            onMouseEnter={() => setActive(i)}
            onFocus={() => setActive(i)}
            onClick={() => setActive(i)}
          >
            {monthLabel(m.month, true)}
          </button>
        ))}
      </div>

      <table className="sr-only">
        <caption>Spending by month</caption>
        <thead>
          <tr><th scope="col">Month</th><th scope="col">Spent</th><th scope="col">Money in</th></tr>
        </thead>
        <tbody>
          {months.map((m) => (
            <tr key={m.month}>
              <th scope="row">{monthLabel(m.month)}{m.partial ? " (part month)" : ""}</th>
              <td>{formatMoney(m.spent, currency)}</td>
              <td>{formatMoney(m.moneyIn, currency)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {(pace || changes.length > 0) && (
        <div className="trend-notes">
          {pace && (
            <p>
              <strong>{formatMoney(insights.spentSoFar, currency)}</strong> spent this month so far. {pace}
            </p>
          )}
          {changes.length > 0 && (
            <ul className="trend-changes" aria-label="Biggest changes from last month">
              {changes.map((c) => (
                <li key={c.category}>
                  <span className="trend-change-name">{c.category}</span>
                  <span className={c.change > 0 ? "trend-change trend-change-up" : "trend-change trend-change-down"}>
                    {c.change > 0 ? "▲" : "▼"} {formatMoney(Math.abs(c.change), currency)}
                    <span className="sr-only">{c.change > 0 ? " more" : " less"} than last month</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
