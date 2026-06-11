# Oracle — JS Ground-Truth Trace Harness

Instrumented copy of `upstream/src/` that emits the same trace schema as the Java port.

## Usage

```sh
# 1. Rebuild oracle/src/ from upstream/src/ (re-run whenever upstream changes)
node oracle/instrument.mjs

# 2. Run a fixture and write a trace file
node oracle/run.mjs <fixtureDir> <op> <outFile>

# Example
node oracle/instrument.mjs && node oracle/run.mjs windmill-4-blades difference /tmp/out.trace
```

`<op>` is one of: `union`, `intersection`, `xor`, `difference`

Fixture directories live under `lib/src/test/resources/end-to-end/`.

## How it works

`instrument.mjs` wipes `oracle/src/`, copies all `.js` files from `upstream/src/`,
then applies exact-string patches:

- Rewrites relative imports to add `.js` extensions (ESM requirement)
- Rewrites `splaytree` and `robust-predicates` bare imports to resolve from `upstream/node_modules/`
- Inserts `trace(...)` calls at key algorithm points (POP, LINK, CONSUME, SPLIT, SWAP, PREV, INRESULT, RING, RINGCUT, RINGDONE)

The script exits non-zero if any anchor string is missing (staleness alarm).

## Trace schema

```
POP seg=<id> side=<L|R> x=<hex> y=<hex>
LINK keep=<segId>:<L|R> from=<segId>:<L|R>
CONSUME consumer=<id> consumee=<id>
SPLIT seg=<id> x=<hex> y=<hex> new=<newSegId>
SWAP seg=<id>
PREV seg=<id> prev=<id|null>
INRESULT seg=<id> val=<t|f>
RING start=<segId>
RINGCUT idx=<n>
RINGDONE size=<nEvents>
RESULT <JSON.stringify of result>
THREW <error message>
```

Coordinates are IEEE-754 bit hex (lowercase, no leading zeros) — matches Java's `Long.toHexString`.
