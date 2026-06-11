package com.bytejoey.polygonclip.num;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlpTest {

    @Nested
    @DisplayName("compare")
    class Compare {

        @Test
        @DisplayName("exactly equal")
        void exactlyEqual() {
            double a = 1;
            double b = 1;
            assertEquals(0, Flp.cmp(a, b));
        }

        @Test
        @DisplayName("flp equal")
        void flpEqual() {
            double a = 1;
            double b = 1 + Math.ulp(1.0);
            assertEquals(0, Flp.cmp(a, b));
        }

        @Test
        @DisplayName("barely less than")
        void barelyLessThan() {
            double a = 1;
            double b = 1 + Math.ulp(1.0) * 2;
            assertEquals(-1, Flp.cmp(a, b));
        }

        @Test
        @DisplayName("less than")
        void lessThan() {
            double a = 1;
            double b = 2;
            assertEquals(-1, Flp.cmp(a, b));
        }

        @Test
        @DisplayName("barely more than")
        void barelyMoreThan() {
            double a = 1 + Math.ulp(1.0) * 2;
            double b = 1;
            assertEquals(1, Flp.cmp(a, b));
        }

        @Test
        @DisplayName("more than")
        void moreThan() {
            double a = 2;
            double b = 1;
            assertEquals(1, Flp.cmp(a, b));
        }

        @Test
        @DisplayName("both flp equal to zero")
        void bothFlpEqualToZero() {
            double a = 0.0;
            double b = Math.ulp(1.0) - Math.ulp(1.0) * Math.ulp(1.0);
            assertEquals(0, Flp.cmp(a, b));
        }

        @Test
        @DisplayName("really close to zero")
        void reallyCloseToZero() {
            double a = Math.ulp(1.0);
            double b = Math.ulp(1.0) + Math.ulp(1.0) * Math.ulp(1.0) * 2;
            assertEquals(-1, Flp.cmp(a, b));
        }
    }
}
