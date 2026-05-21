import { GameState, Season, KhEvent, EventOption, Effect, ResourceTarget } from "./kingshand.ts";
import { formatModifier, targetEmoji } from "./cardCatalog.ts";
import {
  injectSharedStyles,
  buildDeckIndicator,
  attachDeckListeners,
  buildHandbookButton,
  attachHandbookListeners,
  buildOptionsButton,
  attachOptionsListeners,
  spawnResourceIndicator,
} from "./sharedOverlays.ts";

// ─── Public interface ─────────────────────────────────────────────────────────

export interface RandomEventActions {
  pickEventOption: (index: number) => void;
  skipEvent: () => void;
}

// ─── Season display metadata (mirrors addPhaseScene) ─────────────────────────

const SEASON_META: Record<
  Season,
  { label: string; icon: string; headerColor: string; accentColor: string; bg: string }
> = {
  [Season.SPRING]: { label: "Spring", icon: "🌿", headerColor: "#274d30", accentColor: "#3d6b46", bg: "/img/bg-spring.png" },
  [Season.SUMMER]: { label: "Summer", icon: "☀️",  headerColor: "#5a3a0a", accentColor: "#8a5a10", bg: "/img/bg-summer.png" },
  [Season.FALL]:   { label: "Fall",   icon: "🍂", headerColor: "#5a2a10", accentColor: "#8a4020", bg: "/img/bg-fall.png" },
  [Season.WINTER]: { label: "Winter", icon: "❄️",  headerColor: "#1a2a40", accentColor: "#2a4060", bg: "/img/bg-winter.png" },
};

// ─── RandomEventScene ─────────────────────────────────────────────────────────

/**
 * Full-screen HTML overlay for the RANDOM_EVENT phase.  Displays the rolled
 * event description and its selectable options.  If no event was rolled
 * ({@code currentEvent} is undefined) it shows a "no event" message with a
 * Continue button that skips the phase.
 *
 * Lifecycle mirrors {@link AddPhaseScene}: call {@link mount}, {@link update},
 * {@link unmount}.
 */
export class RandomEventScene {
  private readonly overlay: HTMLDivElement;
  private readonly actions: RandomEventActions;
  private state: GameState;

  constructor(state: GameState, actions: RandomEventActions) {
    this.state = state;
    this.actions = actions;
    this.injectStylesOnce();
    injectSharedStyles();
    this.overlay = document.createElement("div");
    this.overlay.id = "kh-random-event-overlay";
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
    const meta = SEASON_META[state.curSeason];

    this.overlay.innerHTML = `
      ${this.buildHeader(state, meta)}
      ${this.buildResourcesBar(state)}
      ${this.buildPhaseTracker()}
      <main class="kh-re-scene">
        ${state.currentEvent
          ? this.buildEventContent(state.currentEvent)
          : this.buildNoEventContent(meta)}
      </main>
      ${buildDeckIndicator(state)}
      ${buildHandbookButton()}
      ${buildOptionsButton()}
      <div class="kh-re-toast" id="kh-re-toast"></div>
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
      <header class="kh-re-header" style="
        background: linear-gradient(180deg, ${meta.headerColor} 0%, ${meta.accentColor} 60%, ${meta.headerColor} 100%);
      ">
        <span class="kh-re-ornament">❧</span>
        <div class="kh-re-header-center">
          <h1>King's Hand</h1>
          <div class="kh-re-year-badge">
            <span>${meta.icon}</span>
            <span>YEAR ${state.year} — ${meta.label.toUpperCase()}</span>
            <span>${meta.icon}</span>
          </div>
        </div>
        <span class="kh-re-ornament">❧</span>
      </header>
    `;
  }

  private buildResourcesBar(state: GameState): string {
    return `
      <div class="kh-re-resources">
        <div class="kh-re-resource kh-re-pop">
          <span class="kh-re-res-icon">👥</span>
          <div>
            <div class="kh-re-res-label">Population</div>
            <div class="kh-re-res-value">${Math.floor(state.population)}</div>
          </div>
        </div>
        <div class="kh-re-res-divider"></div>
        <div class="kh-re-resource kh-re-prod">
          <span class="kh-re-res-icon">⚒️</span>
          <div>
            <div class="kh-re-res-label">Production</div>
            <div class="kh-re-res-value">${Math.floor(state.production)}</div>
          </div>
        </div>
        <div class="kh-re-res-divider"></div>
        <div class="kh-re-resource kh-re-food">
          <span class="kh-re-res-icon">🌾</span>
          <div>
            <div class="kh-re-res-label">Food</div>
            <div class="kh-re-res-value">${Math.floor(state.food)}</div>
          </div>
        </div>
      </div>
    `;
  }

  private buildPhaseTracker(): string {
    return `
      <div class="kh-re-phase-track">
        <div class="kh-re-phase-step">
          <span class="kh-re-phase-dot"></span>I. ADD
        </div>
        <span class="kh-re-phase-arrow">→</span>
        <div class="kh-re-phase-step kh-re-phase-active">
          <span class="kh-re-phase-dot"></span>II. RANDOM EVENT
        </div>
        <span class="kh-re-phase-arrow">→</span>
        <div class="kh-re-phase-step">
          <span class="kh-re-phase-dot"></span>III. PLAY
        </div>
      </div>
    `;
  }

  private buildEventContent(event: KhEvent): string {
    const isWinter = this.state.curSeason === Season.WINTER;
    const optionsHTML = event.options
      .map((opt, i) => this.buildOptionCard(opt, i))
      .join("");

    const winterBanner = isWinter ? `
      <div class="kh-re-winter-banner">
        ❄ Winter Scaling — all effects are multiplied by Year ${this.state.year}
      </div>
    ` : "";

    return `
      <div class="kh-re-prompt">
        <div class="kh-re-event-label">An event unfolds…</div>
        <h2 class="kh-re-event-desc">"${event.description}"</h2>
        <p class="kh-re-event-sub">Choose how your settlement responds.</p>
      </div>
      ${winterBanner}
      <div class="kh-re-options-grid kh-re-options-${event.options.length}">
        ${optionsHTML}
      </div>
    `;
  }

  private buildOptionCard(option: EventOption, index: number): string {
    const isWinter = this.state.curSeason === Season.WINTER;
    const year     = this.state.year;

    const effectsHTML = option.effects.length > 0
      ? option.effects.map((e: Effect) => {
          const targetStr     = typeof e.target === "string" ? e.target : "CARD";
          // Apply winter year-scaling to the displayed modifier so what the player
          // sees matches exactly what the server will apply via scaleForSeason().
          const scaledModifier = isWinter ? e.modifier * year : e.modifier;
          const isNegative     = scaledModifier < 0;
          const scaleNote = "";
          return `
            <div class="kh-re-effect-row">
              <span class="kh-re-effect-target kh-re-et-${targetStr.toLowerCase()}">
                ${targetEmoji(e.target)} ${targetStr}
              </span>
              <span class="kh-re-effect-mod ${isNegative ? "kh-re-mod-neg" : "kh-re-mod-pos"}">
                ${formatModifier(scaledModifier, e.valueType)}
              </span>
              ${scaleNote}
            </div>
          `;
        }).join("")
      : `<div class="kh-re-no-effect">No immediate effect</div>`;

    return `
      <div class="kh-re-option" data-index="${index}">
        <div class="kh-re-option-header">
          <span class="kh-re-option-num">${index + 1}</span>
          <span class="kh-re-option-desc">${option.description}</span>
        </div>
        <div class="kh-re-option-effects">
          <div class="kh-re-effects-label">Outcome</div>
          ${effectsHTML}
        </div>
        <button class="kh-re-btn-choose" data-index="${index}">
          Choose
        </button>
      </div>
    `;
  }

  private buildNoEventContent(
    meta: (typeof SEASON_META)[Season],
  ): string {
    return `
      <div class="kh-re-no-event">
        <div class="kh-re-no-event-icon">${meta.icon}</div>
        <h2>A quiet ${meta.label}</h2>
        <p>No notable events stir this season. Your settlement rests.</p>
        <button class="kh-re-btn-continue" id="kh-re-btn-continue">
          Continue to Play Phase →
        </button>
      </div>
    `;
  }

  // ─── Event listeners ────────────────────────────────────────────────────

  private attachListeners(state: GameState): void {
    // Helper: spawn indicators for all effects of the chosen option.
    const spawnOptionIndicators = (index: number): void => {
      const option = state.currentEvent?.options[index];
      if (!option) return;
      let si = 0;
      for (const eff of option.effects) {
        if (typeof eff.target !== "string") continue; // skip CardTarget effects
        const sel =
          eff.target === ResourceTarget.POPULATION ? ".kh-re-pop  .kh-re-res-value" :
          eff.target === ResourceTarget.PRODUCTION  ? ".kh-re-prod .kh-re-res-value" :
                                                      ".kh-re-food .kh-re-res-value";
        const icon =
          eff.target === ResourceTarget.POPULATION ? "👥" :
          eff.target === ResourceTarget.PRODUCTION  ? "⚒️" : "🌾";
        const anchor = this.overlay.querySelector<HTMLElement>(sel);
        if (anchor) spawnResourceIndicator(anchor, eff.modifier, icon, si++);
      }
    };

    // Option choose buttons
    this.overlay
      .querySelectorAll<HTMLButtonElement>(".kh-re-btn-choose")
      .forEach((btn) => {
        btn.addEventListener("click", () => {
          const index = Number(btn.dataset.index);
          spawnOptionIndicators(index);
          this.disableAllActions();
          const label = state.currentEvent?.options[index]?.description ?? "your choice";
          this.showToast(`"${label}" — advancing to Play…`);
          this.actions.pickEventOption(index);
        });
      });

    // Full option card is also clickable (not just the button)
    this.overlay
      .querySelectorAll<HTMLDivElement>(".kh-re-option")
      .forEach((card) => {
        card.addEventListener("click", (e) => {
          // Avoid double-firing if the button inside was clicked
          if ((e.target as HTMLElement).closest(".kh-re-btn-choose")) return;
          const index = Number(card.dataset.index);
          spawnOptionIndicators(index);
          this.disableAllActions();
          const label = state.currentEvent?.options[index]?.description ?? "your choice";
          this.showToast(`"${label}" — advancing to Play…`);
          this.actions.pickEventOption(index);
        });
      });

    // No-event continue button
    const continueBtn = this.overlay.querySelector<HTMLButtonElement>(
      "#kh-re-btn-continue",
    );
    if (continueBtn) {
      continueBtn.addEventListener("click", () => {
        this.disableAllActions();
        this.showToast("Advancing to Play phase…");
        this.actions.skipEvent();
      });
    }
  }

  private disableAllActions(): void {
    this.overlay
      .querySelectorAll<HTMLButtonElement>("button")
      .forEach((b) => (b.disabled = true));
    this.overlay
      .querySelectorAll<HTMLDivElement>(".kh-re-option")
      .forEach((c) => c.classList.add("kh-re-option-disabled"));
  }

  // ─── Toast ──────────────────────────────────────────────────────────────

  private _toastTimer: ReturnType<typeof setTimeout> | null = null;

  private showToast(msg: string): void {
    const el = this.overlay.querySelector<HTMLDivElement>("#kh-re-toast");
    if (!el) return;
    el.textContent = msg;
    el.classList.add("kh-re-toast-visible");
    if (this._toastTimer) clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(
      () => el.classList.remove("kh-re-toast-visible"),
      5000,
    );
  }

  // ─── Style injection (once per page load) ───────────────────────────────

  private injectStylesOnce(): void {
    if (document.getElementById("kh-re-scene-styles")) return;

    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href =
      "https://fonts.googleapis.com/css2?family=Cinzel:wght@400;600;700&family=Crimson+Text:ital,wght@0,400;0,600;1,400&display=swap";
    document.head.appendChild(link);

    const style = document.createElement("style");
    style.id = "kh-re-scene-styles";
    style.textContent = `
      #kh-random-event-overlay {
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

      /* Header — identical structure to addPhaseScene */
      .kh-re-header {
        border-bottom: 4px solid #c49428;
        padding: 18px 48px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        box-shadow: 0 4px 20px rgba(0,0,0,.45);
      }
      .kh-re-ornament      { font-size: 2rem; color: #f0cc50; opacity: .8; user-select: none; }
      .kh-re-header-center { text-align: center; flex: 1; }
      .kh-re-header-center h1 {
        font-family: 'Cinzel', serif;
        font-size: 2.1rem;
        font-weight: 700;
        color: #f0cc50;
        letter-spacing: 4px;
        text-shadow: 0 2px 10px rgba(0,0,0,.6);
        margin: 0;
      }
      .kh-re-year-badge {
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

      /* Resources — identical to addPhaseScene */
      .kh-re-resources {
        background: linear-gradient(90deg, #1e1208 0%, #2e1c0c 50%, #1e1208 100%);
        border-bottom: 3px solid #c49428;
        padding: 10px 48px;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 64px;
      }
      .kh-re-resource    { display: flex; align-items: center; gap: 10px; }
      .kh-re-res-icon    { font-size: 1.5rem; line-height: 1; }
      .kh-re-res-label   { font-family: 'Cinzel', serif; font-size: .62rem; letter-spacing: 1.5px; color: #a09070; text-transform: uppercase; }
      .kh-re-res-value   { font-family: 'Cinzel', serif; font-size: 1.25rem; font-weight: 700; }
      .kh-re-pop  .kh-re-res-value { color: #f09090; }
      .kh-re-prod .kh-re-res-value { color: #f0cc60; }
      .kh-re-food .kh-re-res-value { color: #90e060; }
      .kh-re-res-divider { width: 1px; height: 32px; background: rgba(255,255,255,.08); }

      /* Phase tracker */
      .kh-re-phase-track {
        background: rgba(10,5,2,0.65);
        border-bottom: 2px solid rgba(196,160,96,0.35);
        padding: 12px 48px;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 6px;
      }
      .kh-re-phase-step {
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
      .kh-re-phase-step.kh-re-phase-active {
        color: #c49428;
        background: rgba(196,148,40,.2);
        border: 1px solid rgba(196,148,40,.4);
        font-weight: 700;
      }
      .kh-re-phase-dot   { width: 7px; height: 7px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
      .kh-re-phase-arrow { color: rgba(196,160,96,0.5); font-size: 1rem; padding: 0 4px; }

      /* Scene body */
      .kh-re-scene { max-width: 860px; margin: 0 auto; padding: 44px 28px 60px; }

      /* Event prompt */
      .kh-re-prompt       { text-align: center; margin-bottom: 40px; }
      .kh-re-event-label  {
        font-family: 'Cinzel', serif;
        font-size: .72rem;
        letter-spacing: 2px;
        text-transform: uppercase;
        color: #8b2020;
        margin-bottom: 12px;
      }
      .kh-re-event-desc {
        font-family: 'Crimson Text', Georgia, serif;
        font-size: 1.7rem;
        font-style: italic;
        color: #f0e8c0;
        line-height: 1.4;
        margin: 0 0 10px;
        max-width: 680px;
        margin-left: auto;
        margin-right: auto;
        text-shadow: 0 2px 10px rgba(0,0,0,.7);
      }
      .kh-re-event-sub { color: #c4b08c; font-size: 1rem; font-style: italic; margin: 0; text-shadow: 0 1px 6px rgba(0,0,0,.6); }

      /* Options grid — 1 column for single option, 2 for two, side-by-side for 3 */
      .kh-re-options-grid {
        display: grid;
        gap: 20px;
      }
      .kh-re-options-1 { grid-template-columns: 1fr;       max-width: 480px; margin: 0 auto; }
      .kh-re-options-2 { grid-template-columns: 1fr 1fr; }
      .kh-re-options-3 { grid-template-columns: repeat(3, 1fr); }

      /* Option card */
      .kh-re-option {
        background: #fdf6e4;
        border: 2px solid #c4a060;
        border-radius: 14px;
        overflow: hidden;
        cursor: pointer;
        transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
        box-shadow: 3px 5px 18px rgba(0,0,0,.12);
        display: flex;
        flex-direction: column;
      }
      .kh-re-option:hover {
        transform: translateY(-6px) scale(1.015);
        box-shadow: 4px 12px 26px rgba(0,0,0,.2);
        border-color: #8b2020;
      }
      .kh-re-option.kh-re-option-disabled {
        opacity: .55;
        filter: saturate(.5);
        cursor: not-allowed;
        pointer-events: none;
      }

      /* Option header */
      .kh-re-option-header {
        background: linear-gradient(135deg, #6b2020 0%, #4a1414 100%);
        border-bottom: 2px solid #c49428;
        padding: 18px 20px 14px;
        display: flex;
        align-items: flex-start;
        gap: 14px;
        min-height: 72px;
      }
      .kh-re-option-num {
        font-family: 'Cinzel', serif;
        font-size: 1.4rem;
        font-weight: 700;
        color: #f0cc50;
        flex-shrink: 0;
        line-height: 1.1;
      }
      .kh-re-option-desc {
        font-family: 'Cinzel', serif;
        font-size: .95rem;
        font-weight: 600;
        color: #f0e8c0;
        line-height: 1.3;
        letter-spacing: .3px;
      }

      /* Winter scaling banner */
      .kh-re-winter-banner {
        background: linear-gradient(90deg, #0e1e30 0%, #1a2e48 50%, #0e1e30 100%);
        border: 1px solid #4a7ab0;
        border-radius: 8px;
        padding: 9px 20px;
        margin-bottom: 22px;
        font-family: 'Cinzel', serif;
        font-size: .75rem;
        font-weight: 600;
        letter-spacing: 1.2px;
        color: #90c8f0;
        text-align: center;
        box-shadow: 0 2px 12px rgba(74,122,176,.25);
      }

      /* Scale annotation next to each effect modifier */
      .kh-re-scale-note {
        font-family: 'Crimson Text', Georgia, serif;
        font-size: .78rem;
        font-style: italic;
        color: #7090b0;
        margin-left: 2px;
      }

      /* Option effects */
      .kh-re-option-effects { padding: 14px 20px; flex: 1; }
      .kh-re-effects-label {
        font-family: 'Cinzel', serif;
        font-size: .6rem;
        letter-spacing: 1.5px;
        text-transform: uppercase;
        color: #7a6a52;
        margin-bottom: 8px;
      }
      .kh-re-effect-row    { display: flex; align-items: center; gap: 10px; padding: 3px 0; font-size: .9rem; }
      .kh-re-effect-target { font-family: 'Cinzel', serif; font-size: .68rem; font-weight: 600; letter-spacing: .8px; min-width: 90px; display: flex; align-items: center; gap: 4px; }
      .kh-re-et-food       { color: #3a7020; }
      .kh-re-et-production { color: #a07010; }
      .kh-re-et-population { color: #8b2020; }
      .kh-re-effect-mod    { font-family: 'Cinzel', serif; font-size: 1rem; font-weight: 700; }
      .kh-re-mod-pos       { color: #3d6b46; }
      .kh-re-mod-neg       { color: #8b2020; }
      .kh-re-no-effect     { font-style: italic; color: #a09070; font-size: .9rem; }

      /* Choose button */
      .kh-re-btn-choose {
        width: calc(100% - 40px);
        margin: 0 20px 18px;
        padding: 10px 0;
        border-radius: 8px;
        border: 2px solid #8b2020;
        background: linear-gradient(135deg, #6b2020 0%, #4a1414 100%);
        color: #f0e8c0;
        font-family: 'Cinzel', serif;
        font-size: .82rem;
        font-weight: 700;
        letter-spacing: 1px;
        cursor: pointer;
        transition: all .15s;
      }
      .kh-re-btn-choose:hover:not(:disabled) {
        background: linear-gradient(135deg, #8b2828 0%, #5e1a1a 100%);
        box-shadow: 0 2px 10px rgba(0,0,0,.28);
      }
      .kh-re-btn-choose:disabled { opacity: .5; cursor: not-allowed; }

      /* No-event state */
      .kh-re-no-event {
        text-align: center;
        padding: 60px 20px;
        max-width: 480px;
        margin: 0 auto;
      }
      .kh-re-no-event-icon { font-size: 4rem; margin-bottom: 20px; }
      .kh-re-no-event h2   { font-family: 'Cinzel', serif; font-size: 1.6rem; color: #f0e8c0; margin: 0 0 10px; text-shadow: 0 2px 8px rgba(0,0,0,.7); }
      .kh-re-no-event p    { color: #c4b08c; font-style: italic; font-size: 1.05rem; margin: 0 0 32px; text-shadow: 0 1px 6px rgba(0,0,0,.6); }
      .kh-re-btn-continue {
        padding: 13px 40px;
        background: linear-gradient(135deg, #3d6b46 0%, #274d30 100%);
        color: #f0e8c0;
        border: 2px solid #c49428;
        border-radius: 9px;
        font-family: 'Cinzel', serif;
        font-size: .9rem;
        font-weight: 700;
        letter-spacing: 1px;
        cursor: pointer;
        transition: all .15s;
        box-shadow: 2px 3px 12px rgba(0,0,0,.2);
      }
      .kh-re-btn-continue:hover:not(:disabled) {
        background: linear-gradient(135deg, #4e8858 0%, #325e3e 100%);
        box-shadow: 0 3px 14px rgba(61,107,70,.4);
      }
      .kh-re-btn-continue:disabled { opacity: .5; cursor: not-allowed; }

      /* Toast */
      .kh-re-toast {
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
      .kh-re-toast.kh-re-toast-visible { opacity: 1; transform: translateX(-50%) translateY(0); }
    `;
    document.head.appendChild(style);
  }
}
