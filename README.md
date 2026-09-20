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

## Database schema

Three tables: `users`, `bank_accounts`, and `transactions`. Bank accounts store the Plaid access token needed to fetch transactions. Transactions store merchant name, amount, date, and category from Plaid.

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
