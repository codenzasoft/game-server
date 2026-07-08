# King's Hand → two-player: pass 2 (seat-aware engine)

Follows pass 1 (the `PlayerState` / match-`GameState` split). This pass removes the seat-0 facade so
`GameState` genuinely manages both kingdoms and the engine acts on a chosen seat.

## What changed

**`GameState` is now a real match container, not a facade.** All the per-player delegating accessors
(`getResources()`, `getBoard()`, `addToTable(card)`, `getCurrentTable()`, …) are **gone**. It now
exposes only:
- structure: `getPlayers()`, `getPlayer(seat)`, `playerCount()`
- shared clock: `getTime()`, `advanceTime()`
- match outcome: `isPlayerOut(seat)`, `isPlayerVictorious(seat)`, `isMatchOver()`, `winnerSeat()`

Per-kingdom operations live on `PlayerState` and are reached via `getPlayer(seat)`.

**Engine is seat-aware.** `GameEngine` gained an `activeSeat` (default 0) with `setActiveSeat(seat)`
/ `getActiveSeat()`. Internally every per-player call now goes through `activePlayer()` (=
`state.getPlayer(activeSeat)`) with the season taken from the shared clock via `season()`. Public
action signatures are unchanged, so existing single-kingdom tests operate on seat 0 exactly as
before. `synchronized` methods make a WS request's set-seat-then-act atomic.

**`Card.play`** now takes `(PlayerState state, Season season, StaffingTier tier)` instead of a
`GameState` — it operates on one kingdom's table for a given season.

**Council rule for two players.** `TimeState.advance(boolean goldUnlocked)` added (old
`advance(ResourceState)` delegates to it). `GameState.advanceTime()` opens the Council after Winter
when **either** kingdom has unlocked GOLD — the rule you chose.

**Two real kingdoms in live games.** `seedStartingDeck()` seeds *every* seat's draw piles, and
`GameService.buildKhGame()` now builds `GameState.newTwoPlayerState()` (seat 0 human, seat 1
opponent). The engine's `activeSeat` stays 0, so single-player play is unchanged; seat 1 is a real,
seeded kingdom awaiting a driver.

## Wire format — CLIENT CHANGE REQUIRED

`GameState` no longer emits the old flat properties. The KH state JSON is now:

```
{ "players": [ {kingdom0…}, {kingdom1…} ], "time": {…}, "matchOver": false, "winnerSeat": -1 }
```

Each element of `players` is a full kingdom (`resources`, `board`, `hand`, `drawPile`,
`discardPile`, `council`, `addChoices`, `tableStaffing` is NOT in here — see note, etc.). The client
must read `players[seat]` instead of top-level `resources`/`board`/`hand`/…. Note `tableStaffing`
and `getCurrentTable` were view-only computed props on the old flat state; if the client needs them
per seat, expose them explicitly next pass.

## Tests

- New **`PlayerStateSpec`** — the per-kingdom table/hand/pile tests, now season-parameterized.
- **`GameStateSpec`** trimmed to match-level: builder preconditions, two-kingdom structure, the
  either-gold council rule, and match outcome (knock-out → survivor wins).
- **`GameEngineSpec`** — reads routed through `getPlayer(0)` (single-kingdom tests still exercise
  seat 0); added a seat-1 test proving `setActiveSeat(1)` targets the right kingdom and an
  invalid-seat rejection test.
- Notification/lifecycle/Season/Effect specs untouched (they don't use the removed delegators).

## Verify locally (sandbox still can't build — JDK 11, Gradle/Maven blocked)

```bash
cd game-server
./gradlew spotlessApply
./gradlew build
```

Most-likely local nits: (1) a serialization test asserting exact KH JSON now sees the new shape —
update it to the `players[]` layout; (2) spotless formatting in the touched files; (3) any spot I
routed to `getPlayer(0)` that a test wanted at a different seat (search `getPlayer(0)` in
GameEngineSpec if a data assertion looks off).

## Still needed for a truly playable 2-player game (next slices)

1. **WS controller seat resolution** — `KhController` should map the authenticated user to their seat
   and call `engine.setActiveSeat(seat)` before each action. Today every request still hits seat 0.
2. **Drive seat 1** — offer ADD choices for seat 1 and run its turns. For "vs bot", a KH bot
   strategy (mirror the Hearts `AdaptiveStrategy` pattern) that plays seat 1.
3. **Readiness barrier + simultaneous resolution** — hold `endAndRun` until both seats submit
   ("waiting for other player…"), then resolve both tables against a pre-resolution snapshot
   (needed before cross-seat sabotage cards).
4. **Per-seat WS broadcast** — send each player their own view (both tables during resolution).
5. **Game creation & persistence** — `createKhGame` should add a bot (or a second human) to
   `game_players`; persist two kingdoms; record match outcome from `winnerSeat()`.
