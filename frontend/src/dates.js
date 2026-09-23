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

/** "14:05" for something updated today, otherwise "17 Sept, 14:05". */
export const updatedLabel = (isoInstant) => {
  const date = new Date(isoInstant);
  const sameDay = toISO(date) === todayISO();
  return new Intl.DateTimeFormat("en-GB", sameDay
    ? { hour: "2-digit", minute: "2-digit" }
    : { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }).format(date);
};

export const shortDate = (iso) =>
  new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short" }).format(parseISO(iso));

/** "3 Sept 2025". */
export const mediumDate = (iso) =>
  new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric" }).format(parseISO(iso));

// Months travel as "yyyy-MM" strings, matching the API.
export const monthISO = (date = new Date()) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;

export const addMonths = (month, count) => {
  const [year, m] = month.split("-").map(Number);
  return monthISO(new Date(year, m - 1 + count, 1));
};

/** "September 2026", or with short = true "Sep". */
export const monthLabel = (month, short = false) => {
  const [year, m] = month.split("-").map(Number);
  return new Intl.DateTimeFormat("en-GB", short ? { month: "short" } : { month: "long", year: "numeric" })
    .format(new Date(year, m - 1, 1));
};

/** "Today", "Yesterday", or "Monday 21 September" for a statement's day headings. */
export const dayHeading = (iso) => {
  const today = todayISO();
  if (iso === today) return "Today";
  if (iso === addDays(today, -1)) return "Yesterday";
  return new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long" }).format(parseISO(iso));
};
