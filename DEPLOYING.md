# Deploying CashMatrix

The API and database run on Render, the frontend on Vercel. Both deploy from this repo on every push to `main`.

## 1. Backend on Render

**Moving the existing service (keeps its database and users).** In the Render dashboard, open the current backend service, then under Settings:

- Repository: `samaunmahmud/CashMatrix`, branch `main`
- Root Directory: `backend`
- Dockerfile Path: `./Dockerfile`, Docker Build Context: `.`
- Environment: add `APP_PUBLIC_URL` (the frontend address from step 2). The database, `JWT_SECRET` and Plaid values it already has carry on working.

**Or starting fresh.** New, then Blueprint, pick this repo. Render reads `render.yaml`, creates the database and the `cashmatrix-api` service, generates `JWT_SECRET`, and asks for:

| Variable | Value |
|---|---|
| `PLAID_CLIENT_ID`, `PLAID_SECRET` | From dashboard.plaid.com (sandbox keys) |
| `APP_PUBLIC_URL` | The frontend address, e.g. `https://cashmatrix.vercel.app` |

Render's free PostgreSQL expires after 30 days unless upgraded, so the existing service's database may be worth keeping.

## 2. Frontend on Vercel

Add New Project, import `samaunmahmud/CashMatrix`, then:

- Root Directory: `frontend` (Vercel detects Vite)
- Environment variable `VITE_API_URL`: the Render address plus `/api`, e.g. `https://cashmatrix-api.onrender.com/api`

`vercel.json` sends every path to the app, so reloading a page such as `/budgets` works.

## 3. Connect the two

`APP_PUBLIC_URL` on Render must be exactly the Vercel address (no path needed). It decides which site may call the API, where email links point, and which domain passkeys belong to. Passkeys made on one address don't work on another, so settle on the final address (a custom domain if you plan one) before adding them.

## Optional

| Feature | Variables (Render) |
|---|---|
| Email reminders | `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `APP_MAIL_FROM` |
| Phone and desktop push | `APP_PUSH_PUBLIC_KEY`, `APP_PUSH_PRIVATE_KEY`, `APP_PUSH_SUBJECT` (make keys with `npx web-push generate-vapid-keys`) |
| More frontend addresses | `APP_CORS_EXTRA_ORIGINS`, comma separated |
| US banks too | `PLAID_COUNTRY_CODES=GB,US` |

## Afterwards

Once the new deploys work, the old `expense-tracker-1` and `expense-tracker-frontend` repos can be archived on GitHub, and the old frontend address removed from `SecurityConfig`.
