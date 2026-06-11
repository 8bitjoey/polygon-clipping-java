# Benchmark results — Java port vs upstream JS

Date: 2026-06-11. Same machine, same process isolation level, back-to-back runs.

- Java: JMH 1.37, avgt mode, 1 fork, 3×1s warmup + 5×1s measurement, JDK 26 (compiled
  for release 17). Run: `lib/` → `java -cp <test-classpath> org.openjdk.jmh.Main "com.bytejoey.polygonclip.bench.*" -tu us`
- JS: Node v22.22.2 running the upstream rollup artifact
  (`upstream/dist/polygon-clipping.cjs.js`), hand-rolled harness matched to the JMH
  settings (3s warmup, 5×1s measured intervals, result-length blackhole). Run:
  `node bench/gen.mjs && node bench/bench.mjs`

Workloads: `windmill-4-blades` fixture (tiny: 4 small polygons, the e2e suite's
multi-loop stressor) and deterministic synthetic "gear" pairs (two overlapping
wavy-boundary polygons; many boundary crossings; `bench/gen.mjs`).

| Case               | Java (µs/op)    | Java after perf. opt. (µs/op) | Java after perf. opt. round 2 (µs/op) | JS (µs/op)      | Java speedup (round 2) |
| ------------------ | --------------- | ----------------------------- | ------------------------------------- | --------------- | ---------------------- |
| windmillUnion      | 10.52 ± 0.46    | 8.57 ± 0.09                   | 7.77 ± 0.10                           | 28.78 ± 0.32    | **3.7×**               |
| windmillDifference | 10.35 ± 0.20    | 8.72 ± 1.35                   | 7.51 ± 0.07                           | 28.87 ± 0.41    | **3.8×**               |
| gear1kUnion        | 2003.8 ± 263.6  | 1657.3 ± 154.7                | 1079.9 ± 52.8                         | 3064.2 ± 77.3   | **2.8×**               |
| gear1kIntersection | 2029.7 ± 390.9  | 1487.5 ± 82.2                 | 1066.2 ± 62.3                         | 3098.5 ± 124.1  | **2.9×**               |
| gear10kUnion       | 24194.5 ± 339.5 | 18766.7 ± 2894.6              | 14873.6 ± 467.8                       | 39100.8 ± 913.4 | **2.6×**               |

Cumulative gain vs the unoptimized port: −26…−27% on micro inputs, −39…−47% on the
gear workloads. Suite stayed 170/170 bit-identical at every step of both rounds.

## Optimization steps (one commit each; cumulative)

| Step | Change                                                                                                         | Main effect                                                                       |
| ---- | -------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| 1    | Zero-allocation comparator/intersection paths (inline vector/bbox math, primitive cores)                       | alloc/op −27% (gear1k 3.64→2.62 MB, gear10k 33.5→24.5 MB); time ≈ flat            |
| 2    | Parent-pointer red-black status tree (`RbTree`) — node prev/next, zero comparator calls                        | −4…−10% time; restores upstream's node-handle navigation                          |
| 3    | Primitive `DoubleRbTree` for coordinate snapping (no `Double` boxing, no value column)                         | −4…−14% time; also fixes -0.0/0.0 key semantics to match upstream's JS comparator |
| 4    | Event queue on `RbTree` — comparator-free `pop()` via cached leftmost                                          | −3…−6% time on intersection-heavy cases                                           |
| R2-1 | Single-descent rounder snap (`DoubleRbTree.snapRound`) — one walk instead of three; snap hits never insert     | rounder was ~17% of post-round-1 profile samples                                  |
| R2-2 | Intrusive node handles (`segment.statusNode`, `event.queueNode`) — find/remove descents become pointer surgery | with R2-1: −9…−14% micro, −21…−35% gear vs round 1                                |

## Reading

- The Java port is faster on every measured case: ~3.7–3.8× on micro inputs,
  ~2.6–2.9× on intersection-heavy kilo/deka-vertex inputs.
- Step 2 closed the one structural regression vs upstream (comparator-driven
  `TreeSet.lower/higher` neighbor walks vs the splay tree's pointer neighbors);
  round 2's node handles finished the job by eliminating the find/remove descents
  entirely — the remaining comparator volume is the genuinely irreducible part
  (insert descents, whose queue-side compare is the load-bearing linking mechanism).
- Allocation reduction (step 1) barely moved wall time — TLAB allocation is cheap
  and GC time was already <0.2% of run time; the wins were always in tree-descent
  and comparator-call volume (steps 2–4, R2-1, R2-2).
- Scaling is super-linear in vertex count for both implementations (10k costs ~12×
  the 1k case at 10× the vertices) — consistent with the O((n+k)·log n) bound with
  k (intersections) growing alongside n in the gear shape.

## Caveats

- Single machine, single JVM/Node version, 1 JMH fork — fine for a porting
  sanity-comparison, not a rigorous cross-runtime study.
- The JS harness is hand-rolled (no JMH-grade dead-code/JIT-profile management);
  blackhole = accumulating result array length.
- µs-level numbers for windmill include fixture-independent per-op overhead
  (Operation/Rounder allocation in Java, equivalents in JS) — representative of real
  per-call cost, not of pure algorithm kernels.
