// Branch names like "Dishoom Kings Cross" can't be recognised on their own, but once the plain
// "Dishoom" turns up too, the longer one is clearly a branch of it. Only whole leading words count,
// so "British Gas" and "British Airways" stay apart unless there is a retailer called just "British".

const words = (name) =>
  name
    .toLowerCase()
    .replace(/[’']/g, "")
    .split(/[^a-z0-9]+/)
    .filter(Boolean);

/** Maps each retailer name to the name it is grouped under: the shortest name that starts it. */
export function retailerGroups(names) {
  const unique = [...new Set(names)].sort((a, b) => words(a).length - words(b).length || a.localeCompare(b));
  const groups = new Map();
  const heads = [];
  for (const name of unique) {
    const own = words(name);
    const head = heads.find(({ key }) => key.length > 0 && key.every((word, i) => own[i] === word));
    if (head) {
      groups.set(name, head.name);
    } else {
      groups.set(name, name);
      heads.push({ name, key: own });
    }
  }
  return groups;
}
