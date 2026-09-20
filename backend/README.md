# CashMatrix — Backend

This is the backend for CashMatrix, my expense tracker app, a full-stack portfolio project I built to learn real-world software development. It's a REST API built with Spring Boot that handles user authentication, connects to Plaid's banking API, and stores transaction data in PostgreSQL.

## What it does

- Users can sign up and log in with email and password
- Passwords are hashed with BCrypt (never stored as plain text)
- Every protected request requires a JWT token in the Authorization header
- Users can connect a real (sandbox) bank account through Plaid
- The app pulls the last 90 days of transactions from Plaid and stores them
- Transactions are deduplicated so syncing multiple times doesn't create duplicates

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
| GET | `/api/transactions` | Yes | Get all stored transactions |
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
| GET | `/api/notifications` | Yes | Latest 50 notifications (also refreshes the caller's reminders) |
| GET | `/api/notifications/unread-count` | Yes | Number of unread notifications, for a badge |
| POST | `/api/notifications/{id}/read` | Yes | Mark one notification read |
| POST | `/api/notifications/read-all` | Yes | Mark all notifications read |
| GET | `/api/settings/notifications` | Yes | Reminder settings and which channels are available |
| PUT | `/api/settings/notifications` | Yes | Turn email reminders on or off (`{"emailEnabled": true}`) |
| POST | `/api/settings/notifications/test` | Yes | Send a test reminder through the channels that are on |
| POST | `/api/push/subscribe` | Yes | Register a browser for push (the body of `PushSubscription.toJSON()`) |
| POST | `/api/push/unsubscribe` | Yes | Remove a browser (`{"endpoint": "..."}`) |

## Banks and sessions

- Banks in the UK are linked by default. Set `plaid.country-codes` (env `PLAID_COUNTRY_CODES`) to `GB`, `US` or `GB,US`. Plaid must have the country enabled for your account.
- Each sync pages through everything Plaid holds for the last 90 days (not just the first 100) and refreshes account balances.
- A missing, expired or invalid login token gets a `401` with `{"error":"Please log in again"}`. A stale token is ignored on the login and signup calls, so it can never lock someone out.

## Subscription detection

The app looks through synced transactions for a merchant that charges a steady amount (within 15%) on a steady weekly or monthly rhythm, and suggests it as a subscription. Weekly needs three charges, monthly needs two. Charges that stopped more than a week past their expected date are treated as cancelled. Only the last 90 days are synced, so yearly subscriptions can't be spotted this way. Merchants are grouped by the first meaningful word of their name, so two different merchants sharing a first word can occasionally be confused.

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
