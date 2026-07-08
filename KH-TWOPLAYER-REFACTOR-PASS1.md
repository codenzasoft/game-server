# King's Hand → two-player: structural pass 1

## What changed (2 files, no behavior change)

The old flat `GameState` was split into two classes:

- **`PlayerState`** (new) — everything that belongs to one player's kingdom: per-season
  `board`/`drawPile`/`discardPile`, `hand`, `ResourceState`, ADD-phase offer state, and the
  Council/Advisors state. It no longer owns the clock, so every season-dependent method now takes a
  `Season` argument (`getTable(season)`, `discardHand(season)`, `replaceTableCard(season, …)`,
  `getTableStaffing(season)`, `getCardsOnBoardAfter(season, card)`, etc.).

- **`GameState`** (rewritten) — now the **match**: holds `List<PlayerState> players` plus the shared
  `TimeState time`. `TimeState` lives here now (single authoritative clock), as decided.

### Why nothing else needed editing

`GameState` was rewritten as a **strict API superset** of the old flat class. Every method the old
`GameState` exposed still exists, delegating to the active player (`primary()`, seat 0) and resolving
"the current season" from the shared `time`. So `GameEngine`, `Card.play`, the KH notifications,
`KhMessage`, `KhBroadcastService`, `GameService`, and the entire Spock suite compile against the same
surface. New additions: `getPlayers()`, `getPlayer(int)`, `primary()`, `GameState.singlePlayer(...)`,
and `PRIMARY_SEAT`.

## Wire format

Backward compatible. All previously-flat properties (`resources`, `board`, `hand`, `time`,
`tableStaffing`, `gameOver`, `gameWon`, …) are still emitted for the active player, so the existing
single-player client keeps working. Two fields are **added**: the `time` object (unchanged shape) and
a new `players` array (each element is a `PlayerState`, which repeats that player's kingdom fields).
Nothing was removed. If any client does strict/exact JSON parsing, teach it to ignore `players` for
now.

## Verify locally (this sandbox can't build — JDK 11 only, Gradle + Maven blocked)

```bash
cd game-server
./gradlew spotlessApply   # both new files follow google-java-format; this normalizes anything off
./gradlew build           # compiles main + runs the Spock suite
```

Expected: green. If anything fails, the most likely spots are:

1. A caller I didn't anticipate that used a *removed* member — but the match is a superset, so this
   should be empty. Grep to be sure: `grep -rn "\.getTime()\|advanceTime()" src` — both still exist
   on `GameState`.
2. `spotless` formatting nits in the two new files (cosmetic; `spotlessApply` fixes).
3. Serialization tests asserting the *exact* JSON of a KH state — those would now see the extra
   `players` key. Search: `grep -rn "players\|writeValueAsString\|KhMessage" src/test`.

## Behavior notes / decisions carried forward

- Validation messages and their per-field triggers are preserved
  (`missing current season/phase`, `… cannot be negative`, `year cannot be negative`). Only internal
  check *ordering* shifted slightly (year is validated before population now); no single-field spec is
  affected.
- **Council-after-Winter rule for two players** (you chose "either player unlocked"): NOT wired yet.
  `GameState.advanceTime()` still consults `primary()`'s unlocks only. This is the first thing to
  implement when the second seat goes in — see `advanceTime()`'s comment.

## Next slices (not in this pass)

1. Second seat: populate `players` with two kingdoms; make the engine seat-aware (the delegators stay
   valid as "active player" accessors, but PLAY/ADD/COUNCIL actions need a seat argument).
2. Readiness barrier ("waiting for other player…") + simultaneous dual-table resolution.
3. Council-after-Winter = "either player unlocked GOLD".
4. Match-level win/forfeit/give-up across both players.
5. Bot AI (copy the Hearts strategy pattern) and persistence for two kingdoms.
