import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { passkeyApi, settingsApi } from "./api";
import { useAuth } from "./AuthContext";
import { FingerprintIcon, TrashIcon } from "./components/Icons";
import { formatMoney } from "./format";
import { createPasskey, passkeysSupported, wasCancelled } from "./passkeys";
import { currentPushSubscription, disablePush, enablePush, pushSupported } from "./push";
import "./styles/settings.css";

const ALERT_MINIMUMS = [0, 10, 25, 50, 100, 250];

/** A name for a new passkey the user will recognise later, from the device it was made on. */
function deviceName() {
  const ua = navigator.userAgent;
  if (/iPhone/.test(ua)) return "iPhone";
  if (/iPad/.test(ua)) return "iPad";
  if (/Android/.test(ua)) return "Android phone";
  if (/Mac/.test(ua)) return "Mac";
  if (/Windows/.test(ua)) return "Windows computer";
  return "";
}

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
  // Shown in the card of whatever produced it ("reminders" or "passkeys").
  const [message, setMessage] = useState({ kind: "", text: "", card: "" });
  const [reloadKey, setReloadKey] = useState(0);
  const [passkeys, setPasskeys] = useState(null);

  useEffect(() => {
    let active = true;
    settingsApi.get()
      .then((res) => {
        if (!active) return;
        setSettings(res.data);
        setLoadFailed(false);
      })
      .catch(() => active && setLoadFailed(true));
    passkeyApi.list()
      .then((res) => active && setPasskeys(res.data))
      .catch(() => active && setPasskeys([]));
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
    setMessage({ kind: "", text: "", card: "" });
    try {
      await action();
      reload();
    } catch (err) {
      setMessage({
        kind: "error",
        text: err.response?.data?.error || err.message || "Something went wrong.",
        card: name === "passkey" ? "passkeys" : "reminders",
      });
    } finally {
      setBusy("");
    }
  };

  const addPasskey = () =>
    run("passkey", async () => {
      try {
        const { data } = await passkeyApi.start();
        const credential = await createPasskey(data.options);
        await passkeyApi.finish(data.requestId, credential, deviceName());
        setMessage({ kind: "ok", text: "Passkey added. Next time, log in with your fingerprint or face.", card: "passkeys" });
      } catch (err) {
        if (wasCancelled(err)) return;
        if (err?.name === "InvalidStateError") throw new Error("This device already has a passkey for your account.", { cause: err });
        throw err;
      }
    });

  const removePasskey = (passkey) => {
    if (!window.confirm(`Remove the passkey "${passkey.name}"? You can still log in with your password.`)) return;
    run("passkey", () => passkeyApi.remove(passkey.id));
  };

  const sendTest = () =>
    run("test", async () => {
      const { data } = await settingsApi.sendTest();
      const lines = [TEST_WORDS.email[data.email], TEST_WORDS.push[data.push]];
      const anySent = data.email === "SENT" || data.push === "SENT";
      setMessage({ kind: anySent ? "ok" : "info", text: lines.join(" "), card: "reminders" });
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

        <div className="setting-row">
          <div className="setting-text">
            <h3 id="tx-alerts-label">Money in and out</h3>
            <p className="muted">An alert for each new payment or deposit, after your bank syncs.</p>
            {settings?.transactionAlertsEnabled && (
              <label className="setting-inline">
                <span>For amounts of</span>
                <select
                  className="input input-sm"
                  value={Number(settings.transactionAlertMinimum)}
                  disabled={busy === "tx-alerts"}
                  onChange={(e) =>
                    run("tx-alerts", () => settingsApi.setTransactionAlerts({ transactionAlertMinimum: Number(e.target.value) }))
                  }
                >
                  {[...new Set([...ALERT_MINIMUMS, Number(settings.transactionAlertMinimum)])]
                    .sort((a, b) => a - b)
                    .map((amount) => (
                      <option key={amount} value={amount}>
                        {amount === 0 ? "any amount" : `${formatMoney(amount).replace(/\.00$/, "")} or more`}
                      </option>
                    ))}
                </select>
              </label>
            )}
          </div>
          <Switch
            checked={Boolean(settings?.transactionAlertsEnabled)}
            disabled={!settings || busy === "tx-alerts"}
            labelledBy="tx-alerts-label"
            onChange={(enabled) => run("tx-alerts", () => settingsApi.setTransactionAlerts({ transactionAlertsEnabled: enabled }))}
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
          {message.card === "reminders" && (
            <p className={message.kind === "error" ? "error-text" : "settings-result"} role="status">
              {message.text}
            </p>
          )}
        </div>
      </section>

      <section className="card settings-card" aria-labelledby="passkeys-title">
        <h2 id="passkeys-title">Log in with fingerprint or face</h2>
        <p className="muted settings-intro">
          A passkey lets you log in with your device's fingerprint, face or screen lock instead of your password.
          It stays on your device, and CashMatrix never sees your fingerprint or face.
        </p>

        {passkeys?.length > 0 && (
          <ul className="passkey-list">
            {passkeys.map((passkey) => (
              <li key={passkey.id}>
                <span className="passkey-icon" aria-hidden="true"><FingerprintIcon size={20} /></span>
                <span className="passkey-text">
                  <strong>{passkey.name}</strong>
                  <span className="muted">
                    Added {new Date(passkey.createdAt).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" })}
                    {passkey.lastUsedAt &&
                      ` · last used ${new Date(passkey.lastUsedAt).toLocaleDateString("en-GB", { day: "numeric", month: "short" })}`}
                  </span>
                </span>
                <button
                  type="button"
                  className="icon-btn icon-btn-danger"
                  onClick={() => removePasskey(passkey)}
                  disabled={busy === "passkey"}
                  aria-label={`Remove passkey ${passkey.name}`}
                >
                  <TrashIcon size={18} />
                </button>
              </li>
            ))}
          </ul>
        )}

        {passkeysSupported() ? (
          <button type="button" className="btn btn-outline" onClick={addPasskey} disabled={busy === "passkey" || passkeys === null}>
            <FingerprintIcon size={20} /> {busy === "passkey" ? "Waiting for your device…" : "Add a passkey on this device"}
          </button>
        ) : (
          <p className="muted">This browser doesn't support passkeys.</p>
        )}
        {message.card === "passkeys" && (
          <p className={message.kind === "error" ? "error-text" : "settings-result"} role="status">
            {message.text}
          </p>
        )}
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
