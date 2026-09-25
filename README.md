# LeaveFlow: Employee Leave Tracker

A full-stack leave management app. Employees apply for leave, managers approve it for their team, and admins manage the organisation.
It handles Bangladesh working days (Friday–Saturday weekends and public holidays), leave balances per type, and a full audit trail.

> 🚧 Work in progress. See [docs/ROADMAP.md](docs/ROADMAP.md) for the plan and [docs/PROGRESS.md](docs/PROGRESS.md) for current status.

## Tech stack
- **Backend:** Java 17, Spring Boot, Spring Security (JWT), Spring Data JPA, Flyway
- **Frontend:** React, Vite, Tailwind CSS, Recharts
- **Database:** PostgreSQL
- **Infrastructure:** Docker, docker-compose, nginx

## Repository layout
```
/backend             Spring Boot REST API
/frontend            React single-page app
/docs                Roadmap, progress log, architecture write-up
docker-compose.yml   Full stack: db + backend + frontend
```
