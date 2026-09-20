import { useState, useCallback, useEffect } from "react";
import { usePlaidLink } from "react-plaid-link";
import { Link } from "react-router-dom";
import api, { calendarApi, plaidApi } from "./api";
import { useAuth } from "./AuthContext";
import { addDays, shortDate, todayISO } from "./dates";
import { formatMoney } from "./format";
import "./styles/dashboard.css";

export default function DashboardPage() {
  const [linkToken, setLinkToken] = useState(null);
  const [connected, setConnected] = useState(false);
  const [transactions, setTransactions] = useState([]);
  const [upcoming, setUpcoming] = useState([]);
  const [statusMessage, setStatusMessage] = useState("");
  const [loadingToken, setLoadingToken] = useState(true);
  const [loadingTransactions, setLoadingTransactions] = useState(true);

  const { user } = useAuth();

  const loadTransactions = useCallback(
    () =>
      api.get("/transactions").then((res) => {
        if (res.data.length > 0) {
          setConnected(true);
          setTransactions(res.data);
        }
      }),
    []
  );

  useEffect(() => {
    plaidApi
      .createLinkToken()
      .then((res) => setLinkToken(res.data.link_token))
      .catch(() => setStatusMessage("Couldn't start bank connection. Try refreshing."))
      .finally(() => setLoadingToken(false));

    loadTransactions()
      .catch(console.error)
      .finally(() => setLoadingTransactions(false));

    // The "Coming up" panel is a bonus; if it fails the rest of the page still works.
    const today = todayISO();
    calendarApi
      .entries(today, addDays(today, 14))
      .then((res) => setUpcoming(res.data.filter((entry) => !entry.completed)))
      .catch(() => {});
  }, [loadTransactions]);

  const refresh = () => {
    setLoadingTransactions(true);
    loadTransactions()
      .catch(console.error)
      .finally(() => setLoadingTransactions(false));
  };

  const onSuccess = useCallback(
    (publicToken) => {
      setStatusMessage("Connecting your account...");
      plaidApi
        .exchangeToken(publicToken)
        .then(() => {
          setStatusMessage("Syncing transactions...");
          return api.post("/transactions/sync");
        })
        .then(() => {
          setConnected(true);
          loadTransactions();
          setStatusMessage("");
        })
        .catch(() => setStatusMessage("Something went wrong connecting your bank."));
    },
    [loadTransactions]
  );

  const { open, ready } = usePlaidLink({ token: linkToken, onSuccess });

  // Plaid reports money leaving the account as a positive amount and money arriving as negative.
  const categoryTotals = transactions.reduce((acc, tx) => {
    if (tx.amount <= 0) return acc;
    const cat = tx.userCategory || tx.plaidCategory || "Uncategorized";
    acc[cat] = (acc[cat] || 0) + tx.amount;
    return acc;
  }, {});
  const categories = Object.entries(categoryTotals).sort((a, b) => b[1] - a[1]);
  const totalSpent = categories.reduce((sum, [, amount]) => sum + amount, 0);
  const moneyIn = transactions.reduce((sum, tx) => (tx.amount < 0 ? sum - tx.amount : sum), 0);

  const firstName = user?.fullName?.split(" ")[0];

  return (
    <div className="dashboard">
      <section className="hero" aria-label="Summary">
        <p className="hero-hello">Hello{firstName ? `, ${firstName}` : ""}</p>
        {connected ? (
          <div className="hero-figures">
            <div>
              <p className="hero-label">Spent in the last 90 days</p>
              <p className="hero-amount">{formatMoney(totalSpent)}</p>
            </div>
            <div>
              <p className="hero-label">Money in</p>
              <p className="hero-amount hero-amount-in">+{formatMoney(moneyIn)}</p>
            </div>
          </div>
        ) : (
          <p className="hero-label">Connect a bank account to see where your money goes.</p>
        )}
      </section>

      <div className="dashboard-grid">
        <div className="dashboard-main">
          {!connected ? (
            <section className="card connect-card">
              <h2>Connect your bank account</h2>
              <p className="muted">
                Securely link a bank account to start tracking your spending automatically.
              </p>
              <button type="button" className="btn" onClick={() => open()} disabled={!ready || loadingToken}>
                {loadingToken ? "Loading…" : "Connect a bank account"}
              </button>
              {statusMessage && <p className="muted status">{statusMessage}</p>}
            </section>
          ) : (
            <>
              <section className="card" aria-labelledby="categories-title">
                <div className="card-header">
                  <h2 id="categories-title">Spending by category</h2>
                </div>
                {categories.length === 0 ? (
                  <p className="empty-state">No spending data yet.</p>
                ) : (
                  categories.map(([cat, amount]) => (
                    <div key={cat} className="category-row">
                      <div className="category-info">
                        <span>{cat}</span>
                        <strong>{formatMoney(amount)}</strong>
                      </div>
                      <div className="bar-track" role="presentation">
                        <div className="bar-fill" style={{ width: `${(amount / totalSpent) * 100}%` }} />
                      </div>
                    </div>
                  ))
                )}
              </section>

              <section className="card" aria-labelledby="transactions-title">
                <div className="card-header">
                  <h2 id="transactions-title">Transactions</h2>
                  <button type="button" className="btn btn-outline btn-sm" onClick={refresh}>
                    Refresh
                  </button>
                </div>
                {loadingTransactions ? (
                  <p className="empty-state">Loading…</p>
                ) : transactions.length === 0 ? (
                  <p className="empty-state">No transactions yet.</p>
                ) : (
                  <ul className="tx-list">
                    {transactions.map((tx) => (
                      <li key={tx.id} className="tx-row">
                        <div className="tx-left">
                          <span className="tx-name">{tx.name}</span>
                          <span className="tx-sub">
                            {tx.userCategory || tx.plaidCategory || "Uncategorized"} · {tx.transactionDate}
                          </span>
                        </div>
                        <span className={`tx-amount${tx.amount < 0 ? " tx-in" : ""}`}>
                          {tx.amount < 0 ? "+" : ""}
                          {formatMoney(Math.abs(tx.amount))}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </>
          )}
        </div>

        <aside className="dashboard-side">
          <section className="card" aria-labelledby="coming-up-title">
            <div className="card-header">
              <h2 id="coming-up-title">Coming up</h2>
              <Link to="/calendar" className="see-all">Calendar</Link>
            </div>
            {upcoming.length === 0 ? (
              <p className="empty-state">
                Nothing due in the next two weeks.{" "}
                <Link to="/calendar">Add a payment</Link>
              </p>
            ) : (
              <ul className="coming-list">
                {upcoming.slice(0, 5).map((entry) => (
                  <li key={`${entry.eventId}-${entry.date}`}>
                    <Link to={`/calendar?date=${entry.date}`} className="coming-item">
                      <span className="coming-date">{shortDate(entry.date)}</span>
                      <span className="coming-title">{entry.title}</span>
                      {entry.amount != null && <span className="coming-amount">{formatMoney(entry.amount)}</span>}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>
    </div>
  );
}
