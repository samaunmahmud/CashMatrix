import { Link } from "react-router-dom";
import { BankIcon, ListIcon, PlusIcon } from "./Icons";

/** The page's main actions. Transactions only shows where the sidebar isn't there to reach it. */
export default function QuickActions({ onAdd, onLinkBank, linkDisabled }) {
  return (
    <div className="quick-actions" role="group" aria-label="Quick actions">
      <Link to="/transactions" className="btn btn-outline btn-sm quick-narrow">
        <ListIcon size={16} /> Transactions
      </Link>
      <button type="button" className="btn btn-outline btn-sm" onClick={onAdd}>
        <PlusIcon size={16} /> Add payment
      </button>
      <button type="button" className="btn btn-sm" onClick={onLinkBank} disabled={linkDisabled}>
        <BankIcon size={16} /> Link a bank
      </button>
    </div>
  );
}
