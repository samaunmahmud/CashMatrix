import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { authApi } from "./api";
import { useAuth } from "./AuthContext";
import { FingerprintIcon } from "./components/Icons";
import TigerLogo from "./components/TigerLogo";
import { passkeysSupported, signInWithPasskey, wasCancelled } from "./passkeys";
import "./styles/auth.css";

export default function LoginPage() {
  const [isSignup, setIsSignup] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

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
      <div className="auth-brand">
        <TigerLogo size={84} badge title="CashMatrix tiger logo" />
        <h1>CashMatrix</h1>
        <p>All your money, bills and dates in one place.</p>
      </div>

      <div className="card auth-card">
        <h2>{isSignup ? "Create your account" : "Log in"}</h2>

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
    </div>
  );
}
