// Draws the favicon and the app icons from the tiger in src/tigerShapes.js:  npm run icons
import { writeFileSync } from "node:fs";
import { Resvg } from "@resvg/resvg-js";
import { BADGE, CENTRE_STRIPE, FACE, FANG, HEAD, INK, MIRROR, MOUTH, NOSE, PAIRS, PAIRS_ON_FACE } from "../src/tigerShapes.js";

const pairs = (shapes) =>
  shapes
    .flatMap(({ fill, stroke, d }) =>
      ["", ` transform="${MIRROR}"`].map(
        (transform) =>
          `<path d="${d}" fill="${fill}"${stroke ? ` stroke="${stroke}" stroke-width="0.8"` : ""} stroke-linejoin="round"${transform}/>`
      )
    )
    .join("");

const tiger = [
  pairs(PAIRS),
  `<path d="${HEAD}" fill="${FACE}" stroke="${FACE}" stroke-width="1" stroke-linejoin="round"/>`,
  `<path d="${CENTRE_STRIPE}" fill="${INK}"/>`,
  pairs(PAIRS_ON_FACE),
  `<path d="${NOSE}" fill="${INK}" stroke="${INK}" stroke-width="1.3" stroke-linejoin="round"/>`,
  `<path d="${MOUTH}" fill="${INK}" stroke="${INK}" stroke-width="1" stroke-linejoin="round"/>`,
  pairs([{ fill: FACE, d: FANG }]),
].join("");

/**
 * A green circle for browser tabs and ordinary app icons. Home Screen icons get a full green square
 * instead, since iOS and Android cut their own shape out of it. Android may cut a circle, so its
 * tiger stays inside the middle 80% that every mask leaves alone; iOS rounds the corners only.
 */
function icon(shape) {
  const background =
    shape === "circle" ? `<circle cx="32" cy="32" r="32" fill="${BADGE}"/>` : `<rect width="64" height="64" fill="${BADGE}"/>`;
  const scale = { circle: 0.8, maskable: 0.66, apple: 0.74 }[shape];
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">${background}<g transform="translate(32 32) scale(${scale}) translate(-32 -32)">${tiger}</g></svg>\n`;
}

const png = (svg, size) => new Resvg(svg, { fitTo: { mode: "width", value: size } }).render().asPng();

const publicDir = new URL("../public/", import.meta.url);
writeFileSync(new URL("favicon.svg", publicDir), icon("circle"));
writeFileSync(new URL("icon-192.png", publicDir), png(icon("circle"), 192));
writeFileSync(new URL("icon-512.png", publicDir), png(icon("circle"), 512));
writeFileSync(new URL("icon-maskable-512.png", publicDir), png(icon("maskable"), 512));
writeFileSync(new URL("apple-touch-icon.png", publicDir), png(icon("apple"), 180));
console.log("Wrote favicon.svg, icon-192.png, icon-512.png, icon-maskable-512.png and apple-touch-icon.png");
