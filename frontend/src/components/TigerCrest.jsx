/**
 * The snarling CashMatrix tiger, drawn for large sizes (the login screen).
 * White face with dark green stripes, so it sits on the brand green; amber eyes.
 * Every shape is drawn once on the right and mirrored, so the face stays symmetrical.
 */
const FACE = "#ffffff";
const INK = "#022e21";
const EYE = "#f2b705";

// Right-hand half of each paired shape, in a 200 x 200 box. The left half is its mirror.
const PAIRED = [
  { fill: FACE, d: "M128 46L170 12L166 70Z" }, // ear
  { fill: INK, d: "M138 50L163 28L161 60Z" }, // inner ear
];
const PAIRED_ON_FACE = [
  { fill: INK, d: "M117 41L129 45L111 69Z" }, // forehead stripes
  { fill: INK, d: "M140 54L148 63L122 73Z" },
  { fill: INK, d: "M174 99L144 106L176 110Z" }, // cheek stripes
  { fill: INK, d: "M171 126L142 128L165 139Z" },
  { fill: INK, d: "M158 70L166 84L150 80Z" },
  { fill: INK, d: "M104 79L152 67L147 80L108 89Z" }, // brow
  { fill: EYE, stroke: INK, d: "M111 93L146 83L137 99Q122 104 111 93Z" }, // eye
  { fill: INK, d: "M126.5 87.5Q130 93 126.5 99.5Q123 93 126.5 87.5Z" }, // slit pupil
  { fill: FACE, stroke: INK, d: "M123 137L107 134L115 165Z" }, // upper fang
  { fill: FACE, stroke: INK, d: "M117 163L106 166L110 151Z" }, // lower fang
];

const mirror = "translate(200 0) scale(-1 1)";

function Pair({ shapes }) {
  return shapes.map(({ fill, stroke, d }) => (
    <g key={d}>
      <path d={d} fill={fill} stroke={stroke} strokeWidth={stroke ? 2 : undefined} strokeLinejoin="round" />
      <path d={d} fill={fill} stroke={stroke} strokeWidth={stroke ? 2 : undefined} strokeLinejoin="round" transform={mirror} />
    </g>
  ));
}

export default function TigerCrest({ size = 200, title = "CashMatrix tiger" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 200 200" role="img" aria-label={title} xmlns="http://www.w3.org/2000/svg">
      <Pair shapes={PAIRED} />

      {/* head, with fur spiking out at the cheeks */}
      <path
        fill={FACE}
        stroke={FACE}
        strokeWidth="3"
        strokeLinejoin="round"
        d="M100 34L132 41L160 62L171 91L190 111L168 120L181 145L151 149L128 173L100 184L72 173L49 149L19 145L32 120L10 111L29 91L40 62L68 41Z"
      />

      {/* centre stripe */}
      <path fill={INK} d="M100 38L109 52L100 76L91 52Z" />

      <Pair shapes={PAIRED_ON_FACE.slice(0, 8)} />

      {/* nose */}
      <path fill={INK} stroke={INK} strokeWidth="4" strokeLinejoin="round" d="M85 112H115L100 128Z" />
      <path d="M100 128V135" stroke={INK} strokeWidth="4" strokeLinecap="round" />

      {/* whisker spots */}
      {[[80, 124], [73, 131], [120, 124], [127, 131]].map(([cx, cy]) => (
        <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="2.4" fill={INK} />
      ))}

      {/* open, snarling mouth */}
      <path fill={INK} stroke={INK} strokeWidth="3" strokeLinejoin="round" d="M70 138Q86 127 100 134Q114 127 130 138L123 161Q100 174 77 161Z" />
      <path fill="#8f2d2d" d="M86 160Q100 150 114 160Q100 167 86 160Z" />

      <Pair shapes={PAIRED_ON_FACE.slice(8)} />
    </svg>
  );
}
