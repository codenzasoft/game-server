export interface LobbyOverlayOptions {
  title: string;
  gameId: string;
  players: string[];
  maxPlayers: number;
  /** CSS background value for the modal panel, e.g. "rgba(10,60,20,0.96)". */
  bgColor?: string;
  onLeave: () => void;
  /** When provided a "Start Game" button is shown. */
  onStart?: () => void;
}

/**
 * Creates and mounts a lobby waiting-room overlay onto `document.body`.
 * Returns the created element so the caller can remove it later.
 */
export function renderLobbyOverlay(opts: LobbyOverlayOptions): HTMLDivElement {
  const { title, players, maxPlayers, onLeave, onStart } = opts;
  const bgColor = opts.bgColor ?? "rgba(20,20,30,0.96)";
  const inviteUrl = window.location.href;

  const slots = Array.from({ length: maxPlayers }, (_, i) =>
    i < players.length
      ? `<div style="padding:8px 0;border-bottom:1px solid rgba(255,255,255,0.12)">
           <span style="color:#7fff7f">&#x25cf;</span>&ensp;${players[i]}
         </div>`
      : `<div style="padding:8px 0;border-bottom:1px solid rgba(255,255,255,0.12);color:rgba(255,255,255,0.35)">
           <span>&#x25cb;</span>&ensp;Waiting for player&hellip;
         </div>`,
  ).join("");

  const overlay = document.createElement("div");
  overlay.style.cssText = `
    position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);
    background:${bgColor};border:1px solid rgba(255,255,255,0.18);
    border-radius:14px;padding:28px 32px;color:white;font-family:Arial,sans-serif;
    min-width:340px;box-shadow:0 8px 32px rgba(0,0,0,0.55);z-index:100;
  `;
  overlay.innerHTML = `
    <h3 style="margin:0 0 20px;font-size:20px">${title} &mdash; Waiting Room</h3>
    <p style="margin:0 0 6px;font-size:13px;color:rgba(255,255,255,0.65)">
      Share this link to invite players:
    </p>
    <div style="display:flex;gap:8px;margin-bottom:22px">
      <input class="lob-link" type="text" readonly value="${inviteUrl}"
        style="flex:1;min-width:0;background:rgba(255,255,255,0.1);
               border:1px solid rgba(255,255,255,0.3);border-radius:6px;
               padding:6px 10px;color:white;font-size:12px">
      <button class="lob-copy"
        style="background:rgba(255,255,255,0.15);border:1px solid rgba(255,255,255,0.3);
               border-radius:6px;padding:6px 14px;color:white;cursor:pointer;
               font-size:12px;white-space:nowrap">
        Copy
      </button>
    </div>
    <div style="margin-bottom:22px">${slots}</div>
    <div style="display:flex;gap:10px">
      ${
        onStart
          ? `<button class="lob-start"
               style="flex:1;background:#2ecc71;border:none;border-radius:8px;
                      padding:10px;color:white;font-size:15px;font-weight:bold;cursor:pointer">
               Start Game (${players.length}/${maxPlayers})
             </button>`
          : ""
      }
      <button class="lob-leave"
        style="background:rgba(200,50,50,0.25);border:1px solid rgba(220,80,80,0.45);
               border-radius:8px;padding:10px 16px;color:#ffaaaa;cursor:pointer;font-size:14px">
        Leave
      </button>
    </div>
  `;

  document.body.appendChild(overlay);

  const copyBtn = overlay.querySelector<HTMLButtonElement>(".lob-copy");
  copyBtn?.addEventListener("click", () => {
    void navigator.clipboard.writeText(inviteUrl);
    if (copyBtn) {
      copyBtn.textContent = "Copied!";
      window.setTimeout(() => {
        copyBtn.textContent = "Copy";
      }, 2000);
    }
  });
  overlay.querySelector<HTMLButtonElement>(".lob-start")?.addEventListener("click", () => onStart?.());
  overlay.querySelector<HTMLButtonElement>(".lob-leave")?.addEventListener("click", () => onLeave());

  return overlay;
}
