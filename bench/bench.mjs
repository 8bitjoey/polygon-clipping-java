// JS-side benchmark, methodology matched to the JMH run (avgt): per case, warmup
// then measured batches; report mean ± stddev of per-op time in microseconds.
//
// usage: node bench/bench.mjs
import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";
import { createRequire } from "module";

const here = dirname(fileURLToPath(import.meta.url));
const pc = createRequire(import.meta.url)(
  join(here, "..", "upstream", "dist", "polygon-clipping.cjs.js")
);

const windmillArgs = JSON.parse(
  readFileSync(
    join(here, "..", "lib", "src", "test", "resources", "end-to-end", "windmill-4-blades", "args.geojson"),
    "utf8"
  )
).features.map((f) => f.geometry.coordinates);

const gear1k = JSON.parse(readFileSync(join(here, "data", "gear-1k.json"), "utf8"));
const gear10k = JSON.parse(readFileSync(join(here, "data", "gear-10k.json"), "utf8"));

const cases = [
  ["windmillUnion", () => pc.union(windmillArgs[0], ...windmillArgs.slice(1))],
  ["windmillDifference", () => pc.difference(windmillArgs[0], ...windmillArgs.slice(1))],
  ["gear1kUnion", () => pc.union(gear1k.a, gear1k.b)],
  ["gear1kIntersection", () => pc.intersection(gear1k.a, gear1k.b)],
  ["gear10kUnion", () => pc.union(gear10k.a, gear10k.b)],
];

// JMH-equivalent settings: 3 warmup + 5 measurement intervals of ~1s each.
const WARMUP_MS = 3000;
const INTERVALS = 5;
const INTERVAL_MS = 1000;

let sink = 0; // blackhole

function runFor(fn, ms) {
  const end = performance.now() + ms;
  let ops = 0;
  while (performance.now() < end) {
    sink += fn().length;
    ops++;
  }
  return ops;
}

console.log("node", process.version);
for (const [name, fn] of cases) {
  runFor(fn, WARMUP_MS);
  const usPerOp = [];
  for (let i = 0; i < INTERVALS; i++) {
    const t0 = performance.now();
    const ops = runFor(fn, INTERVAL_MS);
    const t1 = performance.now();
    usPerOp.push(((t1 - t0) * 1000) / ops);
  }
  const mean = usPerOp.reduce((a, b) => a + b, 0) / usPerOp.length;
  const sd = Math.sqrt(
    usPerOp.reduce((a, b) => a + (b - mean) * (b - mean), 0) / (usPerOp.length - 1)
  );
  console.log(
    `${name.padEnd(20)} ${mean.toFixed(3).padStart(12)} ± ${sd.toFixed(3)} us/op`
  );
}
console.log("sink", sink);
