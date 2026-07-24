// ─── Multiplayer overlays ─────────────────────────────────────────────────────
//
// DOM-layer UI for the two-player experience, kept out of the phase scenes so
// they stay single-kingdom views:
//
//   • Waiting overlay   — "Waiting for the other player…" once you've submitted
//                         the current phase but the opponent hasn't.
//   • Opponent HUD      — compact always-on strip showing the opponent's public
//                         resources and their ready state.
//   • Dual resolution   — the end-of-season screen that shows BOTH kingdoms'
//                         tables (full card art, yours on top of the rival's)
//                         resolving in real time, driven by each kingdom's
//                         server-computed resolutionLog, with floating resource
//                         indicators just like the single-player resolve.

import {
  Card,
  CardStaffing,
  CardTarget,
  Kingdom,
  MatchState,
  OpponentTarget,
  ResolutionStep,
  StaffingTier,
} from "./kingshand.ts";
import {
  cardImagePath,
  formatModifier,
  getCardDisplayInfo,
  resourceIcon,
  resourceLabel,
  resourceShortLabel,
} from "./cardCatalog.ts";
import { spawnResourceIndicator, healthBarHtml, injectSharedStyles } from "./sharedOverlays.ts";

// ─── Shared styles ────────────────────────────────────────────────────────────

let stylesInjected = false;

const injectStyles = (): void => {
  if (stylesInjected) return;
  stylesInjected = true;
  const style = document.createElement("style");
  style.textContent = `
    /* Waiting overlay */
    #kh-waiting-overlay {
      position: fixed; inset: 0; z-index: 8000;
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      background: rgba(8, 4, 2, 0.72); backdrop-filter: blur(2px);
      font-family: 'Cinzel', serif; pointer-events: all;
    }
    .kh-wait-hourglass { font-size: 3.4rem; margin-bottom: 18px;
                         animation: kh-wait-spin 2.4s ease-in-out infinite; }
    @keyframes kh-wait-spin { 0%,100% { transform: rotate(0deg); } 50% { transform: rotate(180deg); } }
    .kh-wait-title { font-size: 1.6rem; font-weight: 700; color: #f0cc50;
                     letter-spacing: 3px; margin: 0 0 10px; }
    .kh-wait-sub   { font-size: .95rem; color: #c4b08c; font-style: italic; margin: 0; }

    /* Opponent HUD — larger so the rival's resources are legible */
    #kh-opponent-hud {
      position: fixed; top: 8px; right: 12px; z-index: 7500;
      display: flex; align-items: center; gap: 16px;
      padding: 10px 18px; border-radius: 12px;
      border: 1px solid rgba(196,148,40,.55);
      background: linear-gradient(135deg, rgba(30,18,6,.92) 0%, rgba(14,8,3,.92) 100%);
      color: #e8d4a0; font-family: 'Cinzel', serif; font-size: 1rem;
      pointer-events: none;
    }
    #kh-opponent-hud .kh-opp-name { font-weight: 700; letter-spacing: 1px; color: #f0cc50; font-size: .95rem; }
    #kh-opponent-hud .kh-opp-res  { display: flex; gap: 14px; font-size: 1.05rem; }
    #kh-opponent-hud .kh-opp-res b { color: #f5e2ac; }
    #kh-opponent-hud .kh-opp-ready { font-size: .8rem; padding: 3px 10px; border-radius: 8px;
                                     border: 1px solid rgba(196,148,40,.4); }
    #kh-opponent-hud .kh-opp-ready.is-ready   { color: #9fdd8f; border-color: rgba(120,200,110,.6); }
    #kh-opponent-hud .kh-opp-ready.is-thinking { color: #d8b46a; }
    #kh-opponent-hud .kh-opp-ready.is-out { color: #e08a7a; border-color: rgba(220,110,90,.6); }

    /* ── Dual resolution screen ── full card-art tables, stacked (you on top) ── */
    #kh-dual-resolution {
      position: fixed; inset: 0; z-index: 8500;
      display: flex; flex-direction: column; align-items: center;
      background: radial-gradient(circle at 50% 30%, rgba(44,30,10,.97) 0%, rgba(8,5,2,.98) 80%);
      font-family: 'Cinzel', serif; color: #e8d4a0; overflow-y: auto; padding: 22px 16px 32px;
    }
    .kh-dual-title { font-size: 1.7rem; font-weight: 700; color: #f0cc50; letter-spacing: 4px;
                     margin: 2px 0 2px; }
    .kh-dual-sub   { font-size: .9rem; font-style: italic; color: #c4b08c; margin: 0 0 16px; }
    .kh-dr-stack   { display: flex; flex-direction: column; gap: 16px;
                     width: min(1120px, 97vw); }

    .kh-dr-panel   { border: 1px solid rgba(196,148,40,.45); border-radius: 14px;
                     padding: 12px 16px 16px;
                     background: linear-gradient(180deg, rgba(30,18,6,.85) 0%, rgba(16,9,3,.9) 100%); }
    .kh-dr-panel.is-you { border-color: rgba(240,204,80,.8);
                          box-shadow: 0 0 0 1px rgba(240,204,80,.25) inset; }
    .kh-dr-panel.is-out { opacity: .6; }

    /* Head is stacked + centered: name, then big resource numbers, then the health/defense bars. */
    .kh-dr-head { display: flex; flex-direction: column; align-items: stretch;
                  gap: 8px; margin-bottom: 12px; }
    .kh-dr-head-name { font-size: 1.05rem; font-weight: 700; letter-spacing: 2px; color: #f0cc50;
                       text-align: center; }
    .kh-dr-resnums { display: flex; justify-content: center; gap: 30px; font-size: 1.55rem; }
    .kh-dr-res { display: flex; align-items: center; gap: 8px; }
    .kh-dr-res b { color: #f5e2ac; font-weight: 700; }
    .kh-dr-res.kh-res-flash b { animation: kh-dr-flash .5s ease; }
    @keyframes kh-dr-flash { 0% { color: #fff; text-shadow: 0 0 10px #f0cc50; } 100% {} }

    /* Card row — reuses the play-scene card look, read-only. Art is ~2× the compact size. */
    .kh-dr-cards { display: flex; gap: 16px; flex-wrap: wrap; min-height: 260px;
                   align-items: flex-start; }
    /* overflow is visible (not hidden) so the hover preview can escape the card; the art image
       carries its own rounding instead. */
    .kh-dr-card { position: relative; width: 188px; height: 256px; border-radius: 14px;
                  border: 3px solid #6b5a34; overflow: visible; flex: 0 0 auto;
                  background: #1a1206; transition: box-shadow .2s, transform .2s, border-color .2s; }
    .kh-dr-card img { width: 100%; height: 100%; object-fit: cover; display: block;
                      border-radius: 11px; }
    .kh-dr-card.tier-full { border-color: #4caf50; }
    .kh-dr-card.tier-half { border-color: #f9a825; }
    .kh-dr-card.tier-none { border-color: #e53935; }
    .kh-dr-card-name { position: absolute; left: 0; right: 0; bottom: 0; padding: 6px 8px;
                       font-size: .82rem; font-weight: 700; text-align: center; color: #fff6dc;
                       background: linear-gradient(0deg, rgba(0,0,0,.85), rgba(0,0,0,0));
                       border-radius: 0 0 11px 11px;
                       white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .kh-dr-card-staff { position: absolute; top: 6px; right: 6px; font-size: .72rem;
                        padding: 2px 8px; border-radius: 9px; background: rgba(8,5,2,.82);
                        border: 1px solid rgba(196,148,40,.5); color: #e8d4a0; }
    .kh-dr-card.is-firing { border-color: #f0cc50; transform: translateY(-6px) scale(1.05);
                            box-shadow: 0 0 20px rgba(240,204,80,.65); z-index: 5; }
    .kh-dr-card.is-spent  { opacity: .5; }
    .kh-dr-card.is-sabotaged { border-color: #e0705a; box-shadow: 0 0 18px rgba(220,110,90,.6); }
    .kh-dr-empty { color: #7a6a52; font-style: italic; font-size: .85rem; padding: 44px 0; }

    /* Hover preview — mirrors the play/build-phase card tooltip, for BOTH kingdoms' tables. */
    .kh-dr-tip { position: absolute; left: 50%; bottom: calc(100% + 8px); transform: translateX(-50%);
                 width: 230px; z-index: 50; display: none;
                 padding: 10px 12px; border-radius: 10px;
                 border: 1px solid rgba(196,148,40,.6);
                 background: linear-gradient(180deg, rgba(34,22,8,.98) 0%, rgba(18,10,4,.98) 100%);
                 box-shadow: 0 6px 20px rgba(0,0,0,.6); text-align: left; pointer-events: none; }
    .kh-dr-card:hover .kh-dr-tip { display: block; }
    .kh-dr-tip-head { display: flex; align-items: center; justify-content: space-between; gap: 8px;
                      margin-bottom: 6px; }
    .kh-dr-tip-name { font-weight: 700; color: #f0cc50; font-size: .9rem; }
    .kh-dr-tip-freq { font-size: .6rem; font-weight: 700; letter-spacing: 1px; padding: 1px 6px;
                      border-radius: 6px; border: 1px solid rgba(196,148,40,.5); color: #d8c290; }
    .kh-dr-tip-freq.is-recurring { color: #9fdd8f; border-color: rgba(120,200,110,.5); }
    .kh-dr-tip-freq.is-single    { color: #d8b46a; }
    .kh-dr-tip-effs { display: flex; flex-direction: column; gap: 4px; }
    .kh-dr-tip-eff { font-size: .76rem; color: #e8d4a0; }
    .kh-dr-tip-eff-engine   { color: #a9c8e8; }
    .kh-dr-tip-eff-sabotage { color: #f0a08c; }

    /* Continue sits exactly where the play button lives — a fixed tab on the right edge, vertically
       centered — so the primary action never moves between the play screen and the resolution. */
    .kh-dual-continue {
      position: fixed;
      right: 0;
      top: 50%;
      transform: translateY(-50%);
      padding: 28px 88px 28px 56px;
      border: 3px solid #c49428;
      border-right: none;
      border-radius: 14px 0 0 14px;
      cursor: pointer;
      background: linear-gradient(135deg, #6b1a1a 0%, #4a0e0e 100%);
      color: #f0e8c0;
      font-family: 'Cinzel', serif;
      font-size: 1.8rem;
      font-weight: 700;
      letter-spacing: 4px;
      transition: all .2s ease;
      box-shadow: -6px 0 30px rgba(0,0,0,.5);
      z-index: 60;
      white-space: nowrap;
    }
    .kh-dual-continue:hover:not([disabled]) {
      background: linear-gradient(135deg, #7a2222 0%, #5a1414 100%);
      box-shadow: -6px 0 34px rgba(240,204,80,.35);
    }
    .kh-dual-continue[disabled] { opacity: .4; cursor: default; }
  `;
  document.head.appendChild(style);
};

// ─── Waiting overlay ──────────────────────────────────────────────────────────

export const showWaitingOverlay = (): void => {
  injectStyles();
  if (document.getElementById("kh-waiting-overlay")) return;
  const overlay = document.createElement("div");
  overlay.id = "kh-waiting-overlay";
  overlay.innerHTML = `
    <div class="kh-wait-hourglass">⏳</div>
    <h2 class="kh-wait-title">WAITING FOR THE OTHER PLAYER…</h2>
    <p class="kh-wait-sub">Your orders are sealed. The season resolves once both rulers are ready.</p>
  `;
  document.body.appendChild(overlay);
};

export const hideWaitingOverlay = (): void => {
  document.getElementById("kh-waiting-overlay")?.remove();
};

// ─── Opponent HUD ─────────────────────────────────────────────────────────────

/**
 * Creates or refreshes the compact opponent strip. No-ops for single-player
 * matches. `mySeat` picks which kingdom is "the opponent".
 */
export const updateOpponentHud = (match: MatchState, mySeat: number): void => {
  if (match.players.length < 2) return;
  injectStyles();
  const oppSeat = mySeat === 0 ? 1 : 0;
  const opp = match.players[oppSeat];

  let hud = document.getElementById("kh-opponent-hud");
  if (!hud) {
    hud = document.createElement("div");
    hud.id = "kh-opponent-hud";
    document.body.appendChild(hud);
  }

  const r = opp.resources;
  const readyState = opp.gameOver
    ? `<span class="kh-opp-ready is-out">${opp.forfeited ? "forfeited" : "fallen"}</span>`
    : match.hasSubmitted(oppSeat)
      ? `<span class="kh-opp-ready is-ready">✓ ready</span>`
      : `<span class="kh-opp-ready is-thinking">thinking…</span>`;

  hud.innerHTML = `
    <span class="kh-opp-name">⚔ RIVAL KINGDOM</span>
    <span class="kh-opp-res">
      <span>👥 <b>${Math.floor(r.population)}</b></span>
      <span>⚒️ <b>${Math.floor(r.production)}</b></span>
      <span>🌾 <b>${Math.floor(r.food)}</b></span>
      ${r.gold > 0 ? `<span>🪙 <b>${Math.floor(r.gold)}</b></span>` : ""}
      ${r.strength > 0 ? `<span>🛡 <b>${Math.floor(r.strength)}</b></span>` : ""}
    </span>
    ${readyState}
  `;
};

export const removeOpponentHud = (): void => {
  document.getElementById("kh-opponent-hud")?.remove();
};

// ─── Dual resolution screen ───────────────────────────────────────────────────

const delay = (ms: number): Promise<void> => new Promise((r) => setTimeout(r, ms));

/** Sums every step's deltas so we can back out the pre-resolution resource values. */
interface ResTotals {
  pop: number; prod: number; food: number; gold: number; strength: number; cap: number;
}
const sumDeltas = (log: ResolutionStep[]): ResTotals => {
  const t: ResTotals = { pop: 0, prod: 0, food: 0, gold: 0, strength: 0, cap: 0 };
  for (const s of log) {
    t.pop += s.populationDelta; t.prod += s.productionDelta; t.food += s.foodDelta;
    t.gold += s.goldDelta; t.strength += s.strengthDelta; t.cap += s.populationCapDelta;
  }
  return t;
};

/** True for the trailing harvest/upkeep step (no card). */
const isHarvestStep = (s: ResolutionStep): boolean =>
  s.cardName === undefined || s.cardName === null;

/** Renders a card's effects as compact spans, matching the play/build-phase tooltip. */
const cardEffectsHTML = (card: Card): string =>
  card.effects
    .map((e) => {
      if (e.target instanceof CardTarget) {
        const label = resourceShortLabel(e.target.targetResource);
        const scope = e.target.adjacentOnly ? `next ${label} card` : `${label} cards`;
        return `<span class="kh-dr-tip-eff kh-dr-tip-eff-engine">${resourceIcon(e.target.targetResource)} ${scope} ${formatModifier(e.modifier)}${card.frequencyType === "RECURRING" ? "/season" : " once"}</span>`;
      }
      if (e.target instanceof OpponentTarget) {
        return `<span class="kh-dr-tip-eff kh-dr-tip-eff-sabotage">⚔ rival ${resourceShortLabel(e.target.targetResource)} ${formatModifier(e.modifier)}${card.frequencyType === "RECURRING" ? "/season" : " once"}</span>`;
      }
      const targetStr = typeof e.target === "string" ? e.target : "CARD";
      return `<span class="kh-dr-tip-eff">${resourceIcon(targetStr)} ${resourceLabel(targetStr)} ${formatModifier(e.modifier)}${card.frequencyType === "RECURRING" ? "/season" : " once"}</span>`;
    })
    .join("");

/** Hover preview shown over a resolution card — the card's name, frequency, and effects. */
const cardTipHTML = (card: Card): string => {
  const info = getCardDisplayInfo(card);
  return `
    <div class="kh-dr-tip">
      <div class="kh-dr-tip-head">
        <span class="kh-dr-tip-name">${info.name}</span>
        <span class="kh-dr-tip-freq ${card.frequencyType === "RECURRING" ? "is-recurring" : "is-single"}">${card.frequencyType}</span>
      </div>
      <div class="kh-dr-tip-effs">${cardEffectsHTML(card) || '<span class="kh-dr-tip-eff">No effects</span>'}</div>
    </div>`;
};

/** Builds one kingdom panel: resource bar + full-art current-season table. */
const buildPanel = (
  parent: HTMLElement,
  title: string,
  isYou: boolean,
  kingdom: Kingdom,
  table: Card[],
  staffing: CardStaffing[],
): HTMLElement => {
  const panel = document.createElement("div");
  panel.className =
    `kh-dr-panel${isYou ? " is-you" : ""}${kingdom.gameOver ? " is-out" : ""}`;

  const r = kingdom.resources;
  const totals = sumDeltas(kingdom.resolutionLog);
  // Pre-resolution values = final − Σ deltas (server deltas are the exact applied changes).
  const pre = {
    pop: r.population - totals.pop,
    prod: r.production - totals.prod,
    food: r.food - totals.food,
    gold: r.gold - totals.gold,
    strength: r.strength - totals.strength,
  };
  const showGold = r.gold > 0 || pre.gold > 0;
  const showStrength = r.strength > 0 || pre.strength > 0;

  const cardsHTML =
    table.length === 0
      ? `<div class="kh-dr-empty">No works on the table this season.</div>`
      : table
          .map((card, i) => {
            const info = getCardDisplayInfo(card);
            const tier = staffing[i]?.tier;
            const tierClass =
              tier === StaffingTier.FULL ? "tier-full"
              : tier === StaffingTier.HALF ? "tier-half"
              : tier === StaffingTier.NONE ? "tier-none" : "";
            const staffChip = staffing[i]
              ? `<div class="kh-dr-card-staff">👥 ${staffing[i].workersAllocated}/${staffing[i].workerCost}</div>`
              : "";
            return `
              <div class="kh-dr-card ${tierClass}" data-card="${card.name}">
                <img src="${cardImagePath(card.name)}" alt="${info.name}" />
                ${staffChip}
                <div class="kh-dr-card-name">${info.name}</div>
                ${cardTipHTML(card)}
              </div>`;
          })
          .join("");

  panel.innerHTML = `
    <div class="kh-dr-head">
      <span class="kh-dr-head-name">${title}</span>
      <div class="kh-dr-resnums">
        <span class="kh-dr-res kh-dr-prod">⚒️ <b>${Math.floor(pre.prod)}</b></span>
        <span class="kh-dr-res kh-dr-food">🌾 <b>${Math.floor(pre.food)}</b></span>
        ${showGold ? `<span class="kh-dr-res kh-dr-gold">💰 <b>${Math.floor(pre.gold)}</b></span>` : ""}
      </div>
      ${healthBarHtml(pre.pop, r.populationCap, pre.strength, showStrength)}
    </div>
    <div class="kh-dr-cards">${cardsHTML}</div>
  `;
  parent.appendChild(panel);
  return panel;
};

/** Animates one kingdom's resolution log against its own panel, in real time. */
const animatePanel = async (
  panel: HTMLElement,
  kingdom: Kingdom,
  table: Card[],
): Promise<void> => {
  const r = kingdom.resources;
  const totals = sumDeltas(kingdom.resolutionLog);
  const sim = {
    pop: r.population - totals.pop,
    prod: r.production - totals.prod,
    food: r.food - totals.food,
    gold: r.gold - totals.gold,
    strength: r.strength - totals.strength,
  };

  const valEl = (cls: string): HTMLElement | null =>
    panel.querySelector<HTMLElement>(`.${cls} b`);
  const resEl = (cls: string): HTMLElement | null =>
    panel.querySelector<HTMLElement>(`.${cls}`);

  const setVal = (cls: string, v: number, flash: boolean): void => {
    const b = valEl(cls);
    if (b) b.textContent = Math.floor(v).toString();
    if (flash) {
      const wrap = resEl(cls);
      if (wrap) {
        wrap.classList.remove("kh-res-flash");
        void wrap.offsetWidth;
        wrap.classList.add("kh-res-flash");
      }
    }
  };

  const cardEls = Array.from(panel.querySelectorAll<HTMLElement>(".kh-dr-card"));
  let slot = 0; // pointer into table for matching card steps left-to-right

  for (const step of kingdom.resolutionLog) {
    // Which table card (if any) does this step correspond to?
    let firing: HTMLElement | null = null;
    if (!isHarvestStep(step) && step.cardName !== "SABOTAGE") {
      while (slot < table.length && table[slot].name !== step.cardName) slot++;
      if (slot < table.length) {
        firing = cardEls[slot] ?? null;
        slot++;
      }
    }
    const sabotaged = step.cardName === "SABOTAGE";

    if (firing) {
      firing.classList.add("is-firing");
      if (step.staffingTier === StaffingTier.NONE) firing.classList.add("is-spent");
    }
    // Sabotage lands ON this kingdom (its own log carries the incoming hit).
    if (sabotaged) cardEls.forEach((c) => c.classList.add("is-sabotaged"));

    // Apply the step's deltas (server-clamped, so forward-sum lands exactly on final).
    sim.pop += step.populationDelta;
    sim.prod += step.productionDelta;
    sim.food += step.foodDelta;
    sim.gold += step.goldDelta;
    sim.strength += step.strengthDelta;

    let si = 0;
    const anchor = (cls: string): HTMLElement | null => valEl(cls);
    // Population + Defense render as bars (matching the play phase); the rest stay as numbers.
    const cap = Math.max(1, r.populationCap);
    const setBarW = (sel: string, pct: number): void => {
      const el = panel.querySelector<HTMLElement>(sel);
      if (el) el.style.width = `${Math.max(0, Math.min(100, pct))}%`;
    };
    if (step.populationDelta !== 0) {
      const popNum = panel.querySelector<HTMLElement>(".kh-hpbar-pop");
      if (popNum) popNum.textContent = Math.floor(sim.pop).toString();
      setBarW(".kh-hpbar-fill", (sim.pop / cap) * 100);
      if (popNum) spawnResourceIndicator(popNum, step.populationDelta, "👥", si++);
    }
    if (step.productionDelta !== 0) {
      setVal("kh-dr-prod", sim.prod, true);
      const a = anchor("kh-dr-prod"); if (a) spawnResourceIndicator(a, step.productionDelta, "⚒️", si++);
    }
    if (step.foodDelta !== 0) {
      setVal("kh-dr-food", sim.food, true);
      const a = anchor("kh-dr-food"); if (a) spawnResourceIndicator(a, step.foodDelta, "🌾", si++);
    }
    if (step.goldDelta !== 0) {
      setVal("kh-dr-gold", sim.gold, true);
      const a = anchor("kh-dr-gold"); if (a) spawnResourceIndicator(a, step.goldDelta, "💰", si++);
    }
    if (step.strengthDelta !== 0) {
      const wallNum = panel.querySelector<HTMLElement>(".kh-hpbar-wallnum");
      if (wallNum) wallNum.textContent = Math.floor(sim.strength).toString();
      setBarW(".kh-hpbar-wallfill", (Math.min(sim.strength, cap) / cap) * 100);
      if (wallNum) spawnResourceIndicator(wallNum, step.strengthDelta, "⚔️", si++);
    }

    await delay(isHarvestStep(step) ? 1300 : 950);

    if (firing) firing.classList.remove("is-firing");
    if (sabotaged) cardEls.forEach((c) => c.classList.remove("is-sabotaged"));
  }
};

/**
 * Plays the end-of-season resolution for a two-player match: both kingdoms'
 * full-art tables resolve in real time — your kingdom on top of the rival's —
 * driven by each kingdom's server-computed resolutionLog, with floating resource
 * indicators. Resolves once the player clicks Continue (enabled after both
 * kingdoms finish).
 *
 * @param match  the post-resolution match state (resolutionLogs populated)
 * @param mySeat the viewer's seat
 */
export const playDualResolution = async (
  match: MatchState,
  mySeat: number,
  onContinue: () => void,
): Promise<void> => {
  injectStyles();
  injectSharedStyles(); // ensures the shared health-bar CSS is present for the resolution panels
  hideWaitingOverlay();
  document.getElementById("kh-dual-resolution")?.remove();

  const oppSeat = mySeat === 0 ? 1 : 0;
  const you = match.players[mySeat];
  const opp = match.players[oppSeat];
  // With deferred advance the clock is still on the resolved season while this screen is up, so
  // time.season is the table that just resolved.
  const season = match.time.season;

  const yourTable = you.board.get(season) ?? [];
  const oppTable = opp.board.get(season) ?? [];
  const yourStaff = you.tableStaffingBySeason.get(season) ?? [];
  const oppStaff = opp.tableStaffingBySeason.get(season) ?? [];

  const overlay = document.createElement("div");
  overlay.id = "kh-dual-resolution";
  overlay.innerHTML = `
    <h2 class="kh-dual-title">THE SEASON RESOLVES</h2>
    <p class="kh-dual-sub">Both kingdoms' works fire in order — your realm above, your rival's below.</p>
    <div class="kh-dr-stack"></div>
    <button class="kh-dual-continue" disabled>Continue</button>
  `;
  document.body.appendChild(overlay);

  const stack = overlay.querySelector<HTMLElement>(".kh-dr-stack")!;
  const youPanel = buildPanel(stack, "👑 YOUR KINGDOM", true, you, yourTable, yourStaff);
  const oppPanel = buildPanel(stack, "⚔ RIVAL KINGDOM", false, opp, oppTable, oppStaff);

  // Both kingdoms resolve simultaneously.
  await Promise.all([
    animatePanel(youPanel, you, yourTable),
    animatePanel(oppPanel, opp, oppTable),
  ]);

  const btn = overlay.querySelector<HTMLButtonElement>(".kh-dual-continue")!;
  btn.disabled = false;
  await new Promise<void>((resolve) => {
    btn.addEventListener(
      "click",
      () => {
        onContinue(); // tell the server this seat is ready; the clock advances once all seats are
        resolve();
      },
      { once: true },
    );
  });
  overlay.remove();
};
