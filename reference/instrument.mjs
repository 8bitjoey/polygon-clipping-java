#!/usr/bin/env node
/**
 * Rebuilds reference/src/ from upstream/src/ with trace instrumentation.
 * Run: node reference/instrument.mjs
 * Exits non-zero if any anchor is missing (staleness alarm).
 */

import { readFileSync, writeFileSync, rmSync, mkdirSync, readdirSync } from "fs"
import { fileURLToPath } from "url"
import path from "path"

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const REPO = path.resolve(__dirname, "..")
const SRC_DIR = path.join(REPO, "upstream", "src")
const OUT_DIR = path.join(REPO, "reference", "src")

// ─── helpers ────────────────────────────────────────────────────────────────

function requireAnchor(file, content, anchor) {
  if (!content.includes(anchor)) {
    console.error(`MISSING ANCHOR in ${file}:\n  ${JSON.stringify(anchor)}`)
    process.exit(1)
  }
}

// Fix relative imports: from "./foo" → from "./foo.js"  (bare names only)
function fixRelativeImports(content) {
  return content.replace(/from "(\.\/[a-z-]+)"/g, 'from "$1.js"')
}

// Fix splaytree import to use createRequire resolved from upstream/
const SPLAYTREE_ESM = `import SplayTree from "splaytree"`
const SPLAYTREE_CJS = `import { createRequire } from "module"
const _req = createRequire(new URL("../../upstream/package.json", import.meta.url))
const SplayTree = _req("splaytree")`

// Fix robust-predicates (ESM package): rewrite to direct file path import
const ROBUST_ESM = `import { orient2d } from "robust-predicates"`
const ROBUST_FIXED = `import { orient2d } from "../../upstream/node_modules/robust-predicates/index.js"`

// ─── wipe & recreate reference/src ─────────────────────────────────────────────

rmSync(OUT_DIR, { recursive: true, force: true })
mkdirSync(OUT_DIR, { recursive: true })

// Write trace.js
const TRACE_SRC = `import { writeFileSync } from "fs"
const dv = new DataView(new ArrayBuffer(8))
export const bits = (v) => {
  dv.setFloat64(0, v)
  return dv.getBigUint64(0).toString(16)
}
let lines = []
export const trace = (s) => {
  lines.push(s)
}
export const flush = (file) => {
  writeFileSync(file, lines.join("\\n") + "\\n")
  lines = []
}
`
writeFileSync(path.join(OUT_DIR, "trace.js"), TRACE_SRC)

// ─── copy & patch each source file ──────────────────────────────────────────

const jsFiles = readdirSync(SRC_DIR).filter((f) => f.endsWith(".js"))
const stats = {}

for (const filename of jsFiles) {
  const srcPath = path.join(SRC_DIR, filename)
  let content = readFileSync(srcPath, "utf8")
  let insertions = 0

  // Step A: fix relative imports
  content = fixRelativeImports(content)

  // Step B: fix third-party imports
  if (content.includes(SPLAYTREE_ESM)) {
    content = content.replace(SPLAYTREE_ESM, SPLAYTREE_CJS)
    insertions++
  }
  if (content.includes(ROBUST_ESM)) {
    content = content.replace(ROBUST_ESM, ROBUST_FIXED)
    insertions++
  }

  // ── per-file patches ──────────────────────────────────────────────────────

  if (filename === "operation.js") {
    // Add trace + bits import at top (after createRequire line which was just inserted)
    const IMPORT_ANCHOR = `import { createRequire } from "module"`
    requireAnchor(filename, content, IMPORT_ANCHOR)
    content = content.replace(
      IMPORT_ANCHOR,
      `import { createRequire } from "module"\nimport { trace, bits } from "./trace.js"`
    )
    insertions++

    // POP: insert after `    const evt = node.key`
    const POP_ANCHOR = `    const evt = node.key`
    requireAnchor(filename, content, POP_ANCHOR)
    const POP_TRACE = `    trace("POP seg=" + evt.segment.id + " side=" + (evt.isLeft ? "L" : "R") + " x=" + bits(evt.point.x) + " y=" + bits(evt.point.y))`
    content = content.replace(
      POP_ANCHOR,
      POP_ANCHOR + "\n" + POP_TRACE
    )
    insertions++
  }

  if (filename === "sweep-event.js") {
    // Add trace import at top (before first import)
    const FIRST_IMPORT = `import Segment from "./segment.js"`
    requireAnchor(filename, content, FIRST_IMPORT)
    content = content.replace(
      FIRST_IMPORT,
      `import { trace } from "./trace.js"\nimport Segment from "./segment.js"`
    )
    insertions++

    // LINK: insert after the guard that throws "Tried to link already linked events"
    const LINK_ANCHOR = `    if (other.point === this.point) {\n      throw new Error("Tried to link already linked events")\n    }`
    requireAnchor(filename, content, LINK_ANCHOR)
    const LINK_TRACE = `    trace("LINK keep=" + this.segment.id + ":" + (this.isLeft ? "L" : "R") + " from=" + other.segment.id + ":" + (other.isLeft ? "L" : "R"))`
    content = content.replace(
      LINK_ANCHOR,
      LINK_ANCHOR + "\n" + LINK_TRACE
    )
    insertions++
  }

  if (filename === "segment.js") {
    // Add trace import at top
    const FIRST_IMPORT = `import operation from "./operation.js"`
    requireAnchor(filename, content, FIRST_IMPORT)
    content = content.replace(
      FIRST_IMPORT,
      `import { trace, bits } from "./trace.js"\nimport operation from "./operation.js"`
    )
    insertions++

    // CONSUME: insert after the closing `}` of the second swap block
    const CONSUME_ANCHOR = `    if (consumer.prev === consumee) {\n      const tmp = consumer\n      consumer = consumee\n      consumee = tmp\n    }`
    requireAnchor(filename, content, CONSUME_ANCHOR)
    const CONSUME_TRACE = `    trace("CONSUME consumer=" + consumer.id + " consumee=" + consumee.id)`
    content = content.replace(
      CONSUME_ANCHOR,
      CONSUME_ANCHOR + "\n" + CONSUME_TRACE
    )
    insertions++

    // SPLIT: insert after the closing `)` of `new Segment(...)` call
    const SPLIT_ANCHOR = `      this.windings.slice(),\n    )`
    requireAnchor(filename, content, SPLIT_ANCHOR)
    const SPLIT_TRACE = `    trace("SPLIT seg=" + this.id + " x=" + bits(point.x) + " y=" + bits(point.y) + " new=" + newSeg.id)`
    content = content.replace(
      SPLIT_ANCHOR,
      SPLIT_ANCHOR + "\n" + SPLIT_TRACE
    )
    insertions++

    // SWAP: insert as first line of swapEvents() body
    const SWAP_ANCHOR = `  swapEvents() {\n    const tmpEvt = this.rightSE`
    requireAnchor(filename, content, SWAP_ANCHOR)
    const SWAP_TRACE = `    trace("SWAP seg=" + this.id)`
    content = content.replace(
      SWAP_ANCHOR,
      `  swapEvents() {\n` + SWAP_TRACE + `\n    const tmpEvt = this.rightSE`
    )
    insertions++

    // INRESULT: insert BEFORE the final `return this._isInResult` (end of method, after switch)
    const INRESULT_ANCHOR = `\n    return this._isInResult\n  }\n}`
    requireAnchor(filename, content, INRESULT_ANCHOR)
    const INRESULT_TRACE = `    trace("INRESULT seg=" + this.id + " val=" + (this._isInResult ? "t" : "f"))`
    content = content.replace(
      INRESULT_ANCHOR,
      `\n` + INRESULT_TRACE + `\n    return this._isInResult\n  }\n}`
    )
    insertions++
  }

  if (filename === "sweep-line.js") {
    // Add trace import at top (after createRequire line which was just inserted)
    const FIRST_IMPORT = `import { createRequire } from "module"`
    requireAnchor(filename, content, FIRST_IMPORT)
    content = content.replace(
      FIRST_IMPORT,
      `import { createRequire } from "module"\nimport { trace } from "./trace.js"`
    )
    insertions++

    // PREV: insert after `segment.prev = prevSeg`
    const PREV_ANCHOR = `        segment.prev = prevSeg`
    requireAnchor(filename, content, PREV_ANCHOR)
    const PREV_TRACE = `        trace("PREV seg=" + segment.id + " prev=" + (prevSeg === null || prevSeg === undefined ? "null" : prevSeg.id))`
    content = content.replace(
      PREV_ANCHOR,
      PREV_ANCHOR + "\n" + PREV_TRACE
    )
    insertions++
  }

  if (filename === "geom-out.js") {
    // Add trace import at top
    const FIRST_IMPORT = `import { compareVectorAngles } from "./vector.js"`
    requireAnchor(filename, content, FIRST_IMPORT)
    content = content.replace(
      FIRST_IMPORT,
      `import { trace } from "./trace.js"\nimport { compareVectorAngles } from "./vector.js"`
    )
    insertions++

    // RING start: after the skip-guard line
    const RING_START_ANCHOR = `      if (!segment.isInResult() || segment.ringOut) continue`
    requireAnchor(filename, content, RING_START_ANCHOR)
    const RING_START_TRACE = `      trace("RING start=" + segment.id)`
    content = content.replace(
      RING_START_ANCHOR,
      RING_START_ANCHOR + "\n" + RING_START_TRACE
    )
    insertions++

    // RINGCUT: after `const intersectionLE = intersectionLEs.splice(indexLE)[0]`
    const RINGCUT_ANCHOR = `            const intersectionLE = intersectionLEs.splice(indexLE)[0]`
    requireAnchor(filename, content, RINGCUT_ANCHOR)
    const RINGCUT_TRACE = `            trace("RINGCUT idx=" + intersectionLE.index)`
    content = content.replace(
      RINGCUT_ANCHOR,
      RINGCUT_ANCHOR + "\n" + RINGCUT_TRACE
    )
    insertions++

    // RINGDONE (loop-cut ring): after `ringsOut.push(new RingOut(ringEvents.reverse()))`
    const RINGDONE1_ANCHOR = `            ringsOut.push(new RingOut(ringEvents.reverse()))`
    requireAnchor(filename, content, RINGDONE1_ANCHOR)
    const RINGDONE1_TRACE = `            trace("RINGDONE size=" + ringEvents.length)`
    content = content.replace(
      RINGDONE1_ANCHOR,
      RINGDONE1_ANCHOR + "\n" + RINGDONE1_TRACE
    )
    insertions++

    // RINGDONE (outer ring): after outer `ringsOut.push(new RingOut(events))`
    const RINGDONE2_ANCHOR = `      ringsOut.push(new RingOut(events))`
    requireAnchor(filename, content, RINGDONE2_ANCHOR)
    const RINGDONE2_TRACE = `      trace("RINGDONE size=" + events.length)`
    content = content.replace(
      RINGDONE2_ANCHOR,
      RINGDONE2_ANCHOR + "\n" + RINGDONE2_TRACE
    )
    insertions++
  }

  const outPath = path.join(OUT_DIR, filename)
  writeFileSync(outPath, content)
  stats[filename] = insertions
}

// ─── summary ─────────────────────────────────────────────────────────────────

console.log("\nPatch summary:")
let total = 0
for (const [file, count] of Object.entries(stats).sort()) {
  console.log(`  ${file}: ${count} insertion(s)`)
  total += count
}
console.log(`  trace.js: written (reference-side)`)
console.log(`  TOTAL: ${total} insertions across ${jsFiles.length} files`)
console.log("\nDone. reference/src/ is ready.")
