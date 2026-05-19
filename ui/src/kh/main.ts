import { Application } from "pixi.js";
import { GameEventHandler, GameMessage, Phase } from "./kingshand.ts";
import { OptionsMenu } from "./settings.ts";
import { AddPhaseScene } from "./addPhaseScene.ts";
import { RandomEventScene } from "./randomEventScene.ts";

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

  let currentScene: AddPhaseScene | RandomEventScene | null = null;

  const mountTarget = document.getElementById("pixi-container")!;

  // onMessage is called for every GameMessage dequeued by GameEventHandler.
  // It inspects curPhase and mounts the corresponding scene overlay.
  const onMessage = (message: GameMessage) => {
    console.log("Processing KH message:", message);

    const { state } = message;

    switch (state.curPhase) {
      case Phase.ADD: {
        if (currentScene) {
          // Scene already mounted from a previous message (e.g. after a
          // reroll the phase stays ADD): just refresh its content.
          currentScene.update(state);
        } else {
          currentScene = new AddPhaseScene(state, {
            reroll:   () => handler.reroll(),
            pickCard: (i) => handler.pickCard(i),
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
            pickEventOption: (i) => handler.pickEventOption(i),
            skipEvent:       () => handler.skipEvent(),
          });
          currentScene.mount(mountTarget);
        }
        break;
      }

      default: {
        // Phase changed to something without a scene yet — tear down whatever is live.
        if (currentScene) {
          currentScene.unmount();
          currentScene = null;
        }
        // TODO: mount PLAY scene as it is built out.
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

  // Options menu — contains "Save and Exit" and "Quit Game".
  // Quit delegates to handler.quit() so the server is notified before we
  // navigate away; saveAndExit() navigates immediately since state is already
  // persisted server-side.
  const optionsMenu = new OptionsMenu(
    app,
    () => {
      optionsMenu.close();
      handler.quit();
    },
    () => {
      handler.saveAndExit();
    },
  );
  app.stage.addChild(optionsMenu);

  // Open the WebSocket connection last, after all local state is ready to
  // receive messages.
  handler.connect(gameId);
})();
