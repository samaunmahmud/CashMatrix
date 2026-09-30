import { BADGE, CENTRE_STRIPE, FACE, FANG, HEAD, INK, MIRROR, MOUTH, NOSE, PAIRS, PAIRS_ON_FACE } from "../tigerShapes";

function Pairs({ shapes }) {
  return shapes.map(({ fill, stroke, d }) =>
    [undefined, MIRROR].map((transform) => (
      <path
        key={d + (transform ?? "")}
        d={d}
        fill={fill}
        stroke={stroke}
        strokeWidth={stroke ? 0.8 : undefined}
        strokeLinejoin="round"
        transform={transform}
      />
    ))
  );
}

/**
 * The CashMatrix tiger for small sizes (header, favicon, app icons): the login screen's
 * snarling crest cut down to the shapes that still read at 16 to 40 pixels. White face,
 * dark green stripes and amber eyes, so it sits on the brand green; with `badge` set it
 * brings its own green circle.
 *
 * The shapes live in tigerShapes.js, so the favicon and app icons (scripts/icons.mjs) match.
 */
export default function TigerLogo({ size = 36, badge = false, title = "CashMatrix" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" role="img" aria-label={title} xmlns="http://www.w3.org/2000/svg">
      {badge && <circle cx="32" cy="32" r="32" fill={BADGE} />}
      <g transform={badge ? "translate(32 32) scale(0.8) translate(-32 -32)" : undefined}>
        <Pairs shapes={PAIRS} />
        <path d={HEAD} fill={FACE} stroke={FACE} strokeWidth="1" strokeLinejoin="round" />
        <path d={CENTRE_STRIPE} fill={INK} />
        <Pairs shapes={PAIRS_ON_FACE} />
        <path d={NOSE} fill={INK} stroke={INK} strokeWidth="1.3" strokeLinejoin="round" />
        <path d={MOUTH} fill={INK} stroke={INK} strokeWidth="1" strokeLinejoin="round" />
        <Pairs shapes={[{ fill: FACE, d: FANG }]} />
      </g>
    </svg>
  );
}
