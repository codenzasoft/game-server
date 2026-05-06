import { Application } from "pixi.js";
import { GameEventHandler } from "./tictactoe.ts";

const loginUrl = import.meta.env.VITE_LOGIN_URL || `${window.location.origin}/login`;

const getLastPathSegment = (url) => {
  const pathname = new URL(url).pathname;
  const parts = pathname.split("/");
  // The pop() method removes the last element and returns it.
  // Calling it a second time handles cases where the URL ends in a trailing slash.
  return parts.pop() || parts.pop();
};

const getGameId = () => {
  return getLastPathSegment(window.location.href);
};

(async () => {
  // Create a new application
  const app = new Application();

  // Initialize the application
  await app.init({ background: "#1099bb", resizeTo: window });

  // Append the application canvas to the document body
  document.getElementById("pixi-container")!.appendChild(app.canvas);

  // connect to game
  const gameId = getGameId();
  if (gameId.length === 0) {
    console.error(`Game ID not provided`);
    // redirect to login page to ensure user is authenticated
    window.location.href = loginUrl;
  } else {
    console.log(`Using game ID: ${gameId}`);
    const eventHandler = new GameEventHandler(app);
    await eventHandler.initImages();
    app.stage.addChild(eventHandler.getBoard());
    eventHandler.connect(gameId);
  }
})();
