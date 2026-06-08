import { Application } from "pixi.js";
import { GameEventHandler, GameMessage, Phase } from "./kingshand.ts";
import { AddPhaseScene } from "./addPhaseScene.ts";
import { RandomEventScene } from "./randomEventScene.ts";
import { PlayPhaseScene } from "./playPhaseScene.ts";
import { initMusic, unlockMusicOnFirstInteraction } from "./audio.ts";

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

  let currentScene: AddPhaseScene | RandomEventScene | PlayPhaseScene | null = null;

  const mountTarget = document.getElementById("pixi-container")!;

  // ── Game-over overlay ──────────────────────────────────────────────────────
  // Shown whenever state.gameOver is true, regardless of curPhase.
  // It sits on top of everything and lets the player return to the home screen.

  const showGameOver = (): void => {
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
      <p class="kh-go-sub">Your people are gone. The land falls silent.</p>
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

    const { state } = message;

    // Game over takes priority over phase routing — the server does not advance
    // the phase when population hits zero, so we must check this first or the
    // player can end up stuck on the current scene with no way forward.
    if (state.gameOver) {
      showGameOver();
      return;
    }

    switch (state.curPhase) {
      case Phase.ADD: {
        if (currentScene instanceof AddPhaseScene) {
          // Same phase — just refresh content.
          currentScene.update(state);
        } else if (currentScene instanceof PlayPhaseScene && state.resolutionLog.length > 0) {
          // PLAY → ADD transition with resolution data: animate first, then switch.
          // Capture the pre-resolution state (still live in the scene) before anything changes.
          const preState = currentScene.getState();
          const playScene = currentScene;
          playScene.animateFromResolutionLog(state, preState).then(() => {
            playScene.unmount();
            const addScene = new AddPhaseScene(state, {
              reroll:   () => handler.reroll(),
              pickCard: (name) => handler.pickCard(name),
              skipAdd:  () => handler.skipAdd(),
            });
            currentScene = addScene;
            addScene.mount(mountTarget);
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

      case Phase.RANDOM_EVENT: {
        if (currentScene instanceof RandomEventScene) {
          // Same phase, same scene type — just refresh content.
          currentScene.update(state);
        } else {
          // Either a fresh mount or switching from ADD: tear down the old scene first.
          if (currentScene) {
            currentScene.unmount();
            currentScene = null;
          }
          currentScene = new RandomEventScene(state, {
            pickEventOption: (name) => handler.pickEventOption(name),
            skipEvent:       () => handler.skipEvent(),
          });
          currentScene.mount(mountTarget);
        }
        break;
      }

      case Phase.PLAY: {
        if (currentScene instanceof PlayPhaseScene) {
          // Already in PLAY (e.g. after a placeCard or demolishCard refresh).
          currentScene.update(state);
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
        console.log(`Phase ${state.curPhase} scene not yet implemented.`);
        break;
      }
    }
  };

  // onQuit is called when the server confirms the quit.  Tear down PixiJS and
  // return the player to the home screen.
  const onQuit = () => {
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
