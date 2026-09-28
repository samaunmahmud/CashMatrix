import { useEffect } from "react";

/** A short confirmation at the bottom of the screen that goes away by itself. */
export default function Toast({ message, onDone, duration = 4500 }) {
  useEffect(() => {
    if (!message) return undefined;
    const timer = setTimeout(onDone, duration);
    return () => clearTimeout(timer);
  }, [message, onDone, duration]);

  return (
    <div className="toast-region" role="status" aria-live="polite">
      {message && <div className="toast">{message}</div>}
    </div>
  );
}
