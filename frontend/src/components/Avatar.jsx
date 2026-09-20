// A round badge with a merchant's initials, tinted by a colour picked from its name so the same
// merchant always looks the same.
const TINTS = [
  ["#e3f1ec", "#024731"],
  ["#efe9fb", "#5b3f9e"],
  ["#fff3dc", "#8a5300"],
  ["#e5f0fb", "#1b5a96"],
  ["#fbe9e7", "#b3261e"],
  ["#eaf3e0", "#3d6b14"],
];

function initialsOf(name) {
  const words = (name || "?").replace(/[^\p{L}\p{N} ]/gu, " ").trim().split(/\s+/);
  const letters = words.length > 1 ? words[0][0] + words[1][0] : words[0].slice(0, 2);
  return letters.toUpperCase();
}

export default function Avatar({ name, size = 40 }) {
  let hash = 0;
  for (const char of name || "") hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  const [background, color] = TINTS[hash % TINTS.length];

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
