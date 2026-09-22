import { Fragment, useState } from "react";
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

export function TransactionRows({ transactions, currency }) {
  return (
    <ul className="tx-list">
      {byDay(transactions).map((day) => (
        <Fragment key={day.date}>
          <li className="tx-day"><h3>{dayHeading(day.date)}</h3></li>
          {day.items.map((tx) => (
            <li key={tx.id} className="tx-row">
              <Avatar name={tx.merchant || tx.name} />
              <div className="tx-left">
                <span className="tx-name">{tx.merchant || tx.name}</span>
                <span className="tx-sub">
                  {tx.userCategory || tx.plaidCategory || "Uncategorised"}
                  {tx.pending && <span className="badge badge-pending">Pending</span>}
                </span>
              </div>
              <span className={`tx-amount${tx.amount < 0 ? " tx-in" : ""}`}>
                {tx.amount < 0 ? "+" : "−"}
                {formatMoney(Math.abs(tx.amount), currency)}
              </span>
            </li>
          ))}
        </Fragment>
      ))}
    </ul>
  );
}

export default function TransactionList({ transactions, loading, currency, onRefresh, refreshing }) {
  const [showAll, setShowAll] = useState(false);
  const visible = showAll ? transactions : transactions.slice(0, PREVIEW);

  return (
    <section className="card" aria-labelledby="transactions-title">
      <div className="card-header">
        <h2 id="transactions-title">Recent transactions</h2>
        <button type="button" className="btn btn-outline btn-sm" onClick={onRefresh} disabled={refreshing}>
          {refreshing ? "Syncing…" : "Sync now"}
        </button>
      </div>

      {loading ? (
        <p className="empty-state">Loading…</p>
      ) : transactions.length === 0 ? (
        <p className="empty-state">No transactions yet.</p>
      ) : (
        <>
          <TransactionRows transactions={visible} currency={currency} />
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
