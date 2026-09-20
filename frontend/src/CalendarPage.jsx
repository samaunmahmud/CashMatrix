import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { calendarApi } from "./api";
import EventDialog from "./components/EventDialog";
import { useNotifications } from "./NotificationsContext";
import { addDays, isISODate, longDate, monthGrid, monthTitle, parseISO, shortDate, todayISO, WEEKDAYS } from "./dates";
import { formatMoney } from "./format";
import "./styles/calendar.css";

const TYPE_LABEL = { PAYMENT: "Payment", SUBSCRIPTION: "Subscription", TASK: "Task" };
const REPEAT_LABEL = { WEEKLY: "Repeats weekly", MONTHLY: "Repeats monthly", YEARLY: "Repeats yearly" };
const MAX_CHIPS = 3;

const viewOf = (iso) => {
  const d = parseISO(iso);
  return { year: d.getFullYear(), month: d.getMonth() };
};

export default function CalendarPage() {
  const today = todayISO();
  const [params] = useSearchParams();
  const requested = params.get("date");
  const startDate = isISODate(requested) ? requested : today;

  const [selected, setSelected] = useState(startDate);
  const [view, setView] = useState(() => viewOf(startDate));
  const [entries, setEntries] = useState([]);
  const [events, setEvents] = useState([]);
  const [upcoming, setUpcoming] = useState([]);
  const [status, setStatus] = useState("loading"); // loading | ready | error
  const [dialog, setDialog] = useState(null); // { event?, date }
  const [confirmingDelete, setConfirmingDelete] = useState(null);
  const [actionError, setActionError] = useState("");
  const { refreshUnread } = useNotifications();

  const grid = useMemo(() => monthGrid(view.year, view.month), [view]);

  // Bumping this reloads the calendar after a change (or a failed load).
  const [reloadKey, setReloadKey] = useState(0);
  const reload = () => setReloadKey((key) => key + 1);

  // The cleanup flag means only the latest request may write to state, so quickly
  // flicking between months can't let a slow earlier response overwrite a newer one.
  useEffect(() => {
    let stale = false;
    Promise.all([
      calendarApi.entries(grid[0], grid[grid.length - 1]),
      calendarApi.events(),
      calendarApi.entries(today, addDays(today, 30)),
    ])
      .then(([entriesRes, eventsRes, upcomingRes]) => {
        if (stale) return;
        setEntries(entriesRes.data);
        setEvents(eventsRes.data);
        setUpcoming(upcomingRes.data.filter((entry) => !entry.completed));
        setStatus("ready");
      })
      .catch(() => {
        if (!stale) setStatus("error");
      });
    return () => {
      stale = true;
    };
  }, [grid, today, reloadKey]);

  const entriesByDate = useMemo(() => {
    const map = new Map();
    for (const entry of entries) {
      if (!map.has(entry.date)) map.set(entry.date, []);
      map.get(entry.date).push(entry);
    }
    return map;
  }, [entries]);

  const eventsById = useMemo(() => new Map(events.map((e) => [e.id, e])), [events]);

  const upcomingTotal = upcoming.reduce((sum, entry) => sum + (entry.amount ?? 0), 0);
  const selectedEntries = entriesByDate.get(selected) ?? [];

  const goToDate = (iso) => {
    setSelected(iso);
    setView(viewOf(iso));
  };

  const changeMonth = (delta) => {
    const first = new Date(view.year, view.month + delta, 1);
    const inThatMonth = first.getFullYear() === parseISO(today).getFullYear() && first.getMonth() === parseISO(today).getMonth();
    const y = first.getFullYear();
    const m = String(first.getMonth() + 1).padStart(2, "0");
    goToDate(inThatMonth ? today : `${y}-${m}-01`);
  };

  const run = async (action) => {
    setActionError("");
    try {
      await action();
      reload();
      refreshUnread();
    } catch (err) {
      setActionError(err.response?.data?.error || "Something went wrong. Please try again.");
    }
  };

  const handleSaved = (saved, wasEdit) => {
    setDialog(null);
    if (!wasEdit) goToDate(saved.nextDueDate);
    reload();
    refreshUnread();
  };

  return (
    <div className="calendar-page">
      <div className="page-head">
        <div>
          <h1>Calendar</h1>
          <p className="muted">Payments, subscriptions and tasks, with a reminder before each one.</p>
        </div>
        <button type="button" className="btn" onClick={() => setDialog({ date: selected })}>
          + Add
        </button>
      </div>

      {status === "error" && (
        <div className="banner banner-error" role="alert">
          <span>We couldn't load your calendar.</span>
          <button type="button" className="link-button" onClick={reload}>Try again</button>
        </div>
      )}

      <div className="calendar-layout">
        <section className="card calendar-card" aria-label="Month view">
          <div className="calendar-toolbar">
            <button type="button" className="icon-btn" onClick={() => changeMonth(-1)} aria-label="Previous month">‹</button>
            <h2 className="calendar-title" aria-live="polite">{monthTitle(view.year, view.month)}</h2>
            <button type="button" className="icon-btn" onClick={() => changeMonth(1)} aria-label="Next month">›</button>
            <button type="button" className="btn btn-outline btn-sm" onClick={() => goToDate(today)}>Today</button>
          </div>

          <div className="calendar-grid" role="group" aria-label={monthTitle(view.year, view.month)}>
            {WEEKDAYS.map((day) => (
              <div key={day} className="weekday" aria-hidden="true">{day}</div>
            ))}
            {grid.map((iso) => {
              const dayEntries = entriesByDate.get(iso) ?? [];
              const inMonth = parseISO(iso).getMonth() === view.month;
              const classes = [
                "day",
                !inMonth && "day-outside",
                iso === today && "day-today",
                iso === selected && "day-selected",
              ].filter(Boolean).join(" ");
              const count = dayEntries.length;
              return (
                <button
                  key={iso}
                  type="button"
                  className={classes}
                  onClick={() => goToDate(iso)}
                  aria-pressed={iso === selected}
                  aria-current={iso === today ? "date" : undefined}
                  aria-label={`${longDate(iso)}${count ? `, ${count} ${count === 1 ? "item" : "items"}` : ""}`}
                >
                  <span className="day-num">{parseISO(iso).getDate()}</span>
                  <span className="day-chips">
                    {dayEntries.slice(0, MAX_CHIPS).map((entry) => (
                      <span
                        key={`${entry.eventId}-${entry.date}`}
                        className={`chip chip-${entry.type.toLowerCase()}${entry.completed ? " chip-done" : ""}`}
                      >
                        <span className="chip-text">{entry.title}</span>
                      </span>
                    ))}
                    {count > MAX_CHIPS && <span className="day-more">+{count - MAX_CHIPS} more</span>}
                  </span>
                </button>
              );
            })}
          </div>

          <ul className="legend" aria-label="Key">
            <li><span className="legend-dot chip-payment" /> Payment</li>
            <li><span className="legend-dot chip-subscription" /> Subscription</li>
            <li><span className="legend-dot chip-task" /> Task</li>
            <li><span className="legend-dot chip-done" /> Done</li>
          </ul>
        </section>

        <aside className="calendar-side">
          <section className="card" aria-labelledby="agenda-title">
            <div className="card-header">
              <h2 id="agenda-title">{longDate(selected)}</h2>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => setDialog({ date: selected })}>
                + Add
              </button>
            </div>

            {actionError && <p className="error-text" role="alert">{actionError}</p>}

            {selectedEntries.length === 0 ? (
              <p className="empty-state">Nothing planned for this day.</p>
            ) : (
              <ul className="agenda">
                {selectedEntries.map((entry) => {
                  const event = eventsById.get(entry.eventId);
                  const overdue = !entry.completed && entry.date < today;
                  const canComplete = !entry.completed && event?.nextDueDate === entry.date;
                  const confirming = confirmingDelete === entry.eventId;
                  return (
                    <li key={`${entry.eventId}-${entry.date}`} className="agenda-item">
                      <div className="agenda-top">
                        <strong className={entry.completed ? "struck" : undefined}>{entry.title}</strong>
                        {entry.amount != null && <span className="agenda-amount">{formatMoney(entry.amount)}</span>}
                      </div>
                      <div className="agenda-meta">
                        <span className={`badge badge-${entry.type.toLowerCase()}`}>{TYPE_LABEL[entry.type]}</span>
                        {REPEAT_LABEL[entry.recurrence] && <span className="muted">{REPEAT_LABEL[entry.recurrence]}</span>}
                        {entry.completed && <span className="badge badge-done">Done</span>}
                        {overdue && <span className="badge badge-danger">Overdue</span>}
                      </div>
                      {entry.description && <p className="agenda-notes">{entry.description}</p>}

                      {confirming ? (
                        <div className="agenda-confirm" role="alert">
                          <span>
                            Delete “{entry.title}”{entry.recurrence !== "NONE" ? " and all its repeats" : ""}?
                          </span>
                          <div className="agenda-actions">
                            <button
                              type="button"
                              className="btn btn-danger btn-sm"
                              onClick={() => run(async () => {
                                await calendarApi.remove(entry.eventId);
                                setConfirmingDelete(null);
                              })}
                            >
                              Delete
                            </button>
                            <button type="button" className="btn btn-outline btn-sm" onClick={() => setConfirmingDelete(null)}>
                              Keep
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="agenda-actions">
                          {canComplete && (
                            <button
                              type="button"
                              className="btn btn-sm"
                              onClick={() => run(() => calendarApi.complete(entry.eventId))}
                            >
                              {entry.type === "TASK" ? "Mark as done" : "Mark as paid"}
                            </button>
                          )}
                          <button
                            type="button"
                            className="btn btn-outline btn-sm"
                            onClick={() => event && setDialog({ event, date: selected })}
                          >
                            Edit
                          </button>
                          <button type="button" className="btn btn-outline btn-sm" onClick={() => setConfirmingDelete(entry.eventId)}>
                            Delete
                          </button>
                        </div>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          <section className="card" aria-labelledby="upcoming-title">
            <div className="card-header">
              <h2 id="upcoming-title">Coming up</h2>
              <span className="muted">Next 30 days</span>
            </div>
            {upcomingTotal > 0 && (
              <p className="upcoming-total">
                <strong>{formatMoney(upcomingTotal)}</strong> <span className="muted">to pay</span>
              </p>
            )}
            {upcoming.length === 0 ? (
              <p className="empty-state">
                {status === "loading" ? "Loading…" : "Nothing due in the next 30 days."}
              </p>
            ) : (
              <ul className="upcoming">
                {upcoming.slice(0, 6).map((entry) => (
                  <li key={`${entry.eventId}-${entry.date}`}>
                    <button type="button" className="upcoming-item" onClick={() => goToDate(entry.date)}>
                      <span className="upcoming-date">{shortDate(entry.date)}</span>
                      <span className="upcoming-title">{entry.title}</span>
                      {entry.amount != null && <span className="upcoming-amount">{formatMoney(entry.amount)}</span>}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>
      </div>

      {dialog && (
        <EventDialog
          event={dialog.event}
          defaultDate={dialog.date}
          onClose={() => setDialog(null)}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
}
