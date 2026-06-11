// Deterministic benchmark-input generator. Two overlapping "gear" polygons
// (r(θ) = R + amp·sin(teeth·θ)) whose wavy boundaries cross many times — a
// classic sweep-line stress shape. No randomness: same output on every run.
//
// usage: node bench/gen.mjs
import { writeFileSync, mkdirSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const here = dirname(fileURLToPath(import.meta.url));

function gear(n, cx, cy, R, amp, teeth, phase) {
  const ring = [];
  for (let i = 0; i < n; i++) {
    const t = (2 * Math.PI * i) / n;
    const r = R + amp * Math.sin(teeth * t + phase);
    ring.push([cx + r * Math.cos(t), cy + r * Math.sin(t)]);
  }
  ring.push([ring[0][0], ring[0][1]]);
  return [ring]; // single-ring Polygon coordinates
}

function dataset(n) {
  return {
    vertices: n,
    a: gear(n, 0, 0, 100, 30, 24, 0),
    b: gear(n, 80, 0, 100, 30, 24, 1.3),
  };
}

mkdirSync(join(here, "data"), { recursive: true });
for (const n of [1000, 10000]) {
  const file = join(here, "data", `gear-${n / 1000}k.json`);
  writeFileSync(file, JSON.stringify(dataset(n)));
  console.log("wrote", file);
}
