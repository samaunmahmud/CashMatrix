import axios from "axios";

const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

const api = axios.create({
  baseURL: API_BASE_URL,
});

// Attach the JWT to every outgoing request automatically, if we have one
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const authApi = {
  signup: (data) => api.post("/auth/signup", data),
  login: (data) => api.post("/auth/login", data),
};

export const plaidApi = {
  createLinkToken: () => api.post("/plaid/link-token"),
  exchangeToken: (publicToken) =>
    api.post("/plaid/exchange-token", { public_token: publicToken }),
};

export default api;
