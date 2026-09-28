import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { accountApi, transactionApi } from "./api";
import { categoryOf, knownCategories } from "./categories";
import { DownloadIcon, SearchIcon } from "./components/Icons";
import { TransactionRows } from "./components/TransactionList";
import { transactionsCsv, downloadFile } from "./csv";
import { addDays, addMonths, monthISO, monthLabel, todayISO } from "./dates";
import { formatMoney } from "./format";
import useTransactionEditing from "./hooks/useTransactionEditing";
import "./styles/account.css";
import "./styles/transactions.css";

const PAGE = 50;

const DIRECTIONS = [
  ["all", "All"],
  ["out", "Money out"],
  ["in", "Money in"],
];

// Each period is a from / to pair of ISO dates (either may be open).
const PERIODS = {
  all: { label: "All time", range: () => [null, null] },
  "30d": { label: "Last 30 days", range: (today) => [addDays(today, -29), today] },
  "90d": { label: "Last 90 days", range: (today) => [addDays(today, -89), today] },
  month: { label: "This month", range: (today) => [`${monthISO()}-01`, today] },
  "last-month": {
    label: "Last month",
    range: () => {
      const month = addMonths(monthISO(), -1);
      return [`${month}-01`, addDays(`${monthISO()}-01`, -1)];
    },
  },
  year: { label: "This year", range: (today) => [`${today.slice(0, 4)}-01-01`, today] },
};

function matches(tx, query) {
  if (!query) return true;
  const q = query.toLowerCase();
  return [tx.merchant, tx.name, categoryOf(tx), tx.note, Math.abs(tx.amount).toFixed(2)]
    .some((field) => field?.toLowerCase().includes(q));
}

/** Every transaction across every account, with filters and a spreadsheet download. */
export default function TransactionsPage() {
  const [params, setParams] = useSearchParams();
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState(null); // null while loading
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [limit, setLimit] = useState(PAGE);

  // Filters live in the address, so a link (such as from the spending chart) can open the page filtered.
  const query = params.get("q") ?? "";
  const direction = params.get("direction") ?? "all";
  const category = params.get("category") ?? "";
  const accountId = params.get("account") ?? "";
  // A single month ("month=2026-09", as the monthly summary links) takes the place of the period.
  const month = /^\d{4}-\d{2}$/.test(params.get("month") ?? "") ? params.get("month") : null;
  const period = month ? `month:${month}` : PERIODS[params.get("period")] ? params.get("period") : "all";
  const periodRange = (today) =>
    month ? [`${month}-01`, addDays(`${addMonths(month, 1)}-01`, -1)] : PERIODS[period].range(today);

  const setFilter = (name, value, fallback = "") => {
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (value === fallback) next.delete(name);
      else next.set(name, value);
      return next;
    }, { replace: true });
    setLimit(PAGE);
  };

  useEffect(() => {
    let active = true;
    Promise.all([accountApi.list(), transactionApi.list()])
      .then(([accountsRes, txRes]) => {
        if (!active) return;
        setAccounts(accountsRes.data);
        setTransactions(txRes.data);
        setError(false);
      })
      .catch(() => active && setError(true));
    return () => {
      active = false;
    };
  }, [reloadKey]);

  const accountNames = useMemo(() => new Map(accounts.map((a) => [a.id, a.name])), [accounts]);
  const currency = accounts.find((a) => a.currency)?.currency;
  const categories = useMemo(() => knownCategories(transactions ?? []), [transactions]);

  const shown = useMemo(() => {
    const [from, to] = periodRange(todayISO());
    const q = query.trim();
    return (transactions ?? [])
      .filter((tx) => !accountId || String(tx.accountId) === accountId)
      .filter((tx) => direction === "all" || (direction === "out" ? tx.amount > 0 : tx.amount < 0))
      .filter((tx) => !category || categoryOf(tx).toLowerCase() === category.toLowerCase())
      .filter((tx) => (!from || tx.transactionDate >= from) && (!to || tx.transactionDate <= to))
      .filter((tx) => matches(tx, q))
      .map((tx) => ({ ...tx, accountName: accountNames.get(tx.accountId) }));
  }, [transactions, accountId, direction, category, period, query, accountNames]); // eslint-disable-line react-hooks/exhaustive-deps

  const totalOut = shown.reduce((sum, tx) => (tx.amount > 0 ? sum + tx.amount : sum), 0);
  const totalIn = shown.reduce((sum, tx) => (tx.amount < 0 ? sum - tx.amount : sum), 0);
  const filtered = Boolean(query || category || accountId || direction !== "all" || period !== "all");

  const editor = useTransactionEditing({
    setTransactions,
    reload: () => setReloadKey((key) => key + 1),
    currency,
    categories,
    accountNameOf: (tx) => accountNames.get(tx.accountId),
  });

  const exportCsv = () => {
    const name = ["cashmatrix-transactions", category, month ?? (period !== "all" && period), todayISO()]
      .filter(Boolean)
      .join("-")
      .replace(/[^\w-]+/g, "-")
      .toLowerCase();
    downloadFile(`${name}.csv`, transactionsCsv(shown));
  };

  return (
    <div className="transactions-page">
      <div className="page-head">
        <div>
          <h1>Transactions</h1>
          <p className="muted">Everything from all your accounts. Tap a transaction to change its category or add a note.</p>
        </div>
        <button type="button" className="btn btn-outline" onClick={exportCsv} disabled={shown.length === 0}>
          <DownloadIcon size={18} /> Export CSV
        </button>
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          <span>We couldn't load your transactions.</span>
          <button type="button" className="link-button" onClick={() => setReloadKey((key) => key + 1)}>Try again</button>
        </div>
      )}

      <section className="card" aria-labelledby="all-tx-title">
        <h2 id="all-tx-title" className="sr-only">Filters and results</h2>
        <div className="statement-tools">
          <label className="search-field">
            <SearchIcon size={18} />
            <span className="sr-only">Search transactions</span>
            <input
              className="input"
              type="search"
              value={query}
              onChange={(e) => setFilter("q", e.target.value)}
              placeholder="Search by name, category, note or amount"
            />
          </label>
          <fieldset className="segmented statement-filter">
            <legend className="sr-only">Show</legend>
            <div className="segmented-options">
              {DIRECTIONS.map(([value, label]) => (
                <label key={value}>
                  <input
                    type="radio"
                    name="tx-direction"
                    value={value}
                    checked={direction === value}
                    onChange={() => setFilter("direction", value, "all")}
                  />
                  <span>{label}</span>
                </label>
              ))}
            </div>
          </fieldset>
        </div>

        <div className="filter-row">
          <label className="field">
            <span>Period</span>
            <select
              className="input"
              value={period}
              onChange={(e) => {
                setParams((current) => {
                  const next = new URLSearchParams(current);
                  next.delete("month");
                  if (e.target.value === "all") next.delete("period");
                  else next.set("period", e.target.value);
                  return next;
                }, { replace: true });
                setLimit(PAGE);
              }}
            >
              {month && <option value={period}>{monthLabel(month)}</option>}
              {Object.entries(PERIODS).map(([value, { label }]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Category</span>
            <select className="input" value={category} onChange={(e) => setFilter("category", e.target.value)}>
              <option value="">All categories</option>
              {category && !categories.some((c) => c.toLowerCase() === category.toLowerCase()) && (
                <option value={category}>{category}</option>
              )}
              {categories.map((name) => (
                <option key={name} value={name}>{name}</option>
              ))}
            </select>
          </label>
          {accounts.length > 1 && (
            <label className="field">
              <span>Account</span>
              <select className="input" value={accountId} onChange={(e) => setFilter("account", e.target.value)}>
                <option value="">All accounts</option>
                {accounts.map((a) => (
                  <option key={a.id} value={String(a.id)}>{a.name}</option>
                ))}
              </select>
            </label>
          )}
        </div>

        {transactions && (
          <div className="statement-totals-row">
            <p className="statement-totals" role="status">
              {shown.length} transaction{shown.length === 1 ? "" : "s"}
              {totalOut > 0 && <> · <strong>{formatMoney(totalOut, currency)}</strong> out</>}
              {totalIn > 0 && <> · <strong className="tx-in">{formatMoney(totalIn, currency)}</strong> in</>}
            </p>
            {filtered && (
              <button type="button" className="link-button" onClick={() => setParams({}, { replace: true })}>
                Clear filters
              </button>
            )}
          </div>
        )}

        {transactions === null ? (
          !error && <p className="empty-state">Loading…</p>
        ) : shown.length === 0 ? (
          <p className="empty-state">
            {transactions.length === 0 ? "No transactions yet. Link a bank from Home to get started." : "Nothing matches these filters."}
          </p>
        ) : (
          <>
            <TransactionRows
              transactions={shown.slice(0, limit)}
              currency={currency}
              onSelect={editor.open}
              showAccount={accounts.length > 1}
            />
            {shown.length > limit && (
              <button type="button" className="btn btn-outline btn-sm show-more" onClick={() => setLimit((l) => l + PAGE)}>
                Show more ({shown.length - limit} left)
              </button>
            )}
          </>
        )}
      </section>

      {editor.ui}
    </div>
  );
}
