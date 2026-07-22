# King's Hand multiplayer — implementation plan (handoff)

Audience: the engineer/agent picking this up next. This plan assumes the three refactor passes
already landed (see `KH-TWOPLAYER-REFACTOR-PASS1/2/3.md`). It lists what exists, the design
decisions already locked in, and the remaining slices in dependency order with concrete steps.

---

## 0. Where things stand

Data + engine + transport are seat-aware; **turn coordination and the second player's agency are
not built yet.**

Already done:
- `PlayerState` = one kingdom (season-parameterized table/pile methods). `GameState` = the match:
  `getPlayer(seat)`, `getPlayers()`, `playerCount()`, shared `getTime()`/`advanceTime()`
  (Council-after-Winter fires when **either** kingdom has GOLD), and outcome helpers
  `isPlayerOut(seat)`, `isPlayerVictorious(seat)`, `isMatchOver()`, `winnerSeat()`.
- `GameEngine` is seat-aware: `activeSeat` + `setActiveSeat`/`getActiveSeat`, private
  `activePlayer()`/`season()`, `seatOf(username)`, `notifyUpdated()`, seat-ordered `List<Player>`
  (`withPlayers`), and `seedStartingDeck()` seeds every seat.
- `Card.play(PlayerState, Season, StaffingTier)`.
- Broadcasting is observer-driven and **per-seat**: `KhObserver` has `gameStarted/gameUpdated/gameOver`;
  `KhBroadcastService implements KhObserver` and sends each human their own `KhMessage` tagged with
  `KhContext.seat`. `KhController` resolves the request's user → seat, `setActiveSeat`, runs the
  action, `notifyUpdated()`.
- `createKhGame` seats creator (0) + bot (1); `buildKhGame` builds one kingdom per seat and registers
  the broadcast observer.

**Prerequisite (Slice 0):** none of the above was compiled/tested in the authoring environment (no
JDK 17, no network for Gradle/Maven). First job: `./gradlew spotlessApply build` on a real machine and
fix compile/test nits. See the three PASS docs for the specific watch-items (bot in test DB, wire
shape, method-ref-as-Consumer).

### Locked design decisions (do not re-litigate)
- One shared clock (`TimeState` on `GameState`); one kingdom (`PlayerState`) per seat.
- Council after Winter opens if **either** kingdom unlocked GOLD.
- Engine acts on one seat at a time via `activeSeat`; `GameState` is not a seat-0 facade.
- Broadcasting lives in the observer, keyed by seat; bots receive nothing.
- Sabotage resolves against a **pre-resolution snapshot** of the opponent (see Slice 3).

---

## Slice 1 — Turn coordination: the readiness barrier  ★ core

This is the heart of the feature and everything else depends on it. It implements the user's UX:
"every PLAY, `endAndRun` doesn't fire until both players have selected it; show 'waiting for other
player…'; then a screen with both tables firing left-to-right at the same time."

### The central problem
The clock is **shared**, but each seat acts on its **own** board independently and asynchronously.
So the shared clock must not advance until *all* seats have finished the current phase. This is true
for PLAY (both must `endAndRun`) **and** for ADD (each picks their own card) and COUNCIL. Today
`pickCard`/`skipAdd`/`endAndRun`/`acceptCouncilProposal` each call `advanceTime()` immediately — that
is single-player behavior and must be decoupled.

### Approach
1. **Add per-seat readiness.** Track which seats have completed the current phase — e.g. a
   `Set<Integer> readySeats` on `GameState`, reset whenever the phase changes. Add
   `markReady(seat)`, `allReady()` (all non-forfeited seats ready), `clearReady()`.
2. **Split "I'm done with this phase" from "advance the clock."** Rename the seat-scoped completion
   intent (e.g. `submitEndAndRun(seat)`, `submitAdd(seat)`, `submitCouncil(seat)`) so it: performs
   that seat's phase work onto its own board, marks the seat ready, and **stops**. Only when
   `allReady()` does the engine run `resolvePhaseAndAdvance()` which advances the shared clock once
   and seeds the next phase for every seat.
3. **Resolution for PLAY** becomes: snapshot both kingdoms → for each seat resolve its table
   left-to-right (current `endAndRun` body minus the `advancePhase()` call, extracted to
   `resolveSeat(seat)`) → apply any cross-seat effects (Slice 3; no-op until then) →
   `state.advanceTime()` once → seed next ADD offer for every seat → fire `gameOver` if
   `isMatchOver()`.
4. **Broadcast states.** After a seat submits but before all are ready, broadcast a "waiting"
   view (add a lightweight flag to the wire, e.g. `submittedSeats` on `GameState`, so the client
   shows "waiting for other player…"). After `resolvePhaseAndAdvance`, broadcast the resolution view;
   the per-card `resolutionLog` already exists on each `PlayerState` to drive the dual-table
   animation.

### Files
- `GameState.java` — readiness set + helpers; expose `submittedSeats` for the wire.
- `GameEngine.java` — extract `resolveSeat(int)` from `endAndRun()`; add `submitEndAndRun(int)` and the
  `resolvePhaseAndAdvance()` gate; do the same decoupling for ADD/COUNCIL completion. Keep
  single-seat behavior working (with one seat, `allReady()` is immediate → identical to today).
- `KhController.java` — `/play/end` calls `submitEndAndRun(seat)`; broadcast happens via
  `notifyUpdated()` regardless (waiting vs resolved is encoded in state).

### Gotchas
- **Timeout / disconnect.** A never-submitting seat stalls the barrier forever. Decide: auto-submit
  after N seconds, auto-forfeit, or bot-takeover. At minimum, bot seats must auto-submit (Slice 2).
- **Single-seat parity.** With `playerCount()==1`, `allReady()` is immediate — verify every existing
  `GameEngineSpec` PLAY/ADD test still passes unchanged.
- **Clock advances exactly once** per resolved phase, not once per seat.

### Tests
- Two-seat: seat 0 submits → phase not advanced, seat 0 in `submittedSeats`, seat 1 not; seat 1
  submits → both tables resolved, clock advanced once, `submittedSeats` cleared.
- Single-seat parity suite (existing tests green).

---

## Slice 2 — Drive seat 1 (bot AI)

Seat 1 has an identity but no agency. Give it a strategy so a solo human can actually play vs bot.

### Approach
- Mirror the Hearts pattern: `HeartsGame.Builder.addPlayerObserver(player, strategy)` +
  `strategies/AdaptiveStrategy`. Add a `KhPlayerStrategy` interface and a heuristic implementation
  (`KhHeuristicStrategy`) that can decide every KH action: ADD (pick/reroll/skip), COUNCIL
  (hire/accept/pass/dismiss), PLAY (place cards, commission, then submit end-and-run).
- Register bot strategies in `buildKhGame` for seats whose `UserEntity.isBot()`.
- Trigger the bot: after any human action / phase seed, if a bot seat still owes an action in the
  current phase, have the engine drive it (`engine.setActiveSeat(botSeat)` → strategy picks →
  submit). In PLAY, the bot plays its board then `submitEndAndRun(botSeat)` so the barrier from
  Slice 1 releases.
- Start simple/greedy; strength can come later.

### Files
- New `kingshand/strategies/KhPlayerStrategy.java`, `KhHeuristicStrategy.java` (+ a `RandomStrategy`
  for tests).
- `GameEngine.java` — hold per-seat strategies; a `driveBots()` pass invoked after phase seeding and
  after each human submit.
- `GameService.buildKhGame` — register strategies for bot seats.

### Gotchas
- Don't branch gameplay on "is bot" beyond *who triggers the action*: bots go through the same
  seat-aware action methods as humans.
- Bot moves must fire `notifyUpdated()` too so humans see the opponent progress (respecting whatever
  hidden-info rules you want — see Slice 6 on per-seat views).

### Tests
- Engine test: with a bot at seat 1, after the human submits PLAY the bot auto-submits and the round
  resolves.

---

## Slice 3 — Sabotage cards (cross-seat effects)  — the payoff

Depends on Slice 1's snapshot resolution.

### Approach
- New effect targeting that reaches the opponent: e.g. an `OpponentResourceTarget` / cross-seat
  `CardTarget`, or an `EffectScope { SELF, OPPONENT }` on `Effect`.
- Resolution rule (locked): in `resolvePhaseAndAdvance`, snapshot every kingdom **before** any table
  resolves; each table resolves against the frozen opponent snapshot; collect cross-seat deltas and
  apply them after all self-resolution completes. This keeps simultaneous resolution deterministic
  and order-independent.
- Add a handful of sabotage cards to `CardCatalog` (e.g. "drain opponent production", "burn a random
  opponent table card").

### Files
- `Effect.java` / `EffectTarget.java` / `CardTarget.java` — cross-seat targeting.
- `GameEngine.resolvePhaseAndAdvance` — snapshot + two-phase apply.
- `CardCatalog.java` — new cards; `Card.play` gains access to the opponent snapshot (pass it in).

### Gotchas
- Never let a sabotage effect read a mid-resolution value — only the frozen snapshot.
- Decide tie-breaking if both players complete their Great Works on the same resolved round
  (`winnerSeat()` currently returns the first victorious seat by index — revisit for fairness).

---

## Slice 4 — Match outcome & lifecycle

- Fire `gameOver` when `isMatchOver()` (already fired on starvation; add the victory + last-standing
  paths from `winnerSeat()`).
- `recordKhGamesPlayed` (in `GameService`) should use `winnerSeat()` to record win/loss for each human
  seat, not just a starvation loss.
- Add an explicit forfeit/give-up path (the user's "gives up → forfeits" rule); `quit` already exists,
  extend it to set the match outcome for the remaining seat.

---

## Slice 5 — Human-vs-human (optional; only if not just vs bot)

Today seat 1 is always a bot. For two humans:
- `createKhGame` creates a pending game with just the creator; add `/kh/{id}/join` +
  `GameService.joinKhGame(id, user)` that seats a second human (max 2), mirroring Hearts' lobby/join.
- Start the match (seed + first offers) once seat 1 is filled.
- Seat resolution and per-seat broadcast already handle N humans, so this is a game-creation concern.

---

## Slice 6 — Frontend / client

The wire shape changed and the PLAY UX is new. (The server is ready; the client isn't in this repo —
coordinate with whoever owns it.)
- Read `players[context.seat]` as "you"; the other index is the opponent. New top-level fields:
  `time`, `matchOver`, `winnerSeat`, and (from Slice 1) `submittedSeats`.
- "Waiting for other player…" state when you've submitted but `submittedSeats` doesn't include all
  seats.
- The resolution screen: render **both** kingdoms' tables firing left-to-right simultaneously, driven
  by each `players[i].resolutionLog`.
- Consider per-seat filtered views (hide opponent hand/offers) — decide the hidden-info model before
  sabotage ships.

---

## Cross-cutting: persistence gap

KH does **not** persist mid-game state — `buildKhGame` rebuilds a fresh `GameState` every cache miss
(see the TODO in `GameEngine.initialize`). Single-player tolerated this; a real two-player match with
progress cannot lose state on a cache eviction. Before shipping, add state persistence for both
kingdoms + the clock + readiness (or pin the game in cache for its lifetime). Flag this early — it may
reorder the slices.

## Suggested order
0 (build green) → 1 (barrier) → 2 (bot) → 4 (outcome) → 3 (sabotage) → 6 (frontend) → 5 (human-vs-human)
→ persistence whenever it blocks a real deployment.
