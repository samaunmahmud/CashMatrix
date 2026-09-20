import { createContext, useContext, useState } from "react";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const email = localStorage.getItem("userEmail");
    const fullName = localStorage.getItem("userFullName");
    return email ? { email, fullName } : null;
  });

  const login = (authResponse) => {
    localStorage.setItem("token", authResponse.token);
    localStorage.setItem("userEmail", authResponse.email);
    localStorage.setItem("userFullName", authResponse.fullName);
    setUser({ email: authResponse.email, fullName: authResponse.fullName });
  };

  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("userEmail");
    localStorage.removeItem("userFullName");
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
