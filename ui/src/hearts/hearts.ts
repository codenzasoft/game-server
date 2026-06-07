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
});

const RoundStateSchema = z
  .object({
    number: z.number(),
    pass: z.nativeEnum(Pass),
    dealer: z.string(),
    passComplete: z.boolean(),
    tricks: z.array(TrickStateSchema),
    complete: z.boolean(),
  })
  .passthrough();
type RoundState = z.infer<typeof RoundStateSchema>;

const HeartsStateSchema = z.object({
  players: z.array(z.string()),
  rounds: z.array(RoundStateSchema),
  over: z.boolean(),
  quit: z.boolean(),
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

// ── Card dimensions ──────────────────────────────────────────────────────────

const CARD_W = 80;
const CARD_H = 112;

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

  // Pass selection state (cleared on each new message when not required)
  private selectedPassCards: Card[] = [];

  // HTML overlay for pass button
  private passOverlay: HTMLDivElement | null = null;

  // Last received message — used to re-render on pass card toggle
  private lastMessage: HeartsMessage | null = null;

  constructor(app: Application) {
    this.app = app;
  }

  // ── Pre-load all 52 card SVGs ──────────────────────────────────────────────

  async preloadTextures() {
    const suits = Object.values(Suit);
    const ranks = Object.values(Rank);
    const resolution = window.devicePixelRatio || 1;
    await Promise.all(
      suits.flatMap((suit) =>
        ranks.map(async (rank) => {
          const key = cardKey(suit, rank);
          const texture = await Assets.load<Texture>({
            src: cardPath(suit, rank),
            data: { resolution },
          });
          this.textures.set(key, texture);
        }),
      ),
    );
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
            const message = HeartsMessageSchema.parse(JSON.parse(msg.body));
            const round = message.state.rounds[message.state.rounds.length - 1];
            const trick = round?.tricks[round.tricks.length - 1];
            console.log(
              `[Hearts] Message received — hand:${message.context.hand.length}`,
              `valid:${message.context.validCards.length}`,
              `passing:${message.context.passingRequired}`,
              `trick cards:${trick?.playedCards.length ?? 0}/4`,
              `queue:${this.messageQueue.length + 1}`,
            );
            this.enqueue(message);
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

  // ── Rendering ─────────────────────────────────────────────────────────────

  private async render(message: HeartsMessage): Promise<void> {
    const { context, state } = message;
    const prevMessage = this.lastMessage;
    this.lastMessage = message;

    // If pass phase ended, clear any stale pass selections
    if (!context.passingRequired) {
      this.selectedPassCards = [];
    }

    this.clearScene();

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

    // Quit button
    const quitStyle = new TextStyle({
      fontFamily: "Arial",
      fontSize: 18,
      fill: "#ffaaaa",
      fontWeight: "bold",
    });
    const quit = new Text({ text: "Quit", style: quitStyle });
    quit.anchor.set(1, 0);
    quit.x = this.app.screen.width - 16;
    quit.y = 14;
    quit.eventMode = "static";
    quit.cursor = "pointer";
    quit.on("pointerdown", () => this.sendQuit());
    this.app.stage.addChild(quit);
  }

  private buildStatusText(
    context: HeartsContext,
    state: HeartsState,
    round: RoundState,
  ): string {
    const passDir = round.pass === Pass.KEEPER ? "" : ` · Pass ${round.pass}`;
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

    const style = new TextStyle({
      fontFamily: "Arial",
      fontSize: 16,
      fill: "#ccffcc",
    });

    for (const { name, pos } of positions) {
      if (name === context.player) continue;
      const handSize = this.getOpponentHandSize(name, round);
      const label = new Text({ text: `${name} (${handSize})`, style });

      switch (pos) {
        case "N":
          label.anchor.set(0.5, 0);
          label.x = this.app.screen.width / 2;
          label.y = 16;
          break;
        case "W":
          label.anchor.set(0, 0.5);
          label.x = 16;
          label.y = this.app.screen.height / 2;
          break;
        case "E":
          label.anchor.set(1, 0.5);
          label.x = this.app.screen.width - 16;
          label.y = this.app.screen.height / 2;
          break;
      }
      this.app.stage.addChild(label);
    }
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
    const cx = this.app.screen.width / 2;
    const cy = this.app.screen.height / 2 - CARD_H / 2 - 20;

    const slotOffsets: Record<string, [number, number]> = {
      S: [0, CARD_H + 16],
      N: [0, -CARD_H - 16],
      W: [-CARD_W - 16, 0],
      E: [CARD_W + 16, 0],
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

      const finalX = cx - CARD_W / 2 + dx;
      const finalY = cy - CARD_H / 2 + dy;

      const sprite = new Sprite(this.getTexture(card));
      sprite.width = CARD_W;
      sprite.height = CARD_H;

      if (newPlayedCard && player === newPlayedCard.player) {
        // Place card at its origin so there's no flash before animation starts
        switch (dir) {
          case "S":
            animFromX = cx - CARD_W / 2;
            animFromY = this.app.screen.height;
            break;
          case "N":
            animFromX = cx - CARD_W / 2;
            animFromY = -CARD_H;
            break;
          case "W":
            animFromX = -CARD_W;
            animFromY = cy - CARD_H / 2;
            break;
          default: // E
            animFromX = this.app.screen.width;
            animFromY = cy - CARD_H / 2;
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

    this.app.stage.addChild(container);

    if (!animSprite) return;
    const { suit, rank } = newPlayedCard!.card;
    console.log(
      `[Hearts] Animating ${rank} of ${suit} played by ${newPlayedCard!.player}`,
    );
    await this.animateCard(animSprite, animFromX, animFromY, animToX, animToY, 450);
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

  // ── Player hand ────────────────────────────────────────────────────────────

  private renderHand(
    context: HeartsContext,
    _state: HeartsState,
    round: RoundState,
  ) {
    const hand = sortHand(context.hand);
    if (hand.length === 0) return;

    const n = hand.length;
    // Large arc radius — center card highest, edges arc downward
    const ARC_RADIUS = 1800;
    const ANGLE_STEP = Math.PI / 80; // ~2.25° between adjacent cards

    const arcCenterX = this.app.screen.width / 2;
    const arcCenterY = this.app.screen.height - CARD_H / 2 - 50 + ARC_RADIUS;

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
      sprite.width = CARD_W;
      sprite.height = CARD_H;
      sprite.anchor.set(0.5, 0.5);

      const angle = (i - (n - 1) / 2) * ANGLE_STEP;
      const cardCenterX = arcCenterX + ARC_RADIUS * Math.sin(angle);
      const cardCenterY = arcCenterY - ARC_RADIUS * Math.cos(angle);

      const isSelected = this.selectedPassCards.some(
        (c) => c.suit === card.suit && c.rank === card.rank,
      );
      const isPassable =
        isPassPhase && (isSelected || this.selectedPassCards.length < 3);
      const isPlayable = !isPassPhase && validSet.has(key);

      // Selected cards lift outward along the arc radius
      const liftAmount = CARD_H * 0.25;
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

  // ── Pass overlay (HTML) ───────────────────────────────────────────────────

  private renderPassOverlay(context: HeartsContext, round: RoundState) {
    if (!context.passingRequired) return;

    this.injectPassStyles();

    const overlay = document.createElement("div");
    overlay.id = "hearts-pass-overlay";

    const dir = round.pass;
    const info = document.createElement("div");
    info.className = "hearts-pass-info";
    info.textContent = `Select 3 cards to pass ${dir}`;
    overlay.appendChild(info);

    const btn = document.createElement("button");
    btn.id = "hearts-pass-btn";
    btn.textContent = `Pass ${dir} →`;
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
        bottom: 180px;
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

  // ── Game over ──────────────────────────────────────────────────────────────

  private renderGameOver(context: HeartsContext, state: HeartsState) {
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

    const title = new Text({
      text: state.quit ? "Game Quit" : "Game Over",
      style,
    });
    title.anchor.set(0.5);
    title.x = this.app.screen.width / 2;
    title.y = this.app.screen.height / 2 - 60;
    this.app.stage.addChild(title);

    const exitBtn = new Text({ text: "Exit", style: subStyle });
    exitBtn.anchor.set(0.5);
    exitBtn.x = this.app.screen.width / 2;
    exitBtn.y = this.app.screen.height / 2 + 60;
    exitBtn.eventMode = "static";
    exitBtn.cursor = "pointer";
    exitBtn.on("pointerdown", () => {
      window.location.href = "/";
    });
    this.app.stage.addChild(exitBtn);
  }
}
