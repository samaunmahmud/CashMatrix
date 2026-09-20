import { createContext, useContext, useEffect, useState } from "react";
import { SESSION_EXPIRED_EVENT } from "./api";

const AuthContext = createContext(null);

const STORED_KEYS = ["token", "userEmail", "userFullName"];

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const email = localStorage.getItem("userEmail");
    const fullName = localStorage.getItem("userFullName");
    return email ? { email, fullName } : null;
  });
  // True after the server rejected the login, so the login screen can explain why they're back there.
  const [sessionExpired, setSessionExpired] = useState(false);

  useEffect(() => {
    const onExpired = () => {
      STORED_KEYS.forEach((key) => localStorage.removeItem(key));
      setUser(null);
      setSessionExpired(true);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  const login = (authResponse) => {
    localStorage.setItem("token", authResponse.token);
    localStorage.setItem("userEmail", authResponse.email);
    localStorage.setItem("userFullName", authResponse.fullName);
    setSessionExpired(false);
    setUser({ email: authResponse.email, fullName: authResponse.fullName });
  };

  const logout = () => {
    STORED_KEYS.forEach((key) => localStorage.removeItem(key));
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, sessionExpired }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext);
}
