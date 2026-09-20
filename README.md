# CashMatrix — Frontend

This is the frontend for CashMatrix, my expense tracker app. It's a React app that lets users sign up, connect a real bank account through Plaid, and see their transactions and spending breakdown on a dashboard.

## What it looks like

- A clean login/signup page
- After logging in, users see a dashboard with their spending by category and a full transaction list
- A "Connect bank account" button opens Plaid's official widget where you can link a sandbox bank
- Transactions show the merchant name, amount (green for money in, black for money out), category, and date
- Staying logged in across page refreshes (JWT stored in localStorage)

## Tech stack

- **React 18** with **Vite** for fast development and building
- **React Router** for client-side navigation
- **Axios** for HTTP requests to the backend API
- **react-plaid-link** for the Plaid bank connection widget
- Plain CSS-in-JS for styling (no UI library — everything is hand-written)

## Project structure

```
src/
  api.js              # Axios client, auto-attaches JWT to every request
  AuthContext.jsx     # Global auth state, login/logout logic
  App.jsx             # Routes and protected route wrapper
  LoginPage.jsx       # Combined login and signup form
  DashboardPage.jsx   # Main dashboard: Plaid Link, transactions, spending chart
```

## Running locally

You'll need Node.js installed.

1. Clone the repo
2. Install dependencies:
```bash
npm install
```
3. Make sure the backend is running on `http://localhost:8080`
4. Start the dev server:
```bash
npm run dev
```

Opens at `http://localhost:5173`

## How the Plaid flow works

1. When the dashboard loads, the frontend asks our backend for a `link_token`
2. That token is used to open Plaid's official Link widget
3. The user picks a sandbox bank (use "First Platypus Bank" with username `user_good` and password `pass_good`)
4. Plaid gives us back a `public_token`
5. We send that to our backend, which exchanges it for a permanent `access_token`
6. The backend then pulls the last 90 days of transactions from Plaid and stores them
7. The dashboard fetches and displays those transactions

## Deployment

Deployed on **Vercel** with automatic deploys on every push to `main`. The `VITE_API_URL` environment variable points to the live Render backend.

Live app: `https://expense-tracker-front-end-pi.vercel.app`

## What I learned building this

- How to manage authentication state globally with React Context
- How to protect routes so unauthenticated users get redirected to login
- How Plaid's Link widget integrates into a React app
- How to use Axios interceptors to attach auth headers automatically
- Debugging CORS issues between a deployed frontend and backend

## Backend

The Spring Boot backend that powers this app lives at: https://github.com/samaunmahmud/Expense-Tracker-BackEnd
