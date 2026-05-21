import { GameState, Card, Season, ResourceTarget } from "./kingshand.ts";
import {
  getCardDisplayInfo,
  formatModifier,
  targetEmoji,
  fingerprint,
} from "./cardCatalog.ts";
import {
  injectSharedStyles,
  buildDeckIndicator,
  attachDeckListeners,
  buildHandbookButton,
  attachHandbookListeners,
  buildOptionsButton,
  attachOptionsListeners,
} from "./sharedOverlays.ts";

// ─── Public interface ─────────────────────────────────────────────────────────

export interface PlayPhaseActions {
  placeCard:    (index: number) => void;
  demolishCard: (slot: number)  => void;
  endAndRun:    ()              => void;
}

// ─── Constants ────────────────────────────────────────────────────────────────

/** Mirrors GameEngine.DEMOLISH_COST_RATE — keep in sync with the Java constant. */
const DEMOLISH_COST_RATE = 0.20;

/** Mirrors GameEngine.MAX_TABLE_SIZE. */
const MAX_TABLE_SIZE = 5;

const HOME_URL = `${window.location.origin}/`;

const SEASON_META: Record<
  Season,
  { label: string; icon: string; headerColor: string; accentColor: string; bg: string }
> = {
  [Season.SPRING]: { label: "Spring", icon: "🌿", headerColor: "#274d30", accentColor: "#3d6b46", bg: "/img/bg-spring.png" },
  [Season.SUMMER]: { label: "Summer", icon: "☀️",  headerColor: "#5a3a0a", accentColor: "#8a5a10", bg: "/img/bg-summer.png" },
  [Season.FALL]:   { label: "Fall",   icon: "🍂", headerColor: "#5a2a10", accentColor: "#8a4020", bg: "/img/bg-fall.png" },
  [Season.WINTER]: { label: "Winter", icon: "❄️",  headerColor: "#1a2a40", accentColor: "#2a4060", bg: "/img/bg-winter.png" },
};

// ─── PlayPhaseScene ───────────────────────────────────────────────────────────

/**
 * Full-screen HTML overlay for the PLAY phase.  Renders the current season's
 * table (up to {@link MAX_TABLE_SIZE} slots with demolish buttons) and the
 * player's hand (up to 5 cards with play/upgrade buttons).  An "End and Run"
 * bar at the bottom resolves the table and advances to the next ADD phase.
 *
 * If {@code state.gameOver} is {@code true} the overlay switches to a
 * game-over screen instead.
 *
 * Lifecycle mirrors the other KH scenes: call {@link mount}, {@link update},
 * {@link unmount}.
 */
export class PlayPhaseScene {
  private readonly overlay: HTMLDivElement;
  private readonly actions: PlayPhaseActions;
  private state: GameState;

  constructor(state: GameState, actions: PlayPhaseActions) {
    this.state = state;
    this.actions = actions;
    this.injectStylesOnce();
    injectSharedStyles();
    this.overlay = document.createElement("div");
    this.overlay.id = "kh-play-phase-overlay";
    this.render();
  }

  public mount(parent: HTMLElement): void {
    parent.appendChild(this.overlay);
  }

  public unmount(): void {
    this.overlay.remove();
  }

  public update(state: GameState): void {
    this.state = state;
    this.render();
  }

  // ─── Rendering ──────────────────────────────────────────────────────────

  private render(): void {
    const { state } = this;

    if (state.gameOver) {
      this.renderGameOver();
      return;
    }

    const meta = SEASON_META[state.curSeason];
    const table = state.getCurrentTable();
    const tableFingerprints = new Set(table.map(fingerprint));

    this.overlay.innerHTML = `
      ${this.buildHeader(state, meta)}
      ${this.buildResourcesBar(state)}
      ${this.buildPhaseTracker()}
      <main class="kh-play-scene">
        ${this.buildTableSection(state, table, meta)}
        <div class="kh-play-ornament">✦ &nbsp; ✦ &nbsp; ✦</div>
        ${this.buildHandSection(state, table, tableFingerprints)}
        <div class="kh-play-action-bar">
          <div class="kh-play-end-hint">
            Resolve all table cards from left to right, then advance the season.
          </div>
          <button class="kh-play-btn-end" id="kh-play-btn-end">
            ⚔ &nbsp;End and Run →
          </button>
        </div>
      </main>
      ${this.buildPileIndicators(state)}
      ${this.buildPileModal()}
      ${buildDeckIndicator(state)}
      ${buildHandbookButton()}
      ${buildOptionsButton()}
      <div class="kh-play-toast" id="kh-play-toast"></div>
    `;

    // Apply season background image over a dark scrim for readability
    this.overlay.style.background =
      `linear-gradient(rgba(8,4,2,.48) 0%, rgba(8,4,2,.48) 100%), url('${meta.bg}') center/cover no-repeat`;

    this.attachListeners(state, tableFingerprints);
    this.attachPileListeners(state);
    attachDeckListeners(this.overlay, state);
    attachHandbookListeners(this.overlay);
    attachOptionsListeners(this.overlay);
  }

  private renderGameOver(): void {
    const { state } = this;
    const meta = SEASON_META[state.curSeason];

    this.overlay.innerHTML = `
      ${this.buildHeader(state, meta)}
      ${this.buildResourcesBar(state)}
      <div class="kh-play-game-over">
        <div class="kh-play-go-icon">☠</div>
        <h2 class="kh-play-go-title">Your Kingdom Has Fallen</h2>
        <p class="kh-play-go-sub">Your people have perished. The land falls silent.</p>
        <div class="kh-play-go-stats">
          <div class="kh-play-go-stat">
            <div class="kh-play-go-stat-label">Years Reigned</div>
            <div class="kh-play-go-stat-val">${state.year}</div>
          </div>
          <div class="kh-play-go-stat">
            <div class="kh-play-go-stat-label">Final Season</div>
            <div class="kh-play-go-stat-val">${meta.icon} ${meta.label}</div>
          </div>
          <div class="kh-play-go-stat">
            <div class="kh-play-go-stat-label">Production</div>
            <div class="kh-play-go-stat-val">${Math.floor(state.production)} ⚒️</div>
          </div>
        </div>
        <button class="kh-play-go-btn" id="kh-play-go-btn">
          Return to Home Screen
        </button>
      </div>
    `;

    // Apply season background image on game-over screen too
    this.overlay.style.background =
      `linear-gradient(rgba(8,4,2,.6) 0%, rgba(8,4,2,.6) 100%), url('${meta.bg}') center/cover no-repeat`;

    this.overlay.insertAdjacentHTML("beforeend", buildHandbookButton());
    this.overlay.insertAdjacentHTML("beforeend", buildOptionsButton());

    const homeBtn = this.overlay.querySelector<HTMLButtonElement>("#kh-play-go-btn");
    if (homeBtn) {
      homeBtn.addEventListener("click", () => {
        window.location.href = HOME_URL;
      });
    }

    attachHandbookListeners(this.overlay);
    attachOptionsListeners(this.overlay);
  }

  // ─── Section builders ───────────────────────────────────────────────────

  private buildHeader(state: GameState, meta: (typeof SEASON_META)[Season]): string {
    return `
      <header class="kh-play-header" style="
        background: linear-gradient(180deg, ${meta.headerColor} 0%, ${meta.accentColor} 60%, ${meta.headerColor} 100%);
      ">
        <span class="kh-play-leaf">❧</span>
        <div class="kh-play-header-center">
          <h1>King's Hand</h1>
          <div class="kh-play-year-badge">
            <span>${meta.icon}</span>
            <span>YEAR ${state.year} — ${meta.label.toUpperCase()}</span>
            <span>${meta.icon}</span>
          </div>
        </div>
        <span class="kh-play-leaf">❧</span>
      </header>
    `;
  }

  private buildResourcesBar(state: GameState): string {
    return `
      <div class="kh-play-resources">
        <div class="kh-play-resource kh-play-pop">
          <span class="kh-play-res-icon">👥</span>
          <div>
            <div class="kh-play-res-label">Population</div>
            <div class="kh-play-res-value">${Math.floor(state.population)}</div>
          </div>
        </div>
        <div class="kh-play-res-divider"></div>
        <div class="kh-play-resource kh-play-prod">
          <span class="kh-play-res-icon">⚒️</span>
          <div>
            <div class="kh-play-res-label">Production</div>
            <div class="kh-play-res-value">${Math.floor(state.production)}</div>
          </div>
        </div>
        <div class="kh-play-res-divider"></div>
        <div class="kh-play-resource kh-play-food">
          <span class="kh-play-res-icon">🌾</span>
          <div>
            <div class="kh-play-res-label">Food</div>
            <div class="kh-play-res-value">${Math.floor(state.food)}</div>
          </div>
        </div>
      </div>
    `;
  }

  private buildPhaseTracker(): string {
    return `
      <div class="kh-play-phase-track">
        <div class="kh-play-phase-step">
          <span class="kh-play-phase-dot"></span>I. ADD
        </div>
        <span class="kh-play-phase-arrow">→</span>
        <div class="kh-play-phase-step">
          <span class="kh-play-phase-dot"></span>II. RANDOM EVENT
        </div>
        <span class="kh-play-phase-arrow">→</span>
        <div class="kh-play-phase-step kh-play-phase-active">
          <span class="kh-play-phase-dot"></span>III. PLAY
        </div>
      </div>
    `;
  }

  private buildTableSection(
    state: GameState,
    table: Card[],
    meta: (typeof SEASON_META)[Season],
  ): string {
    const slots: string[] = [];
    for (let i = 0; i < MAX_TABLE_SIZE; i++) {
      const card = table[i];
      slots.push(card ? this.buildTableCard(card, i, state) : this.buildEmptySlot());
    }

    return `
      <div class="kh-play-section">
        <div class="kh-play-section-header">
          <div class="kh-play-section-title">
            <span>${meta.icon}</span>
            <span>Your Table</span>
          </div>
          <div class="kh-play-table-count">${table.length} / ${MAX_TABLE_SIZE} slots</div>
        </div>
        <p class="kh-play-section-sub">
          Cards fire left-to-right when you End and Run.
          Demolishing a card costs 20% of its production requirement.
        </p>
        <div class="kh-play-table-slots">
          ${slots.join("")}
        </div>
      </div>
    `;
  }

  private buildTableCard(card: Card, slot: number, state: GameState): string {
    const info = getCardDisplayInfo(card);
    const demolishCost = Math.ceil(card.productionRequirement * DEMOLISH_COST_RATE);
    const canDemolish = state.production >= demolishCost;

    const effectsHTML = card.effects
      .map((e) => {
        const targetStr = typeof e.target === "string" ? e.target : "CARD";
        return `
          <div class="kh-play-tc-effect">
            <span class="kh-play-tc-et kh-play-tc-et-${targetStr.toLowerCase()}">
              ${targetEmoji(e.target)} ${targetStr}
            </span>
            <span class="kh-play-tc-mod">${formatModifier(e.modifier, e.valueType)}</span>
            <span class="kh-play-tc-freq">${card.frequencyType === "RECURRING" ? "/ season" : "once"}</span>
          </div>
        `;
      })
      .join("");

    return `
      <div class="kh-play-table-card" data-slot="${slot}">
        <div class="kh-play-tc-header">
          <div class="kh-play-tc-icon">${info.icon}</div>
          <div class="kh-play-tc-meta">
            <div class="kh-play-tc-name">${info.name}</div>
            <div class="kh-play-tc-freq-badge ${card.frequencyType === "RECURRING" ? "kh-play-freq-recurring" : "kh-play-freq-single"}">
              ${card.frequencyType}
            </div>
          </div>
        </div>
        <div class="kh-play-tc-effects">${effectsHTML}</div>
        <div class="kh-play-tc-footer">
          <button class="kh-play-btn-demolish" data-slot="${slot}" ${!canDemolish ? "disabled" : ""}>
            🔨 Demolish
            <span class="kh-play-demolish-cost">${demolishCost} ⚒️</span>
          </button>
          ${!canDemolish ? `<div class="kh-play-tc-warn">Cannot afford</div>` : ""}
        </div>
      </div>
    `;
  }

  private buildEmptySlot(): string {
    return `
      <div class="kh-play-empty-slot">
        <span class="kh-play-empty-icon">+</span>
        <span class="kh-play-empty-label">Open Slot</span>
      </div>
    `;
  }

  private buildHandSection(
    state: GameState,
    table: Card[],
    tableFingerprints: Set<string>,
  ): string {
    if (state.hand.length === 0) {
      return `
        <div class="kh-play-section">
          <div class="kh-play-section-header">
            <div class="kh-play-section-title"><span>✋</span><span>Your Hand</span></div>
          </div>
          <div class="kh-play-empty-hand">
            No cards remain in hand — you may End and Run or have already played everything.
          </div>
        </div>
      `;
    }

    const cardsHTML = state.hand
      .map((card, i) => this.buildHandCard(card, i, state, table, tableFingerprints))
      .join("");

    const colCount = Math.min(state.hand.length, 3);

    return `
      <div class="kh-play-section">
        <div class="kh-play-section-header">
          <div class="kh-play-section-title">
            <span>✋</span>
            <span>Your Hand</span>
          </div>
          <div class="kh-play-table-count">${state.hand.length} card${state.hand.length !== 1 ? "s" : ""}</div>
        </div>
        <p class="kh-play-section-sub">
          You draw 3 cards each phase. Play them onto your table — unplayed cards are discarded when you End and Run.
        </p>
        <div class="kh-play-hand-grid kh-play-hand-cols-${colCount}">
          ${cardsHTML}
        </div>
      </div>
    `;
  }

  private buildHandCard(
    card: Card,
    index: number,
    state: GameState,
    table: Card[],
    tableFingerprints: Set<string>,
  ): string {
    const info = getCardDisplayInfo(card);
    const fp = fingerprint(card);
    const isUpgrade = tableFingerprints.has(fp);
    const tableFull = table.length >= MAX_TABLE_SIZE;

    const canAffordProd = state.production >= card.productionRequirement;
    const canAffordPop  = state.population  >= card.populationRequirement;
    const canAfford = canAffordProd && canAffordPop;
    const hasSlot   = !tableFull || isUpgrade;    // upgrade always has "space"
    const canPlay   = canAfford && hasSlot;

    let btnText    = isUpgrade ? "✦ Upgrade" : "⚔ Play";
    let btnExtra   = "";
    let unavailable = "";

    if (!canAfford) {
      btnExtra    = "disabled";
      unavailable = !canAffordProd
        ? `⚒️ Need ${card.productionRequirement} production`
        : `👥 Need ${card.populationRequirement} population`;
    } else if (!hasSlot) {
      btnExtra    = "disabled";
      unavailable = "Table is full";
    }

    const tagsHTML = [
      `<span class="kh-play-tag kh-play-tag-season">${state.curSeason}</span>`,
      `<span class="kh-play-tag ${card.frequencyType === "RECURRING" ? "kh-play-tag-recurring" : "kh-play-tag-single"}">${card.frequencyType}</span>`,
      ...info.tags.map((t) => `<span class="kh-play-tag kh-play-tag-category">${t}</span>`),
      isUpgrade ? `<span class="kh-play-tag kh-play-tag-upgrade">UPGRADES TABLE</span>` : "",
    ].join("");

    const effectsHTML = card.effects
      .map((e) => {
        const targetStr = typeof e.target === "string" ? e.target : "CARD";
        return `
          <div class="kh-play-hc-effect-row">
            <span class="kh-play-hc-et kh-play-hc-et-${targetStr.toLowerCase()}">
              ${targetEmoji(e.target)} ${targetStr}
            </span>
            <span class="kh-play-hc-mod">${formatModifier(e.modifier, e.valueType)}</span>
            <span class="kh-play-hc-freq">${card.frequencyType === "RECURRING" ? "/ season" : "once"}</span>
          </div>
        `;
      })
      .join("");

    return `
      <div class="kh-play-hand-card ${!canPlay ? "kh-play-hc-unplayable" : ""} ${isUpgrade ? "kh-play-hc-upgradeable" : ""}"
           data-index="${index}">
        <div class="kh-play-hc-header ${isUpgrade ? "kh-play-hc-header-upgrade" : ""}">
          <div class="kh-play-hc-name">${info.name}</div>
          <div class="kh-play-hc-tags">${tagsHTML}</div>
          <div class="kh-play-hc-icon">${info.icon}</div>
        </div>
        <div class="kh-play-hc-body">
          <div class="kh-play-hc-desc">${info.description}</div>
          <div class="kh-play-hc-effects-title">Effects</div>
          ${effectsHTML}
          <div class="kh-play-hc-costs">
            <div class="kh-play-hc-cost-item">
              <span class="kh-play-hc-cost-label">⚒️ Cost</span>
              <span class="kh-play-hc-cost-val kh-play-hc-cost-prod">${card.productionRequirement}</span>
            </div>
            <div class="kh-play-hc-cost-item">
              <span class="kh-play-hc-cost-label">👥 Pop. Req</span>
              <span class="kh-play-hc-cost-val kh-play-hc-cost-pop">${card.populationRequirement}</span>
            </div>
            ${unavailable ? `<span class="kh-play-hc-unavailable">${unavailable}</span>` : ""}
          </div>
          <button class="kh-play-btn-play ${isUpgrade ? "kh-play-btn-upgrade" : ""}"
                  data-index="${index}" ${btnExtra}>
            ${btnText}
          </button>
        </div>
      </div>
    `;
  }

  // ─── Pile indicators & modal ─────────────────────────────────────────────

  private buildPileIndicators(state: GameState): string {
    const drawCount    = state.drawPile.get(state.curSeason)?.length ?? 0;
    const discardCount = state.discardPile.get(state.curSeason)?.length ?? 0;

    return `
      <!-- Draw pile — bottom left -->
      <button class="kh-pile-icon kh-pile-draw" data-pile="draw" title="Draw Pile (this season)">
        <span class="kh-pile-emoji">🗂</span>
        <span class="kh-pile-label">Draw</span>
        <span class="kh-pile-badge">${drawCount}</span>
      </button>

      <!-- Discard pile — bottom right -->
      <button class="kh-pile-icon kh-pile-discard" data-pile="discard" title="Discard Pile (this season)">
        <span class="kh-pile-emoji">🗂</span>
        <span class="kh-pile-label">Discard</span>
        <span class="kh-pile-badge">${discardCount}</span>
      </button>
    `;
  }

  private buildPileModal(): string {
    return `
      <div class="kh-pile-modal-backdrop" id="kh-pile-modal-backdrop" style="display:none;">
        <div class="kh-pile-modal">
          <div class="kh-pile-modal-header">
            <span class="kh-pile-modal-title" id="kh-pile-modal-title"></span>
            <button class="kh-pile-modal-close" id="kh-pile-modal-close">✕</button>
          </div>
          <div class="kh-pile-modal-tabs" id="kh-pile-modal-tabs"></div>
          <div class="kh-pile-modal-body" id="kh-pile-modal-body"></div>
        </div>
      </div>
    `;
  }

  private openPileModal(
    state: GameState,
    pileType: "draw" | "discard",
  ): void {
    const backdrop = this.overlay.querySelector<HTMLDivElement>("#kh-pile-modal-backdrop");
    const titleEl  = this.overlay.querySelector<HTMLSpanElement>("#kh-pile-modal-title");
    const tabsEl   = this.overlay.querySelector<HTMLDivElement>("#kh-pile-modal-tabs");
    const bodyEl   = this.overlay.querySelector<HTMLDivElement>("#kh-pile-modal-body");
    if (!backdrop || !titleEl || !tabsEl || !bodyEl) return;

    const pile  = pileType === "draw" ? state.drawPile : state.discardPile;
    const cards = pile.get(state.curSeason) ?? [];
    const meta  = SEASON_META[state.curSeason];
    titleEl.textContent = pileType === "draw"
      ? `Draw Pile — ${meta.label}`
      : `Discard Pile — ${meta.label}`;
    tabsEl.innerHTML = "";
    bodyEl.innerHTML = cards.length === 0
      ? `<div class="kh-pile-empty">No cards here.</div>`
      : cards.map((c) => {
          const info = getCardDisplayInfo(c);
          const effectsHTML = c.effects.map((e) => {
            const emoji = e.target === ResourceTarget.FOOD       ? "🌾"
                        : e.target === ResourceTarget.PRODUCTION  ? "⚒️"
                        : e.target === ResourceTarget.POPULATION  ? "👥" : "🃏";
            return `<span class="kh-cc-effect">${emoji} ${formatModifier(e.modifier, e.valueType)}${c.frequencyType === "RECURRING" ? "/s" : " once"}</span>`;
          }).join("");
          return `
            <div class="kh-compact-card">
              <div class="kh-cc-left">
                <span class="kh-cc-icon">${info.icon}</span>
                <div>
                  <div class="kh-cc-name">${info.name}</div>
                  <div class="kh-cc-desc">${info.description}</div>
                </div>
              </div>
              <div class="kh-cc-right">
                <span class="kh-cc-freq ${c.frequencyType === "RECURRING" ? "kh-cc-recurring" : "kh-cc-single"}">${c.frequencyType}</span>
                <div class="kh-cc-effects">${effectsHTML}</div>
                <div class="kh-cc-costs">⚒️ ${c.productionRequirement} &nbsp; 👥 ${c.populationRequirement}</div>
              </div>
            </div>
          `;
        }).join("");

    backdrop.style.display = "flex";
  }

  private attachPileListeners(state: GameState): void {
    // Pile icon buttons (draw / discard only — deck is handled by sharedOverlays)
    this.overlay.querySelectorAll<HTMLButtonElement>(".kh-pile-icon").forEach((btn) => {
      btn.addEventListener("click", () => {
        this.openPileModal(state, btn.dataset.pile as "draw" | "discard");
      });
    });

    // Close modal
    const closeBtn = this.overlay.querySelector<HTMLButtonElement>("#kh-pile-modal-close");
    const backdrop = this.overlay.querySelector<HTMLDivElement>("#kh-pile-modal-backdrop");
    if (closeBtn && backdrop) {
      closeBtn.addEventListener("click", () => { backdrop.style.display = "none"; });
      backdrop.addEventListener("click", (e) => {
        if (e.target === backdrop) backdrop.style.display = "none";
      });
    }
  }

  // ─── Event listeners ────────────────────────────────────────────────────

  private attachListeners(state: GameState, tableFingerprints: Set<string>): void {
    // Demolish buttons on table cards
    this.overlay
      .querySelectorAll<HTMLButtonElement>(".kh-play-btn-demolish")
      .forEach((btn) => {
        btn.addEventListener("click", () => {
          const slot = Number(btn.dataset.slot);
          this.disableAllActions();
          this.showToast("Demolishing…");
          this.actions.demolishCard(slot);
        });
      });

    // Play / Upgrade buttons on hand cards
    this.overlay
      .querySelectorAll<HTMLButtonElement>(".kh-play-btn-play")
      .forEach((btn) => {
        btn.addEventListener("click", () => {
          const index = Number(btn.dataset.index);
          const fp = fingerprint(state.hand[index]);
          const isUpgrade = tableFingerprints.has(fp);
          this.disableAllActions();
          this.showToast(isUpgrade ? "Upgrading table card…" : "Playing card…");
          this.actions.placeCard(index);
        });
      });

    // End and Run
    const endBtn = this.overlay.querySelector<HTMLButtonElement>("#kh-play-btn-end");
    if (endBtn) {
      endBtn.addEventListener("click", () => {
        this.disableAllActions();
        this.showToast("Running the table — advancing the season…");
        this.actions.endAndRun();
      });
    }
  }

  private disableAllActions(): void {
    this.overlay
      .querySelectorAll<HTMLButtonElement>("button")
      .forEach((b) => (b.disabled = true));
  }

  // ─── Toast ──────────────────────────────────────────────────────────────

  private _toastTimer: ReturnType<typeof setTimeout> | null = null;

  private showToast(msg: string): void {
    const el = this.overlay.querySelector<HTMLDivElement>("#kh-play-toast");
    if (!el) return;
    el.textContent = msg;
    el.classList.add("kh-play-toast-visible");
    if (this._toastTimer) clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(
      () => el.classList.remove("kh-play-toast-visible"),
      3000,
    );
  }

  // ─── Style injection (runs once per page load) ───────────────────────────

  private injectStylesOnce(): void {
    if (document.getElementById("kh-play-scene-styles")) return;

    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href =
      "https://fonts.googleapis.com/css2?family=Cinzel:wght@400;600;700&family=Crimson+Text:ital,wght@0,400;0,600;1,400&display=swap";
    document.head.appendChild(link);

    const style = document.createElement("style");
    style.id = "kh-play-scene-styles";
    style.textContent = `
      /* ── Overlay ─────────────────────────────────────────────────────── */
      #kh-play-phase-overlay {
        position: fixed;
        inset: 0;
        overflow-y: auto;
        z-index: 20;
        font-family: 'Crimson Text', Georgia, serif;
        color: #261408;
        background:
          radial-gradient(ellipse at 20% 10%, #c8e890 0%, transparent 50%),
          radial-gradient(ellipse at 80% 80%, #e0d080 0%, transparent 50%),
          linear-gradient(160deg, #d8e8a8 0%, #f0e4b0 35%, #e8d490 100%);
      }

      /* ── Header ──────────────────────────────────────────────────────── */
      .kh-play-header {
        border-bottom: 4px solid #c49428;
        padding: 18px 48px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        box-shadow: 0 4px 20px rgba(0,0,0,.45);
      }
      .kh-play-leaf         { font-size: 2rem; color: #f0cc50; opacity: .55; user-select: none; }
      .kh-play-header-center { text-align: center; flex: 1; }
      .kh-play-header-center h1 {
        font-family: 'Cinzel', serif;
        font-size: 2.1rem;
        font-weight: 700;
        color: #f0cc50;
        letter-spacing: 4px;
        text-shadow: 0 2px 10px rgba(0,0,0,.6);
        margin: 0;
      }
      .kh-play-year-badge {
        display: inline-flex;
        align-items: center;
        gap: 10px;
        margin-top: 5px;
        background: rgba(255,255,255,.12);
        border: 1px solid rgba(255,255,255,.25);
        border-radius: 20px;
        padding: 3px 18px;
        font-family: 'Cinzel', serif;
        font-size: .78rem;
        letter-spacing: 2px;
        color: #c8f0b8;
      }

      /* ── Resources bar ───────────────────────────────────────────────── */
      .kh-play-resources {
        background: linear-gradient(90deg, #1e1208 0%, #2e1c0c 50%, #1e1208 100%);
        border-bottom: 3px solid #c49428;
        padding: 10px 48px;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 64px;
      }
      .kh-play-resource    { display: flex; align-items: center; gap: 10px; }
      .kh-play-res-icon    { font-size: 1.5rem; line-height: 1; }
      .kh-play-res-label   { font-family: 'Cinzel', serif; font-size: .62rem; letter-spacing: 1.5px; color: #a09070; text-transform: uppercase; }
      .kh-play-res-value   { font-family: 'Cinzel', serif; font-size: 1.25rem; font-weight: 700; }
      .kh-play-pop  .kh-play-res-value { color: #f09090; }
      .kh-play-prod .kh-play-res-value { color: #f0cc60; }
      .kh-play-food .kh-play-res-value { color: #90e060; }
      .kh-play-res-divider { width: 1px; height: 32px; background: rgba(255,255,255,.08); }

      /* ── Phase tracker ───────────────────────────────────────────────── */
      .kh-play-phase-track {
        background: #e8d49c;
        border-bottom: 2px solid #c4a060;
        padding: 12px 48px;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 6px;
      }
      .kh-play-phase-step {
        display: flex;
        align-items: center;
        gap: 7px;
        padding: 5px 18px;
        font-family: 'Cinzel', serif;
        font-size: .72rem;
        letter-spacing: 1.5px;
        color: #7a6a52;
        border-radius: 4px;
        opacity: .45;
      }
      .kh-play-phase-step.kh-play-phase-active {
        opacity: 1;
        color: #5a1a1a;
        background: rgba(90,26,26,.10);
        border: 1px solid rgba(90,26,26,.30);
        font-weight: 700;
      }
      .kh-play-phase-dot   { width: 7px; height: 7px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
      .kh-play-phase-arrow { color: #c4a060; font-size: 1rem; opacity: .6; padding: 0 4px; }

      /* ── Scene body ──────────────────────────────────────────────────── */
      .kh-play-scene { max-width: 1160px; margin: 0 auto; padding: 36px 28px 80px; }

      /* ── Section shared layout ───────────────────────────────────────── */
      .kh-play-section       { margin-bottom: 40px; }
      .kh-play-section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 6px;
      }
      .kh-play-section-title {
        font-family: 'Cinzel', serif;
        font-size: 1.1rem;
        font-weight: 700;
        letter-spacing: 2px;
        color: #3a2010;
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .kh-play-table-count {
        font-family: 'Cinzel', serif;
        font-size: .72rem;
        letter-spacing: 1px;
        color: #7a6a52;
        background: rgba(0,0,0,.05);
        padding: 3px 12px;
        border-radius: 12px;
        border: 1px solid #c4a060;
      }
      .kh-play-section-sub {
        font-style: italic;
        color: #7a6a52;
        font-size: .93rem;
        margin: 0 0 18px;
        line-height: 1.4;
      }

      /* ── Table slots ─────────────────────────────────────────────────── */
      .kh-play-table-slots {
        display: grid;
        grid-template-columns: repeat(5, 1fr);
        gap: 12px;
      }

      /* Table card — fortress/stone aesthetic */
      .kh-play-table-card {
        background: #f5f0e4;
        border: 2px solid #9a8060;
        border-radius: 12px;
        overflow: hidden;
        box-shadow: 2px 4px 12px rgba(0,0,0,.18), inset 0 0 30px rgba(200,180,130,.2);
        display: flex;
        flex-direction: column;
      }
      .kh-play-tc-header {
        background: linear-gradient(135deg, #2a1a08 0%, #4a2e14 100%);
        border-bottom: 2px solid #c49428;
        padding: 12px 14px;
        display: flex;
        align-items: center;
        gap: 10px;
      }
      .kh-play-tc-icon   { font-size: 1.6rem; line-height: 1; flex-shrink: 0; filter: drop-shadow(0 1px 3px rgba(0,0,0,.5)); }
      .kh-play-tc-meta   { flex: 1; min-width: 0; }
      .kh-play-tc-name   { font-family: 'Cinzel', serif; font-size: .88rem; font-weight: 700; color: #f0e8c0; letter-spacing: .3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
      .kh-play-tc-freq-badge {
        display: inline-block;
        font-family: 'Cinzel', serif;
        font-size: .52rem;
        font-weight: 600;
        letter-spacing: .8px;
        padding: 1px 6px;
        border-radius: 8px;
        margin-top: 4px;
      }
      .kh-play-freq-recurring { background: rgba(100,180,100,.25); color: #a0e0a0; }
      .kh-play-freq-single    { background: rgba(200,120,80,.30);  color: #f0c0a0; }

      .kh-play-tc-effects { padding: 10px 14px; flex: 1; }
      .kh-play-tc-effect  { display: flex; align-items: center; gap: 6px; padding: 2px 0; font-size: .82rem; }
      .kh-play-tc-et      { font-family: 'Cinzel', serif; font-size: .6rem; font-weight: 600; letter-spacing: .6px; min-width: 72px; display: flex; align-items: center; gap: 3px; }
      .kh-play-tc-et-food       { color: #3a7020; }
      .kh-play-tc-et-production { color: #a07010; }
      .kh-play-tc-et-population { color: #8b2020; }
      .kh-play-tc-mod     { font-weight: 700; font-size: .9rem; color: #3d6b46; }
      .kh-play-tc-freq    { font-size: .7rem; color: #a09070; font-style: italic; }

      .kh-play-tc-footer { padding: 8px 14px 12px; border-top: 1px dashed #c4a060; }
      .kh-play-btn-demolish {
        width: 100%;
        padding: 7px 0;
        border-radius: 7px;
        border: 1px solid #a06040;
        background: linear-gradient(135deg, #7a3020 0%, #5a1e10 100%);
        color: #f0d0b0;
        font-family: 'Cinzel', serif;
        font-size: .68rem;
        font-weight: 700;
        letter-spacing: .8px;
        cursor: pointer;
        transition: all .15s;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
      }
      .kh-play-btn-demolish:hover:not(:disabled) {
        background: linear-gradient(135deg, #9a3828 0%, #722416 100%);
        box-shadow: 0 2px 8px rgba(0,0,0,.28);
      }
      .kh-play-btn-demolish:disabled { background: #9a8a70; border-color: #b0a080; color: #d0c0a0; cursor: not-allowed; opacity: .65; }
      .kh-play-demolish-cost { font-weight: 400; opacity: .85; }
      .kh-play-tc-warn { text-align: center; font-family: 'Cinzel', serif; font-size: .58rem; color: #a06040; margin-top: 5px; letter-spacing: .5px; }

      /* Empty slot */
      .kh-play-empty-slot {
        border: 2px dashed #c4a060;
        border-radius: 12px;
        min-height: 130px;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 6px;
        color: #c4a060;
        opacity: .45;
      }
      .kh-play-empty-icon  { font-size: 1.6rem; line-height: 1; }
      .kh-play-empty-label { font-family: 'Cinzel', serif; font-size: .62rem; letter-spacing: 1px; }

      /* ── Ornament ────────────────────────────────────────────────────── */
      .kh-play-ornament { text-align: center; color: #c49428; font-size: 1.4rem; letter-spacing: 10px; opacity: .45; margin: 8px 0 32px; user-select: none; }

      /* ── Hand grid (max 3 cards) ─────────────────────────────────────── */
      .kh-play-hand-grid            { display: grid; gap: 22px; }
      .kh-play-hand-cols-1          { grid-template-columns: 1fr; max-width: 380px; }
      .kh-play-hand-cols-2          { grid-template-columns: repeat(2, 1fr); max-width: 720px; }
      .kh-play-hand-cols-3          { grid-template-columns: repeat(3, 1fr); }

      /* Hand card — same parchment/green aesthetic as ADD phase */
      .kh-play-hand-card {
        background: #fdf6e4;
        border: 2px solid #c4a060;
        border-radius: 14px;
        overflow: hidden;
        box-shadow: 3px 5px 18px rgba(0,0,0,.13), inset 0 0 60px rgba(255,245,210,.4);
        transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
      }
      .kh-play-hand-card:not(.kh-play-hc-unplayable):hover {
        transform: translateY(-6px) scale(1.012);
        box-shadow: 4px 12px 28px rgba(0,0,0,.2);
        border-color: #c49428;
      }
      .kh-play-hc-unplayable       { opacity: .65; filter: saturate(.55); }
      .kh-play-hc-upgradeable      { border-color: #a87820; }
      .kh-play-hc-upgradeable:not(.kh-play-hc-unplayable):hover { border-color: #c8a030; }

      /* Hand card header */
      .kh-play-hc-header {
        background: linear-gradient(135deg, #3d6b46 0%, #274d30 100%);
        padding: 16px 18px 13px;
        border-bottom: 2px solid #c49428;
        position: relative;
        min-height: 70px;
      }
      .kh-play-hc-header-upgrade {
        background: linear-gradient(135deg, #7a5a10 0%, #5a3e08 100%);
      }
      .kh-play-hc-icon { position: absolute; top: 13px; right: 16px; font-size: 2rem; filter: drop-shadow(0 2px 4px rgba(0,0,0,.4)); }
      .kh-play-hc-name { font-family: 'Cinzel', serif; font-size: 1.05rem; font-weight: 700; color: #f0e8c0; letter-spacing: .5px; padding-right: 40px; }
      .kh-play-hc-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 7px; }

      .kh-play-tag          { font-family: 'Cinzel', serif; font-size: .58rem; font-weight: 600; letter-spacing: 1px; padding: 2px 7px; border-radius: 10px; }
      .kh-play-tag-season   { background: rgba(255,255,255,.18); color: #c4f0b0; }
      .kh-play-tag-recurring { background: rgba(120,200,120,.25); color: #b0f0b0; }
      .kh-play-tag-single   { background: rgba(220,140,100,.3);  color: #ffd0b0; }
      .kh-play-tag-category { background: rgba(240,200,80,.22);  color: #f0e090; }
      .kh-play-tag-upgrade  { background: rgba(200,160,40,.35);  color: #f0d060; }

      /* Hand card body */
      .kh-play-hc-body { padding: 15px 18px; }
      .kh-play-hc-desc {
        font-style: italic;
        color: #7a6a52;
        font-size: .93rem;
        line-height: 1.45;
        padding-bottom: 12px;
        border-bottom: 1px dashed #c4a060;
        margin-bottom: 12px;
      }
      .kh-play-hc-effects-title {
        font-family: 'Cinzel', serif;
        font-size: .62rem;
        letter-spacing: 1.5px;
        color: #7a6a52;
        text-transform: uppercase;
        margin-bottom: 7px;
      }
      .kh-play-hc-effect-row   { display: flex; align-items: center; gap: 8px; padding: 3px 0; font-size: .88rem; }
      .kh-play-hc-et           { font-family: 'Cinzel', serif; font-size: .68rem; font-weight: 600; letter-spacing: .8px; min-width: 90px; display: flex; align-items: center; gap: 4px; }
      .kh-play-hc-et-food       { color: #3a7020; }
      .kh-play-hc-et-production { color: #a07010; }
      .kh-play-hc-et-population { color: #8b2020; }
      .kh-play-hc-mod   { font-weight: 700; font-size: 1rem; color: #3d6b46; }
      .kh-play-hc-freq  { font-size: .75rem; color: #a09070; font-style: italic; }

      /* Costs row */
      .kh-play-hc-costs { display: flex; align-items: center; gap: 18px; padding: 11px 0 12px; border-top: 1px dashed #c4a060; margin-top: 12px; }
      .kh-play-hc-cost-item  { display: flex; flex-direction: column; align-items: center; gap: 1px; }
      .kh-play-hc-cost-label { font-family: 'Cinzel', serif; font-size: .58rem; letter-spacing: 1px; color: #7a6a52; text-transform: uppercase; }
      .kh-play-hc-cost-val   { font-family: 'Cinzel', serif; font-size: 1.05rem; font-weight: 700; }
      .kh-play-hc-cost-prod  { color: #a07010; }
      .kh-play-hc-cost-pop   { color: #8b2020; }
      .kh-play-hc-unavailable { margin-left: auto; font-family: 'Cinzel', serif; font-size: .63rem; letter-spacing: .5px; color: #c04030; }

      /* Play / Upgrade buttons */
      .kh-play-btn-play {
        width: 100%;
        padding: 10px 0;
        border-radius: 8px;
        border: 2px solid #c49428;
        background: linear-gradient(135deg, #3d6b46 0%, #274d30 100%);
        color: #f0e8c0;
        font-family: 'Cinzel', serif;
        font-size: .82rem;
        font-weight: 700;
        letter-spacing: 1px;
        cursor: pointer;
        transition: all .15s;
        margin-top: 4px;
      }
      .kh-play-btn-play:hover:not(:disabled) {
        background: linear-gradient(135deg, #4e8858 0%, #325e3e 100%);
        box-shadow: 0 2px 10px rgba(0,0,0,.26);
      }
      .kh-play-btn-upgrade {
        background: linear-gradient(135deg, #8a6010 0%, #5a3e08 100%);
        border-color: #e0b040;
      }
      .kh-play-btn-upgrade:hover:not(:disabled) {
        background: linear-gradient(135deg, #a87820 0%, #7a5010 100%);
        box-shadow: 0 2px 10px rgba(200,160,40,.35);
      }
      .kh-play-btn-play:disabled { background: #a09070; border-color: #c0a060; color: #e0d0a0; cursor: not-allowed; opacity: .68; }

      /* Empty hand */
      .kh-play-empty-hand {
        text-align: center;
        font-style: italic;
        color: #7a6a52;
        font-size: 1.05rem;
        padding: 30px 0;
        border: 2px dashed #c4a060;
        border-radius: 12px;
        background: rgba(255,255,255,.2);
      }

      /* ── End and Run action bar ───────────────────────────────────────── */
      .kh-play-action-bar {
        text-align: center;
        padding-top: 16px;
        border-top: 2px dashed #c4a060;
      }
      .kh-play-end-hint {
        font-style: italic;
        color: #7a6a52;
        font-size: .95rem;
        margin-bottom: 18px;
      }
      .kh-play-btn-end {
        padding: 15px 52px;
        background: linear-gradient(135deg, #6b1a1a 0%, #4a0e0e 100%);
        color: #f0e8c0;
        border: 3px solid #c49428;
        border-radius: 11px;
        font-family: 'Cinzel', serif;
        font-size: 1.05rem;
        font-weight: 700;
        letter-spacing: 2px;
        cursor: pointer;
        transition: all .2s ease;
        box-shadow: 3px 5px 18px rgba(0,0,0,.3);
      }
      .kh-play-btn-end:hover:not(:disabled) {
        background: linear-gradient(135deg, #8b2222 0%, #5e1212 100%);
        box-shadow: 4px 8px 24px rgba(0,0,0,.4);
        transform: translateY(-2px);
      }
      .kh-play-btn-end:disabled { opacity: .5; cursor: not-allowed; transform: none; }

      /* ── Game Over screen ────────────────────────────────────────────── */
      .kh-play-game-over {
        max-width: 560px;
        margin: 60px auto;
        text-align: center;
        background: #2a0c0c;
        border: 3px solid #8b2020;
        border-radius: 20px;
        padding: 50px 40px 44px;
        box-shadow: 0 10px 50px rgba(0,0,0,.55);
      }
      .kh-play-go-icon  { font-size: 4rem; color: #c04030; margin-bottom: 20px; filter: drop-shadow(0 4px 12px rgba(200,60,48,.6)); }
      .kh-play-go-title { font-family: 'Cinzel', serif; font-size: 1.9rem; font-weight: 700; color: #f0cc50; letter-spacing: 3px; margin: 0 0 14px; text-shadow: 0 2px 10px rgba(0,0,0,.6); }
      .kh-play-go-sub   { font-style: italic; color: #c0a090; font-size: 1.1rem; margin: 0 0 36px; line-height: 1.5; }

      .kh-play-go-stats {
        display: flex;
        justify-content: center;
        gap: 40px;
        margin-bottom: 40px;
        padding-bottom: 30px;
        border-bottom: 1px solid rgba(255,255,255,.1);
      }
      .kh-play-go-stat       { display: flex; flex-direction: column; align-items: center; gap: 4px; }
      .kh-play-go-stat-label { font-family: 'Cinzel', serif; font-size: .65rem; letter-spacing: 1.5px; color: #7a5a4a; text-transform: uppercase; }
      .kh-play-go-stat-val   { font-family: 'Cinzel', serif; font-size: 1.2rem; font-weight: 700; color: #d0b090; }

      .kh-play-go-btn {
        padding: 13px 44px;
        background: linear-gradient(135deg, #5a2010 0%, #3a1008 100%);
        color: #f0cc80;
        border: 2px solid #a06030;
        border-radius: 10px;
        font-family: 'Cinzel', serif;
        font-size: .9rem;
        font-weight: 700;
        letter-spacing: 1.5px;
        cursor: pointer;
        transition: all .15s;
        box-shadow: 2px 4px 14px rgba(0,0,0,.4);
      }
      .kh-play-go-btn:hover { background: linear-gradient(135deg, #7a2818 0%, #4a1208 100%); box-shadow: 3px 6px 18px rgba(0,0,0,.5); }

      /* ── Toast ───────────────────────────────────────────────────────── */
      .kh-play-toast {
        position: fixed;
        bottom: 32px;
        left: 50%;
        transform: translateX(-50%) translateY(10px);
        background: #3a2010;
        color: #f0cc50;
        border: 2px solid #c49428;
        border-radius: 10px;
        padding: 12px 30px;
        font-family: 'Cinzel', serif;
        font-size: .88rem;
        letter-spacing: .8px;
        box-shadow: 0 6px 24px rgba(0,0,0,.45);
        opacity: 0;
        transition: opacity .25s ease, transform .25s ease;
        pointer-events: none;
        z-index: 999;
        white-space: nowrap;
      }
      .kh-play-toast.kh-play-toast-visible { opacity: 1; transform: translateX(-50%) translateY(0); }

      /* ── Pile indicator buttons (draw / discard only — play phase) ───── */
      .kh-pile-icon {
        position: fixed;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 3px;
        background: linear-gradient(160deg, #2a1a08 0%, #1a100a 100%);
        border: 2px solid #c49428;
        border-radius: 14px;
        padding: 10px 14px 8px;
        cursor: pointer;
        z-index: 50;
        box-shadow: 2px 4px 18px rgba(0,0,0,.5);
        transition: all .18s ease;
        min-width: 64px;
      }
      .kh-pile-icon:hover {
        background: linear-gradient(160deg, #3e2810 0%, #2a180c 100%);
        box-shadow: 3px 6px 24px rgba(0,0,0,.6);
        transform: translateY(-2px);
        border-color: #e0b040;
      }
      .kh-pile-draw    { bottom: 90px; left: 20px; }
      .kh-pile-discard { bottom: 90px; right: 20px; }
      .kh-pile-emoji { font-size: 1.5rem; line-height: 1; }
      .kh-pile-label {
        font-family: 'Cinzel', serif;
        font-size: .55rem;
        letter-spacing: 1px;
        color: #c4a060;
        text-transform: uppercase;
      }
      .kh-pile-badge {
        background: #c49428;
        color: #1a0e04;
        font-family: 'Cinzel', serif;
        font-size: .75rem;
        font-weight: 700;
        border-radius: 10px;
        padding: 1px 7px;
        min-width: 20px;
        text-align: center;
        margin-top: 2px;
      }

      /* ── Pile modal (draw / discard viewer) ──────────────────────────── */
      .kh-pile-modal-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(0,0,0,.65);
        z-index: 200;
        display: flex;
        align-items: center;
        justify-content: center;
        backdrop-filter: blur(2px);
      }
      .kh-pile-modal {
        background: linear-gradient(160deg, #2a1a08 0%, #1a100a 100%);
        border: 2px solid #c49428;
        border-radius: 18px;
        width: min(680px, 94vw);
        max-height: 78vh;
        display: flex;
        flex-direction: column;
        box-shadow: 0 16px 60px rgba(0,0,0,.7);
        overflow: hidden;
      }
      .kh-pile-modal-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 16px 22px;
        border-bottom: 2px solid #c49428;
        background: linear-gradient(90deg, #3a2010 0%, #2a1608 100%);
        flex-shrink: 0;
      }
      .kh-pile-modal-title {
        font-family: 'Cinzel', serif;
        font-size: 1rem;
        font-weight: 700;
        color: #f0cc50;
        letter-spacing: 2px;
      }
      .kh-pile-modal-close {
        background: none;
        border: 1px solid #a09070;
        color: #c4a060;
        border-radius: 8px;
        padding: 4px 10px;
        font-size: .9rem;
        cursor: pointer;
        font-family: 'Cinzel', serif;
        transition: all .15s;
      }
      .kh-pile-modal-close:hover { background: rgba(255,255,255,.08); color: #f0cc50; border-color: #c49428; }
      .kh-pile-modal-tabs { display: none; }
      .kh-pile-modal-body {
        overflow-y: auto;
        padding: 14px 18px;
        flex: 1;
      }
      .kh-pile-modal-body::-webkit-scrollbar { width: 5px; }
      .kh-pile-modal-body::-webkit-scrollbar-track { background: #1a100a; }
      .kh-pile-modal-body::-webkit-scrollbar-thumb { background: #c49428; border-radius: 4px; }
      .kh-pile-empty {
        text-align: center;
        font-style: italic;
        color: #7a6a52;
        padding: 30px 0;
        font-size: .95rem;
      }
    `;
    document.head.appendChild(style);
  }
}
