# King's Hand → Multiplayer — Plan V2 (post-immutability)

> **Status of this document.** This supersedes `KH-MULTIPLAYER-PLAN.md`, which was written *before*
> the immutable-state refactor and before its own slices 1–4 landed. Most of that plan is now **done**
> (see §1). This V2 is scoped to what actually remains to turn KH into a real, deployable
> human-vs-human game. No code has been changed to produce this plan — it is for review.

Audience: the engineer/agent picking this up next. Read §1 to see what already exists, §2 for the
one architectural decision that gates everything, then §3–§7 for the work in dependency order.

---

## 1. Where things actually stand (much further than the old plan says)

The immutable refactor (`docs/kingshand-immutable-refactor.md`) landed, and it quietly unlocked the
hard parts of multiplayer. Verified in the current tree:

**Engine & state — done.**
- `GameState` is an immutable record: `players` (one `PlayerState` kingdom per seat), shared
  `TimeState`, `submittedSeats`, `resolutionPending`, plus `withPlayer`/`withTime`/`advanceTime`/
  `markSubmitted`/`allSubmitted`/`clearSubmitted`/`winnerSeat`/`isMatchOver`
  (`GameState.java:19`).
- **The readiness barrier is built.** Every seat-scoped action (`pickCard`, `skipAdd`, `endAndRun`,
  `acceptCouncilProposal`, …) calls `submitActiveSeat()`, which marks the seat and only runs
  `resolvePhaseAndAdvance()` once **all live seats** have submitted — advancing the shared clock
  exactly once and reseeding every seat (`GameEngine.java:495`, `:509`). Single-seat parity is
  preserved (one seat ⇒ barrier releases immediately).
- **Bots have agency.** `KhPlayerStrategy` + `KhBeamSearchStrategy`; the engine binds a strategy per
  bot seat and `driveBots()` auto-plays + auto-submits them after every phase seed, so the barrier
  never stalls on a bot (`GameEngine.java:625`, `buildKhGame` at `GameService.java:579`).
- **Sabotage / cross-seat combat is built.** `resolvePlayForAllSeats()` snapshots every kingdom,
  resolves each table against the *frozen* opponent snapshot, then applies collected cross-seat
  deltas in a second pass — including the STRENGTH-as-shield combat rule in `applyIncomingDelta`
  (`GameEngine.java:841`, `:896`). `OpponentResourceTarget`/`SabotageContext`/`Effect.isAttack()`
  exist.
- **Forfeit** releases the barrier for the remaining seat (`GameEngine.java:342`).

**Transport & views — done.**
- Per-seat broadcasting with hidden information: `KhBroadcastService` sends each human a
  `KhMatchView.forSeat(state, seat)` — **own kingdom full, opponents redacted** — and skips bot seats
  (`KhBroadcastService.java:69`, `KhMatchView.java:34`, `KhKingdomView.full/redacted`).
- Full WS surface incl. the multiplayer-specific `/play/continue` acknowledge-resolution handler
  (`KhController.java:194`).
- `recordKhGamesPlayed` already records win/loss per human seat via `winnerSeat()`
  (`GameService.java:774`).

**Frontend — substantially present.** `ui/src/kh/` has `multiplayerOverlays.ts`, `playPhaseScene.ts`,
the "waiting for other player" / dual-table resolution UX, reading `players[context.seat]` as "you".

**Net:** the old plan's Slices 1 (barrier), 2 (bot), 3 (sabotage), 4 (outcome), and most of 6
(frontend) are effectively **complete**. What is *not* done is everything that makes it a real match
between **two humans that survives a production deploy** — Slice 5 (human-vs-human) and the
"persistence gap" the old plan flagged as an afterthought. Those are the whole job now.

---

## 2. The gating decision: where does live match state live? ★ read first

This is the crux and it reorders everything else. Today:

- `GameCache` is a plain in-memory `HashMap<UUID, Game<?>>` (`GameCache.java:14`).
- `buildKhGame` **rebuilds a fresh `GameState.newMultiplayer(...)` on every cache miss** — it never
  loads mid-game progress (`GameService.java:563`). There is no `kh_game_state` column; KH persists
  only the seat roster (`KhGameEntity` carries players, nothing else).
- Production runs on Cloud Run with `--min-instances=0 --max-instances=2` (`deploy.yml:59`).

For a solo-vs-bot game a cache miss just meant "start over," which nobody noticed. For **two humans**
it is fatal in two independent ways:

1. **Eviction / restart / scale-to-zero loses the match.** `GameCleanupService` and any redeploy or
   idle scale-down drops the in-memory engine; the next `/load` silently resets both kingdoms to
   year 1.
2. **Two replicas = two divergent games.** With `max-instances=2`, the two humans' WebSocket
   connections can land on different Cloud Run instances. Each instance builds its *own* in-memory
   `GameEngine` from the same DB row and they never see each other's moves. The engine's
   `synchronized` methods only serialize within one JVM.

The immutable refactor is what makes the fix cheap: **`GameState` is now a pure, immutable record
graph** (`Deck`/`SeasonState`/`ResourceState`/`SeatedAdvisor`/`TimeState` all records), so it
serializes to/from JSON with no hand-written cloning or mutation hazards. Snapshotting the whole
match is now `objectMapper.writeValueAsString(state)`.

Two viable strategies — **decide before writing code:**

| | **A. Persist state + single owning instance (recommended v1)** | **B. Persist state + stateless per-action load/save** |
|---|---|---|
| Storage | `kh_game_state` JSONB column; write the whole `GameState` after each `resolvePhaseAndAdvance` (and each submit) | same |
| Concurrency | One instance owns a live game at a time; pin KH matches to a single replica | No in-memory engine kept; each WS action loads state, applies one move under optimistic lock (`version` column), saves |
| Cloud Run | `max-instances=1` for now, **or** Cloud Run *session affinity* + accept the residual multi-user race | Any number of replicas; DB is the source of truth |
| Effort | Low — add a column + serialize/deserialize in `buildKhGame`/after-resolve; one infra flag | High — restructure the engine's `synchronized`/in-memory assumptions, add optimistic-lock retry, re-drive bots from loaded state |
| Correctness | Solid as long as one instance owns the game; the barrier + `synchronized` still hold | Solid across replicas, but every action becomes a load-mutate-save transaction |

**Decided: A.** Ship persistence + single-instance ownership first (a direct, low-risk payoff of
immutability that unblocks a real two-human match); B (stateless per-action load/save) is the
scale-out follow-up only if volume ever needs >1 replica. Note session affinity on Cloud Run is
*per-client cookie* — it does **not** guarantee two different users hit the same instance — so the
honest interim under A is `max-instances=1` (or a small dedicated service), not affinity.

Everything below assumes **A**.

---

## 3. Slice A — Persist live match state (the unblock)

**Goal:** a KH match survives cache eviction, redeploy, and scale-to-zero.

- **Schema (game-database repo):** new migration adding a nullable `kh_game_state JSONB` (and a
  `version INT` for later use) to the KH game table. Follow the Flyway `V##__...sql` convention;
  KH currently has *no* KH-specific migration, so this is the first.
- **Entity:** add the field to `KhGameEntity` (or a child table) with a JSON converter.
- **Write path:** after `resolvePhaseAndAdvance()` resolves a phase (and, to be safe, after every
  `submitActiveSeat()`), serialize `engine.getState()` and persist. Cleanest seam: a lifecycle/observer
  hook (KH already registers `GameLifecycleListener` + `KhObserver`) or an explicit
  `GameService.saveKhState(engine)` the controller pipeline calls after `notifyUpdated()`.
- **Read path:** in `buildKhGame`, if the entity has saved state, `deserialize → GameState` and
  `GameEngine.Builder.withState(...)` **instead of** `GameState.newMultiplayer(...)`; only seed a
  fresh state when the column is null (first build). Re-bind bot strategies exactly as today so
  `driveBots()` resumes correctly from the loaded phase.
- **Serialization guardrails:**
  - Round-trip test: `state → JSON → state` equals the original for a mid-game fixture (mid-PLAY with
    `submittedSeats` populated, a seated advisor mid-proposal, non-empty resolution logs).
  - Jackson config: records serialize fine, but `Deck`/`SeasonState`/`Map<Season,SeasonState>` and any
    `Card`/`Advisor` subtyping need explicit checking. **Decided: embed the full card & advisor
    objects** (not ids) — Jackson walks the record graph directly, no catalog lookup on load. Verify
    the round-trip below covers boosted/upgraded table cards, since an engine-boosted card differs
    from its catalog original and must survive save/load byte-for-equal.
  - **Accepted consequence:** saved cards are frozen at save time; a `CardCatalog` retune does not
    reach matches already in flight. Not a correctness issue — just expected behavior to document.
  - The engine's `activeSeat` and `SabotageContext` are per-request scratch — **do not** persist them.

**Risk:** low. The one thing to nail in the round-trip test is boosted/upgraded cards and mid-proposal
advisor counters, since those are the state that differs from the catalog defaults.

---

## 4. Slice B — Human-vs-human creation, lobby & join

**Goal:** two people can get into the same match by invite link, while keeping the one-click vs-bot
game. **Decided: invite-by-link only, mirror Hearts; no public lobby.**

**Mirror Hearts exactly** — it already has the two-path shape you want:
- vs-bots: `createHeartsGame` (`GameService.java:197`), one-click via `IndexController:89` /
  `GamesApiController:40`.
- human multiplayer: `createPendingHeartsGame` (host only, no bots — `:453`) → `joinHeartsGame`
  (`:471`) → `startHeartsGame`; MVC at `IndexController:118`, API at
  `GamesApiController:57` ("creates a pending game … other players join").

Apply the same to KH:
- **Keep `createKhGame` as the vs-bot path.** It already seats creator + one bot
  (`GameService.java:174`); that becomes the "Play the AI" button, unchanged.
- **Add `createPendingKhGame(user)`** — seats only the creator at seat 0, **no bot**, leaves seat 1
  open (mirror `createPendingHeartsGame`).
- **Add `joinKhGame(gameId, user)`** — seats a second human at seat 1; reject if full / already
  started / self-join (mirror `joinHeartsGame`).
- **Start on fill.** Since KH is exactly 2 seats, filling seat 1 can start immediately (build +
  `initialize()` + broadcast first offers to both) — no separate host-clicks-start needed (simpler
  than Hearts, which waits for 4). Confirm you don't want a host "start" button anyway.
- **Invite link = the game-page URL** for the pending game. Host is redirected there on create (as
  Hearts does); the friend opens the shared link, which routes through `joinKhGame`. No lobby list,
  no discovery.
- **MVC + API routing.** New pending-create + join endpoints alongside the existing vs-bot one
  (`IndexController:144`, and `GamesApiController` if the client uses the API path).

**Gotcha:** don't fork the gameplay path for "2 humans" vs "human + bot." Seat resolution, the
barrier, and per-seat broadcast are already seat-count-generic — this is purely a *game-creation*
concern, exactly as the code comments promise (`GameService.java:177`). A human-vs-human game simply
has no strategy bound to seat 1, so `driveBots()` no-ops there (verify).

---

## 5. Slice C — Disconnect, timeout & reconnect

**Goal:** the barrier can't hang forever on a human, and a dropped player can come back.

With two humans the readiness barrier has a new failure mode the bot path never hit: **a human who
disconnects mid-phase never submits**, so `resolvePhaseAndAdvance()` never fires and the other player
is stuck on "waiting for other player…" indefinitely.

- **Reconnect (mostly free).** `/kh/{id}/load` already re-pushes per-seat state
  (`KhController.java:38`), and with Slice A the state is durable — a returning player resyncs
  automatically. Verify the client re-subscribes and re-renders mid-phase (incl. the resolution
  screen and `submittedSeats`).
- **Per-phase timer — 30s (decided).** Each phase gets a 30-second deadline. On expiry the engine
  auto-submits a safe default for the seat that hasn't acted — **skip ADD / end PLAY with the current
  board / pass COUNCIL** — reusing the same submit path bots use, which releases the barrier and
  advances the clock. Implementation notes:
  - The timer is a per-game scheduled task keyed to the current phase; reset it on every phase change
    and cancel it when all seats submit early. It must run on the owning instance (ties to §2 —
    single-instance ownership makes this trivial; a bare `ScheduledExecutorService` per live game is
    enough, no distributed scheduler needed).
  - Send the deadline to the client (e.g. a `phaseDeadline` timestamp on `KhMatchView`) so it can show
    a countdown; the server clock is authoritative for the actual auto-submit.
  - Decide whether the timer applies to bot-only phases too (harmless — bots already submit instantly,
    so the timer just never fires).
- **Disconnect → auto-forfeit after grace (decided).** Detect WS disconnect via STOMP session events;
  start a grace window (suggest ~30–60s, tunable) during which the player can reconnect and resume
  from durable state. If they don't return, `forfeit(seat)` ends the match for the remaining player
  (`GameEngine.java:342`). This is distinct from the 30s phase timer: the phase timer keeps a
  *present-but-slow* player's match moving; the grace-then-forfeit handles a *gone* player.

---

## 6. Slice D — Match lifecycle & hardening

- **Rematch.** KH has no `again()` equivalent (XO/Hearts do). Add if you want post-game rematch
  between the same two humans.
- **Tie-breaking.** `winnerSeat()` returns the first victorious seat by index — revisit for fairness
  when both complete a Great Work on the same resolved round (old plan §3 flagged this; still open).
- **Abandonment cleanup.** `GameCleanupService` evicts completed games; make sure a pending
  human-vs-human game that never fills, and an abandoned mid-match, both get reaped without nuking a
  live one.
- **Anti-abuse.** `enforceGameLimit` already caps active games per user; confirm it counts pending
  invites so a user can't spawn unlimited open lobbies.

---

## 7. Slice E — Frontend for two humans

The multiplayer resolution UX largely exists (`multiplayerOverlays.ts`). Remaining, driven by the new
server states:

- **Invite + join** (Slice B): a "share this link / waiting for opponent to join" pre-game state on
  the host side; the friend's link routes straight into the match. No lobby list to build.
- **30s phase countdown** (Slice C): a visible timer on your own turn, driven by the `phaseDeadline`
  the server sends; auto-advance when it hits zero.
- **Disconnect surfacing** (Slice C): "opponent disconnected — waiting…" with the grace countdown.
- Confirm the client reads the durable-resume state cleanly after a mid-match reload.

Coordinate with whoever owns the TS client; the wire shape (`KhMatchView`) is stable and already
seat-relative.

---

## 8. Recommended order & effort

1. **Slice A — persist state** (small, high-value; the unblock). *Do this first.*
2. **Infra: pin KH to one instance** (`max-instances=1` or a dedicated path) — one-line, gates §2.
3. **Slice B — human-vs-human lobby/join** (medium; mirror XO). *This is "multiplayer" to a user.*
4. **Slice C — timeout/disconnect/reconnect** (medium; required before real players touch it).
5. **Slice E — frontend lobby/reconnect** (parallel with B/C).
6. **Slice D — rematch/tie-break/cleanup** (polish).
7. **(Later) Slice B-scaleout** — stateless per-action load/save with optimistic locking, only if
   you outgrow a single instance.

## 9. Decisions (locked)

1. **§2 architecture — A (persist state + single owning instance).** In-memory `synchronized`
   engine stays; state is made durable and KH is pinned to one instance. B (stateless per-action
   load/save) is the scale-out follow-up only if concurrent volume ever needs >1 replica. The DB
   persistence itself is a follow-on implementation step (Slice A).
2. **Persisted blob — embed full card & advisor objects** (not ids). Jackson walks the record graph
   directly; simplest serializer, no catalog lookup on load.
   > **Accepted tradeoff:** a match's saved cards are frozen at save time, so retuning the
   > `CardCatalog`/`AdvisorCatalog` mid-match does **not** affect games already in flight — they keep
   > the stats they were saved with. Fine for correctness; just don't expect a live balance change to
   > reach an in-progress match. (If that ever bites, switching to id+rehydrate is a localized change
   > to the serializer.)
3. **Matchmaking — invite-by-link only, mirroring Hearts, keeping the vs-bot option.** No public
   lobby. KH gets a pending/join/start path like Hearts *and* retains today's one-click vs-bot game.
4. **Disconnect — auto-forfeit after a grace period.** A returning player resyncs from durable state
   (`/load`); if they don't return within the grace window, `forfeit(seat)` ends the match for the
   remaining player. (Bot-takeover deferred.)
5. **Turn timer — 30s per phase, auto-advance on expiry.** Each phase has a 30-second deadline; on
   expiry the engine auto-submits a safe default for the stalling seat (skip ADD / end PLAY with the
   current board / pass COUNCIL), releasing the barrier. Same auto-submit plumbing backs #4's timeout.

## 10. Verification (per slice)

```bash
./gradlew build                                   # compile + spotless + unit tests
./gradlew test --tests "*Kh*"                     # KH suite incl. barrier/sabotage specs
./gradlew test --tests "*.KhSimulationSpec"       # bot arena unchanged (no gameplay regression)
```

- **Slice A:** round-trip serialization spec; integration test that a built engine, saved and
  rebuilt, resumes at the same phase/clock/`submittedSeats`.
- **Slice B:** two-human create→join→first-offer flow; reject join-when-full/started/self.
- **Slice C:** barrier releases on timeout auto-submit; reconnect re-pushes correct per-seat view.
- **Manual:** two browsers, two accounts, one match through several seasons incl. a sabotage
  exchange and a mid-match reload of each client.
