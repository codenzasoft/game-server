import {
  Application,
  Assets,
  Container,
  Sprite,
  Text,
  TextStyle,
  Texture,
} from "pixi.js";
import { DropShadowFilter } from "pixi-filters";
import { z } from "zod";
import { Client, Frame, StompSubscription } from "@stomp/stompjs";
import { playDealSound, playCardPlaySound } from "./sounds";
import { renderLobbyOverlay } from "../shared/lobby";

const wsUrl =
  import.meta.env.VITE_WS_URL ||
  `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/games/connect`;
const loginUrl =
  import.meta.env.VITE_LOGIN_URL || `${window.location.origin}/login`;

// ── Enums ────────────────────────────────────────────────────────────────────

enum Suit {
  CLUBS = "CLUBS",
  DIAMONDS = "DIAMONDS",
  HEARTS = "HEARTS",
  SPADES = "SPADES",
}
enum Rank {
  TWO = "TWO",
  THREE = "THREE",
  FOUR = "FOUR",
  FIVE = "FIVE",
  SIX = "SIX",
  SEVEN = "SEVEN",
  EIGHT = "EIGHT",
  NINE = "NINE",
  TEN = "TEN",
  JACK = "JACK",
  QUEEN = "QUEEN",
  KING = "KING",
  ACE = "ACE",
}
enum Pass {
  LEFT = "LEFT",
  RIGHT = "RIGHT",
  ACROSS = "ACROSS",
  KEEPER = "KEEPER",
}

// ── Hand sorting ─────────────────────────────────────────────────────────────

const SUIT_ORDER: Record<Suit, number> = {
  [Suit.CLUBS]: 0,
  [Suit.DIAMONDS]: 1,
  [Suit.SPADES]: 2,
  [Suit.HEARTS]: 3,
};

const RANK_ORDER: Record<Rank, number> = {
  [Rank.TWO]: 0,
  [Rank.THREE]: 1,
  [Rank.FOUR]: 2,
  [Rank.FIVE]: 3,
  [Rank.SIX]: 4,
  [Rank.SEVEN]: 5,
  [Rank.EIGHT]: 6,
  [Rank.NINE]: 7,
  [Rank.TEN]: 8,
  [Rank.JACK]: 9,
  [Rank.QUEEN]: 10,
  [Rank.KING]: 11,
  [Rank.ACE]: 12,
};

function sortHand(hand: Card[]): Card[] {
  return [...hand].sort((a, b) => {
    const suitDiff = SUIT_ORDER[a.suit] - SUIT_ORDER[b.suit];
    return suitDiff !== 0 ? suitDiff : RANK_ORDER[a.rank] - RANK_ORDER[b.rank];
  });
}

// ── SVG path helper ──────────────────────────────────────────────────────────

const RANK_NUM: Record<string, number> = {
  TWO: 2,
  THREE: 3,
  FOUR: 4,
  FIVE: 5,
  SIX: 6,
  SEVEN: 7,
  EIGHT: 8,
  NINE: 9,
  TEN: 10,
  JACK: 11,
  QUEEN: 12,
  KING: 13,
  ACE: 1,
};
const RANK_SUFFIX: Partial<Record<string, string>> = {
  JACK: "-JACK",
  QUEEN: "-QUEEN",
  KING: "-KING",
};

function cardPath(suit: string, rank: string): string {
  return `/assets/cards/${suit}-${RANK_NUM[rank]}${RANK_SUFFIX[rank] ?? ""}.svg`;
}

function cardKey(suit: string, rank: string): string {
  return `${suit}-${rank}`;
}

// ── Zod schemas ──────────────────────────────────────────────────────────────

const CardSchema = z.object({
  suit: z.nativeEnum(Suit),
  rank: z.nativeEnum(Rank),
});
type Card = z.infer<typeof CardSchema>;

const PlayedCardSchema = z.object({ player: z.string(), card: CardSchema });

const TrickStateSchema = z.object({
  number: z.number(),
  leader: z.string(),
  playedCards: z.array(PlayedCardSchema),
  winner: PlayedCardSchema.optional(),
});

const RoundStateSchema = z
  .object({
    number: z.number(),
    pass: z.nativeEnum(Pass),
    dealer: z.string(),
    passComplete: z.boolean(),
    tricks: z.array(TrickStateSchema),
    complete: z.boolean(),
    score: z.record(z.string(), z.number()).optional(),
  })
  .passthrough();
type RoundState = z.infer<typeof RoundStateSchema>;

const HeartsStateSchema = z.object({
  players: z.array(z.string()),
  rounds: z.array(RoundStateSchema),
  over: z.boolean(),
  quit: z.boolean(),
  quitBy: z.string().optional(),
  score: z.record(z.string(), z.number()).optional(),
  winner: z.string().nullish(),
  tied: z.array(z.string()).optional(),
});
type HeartsState = z.infer<typeof HeartsStateSchema>;

const HeartsContextSchema = z.object({
  player: z.string(),
  gameId: z.string(),
  hand: z.array(CardSchema),
  validCards: z.array(CardSchema),
  passingRequired: z.boolean(),
  nextGameId: z.string().optional(),
});
type HeartsContext = z.infer<typeof HeartsContextSchema>;

const HeartsMessageSchema = z.object({
  context: HeartsContextSchema,
  state: HeartsStateSchema,
});
type HeartsMessage = z.infer<typeof HeartsMessageSchema>;

const HeartsLobbyMessageSchema = z.object({
  lobby: z.object({
    gameId: z.string(),
    players: z.array(z.string()),
  }),
});
type HeartsLobbyState = z.infer<typeof HeartsLobbyMessageSchema>["lobby"];

// ── Card dimensions (base values — scaled at runtime) ────────────────────────

const BASE_CARD_W = 80;
const BASE_CARD_H = 112;
const BASE_ARC_RADIUS = 1800;
const ANGLE_STEP = Math.PI / 80; // ~2.25° between adjacent cards

const OPP_BASE_CARD_W = 44;
const OPP_BASE_CARD_H = 62;
const OPP_BASE_ARC_RADIUS = 600;

// ── HeartsGameHandler ────────────────────────────────────────────────────────

export class HeartsGameHandler {
  private app: Application;
  private gameId = "";
  private stompClient!: Client;
  private subscription!: StompSubscription;

  private messageQueue: HeartsMessage[] = [];
  private isProcessing = false;

  // Loaded card textures (key = "SUIT-RANK")
  private textures = new Map<string, Texture>();
  private cardBackTexture: Texture | null = null;

  // Pass selection state (cleared on each new message when not required)
  private selectedPassCards: Card[] = [];

  // HTML overlay for pass button
  private passOverlay: HTMLDivElement | null = null;

  // HTML overlay for score table
  private scoreOverlay: HTMLDivElement | null = null;
  private scoreExpanded = false;

  // Last received message — used to re-render on pass card toggle
  private lastMessage: HeartsMessage | null = null;

  // Last lobby state — used to re-render on resize while in lobby
  private lastLobbyState: HeartsLobbyState | null = null;

  // HTML overlay for lobby waiting room
  private lobbyOverlay: HTMLDivElement | null = null;

  // HTML overlay for in-game menu dropdown
  private menuOverlay: HTMLDivElement | null = null;
  private menuOutsideClickHandler: ((e: PointerEvent) => void) | null = null;

  // Trick container retained across renders to support the win animation
  private trickContainer: Container | null = null;

  private resizeTimer: number | null = null;

  constructor(app: Application) {
    this.app = app;
    this.setupResizeHandler();
  }

  // ── Dynamic card sizing ────────────────────────────────────────────────────

  private get cardScale(): number {
    // 944 = actual hand span at scale 1: outermost card center (~420px) plus rotated card
    // half-extent (CARD_W*cos(angle) + CARD_H*sin(angle))/2 ≈ 52px. Subtract 40px margin.
    const scaleW = (this.app.screen.width - 40) / 944;
    const scaleH = this.app.screen.height / 600;
    return Math.max(1, Math.min(scaleW, scaleH, 2.5));
  }

  private get cardW(): number {
    return Math.round(BASE_CARD_W * this.cardScale);
  }

  private get cardH(): number {
    return Math.round(BASE_CARD_H * this.cardScale);
  }

  private get arcRadius(): number {
    return Math.round(BASE_ARC_RADIUS * this.cardScale);
  }

  private get oppCardW(): number {
    return Math.round(OPP_BASE_CARD_W * this.cardScale);
  }

  private get oppCardH(): number {
    return Math.round(OPP_BASE_CARD_H * this.cardScale);
  }

  private get oppArcRadius(): number {
    return Math.round(OPP_BASE_ARC_RADIUS * this.cardScale);
  }

  private setupResizeHandler() {
    window.addEventListener("resize", () => {
      if (this.resizeTimer !== null) clearTimeout(this.resizeTimer);
      this.resizeTimer = window.setTimeout(
        () => this.rerenderLastMessage(),
        150,
      );
    });
  }

  private rerenderLastMessage() {
    if (this.lastLobbyState) {
      this.renderLobby(this.lastLobbyState);
      return;
    }
    const msg = this.lastMessage;
    if (!msg || this.isProcessing) return;
    const { context, state } = msg;
    this.clearScene();
    this.renderScoreTable(context, state);
    if (state.over || state.quit) {
      this.renderGameOver(context, state);
      return;
    }
    if (state.rounds.length === 0) {
      this.renderStatus("Starting game...");
      return;
    }
    const round = state.rounds[state.rounds.length - 1];
    this.renderStatus(this.buildStatusText(context, state, round));
    this.renderOpponents(context, state);
    this.renderHand(context, state, round);
    this.renderPassOverlay(context, round);
    // Pass msg as prevMessage so no new-card animation fires
    void this.renderTrick(context, state, round, msg);
  }

  // ── Pre-load all 52 card SVGs ──────────────────────────────────────────────

  async preloadTextures() {
    const suits = Object.values(Suit);
    const ranks = Object.values(Rank);
    const resolution = window.devicePixelRatio || 1;
    await Promise.all([
      ...suits.flatMap((suit) =>
        ranks.map(async (rank) => {
          const key = cardKey(suit, rank);
          const texture = await Assets.load<Texture>({
            src: cardPath(suit, rank),
            data: { resolution },
          });
          this.textures.set(key, texture);
        }),
      ),
      Assets.load<Texture>({ src: "/assets/backs/blue.svg", data: { resolution } }).then(
        (t) => (this.cardBackTexture = t),
      ),
    ]);
  }

  private getTexture(card: Card) {
    return this.textures.get(cardKey(card.suit, card.rank));
  }

  // ── STOMP connection ───────────────────────────────────────────────────────

  connect(gameId: string) {
    this.gameId = gameId;
    const client = new Client({ brokerURL: wsUrl });
    client.onConnect = () => {
      console.log("[Hearts] STOMP connected, subscribing to game", this.gameId);
      this.subscription = this.stompClient.subscribe(
        `/user/queue/games/${this.gameId}`,
        (msg) => {
          try {
            const raw = JSON.parse(msg.body);
            if ("lobby" in raw) {
              const { lobby } = HeartsLobbyMessageSchema.parse(raw);
              console.log(
                `[Hearts] Lobby update — players: ${lobby.players.join(", ")}`,
              );
              this.lastLobbyState = lobby;
              this.lastMessage = null;
              this.renderLobby(lobby);
            } else {
              const message = HeartsMessageSchema.parse(raw);
              const round = message.state.rounds[message.state.rounds.length - 1];
              const trick = round?.tricks[round.tricks.length - 1];
              console.log(
                `[Hearts] Message received — hand:${message.context.hand.length}`,
                `valid:${message.context.validCards.length}`,
                `passing:${message.context.passingRequired}`,
                `trick cards:${trick?.playedCards.length ?? 0}/4`,
                `queue:${this.messageQueue.length + 1}`,
              );
              this.lastLobbyState = null;
              this.enqueue(message);
            }
          } catch (e) {
            console.error("[Hearts] Failed to parse message", e);
          }
        },
      );
      this.stompClient.publish({
        destination: `/games/hearts/${this.gameId}/load`,
        body: "",
      });
    };
    client.onWebSocketClose = () => {
      console.warn("[Hearts] WebSocket closed");
    };
    client.onWebSocketError = () => {
      console.error("[Hearts] WebSocket error — redirecting to login");
      window.location.href = loginUrl;
    };
    client.onStompError = (frame: Frame) => {
      console.error("[Hearts] STOMP error", frame.headers["message"]);
    };
    client.onDisconnect = () => {
      console.warn("[Hearts] STOMP disconnected");
    };
    this.stompClient = client;
    this.stompClient.activate();
  }

  private enqueue(message: HeartsMessage) {
    this.messageQueue.push(message);
    if (!this.isProcessing) this.processQueue();
  }

  private async processQueue() {
    this.isProcessing = true;
    let seq = 0;
    while (this.messageQueue.length > 0) {
      const msg = this.messageQueue.shift()!;
      seq++;
      console.log(
        `[Hearts] Processing message #${seq} (${this.messageQueue.length} remaining in queue)`,
      );
      await this.render(msg);
      console.log(`[Hearts] Render #${seq} complete`);
    }
    this.isProcessing = false;
    console.log("[Hearts] Queue drained");
  }

  // ── WebSocket actions ──────────────────────────────────────────────────────

  private sendPass() {
    if (this.selectedPassCards.length !== 3) return;
    this.stompClient.publish({
      destination: `/games/hearts/${this.gameId}/pass`,
      body: JSON.stringify({ cards: this.selectedPassCards }),
    });
    this.selectedPassCards = [];
    this.updatePassOverlay();
  }

  private sendPlay(card: Card) {
    this.stompClient.publish({
      destination: `/games/hearts/${this.gameId}/play`,
      body: JSON.stringify(card),
    });
  }

  private sendQuit() {
    this.stompClient.publish({
      destination: `/games/hearts/${this.gameId}/quit`,
      body: "",
    });
  }

  private sendAgain() {
    this.stompClient.publish({
      destination: `/games/hearts/${this.gameId}/again`,
      body: "",
    });
  }

  private sendStart() {
    this.stompClient.publish({
      destination: `/games/hearts/${this.gameId}/start`,
      body: "",
    });
  }

  // ── Lobby ──────────────────────────────────────────────────────────────────

  private removeLobbyOverlay() {
    if (this.lobbyOverlay) {
      this.lobbyOverlay.remove();
      this.lobbyOverlay = null;
    }
  }

  private renderLobby(lobby: HeartsLobbyState) {
    this.clearScene();
    this.removeLobbyOverlay();
    this.lobbyOverlay = renderLobbyOverlay({
      title: "Hearts",
      gameId: lobby.gameId,
      players: lobby.players,
      maxPlayers: 4,
      bgColor: "rgba(10,60,20,0.96)",
      onLeave: () => {
        this.sendQuit();
        window.location.href = "/";
      },
      onStart: () => this.sendStart(),
    });
  }

  // ── Rendering ─────────────────────────────────────────────────────────────

  private async render(message: HeartsMessage): Promise<void> {
    const { context, state } = message;
    const prevMessage = this.lastMessage;
    this.lastMessage = message;

    if (context.nextGameId) {
      window.location.href = `/games/hearts/${context.nextGameId}`;
      return;
    }

    // If pass phase ended, clear any stale pass selections
    if (!context.passingRequired) {
      this.selectedPassCards = [];
    }

    await this.maybeAnimateDeal(prevMessage, message);
    await this.maybeAnimateTrickWon(prevMessage, message);
    await this.maybeAnimatePassReceived(prevMessage, message);

    this.clearScene();
    this.renderScoreTable(context, state);

    if (state.over || state.quit) {
      this.renderGameOver(context, state);
      return;
    }

    if (state.rounds.length === 0) {
      this.renderStatus("Starting game...");
      return;
    }

    const round = state.rounds[state.rounds.length - 1];
    this.renderStatus(this.buildStatusText(context, state, round));
    this.renderOpponents(context, state);
    // Render hand before trick so it's visible during card animation
    this.renderHand(context, state, round);
    this.renderPassOverlay(context, round);
    await this.renderTrick(context, state, round, prevMessage);
  }

  private clearScene() {
    this.app.stage.removeChildren();
    this.removePassOverlay();
    this.removeScoreOverlay();
    this.removeLobbyOverlay();
    this.removeMenuOverlay();
    this.trickContainer = null;
  }

  // ── Status bar ─────────────────────────────────────────────────────────────

  private renderStatus(text: string) {
    const style = new TextStyle({
      fontFamily: "Arial",
      fontSize: 22,
      fill: "white",
    });
    const label = new Text({ text, style });
    label.x = 16;
    label.y = 12;
    this.app.stage.addChild(label);

    const menuBtnStyle = new TextStyle({
      fontFamily: "Arial",
      fontSize: 18,
      fill: "#ffffff",
      fontWeight: "bold",
    });

    const menuBtn = new Text({ text: "Menu ▾", style: menuBtnStyle });
    menuBtn.anchor.set(1, 0);
    menuBtn.x = this.app.screen.width - 16;
    menuBtn.y = 14;
    menuBtn.eventMode = "static";
    menuBtn.cursor = "pointer";
    menuBtn.on("pointerdown", () => this.toggleMenuOverlay());
    this.app.stage.addChild(menuBtn);
  }

  private buildStatusText(
    context: HeartsContext,
    state: HeartsState,
    round: RoundState,
  ): string {
    const passDir =
      round.pass === Pass.KEEPER ? " · KEEPER" : ` · Pass ${round.pass}`;
    const prefix = `Round ${round.number}${passDir}`;

    if (context.passingRequired) return `${prefix} · Select 3 cards to pass`;
    if (round.passComplete === false && round.pass !== Pass.KEEPER)
      return `${prefix} · Waiting for pass...`;

    if (context.validCards.length > 0) return `${prefix} · Your turn to play`;

    const nextPlayer =
      state.rounds.length > 0 ? this.getNextPlayer(state) : null;
    if (nextPlayer && nextPlayer !== context.player)
      return `${prefix} · Waiting for ${nextPlayer}`;

    return prefix;
  }

  private getNextPlayer(state: HeartsState): string | null {
    const round = state.rounds[state.rounds.length - 1];
    if (!round || round.tricks.length === 0) return null;
    const trick = round.tricks[round.tricks.length - 1];
    if (trick.playedCards.length === 4) return null;
    const played = new Set(trick.playedCards.map((pc) => pc.player));
    const leader = trick.leader;
    const leaderIdx = state.players.indexOf(leader);
    for (let i = 0; i < 4; i++) {
      const p = state.players[(leaderIdx + i) % 4];
      if (!played.has(p)) return p;
    }
    return null;
  }

  // ── Opponent hand counts ───────────────────────────────────────────────────

  private renderOpponents(context: HeartsContext, state: HeartsState) {
    if (state.players.length < 4) return;
    const myIdx = state.players.indexOf(context.player);
    const positions = this.compassPositions(myIdx, state.players);
    const round = state.rounds[state.rounds.length - 1];

    for (const { name, pos } of positions) {
      if (name === context.player) continue;
      const handSize = this.getOpponentHandSize(name, round);
      this.renderOpponentArc(name, pos as "N" | "W" | "E", handSize);
    }
  }

  private renderOpponentArc(name: string, pos: "N" | "W" | "E", cardCount: number) {
    const sw = this.app.screen.width;
    const sh = this.app.screen.height;
    const ocw = this.oppCardW;
    const och = this.oppCardH;
    const R = this.oppArcRadius;
    const TOP_MARGIN = 50;
    const SIDE_MARGIN = 16;

    if (this.cardBackTexture && cardCount > 0) {
      const container = new Container();
      container.filters = [
        new DropShadowFilter({ alpha: 0.5, blur: 3, distance: 3, padding: 20 }),
      ];

      for (let i = 0; i < cardCount; i++) {
        const θ = (i - (cardCount - 1) / 2) * ANGLE_STEP;
        const sprite = new Sprite(this.cardBackTexture);
        sprite.width = ocw;
        sprite.height = och;
        sprite.anchor.set(0.5, 0.5);

        switch (pos) {
          case "N": {
            const arcCenterY = TOP_MARGIN + och / 2 - R;
            sprite.x = sw / 2 + R * Math.sin(θ);
            sprite.y = arcCenterY + R * Math.cos(θ);
            sprite.rotation = -θ;
            break;
          }
          case "W": {
            const arcCenterX = SIDE_MARGIN + ocw / 2 - R;
            sprite.x = arcCenterX + R * Math.cos(θ);
            sprite.y = sh / 2 + R * Math.sin(θ);
            sprite.rotation = θ - Math.PI / 2;
            break;
          }
          case "E": {
            const arcCenterX = sw - SIDE_MARGIN - ocw / 2 + R;
            sprite.x = arcCenterX - R * Math.cos(θ);
            sprite.y = sh / 2 + R * Math.sin(θ);
            sprite.rotation = Math.PI / 2 - θ;
            break;
          }
        }
        container.addChild(sprite);
      }
      this.app.stage.addChild(container);
    }

    const style = new TextStyle({ fontFamily: "Arial", fontSize: 14, fill: "#ccffcc" });
    const label = new Text({ text: name, style });

    switch (pos) {
      case "N":
        label.anchor.set(0.5, 0);
        label.x = sw / 2;
        label.y = TOP_MARGIN + och + 4;
        break;
      case "W":
        label.anchor.set(0, 0.5);
        label.x = SIDE_MARGIN + ocw / 2 + och / 2 + 6;
        label.y = sh / 2;
        break;
      case "E":
        label.anchor.set(1, 0.5);
        label.x = sw - SIDE_MARGIN - ocw / 2 - och / 2 - 6;
        label.y = sh / 2;
        break;
    }
    this.app.stage.addChild(label);
  }

  private getOpponentHandSize(player: string, round: RoundState): number {
    // Count played cards for this player across all tricks
    const played = round.tricks
      .flatMap((t) => t.playedCards)
      .filter((pc) => pc.player === player).length;
    return 13 - played;
  }

  // Compass positions relative to "S" (the human player)
  private compassPositions(
    myIdx: number,
    players: string[],
  ): Array<{ name: string; pos: "N" | "W" | "E" | "S" }> {
    const dirs: Array<"S" | "W" | "N" | "E"> = ["S", "W", "N", "E"];
    return players.map((name, i) => ({
      name,
      pos: dirs[(i - myIdx + 4) % 4] as "N" | "W" | "E" | "S",
    }));
  }

  // ── Current trick ──────────────────────────────────────────────────────────

  private async renderTrick(
    context: HeartsContext,
    state: HeartsState,
    round: RoundState,
    prevMessage: HeartsMessage | null,
  ): Promise<void> {
    if (round.tricks.length === 0) return;
    const trick = round.tricks[round.tricks.length - 1];
    if (trick.playedCards.length === 0) return;

    const myIdx = state.players.indexOf(context.player);
    const cw = this.cardW;
    const ch = this.cardH;
    const cx = this.app.screen.width / 2;
    const cy = this.app.screen.height / 2 - ch / 2 - 20;

    const slotOffsets: Record<string, [number, number]> = {
      S: [0, ch + 16],
      N: [0, -ch - 16],
      W: [-cw - 16, 0],
      E: [cw + 16, 0],
    };

    // Detect the card added since the last render
    const prevRound =
      prevMessage?.state.rounds[prevMessage.state.rounds.length - 1];
    const prevTrick = prevRound?.tricks.find((t) => t.number === trick.number);
    const prevCount = prevTrick?.playedCards.length ?? 0;
    const newPlayedCard =
      trick.playedCards.length > prevCount
        ? trick.playedCards[trick.playedCards.length - 1]
        : null;

    const spinDir = Math.random() < 0.5 ? 1 : -1;
    let animSprite: Sprite | null = null;
    let animFromX = 0,
      animFromY = 0,
      animToX = 0,
      animToY = 0;

    const container = new Container();
    const dropShadow = new DropShadowFilter({
      alpha: 0.6, // Shadow opacity (range 0 to 1)
      blur: 4, // Blur radius (0 = sharp)
      distance: 5, // Distance from the sprite
      padding: 20,
    });
    container.filters = [dropShadow];

    for (const { player, card } of trick.playedCards) {
      const playerIdx = state.players.indexOf(player);
      const dirs = ["S", "W", "N", "E"];
      const dir = dirs[(playerIdx - myIdx + 4) % 4];
      const [dx, dy] = slotOffsets[dir];

      const finalX = cx - cw / 2 + dx;
      const finalY = cy - ch / 2 + dy;

      const sprite = new Sprite(this.getTexture(card));
      sprite.width = cw;
      sprite.height = ch;

      if (newPlayedCard && player === newPlayedCard.player) {
        // Place card at its origin so there's no flash before animation starts
        switch (dir) {
          case "S":
            animFromX = cx - cw / 2;
            animFromY = this.app.screen.height;
            break;
          case "N":
            animFromX = cx - cw / 2;
            animFromY = -ch;
            break;
          case "W":
            animFromX = -cw;
            animFromY = cy - ch / 2;
            break;
          default: // E
            animFromX = this.app.screen.width;
            animFromY = cy - ch / 2;
        }
        sprite.x = animFromX;
        sprite.y = animFromY;
        sprite.rotation = spinDir * Math.PI * 1.5;
        animSprite = sprite;
        animToX = finalX;
        animToY = finalY;
      } else {
        sprite.x = finalX;
        sprite.y = finalY;
      }

      container.addChild(sprite);
    }

    this.trickContainer = container;
    this.app.stage.addChild(container);

    if (!animSprite) return;
    const { suit, rank } = newPlayedCard!.card;
    console.log(
      `[Hearts] Animating ${rank} of ${suit} played by ${newPlayedCard!.player}`,
    );
    playCardPlaySound();
    await this.animateCard(
      animSprite,
      animFromX,
      animFromY,
      animToX,
      animToY,
      450,
    );
    console.log(`[Hearts] Animation complete for ${rank} of ${suit}`);
  }

  private animateCard(
    sprite: Sprite,
    fromX: number,
    fromY: number,
    toX: number,
    toY: number,
    duration: number,
  ): Promise<void> {
    const startRotation = sprite.rotation;
    return new Promise((resolve) => {
      const startTime = performance.now();
      const tick = () => {
        const t = Math.min((performance.now() - startTime) / duration, 1);
        const ease = 1 - Math.pow(1 - t, 3);
        sprite.x = fromX + (toX - fromX) * ease;
        sprite.y = fromY + (toY - fromY) * ease;
        sprite.rotation = startRotation * (1 - ease);
        if (t >= 1) {
          this.app.ticker.remove(tick);
          resolve();
        }
      };
      this.app.ticker.add(tick);
    });
  }

  // ── Trick win animation ────────────────────────────────────────────────────

  private async maybeAnimateTrickWon(
    prevMessage: HeartsMessage | null,
    message: HeartsMessage,
  ): Promise<void> {
    if (!this.trickContainer || !prevMessage) return;

    const prevRound =
      prevMessage.state.rounds[prevMessage.state.rounds.length - 1];
    const prevTrick = prevRound?.tricks[prevRound.tricks.length - 1];
    if (!prevTrick?.winner) return;

    // Skip if the current message still shows the same complete trick — the
    // server emits separate card-played and trick-ended broadcasts that both
    // carry the same 4-card state; only animate on the transition away from it.
    const curRound = message.state.rounds[message.state.rounds.length - 1];
    const curTrick = curRound?.tricks[curRound.tricks.length - 1];
    if (
      curTrick?.number === prevTrick.number &&
      curTrick?.playedCards.length === 4
    )
      return;

    const winner = prevTrick.winner.player;
    const myIdx = message.state.players.indexOf(message.context.player);
    const winnerIdx = message.state.players.indexOf(winner);
    const dirs = ["S", "W", "N", "E"] as const;
    const dir = dirs[(winnerIdx - myIdx + 4) % 4];

    await this.animateTrickWon(dir);
  }

  private async animateTrickWon(dir: "S" | "N" | "W" | "E"): Promise<void> {
    if (!this.trickContainer) return;

    const sw = this.app.screen.width;
    const sh = this.app.screen.height;
    const cw = this.cardW;
    const ch = this.cardH;

    const targets: Record<string, [number, number]> = {
      S: [sw / 2 - cw / 2, sh + ch],
      N: [sw / 2 - cw / 2, -ch * 2],
      W: [-cw * 2, sh / 2 - ch / 2],
      E: [sw + cw, sh / 2 - ch / 2],
    };
    const [targetX, targetY] = targets[dir];

    // Let the player see the completed trick before cards fly away
    await new Promise<void>((resolve) => setTimeout(resolve, 600));

    const sprites = [...this.trickContainer.children] as Sprite[];
    await Promise.all(
      sprites.map((sprite) =>
        this.animateCard(sprite, sprite.x, sprite.y, targetX, targetY, 400),
      ),
    );
  }

  // ── Pass received animation ───────────────────────────────────────────────

  private async maybeAnimatePassReceived(
    prevMessage: HeartsMessage | null,
    message: HeartsMessage,
  ): Promise<void> {
    if (!prevMessage) return;

    // Trigger on the passComplete: false → true transition within the same round.
    // Using passComplete (server-side round flag) rather than context.passingRequired
    // because passingRequired can go false when the player submits their own cards,
    // before all players have passed — making the transition unreliable.
    const curRound = message.state.rounds[message.state.rounds.length - 1];
    if (!curRound?.passComplete || curRound.pass === Pass.KEEPER) return;

    const prevRound =
      prevMessage.state.rounds[prevMessage.state.rounds.length - 1];
    if (
      !prevRound ||
      prevRound.number !== curRound.number ||
      prevRound.passComplete
    )
      return;

    const prevKeys = new Set(
      prevMessage.context.hand.map((c) => cardKey(c.suit, c.rank)),
    );
    const curKeys = new Set(
      message.context.hand.map((c) => cardKey(c.suit, c.rank)),
    );
    const passed = prevMessage.context.hand.filter(
      (c) => !curKeys.has(cardKey(c.suit, c.rank)),
    );
    const received = message.context.hand.filter(
      (c) => !prevKeys.has(cardKey(c.suit, c.rank)),
    );
    const staying = prevMessage.context.hand.filter((c) =>
      curKeys.has(cardKey(c.suit, c.rank)),
    );

    console.log(
      `[Hearts] Pass animation — passed: ${passed.map((c) => `${c.rank} of ${c.suit}`).join(", ")}`,
      `| received: ${received.map((c) => `${c.rank} of ${c.suit}`).join(", ")}`,
    );
    if (received.length === 0) return;

    await this.animatePassReceived(
      passed,
      received,
      staying,
      curRound.pass,
      message.context,
      message.state,
    );
  }

  private async animatePassReceived(
    passed: Card[],
    received: Card[],
    staying: Card[],
    pass: Pass,
    context: HeartsContext,
    state: HeartsState,
  ): Promise<void> {
    const sw = this.app.screen.width;
    const sh = this.app.screen.height;
    const cw = this.cardW;
    const ch = this.cardH;
    const spacing = cw + 12;
    const showY = sh - ch * 2.5 - 40;
    const cx = sw / 2;

    // Where passed cards fly to (in the pass direction)
    const passTarget: Record<Pass, [number, number]> = {
      [Pass.LEFT]: [-cw * 2, sh / 2 - ch / 2],
      [Pass.RIGHT]: [sw + cw, sh / 2 - ch / 2],
      [Pass.ACROSS]: [sw / 2 - cw / 2, -ch * 2],
      [Pass.KEEPER]: [sw / 2, sh / 2], // unreachable
    };
    // Where received cards arrive from (opposite of pass direction)
    const receiveSource: Record<Pass, [number, number]> = {
      [Pass.LEFT]: [sw + cw, sh / 2 - ch / 2],
      [Pass.RIGHT]: [-cw * 2, sh / 2 - ch / 2],
      [Pass.ACROSS]: [sw / 2 - cw / 2, -ch * 2],
      [Pass.KEEPER]: [sw / 2, sh / 2], // unreachable
    };

    const [toX, toY] = passTarget[pass];
    const [fromX, fromY] = receiveSource[pass];

    // Clear to intermediate state and restore persistent scene elements
    this.clearScene();
    this.renderScoreTable(context, state);
    const round = state.rounds[state.rounds.length - 1];
    this.renderStatus(this.buildStatusText(context, state, round));
    this.renderOpponents(context, state);
    const handContainer = this.renderStaticHand(staying);

    // Phase 1 — show the 3 passed cards above the hand, then fly them out
    const passContainer = new Container();
    passContainer.filters = [
      new DropShadowFilter({ alpha: 0.6, blur: 4, distance: 5, padding: 20 }),
    ];
    this.app.stage.addChild(passContainer);

    const passedSprites = passed.map((card, i) => {
      const sprite = new Sprite(this.getTexture(card)!);
      sprite.width = cw;
      sprite.height = ch;
      sprite.x = cx + (i - (passed.length - 1) / 2) * spacing - cw / 2;
      sprite.y = showY;
      passContainer.addChild(sprite);
      return sprite;
    });

    // Brief pause: player sees which cards are leaving
    await new Promise<void>((resolve) => setTimeout(resolve, 450));

    // Fly passed cards out
    await Promise.all(
      passedSprites.map((sprite) =>
        this.animateCard(sprite, sprite.x, sprite.y, toX, toY, 320),
      ),
    );
    this.app.stage.removeChild(passContainer);
    passContainer.destroy({ children: true });

    // Phase 2 — received cards fly in from the opposite edge
    const recvContainer = new Container();
    recvContainer.filters = [
      new DropShadowFilter({ alpha: 0.6, blur: 4, distance: 5, padding: 20 }),
    ];
    this.app.stage.addChild(recvContainer);

    const receivedEntries = received.map((card, i) => {
      const sprite = new Sprite(this.getTexture(card)!);
      sprite.width = cw;
      sprite.height = ch;
      sprite.x = fromX;
      sprite.y = fromY;
      recvContainer.addChild(sprite);
      return {
        sprite,
        landX: cx + (i - (received.length - 1) / 2) * spacing - cw / 2,
      };
    });

    await Promise.all(
      receivedEntries.map(({ sprite, landX }) =>
        this.animateCard(sprite, fromX, fromY, landX, showY, 400),
      ),
    );

    // Show received cards so the player can read them
    await new Promise<void>((resolve) => setTimeout(resolve, 950));

    // Slide into hand
    await Promise.all(
      receivedEntries.map(({ sprite, landX }) =>
        this.animateCard(sprite, landX, showY, landX, sh + ch, 260),
      ),
    );

    this.app.stage.removeChild(recvContainer);
    recvContainer.destroy({ children: true });
    this.app.stage.removeChild(handContainer);
    handContainer.destroy({ children: true });
  }

  // ── Deal animation ────────────────────────────────────────────────────────

  private async maybeAnimateDeal(
    prevMessage: HeartsMessage | null,
    message: HeartsMessage,
  ): Promise<void> {
    if (!prevMessage || !this.cardBackTexture) return;
    const curRound = message.state.rounds[message.state.rounds.length - 1];
    if (!curRound || curRound.tricks.length > 0 || message.context.hand.length === 0) return;
    const prevRound = prevMessage.state.rounds[prevMessage.state.rounds.length - 1];
    // Fire only when the round number advances (new round just started)
    if ((prevRound?.number ?? 0) >= curRound.number) return;
    await this.animateDeal(message.context, message.state, curRound);
  }

  private async animateDeal(
    context: HeartsContext,
    state: HeartsState,
    round: RoundState,
  ): Promise<void> {
    const sw = this.app.screen.width;
    const sh = this.app.screen.height;
    const cw = this.cardW;
    const ch = this.cardH;
    const ar = this.arcRadius;

    this.clearScene();
    this.renderScoreTable(context, state);
    this.renderStatus(this.buildStatusText(context, state, round));

    const myIdx = state.players.indexOf(context.player);
    const positions = this.compassPositions(myIdx, state.players);

    const sortedHand = sortHand(context.hand);
    const n = sortedHand.length;
    const arcCenterX = sw / 2;
    const arcCenterY = sh - ch / 2 - 50 + ar;

    // Player's cards land at their arc positions (face-up)
    const playerDests: Array<[number, number]> = sortedHand.map((_, i) => {
      const angle = (i - (n - 1) / 2) * ANGLE_STEP;
      return [arcCenterX + ar * Math.sin(angle), arcCenterY - ar * Math.cos(angle)];
    });

    // Opponent cards land at the center of their arc (face-down)
    const ocw = this.oppCardW;
    const och = this.oppCardH;
    const OPP_TOP = 50;
    const OPP_SIDE = 16;
    const opponentDests: Record<string, [number, number]> = {};
    for (const { name, pos } of positions) {
      if (name === context.player) continue;
      switch (pos) {
        case "N":
          opponentDests[name] = [sw / 2, OPP_TOP + och / 2];
          break;
        case "W":
          opponentDests[name] = [OPP_SIDE + ocw / 2, sh / 2];
          break;
        case "E":
          opponentDests[name] = [sw - OPP_SIDE - ocw / 2, sh / 2];
          break;
      }
    }

    // Deck: all cards start here (creates a visible pile that gets dealt away)
    const deckX = sw / 2;
    const deckY = sh * 0.4;

    const container = new Container();
    this.app.stage.addChild(container);

    // Deal order: S (player), then the opponents in table order
    const opponents = positions.filter((p) => p.pos !== "S").map((p) => p.name);
    const dealOrder = [context.player, ...opponents];

    const STAGGER_MS = 30;
    const FLIGHT_MS = 200;
    let playerCardIdx = 0;
    const allDone: Promise<void>[] = [];

    for (let dealRound = 0; dealRound < n; dealRound++) {
      for (let pi = 0; pi < dealOrder.length; pi++) {
        const playerName = dealOrder[pi];
        const seqIdx = dealRound * dealOrder.length + pi;
        const delay = seqIdx * STAGGER_MS;

        let destX: number, destY: number;
        if (playerName === context.player) {
          const idx = playerCardIdx++;
          [destX, destY] = playerDests[idx];
        } else {
          [destX, destY] = opponentDests[playerName] ?? [sw / 2, sh / 2];
        }
        const texture = this.cardBackTexture!;

        const sprite = new Sprite(texture);
        sprite.width = playerName === context.player ? cw : this.oppCardW;
        sprite.height = playerName === context.player ? ch : this.oppCardH;
        sprite.anchor.set(0.5, 0.5);
        sprite.x = deckX;
        sprite.y = deckY;
        container.addChild(sprite);

        allDone.push(
          new Promise<void>((resolve) => {
            window.setTimeout(() => {
              playDealSound();
              void this.animateCard(sprite, deckX, deckY, destX, destY, FLIGHT_MS).then(
                resolve,
              );
            }, delay);
          }),
        );
      }
    }

    await Promise.all(allDone);
    // Brief pause so the player sees their dealt hand before the normal render takes over
    await new Promise<void>((resolve) => setTimeout(resolve, 300));
    container.destroy({ children: true });
  }

  // Renders the given cards in the arc hand layout with no interaction — used
  // for the intermediate state during the pass animation.
  private renderStaticHand(cards: Card[]): Container {
    const sorted = sortHand(cards);
    const n = sorted.length;
    const cw = this.cardW;
    const ch = this.cardH;
    const ar = this.arcRadius;
    const arcCenterX = this.app.screen.width / 2;
    const arcCenterY = this.app.screen.height - ch / 2 - 50 + ar;

    const container = new Container();
    container.filters = [
      new DropShadowFilter({ alpha: 0.6, blur: 4, distance: 5, padding: 30 }),
    ];

    for (let i = 0; i < n; i++) {
      const sprite = new Sprite(this.getTexture(sorted[i])!);
      sprite.width = cw;
      sprite.height = ch;
      sprite.anchor.set(0.5, 0.5);
      const angle = (i - (n - 1) / 2) * ANGLE_STEP;
      sprite.x = arcCenterX + ar * Math.sin(angle);
      sprite.y = arcCenterY - ar * Math.cos(angle);
      sprite.rotation = angle;
      container.addChild(sprite);
    }

    this.app.stage.addChild(container);
    return container;
  }

  // ── Player hand ────────────────────────────────────────────────────────────

  private renderHand(
    context: HeartsContext,
    _state: HeartsState,
    round: RoundState,
  ) {
    const hand = sortHand(context.hand);
    if (hand.length === 0) return;

    const n = hand.length;
    const cw = this.cardW;
    const ch = this.cardH;
    const ar = this.arcRadius;

    const arcCenterX = this.app.screen.width / 2;
    const arcCenterY = this.app.screen.height - ch / 2 - 50 + ar;

    const container = new Container();
    const dropShadow = new DropShadowFilter({
      alpha: 0.6,
      blur: 4,
      distance: 5,
      padding: 30,
    });
    container.filters = [dropShadow];

    const isPassPhase = context.passingRequired;
    const validSet = new Set(
      context.validCards.map((c) => cardKey(c.suit, c.rank)),
    );

    for (let i = 0; i < n; i++) {
      const card = hand[i];
      const key = cardKey(card.suit, card.rank);
      const texture = this.getTexture(card);
      const sprite = new Sprite(texture);
      sprite.width = cw;
      sprite.height = ch;
      sprite.anchor.set(0.5, 0.5);

      const angle = (i - (n - 1) / 2) * ANGLE_STEP;
      const cardCenterX = arcCenterX + ar * Math.sin(angle);
      const cardCenterY = arcCenterY - ar * Math.cos(angle);

      const isSelected = this.selectedPassCards.some(
        (c) => c.suit === card.suit && c.rank === card.rank,
      );
      const isPassable =
        isPassPhase && (isSelected || this.selectedPassCards.length < 3);
      const isPlayable = !isPassPhase && validSet.has(key);

      // Selected cards lift outward along the arc radius
      const liftAmount = ch * 0.25;
      const liftX = isSelected ? Math.sin(angle) * liftAmount : 0;
      const liftY = isSelected ? -Math.cos(angle) * liftAmount : 0;

      sprite.x = cardCenterX + liftX;
      sprite.y = cardCenterY + liftY;
      sprite.rotation = angle;

      if (isPassPhase) {
        if (isPassable || isSelected) {
          sprite.eventMode = "static";
          sprite.cursor = "pointer";
          sprite.on("pointerdown", () => this.togglePassCard(card));
        } else {
          sprite.alpha = 0.5;
        }
      } else if (isPlayable) {
        sprite.eventMode = "static";
        sprite.cursor = "pointer";
        sprite.on("pointerdown", () => this.sendPlay(card));
      } else {
        sprite.alpha = !round.passComplete ? 0.7 : 0.5;
      }

      container.addChild(sprite);
    }

    this.app.stage.addChild(container);
  }

  private togglePassCard(card: Card) {
    const idx = this.selectedPassCards.findIndex(
      (c) => c.suit === card.suit && c.rank === card.rank,
    );
    if (idx >= 0) {
      this.selectedPassCards.splice(idx, 1);
    } else if (this.selectedPassCards.length < 3) {
      this.selectedPassCards.push(card);
    }
    if (this.lastMessage) void this.render(this.lastMessage);
  }

  // ── Menu overlay (HTML) ──────────────────────────────────────────────────

  private toggleMenuOverlay() {
    if (this.menuOverlay) {
      this.removeMenuOverlay();
    } else {
      this.renderMenuOverlay();
    }
  }

  private renderMenuOverlay() {
    this.injectMenuStyles();

    const overlay = document.createElement("div");
    overlay.id = "hearts-menu-overlay";

    const items: { label: string; action: () => void }[] = [
      { label: "Save and Exit", action: () => { window.location.href = "/"; } },
      { label: "Abandon Game",  action: () => { this.removeMenuOverlay(); this.sendQuit(); } },
    ];

    for (const item of items) {
      const btn = document.createElement("button");
      btn.className = "hearts-menu-item";
      btn.textContent = item.label;
      btn.addEventListener("click", item.action);
      overlay.appendChild(btn);
    }

    document.getElementById("app")!.appendChild(overlay);
    this.menuOverlay = overlay;

    // Defer so the opening click doesn't immediately trigger the outside-click handler
    setTimeout(() => {
      if (!this.menuOverlay) return; // menu was already closed before timeout fired
      const onOutsideClick = (e: PointerEvent) => {
        if (this.menuOverlay && !this.menuOverlay.contains(e.target as Node)) {
          this.removeMenuOverlay();
        }
      };
      this.menuOutsideClickHandler = onOutsideClick;
      document.addEventListener("pointerdown", onOutsideClick);
    }, 0);
  }

  private removeMenuOverlay() {
    if (this.menuOutsideClickHandler) {
      document.removeEventListener("pointerdown", this.menuOutsideClickHandler);
      this.menuOutsideClickHandler = null;
    }
    if (this.menuOverlay) {
      this.menuOverlay.remove();
      this.menuOverlay = null;
    }
  }

  private injectMenuStyles() {
    if (document.getElementById("hearts-menu-styles")) return;
    const style = document.createElement("style");
    style.id = "hearts-menu-styles";
    style.textContent = `
      #hearts-menu-overlay {
        position: fixed;
        top: 44px;
        right: 16px;
        display: flex;
        flex-direction: column;
        background: rgba(20,20,20,0.96);
        border: 1px solid rgba(255,255,255,0.15);
        border-radius: 8px;
        overflow: hidden;
        z-index: 20;
        min-width: 160px;
        box-shadow: 0 4px 16px rgba(0,0,0,0.5);
      }
      .hearts-menu-item {
        background: transparent;
        color: #fff;
        border: none;
        padding: 12px 20px;
        font-family: Arial, sans-serif;
        font-size: 15px;
        text-align: left;
        cursor: pointer;
        white-space: nowrap;
      }
      .hearts-menu-item:hover {
        background: rgba(255,255,255,0.1);
      }
      .hearts-menu-item:last-child {
        color: #ffaaaa;
      }
    `;
    document.head.appendChild(style);
  }

  // ── Pass overlay (HTML) ───────────────────────────────────────────────────

  private renderPassOverlay(context: HeartsContext, round: RoundState) {
    if (!context.passingRequired) return;

    this.injectPassStyles();

    const overlay = document.createElement("div");
    overlay.id = "hearts-pass-overlay";
    // Position above the top of the center card: center card top = cardH + 50px from bottom
    overlay.style.bottom = `${this.cardH + 50 + 12}px`;

    const dir = round.pass;
    const info = document.createElement("div");
    info.className = "hearts-pass-info";
    info.textContent = `Select 3 cards to pass ${dir}`;
    overlay.appendChild(info);

    const passArrows: Record<Pass, string> = {
      [Pass.LEFT]: "←",
      [Pass.RIGHT]: "→",
      [Pass.ACROSS]: "↑",
      [Pass.KEEPER]: "",
    };

    const btn = document.createElement("button");
    btn.id = "hearts-pass-btn";
    btn.textContent = `Pass ${dir} ${passArrows[dir]}`;
    btn.disabled = this.selectedPassCards.length !== 3;
    btn.addEventListener("click", () => this.sendPass());
    overlay.appendChild(btn);

    document.getElementById("app")!.appendChild(overlay);
    this.passOverlay = overlay;
  }

  private updatePassOverlay() {
    const btn = document.getElementById(
      "hearts-pass-btn",
    ) as HTMLButtonElement | null;
    if (btn) btn.disabled = this.selectedPassCards.length !== 3;
  }

  private removePassOverlay() {
    if (this.passOverlay) {
      this.passOverlay.remove();
      this.passOverlay = null;
    }
  }

  private injectPassStyles() {
    if (document.getElementById("hearts-pass-styles")) return;
    const style = document.createElement("style");
    style.id = "hearts-pass-styles";
    style.textContent = `
      #hearts-pass-overlay {
        position: fixed;
        left: 50%;
        transform: translateX(-50%);
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8px;
        z-index: 10;
      }
      .hearts-pass-info {
        background: rgba(0,0,0,0.7);
        color: #fff;
        padding: 6px 16px;
        border-radius: 6px;
        font-family: Arial, sans-serif;
        font-size: 16px;
      }
      #hearts-pass-btn {
        background: #c0392b;
        color: white;
        border: none;
        padding: 10px 28px;
        border-radius: 6px;
        font-size: 16px;
        font-family: Arial, sans-serif;
        cursor: pointer;
        font-weight: bold;
      }
      #hearts-pass-btn:disabled {
        background: #666;
        cursor: not-allowed;
      }
      #hearts-pass-btn:not(:disabled):hover {
        background: #e74c3c;
      }
    `;
    document.head.appendChild(style);
  }

  // ── Score table ───────────────────────────────────────────────────────────

  private renderScoreTable(context: HeartsContext, state: HeartsState) {
    this.injectScoreStyles();

    const isGameOver = state.over;
    const showAllRounds = isGameOver || this.scoreExpanded;
    const { players } = state;
    const totalScore = state.score ?? {};
    const completedRounds = showAllRounds
      ? state.rounds.filter((r) => r.complete && r.score)
      : [];

    const overlay = document.createElement("div");
    overlay.id = "hearts-score-overlay";
    if (isGameOver) overlay.classList.add("hearts-score-gameover");

    // Winner / tie line shown only on game over
    if (isGameOver) {
      const winnerDiv = document.createElement("div");
      winnerDiv.className = "hearts-score-winner-line";
      if (state.tied && state.tied.length > 1) {
        winnerDiv.textContent = `Tie: ${state.tied.join(", ")}`;
      } else if (state.winner) {
        winnerDiv.textContent =
          state.winner === context.player ? "You win!" : `${state.winner} wins!`;
      }
      overlay.appendChild(winnerDiv);
    }

    const table = document.createElement("table");
    table.className = "hearts-score-table";

    // Header row — toggle button in first column (hidden on game over), player names in the rest
    const thead = document.createElement("thead");
    const headerRow = document.createElement("tr");

    const toggleTh = document.createElement("th");
    if (!isGameOver) {
      const toggleBtn = document.createElement("button");
      toggleBtn.className = "hearts-score-toggle";
      toggleBtn.textContent = this.scoreExpanded ? "▾" : "▸";
      toggleBtn.addEventListener("click", () => {
        this.scoreExpanded = !this.scoreExpanded;
        this.removeScoreOverlay();
        this.renderScoreTable(context, state);
      });
      toggleTh.appendChild(toggleBtn);
    }
    headerRow.appendChild(toggleTh);

    for (const player of players) {
      const th = document.createElement("th");
      th.textContent = player;
      if (player === context.player) th.classList.add("hearts-score-me");
      if (isGameOver && player === state.winner) th.classList.add("hearts-score-winner-col");
      headerRow.appendChild(th);
    }
    thead.appendChild(headerRow);
    table.appendChild(thead);

    const tbody = document.createElement("tbody");

    // One row per completed round when expanded or on game over
    if (showAllRounds) {
      for (const round of completedRounds) {
        const row = document.createElement("tr");
        const labelTd = document.createElement("td");
        labelTd.textContent = `R${round.number}`;
        row.appendChild(labelTd);
        for (const player of players) {
          const td = document.createElement("td");
          td.textContent = String(round.score?.[player] ?? 0);
          if (player === context.player) td.classList.add("hearts-score-me-val");
          if (isGameOver && player === state.winner) td.classList.add("hearts-score-winner-val");
          row.appendChild(td);
        }
        tbody.appendChild(row);
      }
    }

    // Summary row — totals (labelled "Total" when expanded/game-over, blank when collapsed)
    const totalRow = document.createElement("tr");
    if (showAllRounds) totalRow.className = "hearts-score-total";
    const totalLabel = document.createElement("td");
    totalLabel.textContent = showAllRounds ? "Total" : "";
    totalRow.appendChild(totalLabel);
    for (const player of players) {
      const td = document.createElement("td");
      td.textContent = String(totalScore[player] ?? 0);
      if (player === context.player) td.classList.add("hearts-score-me-val");
      if (isGameOver && player === state.winner) td.classList.add("hearts-score-winner-val");
      totalRow.appendChild(td);
    }
    tbody.appendChild(totalRow);

    table.appendChild(tbody);
    overlay.appendChild(table);
    document.getElementById("app")!.appendChild(overlay);
    this.scoreOverlay = overlay;
  }

  private removeScoreOverlay() {
    if (this.scoreOverlay) {
      this.scoreOverlay.remove();
      this.scoreOverlay = null;
    }
  }

  private injectScoreStyles() {
    if (document.getElementById("hearts-score-styles")) return;
    const style = document.createElement("style");
    style.id = "hearts-score-styles";
    style.textContent = `
      #hearts-score-overlay {
        position: fixed;
        top: 48px;
        left: 16px;
        z-index: 10;
        background: rgba(0, 0, 0, 0.65);
        border-radius: 6px;
        padding: 4px 2px;
        user-select: none;
      }
      .hearts-score-table {
        border-collapse: collapse;
        font-family: Arial, sans-serif;
        font-size: 13px;
        color: #fff;
      }
      .hearts-score-table th,
      .hearts-score-table td {
        padding: 2px 10px;
        text-align: center;
      }
      .hearts-score-table th {
        color: #aaffaa;
        font-weight: bold;
      }
      .hearts-score-table th:first-child,
      .hearts-score-table td:first-child {
        text-align: left;
        padding-left: 6px;
        color: #888;
        min-width: 28px;
      }
      .hearts-score-me {
        color: #ffff88;
      }
      .hearts-score-me-val {
        color: #ffff88;
      }
      .hearts-score-total td {
        font-weight: bold;
        border-top: 1px solid rgba(255, 255, 255, 0.3);
      }
      .hearts-score-toggle {
        background: none;
        border: none;
        color: #888;
        cursor: pointer;
        font-size: 11px;
        padding: 0 2px;
        line-height: 1;
      }
      .hearts-score-toggle:hover {
        color: #fff;
      }
      #hearts-score-overlay.hearts-score-gameover {
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        padding: 16px 24px;
      }
      .hearts-score-winner-line {
        font-family: Arial, sans-serif;
        font-size: 22px;
        font-weight: bold;
        color: #ffd700;
        text-align: center;
        margin-bottom: 12px;
      }
      .hearts-score-winner-col {
        color: #ffd700;
      }
      .hearts-score-winner-val {
        color: #ffd700;
      }
    `;
    document.head.appendChild(style);
  }

  // ── Game over ──────────────────────────────────────────────────────────────

  private renderGameOver(context: HeartsContext, state: HeartsState) {
    // If this player quit, navigate home now that the server has confirmed the quit
    // and persisted quitAt — so the index page won't show a stale active game.
    if (state.quit && state.quitBy === context.player) {
      window.location.href = "/";
      return;
    }
    const style = new TextStyle({
      fontFamily: "Arial",
      fontSize: 56,
      fontWeight: "bold",
      fill: "white",
      dropShadow: {
        color: "#000000",
        blur: 4,
        angle: Math.PI / 6,
        distance: 6,
      },
    });
    const subStyle = new TextStyle({
      fontFamily: "Arial",
      fontSize: 30,
      fill: "white",
      dropShadow: {
        color: "#000000",
        blur: 4,
        angle: Math.PI / 6,
        distance: 6,
      },
    });

    const isQuit = state.quit;

    const titleText = isQuit
      ? (state.quitBy ? `${state.quitBy} quit the game` : "Game Quit")
      : "Game Over";
    const title = new Text({ text: titleText, style });
    title.anchor.set(0.5, isQuit ? 0.5 : 0);
    title.x = this.app.screen.width / 2;
    title.y = isQuit ? this.app.screen.height / 2 - 60 : 20;
    this.app.stage.addChild(title);

    const exitBtn = new Text({ text: "Exit", style: subStyle });
    exitBtn.anchor.set(0.5, isQuit ? 0.5 : 1);
    exitBtn.x = isQuit ? this.app.screen.width / 2 : this.app.screen.width / 2 + 80;
    exitBtn.y = isQuit ? this.app.screen.height / 2 + 60 : this.app.screen.height - 20;
    exitBtn.eventMode = "static";
    exitBtn.cursor = "pointer";
    exitBtn.on("pointerdown", () => {
      window.location.href = "/";
    });
    this.app.stage.addChild(exitBtn);

    if (!isQuit) {
      const againBtn = new Text({ text: "Play Again", style: subStyle });
      againBtn.anchor.set(0.5, 1);
      againBtn.x = this.app.screen.width / 2 - 80;
      againBtn.y = this.app.screen.height - 20;
      againBtn.eventMode = "static";
      againBtn.cursor = "pointer";
      againBtn.on("pointerdown", () => this.sendAgain());
      this.app.stage.addChild(againBtn);
    }
  }
}
