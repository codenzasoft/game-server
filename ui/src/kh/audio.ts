// ─── Background music manager ─────────────────────────────────────────────────
//
// A tiny singleton that loops a background soundtrack and exposes a 0–100
// volume control.  The chosen volume is persisted in localStorage so it's
// remembered across sessions/reloads.
//
// Browsers block audio autoplay until the user interacts with the page, so
// `init()` just prepares the <audio> element and `unlockOnFirstInteraction()`
// kicks off playback the moment the player clicks/taps/presses a key anywhere
// on the page.

const STORAGE_KEY = "kh-music-volume";
const MUSIC_SRC = "/audio/theme.mp3";

const DEFAULT_VOLUME = 35; // 0–100, a comfortable background level

let audio: HTMLAudioElement | null = null;
let unlocked = false;

function readStoredVolume(): number {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw === null) return DEFAULT_VOLUME;
    const parsed = Number(raw);
    if (Number.isNaN(parsed)) return DEFAULT_VOLUME;
    return Math.min(100, Math.max(0, parsed));
  } catch {
    // localStorage may be unavailable (e.g. privacy mode) — fall back quietly.
    return DEFAULT_VOLUME;
  }
}

function writeStoredVolume(volume: number): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, String(volume));
  } catch {
    // Ignore — persistence is a nice-to-have, not a requirement.
  }
}

/** Creates (once) the looping <audio> element and applies the saved volume. */
export function initMusic(): void {
  if (audio) return;

  audio = new Audio(MUSIC_SRC);
  audio.loop = true;
  audio.preload = "auto";
  audio.volume = readStoredVolume() / 100;

  // Surface load failures in the console rather than failing silently —
  // helpful while the soundtrack file is being added to public/audio/.
  audio.addEventListener("error", () => {
    console.warn(`Kingshand: failed to load background music from "${MUSIC_SRC}".`);
  });
}

/**
 * Starts playback the first time the player interacts with the page.
 * Safe to call multiple times — only the first interaction does anything.
 */
export function unlockMusicOnFirstInteraction(): void {
  if (unlocked) return;

  const tryStart = () => {
    if (unlocked || !audio) return;
    unlocked = true;
    cleanup();

    audio.play().catch((err) => {
      // Autoplay can still be rejected in some contexts; allow another
      // interaction to retry.
      console.warn("Kingshand: could not start background music yet.", err);
      unlocked = false;
      attach();
    });
  };

  const attach = () => {
    document.addEventListener("pointerdown", tryStart, { once: true });
    document.addEventListener("keydown", tryStart, { once: true });
  };

  const cleanup = () => {
    document.removeEventListener("pointerdown", tryStart);
    document.removeEventListener("keydown", tryStart);
  };

  attach();
}

/** Returns the current volume as an integer 0–100. */
export function getMusicVolume(): number {
  return Math.round((audio?.volume ?? readStoredVolume() / 100) * 100);
}

/** Sets the volume (0–100), applies it immediately, and persists it. */
export function setMusicVolume(volume: number): void {
  const clamped = Math.min(100, Math.max(0, Math.round(volume)));
  if (audio) audio.volume = clamped / 100;
  writeStoredVolume(clamped);
}
