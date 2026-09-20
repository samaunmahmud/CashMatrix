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

export default api;
