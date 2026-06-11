package com.bytejoey.polygonclip.util;

import com.bytejoey.polygonclip.num.Vec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BboxTest {

    @Nested
    @DisplayName("is in bbox")
    class IsInBbox {

        @Test
        @DisplayName("outside")
        void outside() {
            Bbox bbox = new Bbox(new Vec(1, 2), new Vec(5, 6));
            assertFalse(Bbox.isInBbox(bbox, new Vec(0, 3)));
            assertFalse(Bbox.isInBbox(bbox, new Vec(3, 30)));
            assertFalse(Bbox.isInBbox(bbox, new Vec(3, -30)));
            assertFalse(Bbox.isInBbox(bbox, new Vec(9, 3)));
        }

        @Test
        @DisplayName("inside")
        void inside() {
            Bbox bbox = new Bbox(new Vec(1, 2), new Vec(5, 6));
            assertTrue(Bbox.isInBbox(bbox, new Vec(1, 2)));
            assertTrue(Bbox.isInBbox(bbox, new Vec(5, 6)));
            assertTrue(Bbox.isInBbox(bbox, new Vec(1, 6)));
            assertTrue(Bbox.isInBbox(bbox, new Vec(5, 2)));
            assertTrue(Bbox.isInBbox(bbox, new Vec(3, 4)));
        }

        @Test
        @DisplayName("barely inside & outside")
        void barelyInsideAndOutside() {
            // Math.ulp(1.0) == Number.EPSILON
            Bbox bbox = new Bbox(new Vec(1, 0.8), new Vec(1.2, 6));
            assertTrue(Bbox.isInBbox(bbox, new Vec(1.2 - Math.ulp(1.0), 6)));
            assertFalse(Bbox.isInBbox(bbox, new Vec(1.2 + Math.ulp(1.0), 6)));
            assertTrue(Bbox.isInBbox(bbox, new Vec(1, 0.8 + Math.ulp(1.0))));
            assertFalse(Bbox.isInBbox(bbox, new Vec(1, 0.8 - Math.ulp(1.0))));
        }
    }

    @Nested
    @DisplayName("bbox overlap")
    class BboxOverlap {

        private final Bbox b1 = new Bbox(new Vec(4, 4), new Vec(6, 6));

        @Nested
        @DisplayName("disjoint - none")
        class DisjointNone {

            @Test
            @DisplayName("above")
            void above() {
                Bbox b2 = new Bbox(new Vec(7, 7), new Vec(8, 8));
                assertNull(Bbox.getBboxOverlap(b1, b2));
            }

            @Test
            @DisplayName("left")
            void left() {
                Bbox b2 = new Bbox(new Vec(1, 5), new Vec(3, 8));
                assertNull(Bbox.getBboxOverlap(b1, b2));
            }

            @Test
            @DisplayName("down")
            void down() {
                Bbox b2 = new Bbox(new Vec(2, 2), new Vec(3, 3));
                assertNull(Bbox.getBboxOverlap(b1, b2));
            }

            @Test
            @DisplayName("right")
            void right() {
                Bbox b2 = new Bbox(new Vec(12, 1), new Vec(14, 9));
                assertNull(Bbox.getBboxOverlap(b1, b2));
            }
        }

        @Nested
        @DisplayName("touching - one point")
        class TouchingOnePoint {

            @Test
            @DisplayName("upper right corner of 1")
            void upperRightCornerOf1() {
                Bbox b2 = new Bbox(new Vec(6, 6), new Vec(7, 8));
                Bbox result = Bbox.getBboxOverlap(b1, b2);
                assertEquals(new Vec(6, 6), result.ll);
                assertEquals(new Vec(6, 6), result.ur);
            }

            @Test
            @DisplayName("upper left corner of 1")
            void upperLeftCornerOf1() {
                Bbox b2 = new Bbox(new Vec(3, 6), new Vec(4, 8));
                Bbox result = Bbox.getBboxOverlap(b1, b2);
                assertEquals(new Vec(4, 6), result.ll);
                assertEquals(new Vec(4, 6), result.ur);
            }

            @Test
            @DisplayName("lower left corner of 1")
            void lowerLeftCornerOf1() {
                Bbox b2 = new Bbox(new Vec(0, 0), new Vec(4, 4));
                Bbox result = Bbox.getBboxOverlap(b1, b2);
                assertEquals(new Vec(4, 4), result.ll);
                assertEquals(new Vec(4, 4), result.ur);
            }

            @Test
            @DisplayName("lower right corner of 1")
            void lowerRightCornerOf1() {
                Bbox b2 = new Bbox(new Vec(6, 0), new Vec(12, 4));
                Bbox result = Bbox.getBboxOverlap(b1, b2);
                assertEquals(new Vec(6, 4), result.ll);
                assertEquals(new Vec(6, 4), result.ur);
            }
        }

        @Nested
        @DisplayName("overlapping - two points")
        class OverlappingTwoPoints {

            @Nested
            @DisplayName("full overlap")
            class FullOverlap {

                @Test
                @DisplayName("matching bboxes")
                void matchingBboxes() {
                    Bbox result = Bbox.getBboxOverlap(b1, b1);
                    assertEquals(b1.ll, result.ll);
                    assertEquals(b1.ur, result.ur);
                }

                @Test
                @DisplayName("one side & two corners matching")
                void oneSideAndTwoCornersMatching() {
                    Bbox b2 = new Bbox(new Vec(4, 4), new Vec(5, 6));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(4, 4), result.ll);
                    assertEquals(new Vec(5, 6), result.ur);
                }

                @Test
                @DisplayName("one corner matching, part of two sides")
                void oneCornerMatchingPartOfTwoSides() {
                    Bbox b2 = new Bbox(new Vec(5, 4), new Vec(6, 5));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(5, 4), result.ll);
                    assertEquals(new Vec(6, 5), result.ur);
                }

                @Test
                @DisplayName("part of a side matching, no corners")
                void partOfASideMatchingNoCorners() {
                    Bbox b2 = new Bbox(new Vec(4.5, 4.5), new Vec(5.5, 6));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(4.5, 4.5), result.ll);
                    assertEquals(new Vec(5.5, 6), result.ur);
                }

                @Test
                @DisplayName("completely enclosed - no side or corner matching")
                void completelyEnclosedNoSideOrCornerMatching() {
                    Bbox b2 = new Bbox(new Vec(4.5, 5), new Vec(5.5, 5.5));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(b2.ll, result.ll);
                    assertEquals(b2.ur, result.ur);
                }
            }

            @Nested
            @DisplayName("partial overlap")
            class PartialOverlap {

                @Test
                @DisplayName("full side overlap")
                void fullSideOverlap() {
                    Bbox b2 = new Bbox(new Vec(3, 4), new Vec(5, 6));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(4, 4), result.ll);
                    assertEquals(new Vec(5, 6), result.ur);
                }

                @Test
                @DisplayName("partial side overlap")
                void partialSideOverlap() {
                    Bbox b2 = new Bbox(new Vec(5, 4.5), new Vec(7, 5.5));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(5, 4.5), result.ll);
                    assertEquals(new Vec(6, 5.5), result.ur);
                }

                @Test
                @DisplayName("corner overlap")
                void cornerOverlap() {
                    Bbox b2 = new Bbox(new Vec(5, 5), new Vec(7, 7));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(5, 5), result.ll);
                    assertEquals(new Vec(6, 6), result.ur);
                }
            }
        }

        @Nested
        @DisplayName("line bboxes")
        class LineBboxes {

            @Nested
            @DisplayName("vertical line & normal")
            class VerticalLineAndNormal {

                @Test
                @DisplayName("no overlap")
                void noOverlap() {
                    Bbox b2 = new Bbox(new Vec(7, 3), new Vec(7, 6));
                    assertNull(Bbox.getBboxOverlap(b1, b2));
                }

                @Test
                @DisplayName("point overlap")
                void pointOverlap() {
                    Bbox b2 = new Bbox(new Vec(6, 0), new Vec(6, 4));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(6, 4), result.ll);
                    assertEquals(new Vec(6, 4), result.ur);
                }

                @Test
                @DisplayName("line overlap")
                void lineOverlap() {
                    Bbox b2 = new Bbox(new Vec(5, 0), new Vec(5, 9));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(5, 4), result.ll);
                    assertEquals(new Vec(5, 6), result.ur);
                }
            }

            @Nested
            @DisplayName("horizontal line & normal")
            class HorizontalLineAndNormal {

                @Test
                @DisplayName("no overlap")
                void noOverlap() {
                    Bbox b2 = new Bbox(new Vec(3, 7), new Vec(6, 7));
                    assertNull(Bbox.getBboxOverlap(b1, b2));
                }

                @Test
                @DisplayName("point overlap")
                void pointOverlap() {
                    Bbox b2 = new Bbox(new Vec(1, 6), new Vec(4, 6));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(4, 6), result.ll);
                    assertEquals(new Vec(4, 6), result.ur);
                }

                @Test
                @DisplayName("line overlap")
                void lineOverlap() {
                    Bbox b2 = new Bbox(new Vec(4, 6), new Vec(6, 6));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(4, 6), result.ll);
                    assertEquals(new Vec(6, 6), result.ur);
                }
            }

            @Nested
            @DisplayName("two vertical lines")
            class TwoVerticalLines {

                private final Bbox v1 = new Bbox(new Vec(4, 4), new Vec(4, 6));

                @Test
                @DisplayName("no overlap")
                void noOverlap() {
                    Bbox v2 = new Bbox(new Vec(4, 7), new Vec(4, 8));
                    assertNull(Bbox.getBboxOverlap(v1, v2));
                }

                @Test
                @DisplayName("point overlap")
                void pointOverlap() {
                    Bbox v2 = new Bbox(new Vec(4, 3), new Vec(4, 4));
                    Bbox result = Bbox.getBboxOverlap(v1, v2);
                    assertEquals(new Vec(4, 4), result.ll);
                    assertEquals(new Vec(4, 4), result.ur);
                }

                @Test
                @DisplayName("line overlap")
                void lineOverlap() {
                    Bbox v2 = new Bbox(new Vec(4, 3), new Vec(4, 5));
                    Bbox result = Bbox.getBboxOverlap(v1, v2);
                    assertEquals(new Vec(4, 4), result.ll);
                    assertEquals(new Vec(4, 5), result.ur);
                }
            }

            @Nested
            @DisplayName("two horizontal lines")
            class TwoHorizontalLines {

                private final Bbox h1 = new Bbox(new Vec(4, 6), new Vec(7, 6));

                @Test
                @DisplayName("no overlap")
                void noOverlap() {
                    Bbox h2 = new Bbox(new Vec(4, 5), new Vec(7, 5));
                    assertNull(Bbox.getBboxOverlap(h1, h2));
                }

                @Test
                @DisplayName("point overlap")
                void pointOverlap() {
                    Bbox h2 = new Bbox(new Vec(7, 6), new Vec(8, 6));
                    Bbox result = Bbox.getBboxOverlap(h1, h2);
                    assertEquals(new Vec(7, 6), result.ll);
                    assertEquals(new Vec(7, 6), result.ur);
                }

                @Test
                @DisplayName("line overlap")
                void lineOverlap() {
                    Bbox h2 = new Bbox(new Vec(4, 6), new Vec(7, 6));
                    Bbox result = Bbox.getBboxOverlap(h1, h2);
                    assertEquals(new Vec(4, 6), result.ll);
                    assertEquals(new Vec(7, 6), result.ur);
                }
            }

            @Nested
            @DisplayName("horizonal and vertical lines")
            class HorizonalAndVerticalLines {

                @Test
                @DisplayName("no overlap")
                void noOverlap() {
                    Bbox h1 = new Bbox(new Vec(4, 6), new Vec(8, 6));
                    Bbox v1 = new Bbox(new Vec(5, 7), new Vec(5, 9));
                    assertNull(Bbox.getBboxOverlap(h1, v1));
                }

                @Test
                @DisplayName("point overlap")
                void pointOverlap() {
                    Bbox h1 = new Bbox(new Vec(4, 6), new Vec(8, 6));
                    Bbox v1 = new Bbox(new Vec(5, 5), new Vec(5, 9));
                    Bbox result = Bbox.getBboxOverlap(h1, v1);
                    assertEquals(new Vec(5, 6), result.ll);
                    assertEquals(new Vec(5, 6), result.ur);
                }
            }

            @Nested
            @DisplayName("produced line box")
            class ProducedLineBox {

                @Test
                @DisplayName("horizontal")
                void horizontal() {
                    Bbox b2 = new Bbox(new Vec(4, 6), new Vec(8, 8));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(4, 6), result.ll);
                    assertEquals(new Vec(6, 6), result.ur);
                }

                @Test
                @DisplayName("vertical")
                void vertical() {
                    Bbox b2 = new Bbox(new Vec(6, 2), new Vec(8, 8));
                    Bbox result = Bbox.getBboxOverlap(b1, b2);
                    assertEquals(new Vec(6, 4), result.ll);
                    assertEquals(new Vec(6, 6), result.ur);
                }
            }
        }

        @Nested
        @DisplayName("point bboxes")
        class PointBboxes {

            @Nested
            @DisplayName("point & normal")
            class PointAndNormal {

                @Test
                @DisplayName("no overlap")
                void noOverlap() {
                    Bbox p = new Bbox(new Vec(2, 2), new Vec(2, 2));
                    assertNull(Bbox.getBboxOverlap(b1, p));
                }

                @Test
                @DisplayName("point overlap")
                void pointOverlap() {
                    Bbox p = new Bbox(new Vec(5, 5), new Vec(5, 5));
                    Bbox result = Bbox.getBboxOverlap(b1, p);
                    assertEquals(p.ll, result.ll);
                    assertEquals(p.ur, result.ur);
                }
            }

            @Nested
            @DisplayName("point & line")
            class PointAndLine {

                @Test
                @DisplayName("no overlap")
                void noOverlap() {
                    Bbox p = new Bbox(new Vec(2, 2), new Vec(2, 2));
                    Bbox l = new Bbox(new Vec(4, 6), new Vec(4, 8));
                    assertNull(Bbox.getBboxOverlap(l, p));
                }

                @Test
                @DisplayName("point overlap")
                void pointOverlap() {
                    Bbox p = new Bbox(new Vec(5, 5), new Vec(5, 5));
                    Bbox l = new Bbox(new Vec(4, 5), new Vec(6, 5));
                    Bbox result = Bbox.getBboxOverlap(l, p);
                    assertEquals(p.ll, result.ll);
                    assertEquals(p.ur, result.ur);
                }
            }

            @Nested
            @DisplayName("point & point")
            class PointAndPoint {

                @Test
                @DisplayName("no overlap")
                void noOverlap() {
                    Bbox p1 = new Bbox(new Vec(2, 2), new Vec(2, 2));
                    Bbox p2 = new Bbox(new Vec(4, 6), new Vec(4, 6));
                    assertNull(Bbox.getBboxOverlap(p1, p2));
                }

                @Test
                @DisplayName("point overlap")
                void pointOverlap() {
                    Bbox p = new Bbox(new Vec(5, 5), new Vec(5, 5));
                    Bbox result = Bbox.getBboxOverlap(p, p);
                    assertEquals(p.ll, result.ll);
                    assertEquals(p.ur, result.ur);
                }
            }
        }
    }
}
