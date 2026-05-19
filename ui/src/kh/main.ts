import { Application } from "pixi.js";
import { GameEventHandler, GameMessage } from "./kingshand.ts";
import { OptionsMenu } from "./settings.ts";

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
  // Create a new application
  const app = new Application();

  // Initialize the application
  await app.init({ background: "red", resizeTo: window });

  // Append the application canvas to the document body
  document.getElementById("pixi-container")!.appendChild(app.canvas);

  const gameId = getGameId();
  if (!gameId || gameId.length === 0) {
    console.error(`Game ID not provided`);
    window.location.href = loginUrl;
    return;
  }

  console.log(`Using game ID: ${gameId}`);

  // onMessage is called for every GameMessage dequeued by GameEventHandler.
  // Scene rendering will be wired in here as the display layer is built out.
  const onMessage = (message: GameMessage) => {
    console.log("Processing KH message:", message);
    // TODO: update scene from message.state
  };

  // onQuit is called when the server confirms the quit.  Tear down PixiJS and
  // return the player to the home screen.
  const onQuit = () => {
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
