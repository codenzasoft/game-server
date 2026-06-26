/**
 * Generates a seamlessly-tileable green felt texture (256×256 PNG) using an
 * SVG feTurbulence filter rasterised by sharp.  Run from the ui/ directory:
 *   npm run build:felt
 */
import sharp from "sharp";
import { writeFileSync } from "fs";
import { fileURLToPath } from "url";
import path from "path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(__dirname, "../public/assets/felt-tile.png");

const SIZE = 256;

// Two-layer SVG: a solid base colour + a turbulence-based fabric overlay.
// stitchTiles="stitch" makes the noise tile seamlessly.
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${SIZE}" height="${SIZE}">
  <defs>
    <filter id="felt" x="0%" y="0%" width="100%" height="100%" color-interpolation-filters="linearRGB">
      <!-- Fine fibre noise — higher frequency = smaller grains -->
      <feTurbulence type="fractalNoise" baseFrequency="0.5 0.3"
                    numOctaves="5" seed="5" stitchTiles="stitch"
                    result="noise"/>
      <!-- Desaturate to greyscale -->
      <feColorMatrix type="saturate" values="0" in="noise" result="gray"/>
      <!-- High contrast but clamped so peaks stay dark (no white brightening) -->
      <feComponentTransfer in="gray" result="hiContrast">
        <feFuncR type="linear" slope="2.0" intercept="-0.6"/>
        <feFuncG type="linear" slope="2.0" intercept="-0.6"/>
        <feFuncB type="linear" slope="2.0" intercept="-0.6"/>
      </feComponentTransfer>
      <!-- multiply: only darkens — no bright white highlights -->
      <feBlend in="SourceGraphic" in2="hiContrast" mode="multiply"/>
    </filter>
  </defs>
  <rect width="${SIZE}" height="${SIZE}" fill="#164d25" filter="url(#felt)"/>
</svg>`;

const buf = await sharp(Buffer.from(svg))
  .png({ compressionLevel: 9 })
  .toBuffer();

writeFileSync(OUT, buf);
console.log(`Wrote ${OUT} (${buf.length} bytes)`);
