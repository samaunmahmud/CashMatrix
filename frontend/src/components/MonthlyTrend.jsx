import { useState } from "react";
import { formatMoney } from "../format";
import { monthLabel, parseISO } from "../dates";

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

const dayAndMonth = (iso) => new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" }).format(parseISO(iso));

// A forecast is a rough guide, so it is shown to the nearest pound without pence.
const roughly = (amount, currency) => formatMoney(Math.round(amount), currency).replace(/\.00$/, "");

/** The forecast against the last full month, e.g. "about £120 less than August". */
function forecastComparison(insights, currency) {
  const last = [...insights.months].reverse().find((m) => !m.partial);
  if (!last) return null;
  const diff = insights.forecast.total - last.spent;
  if (Math.abs(diff) < 10) return `about the same as ${monthLabel(last.month).split(" ")[0]}`;
  return `about ${roughly(Math.abs(diff), currency)} ${diff < 0 ? "less" : "more"} than ${monthLabel(last.month).split(" ")[0]}`;
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
  // On the last day of the month there is nothing left to forecast.
  const forecast = insights.forecast?.daysLeft > 0 ? insights.forecast : null;
  const current = months[months.length - 1];
  // Only drawn while there is more still to come than has been spent.
  const ahead = forecast && forecast.total > current.spent ? forecast.total - current.spent : 0;
  const max = niceMax(Math.max(...months.map((m) => m.spent), current.spent + ahead));
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
                <span className={`trend-cap${ahead ? " trend-cap-forecast" : ""}`} style={{ bottom: scale(m.spent + ahead) + 4 }}>
                  {ahead ? `≈ ${roughly(forecast.total, currency)}` : formatMoney(m.spent, currency)}
                </span>
              )}
              <span className="trend-stack">
                {i === months.length - 1 && ahead > 0 && (
                  <span className="trend-bar-forecast" style={{ height: scale(ahead), width: BAR }} />
                )}
                <span
                  className={`trend-bar${m.partial ? " trend-bar-partial" : ""}`}
                  style={{ height: Math.max(scale(m.spent), m.spent > 0 ? 3 : 0), width: BAR }}
                />
              </span>
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

      {/* A table can't be shrunk out of sight like other elements, so it sits in a hidden box. */}
      <div className="sr-only">
        <table>
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
      </div>

      {forecast && (
        <div className="trend-forecast">
          <p>
            <strong>Heading for about {roughly(forecast.total, currency)}</strong> by {dayAndMonth(forecast.monthEnd)}
            {forecastComparison(insights, currency) ? `, ${forecastComparison(insights, currency)}.` : "."}
          </p>
          <dl className="trend-forecast-parts">
            <div>
              <dt>Spent so far</dt>
              <dd>{formatMoney(forecast.spentSoFar, currency)}</dd>
            </div>
            <div>
              <dt>Bills to come{forecast.billCount > 0 ? ` (${forecast.billCount})` : ""}</dt>
              <dd>{formatMoney(forecast.billsToCome, currency)}</dd>
            </div>
            <div>
              <dt>Everyday, {forecast.daysLeft} {forecast.daysLeft === 1 ? "day" : "days"} left</dt>
              <dd>
                {formatMoney(forecast.everydayToCome, currency)}
                <span className="muted"> · {roughly(forecast.everydayPerDay, currency)} a day</span>
              </dd>
            </div>
          </dl>
          <p className="trend-forecast-note muted">
            Based on the bills on your calendar and your usual everyday spending.
          </p>
        </div>
      )}

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
