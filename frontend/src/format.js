// The API stores amounts without a currency, so the display currency is a setting.
// Plaid's sandbox banks are US ones; set VITE_CURRENCY=GBP once UK banks are connected.
const CURRENCY = import.meta.env.VITE_CURRENCY || "USD";

const formatter = new Intl.NumberFormat("en-GB", {
  style: "currency",
  currency: CURRENCY,
  currencyDisplay: "narrowSymbol",
});

export const formatMoney = (amount) => formatter.format(amount ?? 0);
