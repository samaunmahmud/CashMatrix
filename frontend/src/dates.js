// Calendar dates travel as "yyyy-MM-dd" strings, matching the API. Dates are built
// from year/month/day parts and never through toISOString(), which would shift them
// by a day for anyone west or east of UTC.

const pad = (n) => String(n).padStart(2, "0");

export const toISO = (date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

export const parseISO = (iso) => {
  const [year, month, day] = iso.split("-").map(Number);
  return new Date(year, month - 1, day);
};

export const isISODate = (value) => /^\d{4}-\d{2}-\d{2}$/.test(value ?? "");

export const todayISO = () => toISO(new Date());

export const addDays = (iso, days) => {
  const date = parseISO(iso);
  date.setDate(date.getDate() + days);
  return toISO(date);
};

/** 42 consecutive days (six Monday-first weeks) covering the given month. */
export function monthGrid(year, month) {
  const first = new Date(year, month, 1);
  const offset = (first.getDay() + 6) % 7; // Monday = 0
  return Array.from({ length: 42 }, (_, i) => toISO(new Date(year, month, 1 - offset + i)));
}

export const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

export const monthTitle = (year, month) =>
  new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" }).format(new Date(year, month, 1));

export const longDate = (iso) =>
  new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long", year: "numeric" }).format(parseISO(iso));

export const shortDate = (iso) =>
  new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short" }).format(parseISO(iso));
