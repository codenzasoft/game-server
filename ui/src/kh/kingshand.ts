import { z } from "zod";
import { Client, Frame, StompSubscription } from "@stomp/stompjs";

// ─── URLs (resolved at build time from env or from the current origin) ────────

const wsUrl =
  import.meta.env.VITE_WS_URL ||
  `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/games/connect`;
const loginUrl =
  import.meta.env.VITE_LOGIN_URL || `${window.location.origin}/login`;
const homeUrl = `${window.location.origin}/`;

// ─── Enums ────────────────────────────────────────────────────────────────────
//
// These mirror the Java server enums exactly.  TypeScript string enums are used
// so that z.enum(Season) works without a separate allowlist and so serialised
// values are human-readable in logs.

export enum Season {
  SPRING = "SPRING",
  SUMMER = "SUMMER",
  FALL = "FALL",
  WINTER = "WINTER",
}

export enum Phase {
  ADD = "ADD",
  RANDOM_EVENT = "RANDOM_EVENT",
  PLAY = "PLAY",
}

export enum FrequencyType {
  SINGLE = "SINGLE",
  RECURRING = "RECURRING",
}

export enum ValueType {
  INTEGER = "INTEGER",
  PERCENTAGE = "PERCENTAGE",
}

// ResourceTarget: one branch of the EffectTarget union.  The server serialises
// this enum as a plain JSON string (e.g. "POPULATION"), which is why the union
// schema can distinguish it from a CardTarget object without a type tag.
export enum ResourceTarget {
  POPULATION = "POPULATION",
  PRODUCTION = "PRODUCTION",
  FOOD = "FOOD",
  POPULATION_CAP = "POPULATION_CAP",
}

// StaffingTier: the result of distributing floor(population) workers left-to-right
// across the table.  Determines how a card resolves at End and Run.
export enum StaffingTier {
  NONE = "NONE",   // < ceil(staffRequirement / 2) workers — card does not fire
  HALF = "HALF",   // ≥ ceil(staffRequirement / 2) but < staffRequirement — fires at half value
  FULL = "FULL",   // staffRequirement workers met — fires at full value
}

// ─── Data interfaces ──────────────────────────────────────────────────────────
//
// Each *Data interface describes the shape that a constructor expects.  Declaring
// them here, BEFORE the schemas, avoids the circular type-alias problem that
// occurs when you write `type FooData = z.infer<typeof FooSchema>` while
// FooSchema's transform already references FooData.  By making the types
// explicit up-front, schemas and classes can reference them freely.

interface CardTargetData {
  targetResource: ResourceTarget;
  adjacentOnly: boolean;
}

interface EffectData {
  target: EffectTarget;
  modifier: number;
  valueType: ValueType;
}

interface CardData {
  name: string;
  staffRequirement: number;
  productionRequirement: number;
  frequencyType: FrequencyType;
  effects: Effect[];
}

interface CardStaffingData {
  workersAllocated: number;
  workerCost: number;
  tier: StaffingTier;
}

interface EventOptionData {
  description: string;
  effects: Effect[];
}

interface KhEventData {
  id: string;
  season: Season;
  minYear: number;
  description: string;
  options: EventOption[];
}

interface GameContextData {
  player: string;
  gameId: string;
}

interface GameStateData {
  board: Map<Season, Card[]>;
  drawPile: Map<Season, Card[]>;
  discardPile: Map<Season, Card[]>;
  hand: Card[];
  population: number;
  populationCap: number;
  production: number;
  food: number;
  curSeason: Season;
  curPhase: Phase;
  year: number;
  addChoices: Card[];
  rerollCount: number;
  currentEvent: KhEvent | undefined;
  gameOver: boolean;
  tableStaffing: CardStaffing[];
}

interface GameMessageData {
  context: GameContext;
  state: GameState;
}

// ─── Domain classes ───────────────────────────────────────────────────────────
//
// Each class's constructor takes the corresponding *Data interface so the Zod
// schema transforms can hand off validated data without extra casting.

// CardTarget: the object branch of the EffectTarget union.  The server
// serialises this as { targetResource: "FOOD", adjacentOnly: true }.
// adjacentOnly=true  → boosts only the first matching card to the right (recurring, +4–8)
// adjacentOnly=false → boosts all matching cards to the right (recurring +1–3 or single +4–8)
export class CardTarget {
  targetResource: ResourceTarget;
  adjacentOnly: boolean;

  constructor(data: CardTargetData) {
    this.targetResource = data.targetResource;
    this.adjacentOnly   = data.adjacentOnly;
  }
}

// EffectTarget is either a ResourceTarget string or a CardTarget object.  The
// union type is used in Effect and wherever rendering code needs to branch on
// what a card effect actually acts upon.
export type EffectTarget = ResourceTarget | CardTarget;

// Effect: a single numeric modifier on a Card or EventOption.
export class Effect {
  target: EffectTarget;
  modifier: number;
  valueType: ValueType;

  constructor(data: EffectData) {
    this.target = data.target;
    this.modifier = data.modifier;
    this.valueType = data.valueType;
  }
}

// Card: a playable card with a staff requirement, a production cost, a frequency
// type, and a list of effects that fire when the card is resolved.
export class Card {
  name: string;
  staffRequirement: number;
  productionRequirement: number;
  frequencyType: FrequencyType;
  effects: Effect[];

  constructor(data: CardData) {
    this.name = data.name;
    this.staffRequirement = data.staffRequirement;
    this.productionRequirement = data.productionRequirement;
    this.frequencyType = data.frequencyType;
    this.effects = data.effects;
  }
}

// CardStaffing: the staffing snapshot for one table slot during the Play phase.
// Computed by the server and returned in GameState.tableStaffing.
export class CardStaffing {
  workersAllocated: number;
  workerCost: number;
  tier: StaffingTier;

  constructor(data: CardStaffingData) {
    this.workersAllocated = data.workersAllocated;
    this.workerCost = data.workerCost;
    this.tier = data.tier;
  }
}

// EventOption: one choice offered to the player when a random event fires.
export class EventOption {
  description: string;
  effects: Effect[];

  constructor(data: EventOptionData) {
    this.description = data.description;
    this.effects = data.effects;
  }
}

// KhEvent: a random event rolled during the RANDOM_EVENT phase.
// Prefixed "Kh" to avoid shadowing the browser's built-in Event interface.
export class KhEvent {
  id: string;
  season: Season;
  minYear: number;
  description: string;
  options: EventOption[];

  constructor(data: KhEventData) {
    this.id = data.id;
    this.season = data.season;
    this.minYear = data.minYear;
    this.description = data.description;
    this.options = data.options;
  }
}

// GameContext: identifies the recipient (player) and the game.
// Mirrors the Java KhContext record.
export class GameContext {
  player: string;
  gameId: string;

  constructor(data: GameContextData) {
    this.player = data.player;
    this.gameId = data.gameId;
  }
}

// GameState: full snapshot of the game at one point in time.
// Season-keyed piles are kept as Maps so rendering code can do a direct
// state.board.get(Season.SPRING) lookup without scanning arrays.
export class GameState {
  board: Map<Season, Card[]>;
  drawPile: Map<Season, Card[]>;
  discardPile: Map<Season, Card[]>;
  hand: Card[];
  population: number;
  populationCap: number;
  production: number;
  food: number;
  curSeason: Season;
  curPhase: Phase;
  year: number;
  addChoices: Card[];
  rerollCount: number;
  currentEvent: KhEvent | undefined;
  gameOver: boolean;
  tableStaffing: CardStaffing[];

  constructor(data: GameStateData) {
    this.board = data.board;
    this.drawPile = data.drawPile;
    this.discardPile = data.discardPile;
    this.hand = data.hand;
    this.population = data.population;
    this.populationCap = data.populationCap;
    this.production = data.production;
    this.food = data.food;
    this.curSeason = data.curSeason;
    this.curPhase = data.curPhase;
    this.year = data.year;
    this.addChoices = data.addChoices;
    this.rerollCount = data.rerollCount;
    this.currentEvent = data.currentEvent;
    this.gameOver = data.gameOver;
    this.tableStaffing = data.tableStaffing;
  }

  // Convenience accessor — the current season's table is needed very often.
  public getCurrentTable(): Card[] {
    return this.board.get(this.curSeason) ?? [];
  }
}

// GameMessage: the envelope that wraps context + state.  Every item placed on
// the async queue is a GameMessage.  Mirrors the Java KhMessage record.
export class GameMessage {
  context: GameContext;
  state: GameState;

  constructor(data: GameMessageData) {
    this.context = data.context;
    this.state = data.state;
  }
}

// ─── Zod Schemas ──────────────────────────────────────────────────────────────
//
// Each schema validates the raw JSON from the WebSocket and transforms it into
// the corresponding domain class.  Because the *Data interfaces are declared
// above, the casts in the transforms are non-circular and TypeScript can verify
// them.
//
// The naming convention: export schemas that outside code may need to call
// directly (e.g. GameMessageSchema for the WebSocket handler); keep leaf
// schemas unexported since they are only composed into larger schemas.

const CardTargetSchema = z
  .object({ targetResource: z.enum(ResourceTarget), adjacentOnly: z.boolean() })
  .transform((data) => new CardTarget(data as CardTargetData));

// EffectTarget union: ResourceTarget strings are tried first (the common case)
// then CardTarget objects.  z.union() evaluates branches in declaration order,
// so a plain string will pass the enum check immediately; an object will fail
// the enum check and succeed against the object schema.
const EffectTargetSchema = z.union([
  z.enum(ResourceTarget),
  CardTargetSchema,
]);

const EffectSchema = z
  .object({
    target: EffectTargetSchema,
    modifier: z.number(),
    valueType: z.enum(ValueType),
  })
  .transform((data) => new Effect(data as EffectData));

const CardSchema = z
  .object({
    name: z.string(),
    staffRequirement: z.number(),
    productionRequirement: z.number(),
    frequencyType: z.enum(FrequencyType),
    effects: z.array(EffectSchema),
  })
  .transform((data) => new Card(data as CardData));

const CardStaffingSchema = z
  .object({
    workersAllocated: z.number().int(),
    workerCost: z.number().int(),
    tier: z.enum(StaffingTier),
  })
  .transform((data) => new CardStaffing(data as CardStaffingData));

const EventOptionSchema = z
  .object({
    description: z.string(),
    effects: z.array(EffectSchema),
  })
  .transform((data) => new EventOption(data as EventOptionData));

const KhEventSchema = z
  .object({
    id: z.string(),
    season: z.enum(Season),
    minYear: z.number().int(),
    description: z.string(),
    options: z.array(EventOptionSchema),
  })
  .transform((data) => new KhEvent(data as KhEventData));

export const GameContextSchema = z
  .object({
    player: z.string(),
    gameId: z.string(),
  })
  .transform((data) => new GameContext(data as GameContextData));

// The server sends season-keyed piles as plain JSON objects:
//   { "SPRING": [...], "SUMMER": [...], "FALL": [...], "WINTER": [...] }
// z.preprocess converts the plain object to a Map before Zod validates it,
// mirroring the PieceToPositionSchema pattern in xsandos.ts.
const SeasonToCardsSchema = z.preprocess(
  (val) => new Map(Object.entries(val as object)),
  z.map(z.enum(Season), z.array(CardSchema)),
);

export const GameStateSchema = z
  .object({
    board: SeasonToCardsSchema,
    drawPile: SeasonToCardsSchema,
    discardPile: SeasonToCardsSchema,
    hand: z.array(CardSchema),
    population: z.number(),
    populationCap: z.number(),
    production: z.number(),
    food: z.number(),
    curSeason: z.enum(Season),
    curPhase: z.enum(Phase),
    year: z.number().int(),
    addChoices: z.array(CardSchema),
    rerollCount: z.number().int(),
    // currentEvent is absent between RANDOM_EVENT phases: the server omits it
    // entirely thanks to @JsonInclude(NON_NULL), so .optional() is correct.
    currentEvent: KhEventSchema.optional(),
    gameOver: z.boolean(),
    // tableStaffing is only populated during the PLAY phase; empty array otherwise.
    tableStaffing: z.array(CardStaffingSchema).default([]),
  })
  .transform((data) => new GameState(data as GameStateData));

export const GameMessageSchema = z
  .object({
    context: GameContextSchema,
    state: GameStateSchema,
  })
  .transform((data) => new GameMessage(data as GameMessageData));

// ─── GameEventHandler ─────────────────────────────────────────────────────────
//
// Owns the STOMP connection and the async message queue.  The queue guarantees
// messages are processed strictly in arrival order even if the server sends two
// updates in rapid succession (e.g. a state update immediately followed by an
// event roll).
//
// The isProcessing flag is the concurrency guard: once the drain loop starts it
// keeps shifting messages off the front of the array until the array is empty,
// then clears the flag.  Any message that arrives while the loop is running is
// pushed to the end of the array and will be picked up in the same iteration —
// no extra "kick" is required.

export class GameEventHandler {
  private gameId!: string;
  private stompClient!: Client;
  private subscription!: StompSubscription;

  private messageQueue: GameMessage[] = [];
  private isProcessing: boolean = false;

  // Callbacks are injected by main.ts so that this handler stays decoupled from
  // PixiJS scene details that will evolve as the display layer is built out.
  private readonly onMessage: (message: GameMessage) => void;
  private readonly onQuit: () => void;

  constructor(
    onMessage: (message: GameMessage) => void,
    onQuit: () => void,
  ) {
    this.onMessage = onMessage;
    this.onQuit = onQuit;
  }

  // Opens the WebSocket connection and subscribes to the player's private queue.
  // Kept separate from the constructor so callers can finish their own setup
  // (e.g. initialising PixiJS) before the first message arrives.
  public connect(gameId: string): void {
    this.gameId = gameId;

    const client = new Client({ brokerURL: wsUrl });
    client.onConnect = (frame) => this.onConnect(frame);
    client.onWebSocketError = (error) => this.onWebSocketError(error);
    client.onStompError = (frame) => this.onStompError(frame);
    this.stompClient = client;
    this.stompClient.activate();
  }

  // Pushes a parsed GameMessage onto the queue and drains it.
  public async processMessage(message: GameMessage): Promise<void> {
    this.messageQueue.push(message);

    if (this.isProcessing || this.messageQueue.length === 0) return;
    this.isProcessing = true;

    while (this.messageQueue.length > 0) {
      const next = this.messageQueue.shift()!;
      this.onMessage(next);
    }

    this.isProcessing = false;
  }

  // ── Game actions ──────────────────────────────────────────────────────────

  public quit(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/quit`,
    });
  }

  // Game state is persisted server-side; navigating home leaves the game active
  // in the player's active-games list.
  public saveAndExit(): void {
    window.location.href = homeUrl;
  }

  // ── ADD phase actions ─────────────────────────────────────────────────────

  /**
   * Requests a reroll of the current ADD-phase card offer.  The first reroll
   * after the initial free offer costs {@link GameEngine.REROLL_COST_STEP}
   * production; cost increases by the same step on each subsequent call.
   */
  public reroll(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/add/offer`,
    });
  }

  /**
   * Picks the offered ADD-phase card by its stable card name and shuffles it into the season's
   * draw pile.  Advances the phase to RANDOM_EVENT.
   *
   * @param name The stable {@code Card.name} of the chosen card.
   */
  public pickCard(name: string): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/add/pick`,
      body: JSON.stringify({ name }),
    });
  }

  /**
   * Skips the ADD phase without picking a card.  Advances the phase to
   * RANDOM_EVENT.
   */
  public skipAdd(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/add/skip`,
    });
  }

  // ── RANDOM_EVENT phase actions ────────────────────────────────────────────

  /**
   * Applies the chosen event option by its stable option name and advances the phase to PLAY.
   *
   * @param name The {@code EventOption.description} of the chosen option.
   */
  public pickEventOption(name: string): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/event/pick`,
      body: JSON.stringify({ name }),
    });
  }

  /**
   * Skips the RANDOM_EVENT phase when no event was rolled (i.e. {@code currentEvent} is
   * absent).  Advances the phase to PLAY and draws the hand.
   */
  public skipEvent(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/event/skip`,
    });
  }

  // ── PLAY phase actions ────────────────────────────────────────────────────

  /**
   * Places the hand card identified by its stable card name onto the current season's table.
   * Deducts the production cost.  If a matching card is already on the table, amplifies it
   * (upgrade) instead of filling a new slot.
   *
   * @param name The stable {@code Card.name} of the card to place.
   */
  public placeCard(name: string): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/play/card`,
      body: JSON.stringify({ name }),
    });
  }

  /**
   * Demolishes the table card identified by its stable card name.  Charges 20% of the card's
   * production requirement as the demolition cost.
   *
   * @param name The stable {@code Card.name} of the card to demolish.
   */
  public demolishCard(name: string): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/play/demolish`,
      body: JSON.stringify({ name }),
    });
  }

  /**
   * Reorders the current season's table by sending the complete desired card name order.
   * The server replaces the current table order to match the provided list exactly.
   *
   * @param names Ordered array of card names representing the desired table order.
   */
  public reorderTable(names: string[]): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/play/reorder`,
      body: JSON.stringify({ names }),
    });
  }

  /**
   * Ends the PLAY phase: resolves all table cards left-to-right, consumes seasonal food, then
   * advances the season and begins the next ADD phase.
   */
  public endAndRun(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/play/end`,
    });
  }

  // ── STOMP lifecycle ───────────────────────────────────────────────────────

  private onConnect(frame: Frame): void {
    console.log("Connected: " + frame);

    this.subscription = this.stompClient.subscribe(
      `/user/queue/games/${this.gameId}`,
      (stompMessage) => {
        const body = JSON.parse(stompMessage.body);

        // The server sends one of two shapes over this channel:
        //   • { quit: true }           — confirmation after player-initiated quit
        //   • { context: …, state: … } — normal game-state broadcast
        // The quit signal is handled immediately without going through the queue
        // because it carries no game state and should trigger navigation at once.
        if (body.quit === true) {
          this.subscription.unsubscribe();
          this.onQuit();
          return;
        }

        const message = GameMessageSchema.parse(body);
        console.log("Received KH message:", message);
        this.processMessage(message);
      },
    );

    // Request the current state as soon as we are subscribed, so the scene
    // renders immediately on first load rather than waiting for a server push.
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/load`,
    });
  }

  private onWebSocketError(error: unknown): void {
    console.error("Error with websocket", error);
    window.location.href = loginUrl;
  }

  private onStompError(frame: Frame): void {
    console.error("Broker reported error: " + frame.headers["message"]);
    console.error("Additional details: " + frame.body);
  }
}
