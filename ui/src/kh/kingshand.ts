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
  PLAY = "PLAY",
  COUNCIL_ADD = "COUNCIL_ADD",
  COUNCIL_MEETING = "COUNCIL_MEETING",
}

// AdvisorGate: when an Advisor becomes eligible to appear in the COUNCIL_ADD offer pool.
export enum AdvisorGate {
  NONE = "NONE",
  BARRACKS_COMPLETED = "BARRACKS_COMPLETED",
  ANY_GREAT_WORK_COMMISSIONED = "ANY_GREAT_WORK_COMMISSIONED",
  GREAT_ROAD_COMMISSIONED = "GREAT_ROAD_COMMISSIONED",
}

// ProposalKind: whether a CouncilProposal is a repeatable Reform or a one-time Decree.
export enum ProposalKind {
  REFORM = "REFORM",
  DECREE = "DECREE",
}

// DecreeType: what kind of one-time effect a Decree applies when accepted.
export enum DecreeType {
  PASSIVE_BOOST = "PASSIVE_BOOST",
  GREAT_WORK_BOOST = "GREAT_WORK_BOOST",
  POPULATION_CAP_BOOST = "POPULATION_CAP_BOOST",
  GREAT_WORK_ACCELERATE = "GREAT_WORK_ACCELERATE",
  FREE_REROLL = "FREE_REROLL",
}

// ReformBenefitType: what kind of benefit a Reform stack grants.
export enum ReformBenefitType {
  CARD_EFFECT = "CARD_EFFECT",
  FREE_REROLL = "FREE_REROLL",
  EXTRA_ADD_CANDIDATE = "EXTRA_ADD_CANDIDATE",
  CROSS_TABLE_BOOST = "CROSS_TABLE_BOOST",
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
  GOLD = "GOLD",
  STRENGTH = "STRENGTH",
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

interface OpponentTargetData {
  targetResource: ResourceTarget;
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
  /** Seasons of construction remaining; 0 for normal cards and completed Great Works. */
  constructionPeriod: number;
}

interface CardStaffingData {
  workersAllocated: number;
  workerCost: number;
  tier: StaffingTier;
}

interface ReformData {
  name: string;
  maxStacks: number;
  benefitType: ReformBenefitType;
  benefitResource: ResourceTarget | undefined;
  benefitPerStack: number;
  costResource: ResourceTarget;
  costPerStack: number;
}

interface DecreeData {
  name: string;
  type: DecreeType;
  targetGreatWorkName: string | undefined;
  effectResource: ResourceTarget | undefined;
  effectAmount: number;
}

interface AdvisorData {
  id: string;
  displayName: string;
  title: string;
  specialtyDescription: string;
  hiringCost: number;
  baseCard: Card;
  gate: AdvisorGate;
  reform: Reform;
  decree: Decree;
  crossTableBoostAmount: number;
  freeRerollsWhileSeated: number;
  extraAddCandidatesWhileSeated: number;
  ringColor: string;
  portraitPlaceholderId: string;
}

interface SeatedAdvisorData {
  advisorId: string;
  card: Card;
  reformStacksTaken: number;
  decreeTaken: boolean;
  lastProposalYear: number;
  bonusFreeRerolls: number;
  bonusExtraAddCandidates: number;
  bonusCrossTableBoost: number;
  hiredYear: number;
}

interface CouncilProposalData {
  advisorId: string;
  kind: ProposalKind;
  name: string;
}

interface TimeStateData {
  season: Season;
  phase: Phase;
  year: number;
}

interface ResourceStateData {
  food: number;
  production: number;
  population: number;
  populationCap: number;
  gold: number;
  strength: number;
  unlockedResources: ResourceTarget[];
}

interface GameContextData {
  player: string;
  gameId: string;
  /** The recipient's seat index — players[seat] in the match state is "you". */
  seat: number;
}

interface ResolutionStepData {
  cardName: string | undefined;
  staffingTier: StaffingTier | undefined;
  engineBoost: boolean;
  populationDelta: number;
  productionDelta: number;
  foodDelta: number;
  populationCapDelta: number;
  goldDelta: number;
  strengthDelta: number;
}

/** One kingdom on the wire — an element of the match's `players[]` array. */
interface KingdomData {
  board: Map<Season, Card[]>;
  resolutionLog: ResolutionStep[];
  drawPile: Map<Season, Card[]>;
  discardPile: Map<Season, Card[]>;
  hand: Card[];
  resources: ResourceState;
  addChoices: Card[];
  rerollCount: number;
  gameOver: boolean;
  gameWon: boolean;
  forfeited: boolean;
  tableStaffingBySeason: Map<Season, CardStaffing[]>;
  permanentFreeRerolls: number;
  council: SeatedAdvisor[];
  councilOfferChoices: Advisor[];
  currentProposal: CouncilProposal | undefined;
}

/** The whole-match wire shape: kingdoms by seat plus the shared clock and outcome. */
interface MatchStateData {
  players: Kingdom[];
  time: TimeState;
  matchOver: boolean;
  winnerSeat: number;
  submittedSeats: number[];
  resolutionPending: boolean;
}

interface GameStateData {
  board: Map<Season, Card[]>;
  resolutionLog: ResolutionStep[];
  drawPile: Map<Season, Card[]>;
  discardPile: Map<Season, Card[]>;
  hand: Card[];
  resources: ResourceState;
  time: TimeState;
  addChoices: Card[];
  rerollCount: number;
  gameOver: boolean;
  tableStaffing: CardStaffing[];
  gameWon: boolean;
  permanentFreeRerolls: number;
  council: SeatedAdvisor[];
  councilOfferChoices: Advisor[];
  currentProposal: CouncilProposal | undefined;
  // ── Multiplayer extras (defaults keep single-player code paths unchanged) ──
  seat: number;
  matchOver: boolean;
  winnerSeat: number;
  submittedSeats: number[];
  opponent: Kingdom | undefined;
  forfeited: boolean;
}

interface GameMessageData {
  context: GameContext;
  match: MatchState;
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

// OpponentTarget: the sabotage branch of the EffectTarget union.  The server
// serialises this as { targetResource: "PRODUCTION", opponent: true } — the
// `opponent` flag is the wire discriminator versus CardTarget.  The effect
// drains (or boosts) the OPPONENT's kingdom, resolved against a pre-resolution
// snapshot on the server.
export class OpponentTarget {
  targetResource: ResourceTarget;

  constructor(data: OpponentTargetData) {
    this.targetResource = data.targetResource;
  }
}

// EffectTarget is either a ResourceTarget string, a CardTarget object, or an
// OpponentTarget (sabotage) object.  The union type is used in Effect and
// wherever rendering code needs to branch on what a card effect acts upon.
export type EffectTarget = ResourceTarget | CardTarget | OpponentTarget;

// Effect: a single numeric modifier on a Card.
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
  constructionPeriod: number;

  constructor(data: CardData) {
    this.name = data.name;
    this.staffRequirement = data.staffRequirement;
    this.productionRequirement = data.productionRequirement;
    this.frequencyType = data.frequencyType;
    this.effects = data.effects;
    this.constructionPeriod = data.constructionPeriod;
  }

  /** True for a Great Work still under construction (as opposed to a normal or completed card). */
  isGreatWork(): boolean {
    return this.constructionPeriod > 0;
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

// Reform: a repeatable self-enhancement an advisor can propose during COUNCIL_MEETING.
export class Reform {
  name: string;
  maxStacks: number;
  benefitType: ReformBenefitType;
  benefitResource: ResourceTarget | undefined;
  benefitPerStack: number;
  costResource: ResourceTarget;
  costPerStack: number;

  constructor(data: ReformData) {
    this.name = data.name;
    this.maxStacks = data.maxStacks;
    this.benefitType = data.benefitType;
    this.benefitResource = data.benefitResource;
    this.benefitPerStack = data.benefitPerStack;
    this.costResource = data.costResource;
    this.costPerStack = data.costPerStack;
  }
}

// Decree: a one-time civic project an advisor can propose during COUNCIL_MEETING.
export class Decree {
  name: string;
  type: DecreeType;
  targetGreatWorkName: string | undefined;
  effectResource: ResourceTarget | undefined;
  effectAmount: number;

  constructor(data: DecreeData) {
    this.name = data.name;
    this.type = data.type;
    this.targetGreatWorkName = data.targetGreatWorkName;
    this.effectResource = data.effectResource;
    this.effectAmount = data.effectAmount;
  }
}

// Advisor: catalog definition of one of the 15 King's Hand advisors (static data,
// not yet hired). portraitPlaceholderId/ringColor drive the placeholder portrait
// rendering until real per-advisor art is dropped in.
export class Advisor {
  id: string;
  displayName: string;
  title: string;
  specialtyDescription: string;
  hiringCost: number;
  baseCard: Card;
  gate: AdvisorGate;
  reform: Reform;
  decree: Decree;
  crossTableBoostAmount: number;
  freeRerollsWhileSeated: number;
  extraAddCandidatesWhileSeated: number;
  ringColor: string;
  portraitPlaceholderId: string;

  constructor(data: AdvisorData) {
    this.id = data.id;
    this.displayName = data.displayName;
    this.title = data.title;
    this.specialtyDescription = data.specialtyDescription;
    this.hiringCost = data.hiringCost;
    this.baseCard = data.baseCard;
    this.gate = data.gate;
    this.reform = data.reform;
    this.decree = data.decree;
    this.crossTableBoostAmount = data.crossTableBoostAmount;
    this.freeRerollsWhileSeated = data.freeRerollsWhileSeated;
    this.extraAddCandidatesWhileSeated = data.extraAddCandidatesWhileSeated;
    this.ringColor = data.ringColor;
    this.portraitPlaceholderId = data.portraitPlaceholderId;
  }
}

// SeatedAdvisor: runtime state for an Advisor who has actually been hired onto the
// council. card accumulates Reform/Decree boosts over the life of the run.
export class SeatedAdvisor {
  advisorId: string;
  card: Card;
  reformStacksTaken: number;
  decreeTaken: boolean;
  lastProposalYear: number;
  bonusFreeRerolls: number;
  bonusExtraAddCandidates: number;
  bonusCrossTableBoost: number;
  hiredYear: number;

  constructor(data: SeatedAdvisorData) {
    this.advisorId = data.advisorId;
    this.card = data.card;
    this.reformStacksTaken = data.reformStacksTaken;
    this.decreeTaken = data.decreeTaken;
    this.lastProposalYear = data.lastProposalYear;
    this.bonusFreeRerolls = data.bonusFreeRerolls;
    this.bonusExtraAddCandidates = data.bonusExtraAddCandidates;
    this.bonusCrossTableBoost = data.bonusCrossTableBoost;
    this.hiredYear = data.hiredYear;
  }
}

// CouncilProposal: the single rotating Reform/Decree proposal presented during a
// COUNCIL_MEETING phase.
export class CouncilProposal {
  advisorId: string;
  kind: ProposalKind;
  name: string;

  constructor(data: CouncilProposalData) {
    this.advisorId = data.advisorId;
    this.kind = data.kind;
    this.name = data.name;
  }
}

// ResolutionStep: one entry in the end-of-season resolution log sent by the server.
// The frontend uses this to drive indicators and toasts without local simulation.
export class ResolutionStep {
  cardName: string | undefined;
  staffingTier: StaffingTier | undefined;
  engineBoost: boolean;
  populationDelta: number;
  productionDelta: number;
  foodDelta: number;
  populationCapDelta: number;
  goldDelta: number;
  strengthDelta: number;

  constructor(data: ResolutionStepData) {
    this.cardName         = data.cardName;
    this.staffingTier     = data.staffingTier;
    this.engineBoost      = data.engineBoost;
    this.populationDelta  = data.populationDelta;
    this.productionDelta  = data.productionDelta;
    this.foodDelta        = data.foodDelta;
    this.populationCapDelta = data.populationCapDelta;
    this.goldDelta        = data.goldDelta;
    this.strengthDelta    = data.strengthDelta;
  }
}

// GameContext: identifies the recipient (player), the game, and the seat the
// recipient occupies in the match. Mirrors the Java KhContext record.
export class GameContext {
  player: string;
  gameId: string;
  seat: number;

  constructor(data: GameContextData) {
    this.player = data.player;
    this.gameId = data.gameId;
    this.seat = data.seat;
  }
}

export class TimeState {
  season: Season;
  phase: Phase;
  year: number;

  constructor(data: TimeStateData) {
    this.season = data.season;
    this.phase = data.phase;
    this.year = data.year;
  }
}

export class ResourceState {
  food: number;
  production: number;
  population: number;
  populationCap: number;
  gold: number;
  strength: number;
  unlockedResources: ResourceTarget[];

  constructor(data: ResourceStateData) {
    this.food = data.food;
    this.production = data.production;
    this.population = data.population;
    this.populationCap = data.populationCap;
    this.gold = data.gold;
    this.strength = data.strength;
    this.unlockedResources = data.unlockedResources;
  }

  isUnlocked(target: ResourceTarget): boolean {
    return this.unlockedResources.includes(target);
  }
}

// Kingdom: one seat's kingdom as sent inside the match's players[] array.
// Opponent kingdoms arrive REDACTED from the server (empty hand/offers/piles) —
// the hidden-information model.
export class Kingdom {
  board: Map<Season, Card[]>;
  drawPile: Map<Season, Card[]>;
  discardPile: Map<Season, Card[]>;
  hand: Card[];
  resources: ResourceState;
  addChoices: Card[];
  rerollCount: number;
  gameOver: boolean;
  gameWon: boolean;
  forfeited: boolean;
  tableStaffingBySeason: Map<Season, CardStaffing[]>;
  resolutionLog: ResolutionStep[];
  permanentFreeRerolls: number;
  council: SeatedAdvisor[];
  councilOfferChoices: Advisor[];
  currentProposal: CouncilProposal | undefined;

  constructor(data: KingdomData) {
    this.board = data.board;
    this.drawPile = data.drawPile;
    this.discardPile = data.discardPile;
    this.hand = data.hand;
    this.resources = data.resources;
    this.addChoices = data.addChoices;
    this.rerollCount = data.rerollCount;
    this.gameOver = data.gameOver;
    this.gameWon = data.gameWon;
    this.forfeited = data.forfeited;
    this.tableStaffingBySeason = data.tableStaffingBySeason;
    this.resolutionLog = data.resolutionLog;
    this.permanentFreeRerolls = data.permanentFreeRerolls;
    this.council = data.council;
    this.councilOfferChoices = data.councilOfferChoices;
    this.currentProposal = data.currentProposal;
  }
}

// MatchState: the full wire payload — every kingdom by seat, the shared clock,
// the readiness barrier's submittedSeats, and the match outcome.
export class MatchState {
  players: Kingdom[];
  time: TimeState;
  matchOver: boolean;
  winnerSeat: number;
  submittedSeats: number[];
  /** True while a multiplayer PLAY resolution is held on-screen until every seat clicks Continue. */
  resolutionPending: boolean;

  constructor(data: MatchStateData) {
    this.players = data.players;
    this.time = data.time;
    this.matchOver = data.matchOver;
    this.winnerSeat = data.winnerSeat;
    this.submittedSeats = data.submittedSeats;
    this.resolutionPending = data.resolutionPending;
  }

  /** True once `seat` has submitted the current phase (is waiting on others). */
  public hasSubmitted(seat: number): boolean {
    return this.submittedSeats.includes(seat);
  }
}

// GameState: the per-seat view the scenes consume — YOUR kingdom flattened
// together with the shared clock, exactly the shape the scenes were built
// against, plus multiplayer extras (seat, opponent, submittedSeats, outcome).
// Season-keyed piles are kept as Maps so rendering code can do a direct
// state.board.get(Season.SPRING) lookup without scanning arrays.
export class GameState {
  board: Map<Season, Card[]>;
  drawPile: Map<Season, Card[]>;
  discardPile: Map<Season, Card[]>;
  hand: Card[];
  resources: ResourceState;
  time: TimeState;
  addChoices: Card[];
  rerollCount: number;
  gameOver: boolean;
  tableStaffing: CardStaffing[];
  resolutionLog: ResolutionStep[];
  gameWon: boolean;
  permanentFreeRerolls: number;
  council: SeatedAdvisor[];
  councilOfferChoices: Advisor[];
  currentProposal: CouncilProposal | undefined;
  // ── Multiplayer extras ──
  seat: number;
  matchOver: boolean;
  winnerSeat: number;
  submittedSeats: number[];
  opponent: Kingdom | undefined;
  forfeited: boolean;

  constructor(data: GameStateData) {
    this.board = data.board;
    this.drawPile = data.drawPile;
    this.discardPile = data.discardPile;
    this.hand = data.hand;
    this.resources = data.resources;
    this.time = data.time;
    this.addChoices = data.addChoices;
    this.rerollCount = data.rerollCount;
    this.gameOver = data.gameOver;
    this.tableStaffing = data.tableStaffing;
    this.resolutionLog = data.resolutionLog;
    this.gameWon = data.gameWon;
    this.permanentFreeRerolls = data.permanentFreeRerolls;
    this.council = data.council;
    this.councilOfferChoices = data.councilOfferChoices;
    this.currentProposal = data.currentProposal;
    this.seat = data.seat;
    this.matchOver = data.matchOver;
    this.winnerSeat = data.winnerSeat;
    this.submittedSeats = data.submittedSeats;
    this.opponent = data.opponent;
    this.forfeited = data.forfeited;
  }

  /** Builds the scene-facing view of `seat`'s kingdom from the match payload. */
  public static forSeat(match: MatchState, seat: number): GameState {
    const you = match.players[seat] ?? match.players[0];
    const opponent =
      match.players.length > 1 ? match.players[seat === 0 ? 1 : 0] : undefined;
    return new GameState({
      board: you.board,
      drawPile: you.drawPile,
      discardPile: you.discardPile,
      hand: you.hand,
      resources: you.resources,
      time: match.time,
      addChoices: you.addChoices,
      rerollCount: you.rerollCount,
      gameOver: you.gameOver,
      tableStaffing: you.tableStaffingBySeason.get(match.time.season) ?? [],
      resolutionLog: you.resolutionLog,
      gameWon: you.gameWon,
      permanentFreeRerolls: you.permanentFreeRerolls,
      council: you.council,
      councilOfferChoices: you.councilOfferChoices,
      currentProposal: you.currentProposal,
      seat,
      matchOver: match.matchOver,
      winnerSeat: match.winnerSeat,
      submittedSeats: match.submittedSeats,
      opponent,
      forfeited: you.forfeited,
    });
  }

  /** True when this seat has submitted the current phase and is waiting on others. */
  public isWaitingOnOthers(): boolean {
    return !this.matchOver && this.submittedSeats.includes(this.seat);
  }

  // Convenience accessor — the current season's table is needed very often.
  public getCurrentTable(): Card[] {
    return this.board.get(this.time.season) ?? [];
  }
}

// GameMessage: the envelope that wraps context + match state.  Every item
// placed on the async queue is a GameMessage.  `state` is the recipient's
// per-seat view (what the scenes consume); `match` carries the whole match for
// match-level UI (opponent HUD, waiting overlay, dual resolution screen).
export class GameMessage {
  context: GameContext;
  match: MatchState;
  state: GameState;

  constructor(data: GameMessageData) {
    this.context = data.context;
    this.match = data.match;
    this.state = GameState.forSeat(data.match, data.context.seat);
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

// Sabotage target: distinguished from CardTarget by the `opponent: true`
// discriminator the server adds (both otherwise carry targetResource).
const OpponentTargetSchema = z
  .object({ targetResource: z.enum(ResourceTarget), opponent: z.literal(true) })
  .transform((data) => new OpponentTarget({ targetResource: data.targetResource }));

// EffectTarget union: ResourceTarget strings are tried first (the common case),
// then OpponentTarget (requires the `opponent: true` discriminator), then
// CardTarget objects.  z.union() evaluates branches in declaration order, so a
// plain string passes the enum check immediately; objects fall through to the
// matching object schema.
const EffectTargetSchema = z.union([
  z.enum(ResourceTarget),
  OpponentTargetSchema,
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
    // > 0 while a Great Work is still under construction; 0 for normal cards and
    // completed Great Works. Defaulted for safety, though the server always sends it.
    constructionPeriod: z.number().int().default(0),
  })
  .transform((data) => new Card(data as CardData));

const CardStaffingSchema = z
  .object({
    workersAllocated: z.number().int(),
    workerCost: z.number().int(),
    tier: z.enum(StaffingTier),
  })
  .transform((data) => new CardStaffing(data as CardStaffingData));

const ReformSchema = z
  .object({
    name: z.string(),
    maxStacks: z.number().int(),
    benefitType: z.enum(ReformBenefitType),
    // Server (Jackson) sends an explicit JSON `null` for advisors whose Reform benefit
    // isn't resource-typed (e.g. FREE_REROLL/EXTRA_ADD_CANDIDATE/CROSS_TABLE_BOOST), not a
    // missing key. `.optional()` alone only accepts `undefined`, not `null`, and Zod throws
    // on the mismatch — `.nullish()` + normalize keeps the parsed type `ResourceTarget | undefined`.
    benefitResource: z.enum(ResourceTarget).nullish().transform((v) => v ?? undefined),
    benefitPerStack: z.number(),
    costResource: z.enum(ResourceTarget),
    costPerStack: z.number(),
  })
  .transform((data) => new Reform(data as ReformData));

const DecreeSchema = z
  .object({
    name: z.string(),
    type: z.enum(DecreeType),
    // Same null-vs-undefined issue as Reform.benefitResource above: Jackson sends explicit
    // JSON `null` for the advisors whose Decree doesn't target a Great Work / resource.
    targetGreatWorkName: z.string().nullish().transform((v) => v ?? undefined),
    effectResource: z.enum(ResourceTarget).nullish().transform((v) => v ?? undefined),
    effectAmount: z.number(),
  })
  .transform((data) => new Decree(data as DecreeData));

const AdvisorSchema = z
  .object({
    id: z.string(),
    displayName: z.string(),
    title: z.string(),
    specialtyDescription: z.string(),
    hiringCost: z.number().int(),
    baseCard: CardSchema,
    gate: z.enum(AdvisorGate),
    reform: ReformSchema,
    decree: DecreeSchema,
    crossTableBoostAmount: z.number(),
    freeRerollsWhileSeated: z.number().int(),
    extraAddCandidatesWhileSeated: z.number().int(),
    ringColor: z.string(),
    portraitPlaceholderId: z.string(),
  })
  .transform((data) => new Advisor(data as AdvisorData));

const SeatedAdvisorSchema = z
  .object({
    advisorId: z.string(),
    card: CardSchema,
    reformStacksTaken: z.number().int(),
    decreeTaken: z.boolean(),
    lastProposalYear: z.number().int(),
    bonusFreeRerolls: z.number().int(),
    bonusExtraAddCandidates: z.number().int(),
    bonusCrossTableBoost: z.number(),
    hiredYear: z.number().int(),
  })
  .transform((data) => new SeatedAdvisor(data as SeatedAdvisorData));

const CouncilProposalSchema = z
  .object({
    advisorId: z.string(),
    kind: z.enum(ProposalKind),
    name: z.string(),
  })
  .transform((data) => new CouncilProposal(data as CouncilProposalData));

const ResolutionStepSchema = z
  .object({
    cardName:         z.string().optional(),
    staffingTier:     z.enum(StaffingTier).optional(),
    engineBoost:      z.boolean(),
    populationDelta:  z.number(),
    productionDelta:  z.number(),
    foodDelta:        z.number(),
    populationCapDelta: z.number(),
    // Gold/strength deltas are absent on resolution steps recorded before this
    // field existed server-side (shouldn't happen in practice since the log is
    // never persisted across deploys, but defaulting to 0 keeps old messages safe).
    goldDelta:        z.number().default(0),
    strengthDelta:    z.number().default(0),
  })
  .transform((data) => new ResolutionStep(data as ResolutionStepData));

export const GameContextSchema = z
  .object({
    player: z.string(),
    gameId: z.string(),
    // The recipient's seat in the match. Defaulted for safety, though the
    // server always sends it since the two-player refactor.
    seat: z.number().int().default(0),
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

const TimeStateSchema = z
  .object({
    season: z.enum(Season),
    phase: z.enum(Phase),
    year: z.number().int(),
  })
  .transform((data) => new TimeState(data));

const ResourceStateSchema = z
  .object({
    food: z.number(),
    production: z.number(),
    population: z.number(),
    populationCap: z.number(),
    gold: z.number().default(0),
    strength: z.number().default(0),
    unlockedResources: z.array(z.enum(ResourceTarget)).default([]),
  })
  .transform((data) => new ResourceState(data));

// Per-season staffing map, computed server-side per kingdom. The client picks
// the current season's entry as the scene-facing `tableStaffing`.
const SeasonToStaffingSchema = z.preprocess(
  (val) => new Map(Object.entries((val ?? {}) as object)),
  z.map(z.enum(Season), z.array(CardStaffingSchema)),
);

// One kingdom inside the match's players[] array. Opponent kingdoms arrive
// redacted (empty hand/offers/piles) — defaults keep parsing safe either way.
const KingdomSchema = z
  .object({
    board: SeasonToCardsSchema,
    drawPile: SeasonToCardsSchema,
    discardPile: SeasonToCardsSchema,
    hand: z.array(CardSchema).default([]),
    resources: ResourceStateSchema,
    addChoices: z.array(CardSchema).default([]),
    rerollCount: z.number().int().default(0),
    gameOver: z.boolean(),
    // true once all four win-condition Great Works are completed.
    gameWon: z.boolean().default(false),
    // true once this kingdom's player gave up (multi-seat "gives up → forfeits").
    forfeited: z.boolean().default(false),
    // Worker staffing per season, computed server-side from board + population.
    tableStaffingBySeason: SeasonToStaffingSchema.default(new Map()),
    // resolutionLog is populated after a PLAY resolution; empty between seasons.
    resolutionLog: z.array(ResolutionStepSchema).default([]),
    // Permanent free-ADD-phase-reroll count from one-time Decrees (e.g. Lady Iseult Varn's
    // "Plant an Informant"). Distinct from a seated advisor's own bonusFreeRerolls (Reform-driven,
    // tied to that seat) — this persists even after the granting advisor is later dismissed.
    permanentFreeRerolls: z.number().int().default(0),
    // Advisors currently seated on the council; never more than MAX_COUNCIL_SIZE (3).
    council: z.array(SeatedAdvisorSchema).default([]),
    // Candidate advisors offered during the current COUNCIL_ADD phase; empty outside it.
    councilOfferChoices: z.array(AdvisorSchema).default([]),
    // The single rotating Reform/Decree proposal for the current COUNCIL_MEETING phase;
    // omitted from JSON (and thus absent here) if no seated advisor has anything to propose.
    // Same null-vs-undefined issue: Jackson sends explicit JSON `null` when no seated
    // advisor currently has anything left to propose, not a missing key.
    currentProposal: CouncilProposalSchema.nullish().transform((v) => v ?? undefined),
  })
  .transform((data) => new Kingdom(data as KingdomData));

// The whole-match wire shape: { players: [...], time, matchOver, winnerSeat, submittedSeats }.
export const MatchStateSchema = z
  .object({
    players: z.array(KingdomSchema).min(1),
    time: TimeStateSchema,
    matchOver: z.boolean().default(false),
    winnerSeat: z.number().int().default(-1),
    // Seats that have completed the current phase — drives "waiting for other player…".
    submittedSeats: z.array(z.number().int()).default([]),
    // True while a resolved PLAY phase is held until every seat clicks Continue.
    resolutionPending: z.boolean().default(false),
  })
  .transform((data) => new MatchState(data as MatchStateData));

export const GameMessageSchema = z
  .object({
    context: GameContextSchema,
    state: MatchStateSchema,
  })
  .transform(
    (data) =>
      new GameMessage({
        context: data.context as GameContext,
        match: data.state as MatchState,
      }),
  );

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
  private wsErrorCount = 0;

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

    const client = new Client({
      brokerURL: wsUrl,
      heartbeatIncoming: 25000,
      heartbeatOutgoing: 25000,
    });
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
   * draw pile.  Advances the phase to PLAY.
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
   * Skips the ADD phase without picking a card.  Advances the phase to PLAY.
   */
  public skipAdd(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/add/skip`,
    });
  }

  // ── COUNCIL_ADD / COUNCIL_MEETING phase actions ───────────────────────────

  /**
   * Hires an advisor offered this COUNCIL_ADD round by their stable catalog id, deducting their
   * hiring cost in gold.  Advances the phase to COUNCIL_MEETING and seeds that year's single
   * rotating project proposal.
   *
   * @param id The stable {@code Advisor.id} of the chosen advisor.
   */
  public hireAdvisor(id: string): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/council/hire`,
      body: JSON.stringify({ id }),
    });
  }

  /**
   * Declines to hire anyone this year.  Advances the phase to COUNCIL_MEETING and seeds that
   * year's single rotating project proposal.
   */
  public skipCouncilAdd(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/council/skip`,
    });
  }

  /**
   * Accepts the current COUNCIL_MEETING proposal (a Reform or Decree from one seated advisor),
   * applying its effect.  Advances the phase to ADD and seeds the next ADD-phase offer.
   */
  public acceptCouncilProposal(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/council/accept`,
    });
  }

  /**
   * Passes on the current COUNCIL_MEETING proposal without applying it.  Advances the phase to
   * ADD and seeds the next ADD-phase offer.
   */
  public passCouncilProposal(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/council/pass`,
    });
  }

  /**
   * Dismisses ("lets go") the seated advisor identified by stable catalog id, paying a gold cost
   * deducted server-side. Available during either council sub-phase.
   *
   * @param id The stable {@code Advisor.id} of the advisor to dismiss.
   */
  public dismissAdvisor(id: string): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/council/dismiss`,
      body: JSON.stringify({ id }),
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

  /**
   * Acknowledges the multiplayer resolution screen ("Continue"). The clock is held on the resolved
   * season until every seat continues, at which point the server advances to the next phase.
   */
  public continueResolution(): void {
    this.stompClient.publish({
      destination: `/games/kh/${this.gameId}/play/continue`,
    });
  }

  // ── STOMP lifecycle ───────────────────────────────────────────────────────

  private onConnect(frame: Frame): void {
    this.wsErrorCount = 0;
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

        let message: GameMessage;
        try {
          message = GameMessageSchema.parse(body);
        } catch (error) {
          // A schema mismatch here used to throw uncaught inside this STOMP
          // callback, which silently killed all future state updates (the UI
          // would appear to "freeze"). Log loudly and drop the malformed
          // message instead of taking down the whole subscription.
          console.error("Failed to parse KH message; dropping it.", error, body);
          return;
        }
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
    this.wsErrorCount++;
    console.error("Error with websocket", error, `(${this.wsErrorCount})`);
    if (this.wsErrorCount >= 3) {
      console.error("Repeated WebSocket errors — redirecting to login");
      window.location.href = loginUrl;
    }
  }

  private onStompError(frame: Frame): void {
    console.error("Broker reported error: " + frame.headers["message"]);
    console.error("Additional details: " + frame.body);
  }
}
