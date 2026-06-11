package com.bytejoey.polygonclip.num;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential fuzzing of {@link DoubleRbTree} against {@link TreeMap} oracle.
 *
 * <p>Key generator: {@code random.nextInt(500) * 0.25} — 500 distinct positive keys plus 0.0,
 * collision-friendly, never produces -0.0. With this key universe the primitive-ordered tree and
 * the {@code Double.compare}-ordered {@link TreeMap} agree on every query.
 *
 * <p>Three seeds × 20 000 ops each. Operation mix: 50% insert, 30% remove, 20% query
 * (lowerOrNaN + higherOrNaN + contains).
 *
 * <p>Targeted -0.0 / 0.0 identity test pins our semantics directly without an oracle:
 * insert(0.0) then insert(-0.0) must return {@code false} (already present), and contains(-0.0)
 * must be {@code true}.
 */
@DisplayName("DoubleRbTree")
class DoubleRbTreeTest {

    // ------------------------------------------------------------------
    // Oracle helpers
    // ------------------------------------------------------------------

    /** Translate TreeMap.lowerKey null → NaN for comparison with lowerOrNaN. */
    private static double oracleLower(TreeMap<Double, Double> map, double key) {
        Double v = map.lowerKey(key);
        return v == null ? Double.NaN : v;
    }

    /** Translate TreeMap.higherKey null → NaN for comparison with higherOrNaN. */
    private static double oracleHigher(TreeMap<Double, Double> map, double key) {
        Double v = map.higherKey(key);
        return v == null ? Double.NaN : v;
    }

    private static double nanEqual(double a, double b) {
        // helper: asserts NaN==NaN, otherwise exact bits
        if (Double.isNaN(a) && Double.isNaN(b)) return a; // ok — return a for call chain
        assertEquals(a, b);
        return a;
    }

    // ------------------------------------------------------------------
    // Fuzz harness
    // ------------------------------------------------------------------

    private void runFuzz(long seed) {
        Random rng = new Random(seed);
        DoubleRbTree tree = new DoubleRbTree();
        TreeMap<Double, Double> oracle = new TreeMap<>();

        for (int i = 0; i < 20_000; i++) {
            double key = rng.nextInt(500) * 0.25; // keys in {0.0, 0.25, 0.5, … 124.75}
            int op = rng.nextInt(10);

            if (op < 5) {
                // 50% insert
                boolean treeResult = tree.insert(key);
                boolean oracleResult = !oracle.containsKey(key);
                oracle.put(key, key);
                assertEquals(oracleResult, treeResult,
                    "insert(" + key + ") seed=" + seed + " op=" + i);
            } else if (op < 8) {
                // 30% remove
                tree.remove(key);
                oracle.remove(key);
            } else {
                // 20% query
                assertEquals(oracle.containsKey(key), tree.contains(key),
                    "contains(" + key + ") seed=" + seed + " op=" + i);

                double expLower = oracleLower(oracle, key);
                double actLower = tree.lowerOrNaN(key);
                if (Double.isNaN(expLower)) {
                    assertTrue(Double.isNaN(actLower),
                        "lowerOrNaN(" + key + ") should be NaN but was " + actLower);
                } else {
                    assertEquals(expLower, actLower,
                        "lowerOrNaN(" + key + ") seed=" + seed + " op=" + i);
                }

                double expHigher = oracleHigher(oracle, key);
                double actHigher = tree.higherOrNaN(key);
                if (Double.isNaN(expHigher)) {
                    assertTrue(Double.isNaN(actHigher),
                        "higherOrNaN(" + key + ") should be NaN but was " + actHigher);
                } else {
                    assertEquals(expHigher, actHigher,
                        "higherOrNaN(" + key + ") seed=" + seed + " op=" + i);
                }
            }

            // size must always agree
            assertEquals(oracle.size(), tree.size(),
                "size mismatch seed=" + seed + " op=" + i);
        }
    }

    @Test
    @DisplayName("fuzz seed 7 — 20 000 ops against TreeMap oracle")
    void fuzzSeed7() {
        runFuzz(7L);
    }

    @Test
    @DisplayName("fuzz seed 99 — 20 000 ops against TreeMap oracle")
    void fuzzSeed99() {
        runFuzz(99L);
    }

    @Test
    @DisplayName("fuzz seed 123456 — 20 000 ops against TreeMap oracle")
    void fuzzSeed123456() {
        runFuzz(123_456L);
    }

    // ------------------------------------------------------------------
    // Targeted -0.0 / 0.0 identity test (no oracle — pins OUR semantics)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("-0.0 and 0.0 are the same key (primitive comparison)")
    void negativeZeroIdentity() {
        DoubleRbTree tree = new DoubleRbTree();
        assertTrue(tree.insert(0.0), "first insert(0.0) should return true");
        assertFalse(tree.insert(-0.0), "insert(-0.0) after 0.0 already present should return false");
        assertTrue(tree.contains(-0.0), "contains(-0.0) must be true — same key as 0.0");
        assertTrue(tree.contains(0.0),  "contains(0.0) must still be true");
        assertEquals(1, tree.size(), "size must be 1");
    }

    // ------------------------------------------------------------------
    // Empty-tree edge cases
    // ------------------------------------------------------------------

    @Test
    @DisplayName("empty tree: lowerOrNaN and higherOrNaN return NaN, size is 0")
    void emptyTree() {
        DoubleRbTree tree = new DoubleRbTree();
        assertEquals(0, tree.size());
        assertTrue(Double.isNaN(tree.lowerOrNaN(0.0)),  "lowerOrNaN on empty tree");
        assertTrue(Double.isNaN(tree.higherOrNaN(0.0)), "higherOrNaN on empty tree");
        assertFalse(tree.contains(0.0));
    }

    // ------------------------------------------------------------------
    // snapRound — single-descent equivalence with the three-call sequence
    // ------------------------------------------------------------------

    /** Reference implementation: the original insert + lowerOrNaN + higherOrNaN (+ remove). */
    private static double referenceRound(DoubleRbTree tree, double coord) {
        tree.insert(coord);
        double prevKey = tree.lowerOrNaN(coord);
        if (!Double.isNaN(prevKey) && Flp.cmp(coord, prevKey) == 0) {
            tree.remove(coord);
            return prevKey;
        }
        double nextKey = tree.higherOrNaN(coord);
        if (!Double.isNaN(nextKey) && Flp.cmp(coord, nextKey) == 0) {
            tree.remove(coord);
            return nextKey;
        }
        return coord;
    }

    @Test
    @DisplayName("snapRound: identical returns and tree state vs the three-call sequence")
    void snapRoundEquivalence() {
        for (long seed : new long[] {11, 77, 31337}) {
            Random random = new Random(seed);
            DoubleRbTree ref = new DoubleRbTree();
            DoubleRbTree fast = new DoubleRbTree();
            // preseed like CoordRounder does
            assertEquals(referenceRound(ref, 0.0), fast.snapRound(0.0), 0.0);
            for (int i = 0; i < 20_000; i++) {
                double base = random.nextInt(200) * 0.5;
                // a third of the coords land within the Flp epsilon band of a base value,
                // exercising both snap branches; the rest are exact repeats / new keys
                double coord;
                int kind = random.nextInt(3);
                if (kind == 0) coord = base;
                else if (kind == 1) coord = base * (1.0 + (random.nextInt(7) - 3) * 1e-16);
                else coord = base + random.nextInt(1000) * 1e-19;
                double expected = referenceRound(ref, coord);
                double actual = fast.snapRound(coord);
                assertEquals(
                        Double.doubleToRawLongBits(expected),
                        Double.doubleToRawLongBits(actual),
                        "return mismatch at op " + i + " coord=" + coord + " seed=" + seed);
                assertEquals(ref.size(), fast.size(), "size mismatch at op " + i + " seed=" + seed);
            }
            // spot-check final tree content agreement over the whole key universe
            for (int k = 0; k <= 400; k++) {
                double key = k * 0.25;
                assertEquals(ref.contains(key), fast.contains(key), "contains(" + key + ")");
            }
        }
    }
}
