import { formatMoney } from "../format";

const COLOURS = ["var(--chart-1)", "var(--chart-2)", "var(--chart-3)", "var(--chart-4)", "var(--chart-5)", "var(--chart-6)"];
const MAX_SLICES = 5;

const SIZE = 168;
const STROKE = 26;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/** Groups everything past the biggest few categories into "Other" so the chart stays readable. */
function slicesOf(categories) {
  if (categories.length <= MAX_SLICES + 1) return categories;
  const top = categories.slice(0, MAX_SLICES);
  const otherTotal = categories.slice(MAX_SLICES).reduce((sum, [, amount]) => sum + amount, 0);
  return [...top, ["Other", otherTotal]];
}

export default function SpendingDonut({ categories, total, currency, groupedBy = "category" }) {
  const slices = slicesOf(categories);
  // Each slice starts where the previous one ended, so work out the arc lengths and start points up front.
  const lengths = slices.map(([, amount]) => (amount / total) * CIRCUMFERENCE);
  const starts = lengths.map((_, index) => lengths.slice(0, index).reduce((sum, length) => sum + length, 0));

  return (
    <div className="donut">
      <div className="donut-chart">
        <svg
          width={SIZE}
          height={SIZE}
          viewBox={`0 0 ${SIZE} ${SIZE}`}
          role="img"
          aria-label={`Spending by ${groupedBy}, ${formatMoney(total, currency)} in total`}
        >
          <circle cx={SIZE / 2} cy={SIZE / 2} r={RADIUS} fill="none" stroke="var(--brand-soft)" strokeWidth={STROKE} />
          <g transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}>
            {slices.map(([name], index) => {
              const visible = Math.max(lengths[index] - 2, 0); // a hairline gap between slices
              return (
                <circle
                  key={name}
                  cx={SIZE / 2}
                  cy={SIZE / 2}
                  r={RADIUS}
                  fill="none"
                  stroke={COLOURS[index % COLOURS.length]}
                  strokeWidth={STROKE}
                  strokeDasharray={`${visible} ${CIRCUMFERENCE - visible}`}
                  strokeDashoffset={-starts[index]}
                />
              );
            })}
          </g>
        </svg>
        <div className="donut-centre">
          <span className="donut-total">{formatMoney(total, currency)}</span>
          <span className="donut-caption">spent</span>
        </div>
      </div>

      <ul className="donut-legend">
        {slices.map(([name, amount], index) => (
          <li key={name}>
            <span className="donut-swatch" style={{ background: COLOURS[index % COLOURS.length] }} />
            <span className="donut-name">{name}</span>
            <span className="donut-amount">{formatMoney(amount, currency)}</span>
            <span className="donut-percent">{Math.round((amount / total) * 100)}%</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
