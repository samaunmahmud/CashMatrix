import axios from "axios";

const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

// Fired when the server says the login is no longer valid, so the app can send the user back to log in.
export const SESSION_EXPIRED_EVENT = "cashmatrix:session-expired";

const api = axios.create({
  baseURL: API_BASE_URL,
});

// Login and signup are how you get a token, so a leftover one must never ride along with them.
const isAuthCall = (config) => (config?.url ?? "").startsWith("/auth/");

// Attach the JWT to every outgoing request automatically, if we have one
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token && !isAuthCall(config)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !isAuthCall(error.config)) {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  signup: (data) => api.post("/auth/signup", data),
  login: (data) => api.post("/auth/login", data),
};

export const plaidApi = {
  createLinkToken: () => api.post("/plaid/link-token"),
  exchangeToken: (publicToken) =>
    api.post("/plaid/exchange-token", { public_token: publicToken }),
};

export const accountApi = {
  list: () => api.get("/accounts"),
};

export const transactionApi = {
  list: () => api.get("/transactions"),
  sync: () => api.post("/transactions/sync"),
};

export const calendarApi = {
  entries: (from, to) => api.get("/calendar", { params: { from, to } }),
  events: () => api.get("/calendar/events"),
  create: (data) => api.post("/calendar/events", data),
  update: (id, data) => api.put(`/calendar/events/${id}`, data),
  complete: (id) => api.post(`/calendar/events/${id}/complete`),
  remove: (id) => api.delete(`/calendar/events/${id}`),
};

export const notificationApi = {
  list: () => api.get("/notifications"),
  unreadCount: () => api.get("/notifications/unread-count"),
  markRead: (id) => api.post(`/notifications/${id}/read`),
  markAllRead: () => api.post("/notifications/read-all"),
};

export const subscriptionApi = {
  suggestions: () => api.get("/subscriptions/suggestions"),
  accept: (key) => api.post(`/subscriptions/suggestions/${key}/accept`),
  dismiss: (key) => api.post(`/subscriptions/suggestions/${key}/dismiss`),
};

export const budgetApi = {
  overview: (month) => api.get("/budgets", { params: month ? { month } : {} }),
  create: (data) => api.post("/budgets", data),
  update: (id, data) => api.put(`/budgets/${id}`, data),
  remove: (id) => api.delete(`/budgets/${id}`),
};

export const insightsApi = {
  get: (months = 6) => api.get("/insights", { params: { months } }),
};

export const settingsApi = {
  get: () => api.get("/settings/notifications"),
  setEmail: (emailEnabled) => api.put("/settings/notifications", { emailEnabled }),
  sendTest: () => api.post("/settings/notifications/test"),
  subscribePush: (subscription) => api.post("/push/subscribe", subscription),
  unsubscribePush: (endpoint) => api.post("/push/unsubscribe", { endpoint }),
};

export default api;
