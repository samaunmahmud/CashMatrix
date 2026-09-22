import { Link } from "react-router-dom";
import BudgetMeter from "./BudgetMeter";

const URGENCY = { OVER: 0, NEAR_LIMIT: 1, ON_TRACK: 2 };

/** The budgets that most need attention this month, for the home screen. */
export default function BudgetsGlance({ overview, currency }) {
  const budgets = [...(overview?.budgets ?? [])]
    .sort((a, b) => URGENCY[a.status] - URGENCY[b.status] || b.percentUsed - a.percentUsed)
    .slice(0, 3);

  return (
    <section className="card" aria-labelledby="budgets-glance-title">
      <div className="card-header">
        <h2 id="budgets-glance-title">Budgets this month</h2>
        <Link to="/budgets" className="see-all">{budgets.length > 0 ? "All budgets" : "Set up"}</Link>
      </div>
      {budgets.length === 0 ? (
        <p className="empty-state">
          Set a monthly limit for groceries, eating out or anything else, and we'll warn you before you go over.
        </p>
      ) : (
        <ul className="glance-list">
          {budgets.map((budget) => (
            <li key={budget.id}>
              <BudgetMeter budget={budget} currency={currency} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
