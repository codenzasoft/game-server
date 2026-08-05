# Plan: Immutable KingsHand State Refactor

> **Revised 2026-08-04.** The previous draft was written against older code and had several stale
> assumptions. Corrections made this pass, each detailed in its own section below:
>
> 1. **`Card.play()` already returns `PlayedCard`** (not `void`). It threads a resolution result
>    out today *while mutating `PlayerState` in place*. The real change is result-plus-state
>    threading, not `void → PlayerState`. See [Card.play](#cardplay--playedcard--playresult).
> 2. **`KhGameStateCloner` exists and must be addressed.** It is a hand-written deep-copy that
>    exists *only* because the state is mutable; immutability collapses it to the identity function.
>    `KhSimulator` and the beam search depend on it. See [Strategies](#strategies--the-cloner-collapses).
> 3. **`SeatedAdvisor`'s constructors** are a 3-arg defaulting ctor + a 9-arg restore ctor, not the
>    `(advisorId, hiredYear)` pair the old draft assumed.
> 4. **Only `KhKingdomView`/`KhMatchView` are serialized to the client** — `PlayerState`/`GameState`
>    are never sent directly. This bounds frontend risk to a handful of view getters and is why the
>    internal field reshaping is safe. See [Wire compatibility](#wire-compatibility).
> 5. **Keep the `Builder`s** rather than delete them. Tests (15 specs, ~4,500 lines) lean on the
>    fluent builders heavily; deleting `PlayerState.Builder` is high-churn for no runtime benefit.
>    See [Builders](#builders--keep-them).
> 6. **`ResourceState` is already an immutable record** with `with*`/`apply` — the target pattern is
>    established and proven; this refactor extends it to the remaining three types.

## Context

`PlayerState`, `GameState`, and `SeatedAdvisor` are mutable classes with in-place mutation of
`Map<Season, List<Card>>` inner lists, field-by-field setters, and mutable per-run advisor state.
`ResourceState`, `Card`, `Effect`, `TimeState`, `CouncilProposal`, `Advisor`, and `PlayedCard` are
already immutable records — the Hearts game and `ResourceState` both use the `with*(…)` factory
pattern this refactor brings to the three remaining mutable types.

The concrete goals:

1. Replace the three `Map<Season, List<Card>>` fields (`board`/`drawPile`/`discardPile`) with a
   single `Map<Season, SeasonState>`, where `SeasonState` groups the three per-season decks.
2. Replace raw `List<Card>` card collections with a `Deck` record providing a domain API
   (`shuffle(Random)`, `drawTop(int)`, structural `add`/`remove`/`replace`).
3. Make `PlayerState`, `GameState`, and `SeatedAdvisor` immutable records; all mutation expressed as
   `with*(…)` methods returning new instances.
4. Thread the new immutable state through `GameEngine`, `Card.play()`, and the strategy/simulation
   layer, and collapse the now-redundant `KhGameStateCloner`.

### What is *not* changing

- **`SabotageContext` stays a mutable scratch accumulator.** It carries a `List<Effect>` sink that
  `Card.play()` writes to via `collect(effect)` during a single PLAY resolution. Like the engine's
  `activeSeat`, it is per-request bookkeeping, not observable match state — making it immutable buys
  nothing and complicates the two-phase sabotage apply. Leave it as-is.
- **The JSON wire shape.** `PlayerState`/`GameState` are never serialized to the client (see
  [Wire compatibility](#wire-compatibility)); the internal reshaping is invisible to the frontend.

## New Classes

### `Deck` record
**File:** `src/main/java/com/codenzasoft/games/kingshand/Deck.java`

```java
public record Deck(List<Card> cards) {
    public Deck { cards = List.copyOf(cards); }   // compact ctor — defensive, unmodifiable copy

    public static Deck empty() { return new Deck(List.of()); }
    public static Deck of(List<Card> cards) { return new Deck(cards); }
    public static Deck of(Card... cards) { return new Deck(List.of(cards)); }

    // Structural factory methods (return new Deck)
    public Deck add(Card card) { ... }
    public Deck addAll(List<Card> cards) { ... }
    public Deck remove(Card card) { ... }          // first occurrence
    public Deck removeAt(int index) { ... }
    public Deck replace(Card from, Card to) { ... } // first occurrence; throw if absent (mirrors
                                                     // today's replaceTableCard contract)

    public Deck shuffle(Random rng) { /* Collections.shuffle on a mutable copy */ }

    public record DrawResult(List<Card> drawn, Deck remaining) {}
    public DrawResult drawTop(int count) { /* split first `count` off cards */ }

    // Query — mirror the List-based helpers PlayerState/GameEngine use today
    public boolean contains(Card card) { ... }          // by name, see note
    public Optional<Card> findByName(String name) { ... }
    public int indexOf(Card card) { ... }
    public List<Card> subListAfter(Card card) { ... }   // backs getCardsOnBoardAfter
    public int size() { ... }
    public boolean isEmpty() { ... }
    public Stream<Card> stream() { ... }
    public List<Card> asList() { return cards; }        // already unmodifiable
}
```

**Name-vs-identity matching:** several current call sites match cards by `name()` rather than record
equality (`tableContains`, `findUpgradeTarget`, `isCardInPlay`) because an engine-boosted table card
differs from its hand original. Keep those name-based semantics: give `Deck` a name-based
`findByName`/`containsName`, and keep record-equality `contains`/`indexOf` for the
`replaceTableCard`/`getCardsOnBoardAfter` paths, which today use `List.indexOf` on the exact card
instance. **Preserve each call site's existing matching mode** — do not silently unify them.

`addChoices` (`List<Card>`), `councilOfferChoices` (`List<Advisor>`), and `resolutionLog`
(`List<ResolutionStep>`) stay as plain lists — transient phase data, not decks.

---

### `SeasonState` record
**File:** `src/main/java/com/codenzasoft/games/kingshand/SeasonState.java`

```java
public record SeasonState(Deck board, Deck drawPile, Deck discardPile) {
    public static SeasonState empty() {
        return new SeasonState(Deck.empty(), Deck.empty(), Deck.empty());
    }

    public SeasonState withBoard(Deck board) { ... }
    public SeasonState withDrawPile(Deck drawPile) { ... }
    public SeasonState withDiscardPile(Deck discardPile) { ... }

    // Convenience delegates matching today's PlayerState table ops
    public SeasonState addToBoard(Card card) { return withBoard(board.add(card)); }
    public SeasonState removeFromBoard(Card card) { return withBoard(board.remove(card)); }
    public SeasonState removeFromBoardAt(int slot) { return withBoard(board.removeAt(slot)); }
    public SeasonState replaceOnBoard(Card from, Card to) { return withBoard(board.replace(from, to)); }
    public SeasonState discardFromBoard(Card card) {           // remove from board, add to discard
        return withBoard(board.remove(card)).withDiscardPile(discardPile.add(card));
    }
    public SeasonState discardHand(Deck hand) {                // append hand cards to discard
        return withDiscardPile(discardPile.addAll(hand.asList()));
    }
}
```

---

## Classes to Convert to Records

### `SeatedAdvisor` — class → record
**File:** `src/main/java/com/codenzasoft/games/kingshand/SeatedAdvisor.java`

**Correction from prior draft.** The current class does *not* have a `(advisorId, hiredYear)`
constructor. It has:

- a 3-arg defaulting constructor `SeatedAdvisor(String advisorId, Card card, int hiredYear)` that
  initialises the six mutable counters to their defaults (`reformStacksTaken=0`, `decreeTaken=false`,
  `lastProposalYear=-1`, the three bonus counters `=0`), and
- a 9-arg "restore" constructor taking every field (used by tests and, post-refactor, the clone path).

Target record — 9 components, all currently present as fields:

```java
public record SeatedAdvisor(
    String advisorId, Card card, int reformStacksTaken, boolean decreeTaken,
    int lastProposalYear, int bonusFreeRerolls, int bonusExtraAddCandidates,
    double bonusCrossTableBoost, int hiredYear) {

    // Replaces the 3-arg defaulting constructor.
    public static SeatedAdvisor of(String advisorId, Card card, int hiredYear) {
        return new SeatedAdvisor(advisorId, card, 0, false, -1, 0, 0, 0.0, hiredYear);
    }
}
```

The 9-arg record canonical constructor already covers the current "restore" constructor call sites
verbatim — no change needed there.

Replace the 7 mutators with `with*`:

| Old method | New method |
|---|---|
| `setCard(Card)` (3 call sites) | `withCard(Card)` |
| `incrementReformStacksTaken()` | `withReformStacksTaken(reformStacksTaken + 1)` |
| `setDecreeTaken(boolean)` | `withDecreeTaken(boolean)` |
| `setLastProposalYear(int)` (2 call sites) | `withLastProposalYear(int)` |
| `addBonusFreeRerolls(int d)` | `withBonusFreeRerolls(bonusFreeRerolls + d)` |
| `addBonusExtraAddCandidates(int d)` | `withBonusExtraAddCandidates(bonusExtraAddCandidates + d)` |
| `addBonusCrossTableBoost(double d)` | `withBonusCrossTableBoost(bonusCrossTableBoost + d)` |

Because a `SeatedAdvisor` now lives inside `PlayerState.council` (a `List<SeatedAdvisor>`), updating
one advisor means rebuilding the list and the owning `PlayerState`. Add a
`PlayerState.updateAdvisor(String advisorId, UnaryOperator<SeatedAdvisor>)` helper (below) so engine
call sites don't open-code the list rebuild. Getter names change from `getX()` to the record
accessors `x()`; update the ~8 read sites accordingly (mechanical).

---

### `PlayerState` — class → record
**File:** `src/main/java/com/codenzasoft/games/kingshand/PlayerState.java`

**Record components:**

| Component | Type | Replaces |
|---|---|---|
| `seasons` | `Map<Season, SeasonState>` | `board`, `drawPile`, `discardPile` (three maps) |
| `hand` | `Deck` | `List<Card> hand` |
| `resources` | `ResourceState` | unchanged (already immutable) |
| `addChoices` | `List<Card>` | unchanged (transient) |
| `rerollCount` | `int` | unchanged |
| `resolutionLog` | `List<ResolutionStep>` | unchanged |
| `council` | `List<SeatedAdvisor>` | unchanged (elements now immutable records) |
| `councilOfferChoices` | `List<Advisor>` | unchanged |
| `currentProposal` | `CouncilProposal` | unchanged (already immutable) |
| `permanentFreeRerolls` | `int` | unchanged |
| `acceleratedGreatWorkNames` | `Set<String>` | unchanged |
| `forfeited` | `boolean` | **now a real component** (today it's a field the `Builder` never sets) |

> Note: `forfeited` is currently a plain field toggled by `forfeit()` and is *not* wired through the
> `Builder`/`private PlayerState(Builder)` constructor. When it becomes a record component, make sure
> `newPlayer()` / the builder default it to `false` and that `withForfeited(true)` replaces
> `forfeit()`.

**Compact constructor** defensively copies every collection (`Map.copyOf`, `List.copyOf`,
`Set.copyOf`); `Deck`/`SeasonState` already copy internally.

**Replace the ~20 mutators with `with*`**, keeping method *names* close to today's to bound
`GameEngine` churn. Group the season-delegating ones as thin wrappers over `SeasonState`:

```java
// Season access / update
public SeasonState season(Season s) { return seasons.get(s); }
public PlayerState withSeason(Season s, SeasonState ss) { /* copy map, put, new PlayerState */ }

// Table shortcuts (same names as today's mutators, now returning a new PlayerState)
public PlayerState addToTable(Season s, Card c) { return withSeason(s, season(s).addToBoard(c)); }
public PlayerState removeFromTable(Season s, Card c) { return withSeason(s, season(s).removeFromBoard(c)); }
public PlayerState removeFromTableAt(Season s, int slot) { return withSeason(s, season(s).removeFromBoardAt(slot)); }
public PlayerState replaceTableCard(Season s, Card from, Card to) { return withSeason(s, season(s).replaceOnBoard(from, to)); }
public PlayerState discardFromTable(Season s, Card c) { return withSeason(s, season(s).discardFromBoard(c)); }
public PlayerState discardHand(Season s) { return withHand(Deck.empty()).withSeason(s, season(s).discardHand(hand)); }

// Read helpers stay (now delegating to Deck) — getTable, tableContains, findTableCard,
// findUpgradeTarget, getCardsOnBoardAfter, isCardInPlay, getTableStaffing, isVictoryComplete.
// KEEP getTable(Season) returning List<Card> — KhKingdomView and staffing use it.

// Hand / resources
public PlayerState withHand(Deck hand) { ... }
public PlayerState withResources(ResourceState r) { ... }
public PlayerState apply(Effect e) { return withResources(resources.apply(e)); }
public PlayerState consumeFood(double r) { return withResources(resources.consumeFood(r)); }
public PlayerState addProduction(double v) { return withResources(resources.addProduction(v)); }
public PlayerState addGold(double v) { return withResources(resources.addGold(v)); }
public PlayerState addPopulationCap(double v) { return withResources(resources.addPopulationCap(v)); }
public PlayerState unlock(Card c) { return withResources(resources.unlock(c)); }

// ADD-phase offer state
public PlayerState withAddChoices(List<Card> c) { ... }
public PlayerState withRerollCount(int n) { ... }
public PlayerState withResolutionLog(List<ResolutionStep> l) { ... }

// Council
public PlayerState withCouncil(List<SeatedAdvisor> c) { ... }
public PlayerState addToCouncil(SeatedAdvisor a) { ... }
public PlayerState removeFromCouncil(String advisorId) { ... }
public PlayerState updateAdvisor(String advisorId, UnaryOperator<SeatedAdvisor> fn) { ... }
public PlayerState withCouncilOfferChoices(List<Advisor> c) { ... }
public PlayerState withCurrentProposal(CouncilProposal p) { ... }
public PlayerState withPermanentFreeRerolls(int n) { ... }
public PlayerState incrementPermanentFreeRerolls(int d) { return withPermanentFreeRerolls(permanentFreeRerolls + d); }
public PlayerState markGreatWorkAccelerated(String name) { /* copy set, add, new PlayerState */ }
public PlayerState withForfeited(boolean f) { ... }   // replaces forfeit()
```

**The `Builder` stays** (see [Builders](#builders--keep-them)); its `build()` now assembles the
record. The internal per-season `Map<Season, List<Card>>` builder fields fold into building a
`Map<Season, SeasonState>` (or keep the three builder maps and assemble `SeasonState`s in `build()`
— the latter is less churn to the builder's fluent `table()/drawPile()/discardPile()` methods).

---

### `GameState` — class → record
**File:** `src/main/java/com/codenzasoft/games/kingshand/GameState.java`

**Record components:** `List<PlayerState> players`, `TimeState time`, `Set<Integer> submittedSeats`,
`boolean resolutionPending`.

Replace the mutators (accurate list — the class has **more than the 4 the old draft named**):

| Old (mutating) | New (`with*`) |
|---|---|
| `advanceTime()` (reads player resources, sets `time`) | `advanceTime()` → returns new `GameState` |
| `markSubmitted(int)` | `markSubmitted(int)` → new `Set` + new record |
| `clearSubmitted()` | `clearSubmitted()` → new record with empty set |
| `setResolutionPending(boolean)` | `withResolutionPending(boolean)` |
| *(new)* | `withPlayer(int seat, PlayerState p)` — replace element at seat |
| *(new)* | `withTime(TimeState)` |

All the read-only outcome methods (`winnerSeat`, `victoryType`, `isMatchOver`, `allSubmitted`,
`isPlayerOut`, `isPlayerVictorious`, `isSubmitted`, `getSubmittedSeats`, `playerCount`,
`getPlayer`) stay unchanged as record instance methods.

**Static factories stay:** `newState()`, `newMultiplayer(int)`, `newTwoPlayerState()`,
`singlePlayer(...)`, `multiPlayer(...)` — all return the record. `advanceTime()` keeps its current
"council opens if *any* seat has GOLD unlocked" logic, now returning a new record instead of
assigning `this.time`.

**The `Builder` stays** (see [Builders](#builders--keep-them)) — it delegates to
`PlayerState.Builder` and is used across the test suite.

---

## `Card.play` — `PlayedCard` → `PlayResult`

**Correction from prior draft.** `Card.play()` is *already* non-`void`: all three overloads return
`PlayedCard` (a result record: `card, tier, fired, delta, boosted`). What they do *wrong* for
immutability is mutate the passed `PlayerState` in place — `state.apply(effect)`,
`state.replaceTableCard(...)`, `state.unlock(this)`.

The refactor must return **both** the resolution result *and* the updated kingdom. Introduce a small
pairing record and thread it:

```java
public record PlayResult(PlayerState state, PlayedCard played) {}

// Before (mutates state, returns only the result):
public PlayedCard play(PlayerState state, Season season, StaffingTier tier, SabotageContext sabotage, List<Card> targets) {
    state.apply(effect);                       // in-place
    state.replaceTableCard(season, card, boostedCard);
    ...
    return new PlayedCard(this, tier, true, state.getResources().getDelta(before), boostedCards);
}

// After (threads state, returns both):
public PlayResult play(PlayerState state, Season season, StaffingTier tier, SabotageContext sabotage, List<Card> targets) {
    final ResourceState before = state.getResources();
    ...
    state = state.apply(effect);               // reassign local
    state = state.replaceTableCard(season, card, boostedCard);
    ...
    PlayedCard played = new PlayedCard(this, tier, fired, state.getResources().getDelta(before), boostedCards);
    return new PlayResult(state, played);
}
```

All three overloads change return type identically. `SabotageContext.collect(effect)` stays as the
one in-place side effect (the deltas sink is intentionally mutable — see
[What is not changing](#what-is-not-changing)).

**Call-site impact:** `resolveSeat()` in `GameEngine` calls `card.play(...)` in three places (the
staffed table walk, the seated-advisor walk, and the cross-table advisor boost). Each becomes:

```java
PlayResult r = card.play(ps, season(), tier, sabotage);
ps = r.state();                 // thread the local PlayerState forward
log.add(r.played().getResolutionStep());
```

where `ps` is a local `PlayerState` seeded from `state.getPlayer(activeSeat)` at the top of
`resolveSeat` and written back once via `state = state.withPlayer(activeSeat, ps)` at the end. This
also fixes the current snapshot-vs-live subtlety cleanly, since the board walk reads from the
pre-resolution `snapshot` list while `ps` accumulates the changes.

---

## GameEngine Changes
**File:** `src/main/java/com/codenzasoft/games/kingshand/GameEngine.java` (~1,900 lines)

- **Remove `final` from the `state` field** (line ~178). Every action method that previously mutated
  `state` or a `PlayerState` now reassigns `this.state`.
- **Add a helper to bound churn.** Roughly **40** mutation call sites go through `activePlayer()`
  (32 `PlayerState` mutators + 8 `SeatedAdvisor` mutators). Add:

  ```java
  private void updateActivePlayer(UnaryOperator<PlayerState> fn) {
      this.state = state.withPlayer(activeSeat, fn.apply(activePlayer()));
  }
  ```

  Then e.g. `activePlayer().setAddChoices(choices)` becomes
  `updateActivePlayer(p -> p.withAddChoices(choices))`, and a seated-advisor tweak becomes
  `updateActivePlayer(p -> p.updateAdvisor(id, a -> a.withDecreeTaken(true)))`.
- **`resolveSeat(...)`** threads a local `PlayerState ps` through the three `card.play(...)` /
  `PlayResult` calls, the `consumeFood()` tail, and `setResolutionLog`, writing back once with
  `state = state.withPlayer(activeSeat, ps)`.
- **`resolvePlayForAllSeats()` / `applyIncomingDelta(...)`** — `applyIncomingDelta` currently mutates
  `victim` in place. Change it to `PlayerState applyIncomingDelta(PlayerState victim, Effect delta)`
  returning the updated kingdom; the phase-two loop reassigns per victim seat via `withPlayer`.
- **GameState-level mutations** (`advanceTime`, `markSubmitted`, `clearSubmitted`,
  `setResolutionPending` — 11 call sites) become `this.state = state.advanceTime()` etc.
- **`activeSeat` stays a mutable `int` field** — per-request bookkeeping, not observable state.

---

## Strategies — the cloner collapses
**Files:** `src/main/java/com/codenzasoft/games/kingshand/strategies/`

**Not mentioned in the prior draft, and material.** `KhGameStateCloner.deepCopy(GameState)` /
`deepCopy(PlayerState)` is a hand-written deep copy that exists *solely* because the engine mutates
state in place — the search (`KhBeamSearchStrategy`, `KhSimulator`, `KhStateEvaluator`,
`KhStateProjection`) needs throwaway copies. Its own javadoc says so.

Once `GameState`/`PlayerState`/`SeatedAdvisor` are immutable, a "copy" is just the same reference:

- Reduce `KhGameStateCloner.deepCopy(...)` to `return src;` (identity), **or** delete the class and
  inline the removal at its call sites.
- **`KhSimulator`** is the only caller of the cloner (plus the class itself). Update it to run the
  engine against the live immutable `GameState` and keep the pre-action state simply by holding the
  old reference — no copy needed, since actions now produce new instances.
- Re-check `KhGameStateCloner`'s field-by-field `SeatedAdvisor` copy — it becomes dead and should be
  removed with the class.

Recommendation: **keep a one-line `deepCopy` shim returning `src`** for the first landing to minimise
call-site churn and keep the diff reviewable, then delete it in a follow-up once the search code is
confirmed copy-free. Flag the shim with a `// TODO: immutable — copy no longer needed` so it isn't
mistaken for load-bearing.

---

## Wire compatibility

**Only `KhMatchView` → `KhKingdomView` are serialized to the client** (via `KhBroadcastService`);
`PlayerState` and `GameState` are never sent directly, so the `@JsonProperty` annotations on them are
not client-facing and the internal field reshaping is invisible to the frontend.

The one place to keep stable is `KhKingdomView`'s public getters, which the JSON wire shape depends
on. They currently return:

- `getBoard()` → `Map<Season, List<Card>>` (from `player.getBoard()`)
- `getDrawPile()` / `getDiscardPile()` → `Map<Season, List<Card>>`
- `getHand()` → `List<Card>`
- `getTableStaffingBySeason()` uses `player.getTable(season)`

Keep every one of these return types identical, converting from the new `Map<Season, SeasonState>` /
`Deck` internally (e.g. `getBoard()` maps each `SeasonState` to its `board().asList()`). This is the
anti-corruption boundary — do the shape translation here and the TypeScript client needs no change.
`KhMatchView` needs no structural change (it already goes through record accessors).

---

## Builders — keep them

The prior draft called for deleting `PlayerState.Builder`. **Don't.** The test suite (15 KingsHand
specs, ~4,500 lines; 9 files use `PlayerState.builder()` / `GameState.builder()` / `new
SeatedAdvisor(...)`) leans on the fluent builders for readable fixtures, and `GameState.Builder`
delegates every per-player setter to `PlayerState.Builder`. Deleting them forces a large, mechanical
test rewrite for zero runtime benefit — records and builders coexist fine (the builder's `build()`
just constructs the immutable record).

Plan:

- Keep `PlayerState.Builder` and `GameState.Builder`; have `build()` assemble the records.
- Add the `with*` methods for the engine's *runtime* mutation path (that's what immutability is for).
- Tests that call *mutators* (`addToTable`, `setCard`, `advanceTime`, `apply`, `setResolutionPending`
  — in `GameStateSpec`, `GameEngineBarrierSpec`, `EffectSpec`, `GameEngineSpec`, `PlayerStateSpec`)
  must switch to the `with*` equivalents and capture the returned instance, since the old calls no
  longer compile. That's unavoidable and is the bulk of the test churn — but construction via
  builders is preserved.

---

## Implementation Order (dependency-first)

1. **`Deck`** — no deps. Unit-test the structural + `drawTop`/`shuffle` API.
2. **`SeasonState`** — depends on `Deck`.
3. **`SeatedAdvisor`** class → record (+ `of(...)` factory, `with*`). Update ~8 read sites and
   the `KhGameStateCloner` field copy (temporarily).
4. **`PlayerState`** class → record — `seasons`/`hand`/`forfeited` reshape, `with*` methods, keep
   `Builder` and read helpers.
5. **`PlayResult` + `Card.play`** return-type change (all three overloads).
6. **`GameState`** class → record — `with*`, keep `Builder`, static factories return the record.
7. **`GameEngine`** — drop `final` on `state`; add `updateActivePlayer`; thread `resolveSeat` /
   `resolvePlayForAllSeats` / `applyIncomingDelta`; convert all ~40 mutation call sites and 11
   GameState-level ones.
8. **Strategies** — collapse `KhGameStateCloner.deepCopy` to identity (shim); make `KhSimulator`
   copy-free.
9. **Spring layer** — verify `KhKingdomView` getters keep their return shapes, converting from
   `SeasonState`/`Deck` internally.
10. **Tests** — switch mutator calls to `with*` equivalents; builders otherwise unchanged.

---

## Verification

```bash
./gradlew build                                 # compile + spotless + unit tests
./gradlew test --tests "*.KhSimulationSpec"     # Monte Carlo: no crashes, survival year >= baseline
./gradlew test --tests "*Kh*" --tests "*.kingshand.*"
```

- **Regression guard for the cloner collapse:** run the bot arena / simulation before and after
  step 8 and confirm identical aggregate survival statistics — a broken thread-through in
  `resolveSeat` shows up as changed survival years or lost card effects.
- **Manual:** start a single-player KingsHand game in the browser; play ADD → PLAY → COUNCIL for at
  least 2 seasons; confirm resource deltas, the resolution log, and card upgrades render correctly,
  and that a two-player match still resolves sabotage/combat symmetrically.
