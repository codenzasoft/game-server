# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew bootRun          # Start dev server on port 8082
./gradlew build            # Compile and package
./gradlew test             # Run tests
./gradlew spotlessApply    # Apply Google Java Format (runs automatically before compile)
./gradlew spotlessCheck    # Check formatting without applying
```

Spotless is wired as a pre-compile dependency (`compileJava.dependsOn('spotlessApply')`), so formatting is applied on every build.

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

### Schema management

JPA is configured with `hibernate.ddl-auto: update`. Schema evolves automatically; no migration tool is in use.

## Testing

JUnit 4 + Spock (Groovy) are on the test classpath. No tests exist yet. The Gradle test task is configured to log `passed`, `skipped`, and `failed` events.
