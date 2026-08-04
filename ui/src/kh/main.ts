import { Application } from "pixi.js";
import { GameEventHandler, GameMessage, Phase } from "./kingshand.ts";
import { AddPhaseScene } from "./addPhaseScene.ts";
import { CouncilScene } from "./councilScene.ts";
import { PlayPhaseScene } from "./playPhaseScene.ts";
import { initMusic, unlockMusicOnFirstInteraction } from "./audio.ts";
import {
  hideWaitingOverlay,
  playDualResolution,
  removeOpponentHud,
  showWaitingOverlay,
} from "./multiplayerOverlays.ts";

const homeUrl = `${window.location.origin}/`;
const loginUrl =
  import.meta.env.VITE_LOGIN_URL || `${window.location.origin}/login`;

const getLastPathSegment = (url: string): string | undefined => {
  const pathname = new URL(url).pathname;
  const parts = pathname.split("/");
  // The pop() method removes the last element and returns it.
  // Calling it a second time handles cases where the URL ends in a trailing slash.
  return parts.pop() || parts.pop();
};

const getGameId = (): string | undefined => {
  return getLastPathSegment(window.location.href);
};

(async () => {
  // Create a new application.  The dark green background is visible between
  // scene transitions; it doubles as the parchment world background.
  const app = new Application();
  await app.init({ background: 0x2d5a3d, resizeTo: window });

  // Append the application canvas to the document body
  document.getElementById("pixi-container")!.appendChild(app.canvas);

  // Looping background music — browsers require a user gesture before audio
  // can play, so we prep the <audio> element now and start it on the first
  // click/tap/keypress anywhere on the page. Volume is controlled from the
  // Options menu (see sharedOverlays.ts) and persisted in localStorage.
  initMusic();
  unlockMusicOnFirstInteraction();

  const gameId = getGameId();
  if (!gameId || gameId.length === 0) {
    console.error(`Game ID not provided`);
    window.location.href = loginUrl;
    return;
  }

  console.log(`Using game ID: ${gameId}`);

  // ── Active scene ──────────────────────────────────────────────────────────
  //
  // Only one scene is live at a time.  onMessage replaces it whenever the
  // server sends a state update that changes the current phase.

  let currentScene: AddPhaseScene | CouncilScene | PlayPhaseScene | null = null;

  const mountTarget = document.getElementById("pixi-container")!;

  // ── Victory overlay ──────────────────────────────────────────────────────
  // Shown whenever state.gameWon is true (all four win-condition Great Works
  // completed), regardless of curPhase. Sits on top of everything, same as
  // the game-over overlay below.

  const showGameWon = (subtitle?: string): void => {
    if (currentScene) {
      currentScene.unmount();
      currentScene = null;
    }
    if (document.getElementById("kh-game-won-overlay")) return; // already shown

    const style = document.createElement("style");
    style.textContent = `
      #kh-game-won-overlay {
        position: fixed; inset: 0; z-index: 9000;
        display: flex; flex-direction: column;
        align-items: center; justify-content: center;
        background: radial-gradient(circle at 50% 35%, rgba(60,42,10,.96) 0%, rgba(10,6,2,.97) 75%);
        font-family: 'Cinzel', serif;
      }
      .kh-win-crown   { font-size: 5rem; margin-bottom: 24px; }
      .kh-win-title   { font-size: 2.6rem; font-weight: 700; color: #f5d76e;
                         letter-spacing: 5px; text-shadow: 0 2px 18px rgba(245,215,110,.45);
                         margin: 0 0 14px; }
      .kh-win-sub     { font-size: 1.1rem; color: #e8d4a0; font-style: italic;
                         margin: 0 0 48px; letter-spacing: 1px; }
      .kh-win-btn     { padding: 14px 48px; border-radius: 10px; border: 2px solid #f5d76e;
                         background: linear-gradient(135deg, #6a4a10 0%, #3a2406 100%);
                         color: #fff6dc; font-family: 'Cinzel', serif; font-size: 1rem;
                         font-weight: 700; letter-spacing: 2px; cursor: pointer;
                         transition: all .15s; }
      .kh-win-btn:hover { background: linear-gradient(135deg, #8a6420 0%, #5a3a10 100%);
                           box-shadow: 0 3px 18px rgba(245,215,110,.45); }
    `;
    document.head.appendChild(style);

    const overlay = document.createElement("div");
    overlay.id = "kh-game-won-overlay";
    overlay.innerHTML = `
      <div class="kh-win-crown">👑</div>
      <h1 class="kh-win-title">YOUR KINGDOM HAS TRIUMPHED</h1>
      <p class="kh-win-sub">${subtitle ?? "All four Great Works stand complete. Your legacy endures."}</p>
      <button class="kh-win-btn" id="kh-win-home">Return Home</button>
    `;
    document.body.appendChild(overlay);

    document.getElementById("kh-win-home")!.addEventListener("click", () => {
      overlay.remove();
      app.destroy(true, true);
      window.location.href = homeUrl;
    });
  };

  // ── Game-over overlay ──────────────────────────────────────────────────────
  // Shown whenever state.gameOver is true, regardless of curPhase.
  // It sits on top of everything and lets the player return to the home screen.

  const showGameOver = (subtitle?: string): void => {
    if (currentScene) {
      currentScene.unmount();
      currentScene = null;
    }
    if (document.getElementById("kh-game-over-overlay")) return; // already shown

    const style = document.createElement("style");
    style.textContent = `
      #kh-game-over-overlay {
        position: fixed; inset: 0; z-index: 9000;
        display: flex; flex-direction: column;
        align-items: center; justify-content: center;
        background: rgba(8, 4, 2, 0.92);
        font-family: 'Cinzel', serif;
      }
      .kh-go-skull   { font-size: 5rem; margin-bottom: 24px; }
      .kh-go-title   { font-size: 2.6rem; font-weight: 700; color: #f0cc50;
                       letter-spacing: 5px; text-shadow: 0 2px 18px rgba(0,0,0,.8);
                       margin: 0 0 14px; }
      .kh-go-sub     { font-size: 1.1rem; color: #c4b08c; font-style: italic;
                       margin: 0 0 48px; letter-spacing: 1px; }
      .kh-go-btn     { padding: 14px 48px; border-radius: 10px; border: 2px solid #c49428;
                       background: linear-gradient(135deg, #3a2010 0%, #1e0e04 100%);
                       color: #f0e8c0; font-family: 'Cinzel', serif; font-size: 1rem;
                       font-weight: 700; letter-spacing: 2px; cursor: pointer;
                       transition: all .15s; }
      .kh-go-btn:hover { background: linear-gradient(135deg, #5a3020 0%, #2e1408 100%);
                         box-shadow: 0 3px 18px rgba(196,148,40,.35); }
    `;
    document.head.appendChild(style);

    const overlay = document.createElement("div");
    overlay.id = "kh-game-over-overlay";
    overlay.innerHTML = `
      <div class="kh-go-skull">💀</div>
      <h1 class="kh-go-title">YOUR KINGDOM HAS FALLEN</h1>
      <p class="kh-go-sub">${subtitle ?? "Your people are gone. The land falls silent."}</p>
      <button class="kh-go-btn" id="kh-go-home">Return Home</button>
    `;
    document.body.appendChild(overlay);

    document.getElementById("kh-go-home")!.addEventListener("click", () => {
      overlay.remove();
      app.destroy(true, true);
      window.location.href = homeUrl;
    });
  };

  // onMessage is called for every GameMessage dequeued by GameEventHandler.
  // It inspects curPhase and mounts the corresponding scene overlay.
  const onMessage = (message: GameMessage) => {
    console.log("Processing KH message:", message);

    const { state, match, context } = message;
    const isMultiplayer = match.players.length > 1;

    // Match outcome takes priority over phase routing — the server stops the
    // clock once the match is decided (victory, starvation, or forfeit), so we
    // must check first or the player gets stuck on the current scene. The
    // winner's seat decides which overlay each client sees.
    if (match.matchOver) {
      removeOpponentHud();
      hideWaitingOverlay();
      const opponent = isMultiplayer
        ? match.players[context.seat === 0 ? 1 : 0]
        : undefined;
      if (match.winnerSeat === context.seat) {
        showGameWon(
          opponent?.forfeited
            ? "Your rival has surrendered. The realm is yours."
            : state.gameWon
              ? "All four Great Works stand complete. Your legacy endures."
              : "Your rival's kingdom has fallen. Yours endures.",
        );
      } else {
        showGameOver(
          isMultiplayer && match.winnerSeat >= 0 && !state.gameOver
            ? "Your rival's Great Works stand complete. History remembers them, not you."
            : undefined,
        );
      }
      return;
    }

    // The rival's resources are intentionally hidden during normal play — they are revealed only
    // on the dual resolution screen. (The always-on opponent HUD has been removed.)
    removeOpponentHud();

    // Waiting overlay: shown once this seat has submitted the current phase but
    // the barrier hasn't released. Applied after scene routing below so the
    // fresh scene sits underneath; hidden as soon as a new phase arrives.
    const syncWaitingOverlay = () => {
      if (state.isWaitingOnOthers()) {
        showWaitingOverlay();
      } else {
        hideWaitingOverlay();
      }
    };
    syncWaitingOverlay();

    switch (state.time.phase) {
      case Phase.ADD: {
        if (currentScene instanceof AddPhaseScene) {
          // Same phase — just refresh content.
          currentScene.update(state);
        } else if (
          currentScene instanceof PlayPhaseScene &&
          state.resolutionLog.length > 0 &&
          !isMultiplayer
        ) {
          // Single-player PLAY → ADD: animate the in-scene resolution first, then switch. (In
          // multiplayer the resolution already played during the held resolutionPending window, so
          // this ADD message just switches scenes — handled by the else branch below.)
          const playScene = currentScene;
          playScene.animateFromResolutionLog(state, playScene.getState()).then(() => {
            playScene.unmount();
            const addScene = new AddPhaseScene(state, {
              reroll:   () => handler.reroll(),
              pickCard: (name) => handler.pickCard(name),
              skipAdd:  () => handler.skipAdd(),
            });
            currentScene = addScene;
            addScene.mount(mountTarget);
            syncWaitingOverlay();
          });
        } else {
          // Phase changed to ADD without a resolution log — tear down and switch normally.
          if (currentScene) {
            currentScene.unmount();
            currentScene = null;
          }
          currentScene = new AddPhaseScene(state, {
            reroll:   () => handler.reroll(),
            pickCard: (name) => handler.pickCard(name),
            skipAdd:  () => handler.skipAdd(),
          });
          currentScene.mount(mountTarget);
        }
        break;
      }

      case Phase.COUNCIL_ADD:
      case Phase.COUNCIL_MEETING: {
        if (currentScene instanceof CouncilScene) {
          // Same scene type — covers a same-phase refresh (e.g. after hireAdvisor)
          // and the COUNCIL_ADD → COUNCIL_MEETING transition within one council
          // turn, so the roster panel never flickers between the two steps.
          currentScene.update(state);
        } else if (
          currentScene instanceof PlayPhaseScene &&
          state.resolutionLog.length > 0 &&
          !isMultiplayer
        ) {
          // Single-player PLAY → COUNCIL (Winter ended, gold unlocked): animate the resolution
          // first, like the PLAY → ADD case. In multiplayer the resolution already played during
          // the held resolutionPending window, so this message just switches scenes (else branch).
          const playScene = currentScene;
          playScene.animateFromResolutionLog(state, playScene.getState()).then(() => {
            playScene.unmount();
            const councilScene = new CouncilScene(state, {
              hireAdvisor:           (id) => handler.hireAdvisor(id),
              skipCouncilAdd:        ()   => handler.skipCouncilAdd(),
              acceptCouncilProposal: ()   => handler.acceptCouncilProposal(),
              passCouncilProposal:   ()   => handler.passCouncilProposal(),
              dismissAdvisor:        (id) => handler.dismissAdvisor(id),
            });
            currentScene = councilScene;
            councilScene.mount(mountTarget);
            syncWaitingOverlay();
          });
        } else {
          // Either a fresh mount or switching from ADD/PLAY (no resolution log): tear down the
          // old scene first.
          if (currentScene) {
            currentScene.unmount();
            currentScene = null;
          }
          currentScene = new CouncilScene(state, {
            hireAdvisor:           (id) => handler.hireAdvisor(id),
            skipCouncilAdd:        ()   => handler.skipCouncilAdd(),
            acceptCouncilProposal: ()   => handler.acceptCouncilProposal(),
            passCouncilProposal:   ()   => handler.passCouncilProposal(),
            dismissAdvisor:        (id) => handler.dismissAdvisor(id),
          });
          currentScene.mount(mountTarget);
        }
        break;
      }

      case Phase.PLAY: {
        if (currentScene instanceof PlayPhaseScene) {
          if (isMultiplayer && match.resolutionPending) {
            // The tables have resolved but the shared clock is held on this season until every seat
            // clicks Continue. Play the dual resolution once (this seat hasn't continued yet and the
            // screen isn't already up); Continue tells the server this seat is ready.
            if (!state.isWaitingOnOthers() && !document.getElementById("kh-dual-resolution")) {
              playDualResolution(match, context.seat, () => handler.continueResolution()).then(
                () => syncWaitingOverlay(), // opponent may still be watching — show "waiting…"
              );
            }
            // else: already continued (waiting on the opponent) — leave the frozen board under the
            // overlays and don't refresh it with post-resolution data.
          } else {
            // Ordinary PLAY refresh (e.g. after a placeCard or demolishCard).
            currentScene.update(state);
          }
        } else {
          if (currentScene) {
            currentScene.unmount();
            currentScene = null;
          }
          currentScene = new PlayPhaseScene(state, {
            placeCard:    (name)        => handler.placeCard(name),
            demolishCard: (name)        => handler.demolishCard(name),
            endAndRun:    ()            => handler.endAndRun(),
            reorderTable: (names)       => handler.reorderTable(names),
          });
          currentScene.mount(mountTarget);
        }
        break;
      }

      default: {
        // Unknown phase — tear down whatever is live.
        if (currentScene) {
          currentScene.unmount();
          currentScene = null;
        }
        console.log(`Phase ${state.time.phase} scene not yet implemented.`);
        break;
      }
    }
  };

  // onQuit is called when the server confirms the quit.  Tear down PixiJS and
  // return the player to the home screen.
  const onQuit = () => {
    removeOpponentHud();
    hideWaitingOverlay();
    if (currentScene) {
      currentScene.unmount();
      currentScene = null;
    }
    app.destroy(true, true);
    window.location.href = homeUrl;
  };

  const handler = new GameEventHandler(onMessage, onQuit);

  // The quit and save-and-exit buttons live in the HTML overlay layer (sharedOverlays.ts)
  // and communicate via custom DOM events so scenes stay decoupled from the handler.
  document.addEventListener("kh-quit",      () => handler.quit());
  document.addEventListener("kh-save-exit", () => handler.saveAndExit());

  // Open the WebSocket connection last, after all local state is ready to
  // receive messages.
  handler.connect(gameId);
})();
