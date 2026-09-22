import { formatMoney } from "../format";

const STATUS = {
  ON_TRACK: { label: "On track", className: "meter-ok" },
  NEAR_LIMIT: { label: "Nearly used", className: "meter-near" },
  OVER: { label: "Over budget", className: "meter-over" },
};

/**
 * How much of a budget has gone. The fill turns amber, then red, as the limit gets close,
 * and the words beside it say the same thing, so colour is never the only signal.
 */
export default function BudgetMeter({ budget, currency, compact = false }) {
  const status = STATUS[budget.status] ?? STATUS.ON_TRACK;
  const filled = Math.min(budget.percentUsed, 100);
  const over = budget.remaining < 0;

  return (
    <div className={`meter ${status.className}${compact ? " meter-compact" : ""}`}>
      <div className="meter-top">
        <span className="meter-name">{budget.category}</span>
        <span className="meter-figures">
          <strong>{formatMoney(budget.spent, currency)}</strong>
          <span className="muted"> of {formatMoney(budget.monthlyLimit, currency)}</span>
        </span>
      </div>
      <div
        className="meter-track"
        role="meter"
        aria-label={`${budget.category} budget used`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={filled}
        aria-valuetext={`${budget.percentUsed}% used`}
      >
        <span className="meter-fill" style={{ width: `${filled}%` }} />
      </div>
      <div className="meter-bottom">
        <span className="meter-status">{status.label}</span>
        <span className={over ? "meter-left meter-left-over" : "meter-left"}>
          {over
            ? `${formatMoney(-budget.remaining, currency)} over`
            : `${formatMoney(budget.remaining, currency)} left`}
        </span>
      </div>
    </div>
  );
}
