import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { NotificationsProvider, useNotifications } from "../NotificationsContext";
import TigerLogo from "./TigerLogo";
import { BellIcon, BudgetIcon, CalendarIcon, ChartIcon, GoalIcon, HomeIcon, ListIcon, SettingsIcon } from "./Icons";
import "../styles/shell.css";

// Grouped for the desktop sidebar. Smaller screens show the items in one row and leave out
// the `wide` ones, which stay reachable from Home.
const NAV = [
  {
    title: "Overview",
    items: [
      { to: "/dashboard", label: "Home", Icon: HomeIcon },
      { to: "/transactions", label: "Transactions", Icon: ListIcon, wide: true },
      { to: "/summary", label: "Monthly summary", Icon: ChartIcon, wide: true },
    ],
  },
  {
    title: "Planning",
    items: [
      { to: "/budgets", label: "Budgets", Icon: BudgetIcon },
      { to: "/goals", label: "Goals", Icon: GoalIcon },
      { to: "/calendar", label: "Calendar", Icon: CalendarIcon },
    ],
  },
  {
    title: "Account",
    items: [
      { to: "/notifications", label: "Alerts", Icon: BellIcon, badge: true },
      { to: "/settings", label: "Settings", Icon: SettingsIcon },
    ],
  },
];

function Shell() {
  const { user } = useAuth();
  const { unread } = useNotifications();

  return (
    <div className="shell">
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="shell-header">
        <NavLink to="/dashboard" className="brand" aria-label="CashMatrix home">
          <TigerLogo size={34} />
          <span className="brand-name">CashMatrix</span>
        </NavLink>

        <nav className="shell-nav" aria-label="Main">
          {NAV.map(({ title, items }) => (
            <div key={title} className="nav-group">
              <p className="nav-group-title">{title}</p>
              {items.map(({ to, label, Icon, badge, wide }) => (
                <NavLink key={to} to={to} className={`nav-link${wide ? " nav-link-wide" : ""}`}>
                  <span className="nav-icon">
                    <Icon size={20} />
                    {badge && unread > 0 && (
                      <span className="nav-badge" aria-hidden="true">{unread > 9 ? "9+" : unread}</span>
                    )}
                  </span>
                  <span className="nav-label">
                    {label}
                    {badge && unread > 0 && <span className="sr-only"> ({unread} unread)</span>}
                  </span>
                  {badge && unread > 0 && (
                    <span className="nav-count" aria-hidden="true">{unread > 99 ? "99+" : unread}</span>
                  )}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="shell-user">
          <span className="shell-avatar" aria-hidden="true">{user?.fullName?.[0]?.toUpperCase() ?? "?"}</span>
          <span className="shell-user-text">
            <span className="shell-user-name">{user?.fullName}</span>
            <span className="shell-user-email">{user?.email}</span>
          </span>
        </div>
      </header>

      <main id="main" className="shell-main">
        <Outlet />
      </main>
    </div>
  );
}

export default function AppShell() {
  return (
    <NotificationsProvider>
      <Shell />
    </NotificationsProvider>
  );
}
