# REST API

The REST API is used by the iOS client. All endpoints are under `/api/` and communicate in JSON.

## Authentication

Authentication uses Spring Session cookies. A successful `POST /api/auth/login` sets a `SESSION` cookie that must be included in all subsequent requests. iOS's `URLSession` handles this automatically via its cookie jar.

All protected endpoints return `401 Unauthorized` when the `SESSION` cookie is absent or expired.

---

### POST /api/auth/login

Authenticates the user and establishes a session.

**Request**
```json
{ "username": "alice", "password": "secret" }
```
`username` may be either the account username or email address.

**Response `200 OK`** — Sets `SESSION` cookie. Body is the authenticated user object.
```json
{
  "id": "a1b2c3d4-...",
  "userName": "alice",
  "email": "alice@example.com",
  "createdAt": "2025-01-01T00:00:00.000+00:00",
  "roles": [{ "role": "USER" }]
}
```

**Response `401 Unauthorized`** — Bad credentials or unverified email.
```json
{ "error": "Invalid credentials" }
```

---

### POST /api/auth/logout

Invalidates the current session. Requires authentication.

**Response `204 No Content`**

---

### GET /api/auth/me

Returns the authenticated user's profile. Useful for checking session validity on app startup.

**Response `200 OK`** — Same shape as the login response body.

**Response `401 Unauthorized`**

---

## Games

### GET /api/games

Returns the authenticated user's active games (not over, not quit), sorted newest first.

**Response `200 OK`**
```json
[
  {
    "id": "a1b2c3d4-...",
    "gameType": "HEARTS",
    "createdAt": "2025-01-01T00:00:00.000+00:00",
    "players": [
      { "id": "...", "userName": "alice" },
      { "id": "...", "userName": "Bot 1" }
    ]
  }
]
```

`gameType` is one of `HEARTS`, `XO`, `KH`.

**Response `401 Unauthorized`**

---

### POST /api/games/hearts

Creates a new Hearts game for the authenticated user against three bots.

**Response `201 Created`**
```json
{ "id": "a1b2c3d4-..." }
```

Connect to the WebSocket with this id to start playing (see [WebSocket Protocol](#websocket-protocol)).

**Response `409 Conflict`** — User is at the active game limit.
```json
{ "error": "..." }
```

**Response `401 Unauthorized`**

---

### POST /api/games/hearts/multiplayer

Creates a pending Hearts lobby for the authenticated user. Other players join by connecting to the game's WebSocket and sending a `load` message.

**Response `201 Created`**
```json
{ "id": "a1b2c3d4-..." }
```

**Response `409 Conflict`** — User is at the active game limit.

**Response `401 Unauthorized`**

---

### DELETE /api/games/{id}

Quits and removes the identified game. The authenticated user must be a player in the game.

**Response `204 No Content`**

**Response `403 Forbidden`** — User is not a player in the game.

**Response `401 Unauthorized`**

---

## WebSocket Protocol

Game state is delivered over a STOMP WebSocket. The connection endpoint is `/games/connect`. The session cookie from `/api/auth/login` authenticates the upgrade request.

Configure the STOMP client with `heartbeatIncoming: 25000, heartbeatOutgoing: 25000` to keep the connection alive.

### Hearts

After connecting, subscribe to `/user/queue/games/{gameId}`, then publish to `/games/hearts/{gameId}/load` to receive the initial state.

| Client → Server destination | Payload | Description |
|---|---|---|
| `/games/hearts/{id}/load` | _(empty)_ | Join lobby or request initial game state |
| `/games/hearts/{id}/start` | _(empty)_ | Start the game (fills empty seats with bots) |
| `/games/hearts/{id}/pass` | `{ "cards": [...] }` | Submit three cards to pass |
| `/games/hearts/{id}/play` | `{ "suit": "HEARTS", "rank": "ACE" }` | Play a card |
| `/games/hearts/{id}/again` | _(empty)_ | Request a rematch after game over |
| `/games/hearts/{id}/quit` | _(empty)_ | Forfeit the game |

The server pushes updates to `/user/queue/games/{gameId}`.
