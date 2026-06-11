package com.bytejoey.polygonclip.num;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Upstream's perpendicular block was dropped together with the function — dead code upstream
 * too (defined and tested, never called by the algorithm).
 */
@DisplayName("Vector")
class VectorTest {

    @Nested
    @DisplayName("cross product")
    class CrossProduct {
        @Test
        @DisplayName("general")
        void general() {
            Vec pt1 = new Vec(1, 2);
            Vec pt2 = new Vec(3, 4);
            assertEquals(-2, Vector.crossProduct(pt1, pt2));
        }
    }

    @Nested
    @DisplayName("dot product")
    class DotProduct {
        @Test
        @DisplayName("general")
        void general() {
            Vec pt1 = new Vec(1, 2);
            Vec pt2 = new Vec(3, 4);
            assertEquals(11, Vector.dotProduct(pt1, pt2));
        }
    }

    @Nested
    @DisplayName("length()")
    class Length {
        @Test
        @DisplayName("horizontal")
        void horizontal() {
            Vec v = new Vec(3, 0);
            assertEquals(3, Vector.length(v));
        }

        @Test
        @DisplayName("vertical")
        void vertical() {
            Vec v = new Vec(0, -2);
            assertEquals(2, Vector.length(v));
        }

        @Test
        @DisplayName("3-4-5")
        void threefourfive() {
            Vec v = new Vec(3, 4);
            assertEquals(5, Vector.length(v));
        }
    }

    @Nested
    @DisplayName("compare vector angles")
    class CompareVectorAngles {
        @Test
        @DisplayName("colinear")
        void colinear() {
            Vec pt1 = new Vec(1, 1);
            Vec pt2 = new Vec(2, 2);
            Vec pt3 = new Vec(3, 3);

            assertEquals(0, Vector.compareVectorAngles(pt1, pt2, pt3));
            assertEquals(0, Vector.compareVectorAngles(pt2, pt1, pt3));
            assertEquals(0, Vector.compareVectorAngles(pt2, pt3, pt1));
            assertEquals(0, Vector.compareVectorAngles(pt3, pt2, pt1));
        }

        @Test
        @DisplayName("offset")
        void offset() {
            Vec pt1 = new Vec(0, 0);
            Vec pt2 = new Vec(1, 1);
            Vec pt3 = new Vec(1, 0);

            assertEquals(-1, Vector.compareVectorAngles(pt1, pt2, pt3));
            assertEquals(1, Vector.compareVectorAngles(pt2, pt1, pt3));
            assertEquals(-1, Vector.compareVectorAngles(pt2, pt3, pt1));
            assertEquals(1, Vector.compareVectorAngles(pt3, pt2, pt1));
        }
    }

    @Nested
    @DisplayName("sine and cosine of angle")
    class SineAndCosineOfAngle {

        @Nested
        @DisplayName("parallel")
        class Parallel {
            Vec shared = new Vec(0, 0);
            Vec base = new Vec(1, 0);
            Vec angle = new Vec(1, 0);

            @Test
            @DisplayName("sine")
            void sine() {
                assertEquals(0, Vector.sineOfAngle(shared, base, angle));
            }

            @Test
            @DisplayName("cosine")
            void cosine() {
                assertEquals(1, Vector.cosineOfAngle(shared, base, angle));
            }
        }

        @Nested
        @DisplayName("45 degrees")
        class FortyFiveDegrees {
            Vec shared = new Vec(0, 0);
            Vec base = new Vec(1, 0);
            Vec angle = new Vec(1, -1);

            @Test
            @DisplayName("sine")
            void sine() {
                assertEquals(Math.sqrt(2) / 2, Vector.sineOfAngle(shared, base, angle), 0.005);
            }

            @Test
            @DisplayName("cosine")
            void cosine() {
                assertEquals(Math.sqrt(2) / 2, Vector.cosineOfAngle(shared, base, angle), 0.005);
            }
        }

        @Nested
        @DisplayName("90 degrees")
        class NinetyDegrees {
            Vec shared = new Vec(0, 0);
            Vec base = new Vec(1, 0);
            Vec angle = new Vec(0, -1);

            @Test
            @DisplayName("sine")
            void sine() {
                assertEquals(1, Vector.sineOfAngle(shared, base, angle));
            }

            @Test
            @DisplayName("cosine")
            void cosine() {
                assertEquals(0, Vector.cosineOfAngle(shared, base, angle));
            }
        }

        @Nested
        @DisplayName("135 degrees")
        class OneThirtyFiveDegrees {
            Vec shared = new Vec(0, 0);
            Vec base = new Vec(1, 0);
            Vec angle = new Vec(-1, -1);

            @Test
            @DisplayName("sine")
            void sine() {
                assertEquals(Math.sqrt(2) / 2, Vector.sineOfAngle(shared, base, angle), 0.005);
            }

            @Test
            @DisplayName("cosine")
            void cosine() {
                assertEquals(-Math.sqrt(2) / 2, Vector.cosineOfAngle(shared, base, angle), 0.005);
            }
        }

        @Nested
        @DisplayName("anti-parallel")
        class AntiParallel {
            Vec shared = new Vec(0, 0);
            Vec base = new Vec(1, 0);
            Vec angle = new Vec(-1, 0);

            @Test
            @DisplayName("sine")
            void sine() {
                assertEquals(-0.0, Vector.sineOfAngle(shared, base, angle));
            }

            @Test
            @DisplayName("cosine")
            void cosine() {
                assertEquals(-1, Vector.cosineOfAngle(shared, base, angle));
            }
        }

        @Nested
        @DisplayName("225 degrees")
        class TwoTwentyFiveDegrees {
            Vec shared = new Vec(0, 0);
            Vec base = new Vec(1, 0);
            Vec angle = new Vec(-1, 1);

            @Test
            @DisplayName("sine")
            void sine() {
                assertEquals(-Math.sqrt(2) / 2, Vector.sineOfAngle(shared, base, angle), 0.005);
            }

            @Test
            @DisplayName("cosine")
            void cosine() {
                assertEquals(-Math.sqrt(2) / 2, Vector.cosineOfAngle(shared, base, angle), 0.005);
            }
        }

        @Nested
        @DisplayName("270 degrees")
        class TwoSeventyDegrees {
            Vec shared = new Vec(0, 0);
            Vec base = new Vec(1, 0);
            Vec angle = new Vec(0, 1);

            @Test
            @DisplayName("sine")
            void sine() {
                assertEquals(-1, Vector.sineOfAngle(shared, base, angle));
            }

            @Test
            @DisplayName("cosine")
            void cosine() {
                assertEquals(0, Vector.cosineOfAngle(shared, base, angle));
            }
        }

        @Nested
        @DisplayName("315 degrees")
        class ThreeOneFiveDegrees {
            Vec shared = new Vec(0, 0);
            Vec base = new Vec(1, 0);
            Vec angle = new Vec(1, 1);

            @Test
            @DisplayName("sine")
            void sine() {
                assertEquals(-Math.sqrt(2) / 2, Vector.sineOfAngle(shared, base, angle), 0.005);
            }

            @Test
            @DisplayName("cosine")
            void cosine() {
                assertEquals(Math.sqrt(2) / 2, Vector.cosineOfAngle(shared, base, angle), 0.005);
            }
        }
    }

    @Nested
    @DisplayName("closestPoint()")
    class ClosestPoint {
        @Test
        @DisplayName("on line")
        void onLine() {
            Vec pA1 = new Vec(2, 2);
            Vec pA2 = new Vec(3, 3);
            Vec pB = new Vec(-1, -1);
            Vec cp = Vector.closestPoint(pA1, pA2, pB);
            assertEquals(pB, cp);
        }

        @Test
        @DisplayName("on first point")
        void onFirstPoint() {
            Vec pA1 = new Vec(2, 2);
            Vec pA2 = new Vec(3, 3);
            Vec pB = new Vec(2, 2);
            Vec cp = Vector.closestPoint(pA1, pA2, pB);
            assertEquals(pB, cp);
        }

        @Test
        @DisplayName("off line above")
        void offLineAbove() {
            Vec pA1 = new Vec(2, 2);
            Vec pA2 = new Vec(3, 1);
            Vec pB = new Vec(3, 7);
            Vec expected = new Vec(0, 4);
            assertEquals(expected, Vector.closestPoint(pA1, pA2, pB));
            assertEquals(expected, Vector.closestPoint(pA2, pA1, pB));
        }

        @Test
        @DisplayName("off line below")
        void offLineBelow() {
            Vec pA1 = new Vec(2, 2);
            Vec pA2 = new Vec(3, 1);
            Vec pB = new Vec(0, 2);
            Vec expected = new Vec(1, 3);
            assertEquals(expected, Vector.closestPoint(pA1, pA2, pB));
            assertEquals(expected, Vector.closestPoint(pA2, pA1, pB));
        }

        @Test
        @DisplayName("off line perpendicular to first point")
        void offLinePerpendicularToFirstPoint() {
            Vec pA1 = new Vec(2, 2);
            Vec pA2 = new Vec(3, 3);
            Vec pB = new Vec(1, 3);
            Vec cp = Vector.closestPoint(pA1, pA2, pB);
            Vec expected = new Vec(2, 2);
            assertEquals(expected, cp);
        }

        @Test
        @DisplayName("horizontal vector")
        void horizontalVector() {
            Vec pA1 = new Vec(2, 2);
            Vec pA2 = new Vec(3, 2);
            Vec pB = new Vec(1, 3);
            Vec cp = Vector.closestPoint(pA1, pA2, pB);
            Vec expected = new Vec(1, 2);
            assertEquals(expected, cp);
        }

        @Test
        @DisplayName("vertical vector")
        void verticalVector() {
            Vec pA1 = new Vec(2, 2);
            Vec pA2 = new Vec(2, 3);
            Vec pB = new Vec(1, 3);
            Vec cp = Vector.closestPoint(pA1, pA2, pB);
            Vec expected = new Vec(2, 3);
            assertEquals(expected, cp);
        }

        @Test
        @DisplayName("on line but dot product does not think so - part of issue 60-2")
        void onLineButDotProductDoesNotThinkSo() {
            Vec pA1 = new Vec(-45.3269382, -1.4059341);
            Vec pA2 = new Vec(-45.326737413921656, -1.40635);
            Vec pB = new Vec(-45.326833968900424, -1.40615);
            Vec cp = Vector.closestPoint(pA1, pA2, pB);
            assertEquals(pB, cp);
        }
    }

    @Nested
    @DisplayName("verticalIntersection()")
    class VerticalIntersection {
        @Test
        @DisplayName("horizontal")
        void horizontal() {
            Vec p = new Vec(42, 3);
            Vec v = new Vec(-2, 0);
            double x = 37;
            Vec i = Vector.verticalIntersection(p, v, x);
            assertEquals(37, i.x());
            assertEquals(3, i.y());
        }

        @Test
        @DisplayName("vertical")
        void vertical() {
            Vec p = new Vec(42, 3);
            Vec v = new Vec(0, 4);
            double x = 37;
            assertNull(Vector.verticalIntersection(p, v, x));
        }

        @Test
        @DisplayName("45 degree")
        void fortyFiveDegree() {
            Vec p = new Vec(1, 1);
            Vec v = new Vec(1, 1);
            double x = -2;
            Vec i = Vector.verticalIntersection(p, v, x);
            assertEquals(-2, i.x());
            assertEquals(-2, i.y());
        }

        @Test
        @DisplayName("upper left quadrant")
        void upperLeftQuadrant() {
            Vec p = new Vec(-1, 1);
            Vec v = new Vec(-2, 1);
            double x = -3;
            Vec i = Vector.verticalIntersection(p, v, x);
            assertEquals(-3, i.x());
            assertEquals(2, i.y());
        }
    }

    @Nested
    @DisplayName("horizontalIntersection()")
    class HorizontalIntersection {
        @Test
        @DisplayName("horizontal")
        void horizontal() {
            Vec p = new Vec(42, 3);
            Vec v = new Vec(-2, 0);
            double y = 37;
            assertNull(Vector.horizontalIntersection(p, v, y));
        }

        @Test
        @DisplayName("vertical")
        void vertical() {
            Vec p = new Vec(42, 3);
            Vec v = new Vec(0, 4);
            double y = 37;
            Vec i = Vector.horizontalIntersection(p, v, y);
            assertEquals(42, i.x());
            assertEquals(37, i.y());
        }

        @Test
        @DisplayName("45 degree")
        void fortyFiveDegree() {
            Vec p = new Vec(1, 1);
            Vec v = new Vec(1, 1);
            double y = 4;
            Vec i = Vector.horizontalIntersection(p, v, y);
            assertEquals(4, i.x());
            assertEquals(4, i.y());
        }

        @Test
        @DisplayName("bottom left quadrant")
        void bottomLeftQuadrant() {
            Vec p = new Vec(-1, -1);
            Vec v = new Vec(-2, -1);
            double y = -3;
            Vec i = Vector.horizontalIntersection(p, v, y);
            assertEquals(-5, i.x());
            assertEquals(-3, i.y());
        }
    }

    @Nested
    @DisplayName("intersection()")
    class Intersection {
        Vec p1 = new Vec(42, 42);
        Vec p2 = new Vec(-32, 46);

        @Test
        @DisplayName("parrallel")
        void parallel() {
            Vec v1 = new Vec(1, 2);
            Vec v2 = new Vec(-1, -2);
            Vec i = Vector.intersection(p1, v1, p2, v2);
            assertNull(i);
        }

        @Test
        @DisplayName("horizontal and vertical")
        void horizontalAndVertical() {
            Vec v1 = new Vec(0, 2);
            Vec v2 = new Vec(-1, 0);
            Vec i = Vector.intersection(p1, v1, p2, v2);
            assertEquals(42, i.x());
            assertEquals(46, i.y());
        }

        @Test
        @DisplayName("horizontal")
        void horizontal() {
            Vec v1 = new Vec(1, 1);
            Vec v2 = new Vec(-1, 0);
            Vec i = Vector.intersection(p1, v1, p2, v2);
            assertEquals(46, i.x());
            assertEquals(46, i.y());
        }

        @Test
        @DisplayName("vertical")
        void vertical() {
            Vec v1 = new Vec(1, 1);
            Vec v2 = new Vec(0, 1);
            Vec i = Vector.intersection(p1, v1, p2, v2);
            assertEquals(-32, i.x());
            assertEquals(-32, i.y());
        }

        @Test
        @DisplayName("45 degree & 135 degree")
        void fortyFiveAnd135Degree() {
            Vec v1 = new Vec(1, 1);
            Vec v2 = new Vec(-1, 1);
            Vec i = Vector.intersection(p1, v1, p2, v2);
            assertEquals(7, i.x());
            assertEquals(7, i.y());
        }

        @Test
        @DisplayName("consistency")
        void consistency() {
            Vec p1 = new Vec(0.523787, 51.281453);
            Vec v1 = new Vec(0.0002729999999999677, 0.0002729999999999677);
            Vec p2 = new Vec(0.523985, 51.281651);
            Vec v2 = new Vec(0.000024999999999941735, 0.000049000000004184585);
            Vec i1 = Vector.intersection(p1, v1, p2, v2);
            Vec i2 = Vector.intersection(p2, v2, p1, v1);
            assertEquals(i1.x(), i2.x());
            assertEquals(i1.y(), i2.y());
        }
    }
}
