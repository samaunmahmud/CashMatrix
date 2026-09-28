import { Fragment, useState } from "react";
import { Link } from "react-router-dom";
import { categoryOf } from "../categories";
import { formatMoney } from "../format";
import { dayHeading } from "../dates";
import Avatar from "./Avatar";

const PREVIEW = 8;

/** Consecutive transactions on the same day share a heading, as in a bank statement. */
function byDay(transactions) {
  const days = [];
  for (const tx of transactions) {
    const last = days[days.length - 1];
    if (last && last.date === tx.transactionDate) last.items.push(tx);
    else days.push({ date: tx.transactionDate, items: [tx] });
  }
  return days;
}

function RowContent({ tx, currency, showAccount }) {
  return (
    <>
      <Avatar name={tx.merchant || tx.name} />
      <div className="tx-left">
        <span className="tx-name">{tx.merchant || tx.name}</span>
        <span className="tx-sub">
          <span className="tx-category">{categoryOf(tx)}</span>
          {showAccount && tx.accountName && <span className="tx-account">· {tx.accountName}</span>}
          {tx.pending && <span className="badge badge-pending">Pending</span>}
        </span>
        {tx.note && <span className="tx-note">{tx.note}</span>}
      </div>
      <span className={`tx-amount${tx.amount < 0 ? " tx-in" : ""}`}>
        {tx.amount < 0 ? "+" : "−"}
        {formatMoney(Math.abs(tx.amount), currency)}
      </span>
    </>
  );
}

/**
 * A statement: transactions under day headings. With onSelect, each row is a button that opens
 * the transaction (to re-file it or add a note).
 */
export function TransactionRows({ transactions, currency, onSelect, showAccount = false }) {
  return (
    <ul className="tx-list">
      {byDay(transactions).map((day) => (
        <Fragment key={day.date}>
          <li className="tx-day"><h3>{dayHeading(day.date)}</h3></li>
          {day.items.map((tx) =>
            onSelect ? (
              <li key={tx.id} className="tx-row tx-row-action">
                <button
                  type="button"
                  className="tx-button"
                  onClick={() => onSelect(tx)}
                  aria-label={`${tx.merchant || tx.name}, ${tx.amount < 0 ? "money in" : "money out"} ${formatMoney(Math.abs(tx.amount), currency)}, ${categoryOf(tx)}. Edit`}
                >
                  <RowContent tx={tx} currency={currency} showAccount={showAccount} />
                </button>
              </li>
            ) : (
              <li key={tx.id} className="tx-row">
                <RowContent tx={tx} currency={currency} showAccount={showAccount} />
              </li>
            )
          )}
        </Fragment>
      ))}
    </ul>
  );
}

export default function TransactionList({ transactions, loading, currency, onRefresh, refreshing, onSelect }) {
  const [showAll, setShowAll] = useState(false);
  const visible = showAll ? transactions : transactions.slice(0, PREVIEW);

  return (
    <section className="card" aria-labelledby="transactions-title">
      <div className="card-header">
        <h2 id="transactions-title">Recent transactions</h2>
        <div className="card-header-actions">
          <Link to="/transactions" className="see-all">See all</Link>
          <button type="button" className="btn btn-outline btn-sm" onClick={onRefresh} disabled={refreshing}>
            {refreshing ? "Syncing…" : "Sync now"}
          </button>
        </div>
      </div>

      {loading ? (
        <p className="empty-state">Loading…</p>
      ) : transactions.length === 0 ? (
        <p className="empty-state">No transactions yet.</p>
      ) : (
        <>
          <TransactionRows transactions={visible} currency={currency} onSelect={onSelect} />
          {transactions.length > PREVIEW && (
            <button type="button" className="link-button show-more" onClick={() => setShowAll(!showAll)}>
              {showAll ? "Show fewer" : `Show all ${transactions.length}`}
            </button>
          )}
        </>
      )}
    </section>
  );
}
