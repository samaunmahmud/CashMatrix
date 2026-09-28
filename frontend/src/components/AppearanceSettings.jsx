import { useState } from "react";
import { THEMES, setTheme, storedTheme } from "../theme";

export default function AppearanceSettings() {
  const [theme, setChoice] = useState(storedTheme);

  return (
    <section className="card settings-card" aria-labelledby="appearance-title">
      <h2 id="appearance-title">Appearance</h2>
      <p className="muted settings-intro">Dark mode is easier on the eyes at night. This is saved on this device only.</p>
      <fieldset className="segmented theme-picker">
        <legend className="sr-only">Theme</legend>
        <div className="segmented-options">
          {THEMES.map(([value, label]) => (
            <label key={value}>
              <input
                type="radio"
                name="theme"
                value={value}
                checked={theme === value}
                onChange={() => {
                  setChoice(value);
                  setTheme(value);
                }}
              />
              <span>{label}</span>
            </label>
          ))}
        </div>
      </fieldset>
    </section>
  );
}
