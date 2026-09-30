// The small CashMatrix tiger's shapes, shared by the TigerLogo component and scripts/icons.mjs,
// which draws public/favicon.svg and the app icons from them.

export const FACE = "#ffffff";
export const INK = "#022e21";
export const EYE = "#f2b705";
export const BADGE = "#006a4d";

// In a 64 x 64 box. Each paired shape is the right-hand half; the left is its mirror.
export const HEAD = "M32 11L42 13L51 20L55 29L61 35.5L54 38L58 46L48 48L41 55L32 59L23 55L16 48L6 46L10 38L3 35.5L9 29L13 20L22 13Z";
export const PAIRS = [
  { fill: FACE, d: "M41 15L55 4L53 23Z" }, // ear
  { fill: INK, d: "M44 16.5L52 9.5L51.5 19.5Z" }, // inner ear
];
export const PAIRS_ON_FACE = [
  { fill: INK, d: "M37.4 13.1L41.3 14.4L35.5 22Z" }, // forehead stripe
  { fill: INK, d: "M55.7 31.7L46 33.9L56.3 35.2Z" }, // cheek stripes
  { fill: INK, d: "M54.7 40.3L45.4 41L52.8 44.5Z" },
  { fill: INK, d: "M33.3 25.3L48.6 21.4L47 25.6L34.6 28.5Z" }, // brow
  { fill: EYE, stroke: INK, d: "M35.5 29.8L46.7 26.6L43.8 31.7Q39 33.3 35.5 29.8Z" }, // eye
  { fill: INK, d: "M40.6 28Q41.8 29.9 40.6 31.9Q39.4 29.9 40.6 28Z" }, // slit pupil
];
export const CENTRE_STRIPE = "M32 12.5L35 17L32 25L29 17Z";
export const NOSE = "M27.2 35.8H36.8L32 41Z";
export const MOUTH = "M22.4 44.2Q27.5 40.6 32 42.9Q36.5 40.6 41.6 44.2L39.4 51.5Q32 55.7 24.6 51.5Z";
export const FANG = "M39.4 43.8L34.2 42.9L36.8 52.8Z";

export const MIRROR = "translate(64 0) scale(-1 1)";
