# CashMatrix

Spending tracker with bank connections, a payment calendar and reminders, in a green, tiger-branded app.

## What it does

- Link a bank (UK banks by default, through Plaid) and see balances, spending by category and transactions
- Spot subscriptions and regular bills in your transactions and add them to your calendar in one tap
- Tap an account for a searchable statement grouped by day, and see spending by category or retailer
- Get alerts for money in and out, like a bank app, even when the app is closed
- Log in with your fingerprint or face (passkeys)
- Set monthly budgets per category and get a warning at 80% and when you go over
- See spending month by month, and how this month compares with the same point last month
- Keep a calendar of payments, subscriptions and tasks, with repeats and a reminder before each one
- Get reminders in the app, by email, and as notifications on your phone or computer

## Layout

| Folder | What it is |
|---|---|
| `backend/` | Spring Boot API: auth, Plaid, calendar, reminders, budgets, insights, subscription detection (see its README) |
| `frontend/` | React app (see its README) |
| `design/` | Vector screens (SVG) and design tokens you can open in Figma, Penpot, Sketch and others (see its README) |

## Running it locally

1. Backend: follow `backend/README.md` (needs Java 17+, PostgreSQL and free Plaid sandbox keys). It starts on `http://localhost:8080`.
2. Frontend:

```bash
cd frontend
npm install
npm run dev
```

Opens at `http://localhost:5173`.

Tests: `cd backend && ./mvnw test` (uses an in-memory database) and `cd frontend && npm run lint`.

## Note on cloud-synced folders

If this folder lives somewhere iCloud or a similar service syncs, build folders (`backend/target`, `frontend/node_modules`) can pick up duplicate files named like `Something 2.class`. They only appear in build output, never in your code, and can be deleted. Keeping the project outside a synced folder avoids the problem.
