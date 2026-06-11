package com.bytejoey.polygonclip.num;

import com.bytejoey.polygonclip.sweep.SweepPoint;

/**
 * Port of rounder.js (mfogel/polygon-clipping).
 *
 * <p>JS uses a module-level singleton ({@code const rounder = new PtRounder()}) reset at the
 * start and end of each {@code Operation.run()} call. In this Java port, {@code Rounder} is a
 * per-operation object — it should be instantiated at the start of {@code Operation.run()} and
 * discarded at the end, which is equivalent to the two {@code rounder.reset()} calls in the JS
 * upstream. This avoids any shared mutable state between operations.
 *
 * <p>Two independent {@link CoordRounder} instances handle X and Y axes separately, each backed
 * by a {@link DoubleRbTree} preseeded with {@code 0.0} at construction time. Snapping is
 * performed by checking the lower neighbour before the higher neighbour (load-bearing order: when both
 * neighbours are within epsilon, the lower key wins).
 */
public final class Rounder {

    private final CoordRounder xRounder;
    private final CoordRounder yRounder;

    public Rounder() {
        this.xRounder = new CoordRounder();
        this.yRounder = new CoordRounder();
    }

    /**
     * Round a raw (x, y) coordinate pair and return a fresh {@link SweepPoint}.
     * Each axis is rounded independently against all previously seen values on that axis.
     */
    public SweepPoint round(double x, double y) {
        return new SweepPoint(xRounder.round(x), yRounder.round(y));
    }

    // -------------------------------------------------------------------------

    /**
     * Port of {@code CoordRounder} (rounder.js:35-66).
     *
     * <p>Uses a {@link DoubleRbTree} as a sorted set of canonical coordinate values — no boxing,
     * no dead value column (unlike the prior {@code TreeMap<Double,Double>} where key and value
     * were always identical).
     *
     * <p>Ordering: primitive {@code <} / {@code >} (JS default comparator semantics, matching the
     * upstream splay tree). This means {@code -0.0} and {@code 0.0} are treated as the same key —
     * which is faithful to the JS upstream. The old {@code TreeMap} used {@link Double#compare},
     * which (incorrectly for this port) treated them as distinct keys.
     *
     * <p>Preseed: {@code round(0.0)} is called in the constructor so that any coordinate in
     * the open interval {@code (-epsilon, epsilon)} immediately snaps to {@code 0.0} on first
     * appearance, matching the SplayTree preseed in the JS upstream.
     */
    private static final class CoordRounder {

        private final DoubleRbTree tree = new DoubleRbTree();

        CoordRounder() {
            round(0.0);
        }

        /**
         * Insert {@code coord} into the tree and snap it to a nearby canonical value if one
         * exists within floating-point epsilon (mirrors rounder.js:49-65). Implemented as a
         * single tree descent in {@link DoubleRbTree#snapRound}: insertion point, predecessor
         * and successor found in one walk; the predecessor is checked before the successor —
         * load-bearing order, the lower key wins when both neighbours are within epsilon.
         */
        double round(double coord) {
            // single descent: insertion point + predecessor + successor in one walk,
            // snap checks (lower first — load-bearing) inside DoubleRbTree.snapRound
            return tree.snapRound(coord);
        }
    }
}
