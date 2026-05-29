// ─── Shared overlay components ────────────────────────────────────────────────
//
// Handbook and Deck indicator are rendered identically in every scene (ADD,
// RANDOM_EVENT, PLAY).  This module owns their HTML builders, listener wiring,
// and CSS injection so scenes never duplicate the logic.

import { GameState, Season } from "./kingshand.ts";
import { getCardDisplayInfo, formatModifier } from "./cardCatalog.ts";
import { HANDBOOK } from "./handbook.ts";
import { Card, ResourceTarget } from "./kingshand.ts";

// ── Shared style injection ────────────────────────────────────────────────────

export function injectSharedStyles(): void {
  if (document.getElementById("kh-shared-styles")) return;

  const style = document.createElement("style");
  style.id = "kh-shared-styles";
  style.textContent = `
    /* ── Shared pile / handbook icon button ──────────────────────────── */
    .kh-shared-icon-btn {
      position: fixed;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 5px;
      background: linear-gradient(160deg, #2a1a08 0%, #1a100a 100%);
      border: 2px solid #c49428;
      border-radius: 16px;
      padding: 14px 20px 12px;
      cursor: pointer;
      z-index: 50;
      box-shadow: 2px 4px 18px rgba(0,0,0,.5);
      transition: all .18s ease;
      min-width: 84px;
      text-align: center;
    }
    .kh-shared-icon-btn:hover {
      background: linear-gradient(160deg, #3e2810 0%, #2a180c 100%);
      box-shadow: 3px 6px 24px rgba(0,0,0,.6);
      transform: translateY(-2px);
      border-color: #e0b040;
    }
    .kh-shared-icon-btn .kh-sib-emoji  { font-size: 1.9rem; line-height: 1; display: block; }
    .kh-shared-icon-btn .kh-sib-label  {
      font-family: 'Cinzel', serif;
      font-size: .65rem;
      letter-spacing: 1px;
      color: #c4a060;
      text-transform: uppercase;
      display: block;
    }
    .kh-shared-icon-btn .kh-sib-badge {
      background: #c49428;
      color: #1a0e04;
      font-family: 'Cinzel', serif;
      font-size: .8rem;
      font-weight: 700;
      border-radius: 10px;
      padding: 2px 9px;
      min-width: 24px;
      text-align: center;
      margin-top: 2px;
      display: block;
    }

    /* Positions */
    .kh-pos-deck     { top: 190px; right: 20px; }
    .kh-pos-handbook { top: 190px; left: 20px; }
    .kh-pos-options  { top: 14px;  right: 20px; }

    /* ── Options button ──────────────────────────────────────────────── */
    .kh-options-btn {
      position: fixed;
      top: 14px;
      right: 20px;
      display: flex;
      align-items: center;
      gap: 7px;
      background: linear-gradient(160deg, #1e1208 0%, #130c08 100%);
      border: 2px solid #7a5a28;
      border-radius: 10px;
      padding: 7px 14px;
      cursor: pointer;
      z-index: 60;
      box-shadow: 0 2px 12px rgba(0,0,0,.55);
      transition: all .18s ease;
      font-family: 'Cinzel', serif;
      font-size: .72rem;
      letter-spacing: 1px;
      color: #b09060;
      text-transform: uppercase;
    }
    .kh-options-btn:hover {
      border-color: #c49428;
      color: #f0cc50;
      box-shadow: 0 4px 18px rgba(0,0,0,.65);
      transform: translateY(-1px);
    }
    .kh-options-btn-icon { font-size: 1rem; line-height: 1; }

    /* ── Options modal ───────────────────────────────────────────────── */
    .kh-opt-modal {
      background: linear-gradient(170deg, #1a1008 0%, #0e0904 100%);
      border: 2px solid #c49428;
      border-radius: 18px;
      width: min(360px, 90vw);
      box-shadow: 0 20px 70px rgba(0,0,0,.8);
      overflow: hidden;
    }
    .kh-opt-modal-header {
      background: linear-gradient(90deg, #2a1608 0%, #1a0e06 100%);
      border-bottom: 2px solid #c49428;
      padding: 16px 22px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .kh-opt-modal-title {
      font-family: 'Cinzel', serif;
      font-size: 1rem;
      font-weight: 700;
      color: #f0cc50;
      letter-spacing: 2px;
    }
    .kh-opt-modal-body {
      padding: 22px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .kh-opt-action-btn {
      width: 100%;
      padding: 14px 20px;
      border-radius: 12px;
      border: 2px solid;
      font-family: 'Cinzel', serif;
      font-size: .9rem;
      font-weight: 700;
      letter-spacing: 1px;
      cursor: pointer;
      transition: all .18s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
    }
    .kh-opt-save-btn {
      background: linear-gradient(160deg, #1e2e1a 0%, #141e10 100%);
      border-color: #5a8a40;
      color: #90d070;
    }
    .kh-opt-save-btn:hover {
      background: linear-gradient(160deg, #2a4022 0%, #1c2e16 100%);
      border-color: #78b058;
      color: #b0f090;
      transform: translateY(-1px);
      box-shadow: 0 4px 16px rgba(80,160,40,.25);
    }
    .kh-opt-quit-btn {
      background: linear-gradient(160deg, #2e1414 0%, #1e0c0c 100%);
      border-color: #8a3030;
      color: #d07070;
    }
    .kh-opt-quit-btn:hover {
      background: linear-gradient(160deg, #401a1a 0%, #2a1010 100%);
      border-color: #b04040;
      color: #f09090;
      transform: translateY(-1px);
      box-shadow: 0 4px 16px rgba(160,40,40,.25);
    }
    .kh-opt-quit-warn {
      font-family: 'Crimson Text', Georgia, serif;
      font-size: .82rem;
      color: #7a5a5a;
      text-align: center;
      font-style: italic;
      padding: 0 4px;
    }

    /* ── Shared modal backdrop ───────────────────────────────────────── */
    .kh-shared-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,.65);
      z-index: 200;
      display: flex;
      align-items: center;
      justify-content: center;
      backdrop-filter: blur(2px);
    }
    .kh-shared-modal {
      background: linear-gradient(160deg, #2a1a08 0%, #1a100a 100%);
      border: 2px solid #c49428;
      border-radius: 18px;
      width: min(700px, 94vw);
      max-height: 82vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 16px 60px rgba(0,0,0,.7);
      overflow: hidden;
    }
    .kh-shared-modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 22px;
      border-bottom: 2px solid #c49428;
      background: linear-gradient(90deg, #3a2010 0%, #2a1608 100%);
      flex-shrink: 0;
    }
    .kh-shared-modal-title {
      font-family: 'Cinzel', serif;
      font-size: 1rem;
      font-weight: 700;
      color: #f0cc50;
      letter-spacing: 2px;
    }
    .kh-shared-modal-close {
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
    .kh-shared-modal-close:hover {
      background: rgba(255,255,255,.08);
      color: #f0cc50;
      border-color: #c49428;
    }
    .kh-shared-modal-body {
      overflow-y: auto;
      padding: 10px 0;
      flex: 1;
    }
    .kh-shared-modal-body::-webkit-scrollbar       { width: 5px; }
    .kh-shared-modal-body::-webkit-scrollbar-track { background: #1a100a; }
    .kh-shared-modal-body::-webkit-scrollbar-thumb { background: #c49428; border-radius: 4px; }

    /* ── Deck modal specifics ─────────────────────────────────────────── */
    .kh-deck-tabs {
      display: flex;
      border-bottom: 2px solid rgba(196,148,40,.35);
      flex-shrink: 0;
      background: #1a100a;
      overflow-x: auto;
    }
    .kh-deck-tab {
      flex: 1;
      padding: 10px 12px;
      background: none;
      border: none;
      border-bottom: 3px solid transparent;
      color: #a09070;
      font-family: 'Cinzel', serif;
      font-size: .65rem;
      letter-spacing: .8px;
      cursor: pointer;
      white-space: nowrap;
      transition: all .15s;
    }
    .kh-deck-tab:hover { color: #c4a060; background: rgba(255,255,255,.04); }
    .kh-deck-tab.kh-deck-tab-active { color: #f0cc50; border-bottom-color: #c49428; background: rgba(196,148,40,.08); }
    .kh-deck-tab-count { background: rgba(196,148,40,.25); border-radius: 8px; padding: 1px 5px; margin-left: 4px; color: #c4a060; }
    .kh-deck-body { overflow-y: auto; padding: 14px 18px; flex: 1; }
    .kh-deck-body::-webkit-scrollbar       { width: 5px; }
    .kh-deck-body::-webkit-scrollbar-track { background: #1a100a; }
    .kh-deck-body::-webkit-scrollbar-thumb { background: #c49428; border-radius: 4px; }
    .kh-deck-empty {
      text-align: center;
      font-style: italic;
      color: #7a6a52;
      padding: 30px 0;
      font-size: .95rem;
    }
    .kh-deck-sub-heading {
      font-family: 'Cinzel', serif;
      font-size: .65rem;
      letter-spacing: 1.5px;
      color: #c49428;
      text-transform: uppercase;
      margin: 12px 0 8px;
      padding-bottom: 5px;
      border-bottom: 1px solid rgba(196,148,40,.25);
    }
    .kh-deck-sub-heading:first-child { margin-top: 0; }

    /* ── Compact card (deck / pile viewer) ───────────────────────────── */
    .kh-compact-card {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 14px;
      background: rgba(255,255,255,.04);
      border: 1px solid rgba(196,148,40,.3);
      border-radius: 10px;
      padding: 10px 14px;
      margin-bottom: 8px;
      transition: background .15s;
    }
    .kh-compact-card:hover { background: rgba(255,255,255,.07); }
    .kh-cc-left  { display: flex; align-items: flex-start; gap: 10px; flex: 1; min-width: 0; }
    .kh-cc-icon  { font-size: 1.5rem; line-height: 1; flex-shrink: 0; filter: drop-shadow(0 1px 4px rgba(0,0,0,.5)); }
    .kh-cc-name  { font-family: 'Cinzel', serif; font-size: .82rem; font-weight: 700; color: #f0e8c0; margin-bottom: 3px; }
    .kh-cc-desc  { font-style: italic; font-size: .8rem; color: #8a7a60; line-height: 1.35; }
    .kh-cc-right { display: flex; flex-direction: column; align-items: flex-end; gap: 5px; flex-shrink: 0; }
    .kh-cc-freq  { font-family: 'Cinzel', serif; font-size: .55rem; font-weight: 700; letter-spacing: .8px; padding: 2px 7px; border-radius: 8px; }
    .kh-cc-recurring { background: rgba(100,180,100,.22); color: #90d090; }
    .kh-cc-single    { background: rgba(200,120,80,.25);  color: #e0b090; }
    .kh-cc-effects   { display: flex; flex-wrap: wrap; gap: 5px; justify-content: flex-end; }
    .kh-cc-effect    { font-family: 'Cinzel', serif; font-size: .65rem; color: #a0c080; background: rgba(100,180,80,.12); border-radius: 6px; padding: 2px 7px; white-space: nowrap; }
    .kh-cc-costs     { font-family: 'Cinzel', serif; font-size: .6rem; color: #7a6a52; }

    /* ── Handbook modal specifics ─────────────────────────────────────── */
    .kh-hb-chapter { border-bottom: 1px solid rgba(196,148,40,.2); }
    .kh-hb-ch-toggle {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 22px;
      background: none;
      border: none;
      cursor: pointer;
      text-align: left;
      transition: background .15s;
    }
    .kh-hb-ch-toggle:hover { background: rgba(255,255,255,.04); }
    .kh-hb-chapter-open > .kh-hb-ch-toggle { background: rgba(196,148,40,.07); }
    .kh-hb-ch-icon  { font-size: 1.2rem; flex-shrink: 0; }
    .kh-hb-ch-title {
      flex: 1;
      font-family: 'Cinzel', serif;
      font-size: .88rem;
      font-weight: 700;
      color: #f0e8c0;
      letter-spacing: 1px;
    }
    .kh-hb-ch-arrow { font-size: .7rem; color: #c4a060; flex-shrink: 0; }
    .kh-hb-ch-body  { padding: 0 22px 10px; }
    .kh-hb-section  { padding: 14px 0; border-top: 1px dashed rgba(196,148,40,.2); }
    .kh-hb-section:first-child { border-top: none; }
    .kh-hb-sec-title {
      font-family: 'Cinzel', serif;
      font-size: .75rem;
      font-weight: 700;
      color: #c49428;
      letter-spacing: 1px;
      margin-bottom: 8px;
      text-transform: uppercase;
    }
    .kh-hb-sec-body {
      font-family: 'Crimson Text', Georgia, serif;
      font-size: .97rem;
      color: #c8b89a;
      line-height: 1.65;
    }
    .kh-hb-sec-body b  { color: #f0e8c0; }
    .kh-hb-sec-body em { color: #d4c090; font-style: italic; }

    /* ── Floating resource indicators ────────────────────────────────── */
    @keyframes kh-float-rise {
      0%   { opacity: 1;   transform: translateX(-50%) translateY(0); }
      15%  { opacity: 1; }
      100% { opacity: 0;   transform: translateX(-50%) translateY(-64px); }
    }
    .kh-float-indicator {
      position: fixed;
      font-family: 'Cinzel', serif;
      font-size: .95rem;
      font-weight: 700;
      pointer-events: none;
      z-index: 9999;
      white-space: nowrap;
      letter-spacing: .5px;
      animation: kh-float-rise 2s ease-out forwards;
      text-shadow: 0 2px 8px rgba(0,0,0,.85);
    }
    .kh-float-pos { color: #6ae66a; }
    .kh-float-neg { color: #f07070; }
  `;
  document.head.appendChild(style);
}

// ── Floating resource indicator ───────────────────────────────────────────────

/**
 * Spawns a "+N 🌾" / "−N ⚒️" label that floats upward from near {@code anchor}
 * and fades out, giving the player immediate visual feedback when a resource
 * changes.
 *
 * @param anchor      The resource-value DOM element to anchor the indicator to.
 * @param delta       The change amount (positive = green, negative = red).
 * @param icon        Emoji shown after the number (e.g. "🌾", "⚒️", "👥").
 * @param staggerIndex Pass 0, 1, 2 … when spawning multiple indicators at once
 *                    so they cascade rather than pile on top of each other.
 */
export function spawnResourceIndicator(
  anchor: HTMLElement,
  delta: number,
  icon: string,
  staggerIndex = 0,
): void {
  if (delta === 0) return;

  const rect    = anchor.getBoundingClientRect();
  const sign    = delta > 0 ? "+" : "−";
  const absVal  = Math.abs(Math.floor(delta));

  const el = document.createElement("div");
  el.className = `kh-float-indicator ${delta > 0 ? "kh-float-pos" : "kh-float-neg"}`;
  el.textContent = `${sign}${absVal} ${icon}`;

  // Stagger: each successive indicator starts slightly lower and fires later,
  // so multiple effects from a single card cascade upward instead of stacking.
  const startY  = rect.top - 8 + staggerIndex * 18;
  const startX  = rect.left + rect.width / 2;
  const delay   = staggerIndex * 180;

  el.style.cssText = `
    left: ${startX}px;
    top: ${startY}px;
    animation-delay: ${delay}ms;
    opacity: 0;
  `;
  // Make visible only after the delay kicks in by using animationstart
  el.addEventListener("animationstart", () => { el.style.opacity = ""; }, { once: true });

  document.body.appendChild(el);
  setTimeout(() => el.remove(), 2100 + delay);
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function totalCards(pile: Map<Season, Card[]>): number {
  let count = 0;
  for (const cards of pile.values()) count += cards.length;
  return count;
}

function buildCompactCard(card: Card): string {
  const info = getCardDisplayInfo(card);
  const effectsHTML = card.effects
    .map((e) => {
      const emoji = e.target === ResourceTarget.FOOD       ? "🌾"
                  : e.target === ResourceTarget.PRODUCTION  ? "⚒️"
                  : e.target === ResourceTarget.POPULATION  ? "👥"
                  : "🃏";
      return `<span class="kh-cc-effect">${emoji} ${formatModifier(e.modifier, e.valueType)}${card.frequencyType === "RECURRING" ? "/s" : " once"}</span>`;
    })
    .join("");

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
        <span class="kh-cc-freq ${card.frequencyType === "RECURRING" ? "kh-cc-recurring" : "kh-cc-single"}">${card.frequencyType}</span>
        <div class="kh-cc-effects">${effectsHTML}</div>
        <div class="kh-cc-costs">⚒️ ${card.productionRequirement} &nbsp; 👥 ${card.staffRequirement}</div>
      </div>
    </div>
  `;
}

// ── Deck indicator ────────────────────────────────────────────────────────────

export function buildDeckIndicator(state: GameState): string {
  const count = totalCards(state.drawPile) + totalCards(state.discardPile) + state.hand.length;
  return `
    <button class="kh-shared-icon-btn kh-pos-deck" id="kh-deck-open" title="Your full card collection">
      <span class="kh-sib-emoji">📖</span>
      <span class="kh-sib-label">Deck</span>
      <span class="kh-sib-badge">${count}</span>
    </button>
    <div class="kh-shared-backdrop" id="kh-deck-backdrop" style="display:none;">
      <div class="kh-shared-modal" style="max-height:82vh;">
        <div class="kh-shared-modal-header">
          <span class="kh-shared-modal-title">📖 &nbsp;Your Deck</span>
          <button class="kh-shared-modal-close" id="kh-deck-close">✕</button>
        </div>
        <div class="kh-deck-tabs" id="kh-deck-tabs"></div>
        <div class="kh-deck-body" id="kh-deck-body"></div>
      </div>
    </div>
  `;
}

export function attachDeckListeners(overlay: HTMLElement, state: GameState): void {
  const ALL_SEASONS: Season[] = [Season.SPRING, Season.SUMMER, Season.FALL, Season.WINTER];
  const SEASON_ICONS: Record<Season, string> = {
    [Season.SPRING]: "🌿", [Season.SUMMER]: "☀️",
    [Season.FALL]:   "🍂", [Season.WINTER]: "❄️",
  };

  const openBtn  = overlay.querySelector<HTMLButtonElement>("#kh-deck-open");
  const closeBtn = overlay.querySelector<HTMLButtonElement>("#kh-deck-close");
  const backdrop = overlay.querySelector<HTMLDivElement>("#kh-deck-backdrop");
  const tabsEl   = overlay.querySelector<HTMLDivElement>("#kh-deck-tabs");
  const bodyEl   = overlay.querySelector<HTMLDivElement>("#kh-deck-body");

  if (!backdrop || !tabsEl || !bodyEl) return;

  const renderTab = (season: Season) => {
    const tableCards   = state.board.get(season) ?? [];
    const drawCards    = state.drawPile.get(season) ?? [];
    const discardCards = state.discardPile.get(season) ?? [];
    const handCards    = season === state.curSeason ? state.hand : [];
    const allCards     = [...tableCards, ...handCards, ...drawCards, ...discardCards];

    tabsEl.querySelectorAll<HTMLButtonElement>(".kh-deck-tab").forEach((btn) => {
      btn.classList.toggle("kh-deck-tab-active", btn.dataset.season === season);
    });

    if (allCards.length === 0) {
      bodyEl.innerHTML = `<div class="kh-deck-empty">No cards in your ${season.toLowerCase()} collection.</div>`;
    } else {
      bodyEl.innerHTML = `
        ${tableCards.length > 0 ? `
          <div class="kh-deck-sub-heading">Table (${tableCards.length})</div>
          ${tableCards.map(buildCompactCard).join("")}
        ` : ""}
        ${handCards.length > 0 ? `
          <div class="kh-deck-sub-heading">In Hand (${handCards.length})</div>
          ${handCards.map(buildCompactCard).join("")}
        ` : ""}
        ${drawCards.length > 0 ? `
          <div class="kh-deck-sub-heading">Draw (${drawCards.length})</div>
          ${drawCards.map(buildCompactCard).join("")}
        ` : ""}
        ${discardCards.length > 0 ? `
          <div class="kh-deck-sub-heading">Discard (${discardCards.length})</div>
          ${discardCards.map(buildCompactCard).join("")}
        ` : ""}
      `;
    }
  };

  // Build tabs
  tabsEl.innerHTML = ALL_SEASONS.map((s) => {
    const count = (state.board.get(s)?.length ?? 0)
                + (state.drawPile.get(s)?.length ?? 0)
                + (state.discardPile.get(s)?.length ?? 0)
                + (s === state.curSeason ? state.hand.length : 0);
    return `
      <button class="kh-deck-tab ${s === state.curSeason ? "kh-deck-tab-active" : ""}" data-season="${s}">
        ${SEASON_ICONS[s]} ${s} <span class="kh-deck-tab-count">${count}</span>
      </button>
    `;
  }).join("");

  tabsEl.querySelectorAll<HTMLButtonElement>(".kh-deck-tab").forEach((btn) => {
    btn.addEventListener("click", () => renderTab(btn.dataset.season as Season));
  });

  renderTab(state.curSeason);

  openBtn?.addEventListener("click",  () => { backdrop.style.display = "flex"; });
  closeBtn?.addEventListener("click", () => { backdrop.style.display = "none"; });
  backdrop.addEventListener("click",  (e) => { if (e.target === backdrop) backdrop.style.display = "none"; });
}

// ── Handbook ──────────────────────────────────────────────────────────────────

export function buildHandbookButton(): string {
  return `
    <button class="kh-shared-icon-btn kh-pos-handbook" id="kh-hb-open" title="Player Handbook">
      <span class="kh-sib-emoji">📜</span>
      <span class="kh-sib-label">Handbook</span>
    </button>
    <div class="kh-shared-backdrop" id="kh-hb-backdrop" style="display:none;">
      <div class="kh-shared-modal">
        <div class="kh-shared-modal-header">
          <span class="kh-shared-modal-title">📜 &nbsp;Player Handbook</span>
          <button class="kh-shared-modal-close" id="kh-hb-close">✕</button>
        </div>
        <div class="kh-shared-modal-body">
          ${HANDBOOK.map((ch, ci) => `
            <div class="kh-hb-chapter ${ci === 0 ? "kh-hb-chapter-open" : ""}">
              <button class="kh-hb-ch-toggle" data-chapter="${ci}">
                <span class="kh-hb-ch-icon">${ch.icon}</span>
                <span class="kh-hb-ch-title">${ch.title}</span>
                <span class="kh-hb-ch-arrow">${ci === 0 ? "▲" : "▼"}</span>
              </button>
              <div class="kh-hb-ch-body" style="${ci === 0 ? "" : "display:none"}">
                ${ch.sections.map((sec) => `
                  <div class="kh-hb-section">
                    <div class="kh-hb-sec-title">${sec.title}</div>
                    <div class="kh-hb-sec-body">${sec.body}</div>
                  </div>
                `).join("")}
              </div>
            </div>
          `).join("")}
        </div>
      </div>
    </div>
  `;
}

// ── Options menu (Quit / Save and Exit) ───────────────────────────────────────
//
// Fires two custom DOM events so scenes stay decoupled from the handler:
//   document.dispatchEvent(new Event('kh-quit'))
//   document.dispatchEvent(new Event('kh-save-exit'))
// main.ts listens for these and calls handler.quit() / handler.saveAndExit().

export function buildOptionsButton(): string {
  return `
    <button class="kh-options-btn" id="kh-opt-open" title="Options">
      <span class="kh-options-btn-icon">⚙</span>
      Menu
    </button>
    <div class="kh-shared-backdrop" id="kh-opt-backdrop" style="display:none;">
      <div class="kh-opt-modal">
        <div class="kh-opt-modal-header">
          <span class="kh-opt-modal-title">⚙ &nbsp;Options</span>
          <button class="kh-shared-modal-close" id="kh-opt-close">✕</button>
        </div>
        <div class="kh-opt-modal-body">
          <button class="kh-opt-action-btn kh-opt-save-btn" id="kh-opt-save-exit">
            🏠 &nbsp;Save &amp; Exit
          </button>
          <button class="kh-opt-action-btn kh-opt-quit-btn" id="kh-opt-quit">
            ☠ &nbsp;Quit Game
          </button>
          <p class="kh-opt-quit-warn">
            Quitting permanently ends this run. Save &amp; Exit keeps your progress.
          </p>
        </div>
      </div>
    </div>
  `;
}

export function attachOptionsListeners(overlay: HTMLElement): void {
  const openBtn  = overlay.querySelector<HTMLButtonElement>("#kh-opt-open");
  const closeBtn = overlay.querySelector<HTMLButtonElement>("#kh-opt-close");
  const backdrop = overlay.querySelector<HTMLDivElement>("#kh-opt-backdrop");
  const saveBtn  = overlay.querySelector<HTMLButtonElement>("#kh-opt-save-exit");
  const quitBtn  = overlay.querySelector<HTMLButtonElement>("#kh-opt-quit");

  openBtn?.addEventListener("click",  () => { if (backdrop) backdrop.style.display = "flex"; });
  closeBtn?.addEventListener("click", () => { if (backdrop) backdrop.style.display = "none"; });
  backdrop?.addEventListener("click", (e) => { if (e.target === backdrop) backdrop.style.display = "none"; });

  saveBtn?.addEventListener("click", () => {
    document.dispatchEvent(new Event("kh-save-exit"));
  });

  quitBtn?.addEventListener("click", () => {
    if (backdrop) backdrop.style.display = "none";
    document.dispatchEvent(new Event("kh-quit"));
  });
}

export function attachHandbookListeners(overlay: HTMLElement): void {
  const openBtn  = overlay.querySelector<HTMLButtonElement>("#kh-hb-open");
  const closeBtn = overlay.querySelector<HTMLButtonElement>("#kh-hb-close");
  const backdrop = overlay.querySelector<HTMLDivElement>("#kh-hb-backdrop");

  openBtn?.addEventListener("click",  () => { if (backdrop) backdrop.style.display = "flex"; });
  closeBtn?.addEventListener("click", () => { if (backdrop) backdrop.style.display = "none"; });
  backdrop?.addEventListener("click", (e) => { if (e.target === backdrop) backdrop.style.display = "none"; });

  overlay.querySelectorAll<HTMLButtonElement>(".kh-hb-ch-toggle").forEach((btn) => {
    btn.addEventListener("click", () => {
      const chapter = btn.closest<HTMLDivElement>(".kh-hb-chapter");
      if (!chapter) return;
      const body  = chapter.querySelector<HTMLDivElement>(".kh-hb-ch-body");
      const arrow = btn.querySelector<HTMLSpanElement>(".kh-hb-ch-arrow");
      const isOpen = chapter.classList.contains("kh-hb-chapter-open");
      chapter.classList.toggle("kh-hb-chapter-open", !isOpen);
      if (body)  body.style.display = isOpen ? "none" : "block";
      if (arrow) arrow.textContent  = isOpen ? "▼" : "▲";
    });
  });
}
