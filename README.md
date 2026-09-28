# LeaveFlow: Employee Leave Tracker

A full-stack leave management app. Employees apply for leave, managers approve it for their team, and admins manage the organisation.
It handles Bangladesh working days (Friday–Saturday weekends and public holidays), leave balances per type, and a full audit trail.
Authentication uses JWT access tokens plus rotating refresh tokens.

> 🚧 Work in progress. The backend and the React frontend are done; Docker setup and the architecture write-up come next.

## Tech stack
- **Backend:** Java 17, Spring Boot 4, Spring Security, Spring Data JPA, Flyway, jjwt
- **Frontend:** React, Vite, Tailwind CSS, Recharts
- **Database:** PostgreSQL
- **Infrastructure:** Docker, docker-compose, nginx

## Repository layout
```
/backend             Spring Boot REST API
/frontend            React single-page app
docker-compose.yml   Full stack: db + backend + frontend
```

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

## Running the backend locally
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

## Running the frontend locally
Prerequisites: Node.js 20+ and the backend running on port 8080.

```bash
cd frontend
npm install
npm run dev
```
Open http://localhost:5173. The Vite dev server forwards `/api` calls to the backend, so the browser talks to a single origin. That lets the refresh-token cookie work exactly as it will behind nginx in Docker.

On first start, the backend fills an empty database with sample leave requests dated around *today*, so the dashboards, charts and calendar have data. Turn this off with `DEMO_DATA_ENABLED=false`.

### Demo accounts
| Role | Email | Password |
|---|---|---|
| Admin | admin@leaveflow.com | Admin@123 |
| Manager | tanvir.ahmed@leaveflow.com | Password@123 |
| Employee | rakib.hasan@leaveflow.com | Password@123 |
