# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew bootRun          # Start dev server on port 8082
./gradlew build            # Compile and package (includes frontend build)
./gradlew test             # Run tests
./gradlew spotlessApply    # Apply Google Java Format (runs automatically before compile)
./gradlew spotlessCheck    # Check formatting without applying
```

Spotless is wired as a pre-compile dependency (`compileJava.dependsOn('spotlessApply')`), so formatting is applied on every build.

The frontend (`ui/`) is built via the `node-gradle` plugin as part of `processResources`. `vite build` runs automatically during `./gradlew build` and its output is copied into `static/`.

```bash
cd ui && npm run dev       # Start Vite dev server on port 8085 (standalone)
```

## Prerequisites

The app requires a PostgreSQL instance. In development it connects to `localhost:5435` (default profile). The companion repo [codenzasoft/game-database](https://github.com/codenzasoft/game-database) provides this via Docker. Environment variables for non-default setups:

```
POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_HOST, POSTGRES_PORT, POSTGRES_NAME
```

## Frontend (ui/)

Vite + TypeScript app in `ui/`. Environment-specific config uses Vite's env file conventions:

- `ui/.env.development` — loaded by `vite dev` (localhost URLs)
- `ui/.env.production` — loaded by `vite build` / Docker (production URLs pointing to `games.dev.codenzasoft.ca`)

## Architecture

Spring Boot 4 / Java 17 server-side-rendered web app using Thymeleaf. No REST API layer — all pages are MVC controllers returning model+view pairs.

**Package root**: `com.codenzasoft.games.spring`

### Layers

| Layer | Package | Notes |
|---|---|---|
| Controllers | `controllers/mvc/` | MVC only; model attributes defined via `PageConstants` |
| Services | `services/` | `GamesUserDetailsService` bridges JPA users to Spring Security |
| Repositories | `repositories/` | Spring Data `CrudRepository`; custom finders by `userName` / `email` |
| Entities | `entities/` | `UserEntity` (eager roles), `RoleEntity` (composite key via `RoleId`) |
| Config | `config/` | `SecurityConfig` (form login, role-based rules), `Role` enum |
| Views | `views/` | `ViewHelper` component exposes helpers (e.g. `isAdmin()`) to templates |

### Security model

- Form login redirects to `/login`; public paths are `/img/**` and `/login`
- Roles: `ADMIN`, `DEVELOPER`, `USER`, `BOT` (defined in `Role` enum)
- Password encoding uses `DelegatingPasswordEncoder` (default bcrypt; supports legacy PBKDF2/scrypt/Argon2)
- `RoleEntity` uses a composite primary key (`userId` + `role`)

### Templates

Thymeleaf templates live in `src/main/resources/templates/`. Caching is disabled in the `development` profile. Reusable markup is in `templates/fragments/`.

### Profiles

- `development` (default) — local Postgres at `localhost:5435`, template cache off
- `env-development` — environment-variable-driven Postgres connection
- `cloud` — Cloud Run production; connects to Cloud SQL via Unix socket (`postgres-socket-factory`); trusts forwarded headers for HTTPS

### Schema management

JPA is configured with `hibernate.ddl-auto: update`. Schema evolves automatically; no migration tool is in use.

## Deployment

The app is deployed to **Google Cloud Run** at `https://games.dev.codenzasoft.ca`.

- **GCP project**: `crafty-dynamics-495319-e2` (region: `us-central1`)
- **Cloud SQL**: `crafty-dynamics-495319-e2:us-central1:codenza-games-db` (connected via Unix socket)
- **Artifact Registry**: `us-central1-docker.pkg.dev/crafty-dynamics-495319-e2/game-server/game-server`
- **DB credentials**: stored in GCP Secret Manager (`game-server-db-user`, `game-server-db-password`, `game-server-db-name`)

### CI/CD

- **PR builds** (`.github/workflows/pr.yml`): runs `./gradlew build` on every PR to `main`; required to pass before merge
- **Deploy** (`.github/workflows/deploy.yml`): on merge to `main`, builds Docker image, pushes to Artifact Registry, deploys to Cloud Run
- GitHub Actions authenticates to GCP via Workload Identity Federation (no long-lived keys)

## Testing

JUnit 4 + Spock (Groovy) are on the test classpath. No tests exist yet. The Gradle test task is configured to log `passed`, `skipped`, and `failed` events.
