import { useState } from "react";
import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { authApi } from "./api";
import { useAuth } from "./AuthContext";
import { CheckIcon, FingerprintIcon } from "./components/Icons";
import TigerCrest from "./components/TigerCrest";
import TigerLogo from "./components/TigerLogo";
import { passkeysSupported, signInWithPasskey, wasCancelled } from "./passkeys";
import "./styles/auth.css";

export default function LoginPage() {
  const [searchParams] = useSearchParams();
  // The demo's "Create your account" link lands here with the sign-up form already showing.
  const [isSignup, setIsSignup] = useState(searchParams.has("signup"));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [openingDemo, setOpeningDemo] = useState(false);

  const { user, login, sessionExpired } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const navigate = useNavigate();

  if (user) return <Navigate to="/dashboard" replace />;

  const loginWithPasskey = async () => {
    setError("");
    setLoading(true);
    try {
      const { data } = await authApi.passkeyStart();
      const credential = await signInWithPasskey(data.options);
      const response = await authApi.passkeyFinish(data.requestId, credential);
      login(response.data);
      navigate("/dashboard");
    } catch (err) {
      if (!wasCancelled(err)) {
        setError(err.response?.data?.error || "Couldn't log in with a passkey. Please use your password.");
      }
    } finally {
      setLoading(false);
    }
  };

  const openDemo = async () => {
    setError("");
    setLoading(true);
    setOpeningDemo(true);
    try {
      const response = await authApi.demo();
      login(response.data);
      navigate("/dashboard");
    } catch (err) {
      setError(err.response?.data?.error || "Couldn't open the demo just now. Please try again.");
    } finally {
      setLoading(false);
      setOpeningDemo(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const response = isSignup
        ? await authApi.signup({ email, password, fullName })
        : await authApi.login({ email, password });

      login(response.data);
      navigate("/dashboard");
    } catch (err) {
      const data = err.response?.data;
      // Validation failures come back as { field: message }, other failures as { error: message }.
      const message =
        data?.error ||
        (data && typeof data === "object" ? Object.values(data).join(" ") : "") ||
        "Something went wrong. Please try again.";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth">
      <aside className="auth-aside">
        <div className="auth-brand">
          <TigerLogo size={32} />
          <span>CashMatrix</span>
        </div>
        <div className="auth-pitch">
          <div className="auth-crest">
            <TigerCrest size={240} title="CashMatrix tiger logo" />
          </div>
          <h1>All your money, bills and dates in one place.</h1>
          <ul className="auth-points">
            <li><CheckIcon size={18} /> See every account and card balance together</li>
            <li><CheckIcon size={18} /> Budgets, savings goals and a monthly summary</li>
            <li><CheckIcon size={18} /> Reminders before bills and subscriptions are due</li>
          </ul>
        </div>
        <p className="auth-foot">Read-only access through Plaid. CashMatrix never moves your money.</p>
      </aside>

      <main className="auth-main">
        <div className="auth-card">
          <h2>{isSignup ? "Create your account" : "Welcome back"}</h2>
          <p className="auth-sub muted">
            {isSignup ? "It takes less than a minute." : "Log in to see your balances and what's coming up."}
          </p>

          {sessionExpired && (
            <p className="auth-notice" role="status">Your session has ended. Please log in again.</p>
          )}

          <form onSubmit={handleSubmit} className="auth-form">
            {isSignup && (
              <label className="field">
                <span>Full name</span>
                <input
                  className="input"
                  type="text"
                  autoComplete="name"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  required
                />
              </label>
            )}
            <label className="field">
              <span>Email</span>
              <input
                className="input"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </label>
            <label className="field">
              <span>Password</span>
              <div className="password-field">
                <input
                  className="input"
                  type={showPassword ? "text" : "password"}
                  autoComplete={isSignup ? "new-password" : "current-password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-pressed={showPassword}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
              {isSignup && <span className="field-hint">At least 8 characters.</span>}
            </label>

            {error && <p className="error-text" role="alert">{error}</p>}

            <button type="submit" disabled={loading} className="btn">
              {loading ? "Please wait…" : isSignup ? "Sign up" : "Log in"}
            </button>
          </form>

          {!isSignup && passkeysSupported() && (
            <>
              <p className="auth-or"><span>or</span></p>
              <button type="button" className="btn btn-outline auth-passkey" onClick={loginWithPasskey} disabled={loading}>
                <FingerprintIcon size={20} /> Log in with fingerprint or face
              </button>
            </>
          )}

          <div className="auth-demo">
            <button type="button" className="btn btn-outline auth-demo-button" onClick={openDemo} disabled={loading}>
              {openingDemo ? "Opening the demo…" : "Try the demo"}
            </button>
            <p className="auth-demo-hint muted" role="status">
              {openingDemo
                ? "This can take up to a minute if the server has been asleep."
                : "No sign-up needed. Look around an example account with made-up data."}
            </p>
          </div>

          <p className="auth-toggle">
            {isSignup ? "Already have an account?" : "New to CashMatrix?"}{" "}
            <button
              type="button"
              className="link-button"
              onClick={() => {
                setIsSignup(!isSignup);
                setError("");
              }}
            >
              {isSignup ? "Log in" : "Sign up"}
            </button>
          </p>
        </div>
      </main>
    </div>
  );
}
