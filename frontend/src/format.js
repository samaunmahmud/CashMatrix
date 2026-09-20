// Amounts arrive without a currency of their own, so a display currency is set here (pounds by default).
// Accounts that report their own currency, such as a US bank, pass it in.
const DEFAULT_CURRENCY = import.meta.env.VITE_CURRENCY || "GBP";

const formatters = new Map();

export function formatMoney(amount, currency = DEFAULT_CURRENCY) {
  const code = currency || DEFAULT_CURRENCY;
  if (!formatters.has(code)) {
    formatters.set(
      code,
      new Intl.NumberFormat("en-GB", { style: "currency", currency: code, currencyDisplay: "narrowSymbol" })
    );
  }
  return formatters.get(code).format(amount ?? 0);
}
