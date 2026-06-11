# Deviation ledger — deliberate differences from upstream

The port is otherwise verbatim (same structure, same comments, same floating-point
operation order). Every entry below is gated by the 170-case upstream fixture suite,
which passes bit-for-bit.

1. **Event linking moves out of the comparator — with an escalation ladder.** Upstream's
   `SweepEvent.compare` _mutates_ during comparison: equal-coordinate events with distinct
   point objects get linked (`sweep-event.js:12` → `link()` at `:42-53`), which merges their
   `point.events` arrays, unifies point identity, **and triggers `checkForConsuming()`** —
   i.e. duplicate-segment consumption. This is the _primary_ identity-merge mechanism (the
   rounder returns fresh point objects), and its timing depends on which node pairs the tree
   compares. A red-black tree cannot reproduce a splay tree's compare sequence, so **no
   `TreeSet` port can be timing-faithful** — the timing dependence must be engineered away,
   not imitated. Plan A: link explicitly when equal-point events pop consecutively from the
   queue (equal points are adjacent in queue order) — deterministic, structure-independent.
   Open equivalence question, named honestly: consumption then happens at pop time, possibly
   later than upstream's insert-time linking, and `segment.js:351` reads linkage state
   mid-sweep during splits — equivalence is plausible, not proven. the port traced every reader
   of `point.events` / `consumedBy` and compare reachable states. Escalation ladder if
   fixtures disagree: (1) in-comparator linking on `TreeSet` (mechanism faithful, timing still
   differs from splay), (2) port the `splaytree` package itself — bit-faithful compare
   sequence, contained ~300-line swap. The fixture suite arbitrates each rung.
   **Analysis verdict: CONDITIONAL — Plan A approved with conditions C1-C5** (pop-link
   fires `checkForConsuming`; per-pop incremental linking; later-consumption window accepted
   and overlap-fixture-gated; stable sort in geom-out; deterministic merge order). Analysis
   also showed upstream's own link timing is tree-shape-dependent (not deterministic even
   across splay implementations) and that Plan A's linkage is strictly more complete — so if
   fixtures reject Plan A, skip rung 1 and go straight to rung 2.
2. **`ringOut` linkage via map, not field — SUPERSEDED during implementation.** Original idea: keep a
   `HashMap<Segment, RingOut>` in the output pass to kill the sweep→output package cycle.
   Execution kept upstream's field (`Segment.ringOut`, typed `RingOut`) for fidelity and
   because upstream unit tests read/write it directly; the package cycle is legal in Java
   and contained. Recorded here so the ledger matches the shipped code.
3. **Recursion scope corrected by analysis.** `prevInResult` walks the prev-chain
   recursively (`segment.js:449`) with O(chain length) depth — converted to a loop, memo
   intact. `enclosingRing` turned out to already be a `while(true)` loop internally
   (`geom-out.js:151`); its self-calls at `:163`/`:165` recurse only to O(hole-nesting
   depth) through the memo cache — ported as-is with the memo, no conversion needed.
4. **Per-op segment ids.** Upstream's counter is process-global and never resets; only relative
   order matters within one op, so per-op counters are behavior-neutral and make ops fully
   independent.
5. **Status-tree removal hoisted before self-split (commit 989a4b4 of the development history).** Upstream splits the
   currently-processed segment while it is tree-resident and removes it afterwards
   (`sweep-line.js:115`/`:125`). On the splay tree this is safe by a structural accident: the
   `add` at `:35` splays the segment to the ROOT, so the later remove finds it without key
   navigation. A red-black `TreeSet` navigates by the (post-split, mutated) sort key, can miss
   the node, and leaves a stale ghost resident; the retry re-add then inserts a second node.
   Ghost predecessors corrupted `prev` wiring and `isInResult`, causing all 18 phase-5 e2e
   failures. The port removes the segment from the tree immediately BEFORE `split()` in the
   self-split branch (the same remove→mutate→reinsert discipline `_splitSafely` uses); the
   upstream-positioned remove stays for the neighbor-split-only path. Proven by differential
   trace (first divergence `PREV seg=19` on windmill-4-blades/difference) and by the suite
   going 170/170.
