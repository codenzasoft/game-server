import { GameState, Season, Card, CardTarget, OpponentTarget, ResourceTarget } from "./kingshand.ts";
import {
  getCardDisplayInfo,
  cardImagePath,
  formatModifier,
  resourceIcon,
} from "./cardCatalog.ts";
import { seatedFreeRerollBonus } from "./advisorCatalog.ts";
import {
  injectSharedStyles,
  buildDeckIndicator,
  attachDeckListeners,
  buildHandbookButton,
  attachHandbookListeners,
  buildOptionsButton,
  attachOptionsListeners,
  buildHealthBar,
} from "./sharedOverlays.ts";

// ─── Public interface ─────────────────────────────────────────────────────────

export interface AddPhaseActions {
  reroll: () => void;
  pickCard: (name: string) => void;
  skipAdd: () => void;
}

// ─── Constants ────────────────────────────────────────────────────────────────

/** Mirrors GameEngine.REROLL_COST_STEP — keep in sync with the Java constant. */
const REROLL_COST_STEP = 50;

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
    const season = state.time.season;
    const meta = SEASON_META[season];
    // Mirrors GameEngine.offerAddChoices()'s freeCalls/cost formula exactly: the first call each
    // ADD phase is always free, plus any permanent (Decree) and seated (base trait / Reform) free
    // rerolls on top of that. Only once state.rerollCount reaches that total does a reroll cost
    // anything — previously this ignored both bonuses entirely, so the button always showed (and
    // charged) as if no advisor or Decree had ever granted a free reroll.
    const freeRerollCalls = 1 + state.permanentFreeRerolls + seatedFreeRerollBonus(state.council);
    const nextRerollCost =
      state.rerollCount >= freeRerollCalls
        ? (state.rerollCount - freeRerollCalls + 1) * REROLL_COST_STEP
        : 0;
    const isFreeReroll = nextRerollCost === 0;
    const canAffordReroll = state.resources.production >= nextRerollCost;

    this.overlay.innerHTML = `
      ${this.buildHeader(state, meta)}
      ${this.buildResourcesBar(state)}
      <main class="kh-scene">
        <div class="kh-prompt">
          <h2>Choose a Card to Add</h2>
          <p>Select one card to shuffle into your ${meta.label} deck, or pass.</p>
        </div>
        <div class="kh-cards-grid">
          ${state.addChoices.map((card, i) => this.buildCard(card, i)).join("")}
        </div>
        <div class="kh-ornament">✦ &nbsp; ✦ &nbsp; ✦</div>
        <div class="kh-action-bar">
          <button class="kh-btn-skip" id="kh-btn-skip">Skip</button>
          <button class="kh-btn-reroll" id="kh-btn-reroll"
            ${!canAffordReroll ? "disabled" : ""}>
            ⟳ Reroll
            <span class="kh-reroll-cost">${isFreeReroll ? "(Free)" : `(${nextRerollCost} ⚒️)`}</span>
          </button>
        </div>
      </main>
      ${buildDeckIndicator(state)}
      ${buildHandbookButton()}
      ${buildOptionsButton()}
      <div class="kh-toast" id="kh-toast"></div>
    `;

    // Apply season background image over a dark scrim for readability
    this.overlay.style.background =
      `linear-gradient(rgba(8,4,2,.62) 0%, rgba(8,4,2,.62) 100%), url('${meta.bg}') center/cover no-repeat`;

    this.attachListeners(state);
    attachDeckListeners(this.overlay, state);
    attachHandbookListeners(this.overlay);
    attachOptionsListeners(this.overlay);
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
          <div class="kh-year-badge">
            <span>${meta.icon}</span>
            <span>YEAR ${state.time.year} — ${meta.label.toUpperCase()}</span>
            <span>${meta.icon}</span>
          </div>
          ${this.buildPhaseTracker()}
        </div>
        <span class="kh-header-ornament">❧</span>
      </header>
    `;
  }

  private buildResourcesBar(state: GameState): string {
    const goldHTML = state.resources.isUnlocked(ResourceTarget.GOLD)
      ? `
        <div class="kh-res-divider"></div>
        <div class="kh-resource kh-gold">
          <span class="kh-res-icon">💰</span>
          <div>
            <div class="kh-res-label">Gold</div>
            <div class="kh-res-value">${Math.floor(state.resources.gold)}</div>
          </div>
        </div>
      `
      : "";
    // Population + Defense live in the shared health bar (buildHealthBar) beneath this strip.
    return `
      <div class="kh-resources">
        <div class="kh-resource kh-prod">
          <span class="kh-res-icon">⚒️</span>
          <div>
            <div class="kh-res-label">Production</div>
            <div class="kh-res-value">${Math.floor(state.resources.production)}</div>
          </div>
        </div>
        <div class="kh-res-divider"></div>
        <div class="kh-resource kh-food">
          <span class="kh-res-icon">🌾</span>
          <div>
            <div class="kh-res-label">Food</div>
            <div class="kh-res-value">${Math.floor(state.resources.food)}</div>
          </div>
        </div>
        ${goldHTML}
      </div>
      ${buildHealthBar(state)}
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
          <span class="kh-phase-dot"></span>II. PLAY
        </div>
        <span class="kh-phase-arrow">→</span>
        <div class="kh-phase-step">
          <span class="kh-phase-dot"></span>III. COUNCIL
        </div>
      </div>
    `;
  }

  private buildCard(card: Card, index: number): string {
    const info   = getCardDisplayInfo(card);
    const imgSrc = cardImagePath(card.name);

    // Only informative tags: frequency (SINGLE/RECURRING) and ENGINE.
    const tagsHTML = [
      `<span class="kh-tag ${card.frequencyType === "RECURRING" ? "kh-tag-recurring" : "kh-tag-single"}">${card.frequencyType}</span>`,
      ...info.tags.filter((t) => t === "ENGINE").map((t) => `<span class="kh-tag kh-tag-category">${t}</span>`),
    ].join("");

    // Compact effect rows: "<amount> <emoji>" (+ scope for card-boosting effects).
    const effectsHTML = card.effects
      .map((e) => {
        if (e.target instanceof CardTarget) {
          const res = e.target.targetResource;
          const scope = (e.target as CardTarget).adjacentOnly ? "to next card" : "to all cards";
          return `<div class="kh-effect-row"><span class="kh-effect-mod${e.modifier < 0 ? " kh-effect-mod-neg" : ""}">${formatModifier(e.modifier)}</span><span class="kh-effect-eicon">${resourceIcon(res)}</span><span class="kh-effect-escope">${scope}</span></div>`;
        }
        if (e.target instanceof OpponentTarget) {
          const res = e.target.targetResource;
          if (res === ResourceTarget.POPULATION) {
            return `<div class="kh-effect-row kh-effect-attack"><span class="kh-effect-mod">${Math.abs(e.modifier)}</span><span class="kh-effect-eicon">⚔️</span></div>`;
          }
          return `<div class="kh-effect-row"><span class="kh-effect-mod${e.modifier < 0 ? " kh-effect-mod-neg" : ""}">${formatModifier(e.modifier)}</span><span class="kh-effect-eicon">${resourceIcon(res)}</span><span class="kh-effect-escope">rival</span></div>`;
        }
        const icon = typeof e.target === "string" ? resourceIcon(e.target) : "";
        return `<div class="kh-effect-row"><span class="kh-effect-mod${e.modifier < 0 ? " kh-effect-mod-neg" : ""}">${formatModifier(e.modifier)}</span><span class="kh-effect-eicon">${icon}</span></div>`;
      })
      .join("");

    // Border identity: single-use → bronze, recurring → silver, Great Work → gold.
    const typeClass =
      card.constructionPeriod > 0
        ? "kh-card-kind-greatwork"
        : card.frequencyType === "RECURRING"
          ? "kh-card-kind-recurring"
          : "kh-card-kind-single";

    return `
      <div class="kh-card ${typeClass}" data-index="${index}" role="button" tabindex="0" title="Add to your deck">
        <div class="kh-add-art-header">
          <img class="kh-add-art-img" src="${imgSrc}" alt="${info.name}" />
          <div class="kh-add-badge kh-add-popreq" title="Population required">${card.staffRequirement}</div>
          <div class="kh-add-badge kh-add-cost" title="Production cost">${card.productionRequirement}</div>
          <div class="kh-add-art-overlay">
            <div class="kh-card-name">${info.name}</div>
            <div class="kh-card-tags">${tagsHTML}</div>
          </div>
        </div>
        <div class="kh-card-body">
          ${effectsHTML}
          ${card.constructionPeriod > 0 ? `<div class="kh-card-construction">🏗️ ${card.constructionPeriod} season${card.constructionPeriod === 1 ? "" : "s"} to build</div>` : ""}
        </div>
      </div>
    `;
  }

  // ─── Event listeners ────────────────────────────────────────────────────

  private attachListeners(state: GameState): void {
    // Whole card is clickable — pick it into your deck.
    this.overlay.querySelectorAll<HTMLElement>(".kh-card[data-index]").forEach((cardEl) => {
      const pick = () => {
        const index = Number(cardEl.dataset.index);
        const cardName = state.addChoices[index]?.name ?? "";
        this.actions.pickCard(cardName);
        // Optimistic UI: disable all interaction while server responds
        this.disableAllActions();
        cardEl.classList.add("kh-card-picked");
        this.showToast(`Adding card to your ${SEASON_META[state.time.season].label} deck…`);
      };
      cardEl.addEventListener("click", pick);
      cardEl.addEventListener("keydown", (ev) => {
        if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); pick(); }
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
    // Cards are clickable divs now, not buttons — lock them too so you can't pick twice.
    this.overlay
      .querySelectorAll<HTMLElement>(".kh-card")
      .forEach((c) => (c.style.pointerEvents = "none"));
  }

  // ─── Toast ──────────────────────────────────────────────────────────────

  private _toastTimer: ReturnType<typeof setTimeout> | null = null;

  private showToast(msg: string): void {
    const el = this.overlay.querySelector<HTMLDivElement>("#kh-toast");
    if (!el) return;
    el.textContent = msg;
    el.classList.add("kh-toast-visible");
    if (this._toastTimer) clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => el.classList.remove("kh-toast-visible"), 5000);
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
      .kh-pop      .kh-res-value { color: #f09090; }
      .kh-prod     .kh-res-value { color: #e8862a; }
      .kh-food     .kh-res-value { color: #90e060; }
      .kh-gold     .kh-res-value { color: #ffd700; }
      .kh-strength .kh-res-value { color: #9ab0d0; }
      .kh-res-cap { font-size: .85rem; font-weight: 400; color: #9a7a7a; opacity: .85; }
      .kh-res-divider { width: 1px; height: 32px; background: rgba(255,255,255,.08); }

      /* Phase tracker (now nested under the year/season inside the header) */
      .kh-phase-track {
        margin-top: 9px;
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
        color: rgba(200,176,140,0.55);
        border-radius: 4px;
      }
      .kh-phase-step.kh-phase-active {
        color: #90e090;
        background: rgba(61,107,70,.3);
        border: 1px solid rgba(144,224,144,.35);
        font-weight: 700;
      }
      .kh-phase-dot  { width: 7px; height: 7px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
      .kh-phase-arrow { color: rgba(196,160,96,0.5); font-size: 1rem; padding: 0 4px; }

      /* Scene body */
      .kh-scene { max-width: 1080px; margin: 0 auto; padding: 44px 28px 60px; }

      .kh-prompt { text-align: center; margin-bottom: 36px; }
      .kh-prompt h2 { font-family: 'Cinzel', serif; font-size: 1.55rem; color: #f0e8c0; margin: 0 0 6px; text-shadow: 0 2px 8px rgba(0,0,0,.7); }
      .kh-prompt p  { color: #c4b08c; font-size: 1.1rem; font-style: italic; text-shadow: 0 1px 6px rgba(0,0,0,.6); }

      /* Cards grid */
      /* Draft cards — one constant, larger size (scaled up; fewer cards on screen), centered. */
      .kh-cards-grid {
        display: grid;
        grid-auto-flow: column;
        grid-auto-columns: clamp(210px, 30vh, 330px);
        gap: clamp(14px, 2.4vh, 30px);
        justify-content: center;
        margin: 0 auto 20px;
      }
      .kh-card {
        background: #fdf6e4;
        border: 2px solid #c4a060;
        border-radius: 14px;
        overflow: hidden;
        cursor: pointer;
        transition: transform .2s ease, box-shadow .2s ease;
        box-shadow: 3px 5px 18px rgba(0,0,0,.14), inset 0 0 60px rgba(255,245,210,.4);
        /* Fixed aspect + container so every card is the same size and its contents scale together. */
        aspect-ratio: 1 / 1.56;
        display: flex;
        flex-direction: column;
        container-type: inline-size;
      }
      .kh-card:hover { transform: translateY(-7px) scale(1.015); box-shadow: 5px 14px 30px rgba(0,0,0,.28); }
      .kh-card.kh-card-unaffordable { opacity: .7; filter: saturate(.6); }
      .kh-card.kh-card-picked { outline: 3px solid #6ac06a; }
      /* Border identity by kind: single → bronze, recurring → silver, Great Work → gold. */
      .kh-card.kh-card-kind-single    { border: 5px solid #bf7a3a; }
      .kh-card.kh-card-kind-recurring { border: 5px solid #b7bdc4; }
      .kh-card.kh-card-kind-greatwork { border: 5px solid #e6b53c; }

      /* Card art header — square window, middle crop, with corner badges. */
      .kh-add-art-header {
        position: relative;
        width: 100%;
        aspect-ratio: 1 / 1;
        overflow: hidden;
        border-bottom: 2px solid #c49428;
        border-radius: 14px 14px 0 0;
        flex: 0 0 auto;
      }
      .kh-add-art-img { width: 100%; height: 100%; object-fit: cover; object-position: center; display: block; }
      .kh-add-badge {
        position: absolute; top: 4cqw; display: flex; align-items: center; justify-content: center;
        font-family: 'Cinzel', serif; font-weight: 800; color: #fff; z-index: 3;
        box-shadow: 0 2px 7px rgba(0,0,0,.55); text-shadow: 0 1px 2px rgba(0,0,0,.6);
      }
      .kh-add-cost   { right: 4cqw; width: 20cqw; height: 20cqw; border-radius: 50%; font-size: 9cqw; overflow: hidden;
        background: radial-gradient(circle at 35% 30%, #f0932e 0%, #a8560c 82%); border: 1.5cqw solid #ffdca8; }
      .kh-add-popreq { left: 4cqw;  width: 19cqw; height: 19cqw; border-radius: 3cqw; font-size: 8.5cqw; overflow: hidden;
        background: linear-gradient(135deg, #4a7fb0 0%, #1e3f66 100%); border: 1.5cqw solid #cfe2f0; }
      .kh-add-cost::before, .kh-add-popreq::before {
        position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
        opacity: .34; z-index: -1; pointer-events: none; line-height: 1;
      }
      .kh-add-cost::before   { content: "⚒️"; font-size: 15cqw; }
      .kh-add-popreq::before { content: "👥"; font-size: 14cqw; }
      .kh-add-art-overlay {
        position: absolute; bottom: 0; left: 0; right: 0;
        background: linear-gradient(transparent 0%, rgba(10,5,2,.72) 40%, rgba(10,5,2,.92) 100%);
        padding: 11cqw 5cqw 4cqw;
      }
      .kh-card-name { font-family: 'Cinzel', serif; font-size: 8cqw; font-weight: 700; color: #f0e8c0; letter-spacing: .5px; }
      .kh-card-tags { display: flex; flex-wrap: wrap; gap: 2cqw; margin-top: 2cqw; }

      .kh-tag { font-family: 'Cinzel', serif; font-size: 4cqw; font-weight: 600; letter-spacing: 1px; padding: 1cqw 2.6cqw; border-radius: 10px; }
      .kh-tag-recurring { background: rgba(120,200,120,.28); color: #b0f0b0; }
      .kh-tag-single    { background: rgba(220,140,100,.32);  color: #ffd0b0; }
      .kh-tag-category  { background: rgba(240,200,80,.24);  color: #f0e090; }

      /* Card body — just the effects, centered, sized in cqw so it scales with the card. */
      .kh-card-body { flex: 1 1 auto; min-height: 0; overflow: hidden; display: flex; flex-direction: column; justify-content: center; gap: 1cqw; padding: 4cqw 6cqw; }
      .kh-effect-row   { display: flex; align-items: center; gap: 3cqw; padding: 1.4cqw 0; }
      .kh-effect-mod   { font-weight: 800; font-size: 9.5cqw; color: #3d6b46; }
      .kh-effect-eicon { font-size: 9.5cqw; line-height: 1; }
      .kh-effect-escope { font-size: 5.6cqw; font-style: italic; color: #8a7654; line-height: 1.1; }
      .kh-effect-attack .kh-effect-mod { color: #b23b30; }
      .kh-effect-mod-neg { color: #b23b30; }
      .kh-card-construction { font-family: 'Cinzel', serif; font-size: 4.6cqw; color: #9a7a4a; margin-top: 2cqw; }

      /* Costs */
      .kh-card-costs { display: flex; align-items: center; gap: 18px; padding: 12px 0 13px; border-top: 1px dashed #c4a060; margin-top: 13px; }
      .kh-cost-item  { display: flex; flex-direction: column; align-items: center; gap: 1px; }
      .kh-cost-label { font-family: 'Cinzel', serif; font-size: .72rem; letter-spacing: 1px; color: #7a6a52; text-transform: uppercase; }
      .kh-cost-val   { font-family: 'Cinzel', serif; font-size: 1.3rem; font-weight: 700; }
      .kh-cost-prod  { color: #a07010; }
      .kh-cost-pop   { color: #8b2020; }
      .kh-unaffordable-flag { margin-left: auto; font-family: 'Cinzel', serif; font-size: .78rem; letter-spacing: .5px; color: #c04030; }

      /* Select button */
      .kh-btn-select {
        width: 100%;
        padding: 11px 0;
        border-radius: 8px;
        border: 2px solid #c49428;
        background: linear-gradient(135deg, #3d6b46 0%, #274d30 100%);
        color: #f0e8c0;
        font-family: 'Cinzel', serif;
        font-size: 1rem;
        font-weight: 700;
        letter-spacing: 1px;
        cursor: pointer;
        transition: all .15s;
        margin-top: 4px;
      }
      .kh-btn-select:hover:not(:disabled) { background: linear-gradient(135deg, #4e8858 0%, #325e3e 100%); box-shadow: 0 2px 10px rgba(0,0,0,.28); }
      .kh-btn-select:disabled { background: #a09070; border-color: #c0a060; color: #e8d8a0; cursor: not-allowed; opacity: .7; }

      /* Ornament */
      .kh-ornament { text-align: center; color: #f0cc50; font-size: 1.4rem; letter-spacing: 10px; opacity: .7; margin: 6px 0 30px; user-select: none; text-shadow: 0 0 12px rgba(240,204,80,.4); }

      /* Action block — a fixed tab on the right edge, in the same spot as the Play/Continue button.
         Skip sits on top; Reroll lives directly beneath it in the same block. */
      .kh-action-bar {
        position: fixed;
        right: 0;
        top: 50%;
        transform: translateY(-50%);
        display: flex;
        flex-direction: column;
        align-items: stretch;
        z-index: 60;
        border: 3px solid #c49428;
        border-right: none;
        border-radius: 14px 0 0 14px;
        overflow: hidden;
        box-shadow: -6px 0 30px rgba(0,0,0,.5);
      }
      .kh-btn-skip {
        padding: 26px 84px 26px 52px;
        background: linear-gradient(135deg, #6b1a1a 0%, #4a0e0e 100%);
        color: #f0e8c0;
        border: none;
        font-family: 'Cinzel', serif;
        font-size: 1.7rem;
        font-weight: 700;
        letter-spacing: 5px;
        text-align: left;
        white-space: nowrap;
        cursor: pointer;
        transition: all .2s ease;
      }
      .kh-btn-skip:hover:not(:disabled) { background: linear-gradient(135deg, #7a2222 0%, #5a1414 100%); }
      .kh-btn-skip:disabled { opacity: .5; cursor: not-allowed; }

      .kh-btn-reroll {
        padding: 18px 84px 18px 52px;
        background: linear-gradient(135deg, #c49428 0%, #8a6010 100%);
        color: #2a1800;
        border: none;
        border-top: 3px solid #3a2406;
        font-family: 'Cinzel', serif;
        font-size: 1.15rem;
        font-weight: 700;
        letter-spacing: 1px;
        text-align: left;
        white-space: nowrap;
        cursor: pointer;
        transition: all .2s ease;
      }
      .kh-btn-reroll:hover:not(:disabled) { background: linear-gradient(135deg, #e0b040 0%, #a87820 100%); }
      .kh-btn-reroll:disabled { background: #8a8068; color: #ddd0ac; cursor: not-allowed; }
      .kh-reroll-cost { font-weight: 400; font-size: .92rem; opacity: .9; }

      /* Toast */
      .kh-toast {
        position: fixed;
        top: 170px;
        left: 50%;
        transform: translateX(-50%) translateY(-10px);
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
