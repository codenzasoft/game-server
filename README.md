# game-server

The KingsHand game server — a Spring Boot application serving KingsHand (a Hearts card game variant) and Tic-Tac-Toe.

## Prerequisites

- Java 17+
- Node.js (for the frontend build)
- A running PostgreSQL instance (provided by [game-database](https://github.com/codenzasoft/game-database))

## Local Setup

### 1. Start the database

Clone [game-database](https://github.com/codenzasoft/game-database) and start PostgreSQL:

```shell
git clone https://github.com/codenzasoft/game-database.git
cd game-database
docker compose up -d
```

This starts PostgreSQL on port 5435 and runs all Flyway migrations automatically.

### 2. Start the server

```shell
./gradlew bootRun
```

The server starts on port 8082. Open [http://localhost:8082](http://localhost:8082) and log in with:

- **Username:** `admin`
- **Password:** `admin`

> Change the admin password or create a personal account before exposing this server to the public internet.

## Frontend Development (with HMR)

For faster UI development with hot module replacement, run the Vite dev server alongside Spring Boot:

```shell
# Terminal 1 — Spring Boot
./gradlew bootRun

# Terminal 2 — Vite dev server (port 8085)
cd ui && npm run dev
```

Access the app at [http://localhost:8085](http://localhost:8085).

## Other Build Commands

```shell
./gradlew build           # Compile and package
./gradlew test            # Run tests
./gradlew spotlessApply   # Apply Google Java Format
```

## License

This project is licensed under the [Business Source License 1.1](LICENSE). It converts to Apache 2.0 on 2030-10-09.
