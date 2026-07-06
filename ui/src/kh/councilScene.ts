import {
  GameState,
  Season,
  Phase,
  Advisor,
  SeatedAdvisor,
  ProposalKind,
  ReformBenefitType,
  DecreeType,
  ResourceTarget,
} from "./kingshand.ts";
import { formatModifier, resourceIcon, resourceLabel } from "./cardCatalog.ts";
import {
  getAdvisorDisplayInfo,
  buildPortraitPlaceholder,
  AdvisorReformInfo,
  AdvisorDecreeInfo,
} from "./advisorCatalog.ts";
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

export interface CouncilActions {
  hireAdvisor: (id: string) => void;
  skipCouncilAdd: () => void;
  acceptCouncilProposal: () => void;
  passCouncilProposal: () => void;
  dismissAdvisor: (id: string) => void;
}

// Mirrors GameEngine.MAX_COUNCIL_SIZE exactly.
const MAX_COUNCIL_SIZE = 3;

// Mirrors GameEngine.ADVISOR_DISMISSAL_COST_RATE exactly.
const ADVISOR_DISMISSAL_COST_RATE = 0.2;

// ─── Season display metadata (mirrors addPhaseScene's local SEASON_META) ─────

const SEASON_META: Record<
  Season,
  { label: string; icon: string; headerColor: string; accentColor: string; bg: string }
> = {
  [Season.SPRING]: { label: "Spring", icon: "🌿", headerColor: "#274d30", accentColor: "#3d6b46", bg: "/img/bg-spring.png" },
  [Season.SUMMER]: { label: "Summer", icon: "☀️",  headerColor: "#5a3a0a", accentColor: "#8a5a10", bg: "/img/bg-summer.png" },
  [Season.FALL]:   { label: "Fall",   icon: "🍂", headerColor: "#5a2a10", accentColor: "#8a4020", bg: "/img/bg-fall.png" },
  [Season.WINTER]: { label: "Winter", icon: "❄️",  headerColor: "#1a2a40", accentColor: "#2a4060", bg: "/img/bg-winter.png" },
};

/** Formats a Java enum-style Great Work name ("THE_GREAT_ROAD") as "The Great Road". */
function formatGreatWorkName(name: string | undefined): string {
  if (!name) return "a Great Work";
  return name
    .split("_")
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(" ");
}

// ─── CouncilScene ───────────────────────────────────────────────────────────────

/**
 * Full-screen HTML overlay covering both council sub-phases: {@link Phase#COUNCIL_ADD}
 * (hire an advisor or skip) and {@link Phase#COUNCIL_MEETING} (accept or pass the
 * year's single rotating Reform/Decree proposal). One scene class handles both —
 * {@code state.curPhase} picks which body renders — so main.ts can keep a single
 * scene instance alive across the COUNCIL_ADD → COUNCIL_MEETING transition.
 *
 * Advisor portraits are placeholders (a colored ring around a generic profile
 * circle, via {@link buildPortraitPlaceholder}) until real per-advisor art is
 * dropped in — see AdvisorCatalog.java's javadoc.
 *
 * Lifecycle mirrors {@link AddPhaseScene}: call {@link mount}, {@link update},
 * {@link unmount}.
 */
export class CouncilScene {
  private readonly overlay: HTMLDivElement;
  private readonly actions: CouncilActions;
  private state: GameState;

  constructor(state: GameState, actions: CouncilActions) {
    this.state = state;
    this.actions = actions;
    this.injectStylesOnce();
    injectSharedStyles();
    this.overlay = document.createElement("div");
    this.overlay.id = "kh-council-overlay";
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

    const bodyHTML =
      state.curPhase === Phase.COUNCIL_ADD
        ? this.buildCouncilAddBody(state)
        : this.buildCouncilMeetingBody(state);

    this.overlay.innerHTML = `
      ${this.buildHeader(state, meta)}
      ${this.buildResourcesBar(state)}
      ${this.buildPhaseTracker()}
      <main class="kh-cs-scene">
        ${this.buildRosterPanel(state)}
        ${bodyHTML}
      </main>
      ${buildDeckIndicator(state)}
      ${buildHandbookButton()}
      ${buildOptionsButton()}
      <div class="kh-cs-toast" id="kh-cs-toast"></div>
    `;

    // Apply season background image over a dark scrim for readability
    this.overlay.style.background =
      `linear-gradient(rgba(8,4,2,.62) 0%, rgba(8,4,2,.62) 100%), url('${meta.bg}') center/cover no-repeat`;

    this.attachListeners(state);
    attachDeckListeners(this.overlay, state);
    attachHandbookListeners(this.overlay);
    attachOptionsListeners(this.overlay);
  }

  private buildHeader(state: GameState, meta: (typeof SEASON_META)[Season]): string {
    return `
      <header class="kh-cs-header" style="
        background: linear-gradient(180deg, ${meta.headerColor} 0%, ${meta.accentColor} 60%, ${meta.headerColor} 100%);
      ">
        <span class="kh-cs-ornament">❧</span>
        <div class="kh-cs-header-center">
          <h1>King's Hand</h1>
          <div class="kh-cs-year-badge">
            <span>🏰</span>
            <span>YEAR ${state.year} — COUNCIL</span>
            <span>🏰</span>
          </div>
        </div>
        <span class="kh-cs-ornament">❧</span>
      </header>
    `;
  }

  private buildResourcesBar(state: GameState): string {
    const strengthHTML = state.resources.isUnlocked(ResourceTarget.STRENGTH) ? `
        <div class="kh-cs-res-divider"></div>
        <div class="kh-cs-resource kh-cs-strength">
          <span class="kh-cs-res-icon">⚔️</span>
          <div>
            <div class="kh-cs-res-label">Strength</div>
            <div class="kh-cs-res-value">${Math.floor(state.resources.strength)}</div>
          </div>
        </div>` : "";
    return `
      <div class="kh-cs-resources">
        <div class="kh-cs-resource kh-cs-pop">
          <span class="kh-cs-res-icon">👥</span>
          <div>
            <div class="kh-cs-res-label">Population</div>
            <div class="kh-cs-res-value">${Math.floor(state.resources.population)} <span class="kh-cs-res-cap">/ ${Math.floor(state.resources.populationCap)}</span></div>
          </div>
        </div>
        <div class="kh-cs-res-divider"></div>
        <div class="kh-cs-resource kh-cs-prod">
          <span class="kh-cs-res-icon">⚒️</span>
          <div>
            <div class="kh-cs-res-label">Production</div>
            <div class="kh-cs-res-value">${Math.floor(state.resources.production)}</div>
          </div>
        </div>
        <div class="kh-cs-res-divider"></div>
        <div class="kh-cs-resource kh-cs-food">
          <span class="kh-cs-res-icon">🌾</span>
          <div>
            <div class="kh-cs-res-label">Food</div>
            <div class="kh-cs-res-value">${Math.floor(state.resources.food)}</div>
          </div>
        </div>
        <div class="kh-cs-res-divider"></div>
        <div class="kh-cs-resource kh-cs-gold">
          <span class="kh-cs-res-icon">💰</span>
          <div>
            <div class="kh-cs-res-label">Gold</div>
            <div class="kh-cs-res-value">${Math.floor(state.resources.gold)}</div>
          </div>
        </div>
        ${strengthHTML}
      </div>
    `;
  }

  private buildPhaseTracker(): string {
    return `
      <div class="kh-cs-phase-track">
        <div class="kh-cs-phase-step">
          <span class="kh-cs-phase-dot"></span>I. ADD
        </div>
        <span class="kh-cs-phase-arrow">→</span>
        <div class="kh-cs-phase-step">
          <span class="kh-cs-phase-dot"></span>II. PLAY
        </div>
        <span class="kh-cs-phase-arrow">→</span>
        <div class="kh-cs-phase-step kh-cs-phase-active">
          <span class="kh-cs-phase-dot"></span>III. COUNCIL
        </div>
      </div>
    `;
  }

  // ── Council roster (always visible, both sub-phases) ───────────────────

  private buildRosterPanel(state: GameState): string {
    const seatsHTML = Array.from({ length: MAX_COUNCIL_SIZE }, (_, i) => state.council[i])
      .map((seated) => (seated ? this.buildFilledSeat(state, seated) : this.buildEmptySeat()))
      .join("");

    return `
      <div class="kh-cs-roster">
        <div class="kh-cs-roster-label">Your Council — ${state.council.length}/${MAX_COUNCIL_SIZE}</div>
        <div class="kh-cs-roster-seats">${seatsHTML}</div>
      </div>
    `;
  }

  private buildFilledSeat(state: GameState, seated: SeatedAdvisor): string {
    const info = getAdvisorDisplayInfo(seated.advisorId);
    const reformDone = seated.reformStacksTaken >= info.reform.maxStacks;
    const dismissCost = Math.round(info.hiringCost * ADVISOR_DISMISSAL_COST_RATE);
    const canAffordDismissal = state.resources.gold >= dismissCost;
    // Render the advisor's *live* card effects rather than trusting the static catalog
    // description alone: accepted Reforms/Decrees permanently boost (or add to) this card via
    // GameEngine#withEffectBoostOrAdd, so info.specialtyDescription's baked-in number goes stale
    // the moment a proposal is accepted, even though the PLAY-phase resolution already applies
    // the correct boosted value.
    const effectsHTML = seated.card.effects
      .map((e) => {
        const targetStr = typeof e.target === "string" ? e.target : "CARD";
        return `<span class="kh-cc-effect">${resourceIcon(targetStr)} ${resourceLabel(targetStr)} ${formatModifier(e.modifier, e.valueType)}/season</span>`;
      })
      .join("");
    return `
      <div class="kh-cs-seat" data-advisor-id="${seated.advisorId}">
        ${buildPortraitPlaceholder(info.ringColor, 56)}
        <div class="kh-cs-seat-name">${info.displayName}</div>
        <div class="kh-cs-seat-title">${info.title}</div>
        <div class="kh-cs-seat-progress">
          <span class="kh-cs-seat-tag ${reformDone ? "kh-cs-seat-tag-done" : ""}">⚖ ${seated.reformStacksTaken}/${info.reform.maxStacks}</span>
          <span class="kh-cs-seat-tag ${seated.decreeTaken ? "kh-cs-seat-tag-done" : ""}">📜 ${seated.decreeTaken ? "Spent" : "Ready"}</span>
        </div>

        <!-- ── Hover tooltip: what they do, and "let them go" ─────────────── -->
        <div class="kh-cs-seat-tooltip">
          <div class="kh-cs-seat-tt-name">${info.displayName} — ${info.title}</div>
          <div class="kh-cs-seat-tt-desc">${info.specialtyDescription}</div>
          ${effectsHTML ? `
          <div class="kh-cs-seat-tt-effects-label">Currently grants</div>
          <div class="kh-cs-seat-tt-effects">${effectsHTML}</div>
          ` : ""}
          <div class="kh-cs-seat-tt-footer">
            <button class="kh-cs-btn-letgo" data-advisor-id="${seated.advisorId}" ${canAffordDismissal ? "" : "disabled"}>
              👋 Let Go <span class="kh-cs-letgo-cost">💰 ${dismissCost}</span>
            </button>
            ${canAffordDismissal ? "" : `<div class="kh-cs-letgo-warn">Cannot afford</div>`}
          </div>
        </div>
      </div>
    `;
  }

  private buildEmptySeat(): string {
    return `
      <div class="kh-cs-seat kh-cs-seat-empty">
        <div class="kh-cs-seat-empty-ring">👤</div>
        <div class="kh-cs-seat-empty-label">Open Seat</div>
      </div>
    `;
  }

  // ── COUNCIL_ADD body ────────────────────────────────────────────────────

  private buildCouncilAddBody(state: GameState): string {
    const councilFull = state.council.length >= MAX_COUNCIL_SIZE;
    const choices = state.councilOfferChoices;

    const banner = councilFull
      ? `<div class="kh-cs-banner">👑 Your council is full — you cannot hire anyone new this year, but you may still review the offer.</div>`
      : "";

    const body =
      choices.length === 0
        ? `<div class="kh-cs-empty">No eligible advisors are available to hire this year.</div>`
        : `<div class="kh-cs-advisor-grid">${choices.map((a) => this.buildAdvisorCard(state, a, councilFull)).join("")}</div>`;

    return `
      <div class="kh-cs-prompt">
        <div class="kh-cs-prompt-label">The Council Convenes</div>
        <h2 class="kh-cs-prompt-title">Hire an advisor, or hold your gold</h2>
        <p class="kh-cs-prompt-sub">Hiring costs Gold immediately — once seated, an advisor acts every season automatically.</p>
      </div>
      ${banner}
      ${body}
      <button class="kh-cs-btn-skip" id="kh-cs-btn-skip">Skip — Hire No One →</button>
    `;
  }

  private buildAdvisorCard(state: GameState, advisor: Advisor, councilFull: boolean): string {
    const canAfford = state.resources.gold >= advisor.hiringCost;
    const disabled = councilFull || !canAfford;
    return `
      <div class="kh-cs-advisor-card ${disabled ? "kh-cs-advisor-card-disabled" : ""}" data-id="${advisor.id}">
        ${buildPortraitPlaceholder(advisor.ringColor, 80)}
        <div class="kh-cs-advisor-name">${advisor.displayName}</div>
        <div class="kh-cs-advisor-title">${advisor.title}</div>
        <div class="kh-cs-advisor-desc">${advisor.specialtyDescription}</div>
        <div class="kh-cs-advisor-cost ${canAfford ? "" : "kh-cs-cost-unaffordable"}">💰 ${advisor.hiringCost}</div>
        <button class="kh-cs-btn-hire" data-id="${advisor.id}" ${disabled ? "disabled" : ""}>
          ${councilFull ? "Council Full" : canAfford ? "Hire" : "Cannot Afford"}
        </button>
      </div>
    `;
  }

  // ── COUNCIL_MEETING body ────────────────────────────────────────────────

  private buildCouncilMeetingBody(state: GameState): string {
    const proposal = state.currentProposal;

    if (!proposal) {
      return `
        <div class="kh-cs-prompt">
          <div class="kh-cs-prompt-label">The Council Convenes</div>
          <h2 class="kh-cs-prompt-title">No new business this year</h2>
          <p class="kh-cs-prompt-sub">Every seated advisor has already exhausted what they have to propose — or your council sits empty.</p>
        </div>
        <button class="kh-cs-btn-continue" id="kh-cs-btn-continue">Continue to Add Phase →</button>
      `;
    }

    const info = getAdvisorDisplayInfo(proposal.advisorId);
    const seated = state.council.find((s) => s.advisorId === proposal.advisorId);
    const kindLabel = proposal.kind === ProposalKind.REFORM ? "Reform" : "Decree";
    const kindIcon = proposal.kind === ProposalKind.REFORM ? "⚖" : "📜";

    const effectsHTML =
      proposal.kind === ProposalKind.REFORM
        ? this.buildReformEffects(info.reform, seated)
        : this.buildDecreeEffects(info.decree);

    return `
      <div class="kh-cs-prompt">
        <div class="kh-cs-prompt-label">The Council Convenes</div>
        <h2 class="kh-cs-prompt-title">${info.displayName} proposes a ${kindLabel.toLowerCase()}</h2>
        <p class="kh-cs-prompt-sub">${info.title}</p>
      </div>
      <div class="kh-cs-proposal-card">
        <div class="kh-cs-proposal-header">
          ${buildPortraitPlaceholder(info.ringColor, 84)}
          <div class="kh-cs-proposal-header-text">
            <div class="kh-cs-proposal-kind kh-cs-proposal-kind-${proposal.kind.toLowerCase()}">${kindIcon} ${kindLabel}</div>
            <div class="kh-cs-proposal-name">${proposal.name}</div>
          </div>
        </div>
        <div class="kh-cs-proposal-effects">
          ${effectsHTML}
        </div>
      </div>
      <div class="kh-cs-proposal-actions">
        <button class="kh-cs-btn-pass" id="kh-cs-btn-pass">Pass</button>
        <button class="kh-cs-btn-accept" id="kh-cs-btn-accept">Accept</button>
      </div>
    `;
  }

  private buildReformEffects(reform: AdvisorReformInfo, seated: SeatedAdvisor | undefined): string {
    const stackNote = seated
      ? `<div class="kh-cs-effects-label">Stack ${seated.reformStacksTaken + 1} of ${reform.maxStacks}</div>`
      : "";

    let benefitRow: string;
    switch (reform.benefitType) {
      case ReformBenefitType.FREE_REROLL:
        benefitRow = this.buildEffectRow("🎲", "FREE REROLL", `${formatModifier(reform.benefitPerStack, "INTEGER")}/season`, true);
        break;
      case ReformBenefitType.EXTRA_ADD_CANDIDATE:
        benefitRow = this.buildEffectRow("🔍", "ADD CANDIDATES", formatModifier(reform.benefitPerStack, "INTEGER"), true);
        break;
      case ReformBenefitType.CROSS_TABLE_BOOST:
        benefitRow = this.buildEffectRow("⚡", "CROSS-TABLE BOOST", `${formatModifier(reform.benefitPerStack, "INTEGER")}/season`, true);
        break;
      case ReformBenefitType.CARD_EFFECT:
      default:
        benefitRow = this.buildEffectRow(
          resourceIcon(reform.benefitResource!),
          resourceLabel(reform.benefitResource!),
          `${formatModifier(reform.benefitPerStack, "INTEGER")}/season`,
          true,
        );
        break;
    }

    const costRow = this.buildEffectRow(
      resourceIcon(reform.costResource),
      resourceLabel(reform.costResource),
      `${formatModifier(reform.costPerStack, "INTEGER")}/season`,
      false,
    );

    return `
      ${stackNote}
      ${benefitRow}
      ${costRow}
      <div class="kh-cs-effects-note">Permanently strengthens this advisor's own card — the cost stacks alongside the benefit, forever.</div>
    `;
  }

  private buildDecreeEffects(decree: AdvisorDecreeInfo): string {
    switch (decree.type) {
      case DecreeType.PASSIVE_BOOST:
        return this.buildEffectRow(
          resourceIcon(decree.effectResource!),
          resourceLabel(decree.effectResource!),
          `${formatModifier(decree.effectAmount, "INTEGER")}/season (permanent)`,
          true,
        );
      case DecreeType.GREAT_WORK_BOOST:
        return this.buildEffectRow(
          resourceIcon(decree.effectResource!),
          resourceLabel(decree.effectResource!),
          `${formatModifier(decree.effectAmount, "INTEGER")} once, to ${formatGreatWorkName(decree.targetGreatWorkName)}`,
          true,
        );
      case DecreeType.POPULATION_CAP_BOOST:
        return this.buildEffectRow("🏠", "POPULATION CAP", `${formatModifier(decree.effectAmount, "INTEGER")} (once)`, true);
      case DecreeType.GREAT_WORK_ACCELERATE:
        return this.buildEffectRow("🏗️", "GREAT WORK", "Advances one Great Work under construction by a full season, instantly", true);
      case DecreeType.FREE_REROLL:
        return this.buildEffectRow("🎲", "FREE REROLL", `+${decree.effectAmount} permanent, every season`, true);
      default:
        return "";
    }
  }

  private buildEffectRow(icon: string, label: string, value: string, isPositive: boolean): string {
    return `
      <div class="kh-cs-effect-row">
        <span class="kh-cs-effect-target">${icon} ${label}</span>
        <span class="kh-cs-effect-mod ${isPositive ? "kh-cs-mod-pos" : "kh-cs-mod-neg"}">${value}</span>
      </div>
    `;
  }

  // ─── Event listeners ────────────────────────────────────────────────────

  private attachListeners(state: GameState): void {
    this.attachRosterListeners();
    if (state.curPhase === Phase.COUNCIL_ADD) {
      this.attachCouncilAddListeners(state);
    } else {
      this.attachCouncilMeetingListeners();
    }
  }

  /** Wires the "let go" button on the roster panel — present in both council sub-phases. */
  private attachRosterListeners(): void {
    this.overlay.querySelectorAll<HTMLButtonElement>(".kh-cs-btn-letgo").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.dataset.advisorId;
        if (!id) return;
        const info = getAdvisorDisplayInfo(id);
        this.disableAllActions();
        this.showToast(`Letting ${info.displayName} go…`);
        this.actions.dismissAdvisor(id);
      });
    });
  }

  private attachCouncilAddListeners(state: GameState): void {
    this.overlay.querySelectorAll<HTMLButtonElement>(".kh-cs-btn-hire").forEach((btn) => {
      btn.addEventListener("click", () => {
        const id = btn.dataset.id;
        if (!id) return;
        const advisor = state.councilOfferChoices.find((a) => a.id === id);
        this.disableAllActions();
        this.showToast(`Hiring ${advisor?.displayName ?? "advisor"}…`);
        this.actions.hireAdvisor(id);
      });
    });

    const skipBtn = this.overlay.querySelector<HTMLButtonElement>("#kh-cs-btn-skip");
    skipBtn?.addEventListener("click", () => {
      this.disableAllActions();
      this.showToast("Holding your gold this year…");
      this.actions.skipCouncilAdd();
    });
  }

  private attachCouncilMeetingListeners(): void {
    const continueBtn = this.overlay.querySelector<HTMLButtonElement>("#kh-cs-btn-continue");
    continueBtn?.addEventListener("click", () => {
      this.disableAllActions();
      this.showToast("Advancing to the Add phase…");
      this.actions.passCouncilProposal();
    });

    const acceptBtn = this.overlay.querySelector<HTMLButtonElement>("#kh-cs-btn-accept");
    acceptBtn?.addEventListener("click", () => {
      this.disableAllActions();
      this.showToast("Proposal accepted…");
      this.actions.acceptCouncilProposal();
    });

    const passBtn = this.overlay.querySelector<HTMLButtonElement>("#kh-cs-btn-pass");
    passBtn?.addEventListener("click", () => {
      this.disableAllActions();
      this.showToast("Proposal passed…");
      this.actions.passCouncilProposal();
    });
  }

  private disableAllActions(): void {
    this.overlay.querySelectorAll<HTMLButtonElement>("button").forEach((b) => (b.disabled = true));
    this.overlay
      .querySelectorAll<HTMLDivElement>(".kh-cs-advisor-card")
      .forEach((c) => c.classList.add("kh-cs-advisor-card-disabled"));
  }

  // ─── Toast ──────────────────────────────────────────────────────────────

  private _toastTimer: ReturnType<typeof setTimeout> | null = null;

  private showToast(msg: string): void {
    const el = this.overlay.querySelector<HTMLDivElement>("#kh-cs-toast");
    if (!el) return;
    el.textContent = msg;
    el.classList.add("kh-cs-toast-visible");
    if (this._toastTimer) clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => el.classList.remove("kh-cs-toast-visible"), 5000);
  }

  // ─── Style injection (once per page load) ───────────────────────────────

  private injectStylesOnce(): void {
    if (document.getElementById("kh-cs-scene-styles")) return;

    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href =
      "https://fonts.googleapis.com/css2?family=Cinzel:wght@400;600;700&family=Crimson+Text:ital,wght@0,400;0,600;1,400&display=swap";
    document.head.appendChild(link);

    const style = document.createElement("style");
    style.id = "kh-cs-scene-styles";
    style.textContent = `
      #kh-council-overlay {
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
      .kh-cs-header {
        border-bottom: 4px solid #c49428;
        padding: 18px 48px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        box-shadow: 0 4px 20px rgba(0,0,0,.45);
      }
      .kh-cs-ornament      { font-size: 2rem; color: #f0cc50; opacity: .8; user-select: none; }
      .kh-cs-header-center { text-align: center; flex: 1; }
      .kh-cs-header-center h1 {
        font-family: 'Cinzel', serif;
        font-size: 2.1rem;
        font-weight: 700;
        color: #f0cc50;
        letter-spacing: 4px;
        text-shadow: 0 2px 10px rgba(0,0,0,.6);
        margin: 0;
      }
      .kh-cs-year-badge {
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
      .kh-cs-resources {
        background: linear-gradient(90deg, #1e1208 0%, #2e1c0c 50%, #1e1208 100%);
        border-bottom: 3px solid #c49428;
        padding: 10px 48px;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 64px;
        flex-wrap: wrap;
      }
      .kh-cs-resource    { display: flex; align-items: center; gap: 10px; }
      .kh-cs-res-icon    { font-size: 1.5rem; line-height: 1; }
      .kh-cs-res-label   { font-family: 'Cinzel', serif; font-size: .62rem; letter-spacing: 1.5px; color: #a09070; text-transform: uppercase; }
      .kh-cs-res-value   { font-family: 'Cinzel', serif; font-size: 1.25rem; font-weight: 700; }
      .kh-cs-pop  .kh-cs-res-value { color: #f09090; }
      .kh-cs-prod .kh-cs-res-value { color: #f0cc60; }
      .kh-cs-food .kh-cs-res-value { color: #90e060; }
      .kh-cs-gold .kh-cs-res-value { color: #ffd700; }
      .kh-cs-strength .kh-cs-res-value { color: #9ab0d0; }
      .kh-cs-res-cap { font-size: .85rem; font-weight: 400; color: #9a7a7a; opacity: .85; }
      .kh-cs-res-divider { width: 1px; height: 32px; background: rgba(255,255,255,.08); }

      /* Phase tracker */
      .kh-cs-phase-track {
        background: rgba(10,5,2,0.65);
        border-bottom: 2px solid rgba(196,160,96,0.35);
        padding: 12px 48px;
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 6px;
      }
      .kh-cs-phase-step {
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
      .kh-cs-phase-step.kh-cs-phase-active {
        color: #c49428;
        background: rgba(196,148,40,.2);
        border: 1px solid rgba(196,148,40,.4);
        font-weight: 700;
      }
      .kh-cs-phase-dot   { width: 7px; height: 7px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
      .kh-cs-phase-arrow { color: rgba(196,160,96,0.5); font-size: 1rem; padding: 0 4px; }

      /* Scene body */
      .kh-cs-scene { max-width: 980px; margin: 0 auto; padding: 36px 28px 60px; }

      /* Council roster */
      .kh-cs-roster {
        background: rgba(20,12,4,.55);
        border: 1px solid rgba(196,148,40,.35);
        border-radius: 14px;
        padding: 16px 20px;
        margin-bottom: 32px;
      }
      .kh-cs-roster-label {
        font-family: 'Cinzel', serif;
        font-size: .68rem;
        letter-spacing: 1.5px;
        text-transform: uppercase;
        color: #c49428;
        margin-bottom: 12px;
        text-align: center;
      }
      .kh-cs-roster-seats {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 14px;
      }
      .kh-cs-seat {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 6px;
        background: rgba(255,255,255,.04);
        border: 1px solid rgba(196,148,40,.2);
        border-radius: 10px;
        padding: 14px 10px;
        text-align: center;
        position: relative;
      }
      .kh-cs-seat:hover { z-index: 10; }
      .kh-cs-seat-empty { border-style: dashed; opacity: .6; }
      .kh-cs-seat-empty-ring {
        width: 56px; height: 56px; min-width: 56px;
        border-radius: 50%;
        border: 3px dashed rgba(196,148,40,.4);
        display: flex; align-items: center; justify-content: center;
        font-size: 1.6rem;
        opacity: .5;
      }
      .kh-cs-seat-empty-label {
        font-family: 'Cinzel', serif;
        font-size: .68rem;
        letter-spacing: 1px;
        color: #8a7a60;
        text-transform: uppercase;
      }
      .kh-cs-seat-name  { font-family: 'Cinzel', serif; font-size: .82rem; font-weight: 700; color: #f0e8c0; }
      .kh-cs-seat-title { font-style: italic; font-size: .76rem; color: #a8946a; }
      .kh-cs-seat-progress { display: flex; gap: 6px; margin-top: 2px; flex-wrap: wrap; justify-content: center; }
      .kh-cs-seat-tag {
        font-family: 'Cinzel', serif;
        font-size: .62rem;
        letter-spacing: .5px;
        padding: 2px 7px;
        border-radius: 8px;
        background: rgba(196,148,40,.15);
        color: #c4a060;
      }
      .kh-cs-seat-tag-done { background: rgba(100,180,100,.18); color: #90d090; }

      /* ── Seated-advisor hover tooltip: what they do, and "let them go" ──── */
      .kh-cs-seat-tooltip {
        display: none;
        position: absolute;
        bottom: calc(100% + 8px);
        left: 50%;
        transform: translateX(-50%);
        width: 260px;
        background: linear-gradient(160deg, #2a1a08 0%, #1a100a 100%);
        border: 2px solid #c49428;
        border-radius: 12px;
        padding: 14px 16px 12px;
        box-shadow: 0 10px 36px rgba(0,0,0,.75);
        z-index: 30;
        pointer-events: none;
        text-align: left;
      }
      .kh-cs-seat:hover .kh-cs-seat-tooltip {
        display: block;
        pointer-events: auto;
      }
      .kh-cs-seat-tt-name {
        font-family: 'Cinzel', serif;
        font-size: .82rem;
        font-weight: 700;
        color: #f0e8c0;
        margin-bottom: 8px;
        padding-bottom: 7px;
        border-bottom: 1px solid rgba(196,160,96,.3);
      }
      .kh-cs-seat-tt-desc {
        font-family: 'Crimson Text', serif;
        font-size: .82rem;
        line-height: 1.4;
        color: #d4c090;
        margin-bottom: 10px;
      }
      .kh-cs-seat-tt-effects-label {
        font-size: .72rem;
        color: #9a8860;
        margin-top: -4px;
        margin-bottom: 5px;
      }
      .kh-cs-seat-tt-effects {
        display: flex;
        flex-wrap: wrap;
        gap: 5px;
        margin-bottom: 8px;
      }
      .kh-cs-seat-tt-footer { border-top: 1px dashed rgba(196,160,96,.3); padding-top: 10px; }
      .kh-cs-btn-letgo {
        width: 100%;
        padding: 7px 0;
        border-radius: 7px;
        border: 1px solid #a06040;
        background: linear-gradient(135deg, #7a3020 0%, #5a1e10 100%);
        color: #f0d0b0;
        font-family: 'Cinzel', serif;
        font-size: .68rem;
        font-weight: 700;
        letter-spacing: .5px;
        cursor: pointer;
      }
      .kh-cs-btn-letgo:hover:not(:disabled) {
        background: linear-gradient(135deg, #9a3828 0%, #722416 100%);
        box-shadow: 0 2px 8px rgba(0,0,0,.28);
      }
      .kh-cs-btn-letgo:disabled { background: #9a8a70; border-color: #b0a080; color: #d0c0a0; cursor: not-allowed; opacity: .65; }
      .kh-cs-letgo-cost { font-weight: 400; opacity: .85; }
      .kh-cs-letgo-warn { text-align: center; font-family: 'Cinzel', serif; font-size: .58rem; color: #a06040; margin-top: 5px; letter-spacing: .5px; }

      /* Prompt */
      .kh-cs-prompt       { text-align: center; margin-bottom: 32px; }
      .kh-cs-prompt-label {
        font-family: 'Cinzel', serif;
        font-size: .72rem;
        letter-spacing: 2px;
        text-transform: uppercase;
        color: #8b6a20;
        margin-bottom: 12px;
      }
      .kh-cs-prompt-title {
        font-family: 'Cinzel', serif;
        font-size: 1.6rem;
        font-weight: 700;
        color: #f0e8c0;
        line-height: 1.35;
        margin: 0 0 10px;
        text-shadow: 0 2px 10px rgba(0,0,0,.7);
      }
      .kh-cs-prompt-sub { color: #c4b08c; font-size: 1rem; font-style: italic; margin: 0; text-shadow: 0 1px 6px rgba(0,0,0,.6); }

      /* Banner / empty states */
      .kh-cs-banner {
        background: linear-gradient(90deg, #3a2a0a 0%, #4a3812 50%, #3a2a0a 100%);
        border: 1px solid #c49428;
        border-radius: 8px;
        padding: 10px 20px;
        margin-bottom: 22px;
        font-family: 'Cinzel', serif;
        font-size: .78rem;
        font-weight: 600;
        letter-spacing: .5px;
        color: #f0cc50;
        text-align: center;
      }
      .kh-cs-empty {
        text-align: center;
        font-style: italic;
        color: #8a7a60;
        padding: 30px 0 10px;
      }

      /* Advisor offer grid */
      .kh-cs-advisor-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 20px;
        margin-bottom: 28px;
      }
      .kh-cs-advisor-card {
        background: #fdf6e4;
        border: 2px solid #c4a060;
        border-radius: 14px;
        padding: 22px 18px;
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: 8px;
        box-shadow: 3px 5px 18px rgba(0,0,0,.12);
        transition: transform .2s ease, box-shadow .2s ease;
      }
      .kh-cs-advisor-card:not(.kh-cs-advisor-card-disabled):hover {
        transform: translateY(-6px) scale(1.015);
        box-shadow: 4px 12px 26px rgba(0,0,0,.2);
      }
      .kh-cs-advisor-card-disabled { opacity: .55; filter: saturate(.5); }
      .kh-cs-advisor-name  { font-family: 'Cinzel', serif; font-size: 1rem; font-weight: 700; color: #3a2a10; margin-top: 4px; }
      .kh-cs-advisor-title { font-style: italic; font-size: .85rem; color: #8a6a30; }
      .kh-cs-advisor-desc  { font-size: .92rem; color: #4a3a20; line-height: 1.45; margin: 4px 0; }
      .kh-cs-advisor-cost  {
        font-family: 'Cinzel', serif;
        font-size: 1.05rem;
        font-weight: 700;
        color: #8a6a10;
      }
      .kh-cs-cost-unaffordable { color: #8b2020; }

      .kh-cs-btn-hire {
        width: 100%;
        margin-top: 6px;
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
      .kh-cs-btn-hire:hover:not(:disabled) {
        background: linear-gradient(135deg, #8b2828 0%, #5e1a1a 100%);
        box-shadow: 0 2px 10px rgba(0,0,0,.28);
      }
      .kh-cs-btn-hire:disabled { opacity: .5; cursor: not-allowed; }

      .kh-cs-btn-skip {
        display: block;
        margin: 0 auto;
        padding: 12px 36px;
        background: rgba(20,12,4,.5);
        color: #d4c090;
        border: 2px solid rgba(196,148,40,.4);
        border-radius: 9px;
        font-family: 'Cinzel', serif;
        font-size: .85rem;
        font-weight: 700;
        letter-spacing: 1px;
        cursor: pointer;
        transition: all .15s;
      }
      .kh-cs-btn-skip:hover:not(:disabled) { border-color: #c49428; color: #f0cc50; }
      .kh-cs-btn-skip:disabled { opacity: .5; cursor: not-allowed; }

      /* Proposal card (COUNCIL_MEETING) */
      .kh-cs-proposal-card {
        background: #fdf6e4;
        border: 2px solid #c4a060;
        border-radius: 14px;
        overflow: hidden;
        box-shadow: 3px 5px 18px rgba(0,0,0,.18);
        max-width: 560px;
        margin: 0 auto 28px;
      }
      .kh-cs-proposal-header {
        background: linear-gradient(135deg, #2a1608 0%, #1a0e06 100%);
        border-bottom: 2px solid #c49428;
        padding: 20px 22px;
        display: flex;
        align-items: center;
        gap: 18px;
      }
      .kh-cs-proposal-header-text { text-align: left; }
      .kh-cs-proposal-kind {
        display: inline-block;
        font-family: 'Cinzel', serif;
        font-size: .68rem;
        font-weight: 700;
        letter-spacing: 1.2px;
        text-transform: uppercase;
        padding: 3px 10px;
        border-radius: 8px;
        margin-bottom: 6px;
      }
      .kh-cs-proposal-kind-reform { background: rgba(120,150,220,.2); color: #98b4ec; }
      .kh-cs-proposal-kind-decree { background: rgba(220,170,80,.22); color: #f0cc60; }
      .kh-cs-proposal-name {
        font-family: 'Cinzel', serif;
        font-size: 1.15rem;
        font-weight: 700;
        color: #f0e8c0;
      }
      .kh-cs-proposal-effects { padding: 18px 22px 20px; }
      .kh-cs-effects-label {
        font-family: 'Cinzel', serif;
        font-size: .65rem;
        letter-spacing: 1.2px;
        text-transform: uppercase;
        color: #8a7a52;
        margin-bottom: 10px;
      }
      .kh-cs-effect-row    { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 5px 0; font-size: .95rem; }
      .kh-cs-effect-target { font-family: 'Cinzel', serif; font-size: .72rem; font-weight: 600; letter-spacing: .8px; display: flex; align-items: center; gap: 6px; color: #5a4828; }
      .kh-cs-effect-mod    { font-family: 'Cinzel', serif; font-size: 1.05rem; font-weight: 700; }
      .kh-cs-mod-pos       { color: #3d6b46; }
      .kh-cs-mod-neg       { color: #8b2020; }
      .kh-cs-effects-note {
        margin-top: 12px;
        font-size: .85rem;
        font-style: italic;
        color: #8a7a60;
        border-top: 1px dashed rgba(196,148,40,.3);
        padding-top: 10px;
      }

      .kh-cs-proposal-actions {
        display: flex;
        justify-content: center;
        gap: 18px;
      }
      .kh-cs-btn-accept, .kh-cs-btn-pass, .kh-cs-btn-continue {
        padding: 13px 40px;
        border-radius: 9px;
        font-family: 'Cinzel', serif;
        font-size: .9rem;
        font-weight: 700;
        letter-spacing: 1px;
        cursor: pointer;
        transition: all .15s;
      }
      .kh-cs-btn-accept {
        background: linear-gradient(135deg, #3d6b46 0%, #274d30 100%);
        color: #f0e8c0;
        border: 2px solid #c49428;
        box-shadow: 2px 3px 12px rgba(0,0,0,.2);
      }
      .kh-cs-btn-accept:hover:not(:disabled) {
        background: linear-gradient(135deg, #4e8858 0%, #325e3e 100%);
        box-shadow: 0 3px 14px rgba(61,107,70,.4);
      }
      .kh-cs-btn-pass {
        background: rgba(20,12,4,.5);
        color: #d4c090;
        border: 2px solid rgba(196,148,40,.4);
      }
      .kh-cs-btn-pass:hover:not(:disabled) { border-color: #c49428; color: #f0cc50; }
      .kh-cs-btn-continue {
        display: block;
        margin: 0 auto;
        background: linear-gradient(135deg, #3d6b46 0%, #274d30 100%);
        color: #f0e8c0;
        border: 2px solid #c49428;
        box-shadow: 2px 3px 12px rgba(0,0,0,.2);
      }
      .kh-cs-btn-continue:hover:not(:disabled) {
        background: linear-gradient(135deg, #4e8858 0%, #325e3e 100%);
        box-shadow: 0 3px 14px rgba(61,107,70,.4);
      }
      .kh-cs-btn-accept:disabled, .kh-cs-btn-pass:disabled, .kh-cs-btn-continue:disabled { opacity: .5; cursor: not-allowed; }

      /* Toast */
      .kh-cs-toast {
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
      .kh-cs-toast.kh-cs-toast-visible { opacity: 1; transform: translateX(-50%) translateY(0); }
    `;
    document.head.appendChild(style);
  }
}
