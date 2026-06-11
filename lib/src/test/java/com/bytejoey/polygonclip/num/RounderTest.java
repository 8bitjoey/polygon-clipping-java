package com.bytejoey.polygonclip.num;

import com.bytejoey.polygonclip.sweep.SweepPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Transliteration of upstream/test/rounder.test.js — all 5 tests.
 *
 * Mapping: upstream calls rounder.reset() at the start of each test to return
 * the module singleton to a clean state. In this Java port, Rounder is a
 * per-operation object (not a singleton), so reset() ≡ new Rounder(). Each
 * test constructs a fresh Rounder instance rather than resetting a shared one.
 *
 * assertEquals(expected, actual) on double fields uses JUnit 5's bitwise
 * double equality (no delta), matching JS's toEqual on Number.
 *
 * Number.EPSILON → Math.ulp(1.0) (both are 2^-52, IEEE 754 machine epsilon).
 */
@DisplayName("rounder.round()")
class RounderTest {

    @Nested
    @DisplayName("no overlap")
    class NoOverlap {
        @Test
        @DisplayName("no overlap")
        void noOverlap() {
            Rounder rounder = new Rounder();
            double pt1x = 3, pt1y = 4;
            double pt2x = 4, pt2y = 5;
            double pt3x = 5, pt3y = 5;

            SweepPoint r1 = rounder.round(pt1x, pt1y);
            assertEquals(pt1x, r1.x);
            assertEquals(pt1y, r1.y);

            SweepPoint r2 = rounder.round(pt2x, pt2y);
            assertEquals(pt2x, r2.x);
            assertEquals(pt2y, r2.y);

            SweepPoint r3 = rounder.round(pt3x, pt3y);
            assertEquals(pt3x, r3.x);
            assertEquals(pt3y, r3.y);
        }
    }

    @Nested
    @DisplayName("exact overlap")
    class ExactOverlap {
        @Test
        @DisplayName("exact overlap")
        void exactOverlap() {
            Rounder rounder = new Rounder();
            double pt1x = 3, pt1y = 4;
            double pt2x = 4, pt2y = 5;
            // pt3 == pt1 exactly
            double pt3x = 3, pt3y = 4;

            SweepPoint r1 = rounder.round(pt1x, pt1y);
            assertEquals(pt1x, r1.x);
            assertEquals(pt1y, r1.y);

            SweepPoint r2 = rounder.round(pt2x, pt2y);
            assertEquals(pt2x, r2.x);
            assertEquals(pt2y, r2.y);

            SweepPoint r3 = rounder.round(pt3x, pt3y);
            assertEquals(pt1x, r3.x);
            assertEquals(pt1y, r3.y);
        }
    }

    @Nested
    @DisplayName("rounding one coordinate")
    class RoundingOneCoordinate {
        @Test
        @DisplayName("rounding one coordinate")
        void roundingOneCoordinate() {
            Rounder rounder = new Rounder();
            double eps = Math.ulp(1.0);
            double pt1x = 3, pt1y = 4;
            double pt2x = 3 + eps, pt2y = 4;
            double pt3x = 3, pt3y = 4 + eps;

            SweepPoint r1 = rounder.round(pt1x, pt1y);
            assertEquals(pt1x, r1.x);
            assertEquals(pt1y, r1.y);

            // pt2.x rounds to pt1.x, pt2.y is same as pt1.y → result == pt1
            SweepPoint r2 = rounder.round(pt2x, pt2y);
            assertEquals(pt1x, r2.x);
            assertEquals(pt1y, r2.y);

            // pt3.x is same as pt1.x, pt3.y rounds to pt1.y → result == pt1
            SweepPoint r3 = rounder.round(pt3x, pt3y);
            assertEquals(pt1x, r3.x);
            assertEquals(pt1y, r3.y);
        }
    }

    @Nested
    @DisplayName("rounding both coordinates")
    class RoundingBothCoordinates {
        @Test
        @DisplayName("rounding both coordinates")
        void roundingBothCoordinates() {
            Rounder rounder = new Rounder();
            double eps = Math.ulp(1.0);
            double pt1x = 3, pt1y = 4;
            double pt2x = 3 + eps, pt2y = 4 + eps;

            SweepPoint r1 = rounder.round(pt1x, pt1y);
            assertEquals(pt1x, r1.x);
            assertEquals(pt1y, r1.y);

            SweepPoint r2 = rounder.round(pt2x, pt2y);
            assertEquals(pt1x, r2.x);
            assertEquals(pt1y, r2.y);
        }
    }

    @Nested
    @DisplayName("preseed with 0")
    class PreseedWithZero {
        @Test
        @DisplayName("preseed with 0")
        void preseedWithZero() {
            Rounder rounder = new Rounder();
            double eps = Math.ulp(1.0);
            double pt1x = eps / 2;
            double pt1y = -eps / 2;

            // Guard: these values are not exactly 0
            assert pt1x != 0.0;
            assert pt1y != 0.0;

            // Both snap to the preseeded 0
            SweepPoint r1 = rounder.round(pt1x, pt1y);
            assertEquals(0.0, r1.x);
            assertEquals(0.0, r1.y);
        }
    }
}
