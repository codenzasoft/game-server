# King's Hand → two-player: pass 3 (observer + controller seat routing)

Follows pass 2 (seat-aware engine). This pass makes broadcasting engine-driven via the observer and
routes each WS request to the acting player's seat, and gives every game two seated identities.

## What changed

**`KhObserver` gained `gameUpdated(engine, state)`** (alongside `gameStarted`/`gameOver`), with a
matching `KhGameUpdatedNotification`. The engine exposes `notifyUpdated()` which fires it.

**`KhBroadcastService` is now a `KhObserver`.** It implements all three callbacks and broadcasts
**per seat**: it loops the engine's seat-ordered players, resolves each to a `UserEntity`, skips
bots, and sends that human a `KhMessage`. `KhContext` gained a `seat` field so the client knows which
entry in `players[]` is "you". It's registered on each engine in `buildKhGame` via `.addListener(...)`,
so pushes are driven by the engine (started → updated after every action → over) rather than by the
controller.

**Engine holds seat-ordered identities.** `GameEngine` now stores `List<Player> players` (was a
single `Player`), with `withPlayers(...)` on the builder and `seatOf(username)` to map an
authenticated request to a seat. The single-`Player` constructors are retained (they wrap into a
one-element list), so existing tests are unaffected.

**`KhController` resolves the seat per request.** Every action handler now takes the `Principal` and
goes through one `act(gameId, principal, op, action)` helper that: resolves the user → seat via
`seatOf`, `setActiveSeat(seat)`, runs the action on that kingdom, then calls `notifyUpdated()` so the
observer broadcasts. The old per-handler `khBroadcastService.gameLoaded(game)` calls are gone (only
`load` still pulls a broadcast directly).

**Two seated players per game.** `createKhGame` now seats the creator (seat 0) plus a bot (seat 1),
and `buildKhGame` maps all entity players to seat-ordered identities and builds one kingdom per seat.
Seat resolution and per-seat broadcast are generic, so swapping the bot for a second human is purely
a game-creation change.

## Tests

- `GameEngineNotificationSpec`: added a `notifyUpdated` → `gameUpdated` test. Existing
  `0 * listener.gameStarted/gameOver` assertions are unaffected (they're method-specific and
  `gameUpdated` never fires in those cases).
- `GameEngineSpec`: added a `seatOf`/`getPlayers` (two-identity) test.
- `GameServiceSpec`: updated the two KH-creation tests to expect **two** players (user at seat 0, bot
  at seat 1) instead of one.

## Verify locally (sandbox still can't build — JDK 11, Gradle/Maven blocked)

```bash
cd game-server
./gradlew spotlessApply
./gradlew build
```

Watch for:
1. **Bots in the test DB.** `createKhGame` now calls `getBots(1)`. `GameServiceSpec` is an
   integration test; `getBots` creates a bot if none exist (same path Hearts uses), so this should be
   fine, but if the KH-creation tests fail with "Not enough bots", that's why.
2. **Method-reference-as-`Consumer`.** Handlers like `GameEngine::offerAddChoices` (returns a value)
   are passed where `Consumer<GameEngine>` is expected — legal in Java (return discarded). If a
   compiler is stricter than expected, wrap as `g -> g.offerAddChoices()`.
3. **`KhContext` arity.** Now `(player, gameId, seat)`; only `KhBroadcastService` constructs it. Any
   client/JSON test asserting the old 2-arg shape needs updating.
4. **Wire shape** (from pass 2) is still `{ players:[...], time, matchOver, winnerSeat }`, now with a
   per-recipient `context.seat`. The client must read `players[context.seat]` as "you".

## Still needed for human-vs-human / vs-bot play

1. **Drive seat 1.** The bot at seat 1 has an identity but no AI and never sends WS messages, so its
   turns don't happen yet. Add a KH bot strategy (mirror Hearts' `AdaptiveStrategy`) invoked to act on
   seat 1, or a human-join flow so a second person occupies seat 1.
2. **Readiness barrier + simultaneous resolution** (the "waiting for other player…" screen) before
   cross-seat sabotage.
3. **Human join / matchmaking** if you want two humans rather than a bot (a `/kh/{id}/join` handler +
   pending-game semantics).
4. **Outcome recording** from `winnerSeat()` (today `recordKhGamesPlayed` records a loss on
   starvation for humans only).
