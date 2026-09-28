# LeaveFlow: Employee Leave Tracker

A full-stack leave management app. Employees apply for leave, managers approve it for their team, and admins manage the organisation.
It follows Bangladesh working days (Friday–Saturday weekend and public holidays), tracks a yearly balance per leave type,
and records every decision in an audit trail. Authentication uses short-lived JWT access tokens plus rotating refresh tokens.

Built for the MISL Fresher Assessment (problem #2). The architecture, components, API internals and Docker setup are
explained in **[ARCHITECTURE.md](ARCHITECTURE.md)**.

## Features

**Every employee**
- Apply for leave with a **live preview**: working days are counted as you pick dates (weekends and holidays skipped),
  and rule problems show before you submit (overlap, not enough balance, past dates).
- See balances per leave type (Annual 15, Sick 14, Casual 10, Unpaid unlimited), edit or withdraw pending requests,
  cancel approved leave before it starts, and follow each request's history timeline.
- Personal dashboard, the public holiday list, and a profile page with change password.

**Managers** (plus everything above)
- An approvals inbox for their direct reports. Approve with an optional comment; rejecting needs one.
- A team dashboard (who is away today and in the next two weeks) and a monthly **team leave calendar**.

**Admins** (plus everything above)
- Employee management: create, edit, deactivate and reactivate, with search, filters and paging.
- Company-wide dashboard with charts, a list of all leave requests, and public holiday management.

**Under the hood**
- Leave status state machine (PENDING → APPROVED / REJECTED / CANCELLED), enforced in one place.
- Balances computed from requests (no stored counters to drift), plus optimistic locking against double reviews.
- One JSON error format for every endpoint, Flyway-versioned schema, Swagger UI, and 159 backend tests.

## Quick start with Docker
Prerequisite: Docker Desktop (or Docker Engine with the compose plugin).

```bash
docker compose up --build
```

Then open **http://localhost:3000** and log in with one of the demo accounts below. The login page also has one-click
buttons for them. The first build downloads Maven and npm dependencies and takes a few minutes; later starts take seconds.

| Container | What it runs | Host port |
|---|---|---|
| `frontend` | nginx serving the React build; forwards `/api` and Swagger UI to the backend | 3000 |
| `backend` | Spring Boot jar (runs as a non-root user) | 8080 (Swagger UI: http://localhost:8080/swagger-ui.html) |
| `db` | PostgreSQL 17, data kept in the `db-data` volume | 5433 (so it doesn't clash with a local PostgreSQL) |

- The containers start in order using healthchecks: the backend waits for the database, and nginx waits for the backend.
- Flyway creates the schema on the first start, and demo leave data dated around *today* is loaded into the empty database.
- Every setting has a working default. To change passwords, the JWT key or ports, copy `.env.example` to `.env` and edit it.
- `docker compose down` stops everything and keeps the data; `docker compose down -v` also deletes the database volume.

### Demo accounts
| Role | Email | Password |
|---|---|---|
| Admin | admin@leaveflow.com | Admin@123 |
| Manager | tanvir.ahmed@leaveflow.com | Password@123 |
| Employee | rakib.hasan@leaveflow.com | Password@123 |

## Tech stack
- **Backend:** Java 17, Spring Boot 4, Spring Security, Spring Data JPA, Flyway, jjwt, springdoc-openapi
- **Frontend:** React 19, Vite, Tailwind CSS 4, React Router, axios, Recharts
- **Database:** PostgreSQL
- **Infrastructure:** Docker, docker-compose, nginx
- **Tests:** JUnit 5, Mockito, Spring MockMvc

## Repository layout
```
/backend             Spring Boot REST API (packages grouped by feature: auth, employee, leave, holiday, dashboard, ...)
/frontend            React single-page app (api, auth, components, pages, hooks, utils)
docker-compose.yml   Full stack: db + backend + frontend
.env.example         Optional settings for docker-compose
ARCHITECTURE.md      How it works: components, request flow, auth, rules, data model, Docker
```

## How it works (short version)
The browser only talks to nginx. nginx serves the React files and forwards `/api` to Spring Boot, which holds every
business rule and is the only component that talks to PostgreSQL. Inside the backend, each feature follows
`Controller → Service → Repository → Entity`. Controllers deal with HTTP and DTOs, services hold the rules inside
transactions, and repositories use Spring Data JPA. See [ARCHITECTURE.md](ARCHITECTURE.md) for diagrams of the request
lifecycle, the token flow, the leave state machine and the data model.

## Authentication
LeaveFlow uses the standard two-token model:

| | Access token | Refresh token |
|---|---|---|
| What it is | Signed JWT (HS256) with the user's id, email, name and role | 256-bit random string |
| Lifetime | 15 minutes | 7 days |
| Where the client keeps it | In memory | `HttpOnly`, `SameSite=Strict` cookie (JavaScript cannot read it) |
| Where the server keeps it | Nowhere: it is verified by signature alone (stateless) | Only a SHA-256 hash, in the `refresh_tokens` table |
| Can it be revoked? | No, so it is kept short-lived | Yes: logout, password change, account deactivation |

**How it flows**
1. `POST /api/auth/login` returns the access token in the JSON body and sets the refresh-token cookie.
2. Each API call sends `Authorization: Bearer <access token>`.
3. When the access token expires, the API answers `401 "Access token has expired"`. The client then calls `POST /api/auth/refresh`, and the browser sends the cookie automatically.
4. Refresh **rotates** the refresh token: the old one is revoked, and a new pair is returned.
5. If an already-rotated refresh token is ever used again, it was probably stolen. All of that user's sessions are revoked (reuse detection).
6. `POST /api/auth/logout` revokes the refresh token and clears the cookie.

## API overview
All endpoints are under `/api` and documented in Swagger UI (`/swagger-ui.html`). Main groups:

| Area | Endpoints | Who |
|---|---|---|
| Auth | `POST /auth/login`, `/auth/refresh`, `/auth/logout` · `GET /auth/me` · `PUT /auth/password` | Public / logged in |
| Employees | `GET /employees` (search, filter, page) · `POST` · `PUT /{id}` · `DELETE /{id}` (deactivate) · `PATCH /{id}/activate` | Admin |
| | `GET /employees/team`, `/employees/{id}` | Manager, admin |
| Leave requests | `GET /leaves/preview` · `POST /leaves` · `PUT /leaves/{id}` · `POST /leaves/{id}/cancel` · `GET /leaves/my`, `/leaves/balance`, `/leaves/{id}/history` | Everyone (own requests) |
| | `GET /leaves/approvals`, `/leaves/team` · `POST /leaves/{id}/approve`, `/leaves/{id}/reject` | Manager (own team), admin |
| | `GET /leaves` (all requests, filtered and paged) | Admin |
| Reference data | `GET /leave-types` · `GET /holidays?year=` · holiday `POST` / `PUT` / `DELETE` | Everyone / admin writes |
| Dashboards | `GET /dashboard/me` · `/dashboard/team` · `/dashboard/organisation` · `GET /calendar?month=yyyy-MM` | By role |

Errors always use one JSON shape: `{ timestamp, status, error, message, path, fieldErrors? }`.

## Local development (without Docker)

### Backend
Prerequisites: Java 17 and a running PostgreSQL.

1. Create an empty database, for example `leaveflow_misl_assignment`.
2. Copy `backend/.env.example` to `backend/.env` and fill in your database credentials.
3. Start the API from the `backend` folder:
   ```bash
   ./mvnw spring-boot:run
   ```
4. Flyway creates the schema and seed data on first start. Once it's up:
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - Health: http://localhost:8080/actuator/health

Run the tests (no database needed):
```bash
./mvnw test
```

### Frontend
Prerequisites: Node.js 20+ and the backend running on port 8080.

```bash
cd frontend
npm install
npm run dev
```
Open http://localhost:5173. The Vite dev server forwards `/api` calls to the backend, so the browser talks to a single
origin, just as it does behind nginx in Docker, and the refresh-token cookie works the same way.

On first start, the backend fills an empty database with sample leave requests dated around *today*, so the dashboards,
charts and calendar have data. Turn this off with `DEMO_DATA_ENABLED=false`.

## Known limitations
- If the only admin applies for leave and has no manager, nobody else can approve it.
- Editing a public holiday does not recalculate requests that were already submitted.
- Two browser tabs refreshing at exactly the same moment can trigger reuse detection and force a new login.
- Not included on purpose: email notifications, half-day leave, attachments, multi-level approval.
