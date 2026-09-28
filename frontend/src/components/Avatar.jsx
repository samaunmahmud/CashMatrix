// A round badge with a merchant's initials, tinted by a colour picked from its name so the same
// merchant always looks the same.
// The pairs are theme tokens (--tint-N-bg / --tint-N-fg) so they suit light and dark.
const TINT_COUNT = 6;

function initialsOf(name) {
  const words = (name || "?").replace(/[^\p{L}\p{N} ]/gu, " ").trim().split(/\s+/);
  const letters = words.length > 1 ? words[0][0] + words[1][0] : words[0].slice(0, 2);
  return letters.toUpperCase();
}

export default function Avatar({ name, size = 40 }) {
  let hash = 0;
  for (const char of name || "") hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  const tint = hash % TINT_COUNT;
  const background = `var(--tint-${tint}-bg)`;
  const color = `var(--tint-${tint}-fg)`;

  return (
    <span
      className="avatar"
      style={{ width: size, height: size, background, color, fontSize: size * 0.36 }}
      aria-hidden="true"
    >
      {initialsOf(name)}
    </span>
  );
}
