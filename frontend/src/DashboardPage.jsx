import { useState, useCallback, useEffect } from "react";
import { usePlaidLink } from "react-plaid-link";
import { Link } from "react-router-dom";
import { accountApi, budgetApi, calendarApi, insightsApi, plaidApi, subscriptionApi, transactionApi } from "./api";
import { useAuth } from "./AuthContext";
import { useNotifications } from "./NotificationsContext";
import AccountCard from "./components/AccountCard";
import BudgetsGlance from "./components/BudgetsGlance";
import EventDialog from "./components/EventDialog";
import MonthlyTrend from "./components/MonthlyTrend";
import QuickActions from "./components/QuickActions";
import SpendingDonut from "./components/SpendingDonut";
import SubscriptionSuggestions from "./components/SubscriptionSuggestions";
import TransactionList from "./components/TransactionList";
import { addDays, shortDate, todayISO, updatedLabel } from "./dates";
import { formatMoney } from "./format";
import "./styles/dashboard.css";

const greeting = () => {
  const hour = new Date().getHours();
  return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
};

export default function DashboardPage() {
  const [linkToken, setLinkToken] = useState(null);
  const [accounts, setAccounts] = useState(null); // null while loading
  const [transactions, setTransactions] = useState([]);
  const [loadingTransactions, setLoadingTransactions] = useState(true);
  const [upcoming, setUpcoming] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [insights, setInsights] = useState(null);
  const [budgets, setBudgets] = useState(null);
  const [statusMessage, setStatusMessage] = useState("");
  const [syncing, setSyncing] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  const { user } = useAuth();
  const { unread, refreshUnread } = useNotifications();
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  // One place that loads everything the page shows. Each piece fails on its own, so a
  // problem with (say) suggestions never blanks the balances.
  useEffect(() => {
    let active = true;
    const today = todayISO();

    accountApi.list()
      .then((res) => active && setAccounts(res.data))
      .catch(() => active && setAccounts([]));
    transactionApi.list()
      .then((res) => active && setTransactions(res.data))
      .catch(console.error)
      .finally(() => active && setLoadingTransactions(false));
    calendarApi.entries(today, addDays(today, 14))
      .then((res) => active && setUpcoming(res.data.filter((entry) => !entry.completed)))
      .catch(() => {});
    subscriptionApi.suggestions()
      .then((res) => active && setSuggestions(res.data))
      .catch(() => {});
    insightsApi.get(6)
      .then((res) => active && setInsights(res.data))
      .catch(() => {});
    budgetApi.overview()
      .then((res) => active && setBudgets(res.data))
      .catch(() => {});

    return () => {
      active = false;
    };
  }, [reloadKey]);

  useEffect(() => {
    let active = true;
    plaidApi.createLinkToken()
      .then((res) => active && setLinkToken(res.data.link_token))
      .catch(() => active && setStatusMessage("Couldn't start bank connection. Try refreshing."));
    return () => {
      active = false;
    };
  }, []);

  const syncNow = async () => {
    setSyncing(true);
    setStatusMessage("");
    try {
      await transactionApi.sync();
      reload();
      refreshUnread();
    } catch {
      setStatusMessage("Couldn't sync just now. Please try again.");
    } finally {
      setSyncing(false);
    }
  };

  const onPlaidSuccess = useCallback(
    (publicToken) => {
      setStatusMessage("Connecting your account…");
      plaidApi.exchangeToken(publicToken)
        .then(() => {
          setStatusMessage("Syncing transactions…");
          return transactionApi.sync();
        })
        .then(() => {
          setStatusMessage("");
          reload();
        })
        .catch(() => setStatusMessage("Something went wrong connecting your bank."));
    },
    [reload]
  );

  const { open, ready } = usePlaidLink({ token: linkToken, onSuccess: onPlaidSuccess });

  const connected = (accounts?.length ?? 0) > 0 || transactions.length > 0;
  const currency = accounts?.find((a) => a.currency)?.currency;

  // Plaid reports money leaving the account as a positive amount and money arriving as negative.
  const categoryTotals = transactions.reduce((acc, tx) => {
    if (tx.amount <= 0) return acc;
    const category = tx.userCategory || tx.plaidCategory || "Uncategorised";
    acc[category] = (acc[category] || 0) + tx.amount;
    return acc;
  }, {});
  const categories = Object.entries(categoryTotals).sort((a, b) => b[1] - a[1]);
  const totalSpent = categories.reduce((sum, [, amount]) => sum + amount, 0);
  const moneyIn = transactions.reduce((sum, tx) => (tx.amount < 0 ? sum - tx.amount : sum), 0);

  // A total across accounts only makes sense when they are all in one currency.
  const cash = (accounts ?? []).filter((a) => a.type === "depository" && a.currentBalance != null);
  const totalBalance =
    cash.length > 0 && new Set(cash.map((a) => a.currency)).size === 1
      ? cash.reduce((sum, a) => sum + a.currentBalance, 0)
      : null;
  const lastUpdated = (accounts ?? [])
    .map((a) => a.balanceUpdatedAt)
    .filter(Boolean)
    .sort()
    .pop();

  const firstName = user?.fullName?.split(" ")[0];

  return (
    <div className="home">
      <section className="hero" aria-label="Summary">
        <p className="hero-hello">{greeting()}{firstName ? `, ${firstName}` : ""}</p>
        {totalBalance != null ? (
          <>
            <p className="hero-label">Total in your accounts</p>
            <p className="hero-amount">{formatMoney(totalBalance, currency)}</p>
            {transactions.length > 0 && (
              <p className="hero-stats">
                <span>Spent <strong>{formatMoney(totalSpent, currency)}</strong></span>
                <span>Money in <strong className="hero-in">{formatMoney(moneyIn, currency)}</strong></span>
                <span className="hero-period">last 90 days</span>
              </p>
            )}
          </>
        ) : connected ? (
          <p className="hero-label">Here's where your money went.</p>
        ) : (
          <p className="hero-label">Link a bank to see your balances and spending.</p>
        )}
      </section>

      <section className="accounts" aria-label="Your accounts">
        <div className="accounts-head">
          <h2>Your accounts</h2>
          {lastUpdated && <span className="muted">Updated {updatedLabel(lastUpdated)}</span>}
        </div>
        {accounts === null ? (
          <div className="accounts-row" aria-busy="true">
            <div className="account-card skeleton" />
            <div className="account-card skeleton" />
          </div>
        ) : accounts.length > 0 ? (
          <div className="accounts-row">
            {[...accounts]
              .sort((a, b) => (a.type === "credit") - (b.type === "credit"))
              .map((account) => (
                <AccountCard key={account.id} account={account} />
              ))}
          </div>
        ) : (
          <div className="card connect-card">
            <h3>Connect your bank account</h3>
            <p className="muted">
              Securely link a bank account to see your balances and track your spending automatically.
            </p>
            <button type="button" className="btn" onClick={() => open()} disabled={!ready}>
              {linkToken ? "Connect a bank account" : "Loading…"}
            </button>
          </div>
        )}
        {statusMessage && <p className="status" role="status">{statusMessage}</p>}
      </section>

      <QuickActions
        onAdd={() => setAddOpen(true)}
        onLinkBank={() => open()}
        linkDisabled={!ready}
        unread={unread}
      />

      <div className="home-grid">
        <div className="home-main">
          {connected && insights?.months.length > 0 && (
            <section className="card" aria-labelledby="trend-title">
              <div className="card-header">
                <h2 id="trend-title">Monthly spending</h2>
                <span className="muted">Last {insights.months.length} month{insights.months.length === 1 ? "" : "s"}</span>
              </div>
              <MonthlyTrend insights={insights} currency={currency} />
            </section>
          )}

          {connected && (
            <section className="card" aria-labelledby="categories-title">
              <div className="card-header">
                <h2 id="categories-title">Where your money went</h2>
                <span className="muted">Last 90 days</span>
              </div>
              {categories.length === 0 ? (
                <p className="empty-state">No spending data yet.</p>
              ) : (
                <SpendingDonut categories={categories} total={totalSpent} currency={currency} />
              )}
            </section>
          )}

          {connected && (
            <TransactionList
              transactions={transactions}
              loading={loadingTransactions}
              currency={currency}
              onRefresh={syncNow}
              refreshing={syncing}
            />
          )}
        </div>

        <aside className="home-side">
          <section className="card" aria-labelledby="coming-up-title">
            <div className="card-header">
              <h2 id="coming-up-title">Coming up</h2>
              <Link to="/calendar" className="see-all">Calendar</Link>
            </div>
            {upcoming.length === 0 ? (
              <p className="empty-state">
                Nothing due in the next two weeks.{" "}
                <button type="button" className="link-button" onClick={() => setAddOpen(true)}>Add a payment</button>
              </p>
            ) : (
              <ul className="coming-list">
                {upcoming.slice(0, 5).map((entry) => (
                  <li key={`${entry.eventId}-${entry.date}`}>
                    <Link to={`/calendar?date=${entry.date}`} className="coming-item">
                      <span className="coming-date">{shortDate(entry.date)}</span>
                      <span className="coming-title">{entry.title}</span>
                      {entry.amount != null && <span className="coming-amount">{formatMoney(entry.amount, currency)}</span>}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {connected && budgets && <BudgetsGlance overview={budgets} currency={currency} />}

          <SubscriptionSuggestions
            suggestions={suggestions}
            currency={currency}
            onChanged={async () => {
              reload();
              refreshUnread();
            }}
          />
        </aside>
      </div>

      {addOpen && (
        <EventDialog
          defaultDate={todayISO()}
          onClose={() => setAddOpen(false)}
          onSaved={() => {
            setAddOpen(false);
            reload();
            refreshUnread();
          }}
        />
      )}
    </div>
  );
}
