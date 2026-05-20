import { GameState, Season, Card } from "./kingshand.ts";
import {
  getCardDisplayInfo,
  formatModifier,
  targetEmoji,
} from "./cardCatalog.ts";
import {
  injectSharedStyles,
  buildDeckIndicator,
  attachDeckListeners,
  buildHandbookButton,
  attachHandbookListeners,
} from "./sharedOverlays.ts";

// ─── Public interface ─────────────────────────────────────────────────────────

export interface AddPhaseActions {
  reroll: () => void;
  pickCard: (index: number) => void;
  skipAdd: () => void;
}

// ─── Constants ────────────────────────────────────────────────────────────────

/** Mirrors GameEngine.REROLL_COST_STEP — keep in sync with the Java constant. */
const REROLL_COST_STEP = 10;

const SEASON_META: Record<
  Season,
  { label: string; icon: string; headerColor: string; accentColor: string; bg: string }
> = {
  [Season.SPRING]: {
    label: "Spring",
    icon: "🌿",
    headerColor: "#274d30",
    accentColor: "#3d6b46",
    bg: "/img/bg-spring.png",
  },
  [Season.SUMMER]: {
    label: "Summer",
    icon: "☀️",
    headerColor: "#5a3a0a",
    accentColor: "#8a5a10",
    bg: "/img/bg-summer.png",
  },
  [Season.FALL]: {
    label: "Fall",
    icon: "🍂",
    headerColor: "#5a2a10",
    accentColor: "#8a4020",
    bg: "/img/bg-fall.png",
  },
  [Season.WINTER]: {
    label: "Winter",
    icon: "❄️",
    headerColor: "#1a2a40",
    accentColor: "#2a4060",
    bg: "/img/bg-winter.png",
  },
};

// ─── AddPhaseScene ────────────────────────────────────────────────────────────

/**
 * Renders the ADD phase as a full-screen HTML overlay on top of the PixiJS
 * canvas.  Call {@link mount} to attach it to the DOM and {@link unmount} to
 * remove it.  Call {@link update} when a new {@link GameState} arrives while
 * the scene is already visible (e.g. after a reroll).
 */
export class AddPhaseScene {
  private readonly overlay: HTMLDivElement;
  private readonly actions: AddPhaseActions;
  private state: GameState;

  constructor(state: GameState, actions: AddPhaseActions) {
    this.state = state;
    this.actions = actions;
    this.injectStylesOnce();
    injectSharedStyles();
    this.overlay = document.createElement("div");
    this.overlay.id = "kh-add-phase-overlay";
    this.render();
  }

  /** Appends the overlay to {@code parent} and starts receiving events. */
  public mount(parent: HTMLElement): void {
    parent.appendChild(this.overlay);
  }

  /** Removes the overlay from the DOM. */
  public unmount(): void {
    this.overlay.remove();
  }

  /** Replaces the overlay content with data from a fresh state update. */
  public update(state: GameState): void {
    this.state = state;
    this.render();
  }

  // ─── Internal rendering ─────────────────────────────────────────────────

  private render(): void {
    const { state } = this;
    const season = state.curSeason;
    const meta = SEASON_META[season];
    const nextRerollCost = state.rerollCount * REROLL_COST_STEP;
    const canAffordReroll = state.production >= nextRerollCost;

    this.overlay.innerHTML = `
      ${this.buildHeader(state, meta)}
      ${this.buildResourcesBar(state)}
      ${this.buildPhaseTracker()}
      <main class="kh-scene">
        <div class="kh-prompt">
          <h2>Choose a Card to Add</h2>
          <p>Select one card to shuffle into your ${meta.label} deck, or pass.</p>
        </div>
        <div class="kh-cards-grid">
          ${state.addChoices.map((card, i) => this.buildCard(card, i, state)).join("")}
        </div>
        <div class="kh-ornament">✦ &nbsp; ✦ &nbsp; ✦</div>
        <div class="kh-action-bar">
          <button class="kh-btn-reroll" id="kh-btn-reroll"
            ${!canAffordReroll ? "disabled" : ""}>
            ⟳ &nbsp;Reroll
            <span class="kh-reroll-cost">(${nextRerollCost} ⚒️)</span>
          </button>
          <button class="kh-btn-skip" id="kh-btn-skip">
            Pass — Add Nothing
          </button>
        </div>
      </main>
      ${buildDeckIndicator(state)}
      ${buildHandbookButton()}
      <div class="kh-toast" id="kh-toast"></div>
    `;

    // Apply season background image over a dark scrim for readability
    this.overlay.style.background =
      `linear-gradient(rgba(8,4,2,.48) 0%, rgba(8,4,2,.48) 100%), url('${meta.bg}') center/cover no-repeat`;

    this.attachListeners(state);
    attachDeckListeners(this.overlay, state);
    attachHandbookListeners(this.overlay);
  }

  private buildHeader(
    state: GameState,
    meta: (typeof SEASON_META)[Season],
  ): string {
    return `
      <header class="kh-header" style="
        background: linear-gradient(180deg, ${meta.headerColor} 0%, ${meta.accentColor} 60%, ${meta.headerColor} 100%);
      ">
        <span class="kh-header-ornament">❧</span>
        <div class="kh-header-center">
          <h1>King's Hand</h1>
          <div class="kh-year-badge">
            <span>${meta.icon}</span>
            <span>YEAR ${state.year} — ${meta.label.toUpperCase()}</span>
            <span>${meta.icon}</span>
          </div>
        </div>
        <span class="kh-header-ornament">❧</span>
      </header>
    `;
  }

  private buildResourcesBar(state: GameState): string {
    return `
      <div class="kh-resources">
        <div class="kh-resource kh-pop">
          <span class="kh-res-icon">👥</span>
          <div>
            <div class="kh-res-label">Population</div>
            <div class="kh-res-value">${Math.floor(state.population)}</div>
          </div>
        </div>
        <div class="kh-res-divider"></div>
        <div class="kh-resource kh-prod">
          <span class="kh-res-icon">⚒️</span>
          <div>
            <div class="kh-res-label">Production</div>
            <div class="kh-res-value">${Math.floor(state.production)}</div>
          </div>
        </div>
        <div class="kh-res-divider"></div>
        <div class="kh-resource kh-food">
          <span class="kh-res-icon">🌾</span>
          <div>
            <div class="kh-res-label">Food</div>
            <div class="kh-res-value">${Math.floor(state.food)}</div>
          </div>
        </div>
      </div>
    `;
  }

  private buildPhaseTracker(): string {
    return `
      <div class="kh-phase-track">
        <div class="kh-phase-step kh-phase-active">
          <span class="kh-phase-dot"></span>I. ADD
        </div>
        <span class="kh-phase-arrow">→</span>
        <div class="kh-phase-step">
          <span class="kh-phase-dot"></span>II. RANDOM EVENT
        </div>
        <span class="kh-phase-arrow">→</span>
        <div class="kh-phase-step">
          <span class="kh-phase-dot"></span>III. PLAY
        </div>
      </div>
    `;
  }

  private buildCard(card: Card, index: number, state: GameState): string {
    const info = getCardDisplayInfo(card);
    const season = state.curSeason;

    const tagsHTML = [
      `<span class="kh-tag kh-tag-season">${season}</span>`,
      `<span class="kh-tag ${card.frequencyType === "RECURRING" ? "kh-tag-recurring" : "kh-tag-single"}">${card.frequencyType}</span>`,
      ...info.tags.map((t) => `<span class="kh-tag kh-tag-category">${t}</span>`),
    ].join("");

    const effectsHTML = card.effects
      .map((e) => {
        const targetStr =
          typeof e.target === "string" ? e.target : "CARD";
        return `
          <div class="kh-effect-row">
            <span class="kh-effect-target kh-et-${targetStr.toLowerCase()}">
              ${targetEmoji(e.target)} ${targetStr}
            </span>
            <span class="kh-effect-mod">${formatModifier(e.modifier, e.valueType)}</span>
            <span class="kh-effect-freq">${card.frequencyType === "RECURRING" ? "/ season" : "once"}</span>
          </div>
        `;
      })
      .join("");

    return `
      <div class="kh-card" data-index="${index}">
        <div class="kh-card-header">
          <div class="kh-card-name">${info.name}</div>
          <div class="kh-card-tags">${tagsHTML}</div>
          <div class="kh-card-icon">${info.icon}</div>
        </div>
        <div class="kh-card-body">
          <div class="kh-card-desc">${info.description}</div>
          <div class="kh-effects-title">Effects</div>
          ${effectsHTML}
          <div class="kh-card-costs">
            <div class="kh-cost-item">
              <span class="kh-cost-label">⚒️ To Play</span>
              <span class="kh-cost-val kh-cost-prod">${card.productionRequirement}</span>
            </div>
            <div class="kh-cost-item">
              <span class="kh-cost-label">👥 Pop. Req</span>
              <span class="kh-cost-val kh-cost-pop">${card.populationRequirement}</span>
            </div>
          </div>
          <button class="kh-btn-select" data-index="${index}">
            Add to Deck
          </button>
        </div>
      </div>
    `;
  }

  // ─── Event listeners ────────────────────────────────────────────────────

  private attachListeners(state: GameState): void {
    // Card select buttons
    this.overlay.querySelectorAll<HTMLButtonElement>(".kh-btn-select").forEach((btn) => {
      btn.addEventListener("click", () => {
        const index = Number(btn.dataset.index);
        this.actions.pickCard(index);
        // Optimistic UI: disable all interaction while server responds
        this.disableAllActions();
        this.showToast(`Adding card to your ${SEASON_META[state.curSeason].label} deck…`);
      });
    });

    // Reroll
    const rerollBtn = this.overlay.querySelector<HTMLButtonElement>("#kh-btn-reroll");
    if (rerollBtn) {
      rerollBtn.addEventListener("click", () => {
        this.actions.reroll();
        this.disableAllActions();
        this.showToast("Drawing new cards…");
      });
    }

    // Skip
    const skipBtn = this.overlay.querySelector<HTMLButtonElement>("#kh-btn-skip");
    if (skipBtn) {
      skipBtn.addEventListener("click", () => {
        this.actions.skipAdd();
        this.disableAllActions();
        this.showToast("Passing — advancing to Random Event…");
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
    const el = this.overlay.querySelector<HTMLDivElement>("#kh-toast");
    if (!el) return;
    el.textContent = msg;
    el.classList.add("kh-toast-visible");
    if (this._toastTimer) clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => el.classList.remove("kh-toast-visible"), 3000);
  }

  // ─── Style injection (runs once per page load) ───────────────────────────

  private injectStylesOnce(): void {
    if (document.getElementById("kh-scene-styles")) return;

    // Google Fonts
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href =
      "https://fonts.googleapis.com/css2?family=Cinzel:wght@400;600;700&family=Crimson+Text:ital,wght@0,400;0,600;1,400&display=swap";
    document.head.appendChild(link);

    const style = document.createElement("style");
    style.id = "kh-scene-styles";
    style.textContent = `
      #kh-add-phase-overlay {
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

      /* Header */
      .kh-header {
        border-bottom: 4px solid #c49428;
        padding: 18px 48px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        box-shadow: 0 4px 20px rgba(0,0,0,.45);
      }
      .kh-header-ornament { font-size: 2rem; color: #f0cc50; opacity: .55; user-select: none; }
      .kh-header-center   { text-align: center; flex: 1; }
      .kh-header-center h1 {
        font-family: 'Cinzel', serif;
        font-size: 2.1rem;
        font-weight: 700;
        color: #f0cc50;
        letter-spacing: 4px;
        text-shadow: 0 2px 10px rgba(0,0,0,.6);
        margin: 0;
      }
      .kh-year-badge {
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

      /* Resources */
      .kh-resources {
        background: linear-gradient(90deg, #1e1208 0%, #2e1c0c 50%, #1e1208 100%);
        border-bottom: 3px solid #c49428;
        padding: 10px 48px;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 64px;
      }
      .kh-resource   { display: flex; align-items: center; gap: 10px; }
      .kh-res-icon   { font-size: 1.5rem; line-height: 1; }
      .kh-res-label  { font-family: 'Cinzel', serif; font-size: .62rem; letter-spacing: 1.5px; color: #a09070; text-transform: uppercase; }
      .kh-res-value  { font-family: 'Cinzel', serif; font-size: 1.25rem; font-weight: 700; }
      .kh-pop  .kh-res-value { color: #f09090; }
      .kh-prod .kh-res-value { color: #f0cc60; }
      .kh-food .kh-res-value { color: #90e060; }
      .kh-res-divider { width: 1px; height: 32px; background: rgba(255,255,255,.08); }

      /* Phase tracker */
      .kh-phase-track {
        background: #e8d49c;
        border-bottom: 2px solid #c4a060;
        padding: 12px 48px;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 6px;
      }
      .kh-phase-step {
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
      .kh-phase-step.kh-phase-active {
        opacity: 1;
        color: #3d6b46;
        background: rgba(61,107,70,.12);
        border: 1px solid rgba(61,107,70,.35);
        font-weight: 700;
      }
      .kh-phase-dot  { width: 7px; height: 7px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
      .kh-phase-arrow { color: #c4a060; font-size: 1rem; opacity: .6; padding: 0 4px; }

      /* Scene body */
      .kh-scene { max-width: 1080px; margin: 0 auto; padding: 44px 28px 60px; }

      .kh-prompt { text-align: center; margin-bottom: 36px; }
      .kh-prompt h2 { font-family: 'Cinzel', serif; font-size: 1.55rem; color: #3a2010; margin: 0 0 6px; }
      .kh-prompt p  { color: #7a6a52; font-size: 1.1rem; font-style: italic; }

      /* Cards grid */
      .kh-cards-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 26px; margin-bottom: 36px; }

      .kh-card {
        background: #fdf6e4;
        border: 2px solid #c4a060;
        border-radius: 14px;
        overflow: hidden;
        cursor: pointer;
        transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
        box-shadow: 3px 5px 18px rgba(0,0,0,.14), inset 0 0 60px rgba(255,245,210,.4);
      }
      .kh-card:hover { transform: translateY(-7px) scale(1.015); box-shadow: 5px 14px 30px rgba(0,0,0,.22); border-color: #c49428; }
      .kh-card.kh-card-unaffordable { opacity: .68; filter: saturate(.6); }
      .kh-card.kh-card-unaffordable:hover { transform: translateY(-3px) scale(1.005); border-color: #cc6644; }

      /* Card header */
      .kh-card-header {
        background: linear-gradient(135deg, #3d6b46 0%, #274d30 100%);
        padding: 16px 18px 13px;
        border-bottom: 2px solid #c49428;
        position: relative;
        min-height: 72px;
      }
      .kh-card-icon { position: absolute; top: 13px; right: 16px; font-size: 2rem; filter: drop-shadow(0 2px 4px rgba(0,0,0,.4)); }
      .kh-card-name { font-family: 'Cinzel', serif; font-size: 1.05rem; font-weight: 700; color: #f0e8c0; letter-spacing: .5px; padding-right: 40px; }
      .kh-card-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 7px; }

      .kh-tag { font-family: 'Cinzel', serif; font-size: .58rem; font-weight: 600; letter-spacing: 1px; padding: 2px 7px; border-radius: 10px; }
      .kh-tag-season    { background: rgba(255,255,255,.18); color: #c4f0b0; }
      .kh-tag-recurring { background: rgba(120,200,120,.25); color: #b0f0b0; }
      .kh-tag-single    { background: rgba(220,140,100,.3);  color: #ffd0b0; }
      .kh-tag-category  { background: rgba(240,200,80,.22);  color: #f0e090; }

      /* Card body */
      .kh-card-body { padding: 16px 18px; }
      .kh-card-desc {
        font-style: italic;
        color: #7a6a52;
        font-size: .93rem;
        line-height: 1.45;
        padding-bottom: 13px;
        border-bottom: 1px dashed #c4a060;
        margin-bottom: 14px;
      }
      .kh-effects-title {
        font-family: 'Cinzel', serif;
        font-size: .62rem;
        letter-spacing: 1.5px;
        color: #7a6a52;
        text-transform: uppercase;
        margin-bottom: 7px;
      }
      .kh-effect-row   { display: flex; align-items: center; gap: 8px; padding: 3px 0; font-size: .88rem; }
      .kh-effect-target { font-family: 'Cinzel', serif; font-size: .68rem; font-weight: 600; letter-spacing: .8px; min-width: 90px; display: flex; align-items: center; gap: 4px; }
      .kh-et-food       { color: #3a7020; }
      .kh-et-production { color: #a07010; }
      .kh-et-population { color: #8b2020; }
      .kh-effect-mod  { font-weight: 700; font-size: 1rem; color: #3d6b46; }
      .kh-effect-freq { font-size: .75rem; color: #a09070; font-style: italic; }

      /* Costs */
      .kh-card-costs { display: flex; align-items: center; gap: 18px; padding: 12px 0 13px; border-top: 1px dashed #c4a060; margin-top: 13px; }
      .kh-cost-item  { display: flex; flex-direction: column; align-items: center; gap: 1px; }
      .kh-cost-label { font-family: 'Cinzel', serif; font-size: .58rem; letter-spacing: 1px; color: #7a6a52; text-transform: uppercase; }
      .kh-cost-val   { font-family: 'Cinzel', serif; font-size: 1.05rem; font-weight: 700; }
      .kh-cost-prod  { color: #a07010; }
      .kh-cost-pop   { color: #8b2020; }
      .kh-unaffordable-flag { margin-left: auto; font-family: 'Cinzel', serif; font-size: .65rem; letter-spacing: .5px; color: #c04030; }

      /* Select button */
      .kh-btn-select {
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
      .kh-btn-select:hover:not(:disabled) { background: linear-gradient(135deg, #4e8858 0%, #325e3e 100%); box-shadow: 0 2px 10px rgba(0,0,0,.28); }
      .kh-btn-select:disabled { background: #a09070; border-color: #c0a060; color: #e8d8a0; cursor: not-allowed; opacity: .7; }

      /* Ornament */
      .kh-ornament { text-align: center; color: #c49428; font-size: 1.4rem; letter-spacing: 10px; opacity: .45; margin: 6px 0 30px; user-select: none; }

      /* Action bar */
      .kh-action-bar { display: flex; justify-content: center; align-items: center; gap: 18px; }
      .kh-btn-reroll {
        padding: 12px 34px;
        background: linear-gradient(135deg, #c49428 0%, #8a6010 100%);
        color: #2a1800;
        border: 2px solid #f0cc50;
        border-radius: 9px;
        font-family: 'Cinzel', serif;
        font-size: .88rem;
        font-weight: 700;
        letter-spacing: 1px;
        cursor: pointer;
        transition: all .15s;
        box-shadow: 2px 3px 12px rgba(0,0,0,.2);
      }
      .kh-btn-reroll:hover:not(:disabled) { background: linear-gradient(135deg, #e0b040 0%, #a87820 100%); box-shadow: 0 3px 14px rgba(200,160,40,.45); }
      .kh-btn-reroll:disabled { background: #a09070; border-color: #c0a060; color: #e0d0a0; cursor: not-allowed; opacity: .65; }
      .kh-reroll-cost { font-weight: 400; }

      .kh-btn-skip {
        padding: 12px 28px;
        background: transparent;
        color: #7a6a52;
        border: 2px solid #c4a060;
        border-radius: 9px;
        font-family: 'Cinzel', serif;
        font-size: .82rem;
        letter-spacing: 1px;
        cursor: pointer;
        transition: all .15s;
      }
      .kh-btn-skip:hover:not(:disabled) { border-color: #7a6a52; color: #3a2010; background: rgba(0,0,0,.04); }
      .kh-btn-skip:disabled { opacity: .5; cursor: not-allowed; }

      /* Toast */
      .kh-toast {
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
      .kh-toast.kh-toast-visible { opacity: 1; transform: translateX(-50%) translateY(0); }
    `;
    document.head.appendChild(style);
  }
}
