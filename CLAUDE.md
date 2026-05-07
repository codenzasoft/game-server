# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew bootRun          # Start dev server on port 8082 (includes frontend build)
./gradlew build            # Compile and package
./gradlew test             # Run tests
./gradlew spotlessApply    # Apply Google Java Format (runs automatically before compile)
./gradlew spotlessCheck    # Check formatting without applying
```

Spotless is wired as a pre-compile dependency (`compileJava.dependsOn('spotlessApply')`), so formatting is applied on every build.

The `buildFrontend` Gradle task runs `vite build --mode development` by default. Pass `-PviteMode=production` to use production env vars (the Dockerfile does this automatically).

## Frontend Development

The frontend is a Vite/TypeScript (Pixi.js) SPA. Two local development workflows are supported:

**Mode 1 — everything through Spring Boot (port 8082):**
```bash
./gradlew bootRun
# Access app at http://localhost:8082
```
Gradle compiles the frontend via `vite build --mode development` and serves it as static resources. No HMR.

**Mode 2 — Vite dev server with HMR (port 8085):**
```bash
# Terminal 1
./gradlew bootRun --no-daemon   # or just start Spring Boot without building frontend

# Terminal 2
cd ui && npm run dev
# Access app at http://localhost:8085
```
The Vite dev server at `localhost:8085` proxies all requests to Spring Boot at `localhost:8082`. The game page (`/games/xo/{uuid}`) is served by Vite directly (for HMR), while all other routes (auth, home, APIs) are proxied to Spring Boot. The `/games-connect` WebSocket has its own proxy entry.

**Key proxy design decisions:**
- No `changeOrigin` on the HTTP proxy — Spring Boot must see `Host: localhost:8085` so its redirect `Location` headers point back to port 8085 (not 8082).
- The WebSocket proxy (`/games-connect`) is a separate entry from the HTTP proxy — if `ws: true` is on the general `/` rule it intercepts Vite's own HMR WebSocket and sends it to Spring Boot, causing an infinite reload loop.
- Game-page bypass returns `/index.html` so Vite serves its own `index.html` (with HMR) instead of proxying to Spring Boot's compiled version.

**Environment files:**
- `ui/.env.development` — minimal; omits `VITE_WS_URL` and `VITE_LOGIN_URL` so the TypeScript fallbacks kick in (`window.location.host`), which resolves correctly for both dev modes.
- `ui/.env.production` — hardcodes `wss://games.dev.codenzasoft.ca/...` URLs for the Cloud Run deployment.

## Prerequisites

The app requires a PostgreSQL instance. In development it connects to `localhost:5435` (default profile). The companion repo [codenzasoft/game-database](https://github.com/codenzasoft/game-database) provides this via Docker. Environment variables for non-default setups:

```
POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_HOST, POSTGRES_PORT, POSTGRES_NAME
```

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
- `cloud` — Cloud Run / GCP; enables `forward-headers-strategy: native` and the Cloud SQL socket-factory JDBC URL

### Schema management

JPA is configured with `hibernate.ddl-auto: update`. Schema evolves automatically; no migration tool is in use.

## Testing

JUnit 4 + Spock (Groovy) are on the test classpath. No tests exist yet. The Gradle test task is configured to log `passed`, `skipped`, and `failed` events.

## Deployment

The app is deployed to **Google Cloud Run** at `https://games.dev.codenzasoft.ca`.

| Resource | Value |
|---|---|
| GCP project | `crafty-dynamics-495319-e2` |
| Region | `us-central1` |
| Cloud SQL instance | `crafty-dynamics-495319-e2:us-central1:codenza-games-db` |
| Artifact Registry | `us-central1-docker.pkg.dev/crafty-dynamics-495319-e2/game-server/game-server` |
| Runtime secrets | `game-server-db-user`, `game-server-db-password`, `game-server-db-name` (GCP Secret Manager) |

**CI/CD (`.github/workflows/`):**
- `pr.yml` — runs `./gradlew build` on every PR to `main`; required status check before merge.
- `deploy.yml` — triggers on push to `main`; builds the Docker image, pushes to Artifact Registry, deploys to Cloud Run with `SPRING_PROFILES_ACTIVE=cloud` and Cloud SQL instance attached.

Authentication from GitHub Actions to GCP uses Workload Identity Federation (keyless — no service account key files).
