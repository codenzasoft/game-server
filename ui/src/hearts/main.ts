import { Application } from "pixi.js";
import { HeartsGameHandler } from "./hearts.ts";

const loginUrl =
  import.meta.env.VITE_LOGIN_URL || `${window.location.origin}/login`;

const getGameId = (): string => {
  const parts = new URL(window.location.href).pathname.split("/");
  return parts.pop() || parts.pop() || "";
};

function createLoadingOverlay(): {
  update: (loaded: number, total: number) => void;
  remove: () => void;
} {
  const overlay = document.createElement("div");
  overlay.style.cssText = [
    "position:fixed", "inset:0", "display:flex", "flex-direction:column",
    "align-items:center", "justify-content:center", "z-index:100",
    "background:#1a6b1a", "font-family:Arial,sans-serif",
    "color:rgba(255,255,255,0.75)",
  ].join(";");

  const text = document.createElement("div");
  text.style.cssText = "margin-bottom:10px;font-size:13px;letter-spacing:0.06em";
  text.textContent = "Loading…";

  const track = document.createElement("div");
  track.style.cssText = [
    "width:180px", "height:3px", "background:rgba(255,255,255,0.2)",
    "border-radius:2px", "overflow:hidden",
  ].join(";");

  const bar = document.createElement("div");
  bar.style.cssText = [
    "height:100%", "width:0%", "background:rgba(255,255,255,0.65)",
    "border-radius:2px", "transition:width 0.1s ease",
  ].join(";");

  track.appendChild(bar);
  overlay.appendChild(text);
  overlay.appendChild(track);
  document.body.appendChild(overlay);

  return {
    update(loaded: number, total: number) {
      bar.style.width = `${Math.round((loaded / total) * 100)}%`;
    },
    remove() {
      overlay.remove();
    },
  };
}

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

  const loading = createLoadingOverlay();
  const handler = new HeartsGameHandler(app);
  await handler.preloadTextures((loaded, total) => loading.update(loaded, total));
  loading.remove();
  handler.connect(gameId);
})();
