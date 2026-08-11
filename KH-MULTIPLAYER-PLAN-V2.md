# King's Hand → Multiplayer — Step-by-step build plan

Concrete implementation steps in build order. Each step lists the files to touch, what to do, and how
to verify it. Do the steps in order; each phase ends green (`./gradlew build`) before the next starts.

**Decisions already locked** (don't re-litigate):
- **Persistence:** serialize the whole immutable `GameState` to a JSON column; **embed full card &
  advisor objects** (not ids). One owning instance — pin KH to `max-instances=1`.
- **Human-vs-human:** invite-by-link, mirroring Hearts' pending→join flow; **keep** the one-click
  vs-bot game. KH is exactly 2 seats, so filling seat 1 auto-starts (no host "start" button).
- **Disconnect:** auto-forfeit after a reconnect grace window.
- **Turn timer:** 30s per phase; on expiry auto-submit a safe default (skip ADD / end PLAY / pass
  COUNCIL).

Precedents to copy: Hearts persistence (`getOrBuildHeartsGame`, `GameService.java:407`; restore at
`:424`) and Hearts lobby (`createPendingHeartsGame`/`joinHeartsGame`/`startHeartsGame`, `:453`–`:518`).

---

## Phase 0 — Baseline green (prerequisite)

**0.1** Run `./gradlew spotlessApply build` and fix any compile/test nits so the tree is green before
touching anything. Confirm the KH suite passes:
```bash
./gradlew test --tests "*Kh*" --tests "*.kingshand.*"
```
**Verify:** build passes; note the KH test count as your regression baseline.

---

## Phase 1 — Persist live match state (Slice A)

Goal: a KH match survives cache eviction, redeploy, and scale-to-zero. This is the unblock — do it
first.

**1.1 — Migration (game-database repo).** Add `V16__Define_kh_game_state.sql` (V15 is the latest;
follow the `V##__Define_*` convention) creating a dedicated one-row-per-game table:
```sql
CREATE TABLE kh_game_states (
    game_id    UUID PRIMARY KEY REFERENCES games(id) ON DELETE CASCADE,
    state      JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
A separate table (not a column on `games`) keeps KH-only state out of the shared single-table
`games` rows, and the `ON DELETE CASCADE` means `GameCleanupService`'s existing bulk-delete of `games`
reaps the state row automatically — same pattern as V9's cascade for the XO child tables.
**Verify:** migration applies cleanly against local Postgres (`localhost:5435`).

**1.2 — Jackson round-trip proof (do this before wiring anything).** New Spock spec
`GameStateSerializationSpec`: build a mid-game `GameState` fixture (mid-PLAY with `submittedSeats`
populated, a seated advisor mid-proposal, a **boosted/upgraded** table card, non-empty resolution
logs), serialize with the app's `ObjectMapper`, deserialize, assert equality with the original.
**Verify:** the spec passes. This is the highest-risk part — boosted cards and advisor counters are
the state that differs from catalog defaults, so if anything round-trips lossily it shows here.

**1.3 — Entity + repository.** New `entities/kh/KhGameStateEntity` (a standalone `@Entity`, **not**
part of the `games` single-table inheritance):
- `@Id @Column(name = "game_id") UUID gameId;`
- `@Column(columnDefinition = "jsonb") String state;` (store the JSON string — conversion happens in
  the service so the `ObjectMapper` stays injectable/shared)
- `@Column(name = "updated_at") Instant updatedAt;` with `@PreUpdate`/`@PrePersist`.

New `repositories/kh/KhGameStateRepository extends CrudRepository<KhGameStateEntity, UUID>` (keyed by
`gameId`).
**Verify:** app boots; entity maps without Hibernate errors.

**1.4 — Persistence service.** New `services/kh/KhPersistenceService` (mirror the role of Hearts'
persistence service), backed by `KhGameStateRepository`:
- `void save(UUID gameId, GameState state)` — `objectMapper.writeValueAsString(state)`, upsert a
  `KhGameStateEntity(gameId, json)` via the repository.
- `Optional<GameState> reconstruct(UUID gameId)` — `findById(gameId)`; if present,
  `objectMapper.readValue(row.getState(), GameState.class)`.
- Inject the shared Spring `ObjectMapper`.
**Verify:** unit test save-then-reconstruct returns an equal `GameState`.

**1.5 — Write path.** Persist after every state change that must survive a crash. Cleanest seam: the
controller's shared action pipeline (`KhController.java:~254`, right after `game.notifyUpdated()`)
calls `khPersistenceService.save(gameId, game.getState())`. Also persist inside the `/load` path is
unnecessary (read-only). Bot moves already run through the engine before `notifyUpdated`, so a single
save after each pipeline run captures them.
**Verify:** play a few actions in the browser; confirm a `kh_game_states` row appears and its `state`
updates in Postgres after each move.

**1.6 — Read path.** In `GameService.buildKhGame` (`:557`), before seeding fresh, call
`khPersistenceService.reconstruct(entity.getId())`:
- if present → `GameEngine.Builder.withState(restored)` and **do not** re-seed
  (`engine.initialize()` currently seeds starting decks — split it so restore skips seeding but still
  registers observers, binds bot strategies, and calls `driveBots()` to resume). Model this on Hearts'
  `buildRestoredHeartsGame` + `resumePlay()` split (`GameService.java:426`).
- if absent → today's fresh path (`GameState.newMultiplayer(...)` + `initialize()`).
Re-bind bot strategies for bot seats exactly as today either way.
**Verify:** integration test — build an engine, advance it a couple of phases, save, evict from
`GameCache`, `getOrBuildKhGame` again, assert it resumes at the same phase/clock/`submittedSeats` (not
year 1).

**1.7 — Pin to one instance.** In `.github/workflows/deploy.yml:60`, set `--max-instances=1` for the
KH-serving service (leave a comment tying it to §2 of the design; revisit only if a stateless
per-action rewrite lands later).
**Verify:** deploy config diff only; no runtime check needed here.

**Phase 1 done when:** a match reloaded after a simulated restart resumes mid-game in the browser.

---

## Phase 2 — Human-vs-human invite & join (Slice B)

Goal: two people reach the same match by link; the vs-bot game still works untouched.

**2.1 — `createPendingKhGame(user)`** in `GameService` (mirror `createPendingHeartsGame`, `:453`):
seat only the creator (seat 0), **no bot**, save, return the entity. Leave `createKhGame` (`:174`)
exactly as-is — that stays the "Play the AI" path.
**Verify:** unit test — pending game has exactly one player, no bot.

**2.2 — `joinKhGame(gameId, user)`** in `GameService` (mirror `joinHeartsGame`, `:471`): load the
entity; reject if complete, if already 2 players, or if the joiner is the host; enforce game limit;
append the joiner as seat 1; save. Because KH is exactly 2 seats, seat 1 filling means the match is
ready — no separate start method (unlike Hearts' `startHeartsGame`).
**Verify:** unit tests — join fills seat 1; second join / self-join / completed-game join all rejected.

**2.3 — Start-on-fill.** After `joinKhGame` fills seat 1, `getOrBuildKhGame` should now build a real
2-human engine (both seats human, no strategy bound → `driveBots()` no-ops). Ensure
`getOrBuildKhGame` (`:384`) returns `empty` while the game is still pending (1 player), mirroring
Hearts' pending guard (`:415`), so a premature `/load` doesn't build a half-game.
**Verify:** unit test — `getOrBuildKhGame` is empty at 1 player, builds at 2.

**2.4 — Routing.** Add endpoints alongside the existing vs-bot one:
- MVC in `IndexController` (mirror the Hearts pair at `:82` vs `:118`): keep the vs-bot create; add a
  pending-create that redirects the host to the game page (the URL *is* the invite link), and a
  join route the invited link hits.
- If the client uses the API path, mirror `GamesApiController` Hearts endpoints (`:40` vs-bots vs
  `:57` pending).
**Verify:** manually hit the pending-create route → get a game URL; open it as a second account →
lands in the match.

**2.5 — Home/menu UI.** Two KH buttons: "Play the AI" (existing) and "Invite a friend" (pending
create). On pending create, show the host a "share this link — waiting for opponent…" state.
**Verify:** both buttons work from the home page.

**Phase 2 done when:** two browser sessions/accounts start a match via a shared link, and the vs-bot
game is unchanged.

---

## Phase 3 — 30s phase timer (Slice C, part 1)

Goal: a present-but-slow player can't stall the barrier.

**3.1 — Auto-submit helper in the engine.** Add `GameEngine.autoSubmit(int seat)` that performs the
phase-appropriate safe default for a seat that hasn't submitted the current phase: ADD → `skipAdd`,
PLAY → `endAndRun` with the current board, COUNCIL_ADD → `skipCouncilAdd`, COUNCIL_MEETING →
`passCouncilProposal`, resolution-pending → `continueResolution`. Reuse the existing seat-scoped
action bodies (same ones bots call); guard against a seat that already submitted.
**Verify:** engine test — a seat that never acts gets auto-submitted on demand and the barrier
releases.

**3.2 — Per-game scheduler.** Add a `ScheduledExecutorService`-backed 30s timer keyed to the current
phase, owned per live game (a `KhTurnTimerService` in the Spring layer, or a field on the engine
driven by the service). On phase change, `(re)schedule`; on all-submitted / phase resolve, `cancel`.
On fire: for each live human seat not yet in `submittedSeats`, call `autoSubmit(seat)`, then
`notifyUpdated()` + persist. Single owning instance (Phase 1.7) means a plain in-JVM scheduler is
sufficient — no distributed scheduler.
**Verify:** engine/service test with a shortened timeout — a seat that idles past the deadline is
auto-submitted and the clock advances.

**3.3 — Expose the deadline to the client.** Add `phaseDeadline` (epoch millis) to `KhMatchView`
(`views/kh/KhMatchView.java`), set when the phase timer is scheduled. Server clock is authoritative;
the field is display-only.
**Verify:** `/load` payload includes a future `phaseDeadline` during an active phase.

**Phase 3 done when:** in a 2-human match, one player going idle for 30s auto-advances the phase.

---

## Phase 4 — Disconnect → grace → auto-forfeit (Slice C, part 2)

Goal: a *gone* player ends the match instead of hanging it forever. (Distinct from Phase 3: that
handles slow-but-present; this handles absent.)

**4.1 — Presence tracking.** Listen for STOMP connect/disconnect events (`SessionConnectedEvent` /
`SessionDisconnectEvent`) in a `KhPresenceListener`; map principal → seat → game.
**Verify:** log shows connect/disconnect mapped to the right seat.

**4.2 — Grace-then-forfeit.** On a human seat's disconnect in a live 2-human game, start a grace
window (config `game.kh.disconnectGraceSeconds`, default ~45s). If the player reconnects (a new
`/load`, which already re-pushes durable per-seat state — `KhController.java:38`) cancel the timer. If
the window elapses, call `engine.forfeit(seat)` (`GameEngine.java:342`), `notifyUpdated()`, persist,
and run the normal game-over path (`recordKhGamesPlayed`).
**Verify:** integration test — disconnect a seat, don't reconnect, assert the match forfeits to the
remaining seat after the grace window; reconnect within the window and assert it resumes.

**Phase 4 done when:** closing one player's tab ends the match for the other after the grace window,
and reconnecting inside the window resumes cleanly.

---

## Phase 5 — Frontend for two humans (Slice E)

Wire the new server states into `ui/src/kh/`. The resolution/dual-table UX already exists
(`multiplayerOverlays.ts`).

- **5.1 Invite/join:** host "share this link / waiting for opponent…" pre-game state; the invited link
  routes straight into the match.
- **5.2 Phase countdown:** visible 30s timer driven by `phaseDeadline`; auto-advance visual when it
  hits zero.
- **5.3 Disconnect surfacing:** "opponent disconnected — waiting…" with the grace countdown.
- **5.4 Resume:** confirm a mid-match reload re-renders the correct phase (incl. resolution screen and
  `submittedSeats`).
**Verify:** two browsers play a full match through several seasons incl. a sabotage exchange, a
timeout, a disconnect, and a mid-match reload of each client.

---

## Phase 6 — Lifecycle polish (Slice D, optional)

- **6.1 Rematch:** add an `again()` equivalent for two humans (XO/Hearts have one; KH doesn't).
- **6.2 Tie-break:** `winnerSeat()` returns the first victorious seat by index — revisit for the case
  where both complete a Great Work on the same resolved round (`GameState.java:206`).
- **6.3 Cleanup:** ensure `GameCleanupService` reaps abandoned *pending* invites and abandoned live
  matches without evicting an active one.
- **6.4 Anti-abuse:** confirm `enforceGameLimit` counts pending invites so a user can't spawn unlimited
  open links.

---

## Verification (run per phase)

```bash
./gradlew build                                   # compile + spotless + unit tests
./gradlew test --tests "*Kh*"                     # KH suite incl. barrier/sabotage/serialization
./gradlew test --tests "*.KhSimulationSpec"       # bot arena — must be unchanged (no gameplay regression)
```

**Regression guard:** the bot-arena aggregate survival stats must be identical before and after
Phase 1 — a lossy serialize/restore shows up as changed survival years or lost card effects.

**Manual gate before deploy:** two accounts, two browsers, one match through several seasons including
a sabotage exchange, a 30s timeout, a disconnect+grace, and a mid-match reload of each client.
