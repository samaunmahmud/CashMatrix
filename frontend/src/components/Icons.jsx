// Small outline icons. Decorative: the text beside each one carries the meaning,
// so they are hidden from screen readers.
const base = {
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
  focusable: false,
};

const icon = (children) =>
  function Icon({ size = 22 }) {
    return (
      <svg {...base} width={size} height={size}>
        {children}
      </svg>
    );
  };

export const HomeIcon = icon(<><path d="M3 11l9-8 9 8" /><path d="M5 10v10h14V10" /><path d="M10 20v-6h4v6" /></>);
export const CalendarIcon = icon(<><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4" /></>);
export const BellIcon = icon(<><path d="M6 8a6 6 0 0112 0c0 7 3 8 3 8H3s3-1 3-8" /><path d="M10 20a2 2 0 004 0" /></>);
export const SettingsIcon = icon(<><path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1" /><circle cx="15" cy="6" r="2" /><circle cx="9" cy="12" r="2" /><circle cx="17" cy="18" r="2" /></>);
export const PlusIcon = icon(<path d="M12 5v14M5 12h14" />);
export const BankIcon = icon(<><path d="M3 10l9-6 9 6" /><path d="M5 10v8M9 10v8M15 10v8M19 10v8M3 21h18" /></>);
export const RepeatIcon = icon(<><path d="M17 2l4 4-4 4" /><path d="M3 11v-1a4 4 0 014-4h14" /><path d="M7 22l-4-4 4-4" /><path d="M21 13v1a4 4 0 01-4 4H3" /></>);
export const RefreshIcon = icon(<><path d="M21 12a9 9 0 11-3-6.7L21 8" /><path d="M21 3v5h-5" /></>);
export const ChevronRightIcon = icon(<path d="M9 6l6 6-6 6" />);
export const CheckIcon = icon(<path d="M5 13l4 4L19 7" />);
export const BudgetIcon = icon(<><path d="M21 12a9 9 0 11-9-9v9z" /><path d="M15 3.5A9 9 0 0120.5 9H15z" /></>);
export const ChevronLeftIcon = icon(<path d="M15 6l-6 6 6 6" />);
export const PencilIcon = icon(<><path d="M4 20h4L19 9l-4-4L4 16z" /><path d="M13 7l4 4" /></>);
export const TrashIcon = icon(<><path d="M4 7h16M10 11v6M14 11v6" /><path d="M6 7l1 13h10l1-13M9 7V4h6v3" /></>);
