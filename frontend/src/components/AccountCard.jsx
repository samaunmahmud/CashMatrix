import { formatMoney } from "../format";
import { BankIcon } from "./Icons";

const LABELS = {
  checking: "Current account",
  savings: "Savings",
  "credit card": "Credit card",
  cd: "Deposit",
  "money market": "Money market",
};

// What to lead with depends on the kind of account: spendable money for a current account,
// what is owed for a card.
function describe(account) {
  const isCredit = account.type === "credit";
  const headline = isCredit ? account.currentBalance : account.availableBalance ?? account.currentBalance;
  const label = LABELS[account.subtype] || (isCredit ? "Credit card" : "Account");
  return {
    label,
    headline,
    headlineCaption: isCredit ? "Balance owed" : "Available",
    secondary: isCredit
      ? account.availableBalance != null && { caption: "Available credit", value: account.availableBalance }
      : account.availableBalance != null &&
        account.currentBalance != null &&
        account.currentBalance !== account.availableBalance && { caption: "Balance", value: account.currentBalance },
  };
}

export default function AccountCard({ account }) {
  const { label, headline, headlineCaption, secondary } = describe(account);

  return (
    <article className="account-card" aria-label={`${account.name}, ${label}`}>
      <div className="account-card-top">
        <span className="account-icon"><BankIcon size={18} /></span>
        <div className="account-names">
          <h3>{account.name}</h3>
          <p className="muted">
            {label}
            {account.mask ? ` · •• ${account.mask}` : ""}
          </p>
        </div>
      </div>

      <div className="account-balance">
        <span className="account-balance-caption">{headlineCaption}</span>
        <span className="account-balance-amount">
          {headline != null ? formatMoney(headline, account.currency) : "Not available"}
        </span>
      </div>

      {secondary && (
        <p className="account-secondary">
          {secondary.caption} <strong>{formatMoney(secondary.value, account.currency)}</strong>
        </p>
      )}
    </article>
  );
}
