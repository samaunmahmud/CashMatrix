import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { settingsApi } from "./api";
import { useAuth } from "./AuthContext";
import { currentPushSubscription, disablePush, enablePush, pushSupported } from "./push";
import "./styles/settings.css";

const TEST_WORDS = {
  email: {
    SENT: "Email sent. Check your inbox.",
    OFF: "Email reminders are switched off.",
    UNAVAILABLE: "Email isn't set up on the server yet.",
    FAILED: "Couldn't send the email. Check the server's mail settings.",
  },
  push: {
    SENT: "Notification sent to your devices.",
    NO_DEVICES: "No device is registered for notifications yet.",
    UNAVAILABLE: "Notifications aren't set up on the server yet.",
    FAILED: "Couldn't reach your device. It may be offline, so try again shortly.",
  },
};

function Switch({ checked, onChange, disabled, labelledBy }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-labelledby={labelledBy}
      className={`switch${checked ? " switch-on" : ""}`}
      disabled={disabled}
      onClick={() => onChange(!checked)}
    >
      <span className="switch-knob" />
    </button>
  );
}

export default function SettingsPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [settings, setSettings] = useState(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [thisDevice, setThisDevice] = useState(null); // null = still checking
  const [busy, setBusy] = useState("");
  const [message, setMessage] = useState({ kind: "", text: "" });
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    settingsApi.get()
      .then((res) => {
        if (!active) return;
        setSettings(res.data);
        setLoadFailed(false);
      })
      .catch(() => active && setLoadFailed(true));
    currentPushSubscription()
      .then((subscription) => active && setThisDevice(Boolean(subscription)))
      .catch(() => active && setThisDevice(false));
    return () => {
      active = false;
    };
  }, [reloadKey]);

  const reload = () => setReloadKey((key) => key + 1);

  const run = async (name, action) => {
    setBusy(name);
    setMessage({ kind: "", text: "" });
    try {
      await action();
      reload();
    } catch (err) {
      setMessage({ kind: "error", text: err.response?.data?.error || err.message || "Something went wrong." });
    } finally {
      setBusy("");
    }
  };

  const sendTest = () =>
    run("test", async () => {
      const { data } = await settingsApi.sendTest();
      const lines = [TEST_WORDS.email[data.email], TEST_WORDS.push[data.push]];
      const anySent = data.email === "SENT" || data.push === "SENT";
      setMessage({ kind: anySent ? "ok" : "info", text: lines.join(" ") });
    });

  const handleLogout = () => {
    logout();
    navigate("/");
  };

  const pushBlocked = pushSupported() && Notification.permission === "denied";

  return (
    <div className="settings-page">
      <div className="page-head">
        <div>
          <h1>Settings</h1>
          <p className="muted">Choose how CashMatrix reminds you before payments are due.</p>
        </div>
      </div>

      {loadFailed && (
        <div className="banner banner-error" role="alert">
          <span>We couldn't load your settings.</span>
          <button type="button" className="link-button" onClick={reload}>Try again</button>
        </div>
      )}

      <section className="card settings-card" aria-labelledby="reminders-title">
        <h2 id="reminders-title">Reminders</h2>

        <div className="setting-row">
          <div className="setting-text">
            <h3 id="in-app-label">Alerts in the app</h3>
            <p className="muted">Always on. They appear under Alerts and on the bell.</p>
          </div>
          <Switch checked disabled labelledBy="in-app-label" onChange={() => {}} />
        </div>

        <div className="setting-row">
          <div className="setting-text">
            <h3 id="email-label">Email</h3>
            <p className="muted">
              {settings && !settings.emailAvailable
                ? "Not available yet: the server has no email service set up."
                : `Sent to ${user?.email}.`}
            </p>
          </div>
          <Switch
            checked={Boolean(settings?.emailEnabled)}
            disabled={!settings || !settings.emailAvailable || busy === "email"}
            labelledBy="email-label"
            onChange={(enabled) => run("email", () => settingsApi.setEmail(enabled))}
          />
        </div>

        <div className="setting-row">
          <div className="setting-text">
            <h3 id="push-label">Notifications on this device</h3>
            <p className="muted">
              {!pushSupported()
                ? "This browser can't show notifications. On an iPhone, add CashMatrix to your Home Screen first."
                : settings && !settings.pushAvailable
                  ? "Not available yet: the server has no notification keys set up."
                  : pushBlocked
                    ? "Blocked. Allow notifications for this site in your browser settings."
                    : thisDevice
                      ? "On for this device."
                      : "Get a pop-up on your phone or computer, even when CashMatrix is closed."}
              {settings?.pushDevices > 0 && ` ${settings.pushDevices} device${settings.pushDevices > 1 ? "s" : ""} registered.`}
            </p>
          </div>
          <Switch
            checked={Boolean(thisDevice)}
            disabled={!pushSupported() || !settings?.pushAvailable || pushBlocked || thisDevice === null || busy === "push"}
            labelledBy="push-label"
            onChange={(enable) =>
              run("push", () => (enable ? enablePush(settings.pushPublicKey) : disablePush()))
            }
          />
        </div>

        <div className="settings-test">
          <button
            type="button"
            className="btn btn-outline btn-sm"
            onClick={sendTest}
            disabled={busy === "test" || !settings}
          >
            {busy === "test" ? "Sending…" : "Send a test reminder"}
          </button>
          {message.text && (
            <p className={message.kind === "error" ? "error-text" : "settings-result"} role="status">
              {message.text}
            </p>
          )}
        </div>
      </section>

      <section className="card settings-card" aria-labelledby="account-title">
        <h2 id="account-title">Your account</h2>
        <dl className="account-details">
          <div><dt>Name</dt><dd>{user?.fullName}</dd></div>
          <div><dt>Email</dt><dd>{user?.email}</dd></div>
        </dl>
        <button type="button" className="btn btn-outline" onClick={handleLogout}>Log out</button>
      </section>
    </div>
  );
}
