import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { NotificationsProvider, useNotifications } from "../NotificationsContext";
import TigerLogo from "./TigerLogo";
import { BellIcon, CalendarIcon, HomeIcon } from "./Icons";
import "../styles/shell.css";

const NAV = [
  { to: "/dashboard", label: "Home", Icon: HomeIcon },
  { to: "/calendar", label: "Calendar", Icon: CalendarIcon },
  { to: "/notifications", label: "Alerts", Icon: BellIcon, badge: true },
];

function Shell() {
  const { user, logout } = useAuth();
  const { unread } = useNotifications();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/");
  };

  return (
    <div className="shell">
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
            <span className="shell-user-name">{user?.fullName}</span>
            <button type="button" className="shell-logout" onClick={handleLogout}>
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="shell-main">
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
