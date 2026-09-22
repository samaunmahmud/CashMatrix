import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { accountApi, transactionApi } from "./api";
import { AccountSummary } from "./components/AccountCard";
import { ChevronLeftIcon, SearchIcon } from "./components/Icons";
import { TransactionRows } from "./components/TransactionList";
import { formatMoney } from "./format";
import "./styles/account.css";

const PAGE = 30;

const FILTERS = [
  ["all", "All"],
  ["out", "Money out"],
  ["in", "Money in"],
];

/** Matches the merchant, the raw bank description, the category or the amount ("12.50"). */
function matches(tx, query) {
  if (!query) return true;
  const q = query.toLowerCase();
  return [tx.merchant, tx.name, tx.userCategory, tx.plaidCategory, Math.abs(tx.amount).toFixed(2)]
    .some((field) => field?.toLowerCase().includes(q));
}

export default function AccountPage() {
  const { id } = useParams();
  const [account, setAccount] = useState(undefined); // undefined while loading, null if not found
  const [transactions, setTransactions] = useState(null);
  const [error, setError] = useState(false);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");
  const [limit, setLimit] = useState(PAGE);

  useEffect(() => {
    let active = true;
    Promise.all([accountApi.list(), transactionApi.list()])
      .then(([accounts, txs]) => {
        if (!active) return;
        setAccount(accounts.data.find((a) => String(a.id) === id) ?? null);
        setTransactions(txs.data.filter((tx) => String(tx.accountId) === id));
        setError(false);
      })
      .catch(() => active && setError(true));
    return () => {
      active = false;
    };
  }, [id]);

  const shown = useMemo(
    () =>
      (transactions ?? [])
        .filter((tx) => filter === "all" || (filter === "out" ? tx.amount > 0 : tx.amount < 0))
        .filter((tx) => matches(tx, query.trim())),
    [transactions, filter, query]
  );
  const totalOut = shown.reduce((sum, tx) => (tx.amount > 0 ? sum + tx.amount : sum), 0);
  const totalIn = shown.reduce((sum, tx) => (tx.amount < 0 ? sum - tx.amount : sum), 0);
  const currency = account?.currency;

  return (
    <div className="account-page">
      <Link to="/dashboard" className="back-link"><ChevronLeftIcon size={18} /> Home</Link>

      {error && (
        <div className="banner banner-error" role="alert">
          <span>We couldn't load this account.</span>
        </div>
      )}

      {account === null ? (
        <section className="card"><p className="empty-state">We couldn't find that account.</p></section>
      ) : account === undefined ? (
        !error && <div className="account-card skeleton account-hero" aria-busy="true" />
      ) : (
        <>
          <h1 className="sr-only">{account.name}</h1>
          <div className="account-hero"><AccountSummary account={account} /></div>

          <section className="card" aria-labelledby="statement-title">
            <div className="card-header">
              <h2 id="statement-title">Transactions</h2>
              <span className="muted">Last 90 days</span>
            </div>

            <div className="statement-tools">
              <label className="search-field">
                <SearchIcon size={18} />
                <span className="sr-only">Search transactions</span>
                <input
                  className="input"
                  type="search"
                  value={query}
                  onChange={(e) => {
                    setQuery(e.target.value);
                    setLimit(PAGE);
                  }}
                  placeholder="Search by name, category or amount"
                />
              </label>
              <fieldset className="segmented statement-filter">
                <legend className="sr-only">Show</legend>
                <div className="segmented-options">
                  {FILTERS.map(([value, label]) => (
                    <label key={value}>
                      <input type="radio" name="tx-filter" value={value} checked={filter === value} onChange={() => {
                        setFilter(value);
                        setLimit(PAGE);
                      }} />
                      <span>{label}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
            </div>

            {transactions && shown.length > 0 && (
              <p className="statement-totals" role="status">
                {shown.length} transaction{shown.length === 1 ? "" : "s"}
                {totalOut > 0 && <> · <strong>{formatMoney(totalOut, currency)}</strong> out</>}
                {totalIn > 0 && <> · <strong className="tx-in">{formatMoney(totalIn, currency)}</strong> in</>}
              </p>
            )}

            {transactions === null ? (
              <p className="empty-state">Loading…</p>
            ) : shown.length === 0 ? (
              <p className="empty-state">
                {transactions.length === 0 ? "No transactions on this account yet." : "Nothing matches your search."}
              </p>
            ) : (
              <>
                <TransactionRows transactions={shown.slice(0, limit)} currency={currency} />
                {shown.length > limit && (
                  <button type="button" className="btn btn-outline btn-sm show-more" onClick={() => setLimit((l) => l + PAGE)}>
                    Show more ({shown.length - limit} left)
                  </button>
                )}
              </>
            )}
          </section>
        </>
      )}
    </div>
  );
}
