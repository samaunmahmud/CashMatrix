// The colour theme is a per-device choice (a phone might follow the system while a laptop stays
// light), so it lives in this browser's storage rather than on the server. index.html applies it
// before the page draws, so there is no flash of the wrong theme.
const KEY = "cashmatrix:theme";

export const THEMES = [
  ["system", "Match device"],
  ["light", "Light"],
  ["dark", "Dark"],
];

export function storedTheme() {
  try {
    const value = localStorage.getItem(KEY);
    return THEMES.some(([name]) => name === value) ? value : "system";
  } catch {
    return "system";
  }
}

export function setTheme(theme) {
  try {
    if (theme === "system") localStorage.removeItem(KEY);
    else localStorage.setItem(KEY, theme);
  } catch {
    // Storage can be blocked (private browsing); the choice then lasts until the page is reloaded.
  }
  if (theme === "system") document.documentElement.removeAttribute("data-theme");
  else document.documentElement.setAttribute("data-theme", theme);
}
