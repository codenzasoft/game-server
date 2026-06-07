import { Application } from "pixi.js";
import { HeartsGameHandler } from "./hearts.ts";

const loginUrl =
  import.meta.env.VITE_LOGIN_URL || `${window.location.origin}/login`;

const getGameId = (): string => {
  const parts = new URL(window.location.href).pathname.split("/");
  return parts.pop() || parts.pop() || "";
};

(async () => {
  const app = new Application();
  await app.init({
    background: "#1a6b1a",
    resizeTo: window,
    antialias: true,
    resolution: window.devicePixelRatio || 1,
    autoDensity: true,
  });
  document.getElementById("pixi-container")!.appendChild(app.canvas);

  const gameId = getGameId();
  if (!gameId) {
    console.error("Game ID not provided");
    window.location.href = loginUrl;
    return;
  }

  const handler = new HeartsGameHandler(app);
  await handler.preloadTextures();
  handler.connect(gameId);
})();
