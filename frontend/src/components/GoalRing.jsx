import "../styles/goals.css";

/** A ring that fills as a goal fills, with the percentage in the middle. */
export default function GoalRing({ percent, size = 88, stroke = 9, emoji, reached }) {
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const filled = (Math.min(Math.max(percent, 0), 100) / 100) * circumference;

  return (
    <div className={`goal-ring${reached ? " goal-ring-reached" : ""}`} style={{ width: size, height: size }} aria-hidden="true">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="var(--ring-track)" strokeWidth={stroke} />
        <circle
          className="goal-ring-fill"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--ring-fill)"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={`${filled} ${circumference}`}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <span className="goal-ring-centre">
        {emoji ? <span className="goal-ring-emoji">{emoji}</span> : <span className="goal-ring-percent">{percent}%</span>}
      </span>
    </div>
  );
}
