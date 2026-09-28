import { Link } from "react-router-dom";
import { formatMoney } from "../format";
import GoalRing from "./GoalRing";
import "../styles/goals.css";

/** The savings goals closest to done, for the home screen. */
export default function GoalsGlance({ goals, currency }) {
  const inProgress = goals.filter((g) => g.status !== "REACHED").sort((a, b) => b.percentSaved - a.percentSaved);
  const shown = (inProgress.length > 0 ? inProgress : goals).slice(0, 3);

  return (
    <section className="card" aria-labelledby="goals-glance-title">
      <div className="card-header">
        <h2 id="goals-glance-title">Savings goals</h2>
        <Link to="/goals" className="see-all">{goals.length > 0 ? "All goals" : "Set up"}</Link>
      </div>
      {shown.length === 0 ? (
        <p className="empty-state">Saving for a holiday or a rainy day? Set a goal and watch it fill up.</p>
      ) : (
        <ul className="goals-glance">
          {shown.map((goal) => (
            <li key={goal.id}>
              <Link to="/goals" className="goals-glance-item">
                <GoalRing percent={goal.percentSaved} size={46} stroke={5} emoji={goal.emoji} reached={goal.status === "REACHED"} />
                <span className="goals-glance-text">
                  <span className="goals-glance-name">{goal.name}</span>
                  <span className="muted">
                    {formatMoney(goal.savedAmount, currency)} of {formatMoney(goal.targetAmount, currency)}
                  </span>
                </span>
                <span className="goals-glance-percent">{goal.percentSaved}%</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
