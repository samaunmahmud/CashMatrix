import { Link } from "react-router-dom";
import { BankIcon, BellIcon, CalendarIcon, PlusIcon } from "./Icons";

/** Round shortcut buttons, like the row of actions under the balances in a banking app. */
export default function QuickActions({ onAdd, onLinkBank, linkDisabled, unread }) {
  return (
    <nav className="quick-actions" aria-label="Quick actions">
      <button type="button" className="quick-action" onClick={onAdd} aria-label="Add a payment, subscription or task">
        <span className="quick-icon"><PlusIcon /></span>
        <span aria-hidden="true">Add</span>
      </button>
      <Link to="/calendar" className="quick-action">
        <span className="quick-icon"><CalendarIcon /></span>
        <span>Calendar</span>
      </Link>
      <Link to="/notifications" className="quick-action">
        <span className="quick-icon">
          <BellIcon />
          {unread > 0 && <span className="quick-badge" aria-hidden="true">{unread > 9 ? "9+" : unread}</span>}
        </span>
        <span>Alerts{unread > 0 && <span className="sr-only"> ({unread} unread)</span>}</span>
      </Link>
      <button type="button" className="quick-action" onClick={onLinkBank} disabled={linkDisabled}>
        <span className="quick-icon"><BankIcon /></span>
        <span>Link a bank</span>
      </button>
    </nav>
  );
}
