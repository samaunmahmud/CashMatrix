import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { notificationApi } from "./api";

const NotificationsContext = createContext({ unread: 0, refreshUnread: () => {} });

const POLL_MS = 60_000;

/** Keeps the unread count for the bell fresh: on load, every minute, and when the tab regains focus. */
export function NotificationsProvider({ children }) {
  const [unread, setUnread] = useState(0);

  // On failure keep showing the last known count; the bell is not worth an error message.
  const refreshUnread = useCallback(
    () => notificationApi.unreadCount().then(({ data }) => setUnread(data.count)).catch(() => {}),
    []
  );

  useEffect(() => {
    let active = true;
    const check = () =>
      notificationApi
        .unreadCount()
        .then(({ data }) => {
          if (active) setUnread(data.count);
        })
        .catch(() => {});

    check();
    const timer = setInterval(check, POLL_MS);
    window.addEventListener("focus", check);
    return () => {
      active = false;
      clearInterval(timer);
      window.removeEventListener("focus", check);
    };
  }, []);

  const value = useMemo(() => ({ unread, refreshUnread }), [unread, refreshUnread]);
  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>;
}

// The hook lives beside its provider on purpose; splitting one tiny context across files adds nothing.
// eslint-disable-next-line react-refresh/only-export-components
export function useNotifications() {
  return useContext(NotificationsContext);
}
