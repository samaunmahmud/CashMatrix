/**
 * The CashMatrix tiger. Drawn as a flat two-tone mark: a light face with
 * stripes in the brand green, so it reads on green (header) or, when
 * `badge` is set, sits inside its own green circle (login screen).
 */
export default function TigerLogo({ size = 36, badge = false, title = "CashMatrix" }) {
  const face = "#ffffff";
  const stripe = "#006a4d";

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      role="img"
      aria-label={title}
      xmlns="http://www.w3.org/2000/svg"
    >
      {badge && <circle cx="32" cy="32" r="30.5" fill={stripe} stroke="#ffffff" strokeWidth="3" />}
      <g transform={badge ? "translate(32 33) scale(0.78) translate(-32 -33)" : undefined}>
        {/* ears */}
        <circle cx="14" cy="16" r="8" fill={face} />
        <circle cx="50" cy="16" r="8" fill={face} />
        <circle cx="14" cy="16" r="3.6" fill={stripe} />
        <circle cx="50" cy="16" r="3.6" fill={stripe} />

        {/* head */}
        <path
          fill={face}
          d="M32 10C44 10 54 17 56 28C58 36 54 46 46 53C41 57.5 36 58.5 32 58.5C28 58.5 23 57.5 18 53C10 46 6 36 8 28C10 17 20 10 32 10Z"
        />

        {/* stripes */}
        <g fill="none" stroke={stripe} strokeWidth="2.6" strokeLinecap="round">
          <path d="M32 12V21" />
          <path d="M25 13.5L27.5 21" />
          <path d="M39 13.5L36.5 21" />
          <path d="M9.5 30L17 32" />
          <path d="M10.5 38L18 38" />
          <path d="M14.5 46.5L21 43.5" />
          <path d="M54.5 30L47 32" />
          <path d="M53.5 38L46 38" />
          <path d="M49.5 46.5L43 43.5" />
        </g>

        {/* eyes */}
        <path fill={stripe} d="M19 29.5Q24 25 28.5 30Q24 33.5 19 29.5Z" />
        <path fill={stripe} d="M45 29.5Q40 25 35.5 30Q40 33.5 45 29.5Z" />
        <circle cx="24" cy="29.6" r="1.5" fill={face} />
        <circle cx="40" cy="29.6" r="1.5" fill={face} />

        {/* nose and mouth */}
        <path fill={stripe} strokeLinejoin="round" stroke={stripe} strokeWidth="2" d="M28 40.5H36L32 45.5Z" />
        <g fill="none" stroke={stripe} strokeWidth="2" strokeLinecap="round">
          <path d="M32 46V49.5" />
          <path d="M32 49.5Q28.5 52.5 25.5 50.5" />
          <path d="M32 49.5Q35.5 52.5 38.5 50.5" />
        </g>
      </g>
    </svg>
  );
}
