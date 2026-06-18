/**
 * Generates public/assets/cards.png + public/assets/cards.json from the
 * individual card SVGs in public/assets/cards/ and public/assets/backs/.
 *
 * Run from the ui/ directory:  node scripts/build-card-atlas.mjs
 *
 * Each card is rasterized at 2× BASE_CARD_W/H (160×224) so the atlas is
 * @2x resolution. The JSON meta sets scale:"0.5" so Pixi reports each
 * frame as 80×112 logical pixels, matching BASE_CARD_W/H in hearts.ts.
 */

import sharp from "sharp";
import { writeFile } from "fs/promises";

const CARD_W = 160; // 2 × BASE_CARD_W
const CARD_H = 224; // 2 × BASE_CARD_H
const COLS = 9;

const suits = ["CLUBS", "DIAMONDS", "SPADES", "HEARTS"];
const ranks = [
  "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN",
  "EIGHT", "NINE", "TEN", "JACK", "QUEEN", "KING", "ACE",
];
const rankNum = {
  TWO: 2, THREE: 3, FOUR: 4, FIVE: 5, SIX: 6, SEVEN: 7,
  EIGHT: 8, NINE: 9, TEN: 10, JACK: 11, QUEEN: 12, KING: 13, ACE: 1,
};
const rankSuffix = { JACK: "-JACK", QUEEN: "-QUEEN", KING: "-KING" };

// 52 cards in suit/rank order, then the card back
const frames = [];
for (const suit of suits) {
  for (const rank of ranks) {
    frames.push({
      key: `${suit}-${rank}`,
      src: `public/assets/cards/${suit}-${rankNum[rank]}${rankSuffix[rank] ?? ""}.svg`,
    });
  }
}
frames.push({ key: "back", src: "public/assets/backs/blue.svg" });

const ROWS = Math.ceil(frames.length / COLS);
const atlasW = COLS * CARD_W;
const atlasH = ROWS * CARD_H;

console.log(`Rasterizing ${frames.length} frames into ${atlasW}×${atlasH} atlas…`);

const composites = [];
const jsonFrames = {};

for (let i = 0; i < frames.length; i++) {
  const { key, src } = frames[i];
  const col = i % COLS;
  const row = Math.floor(i / COLS);
  const x = col * CARD_W;
  const y = row * CARD_H;

  const buf = await sharp(src).resize(CARD_W, CARD_H).png().toBuffer();
  composites.push({ input: buf, left: x, top: y });

  jsonFrames[key] = {
    frame: { x, y, w: CARD_W, h: CARD_H },
    sourceSize: { w: CARD_W, h: CARD_H },
    spriteSourceSize: { x: 0, y: 0, w: CARD_W, h: CARD_H },
  };

  if ((i + 1) % 10 === 0 || i + 1 === frames.length) {
    console.log(`  ${i + 1}/${frames.length}`);
  }
}

await sharp({
  create: { width: atlasW, height: atlasH, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
})
  .composite(composites)
  .png({ compressionLevel: 9 })
  .toFile("public/assets/cards.png");

await writeFile(
  "public/assets/cards.json",
  JSON.stringify(
    {
      frames: jsonFrames,
      meta: { image: "cards.png", size: { w: atlasW, h: atlasH }, scale: "0.5" },
    },
    null,
    2,
  ),
);

console.log(`Done — cards.png (${atlasW}×${atlasH}), cards.json (${frames.length} frames)`);
