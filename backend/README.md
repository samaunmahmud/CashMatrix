# CashMatrix — Backend

This is the backend for CashMatrix, my expense tracker app, a full-stack portfolio project I built to learn real-world software development. It's a REST API built with Spring Boot that handles user authentication, connects to Plaid's banking API, and stores transaction data in PostgreSQL.

## What it does

- Users can sign up and log in with email and password
- Passwords are hashed with BCrypt (never stored as plain text)
- Every protected request requires a JWT token in the Authorization header
- Users can connect a real (sandbox) bank account through Plaid
- The app pulls the last 90 days of transactions from Plaid and stores them
- Transactions are deduplicated so syncing multiple times doesn't create duplicates
- Users can set monthly budgets per category and get warned when one is nearly used or overspent
- Month-by-month spending totals, and this month compared with the same point last month
- Alerts for money in and out, and a background bank sync every 4 hours so they reach phones
- Log in with a passkey (fingerprint, face or phone screen lock) as well as a password

## Tech stack

- **Java 17** with **Spring Boot 3.3**
- **Spring Security** for authentication
- **JWT** (JSON Web Tokens) for stateless auth
- **Spring Data JPA / Hibernate** for database access
- **PostgreSQL** as the database
- **WebClient** (Spring WebFlux) for calling the Plaid API
- **Lombok** to reduce boilerplate
- **Docker** for deployment

## API endpoints

| Method | Endpoint | Auth required | Description |
|--------|----------|---------------|-------------|
| POST | `/api/auth/signup` | No | Create a new account |
| POST | `/api/auth/login` | No | Log in and get a JWT |
| GET | `/api/me` | Yes | Get current user info |
| POST | `/api/plaid/link-token` | Yes | Start Plaid bank connection |
| POST | `/api/plaid/exchange-token` | Yes | Complete bank connection |
| POST | `/api/transactions/sync` | Yes | Pull latest transactions from Plaid |
| GET | `/api/transactions` | Yes | Get all stored transactions, each with its `accountId` and a tidy `merchant` name |
| GET | `/api/accounts` | Yes | Linked accounts with balances (never the Plaid token) |
| GET | `/api/subscriptions/suggestions` | Yes | Subscriptions found in your transactions that aren't on your calendar yet |
| POST | `/api/subscriptions/suggestions/{key}/accept` | Yes | Add a suggestion to the calendar as a repeating subscription |
| POST | `/api/subscriptions/suggestions/{key}/dismiss` | Yes | Stop suggesting it |
| GET | `/api/calendar?from=&to=` | Yes | Every occurrence between two dates (`yyyy-MM-dd`, max 400 days), for drawing the calendar |
| GET | `/api/calendar/events` | Yes | All calendar items, one row each |
| POST | `/api/calendar/events` | Yes | Create a task, payment or subscription |
| PUT | `/api/calendar/events/{id}` | Yes | Edit an item |
| POST | `/api/calendar/events/{id}/complete` | Yes | Mark the current occurrence done or paid |
| DELETE | `/api/calendar/events/{id}` | Yes | Delete an item and its notifications |
| GET | `/api/budgets?month=yyyy-MM` | Yes | Every budget with spent, remaining and status for a month (default this month), plus suggested budgets |
| POST | `/api/budgets` | Yes | Create a budget (`{"category": "Groceries", "monthlyLimit": 250}`) |
| PUT | `/api/budgets/{id}` | Yes | Change a budget's category or limit |
| DELETE | `/api/budgets/{id}` | Yes | Delete a budget |
| GET | `/api/insights?months=6` | Yes | Spending per month (1 to 12 months) and this month against last, overall and by category |
| GET | `/api/notifications` | Yes | Latest 50 notifications (also refreshes the caller's reminders and budget alerts) |
| GET | `/api/notifications/unread-count` | Yes | Number of unread notifications, for a badge |
| POST | `/api/notifications/{id}/read` | Yes | Mark one notification read |
| POST | `/api/notifications/read-all` | Yes | Mark all notifications read |
| GET | `/api/settings/notifications` | Yes | Reminder settings and which channels are available |
| PUT | `/api/settings/notifications` | Yes | Change any of `emailEnabled`, `transactionAlertsEnabled`, `transactionAlertMinimum` |
| POST | `/api/passkeys/register/start` | Yes | Options for adding a passkey (`navigator.credentials.create`) |
| POST | `/api/passkeys/register/finish` | Yes | Save the new passkey (`{"requestId", "credential", "name"}`) |
| GET | `/api/passkeys` | Yes | The user's passkeys |
| DELETE | `/api/passkeys/{id}` | Yes | Remove a passkey |
| POST | `/api/auth/passkey/start` | No | Options for logging in with a passkey (`navigator.credentials.get`) |
| POST | `/api/auth/passkey/finish` | No | Check the signed challenge and return a login token |
| POST | `/api/settings/notifications/test` | Yes | Send a test reminder through the channels that are on |
| POST | `/api/push/subscribe` | Yes | Register a browser for push (the body of `PushSubscription.toJSON()`) |
| POST | `/api/push/unsubscribe` | Yes | Remove a browser (`{"endpoint": "..."}`) |

## Banks and sessions

- Banks in the UK are linked by default. Set `plaid.country-codes` (env `PLAID_COUNTRY_CODES`) to `GB`, `US` or `GB,US`. Plaid must have the country enabled for your account.
- `plaid.base-url` (env `PLAID_BASE_URL`) sends Plaid calls somewhere else, such as a local stand-in server while developing.
- Each sync pages through everything Plaid holds for the last 90 days (not just the first 100) and refreshes account balances.
- A missing, expired or invalid login token gets a `401` with `{"error":"Please log in again"}`. A stale token is ignored on the login and signup calls, so it can never lock someone out.

## Subscription detection

The app looks through synced transactions for a merchant that charges a steady amount (within 15%) on a steady weekly or monthly rhythm, and suggests it as a subscription. Weekly needs three charges, monthly needs two. Charges that stopped more than a week past their expected date are treated as cancelled. Only the last 90 days are synced, so yearly subscriptions can't be spotted this way. Merchants are grouped by the first meaningful word of their name, so two different merchants sharing a first word can occasionally be confused.

## Budgets and insights

A budget is a monthly limit for one category. Spending is money out (Plaid's positive amounts), pending included, matched to a budget by the transaction's category (the user's own if set, otherwise the bank's), ignoring case. Each budget reports `ON_TRACK`, `NEAR_LIMIT` (80% or more) or `OVER`.

Budget alerts appear alongside calendar reminders and go out by email and push the same way. There is at most one "nearly used" and one "over" alert per budget per month, and none of the "nearly used" kind once a budget is already over. Budgets are checked by the hourly job, after each sync, when a budget is saved, and when the user opens their alerts.

Suggested budgets are the user's average month in each category they spend in but haven't budgeted for, rounded up to the next 5. The average uses the last three complete months the synced history fully covers. Someone with only part of a month synced gets this month so far instead.

Insights leave out months before the earliest synced transaction instead of showing them as zero. A month the history only partly covers is marked `partial`, and so is the current month. The "same time last month" figure is `null` when the history doesn't reach back to the start of last month.

## Money in and out alerts

After each sync, every new settled transaction of at least the user's chosen amount (default 25, or any amount if set to 0) becomes an alert such as "£46.50 spent at Dishoom" that opens the account. An account's first sync imports up to 90 days at once, so it never raises these alerts. Pending transactions wait until they settle, because the bank re-sends them under a new id, and anything dated more than 3 days ago isn't treated as news.

A background job syncs every linked bank every 4 hours (`app.sync.enabled`, `app.sync.cron`), so alerts and budget warnings reach email and phones without the app being open. Each sync is a call to Plaid, which matters on paid Plaid plans.

## Passkeys

Users can add passkeys in Settings and then log in with their fingerprint, face or screen lock, without typing an email. The server stores only the public key (built on Yubico's `webauthn-server-core`). Passkeys belong to the domain the app is served from, taken from `app.public-url` (override with `app.webauthn.rp-id`, and allow more addresses with `app.webauthn.extra-origins`). In production `app.public-url` must be the real frontend address, or passkeys won't work there. Challenges are kept in memory for 5 minutes, so this assumes a single backend instance.

## Calendar and reminders

A calendar item has a `type` (`TASK`, `PAYMENT`, `SUBSCRIPTION`), an optional `amount`, a `startDate`, a `recurrence` (`NONE`, `WEEKLY`, `MONTHLY`, `YEARLY`) and `remindDaysBefore` (0 to 30, default 1). Example body for `POST /api/calendar/events`:

```json
{ "title": "Netflix", "type": "SUBSCRIPTION", "amount": 9.99,
  "startDate": "2026-09-25", "recurrence": "MONTHLY", "remindDaysBefore": 3 }
```

- A repeating item is stored once; the calendar endpoint expands it into each day it falls on. Month-end dates stay stable (31 Jan repeats on 28 Feb, then 31 Mar).
- Completing a repeating item moves it to its next occurrence. Subscriptions are assumed to be charged automatically, so they move on by themselves after the due date. Bills and tasks wait until you mark them done.
- A background job runs at startup and every hour. It creates one in-app notification per occurrence once the due date is inside the item's reminder window, and keeps reminding for up to 7 days after a missed date. The job also runs for a user whenever they read their notifications, so a sleeping free-tier host still catches up.

Reminder settings (all optional): `app.reminders.enabled` (default `true`), `app.reminders.cron` (default top of every hour), `app.reminders.zone` (default `Europe/London`, used to decide what "today" is).

## Email and phone push reminders

In-app alerts work out of the box. Email and push are optional and off until you configure them (each can be set as an environment variable, shown in brackets). Users then opt in from Settings.

**Email** needs an SMTP server. Nothing is sent, and the Settings toggle is hidden, until `spring.mail.host` is set.

```
spring.mail.host=smtp.gmail.com        [SPRING_MAIL_HOST]
spring.mail.port=587                   [SPRING_MAIL_PORT]
spring.mail.username=you@gmail.com     [SPRING_MAIL_USERNAME]
spring.mail.password=an-app-password   [SPRING_MAIL_PASSWORD]
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
app.mail.from=CashMatrix <you@gmail.com>   [APP_MAIL_FROM]
app.public-url=https://your-frontend.example  [APP_PUBLIC_URL]   # used for the link in the email
```

**Push** (notifications on a phone or desktop through the browser) needs a VAPID key pair. Generate one once:

```bash
npx web-push generate-vapid-keys
```

```
app.push.public-key=...                [APP_PUSH_PUBLIC_KEY]
app.push.private-key=...               [APP_PUSH_PRIVATE_KEY]   # keep secret
app.push.subject=mailto:you@example.com [APP_PUSH_SUBJECT]
```

Push needs HTTPS in production (localhost is fine while developing). On iPhone it also needs the site added to the Home Screen.

How delivery behaves:
- Each reminder goes out once per channel. If a channel fails, the next hourly run retries; a channel that succeeded is never repeated.
- Only reminders from the last 24 hours are sent, so switching a channel on never floods you with old ones.
- Reminders created while you have the app open are shown in-app only, not also emailed or pushed.
- Only real browser push services (Google, Mozilla, Apple, Microsoft) are accepted as push addresses, because the server posts to whatever address a subscription names.
- A device that has unsubscribed or expired is forgotten automatically.

## Database schema

Nine tables: `users`, `bank_accounts`, `transactions`, `calendar_events`, `notifications`, `notification_preferences`, `push_subscriptions`, `dismissed_suggestions`. Bank accounts store the Plaid access token needed to fetch transactions. Transactions store merchant name, amount, date, and category from Plaid. Calendar events hold tasks, payments and subscriptions; notifications hold the reminders generated for them (unique per event and due date).

## Running the tests

```bash
./mvnw test
```

Tests use an in-memory H2 database, so PostgreSQL isn't needed.

## Running locally

You'll need Java 17+, Maven, and PostgreSQL installed.

1. Clone the repo
2. Create a local PostgreSQL database called `expense_tracker`
3. Copy `src/main/resources/application-example.properties` to `application.properties` and fill in your values
4. Get free Plaid sandbox credentials at https://dashboard.plaid.com
5. Run the app:

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` and auto-creates the database tables on first run.

## Deployment

Deployed on **Render** using Docker. The backend and PostgreSQL database both run on Render's free tier. Environment variables are used for all secrets — nothing sensitive is in the codebase.

Live API: `https://expense-tracker-backend-2lgp.onrender.com`

> Note: the free tier spins down after inactivity, so the first request after a period of no use may take 30-60 seconds to respond.

## What I learned building this

This was my first time building a production-deployed backend from scratch. The things that took the most learning were:

- How JWT authentication actually works under the hood (not just using a library blindly)
- How Plaid's OAuth-style token exchange flow works
- Debugging Spring Security filter chain issues
- The difference between reactive (WebFlux) and blocking code in a servlet app
- Setting up Docker and environment variables for deployment

## Frontend

The React frontend that consumes this API lives at: https://github.com/samaunmahmud/expense-tracker-frontend
