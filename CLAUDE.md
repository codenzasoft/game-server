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

## Local Build Configuration

The build depends on `game-common` and `game-hearts` from Maven Central — no credentials required.

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
The Vite dev server at `localhost:8085` proxies all requests to Spring Boot at `localhost:8082`. The game page (`/games/xo/{uuid}`) is served by Vite directly (for HMR), while all other routes (auth, home, APIs) are proxied to Spring Boot. The `/games/connect` WebSocket has its own proxy entry.

**Key proxy design decisions:**
- No `changeOrigin` on the HTTP proxy — Spring Boot must see `Host: localhost:8085` so its redirect `Location` headers point back to port 8085 (not 8082).
- The WebSocket proxy (`/games/connect`) is a separate entry from the HTTP proxy — if `ws: true` is on the general `/` rule it intercepts Vite's own HMR WebSocket and sends it to Spring Boot, causing an infinite reload loop.
- Game-page bypass returns `/index.html` so Vite serves its own `index.html` (with HMR) instead of proxying to Spring Boot's compiled version.

**Environment files:**
- `ui/.env.development` — minimal; omits `VITE_WS_URL` and `VITE_LOGIN_URL` so the TypeScript fallbacks kick in (`window.location.host`), which resolves correctly for both dev modes.
- `ui/.env.production` — hardcodes `wss://games.dev.codenzasoft.ca/...` URLs for the Cloud Run deployment.

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

- Form login redirects to `/login`; public paths: `/img/**`, `/login`, `/register`, `/register/check-email`, `/forgot-password`, `/reset-password`, `/verify-email`, `/verify-email/resend`, `/guest/login`
- Roles: `ADMIN`, `DEVELOPER`, `USER`, `GUEST`, `BOT` (defined in `Role` enum); new self-registered accounts receive `USER`; guest sessions receive `GUEST`
- Role hierarchy (`ADMIN > DEVELOPER > USER > GUEST > BOT`) is defined in `SecurityConfig` and auto-wired into both URL and method security by Spring Security 6.3+
- `/profile` requires `USER` or above — guests are redirected to login
- Password encoding uses `DelegatingPasswordEncoder` (default bcrypt; supports legacy PBKDF2/scrypt/Argon2)
- `RoleEntity` uses a composite primary key (`userId` + `role`)
- Unverified accounts (`email_verified_at IS NULL`) are blocked at login — `GamesUserDetailsService` sets `enabled=false` on the Spring Security `UserDetails` object
- Self-service password reset: 30-minute single-use token stored in `password_reset_tokens`, delivered via Mailgun (`PasswordResetService`)
- Rate limiting on registration endpoints: `RegistrationRateLimiter` (`HandlerInterceptor`) — 5 requests/IP/hour on `POST /register`, `POST /verify-email/resend`, and `POST /guest/login`

### Templates

Thymeleaf templates live in `src/main/resources/templates/`. Caching is disabled in the `development` profile. Reusable markup is in `templates/fragments/`.

### Profiles

- `development` (default) — local Postgres at `localhost:5435`, template cache off
- `env-development` — environment-variable-driven Postgres connection
- `cloud` — Cloud Run / GCP; enables `forward-headers-strategy: native` and the Cloud SQL socket-factory JDBC URL

### WebSocket routes

STOMP over WebSocket. Connection endpoint: `/games/connect` (`WebSocketConfig.java`). Application destination prefix: `/games` (stripped server-side, so a client destination of `/games/xo/{id}/play` maps to `@MessageMapping("/xo/{id}/play")`).

**Client → Server** (`XoController.java`):

| Client destination | Handler | Description |
|---|---|---|
| `/games/xo/{gameId}/load` | `load()` | Request initial game state on connect |
| `/games/xo/{gameId}/play` | `play()` | Submit a move (payload: `{row, column}`) |
| `/games/xo/{gameId}/quit` | `quit()` | Forfeit the game |
| `/games/xo/{gameId}/again` | `again()` | Request a rematch |

**Server → Client** (`XoBroadcastService.java`):

| Subscription | Sent by | Description |
|---|---|---|
| `/user/queue/games/{gameId}` | `convertAndSendToUser(...)` | Per-player game state updates |

### Account creation flow

Registration is open to anyone with a valid email address.

1. User submits the registration form (`POST /register`) — `RegisterController` validates input, checks email/username uniqueness, creates the `UserEntity` with `email_verified_at = NULL` and the `USER` role, then calls `EmailVerificationService.sendVerification()`.
2. `EmailVerificationService` (extends `MailgunEmailService`) stores a 24-hour UUID token in `email_verification_tokens` and sends an email via the Mailgun HTTP API. In local development, when `MAILGUN_API_KEY` / `MAILGUN_DOMAIN` are not set, the verification URL is logged at WARN level instead.
3. The user is redirected to `/register/check-email`. Clicking the emailed link hits `GET /verify-email?token=…` — `VerifyEmailController` validates the token, sets `email_verified_at` on the user, and redirects to `/login`.
4. `GamesUserDetailsService` passes `user.isEmailVerified()` as the `enabled` flag when building the Spring Security `UserDetails` object, so unverified accounts are rejected at login with a disabled-account error.
5. Users who never received the email can request a new link at `/verify-email/resend` (linked from the login page). The resend endpoint is anti-enumeration: it always returns a generic success message regardless of whether the account exists.

**Rate limiting:** A `RegistrationRateLimiter` (`HandlerInterceptor`) applies a per-IP sliding-window limit of 5 requests/hour to `POST /register` and `POST /verify-email/resend`. The limiter is per-instance (no shared state across Cloud Run replicas), which is acceptable because email verification is still required to activate any account created through it.

**Mailgun configuration** (`application.yaml`):

```
MAILGUN_API_KEY, MAILGUN_DOMAIN, MAILGUN_FROM
```
Both `EmailVerificationService` and `PasswordResetService` extend `MailgunEmailService`, which holds the shared Mailgun config fields and `sendEmail()` implementation.

### Guest login flow

Guests can play without creating an account. Progress is not saved across sessions.

1. User enters a display name (3–25 chars, `[a-zA-Z0-9_-]+`) in the "Play Now" section on the login page and submits `POST /guest/login` (`GuestLoginController`).
2. The controller generates a unique internal username: `displayName#xxxx` (4 random hex digits). The `#` separator cannot appear in validated regular usernames, so it is a safe namespace boundary. Up to 5 suffix candidates are tried before returning an error.
3. A `UserEntity` is created with no email or password, `email_verified_at = NOW()` (so Spring Security considers the account enabled), and the `GUEST` role.
4. A Spring Security session is established programmatically — a `UsernamePasswordAuthenticationToken` with a `UserDetails` principal is written to the `HttpSession` under `SPRING_SECURITY_CONTEXT_KEY`.
5. The guest is redirected to `/`. The "Profile" nav link is hidden (`th:unless="${user.isGuest()}"`), and `/profile` is blocked at the security layer (`hasAuthority("USER")`).

**Display name vs. stored username:** `UserEntity.getDisplayName()` strips the `#xxxx` suffix for display and for game-engine player identity (seat lookups, state keys). `getUsername()` returns the full stored name and is used only for STOMP routing (`convertAndSendToUser`) and Spring Security principal name — places that must match the session's principal exactly.

**Cleanup:** `GuestCleanupService` runs hourly (1-hour initial delay). It finds `GUEST` users whose `created_at` is older than 1 hour, skips any still in an active game (`over_at IS NULL AND quit_at IS NULL`), deletes their `game_players` rows (no FK cascade from `users`), then deletes the `UserEntity`. Timing is overridable via `guest.cleanup.initialDelay` and `guest.cleanup.fixedDelay` (milliseconds).

**Rate limiting:** `POST /guest/login` is covered by the same `RegistrationRateLimiter` as registration — 5 requests/IP/hour.

### Session persistence

HTTP sessions are stored in PostgreSQL via **Spring Session JDBC** (`SessionConfig.java`, `@EnableJdbcHttpSession`). This means sessions survive server restarts and are shared across Cloud Run replicas — users are not logged out on deploy.

**Tables** (created by `V14__Add_spring_session.sql` in `game-database`):
- `SPRING_SESSION` — one row per active session; tracks `LAST_ACCESS_TIME`, `EXPIRY_TIME`, and `PRINCIPAL_NAME`
- `SPRING_SESSION_ATTRIBUTES` — serialized session attributes (e.g. Spring Security `SecurityContext`)

**Expiry:** Session timeout is 30 minutes of inactivity (Spring Boot default, configurable via `server.servlet.session.timeout`). Spring Session runs a background `@Scheduled` task (default: every 60 seconds) that deletes rows where `EXPIRY_TIME < now`. Logout deletes the row immediately. Each Cloud Run replica runs this cleanup independently — the `DELETE WHERE EXPIRY_TIME < ?` is idempotent so concurrent cleanup across replicas is safe.

> **Spring Boot 4 note:** `spring.session.store-type` was removed in Spring Boot 4.0 and is silently ignored. Use `@EnableJdbcHttpSession` (or the equivalent annotation for other stores) — not the `store-type` property — to activate Spring Session.

### Hearts card texture atlas

Card textures for the Hearts game are served as a single PNG texture atlas (`ui/public/assets/cards.png`) rather than 53 individual SVG files. This reduces asset loading to 2 HTTP requests (JSON descriptor + PNG) and eliminates intermittent stalls caused by browser per-origin connection limits being saturated by parallel requests.

**Files:**
- `ui/public/assets/cards.png` — 9×6 grid atlas, each card at 160×224px (2× `BASE_CARD_W/H`)
- `ui/public/assets/cards.json` — Pixi spritesheet descriptor; `"scale": "0.5"` in meta tells Pixi to report frames at 80×112 logical pixels

**Regenerating the atlas** (only needed if card SVGs change):
```bash
cd ui && npm run build:atlas
```
This runs `ui/scripts/build-card-atlas.mjs`, which rasterises each card SVG via `sharp` and repacks the atlas. Commit the updated `cards.png` and `cards.json` afterward.

**Source SVGs** remain in `ui/public/assets/cards/` and `ui/public/assets/backs/` as the authoritative source for regeneration.

### Hearts felt background

The Hearts game background is a seamlessly-tileable green felt texture (`ui/public/assets/felt-tile.png`) rendered as a Pixi `TilingSprite` pinned at stage index 0. It is loaded in `preloadTextures()` alongside the card atlas and re-added to the stage after every `clearScene()` call.

**Regenerating the tile** (only needed if the colour or texture needs adjustment):
```bash
cd ui && npm run build:felt
```
This runs `ui/scripts/build-felt-tile.mjs`, which renders an SVG `feTurbulence` filter via `sharp` to produce a 256×256 PNG. Tweak the `baseFrequency`, `slope`/`intercept`, blend `mode`, or base fill colour in the script to adjust the look, then commit the updated `felt-tile.png`.

### Schema management

JPA is configured with `hibernate.ddl-auto: none`. Schema migrations are managed by Flyway in the companion repo [codenzasoft/game-database](https://github.com/codenzasoft/game-database).

### Background tasks

`GameCleanupService` runs 2 minutes after startup and every 6 hours. It evicts completed games (where `overAt` or `quitAt` is set) from the in-memory cache and bulk-deletes them from the database; child rows in `game_players`, `xo_game_pieces`, and `xo_game_moves` are removed via `ON DELETE CASCADE`. Timing is overridable via `game.cleanup.initialDelay` and `game.cleanup.fixedDelay` (milliseconds).

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

All tests are written in Groovy using the Spock framework. Unit tests (game logic) live in `src/test/groovy/` and extend `spock.lang.Specification` with no Spring context.

### Database integration tests

Database tests use `@SpringBootTest` to load the full Spring context against a real Postgres instance — `localhost:5435` locally (the dev Postgres from game-database Docker), and the GitHub Actions Postgres service container on the same port in CI.

**Flyway migrations** are applied via `DatabaseMigrationTestConfig` (`src/test/groovy/.../repositories/`), a `@Configuration` class that Spring Boot picks up through its normal component scan. It defines a `Flyway` bean that calls `migrate()` before JPA initialises the `EntityManagerFactory`. The migrations come from `../game-database/migrations` (sibling checkout — in CI, `pr.yml` checks out `codenzasoft/game-database` as a sibling using the `REPO_ACCESS_TOKEN` secret).

> **Note:** `@ActiveProfiles` and `@TestPropertySource` are not reliably applied by `spock-spring:2.4-M7-groovy-5.0` with Spring Boot 4.0. Flyway configuration must go through the component-scan `@Configuration` bean, not through profile-specific YAML or test annotations.

Use `@Transactional` on the spec class so each feature method runs in a rolled-back transaction. Add a `setup()` block to delete rows in FK-safe order before each test — this keeps local runs clean when the dev database has existing data:

```groovy
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MyRepositorySpec extends Specification {

    @Autowired MyRepository myRepository
    @Autowired JdbcTemplate jdbcTemplate

    def setup() {
        jdbcTemplate.execute("DELETE FROM xo_game_moves")
        jdbcTemplate.execute("DELETE FROM xo_game_pieces")
        jdbcTemplate.execute("DELETE FROM game_players")
        jdbcTemplate.execute("DELETE FROM games")
    }
}
```

In CI, the GitHub Actions Postgres service starts with an empty database, so the `setup()` deletes are no-ops — but `@Transactional` rollback still ensures test isolation.

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
