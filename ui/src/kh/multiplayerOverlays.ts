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
//                         tables firing left-to-right at the same time, driven
//                         by each kingdom's server-computed resolutionLog.

import { Card, Kingdom, MatchState, ResolutionStep, StaffingTier } from "./kingshand.ts";
import { getCardDisplayInfo } from "./cardCatalog.ts";

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

    /* Opponent HUD */
    #kh-opponent-hud {
      position: fixed; top: 8px; right: 12px; z-index: 7500;
      display: flex; align-items: center; gap: 14px;
      padding: 8px 14px; border-radius: 10px;
      border: 1px solid rgba(196,148,40,.55);
      background: linear-gradient(135deg, rgba(30,18,6,.92) 0%, rgba(14,8,3,.92) 100%);
      color: #e8d4a0; font-family: 'Cinzel', serif; font-size: .82rem;
      pointer-events: none;
    }
    #kh-opponent-hud .kh-opp-name { font-weight: 700; letter-spacing: 1px; color: #f0cc50; }
    #kh-opponent-hud .kh-opp-res  { display: flex; gap: 10px; }
    #kh-opponent-hud .kh-opp-ready { font-size: .75rem; padding: 2px 8px; border-radius: 8px;
                                     border: 1px solid rgba(196,148,40,.4); }
    #kh-opponent-hud .kh-opp-ready.is-ready   { color: #9fdd8f; border-color: rgba(120,200,110,.6); }
    #kh-opponent-hud .kh-opp-ready.is-thinking { color: #d8b46a; }
    #kh-opponent-hud .kh-opp-ready.is-out { color: #e08a7a; border-color: rgba(220,110,90,.6); }

    /* Dual resolution screen */
    #kh-dual-resolution {
      position: fixed; inset: 0; z-index: 8500;
      display: flex; flex-direction: column; align-items: center;
      background: radial-gradient(circle at 50% 30%, rgba(44,30,10,.97) 0%, rgba(8,5,2,.98) 80%);
      font-family: 'Cinzel', serif; color: #e8d4a0; overflow-y: auto; padding: 28px 16px;
    }
    .kh-dual-title { font-size: 1.7rem; font-weight: 700; color: #f0cc50; letter-spacing: 4px;
                     margin: 4px 0 2px; }
    .kh-dual-sub   { font-size: .9rem; font-style: italic; color: #c4b08c; margin: 0 0 22px; }
    .kh-dual-cols  { display: flex; gap: 26px; width: min(1080px, 96vw); align-items: flex-start;
                     justify-content: center; }
    .kh-dual-col   { flex: 1 1 0; min-width: 0; border: 1px solid rgba(196,148,40,.45);
                     border-radius: 12px; padding: 14px 16px 18px;
                     background: linear-gradient(180deg, rgba(30,18,6,.85) 0%, rgba(16,9,3,.9) 100%); }
    .kh-dual-col.kh-dual-you { border-color: rgba(240,204,80,.75); }
    .kh-dual-col-title { font-size: 1.05rem; font-weight: 700; letter-spacing: 2px;
                         color: #f0cc50; margin: 0 0 8px; text-align: center; }
    .kh-dual-resline { display: flex; justify-content: center; gap: 12px; font-size: .8rem;
                       margin-bottom: 12px; color: #d8c290; flex-wrap: wrap; }
    .kh-dual-resline b { color: #f5e2ac; font-weight: 700; }
    .kh-dual-rows { display: flex; flex-direction: column; gap: 6px; }
    .kh-dual-row  { display: flex; align-items: center; gap: 10px; padding: 7px 10px;
                    border-radius: 8px; border: 1px solid rgba(196,148,40,.25);
                    background: rgba(60,40,14,.35); font-size: .85rem; opacity: .55;
                    transition: all .25s; }
    .kh-dual-row .kh-dual-card { font-weight: 700; color: #f5e2ac; white-space: nowrap;
                                 overflow: hidden; text-overflow: ellipsis; }
    .kh-dual-row .kh-dual-delta { margin-left: auto; font-size: .8rem; color: #c4b08c;
                                  white-space: nowrap; }
    .kh-dual-row.is-firing { opacity: 1; border-color: #f0cc50;
                             background: rgba(120,84,24,.55);
                             box-shadow: 0 0 14px rgba(240,204,80,.35); transform: scale(1.02); }
    .kh-dual-row.is-done   { opacity: .9; }
    .kh-dual-row.is-none   { opacity: .5; }
    .kh-dual-row.is-sabotage { border-color: rgba(220,90,70,.8); background: rgba(96,28,18,.5); }
    .kh-dual-row.is-sabotage .kh-dual-card { color: #f0a08c; }
    .kh-dual-delta .up   { color: #9fdd8f; }
    .kh-dual-delta .down { color: #e08a7a; }
    .kh-dual-continue { margin-top: 24px; padding: 12px 42px; border-radius: 10px;
                        border: 2px solid #f0cc50; cursor: pointer;
                        background: linear-gradient(135deg, #6a4a10 0%, #3a2406 100%);
                        color: #fff6dc; font-family: 'Cinzel', serif; font-size: .95rem;
                        font-weight: 700; letter-spacing: 2px; transition: all .15s; }
    .kh-dual-continue:hover { background: linear-gradient(135deg, #8a6420 0%, #5a3a10 100%);
                              box-shadow: 0 3px 16px rgba(240,204,80,.4); }
    .kh-dual-continue[disabled] { opacity: .35; cursor: default; }
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
      <span>⚒ <b>${Math.floor(r.production)}</b></span>
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

interface DualRow {
  el: HTMLElement;
  step: ResolutionStep;
}

/** Formats a step's non-zero resource deltas as a compact signed string. */
const deltaText = (s: ResolutionStep): string => {
  const parts: string[] = [];
  const fmt = (v: number, icon: string): void => {
    if (v === 0) return;
    const cls = v > 0 ? "up" : "down";
    parts.push(`<span class="${cls}">${icon}${v > 0 ? "+" : ""}${Math.floor(v)}</span>`);
  };
  fmt(s.foodDelta, "🌾");
  fmt(s.productionDelta, "⚒");
  fmt(s.populationDelta, "👥");
  fmt(s.goldDelta, "🪙");
  fmt(s.strengthDelta, "🛡");
  if (parts.length === 0) {
    if (s.staffingTier === StaffingTier.NONE) return `<span class="down">understaffed</span>`;
    if (s.engineBoost) return `<span class="up">boosts neighbors</span>`;
    return "—";
  }
  return parts.join(" ");
};

/** Human-readable row label for a resolution step. */
const rowLabel = (s: ResolutionStep): string => {
  if (s.cardName === undefined || s.cardName === null) return "🌱 Harvest & upkeep";
  if (s.cardName === "SABOTAGE") return "⚔ SABOTAGE!";
  return getCardDisplayInfo({ name: s.cardName } as Card).name;
};

/** Builds one kingdom's column; returns the row elements for the animation. */
const buildColumn = (
  parent: HTMLElement,
  title: string,
  isYou: boolean,
  kingdom: Kingdom,
): DualRow[] => {
  const col = document.createElement("div");
  col.className = `kh-dual-col${isYou ? " kh-dual-you" : ""}`;
  const r = kingdom.resources;
  col.innerHTML = `
    <h3 class="kh-dual-col-title">${title}</h3>
    <div class="kh-dual-resline">
      <span>👥 <b>${Math.floor(r.population)}</b></span>
      <span>⚒ <b>${Math.floor(r.production)}</b></span>
      <span>🌾 <b>${Math.floor(r.food)}</b></span>
      ${r.gold > 0 ? `<span>🪙 <b>${Math.floor(r.gold)}</b></span>` : ""}
      ${r.strength > 0 ? `<span>🛡 <b>${Math.floor(r.strength)}</b></span>` : ""}
    </div>
    <div class="kh-dual-rows"></div>
  `;
  parent.appendChild(col);

  const rowsEl = col.querySelector<HTMLElement>(".kh-dual-rows")!;
  const rows: DualRow[] = [];
  for (const step of kingdom.resolutionLog) {
    const row = document.createElement("div");
    row.className = "kh-dual-row";
    if (step.cardName === "SABOTAGE") row.classList.add("is-sabotage");
    row.innerHTML = `
      <span class="kh-dual-card">${rowLabel(step)}</span>
      <span class="kh-dual-delta"></span>
    `;
    rowsEl.appendChild(row);
    rows.push({ el: row, step });
  }
  return rows;
};

/**
 * Plays the end-of-season resolution for a two-player match: both kingdoms'
 * tables fire left-to-right AT THE SAME TIME, one row per beat, driven by each
 * kingdom's server-computed resolutionLog. Resolves once the player clicks
 * Continue (enabled after the last beat).
 *
 * @param match  the post-resolution match state (resolutionLogs populated)
 * @param mySeat the viewer's seat
 */
export const playDualResolution = async (
  match: MatchState,
  mySeat: number,
): Promise<void> => {
  injectStyles();
  hideWaitingOverlay();
  document.getElementById("kh-dual-resolution")?.remove();

  const oppSeat = mySeat === 0 ? 1 : 0;
  const you = match.players[mySeat];
  const opp = match.players[oppSeat];

  const overlay = document.createElement("div");
  overlay.id = "kh-dual-resolution";
  overlay.innerHTML = `
    <h2 class="kh-dual-title">THE SEASON RESOLVES</h2>
    <p class="kh-dual-sub">Both kingdoms' works fire in order — left to right, side by side.</p>
    <div class="kh-dual-cols"></div>
    <button class="kh-dual-continue" disabled>Continue</button>
  `;
  document.body.appendChild(overlay);

  const cols = overlay.querySelector<HTMLElement>(".kh-dual-cols")!;
  const yourRows = buildColumn(cols, "👑 YOUR KINGDOM", true, you);
  const oppRows = buildColumn(cols, "⚔ RIVAL KINGDOM", false, opp);

  // Beat through both logs in lockstep.
  const beats = Math.max(yourRows.length, oppRows.length);
  for (let i = 0; i < beats; i++) {
    for (const rows of [yourRows, oppRows]) {
      const row = rows[i];
      if (!row) continue;
      row.el.classList.add("is-firing");
      row.el.querySelector<HTMLElement>(".kh-dual-delta")!.innerHTML = deltaText(row.step);
      if (row.step.staffingTier === StaffingTier.NONE) row.el.classList.add("is-none");
    }
    await delay(950);
    for (const rows of [yourRows, oppRows]) {
      const row = rows[i];
      if (!row) continue;
      row.el.classList.remove("is-firing");
      row.el.classList.add("is-done");
    }
  }

  const btn = overlay.querySelector<HTMLButtonElement>(".kh-dual-continue")!;
  btn.disabled = false;
  await new Promise<void>((resolve) => {
    btn.addEventListener("click", () => resolve(), { once: true });
  });
  overlay.remove();
};
