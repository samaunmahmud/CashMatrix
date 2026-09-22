import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { NotificationsProvider, useNotifications } from "../NotificationsContext";
import TigerLogo from "./TigerLogo";
import { BellIcon, BudgetIcon, CalendarIcon, HomeIcon, SettingsIcon } from "./Icons";
import "../styles/shell.css";

const NAV = [
  { to: "/dashboard", label: "Home", Icon: HomeIcon },
  { to: "/calendar", label: "Calendar", Icon: CalendarIcon },
  { to: "/budgets", label: "Budgets", Icon: BudgetIcon },
  { to: "/notifications", label: "Alerts", Icon: BellIcon, badge: true },
  { to: "/settings", label: "Settings", Icon: SettingsIcon },
];

function Shell() {
  const { user } = useAuth();
  const { unread } = useNotifications();

  return (
    <div className="shell">
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="shell-header">
        <div className="shell-header-inner">
          <NavLink to="/dashboard" className="brand" aria-label="CashMatrix home">
            <TigerLogo size={38} />
            <span className="brand-name">CashMatrix</span>
          </NavLink>

          <nav className="shell-nav" aria-label="Main">
            {NAV.map(({ to, label, Icon, badge }) => (
              <NavLink key={to} to={to} className="nav-link">
                <span className="nav-icon">
                  <Icon />
                  {badge && unread > 0 && (
                    <span className="nav-badge" aria-hidden="true">{unread > 9 ? "9+" : unread}</span>
                  )}
                </span>
                <span className="nav-label">
                  {label}
                  {badge && unread > 0 && <span className="sr-only"> ({unread} unread)</span>}
                </span>
              </NavLink>
            ))}
          </nav>

          <div className="shell-user">
            <span className="shell-avatar" aria-hidden="true">{user?.fullName?.[0]?.toUpperCase() ?? "?"}</span>
            <span className="shell-user-name">{user?.fullName}</span>
          </div>
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
