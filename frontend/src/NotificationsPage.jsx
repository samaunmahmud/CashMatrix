import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { notificationApi } from "./api";
import { useNotifications } from "./NotificationsContext";
import { shortDate } from "./dates";
import "./styles/notifications.css";

export default function NotificationsPage() {
  const [items, setItems] = useState(null); // null while loading
  const [error, setError] = useState(false);
  const { unread, refreshUnread } = useNotifications();
  const navigate = useNavigate();

  const [reloadKey, setReloadKey] = useState(0);
  const reload = () => setReloadKey((key) => key + 1);

  useEffect(() => {
    let active = true;
    notificationApi
      .list()
      .then(({ data }) => {
        if (!active) return;
        setItems(data);
        setError(false);
      })
      .catch(() => {
        if (active) setError(true);
      })
      // Loading the list also makes the server check for new reminders, so refresh the bell after.
      .finally(refreshUnread);
    return () => {
      active = false;
    };
  }, [reloadKey, refreshUnread]);

  const open = async (notification) => {
    if (!notification.read) {
      try {
        await notificationApi.markRead(notification.id);
        refreshUnread();
      } catch {
        // Not being able to mark it read shouldn't stop the user going to the item.
      }
    }
    navigate(notification.link ?? `/calendar?date=${notification.dueDate}`);
  };

  const markAllRead = async () => {
    try {
      await notificationApi.markAllRead();
      reload();
      refreshUnread();
    } catch {
      setError(true);
    }
  };

  return (
    <div className="notifications-page">
      <div className="page-head">
        <div>
          <h1>Alerts</h1>
          <p className="muted">Reminders for payments, subscriptions and tasks, money in and out of your accounts, and budget warnings.</p>
        </div>
        {unread > 0 && (
          <button type="button" className="btn btn-outline" onClick={markAllRead}>
            Mark all as read
          </button>
        )}
      </div>

      {error && (
        <div className="banner banner-error" role="alert">
          <span>We couldn't load your alerts.</span>
          <button type="button" className="link-button" onClick={reload}>Try again</button>
        </div>
      )}

      <section className="card">
        {items === null && !error ? (
          <p className="empty-state">Loading…</p>
        ) : items?.length === 0 ? (
          <p className="empty-state">
            You're all caught up. Add payments and subscriptions to your calendar and we'll remind you before they're due.
          </p>
        ) : (
          <ul className="notice-list">
            {items?.map((n) => (
              <li key={n.id}>
                <button type="button" className={`notice${n.read ? "" : " notice-unread"}`} onClick={() => open(n)}>
                  <span className="notice-dot" aria-hidden="true" />
                  <span className="notice-body">
                    <span className="notice-title">
                      {n.title}
                      {!n.read && <span className="sr-only"> (unread)</span>}
                    </span>
                    <span className="notice-message">{n.message}</span>
                  </span>
                  <span className="notice-date">{shortDate(n.dueDate)}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
