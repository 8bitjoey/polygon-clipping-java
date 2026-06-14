#!/usr/bin/env node
/**
 * Run one fixture through the instrumented reference and write a trace file.
 *
 * Usage: node reference/run.mjs <fixtureDir> <op> <outFile>
 *
 * Example:
 *   node reference/instrument.mjs && node reference/run.mjs windmill-4-blades difference /tmp/out.trace
 */

import { readFileSync } from "fs"
import { fileURLToPath } from "url"
import path from "path"

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const REPO = path.resolve(__dirname, "..")

const [, , fixtureDir, op, outFile] = process.argv
if (!fixtureDir || !op || !outFile) {
  console.error("Usage: node reference/run.mjs <fixtureDir> <op> <outFile>")
  process.exit(1)
}

const FIXTURE_BASE = path.join(REPO, "lib", "src", "test", "resources", "end-to-end")
const argsPath = path.join(FIXTURE_BASE, fixtureDir, "args.geojson")

const argsJson = JSON.parse(readFileSync(argsPath, "utf8"))
// Same as Java GeoJson reader: features[i].geometry.coordinates
const geometries = argsJson.features.map((f) => f.geometry.coordinates)

// Import instrumented index (default export is { union, intersection, xor, difference })
const { default: pc } = await import("./src/index.js")
const { trace, flush } = await import("./src/trace.js")

const fn = pc[op]
if (!fn) {
  console.error(`Unknown operation: ${op}. Valid: union, intersection, xor, difference`)
  process.exit(1)
}

let result
try {
  result = fn(geometries[0], ...geometries.slice(1))
  trace("RESULT " + JSON.stringify(result))
} catch (e) {
  trace("THREW " + e.message)
}

flush(outFile)
console.log(`Trace written to: ${outFile}`)
