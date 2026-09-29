// A round badge with a merchant's initials. Kept neutral so the amounts, not the badges,
// are what catch the eye in a long statement.
function initialsOf(name) {
  const words = (name || "?").replace(/[^\p{L}\p{N} ]/gu, " ").trim().split(/\s+/);
  const letters = words.length > 1 ? words[0][0] + words[1][0] : words[0].slice(0, 2);
  return letters.toUpperCase();
}

export default function Avatar({ name, size = 40 }) {
  return (
    <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.34 }} aria-hidden="true">
      {initialsOf(name)}
    </span>
  );
}
