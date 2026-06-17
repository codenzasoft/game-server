let audioCtx: AudioContext | null = null;

function getCtx(): AudioContext | null {
  if (!audioCtx) {
    try {
      audioCtx = new AudioContext();
    } catch {
      return null;
    }
  }
  if (audioCtx.state === "suspended") void audioCtx.resume();
  return audioCtx;
}

function playNoise(
  filterType: BiquadFilterType,
  frequency: number,
  decay: number,
  duration: number,
  gain: number,
): void {
  const ctx = getCtx();
  if (!ctx) return;
  const sampleCount = Math.floor(ctx.sampleRate * duration);
  const buffer = ctx.createBuffer(1, sampleCount, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < sampleCount; i++) {
    data[i] = (Math.random() * 2 - 1) * Math.exp((-i / sampleCount) * decay);
  }
  const source = ctx.createBufferSource();
  source.buffer = buffer;
  const filter = ctx.createBiquadFilter();
  filter.type = filterType;
  filter.frequency.value = frequency;
  const gainNode = ctx.createGain();
  gainNode.gain.value = gain;
  source.connect(filter);
  filter.connect(gainNode);
  gainNode.connect(ctx.destination);
  source.start();
}

// Short crisp flick — used when each card leaves the deck
export function playDealSound(): void {
  playNoise("highpass", 2500, 35, 0.06, 0.35);
}

// Softer thud — used when a played card lands in the center
export function playCardPlaySound(): void {
  playNoise("bandpass", 1000, 22, 0.09, 0.45);
}

let slideCardBuffer: AudioBuffer | null = null;
let swooshBuffer: AudioBuffer | null = null;

async function playWav(
  cache: { buf: AudioBuffer | null },
  url: string,
): Promise<void> {
  const ctx = getCtx();
  if (!ctx) return;
  if (!cache.buf) {
    const resp = await fetch(url);
    cache.buf = await ctx.decodeAudioData(await resp.arrayBuffer());
  }
  const src = ctx.createBufferSource();
  src.buffer = cache.buf;
  src.connect(ctx.destination);
  src.start();
}

const slideCache = { buf: slideCardBuffer };
const swooshCache = { buf: swooshBuffer };

export function playSlideCardSound(): void {
  void playWav(slideCache, "/audio/hearts/slide-card.wav");
}

export function playPassSound(): void {
  void playWav(swooshCache, "/audio/hearts/swoosh.wav");
}

const applauseCache = { buf: null as AudioBuffer | null };

export function playApplauseSound(): void {
  void playWav(applauseCache, "/audio/hearts/lightapplause.mp3");
}
