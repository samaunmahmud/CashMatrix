import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { categoryRuleApi } from "../api";
import { TrashIcon } from "./Icons";

/** The "always file this retailer under..." rules, with a way to stop each one. */
export default function CategoryRulesSettings() {
  const [rules, setRules] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    categoryRuleApi.list()
      .then((res) => active && setRules(res.data))
      .catch(() => active && setError("We couldn't load your rules."));
    return () => {
      active = false;
    };
  }, []);

  const remove = async (rule) => {
    try {
      await categoryRuleApi.remove(rule.id);
      setRules((list) => list.filter((r) => r.id !== rule.id));
    } catch {
      setError("Couldn't remove that rule. Please try again.");
    }
  };

  return (
    <section className="card settings-card" aria-labelledby="rules-title">
      <h2 id="rules-title">Category rules</h2>
      <p className="muted settings-intro">
        New transactions from these retailers are filed under your chosen category. Make a rule by opening
        any transaction in <Link to="/transactions">Transactions</Link> and ticking “Always use this”.
      </p>
      {error && <p className="error-text" role="alert">{error}</p>}
      {rules && rules.length === 0 && <p className="muted">No rules yet.</p>}
      {rules && rules.length > 0 && (
        <ul className="rule-list">
          {rules.map((rule) => (
            <li key={rule.id}>
              <span className="rule-text">
                <strong>{rule.retailer}</strong>
                <span className="muted">→ {rule.category}</span>
              </span>
              <button
                type="button"
                className="icon-btn icon-btn-danger"
                onClick={() => remove(rule)}
                aria-label={`Stop filing ${rule.retailer} under ${rule.category}`}
              >
                <TrashIcon size={18} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
