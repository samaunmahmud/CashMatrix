import { useState } from "react";
import { formatMoney } from "../format";
import { shortDate } from "../dates";
import Avatar from "./Avatar";

const PREVIEW = 8;

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
          <ul className="tx-list">
            {visible.map((tx) => (
              <li key={tx.id} className="tx-row">
                <Avatar name={tx.name} />
                <div className="tx-left">
                  <span className="tx-name">{tx.name}</span>
                  <span className="tx-sub">
                    {tx.userCategory || tx.plaidCategory || "Uncategorised"} · {shortDate(tx.transactionDate)}
                    {tx.pending ? " · Pending" : ""}
                  </span>
                </div>
                <span className={`tx-amount${tx.amount < 0 ? " tx-in" : ""}`}>
                  {tx.amount < 0 ? "+" : "−"}
                  {formatMoney(Math.abs(tx.amount), currency)}
                </span>
              </li>
            ))}
          </ul>
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
