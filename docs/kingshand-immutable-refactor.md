# Plan: Immutable KingsHand State Refactor

## Context

`PlayerState` and `GameState` are mutable classes with in-place mutation of `Map<Season, List<Card>>` inner lists, field-by-field setters, and a mutable `SeatedAdvisor` class. The Hearts game uses Java records with `with*(…)` factory methods throughout — this refactor brings KingsHand up to the same pattern. The concrete goals are:

1. Replace `Map<Season, List<Card>> board/drawPile/discardPile` with a new `SeasonState` record that groups all three per-season collections.
2. Replace raw `List<Card>` card collections (board, drawPile, discardPile, hand) with a new `Deck` record that provides a rich domain API including `shuffle(Random)` and `drawTop(int)`.
3. Make `PlayerState`, `GameState`, and `SeatedAdvisor` immutable Java records; all mutation expressed as `with*(…)` methods returning new instances.

## New Classes

### `Deck` record
**File:** `src/main/java/com/codenzasoft/games/kingshand/Deck.java`

```java
public record Deck(List<Card> cards) {
    // Compact constructor — defensive copy
    public Deck { cards = List.copyOf(cards); }

    public static Deck empty() { return new Deck(List.of()); }
    public static Deck of(List<Card> cards) { return new Deck(cards); }
    public static Deck of(Card... cards) { return new Deck(List.of(cards)); }

    // Structural factory methods (return new Deck)
    public Deck add(Card card) { ... }
    public Deck addAll(List<Card> cards) { ... }
    public Deck remove(Card card) { ... }
    public Deck removeAt(int index) { ... }
    public Deck replace(Card from, Card to) { ... }

    // Rich domain API
    public Deck shuffle(Random rng) { /* Collections.shuffle on copy */ }

    public record DrawResult(List<Card> drawn, Deck remaining) {}
    public DrawResult drawTop(int count) { /* split first `count` off cards */ }

    // Query
    public boolean contains(Card card) { ... }
    public Optional<Card> findByName(String name) { ... }
    public int size() { ... }
    public boolean isEmpty() { ... }
    public Stream<Card> stream() { ... }
}
```

`addChoices` and `councilOfferChoices` stay as `List<Card>` / `List<Advisor>` (transient phase data, not decks).

---

### `SeasonState` record
**File:** `src/main/java/com/codenzasoft/games/kingshand/SeasonState.java`

```java
public record SeasonState(Deck board, Deck drawPile, Deck discardPile) {
    public static SeasonState empty() {
        return new SeasonState(Deck.empty(), Deck.empty(), Deck.empty());
    }

    // with* factory methods
    public SeasonState withBoard(Deck board) { ... }
    public SeasonState withDrawPile(Deck drawPile) { ... }
    public SeasonState withDiscardPile(Deck discardPile) { ... }

    // Convenience delegates
    public SeasonState addToBoard(Card card) { return withBoard(board.add(card)); }
    public SeasonState removeFromBoard(Card card) { return withBoard(board.remove(card)); }
    public SeasonState replaceOnBoard(Card from, Card to) { return withBoard(board.replace(from, to)); }
    public SeasonState discardFromBoard(Card card) { ... } // remove from board, add to discardPile
    public SeasonState discardHand(Deck hand) { ... }     // adds hand cards to discardPile

    // Query
    public boolean boardContains(Card card) { return board.contains(card); }
    public Optional<Card> findBoardCard(String name) { return board.findByName(name); }
}
```

---

## Classes to Convert to Records

### `SeatedAdvisor` — class → record
**File:** `src/main/java/com/codenzasoft/games/kingshand/SeatedAdvisor.java`

All 9 existing fields become record components. Replace the 7 setter/mutator methods with `with*` equivalents:

| Old method | New method |
|---|---|
| `setCard(Card)` | `withCard(Card)` |
| `incrementReformStacksTaken()` | `withReformStacksTaken(reformStacksTaken + 1)` |
| `setDecreeTaken(boolean)` | `withDecreeTaken(boolean)` |
| `setLastProposalYear(int)` | `withLastProposalYear(int)` |
| `addBonusFreeRerolls(int)` | `withBonusFreeRerolls(bonusFreeRerolls + delta)` |
| `addBonusExtraAddCandidates(int)` | `withBonusExtraAddCandidates(bonusExtraAddCandidates + delta)` |
| `addBonusCrossTableBoost(double)` | `withBonusCrossTableBoost(bonusCrossTableBoost + delta)` |

The named static factory currently used as a constructor (`SeatedAdvisor(advisorId, hiredYear)`) becomes a static `of(String advisorId, int hiredYear)` method that initialises defaults.

---

### `PlayerState` — class → record
**File:** `src/main/java/com/codenzasoft/games/kingshand/PlayerState.java`

**Record components:**

| Component | Type | Replaces |
|---|---|---|
| `seasons` | `Map<Season, SeasonState>` | `board`, `drawPile`, `discardPile` (three `Map<Season, List<Card>>`) |
| `hand` | `Deck` | `List<Card> hand` |
| `resources` | `ResourceState` | unchanged |
| `addChoices` | `List<Card>` | unchanged (transient, stays List) |
| `rerollCount` | `int` | unchanged |
| `resolutionLog` | `List<ResolutionStep>` | unchanged |
| `council` | `List<SeatedAdvisor>` | unchanged (elements now immutable records) |
| `councilOfferChoices` | `List<Advisor>` | unchanged |
| `currentProposal` | `CouncilProposal` | unchanged |
| `permanentFreeRerolls` | `int` | unchanged |
| `acceleratedGreatWorkNames` | `Set<String>` | unchanged |
| `forfeited` | `boolean` | unchanged |

Compact constructor defensively copies all collections.

**Replacing the 20 mutation methods:** Each becomes a `with*(…)` method returning a new `PlayerState`. Group the season-delegating ones as thin wrappers:

```java
// Season delegate
public SeasonState getSeason(Season s) { return seasons.get(s); }
public PlayerState withSeason(Season s, SeasonState ss) { /* rebuild map */ }

// Table shortcuts (delegate to withSeason + SeasonState)
public PlayerState addToTable(Season s, Card c) { return withSeason(s, getSeason(s).addToBoard(c)); }
public PlayerState removeFromTable(Season s, Card c) { return withSeason(s, getSeason(s).removeFromBoard(c)); }
// …etc — keep same method names to minimise call-site churn in GameEngine

// Hand
public PlayerState withHand(Deck hand) { ... }
public PlayerState discardHand(Season season) { ... } // clears hand, adds to season discardPile

// Resources
public PlayerState withResources(ResourceState r) { ... }
public PlayerState apply(Effect e) { return withResources(resources.apply(e)); }
// …etc

// Council
public PlayerState withCouncil(List<SeatedAdvisor> council) { ... }
public PlayerState addToCouncil(SeatedAdvisor a) { ... }
public PlayerState removeFromCouncil(String advisorId) { ... }
public PlayerState updateAdvisor(String advisorId, UnaryOperator<SeatedAdvisor> fn) { ... }
```

The `PlayerState.Builder` inner class is removed; replace constructor call sites with static factory methods:

```java
public static PlayerState newKingdom(Map<Season, SeasonState> seasons) { /* all other fields default */ }
```

---

### `GameState` — class → record
**File:** `src/main/java/com/codenzasoft/games/kingshand/GameState.java`

**Record components:** `List<PlayerState> players`, `TimeState time`, `Set<Integer> submittedSeats`, `boolean resolutionPending`.

Replace the 4 mutation methods with `with*` equivalents:

```java
public GameState withTime(TimeState time) { ... }
public GameState withPlayer(int seat, PlayerState player) { ... }   // replaces element at index
public GameState markSubmitted(int seat) { ... }    // returns new Set + new record
public GameState clearSubmitted() { ... }
public GameState withResolutionPending(boolean pending) { ... }
public GameState advanceTime() { ... }              // reads player resources, returns new GameState
```

Static factories `newState()`, `newMultiplayer(int)`, etc. stay but return the new record.

---

## Classes with Signature Changes

### `Card.play()` — void → PlayerState
**File:** `src/main/java/com/codenzasoft/games/kingshand/Card.java`

```java
// Before
public void play(PlayerState ps, Season season, StaffingTier tier) { ps.addToTable(...); ... }

// After
public PlayerState play(PlayerState ps, Season season, StaffingTier tier) {
    ps = ps.addToTable(season, ...);
    ...
    return ps;
}
```

Same change for the overload that takes `SabotageContext`.

---

## GameEngine Changes
**File:** `src/main/java/com/codenzasoft/games/kingshand/GameEngine.java`

- Remove `final` from `state` field.
- Every action method that previously called a mutating method on `state` or a `PlayerState` now reassigns `this.state`:

```java
// Before
playerState.setAddChoices(choices);

// After
this.state = state.withPlayer(seat, playerState.withAddChoices(choices));
```

- `resolvePlayForAllSeats()` threads `PlayerState` through sequential `card.play()` calls:

```java
PlayerState ps = state.getPlayer(seat);
for (Card card : ps.getSeason(season).board().asList()) {
    ps = card.play(ps, season, staffing);
}
this.state = state.withPlayer(seat, ps);
```

- `activeSeat` stays mutable (it's local per-request bookkeeping, not observable state).

---

## Spring Layer Updates

| Class | Change |
|---|---|
| `KhKingdomView` | `playerState.getTable(season)` → `playerState.getSeason(season).board()` (or add a `getBoard(Season)` delegate on `PlayerState`). `getDrawPile(season)` → `getSeason(season).drawPile()`. |
| `KhMatchView` | No change to structure; `GameState.players()` is now a record accessor. |
| `KhBroadcastService` | No change — receives `GameState` from engine, passes to `KhMatchView`. |

---

## Implementation Order (dependency-first)

1. **`Deck`** — no deps
2. **`SeasonState`** — depends on `Deck`
3. **`SeatedAdvisor`** — depends on `Card`, `Advisor`
4. **`PlayerState`** — depends on `SeasonState`, `Deck`, `SeatedAdvisor`
5. **`Card.play()` return type** — depends on new `PlayerState`
6. **`GameState`** — depends on `PlayerState`, `TimeState`
7. **`GameEngine`** — update all action methods to replace `this.state`
8. **Spring layer** (`KhKingdomView`) — accessor name updates
9. **Tests** — update `PlayerState.Builder` usages to the new record constructor / static factory

---

## Verification

```bash
./gradlew build                    # compile + spotless + unit tests
./gradlew test --tests "*.KhSimulationSpec"   # Monte Carlo: verify no crashes, survival year ≥ prior baseline
```

Manual: start a singleplayer KingsHand game via the browser, play through ADD → PLAY → COUNCIL phases for at least 2 seasons, confirm resource deltas and card resolution log display correctly.
