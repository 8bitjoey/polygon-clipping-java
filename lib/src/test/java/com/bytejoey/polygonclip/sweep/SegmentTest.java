package com.bytejoey.polygonclip.sweep;

import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.OpType;
import com.bytejoey.polygonclip.input.RingIn;
import com.bytejoey.polygonclip.num.Vec;
import com.bytejoey.polygonclip.util.Bbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transliteration of upstream/test/segment.test.js — describe-blocks:
 * constructor (:6), fromRing (:33), simple properties - bbox, vector (:120),
 * is an endpoint (:192), comparison with point (:208), compare segments (:669),
 * split (:57), consume() (:140), get intersections 2 (:351).
 *
 * Mapping notes:
 * - JS point literals {x, y} → new SweepPoint(x, y); duck rings {id: n} → new RingIn(n).
 * - JS `Segment.fromRing(p1, p2)` with the ring omitted (undefined) → null ring here;
 *   fromRing then stores a one-element rings list containing null, faithful to JS [undefined].
 * - JS `new SweepEvent({x, y})` with isLeft omitted (undefined, falsy) → isLeft false.
 * - JS `new Segment(leftSE, rightSE, [])` with windings omitted → null windings.
 * - Number.EPSILON → Math.ulp(1.0) (bit-identical, see BboxTest).
 */
class SegmentTest {

    private Operation op;

    @BeforeEach
    void resetIds() {
        op = new Operation(OpType.UNION);
    }

    /** JS `expect(point).toEqual({x, y})` — value comparison of the coordinates. */
    private static void assertPointEquals(SweepPoint expected, SweepPoint actual) {
        assertEquals(expected.x, actual.x);
        assertEquals(expected.y, actual.y);
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("general")
        void general() {
            SweepEvent leftSE = new SweepEvent(new SweepPoint(0, 0), false);
            SweepEvent rightSE = new SweepEvent(new SweepPoint(1, 1), false);
            List<RingIn> rings = new ArrayList<>();
            List<Integer> windings = new ArrayList<>();
            Segment seg = new Segment(leftSE, rightSE, rings, windings, op);
            assertSame(rings, seg.rings);
            assertSame(windings, seg.windings);
            assertSame(leftSE, seg.leftSE);
            assertSame(rightSE, seg.leftSE.otherSE);
            assertSame(rightSE, seg.rightSE);
            assertSame(leftSE, seg.rightSE.otherSE);
            assertNull(seg.ringOut);
            assertNull(seg.prev);
            assertNull(seg.consumedBy);
        }

        @Test
        @DisplayName("segment Id increments")
        void segmentIdIncrements() {
            SweepEvent leftSE = new SweepEvent(new SweepPoint(0, 0), false);
            SweepEvent rightSE = new SweepEvent(new SweepPoint(1, 1), false);
            Segment seg1 = new Segment(leftSE, rightSE, new ArrayList<>(), null, op);
            Segment seg2 = new Segment(leftSE, rightSE, new ArrayList<>(), null, op);
            assertEquals(1, seg2.id - seg1.id);
        }
    }

    @Nested
    @DisplayName("fromRing")
    class FromRing {

        @Test
        @DisplayName("correct point on left and right 1")
        void correctPointOnLeftAndRight1() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepPoint p2 = new SweepPoint(0, 1);
            Segment seg = Segment.fromRing(p1, p2, null, op);
            assertPointEquals(p1, seg.leftSE.point);
            assertPointEquals(p2, seg.rightSE.point);
        }

        // upstream reuses the same test name for this second case (segment.test.js:42)
        @Test
        @DisplayName("correct point on left and right 1")
        void correctPointOnLeftAndRight1Again() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepPoint p2 = new SweepPoint(-1, 0);
            Segment seg = Segment.fromRing(p1, p2, null, op);
            assertPointEquals(p2, seg.leftSE.point);
            assertPointEquals(p1, seg.rightSE.point);
        }

        @Test
        @DisplayName("attempt create segment with same points")
        void attemptCreateSegmentWithSamePoints() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepPoint p2 = new SweepPoint(0, 0);
            assertThrows(IllegalArgumentException.class, () -> Segment.fromRing(p1, p2, null, op));
        }
    }

    @Nested
    @DisplayName("simple properties - bbox, vector")
    class SimplePropertiesBboxVector {

        @Test
        @DisplayName("general")
        void general() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 2), new SweepPoint(3, 4), null, op);
            Bbox bbox = seg.bbox();
            assertEquals(new Vec(1, 2), bbox.ll);
            assertEquals(new Vec(3, 4), bbox.ur);
            assertEquals(new Vec(2, 2), seg.vector());
        }

        @Test
        @DisplayName("horizontal")
        void horizontal() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 4), new SweepPoint(3, 4), null, op);
            Bbox bbox = seg.bbox();
            assertEquals(new Vec(1, 4), bbox.ll);
            assertEquals(new Vec(3, 4), bbox.ur);
            assertEquals(new Vec(2, 0), seg.vector());
        }

        @Test
        @DisplayName("vertical")
        void vertical() {
            Segment seg = Segment.fromRing(new SweepPoint(3, 2), new SweepPoint(3, 4), null, op);
            Bbox bbox = seg.bbox();
            assertEquals(new Vec(3, 2), bbox.ll);
            assertEquals(new Vec(3, 4), bbox.ur);
            assertEquals(new Vec(0, 2), seg.vector());
        }
    }

    @Nested
    @DisplayName("is an endpoint")
    class IsAnEndpoint {

        private final Operation opLocal = new Operation(OpType.UNION);
        private final SweepPoint p1 = new SweepPoint(0, -1);
        private final SweepPoint p2 = new SweepPoint(1, 0);
        private final Segment seg = Segment.fromRing(p1, p2, null, opLocal);

        @Test
        @DisplayName("yup")
        void yup() {
            assertTrue(seg.isAnEndpoint(p1));
            assertTrue(seg.isAnEndpoint(p2));
        }

        @Test
        @DisplayName("nope")
        void nope() {
            assertFalse(seg.isAnEndpoint(new SweepPoint(-34, 46)));
            assertFalse(seg.isAnEndpoint(new SweepPoint(0, 0)));
        }
    }

    @Nested
    @DisplayName("split")
    class Split {

        private static SweepEvent find(List<SweepEvent> evts, SweepPoint pt, boolean isLeft) {
            for (SweepEvent e : evts) {
                if (e.point == pt && e.isLeft == isLeft) return e;
            }
            return null;
        }

        @Test
        @DisplayName("on interior point")
        void onInteriorPoint() {
            Segment seg = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(10, 10), new RingIn(1), op);
            SweepPoint pt = new SweepPoint(5, 5);
            List<SweepEvent> evts = seg.split(pt);
            assertSame(seg, evts.get(0).segment);
            assertSame(pt, evts.get(0).point);
            assertFalse(evts.get(0).isLeft);
            assertSame(evts.get(0), evts.get(0).otherSE.otherSE);
            assertSame(evts.get(1).segment, evts.get(1).segment.leftSE.segment);
            assertNotSame(seg, evts.get(1).segment);
            assertSame(pt, evts.get(1).point);
            assertTrue(evts.get(1).isLeft);
            assertSame(evts.get(1), evts.get(1).otherSE.otherSE);
            assertSame(evts.get(1).segment, evts.get(1).segment.rightSE.segment);
        }

        @Test
        @DisplayName("on close-to-but-not-exactly interior point")
        void onCloseToButNotExactlyInteriorPoint() {
            Segment seg = Segment.fromRing(new SweepPoint(0, 10), new SweepPoint(10, 0), new RingIn(1), op);
            SweepPoint pt = new SweepPoint(5 + Math.ulp(1.0), 5);
            List<SweepEvent> evts = seg.split(pt);
            assertSame(seg, evts.get(0).segment);
            assertSame(pt, evts.get(0).point);
            assertFalse(evts.get(0).isLeft);
            assertNotSame(seg, evts.get(1).segment);
            assertSame(pt, evts.get(1).point);
            assertTrue(evts.get(1).isLeft);
            assertSame(evts.get(1).segment, evts.get(1).segment.rightSE.segment);
        }

        @Test
        @DisplayName("on three interior points")
        void onThreeInteriorPoints() {
            Segment seg = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(10, 10), new RingIn(1), op);
            SweepPoint sPt1 = new SweepPoint(2, 2);
            SweepPoint sPt2 = new SweepPoint(4, 4);
            SweepPoint sPt3 = new SweepPoint(6, 6);

            SweepEvent orgLeftEvt = seg.leftSE;
            SweepEvent orgRightEvt = seg.rightSE;
            List<SweepEvent> newEvts3 = seg.split(sPt3);
            List<SweepEvent> newEvts2 = seg.split(sPt2);
            List<SweepEvent> newEvts1 = seg.split(sPt1);
            List<SweepEvent> newEvts = new ArrayList<>();
            newEvts.addAll(newEvts1);
            newEvts.addAll(newEvts2);
            newEvts.addAll(newEvts3);

            assertEquals(6, newEvts.size());

            assertSame(orgLeftEvt, seg.leftSE);
            SweepEvent evt = find(newEvts, sPt1, false);
            assertSame(evt, seg.rightSE);

            evt = find(newEvts, sPt1, true);
            SweepEvent otherEvt = find(newEvts, sPt2, false);
            assertSame(evt.segment, otherEvt.segment);

            evt = find(newEvts, sPt2, true);
            otherEvt = find(newEvts, sPt3, false);
            assertSame(evt.segment, otherEvt.segment);

            evt = find(newEvts, sPt3, true);
            assertSame(evt.segment, orgRightEvt.segment);
        }
    }

    @Nested
    @DisplayName("consume()")
    class Consume {

        @Test
        @DisplayName("not automatically consumed")
        void notAutomaticallyConsumed() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepPoint p2 = new SweepPoint(1, 0);
            Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
            Segment seg2 = Segment.fromRing(p1, p2, new RingIn(2), op);
            assertNull(seg1.consumedBy);
            assertNull(seg2.consumedBy);
        }

        @Test
        @DisplayName("basic case")
        void basicCase() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepPoint p2 = new SweepPoint(1, 0);
            Segment seg1 = Segment.fromRing(p1, p2, new RingIn(0), op);
            Segment seg2 = Segment.fromRing(p1, p2, new RingIn(0), op);
            seg1.consume(seg2);
            assertSame(seg1, seg2.consumedBy);
            assertNull(seg1.consumedBy);
        }

        @Test
        @DisplayName("ealier in sweep line sorting consumes later")
        void ealierInSweepLineSortingConsumesLater() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepPoint p2 = new SweepPoint(1, 0);
            Segment seg1 = Segment.fromRing(p1, p2, new RingIn(0), op);
            Segment seg2 = Segment.fromRing(p1, p2, new RingIn(0), op);
            seg2.consume(seg1);
            assertSame(seg1, seg2.consumedBy);
            assertNull(seg1.consumedBy);
        }

        @Test
        @DisplayName("consuming cascades")
        void consumingCascades() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepPoint p2 = new SweepPoint(0, 0);
            SweepPoint p3 = new SweepPoint(1, 0);
            SweepPoint p4 = new SweepPoint(1, 0);
            Segment seg1 = Segment.fromRing(p1, p3, new RingIn(0), op);
            Segment seg2 = Segment.fromRing(p1, p3, new RingIn(0), op);
            Segment seg3 = Segment.fromRing(p2, p4, new RingIn(0), op);
            Segment seg4 = Segment.fromRing(p2, p4, new RingIn(0), op);
            Segment seg5 = Segment.fromRing(p2, p4, new RingIn(0), op);
            seg1.consume(seg2);
            seg4.consume(seg2);
            seg3.consume(seg2);
            seg3.consume(seg5);
            assertNull(seg1.consumedBy);
            assertSame(seg1, seg2.consumedBy);
            assertSame(seg1, seg3.consumedBy);
            assertSame(seg1, seg4.consumedBy);
            assertSame(seg1, seg5.consumedBy);
        }
    }

    @Nested
    @DisplayName("comparison with point")
    class ComparisonWithPoint {

        @Test
        @DisplayName("general")
        void general() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), null, op);
            Segment s2 = Segment.fromRing(new SweepPoint(0, 1), new SweepPoint(0, 0), null, op);

            assertEquals(1, s1.comparePoint(new SweepPoint(0, 1)));
            assertEquals(1, s1.comparePoint(new SweepPoint(1, 2)));
            assertEquals(0, s1.comparePoint(new SweepPoint(0, 0)));
            assertEquals(-1, s1.comparePoint(new SweepPoint(5, -1)));

            assertEquals(0, s2.comparePoint(new SweepPoint(0, 1)));
            assertEquals(-1, s2.comparePoint(new SweepPoint(1, 2)));
            assertEquals(0, s2.comparePoint(new SweepPoint(0, 0)));
            assertEquals(-1, s2.comparePoint(new SweepPoint(5, -1)));
        }

        @Test
        @DisplayName("barely above")
        void barelyAbove() {
            Segment s1 = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 1), null, op);
            SweepPoint pt = new SweepPoint(2, 1 - Math.ulp(1.0));
            assertEquals(-1, s1.comparePoint(pt));
        }

        @Test
        @DisplayName("barely below")
        void barelyBelow() {
            Segment s1 = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 1), null, op);
            SweepPoint pt = new SweepPoint(2, 1 + (Math.ulp(1.0) * 3) / 2);
            assertEquals(1, s1.comparePoint(pt));
        }

        @Test
        @DisplayName("vertical before")
        void verticalBefore() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(1, 3), null, op);
            SweepPoint pt = new SweepPoint(0, 0);
            assertEquals(1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("vertical after")
        void verticalAfter() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(1, 3), null, op);
            SweepPoint pt = new SweepPoint(2, 0);
            assertEquals(-1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("vertical on")
        void verticalOn() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(1, 3), null, op);
            SweepPoint pt = new SweepPoint(1, 0);
            assertEquals(0, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("horizontal below")
        void horizontalBelow() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 1), null, op);
            SweepPoint pt = new SweepPoint(0, 0);
            assertEquals(-1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("horizontal above")
        void horizontalAbove() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 1), null, op);
            SweepPoint pt = new SweepPoint(0, 2);
            assertEquals(1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("horizontal on")
        void horizontalOn() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 1), null, op);
            SweepPoint pt = new SweepPoint(0, 1);
            assertEquals(0, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("in vertical plane below")
        void inVerticalPlaneBelow() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 3), null, op);
            SweepPoint pt = new SweepPoint(2, 0);
            assertEquals(-1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("in vertical plane above")
        void inVerticalPlaneAbove() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 3), null, op);
            SweepPoint pt = new SweepPoint(2, 4);
            assertEquals(1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("in horizontal plane upward sloping before")
        void inHorizontalPlaneUpwardSlopingBefore() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 3), null, op);
            SweepPoint pt = new SweepPoint(0, 2);
            assertEquals(1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("in horizontal plane upward sloping after")
        void inHorizontalPlaneUpwardSlopingAfter() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 3), null, op);
            SweepPoint pt = new SweepPoint(4, 2);
            assertEquals(-1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("in horizontal plane downward sloping before")
        void inHorizontalPlaneDownwardSlopingBefore() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 3), new SweepPoint(3, 1), null, op);
            SweepPoint pt = new SweepPoint(0, 2);
            assertEquals(-1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("in horizontal plane downward sloping after")
        void inHorizontalPlaneDownwardSlopingAfter() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 3), new SweepPoint(3, 1), null, op);
            SweepPoint pt = new SweepPoint(4, 2);
            assertEquals(1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("upward more vertical before")
        void upwardMoreVerticalBefore() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 6), null, op);
            SweepPoint pt = new SweepPoint(0, 2);
            assertEquals(1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("upward more vertical after")
        void upwardMoreVerticalAfter() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 6), null, op);
            SweepPoint pt = new SweepPoint(4, 2);
            assertEquals(-1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("downward more vertical before")
        void downwardMoreVerticalBefore() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 6), new SweepPoint(3, 1), null, op);
            SweepPoint pt = new SweepPoint(0, 2);
            assertEquals(-1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("downward more vertical after")
        void downwardMoreVerticalAfter() {
            Segment seg = Segment.fromRing(new SweepPoint(1, 6), new SweepPoint(3, 1), null, op);
            SweepPoint pt = new SweepPoint(4, 2);
            assertEquals(1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("downward-slopping segment with almost touching point - from issue 37")
        void downwardSloppingSegmentWithAlmostTouchingPointFromIssue37() {
            Segment seg = Segment.fromRing(
                    new SweepPoint(0.523985, 51.281651),
                    new SweepPoint(0.5241, 51.281651000100005),
                    null, op);
            SweepPoint pt = new SweepPoint(0.5239850000000027, 51.281651000000004);
            assertEquals(1, seg.comparePoint(pt));
        }

        @Test
        @DisplayName("avoid splitting loops on near vertical segments - from issue 60-2")
        void avoidSplittingLoopsOnNearVerticalSegmentsFromIssue602() {
            Segment seg = Segment.fromRing(
                    new SweepPoint(-45.3269382, -1.4059341),
                    new SweepPoint(-45.326737413921656, -1.40635),
                    null, op);
            SweepPoint pt = new SweepPoint(-45.326833968900424, -1.40615);
            assertEquals(0, seg.comparePoint(pt));
        }
    }

    @Nested
    @DisplayName("compare segments")
    class CompareSegments {

        @Nested
        @DisplayName("non intersecting")
        class NonIntersecting {

            @Test
            @DisplayName("not in same vertical space")
            void notInSameVerticalSpace() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(4, 3), new SweepPoint(6, 7), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("in same vertical space, earlier is below")
            void inSameVerticalSpaceEarlierIsBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, -4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(6, 7), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("in same vertical space, later is below")
            void inSameVerticalSpaceLaterIsBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, -4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-5, -5), new SweepPoint(6, -7), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("with left points in same vertical line")
            void withLeftPointsInSameVerticalLine() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(0, -1), new SweepPoint(-5, -5), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("with earlier right point directly under later left point")
            void withEarlierRightPointDirectlyUnderLaterLeftPoint() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-5, -5), new SweepPoint(0, -3), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("with eariler right point directly over earlier left point")
            void withEarilerRightPointDirectlyOverEarlierLeftPoint() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-5, 5), new SweepPoint(0, 3), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }
        }

        @Nested
        @DisplayName("intersecting not on endpoint")
        class IntersectingNotOnEndpoint {

            @Test
            @DisplayName("earlier comes up from before & below")
            void earlierComesUpFromBeforeAndBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-1, -5), new SweepPoint(1, 2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("earlier comes up from directly over & below")
            void earlierComesUpFromDirectlyOverAndBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(0, -2), new SweepPoint(3, 2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("earlier comes up from after & below")
            void earlierComesUpFromAfterAndBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(1, -2), new SweepPoint(3, 2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("later comes down from before & above")
            void laterComesDownFromBeforeAndAbove() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-1, 5), new SweepPoint(1, -2), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("later comes up from directly over & above")
            void laterComesUpFromDirectlyOverAndAbove() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(0, 2), new SweepPoint(3, -2), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("later comes up from after & above")
            void laterComesUpFromAfterAndAbove() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(1, 2), new SweepPoint(3, -2), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("with a vertical")
            void withAVertical() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(1, -2), new SweepPoint(1, 2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }
        }

        @Nested
        @DisplayName("intersect but not share on an endpoint")
        class IntersectButNotShareOnAnEndpoint {

            @Test
            @DisplayName("intersect on right")
            void intersectOnRight() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(2, -2), new SweepPoint(6, 2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("intersect on left from above")
            void intersectOnLeftFromAbove() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-2, 2), new SweepPoint(2, -2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("intersect on left from below")
            void intersectOnLeftFromBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-2, -2), new SweepPoint(2, 2), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("intersect on left from vertical")
            void intersectOnLeftFromVertical() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(0, -2), new SweepPoint(0, 2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }
        }

        @Nested
        @DisplayName("share right endpoint")
        class ShareRightEndpoint {

            @Test
            @DisplayName("earlier comes up from before & below")
            void earlierComesUpFromBeforeAndBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-1, -5), new SweepPoint(4, 0), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("earlier comes up from directly over & below")
            void earlierComesUpFromDirectlyOverAndBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(0, -2), new SweepPoint(4, 0), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("earlier comes up from after & below")
            void earlierComesUpFromAfterAndBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(1, -2), new SweepPoint(4, 0), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("later comes down from before & above")
            void laterComesDownFromBeforeAndAbove() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-1, 5), new SweepPoint(4, 0), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("laterjcomes up from directly over & above")
            void laterjcomesUpFromDirectlyOverAndAbove() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(0, 2), new SweepPoint(4, 0), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("later comes up from after & above")
            void laterComesUpFromAfterAndAbove() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 0), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(1, 2), new SweepPoint(4, 0), null, op);
                assertEquals(-1, Segment.compare(seg1, seg2));
                assertEquals(1, Segment.compare(seg2, seg1));
            }
        }

        @Nested
        @DisplayName("share left endpoint but not colinear")
        class ShareLeftEndpointButNotColinear {

            @Test
            @DisplayName("earlier comes up from before & below")
            void earlierComesUpFromBeforeAndBelow() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("one vertical, other not")
            void oneVerticalOtherNot() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(0, 4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("one segment thinks theyre colinear, but the other says no")
            void oneSegmentThinksTheyreColinearButTheOtherSaysNo() {
                Segment seg1 = Segment.fromRing(
                        new SweepPoint(-60.6876, -40.83428174062278),
                        new SweepPoint(-60.6841701, -40.83491),
                        null, op);
                Segment seg2 = Segment.fromRing(
                        new SweepPoint(-60.6876, -40.83428174062278),
                        new SweepPoint(-60.6874, -40.83431837489067),
                        null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }
        }

        @Nested
        @DisplayName("colinear")
        class Colinear {

            @Test
            @DisplayName("partial mutal overlap")
            void partialMutalOverlap() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-1, -1), new SweepPoint(2, 2), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("complete overlap")
            void completeOverlap() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-1, -1), new SweepPoint(5, 5), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("right endpoints match")
            void rightEndpointsMatch() {
                Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), null, op);
                Segment seg2 = Segment.fromRing(new SweepPoint(-1, -1), new SweepPoint(4, 4), null, op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));
            }

            @Test
            @DisplayName("left endpoints match - should be length")
            void leftEndpointsMatchShouldBeLength() {
                Segment seg1 = Segment.fromRing(
                        new SweepPoint(0, 0), new SweepPoint(4, 4), new RingIn(1), op);
                Segment seg2 = Segment.fromRing(
                        new SweepPoint(0, 0), new SweepPoint(3, 3), new RingIn(2), op);
                Segment seg3 = Segment.fromRing(
                        new SweepPoint(0, 0), new SweepPoint(5, 5), new RingIn(3), op);
                assertEquals(1, Segment.compare(seg1, seg2));
                assertEquals(-1, Segment.compare(seg2, seg1));

                assertEquals(-1, Segment.compare(seg2, seg3));
                assertEquals(1, Segment.compare(seg3, seg2));

                assertEquals(-1, Segment.compare(seg1, seg3));
                assertEquals(1, Segment.compare(seg3, seg1));
            }
        }

        @Test
        @DisplayName("exactly equal segments should be sorted by ring id")
        void exactlyEqualSegmentsShouldBeSortedByRingId() {
            Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), new RingIn(1), op);
            Segment seg2 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), new RingIn(2), op);
            assertEquals(-1, Segment.compare(seg1, seg2));
            assertEquals(1, Segment.compare(seg2, seg1));
        }

        @Test
        @DisplayName("exactly equal segments (but not identical) are consistent")
        void exactlyEqualSegmentsButNotIdenticalAreConsistent() {
            Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), new RingIn(1), op);
            Segment seg2 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), new RingIn(1), op);
            int result = Segment.compare(seg1, seg2);
            assertEquals(result, Segment.compare(seg1, seg2));
            assertEquals(result * -1, Segment.compare(seg2, seg1));
        }

        @Test
        @DisplayName("segment consistency - from #60")
        void segmentConsistencyFrom60() {
            Segment seg1 = Segment.fromRing(
                    new SweepPoint(-131.57153657554915, 55.01963125),
                    new SweepPoint(-131.571478, 55.0187174),
                    null, op);
            Segment seg2 = Segment.fromRing(
                    new SweepPoint(-131.57153657554915, 55.01963125),
                    new SweepPoint(-131.57152375603846, 55.01943125),
                    null, op);
            assertEquals(-1, Segment.compare(seg1, seg2));
            assertEquals(1, Segment.compare(seg2, seg1));
        }

        @Test
        @DisplayName("ensure transitive - part of issue 60")
        void ensureTransitivePartOfIssue60() {
            Segment seg2 = Segment.fromRing(
                    new SweepPoint(-10.000000000000018, -9.17),
                    new SweepPoint(-10.000000000000004, -8.79),
                    null, op);
            Segment seg6 = Segment.fromRing(
                    new SweepPoint(-10.000000000000016, 1.44),
                    new SweepPoint(-9, 1.5),
                    null, op);
            Segment seg4 = Segment.fromRing(
                    new SweepPoint(-10.00000000000001, 1.75),
                    new SweepPoint(-9, 1.5),
                    null, op);

            assertEquals(-1, Segment.compare(seg2, seg6));
            assertEquals(-1, Segment.compare(seg6, seg4));
            assertEquals(-1, Segment.compare(seg2, seg4));

            assertEquals(1, Segment.compare(seg6, seg2));
            assertEquals(1, Segment.compare(seg4, seg6));
            assertEquals(1, Segment.compare(seg4, seg2));
        }

        @Test
        @DisplayName("ensure transitive 2 - also part of issue 60")
        void ensureTransitive2AlsoPartOfIssue60() {
            Segment seg1 = Segment.fromRing(
                    new SweepPoint(-10.000000000000002, 1.8181818181818183),
                    new SweepPoint(-9.999999999999996, -3),
                    null, op);
            Segment seg2 = Segment.fromRing(
                    new SweepPoint(-10.000000000000002, 1.8181818181818183),
                    new SweepPoint(0, 0),
                    null, op);
            Segment seg3 = Segment.fromRing(
                    new SweepPoint(-10.000000000000002, 1.8181818181818183),
                    new SweepPoint(-10.000000000000002, 2),
                    null, op);

            assertEquals(-1, Segment.compare(seg1, seg2));
            assertEquals(-1, Segment.compare(seg2, seg3));
            assertEquals(-1, Segment.compare(seg1, seg3));

            assertEquals(1, Segment.compare(seg2, seg1));
            assertEquals(1, Segment.compare(seg3, seg2));
            assertEquals(1, Segment.compare(seg3, seg1));
        }
    }

    @Nested
    @DisplayName("get intersections 2")
    class GetIntersections2 {

        @Test
        @DisplayName("colinear full overlap")
        void colinearFullOverlap() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            assertNull(s1.getIntersection(s2));
            assertNull(s2.getIntersection(s1));
        }

        @Test
        @DisplayName("colinear partial overlap upward slope")
        void colinearPartialOverlapUpwardSlope() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(2, 2), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 3), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(1.0, inter1.x);
            assertEquals(1.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(1.0, inter2.x);
            assertEquals(1.0, inter2.y);
        }

        @Test
        @DisplayName("colinear partial overlap downward slope")
        void colinearPartialOverlapDownwardSlope() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 2), new SweepPoint(2, 0), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(-1, 3), new SweepPoint(1, 1), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(0.0, inter1.x);
            assertEquals(2.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(0.0, inter2.x);
            assertEquals(2.0, inter2.y);
        }

        @Test
        @DisplayName("colinear partial overlap horizontal")
        void colinearPartialOverlapHorizontal() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 1), new SweepPoint(2, 1), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(3, 1), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(1.0, inter1.x);
            assertEquals(1.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(1.0, inter2.x);
            assertEquals(1.0, inter2.y);
        }

        @Test
        @DisplayName("colinear partial overlap vertical")
        void colinearPartialOverlapVertical() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(0, 3), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(0, 2), new SweepPoint(0, 4), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(0.0, inter1.x);
            assertEquals(2.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(0.0, inter2.x);
            assertEquals(2.0, inter2.y);
        }

        @Test
        @DisplayName("colinear endpoint overlap")
        void colinearEndpointOverlap() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(2, 2), new RingIn(1), op);
            assertNull(s1.getIntersection(s2));
            assertNull(s2.getIntersection(s1));
        }

        @Test
        @DisplayName("colinear no overlap")
        void colinearNoOverlap() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(3, 3), new SweepPoint(4, 4), new RingIn(1), op);
            assertNull(s1.getIntersection(s2));
            assertNull(s2.getIntersection(s1));
        }

        @Test
        @DisplayName("parallel no overlap")
        void parallelNoOverlap() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(0, 3), new SweepPoint(1, 4), new RingIn(1), op);
            assertNull(s1.getIntersection(s2));
            assertNull(s2.getIntersection(s1));
        }

        @Test
        @DisplayName("intersect general")
        void intersectGeneral() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(2, 2), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(0, 2), new SweepPoint(2, 0), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(1.0, inter1.x);
            assertEquals(1.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(1.0, inter2.x);
            assertEquals(1.0, inter2.y);
        }

        @Test
        @DisplayName("T-intersect with an endpoint")
        void tIntersectWithAnEndpoint() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(2, 2), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(5, 4), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(1.0, inter1.x);
            assertEquals(1.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(1.0, inter2.x);
            assertEquals(1.0, inter2.y);
        }

        @Test
        @DisplayName("intersect with vertical")
        void intersectWithVertical() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(5, 5), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(3, 0), new SweepPoint(3, 44), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(3.0, inter1.x);
            assertEquals(3.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(3.0, inter2.x);
            assertEquals(3.0, inter2.y);
        }

        @Test
        @DisplayName("intersect with horizontal")
        void intersectWithHorizontal() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(5, 5), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(0, 3), new SweepPoint(23, 3), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(3.0, inter1.x);
            assertEquals(3.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(3.0, inter2.x);
            assertEquals(3.0, inter2.y);
        }

        @Test
        @DisplayName("horizontal and vertical T-intersection")
        void horizontalAndVerticalTIntersection() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(5, 0), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(3, 0), new SweepPoint(3, 5), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(3.0, inter1.x);
            assertEquals(0.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(3.0, inter2.x);
            assertEquals(0.0, inter2.y);
        }

        @Test
        @DisplayName("horizontal and vertical general intersection")
        void horizontalAndVerticalGeneralIntersection() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(5, 0), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(3, -5), new SweepPoint(3, 5), new RingIn(1), op);
            SweepPoint inter1 = s1.getIntersection(s2);
            assertEquals(3.0, inter1.x);
            assertEquals(0.0, inter1.y);
            SweepPoint inter2 = s2.getIntersection(s1);
            assertEquals(3.0, inter2.x);
            assertEquals(0.0, inter2.y);
        }

        @Test
        @DisplayName("no intersection not even close")
        void noIntersectionNotEvenClose() {
            Segment s1 = Segment.fromRing(new SweepPoint(1000, 10002), new SweepPoint(2000, 20002), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(-234, -123), new SweepPoint(-12, -23), new RingIn(1), op);
            assertNull(s1.getIntersection(s2));
            assertNull(s2.getIntersection(s1));
        }

        @Test
        @DisplayName("no intersection kinda close")
        void noIntersectionKindaClose() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(0, 10), new SweepPoint(10, 0), new RingIn(1), op);
            assertNull(s1.getIntersection(s2));
            assertNull(s2.getIntersection(s1));
        }

        @Test
        @DisplayName("no intersection with vertical touching bbox")
        void noIntersectionWithVerticalTouchingBbox() {
            Segment s1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), new RingIn(1), op);
            Segment s2 = Segment.fromRing(new SweepPoint(2, -5), new SweepPoint(2, 0), new RingIn(1), op);
            assertNull(s1.getIntersection(s2));
            assertNull(s2.getIntersection(s1));
        }

        @Test
        @DisplayName("shared point 1 (endpoint)")
        void sharedPoint1Endpoint() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0, 1), new SweepPoint(0, 0), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("shared point 2 (endpoint)")
        void sharedPoint2Endpoint() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0, 1), new SweepPoint(1, 1), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("T-crossing left endpoint")
        void tCrossingLeftEndpoint() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0.5, 0.5), new SweepPoint(1, 0), new RingIn(1), op);
            SweepPoint inter1 = a.getIntersection(b);
            assertEquals(0.5, inter1.x);
            assertEquals(0.5, inter1.y);
            SweepPoint inter2 = b.getIntersection(a);
            assertEquals(0.5, inter2.x);
            assertEquals(0.5, inter2.y);
        }

        @Test
        @DisplayName("T-crossing right endpoint")
        void tCrossingRightEndpoint() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0, 1), new SweepPoint(0.5, 0.5), new RingIn(1), op);
            SweepPoint inter1 = a.getIntersection(b);
            assertEquals(0.5, inter1.x);
            assertEquals(0.5, inter1.y);
            SweepPoint inter2 = b.getIntersection(a);
            assertEquals(0.5, inter2.x);
            assertEquals(0.5, inter2.y);
        }

        @Test
        @DisplayName("full overlap")
        void fullOverlap() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(10, 10), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(5, 5), new RingIn(1), op);
            SweepPoint inter1 = a.getIntersection(b);
            assertEquals(1.0, inter1.x);
            assertEquals(1.0, inter1.y);
            SweepPoint inter2 = b.getIntersection(a);
            assertEquals(1.0, inter2.x);
            assertEquals(1.0, inter2.y);
        }

        @Test
        @DisplayName("shared point + overlap")
        void sharedPointPlusOverlap() {
            Segment a = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(10, 10), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(5, 5), new RingIn(1), op);
            SweepPoint inter1 = a.getIntersection(b);
            assertEquals(5.0, inter1.x);
            assertEquals(5.0, inter1.y);
            SweepPoint inter2 = b.getIntersection(a);
            assertEquals(5.0, inter2.x);
            assertEquals(5.0, inter2.y);
        }

        @Test
        @DisplayName("mutual overlap")
        void mutualOverlap() {
            Segment a = Segment.fromRing(new SweepPoint(3, 3), new SweepPoint(10, 10), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(5, 5), new RingIn(1), op);
            SweepPoint inter1 = a.getIntersection(b);
            assertEquals(3.0, inter1.x);
            assertEquals(3.0, inter1.y);
            SweepPoint inter2 = b.getIntersection(a);
            assertEquals(3.0, inter2.x);
            assertEquals(3.0, inter2.y);
        }

        @Test
        @DisplayName("full overlap")
        void fullOverlap2() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("full overlap, orientation")
        void fullOverlapOrientation() {
            Segment a = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(0, 0), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("colinear, shared point")
        void colinearSharedPoint() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(2, 2), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("colinear, shared other point")
        void colinearSharedOtherPoint() {
            Segment a = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(0, 0), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(2, 2), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("colinear, one encloses other")
        void colinearOneEnclosesOther() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(2, 2), new RingIn(1), op);
            SweepPoint inter1 = a.getIntersection(b);
            assertEquals(1.0, inter1.x);
            assertEquals(1.0, inter1.y);
            SweepPoint inter2 = b.getIntersection(a);
            assertEquals(1.0, inter2.x);
            assertEquals(1.0, inter2.y);
        }

        @Test
        @DisplayName("colinear, one encloses other 2")
        void colinearOneEnclosesOther2() {
            Segment a = Segment.fromRing(new SweepPoint(4, 0), new SweepPoint(0, 4), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(3, 1), new SweepPoint(1, 3), new RingIn(1), op);
            SweepPoint inter1 = a.getIntersection(b);
            assertEquals(1.0, inter1.x);
            assertEquals(3.0, inter1.y);
            SweepPoint inter2 = b.getIntersection(a);
            assertEquals(1.0, inter2.x);
            assertEquals(3.0, inter2.y);
        }

        @Test
        @DisplayName("colinear, no overlap")
        void colinearNoOverlap2() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(2, 2), new SweepPoint(4, 4), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("parallel")
        void parallel() {
            Segment a = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0, -1), new SweepPoint(1, 0), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("parallel, orientation")
        void parallelOrientation() {
            Segment a = Segment.fromRing(new SweepPoint(1, 1), new SweepPoint(0, 0), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0, -1), new SweepPoint(1, 0), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("parallel, position")
        void parallelPosition() {
            Segment a = Segment.fromRing(new SweepPoint(0, -1), new SweepPoint(1, 0), new RingIn(1), op);
            Segment b = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), new RingIn(1), op);
            assertNull(a.getIntersection(b));
            assertNull(b.getIntersection(a));
        }

        @Test
        @DisplayName("endpoint intersections should be consistent - issue 60")
        void endpointIntersectionsShouldBeConsistentIssue60() {
            double x = -91.41360941065206;
            double y = 29.53135;
            Segment segA1 = Segment.fromRing(
                    new SweepPoint(x, y),
                    new SweepPoint(-91.4134943, 29.5310677),
                    new RingIn(1), op);
            Segment segA2 = Segment.fromRing(
                    new SweepPoint(x, y),
                    new SweepPoint(-91.413, 29.5315),
                    new RingIn(1), op);
            Segment segB = Segment.fromRing(
                    new SweepPoint(-91.4137213, 29.5316244),
                    new SweepPoint(-91.41352785864918, 29.53115),
                    new RingIn(1), op);

            SweepPoint r1 = segA1.getIntersection(segB);
            assertEquals(x, r1.x);
            assertEquals(y, r1.y);
            SweepPoint r2 = segA2.getIntersection(segB);
            assertEquals(x, r2.x);
            assertEquals(y, r2.y);
            SweepPoint r3 = segB.getIntersection(segA1);
            assertEquals(x, r3.x);
            assertEquals(y, r3.y);
            SweepPoint r4 = segB.getIntersection(segA2);
            assertEquals(x, r4.x);
            assertEquals(y, r4.y);
        }

        @Test
        @DisplayName("endpoint intersection takes priority - issue 60-5")
        void endpointIntersectionTakesPriorityIssue605() {
            double endX = 55.31;
            double endY = -0.23544126113;
            Segment segA = Segment.fromRing(
                    new SweepPoint(18.60315316392773, 10.491431056669754),
                    new SweepPoint(endX, endY),
                    new RingIn(1), op);
            Segment segB = Segment.fromRing(
                    new SweepPoint(-32.42, 55.26),
                    new SweepPoint(endX, endY),
                    new RingIn(1), op);

            assertNull(segA.getIntersection(segB));
            assertNull(segB.getIntersection(segA));
        }

        @Test
        @DisplayName("endpoint intersection between very short and very vertical segment")
        void endpointIntersectionBetweenVeryShortAndVeryVerticalSegment() {
            Segment segA = Segment.fromRing(
                    new SweepPoint(-10.000000000000004, 0),
                    new SweepPoint(-9.999999999999995, 0),
                    new RingIn(1), op);
            Segment segB = Segment.fromRing(
                    new SweepPoint(-10.000000000000004, 0),
                    new SweepPoint(-9.999999999999995, 1000),
                    new RingIn(1), op);
            assertNull(segA.getIntersection(segB));
            assertNull(segB.getIntersection(segA));
        }

        @Test
        @DisplayName("avoid intersection - issue 79")
        void avoidIntersectionIssue79() {
            Segment segA = Segment.fromRing(
                    new SweepPoint(145.854148864746, -41.99816840491791),
                    new SweepPoint(145.85421323776, -41.9981723915721),
                    new RingIn(1), op);
            Segment segB = Segment.fromRing(
                    new SweepPoint(145.854148864746, -41.998168404918),
                    new SweepPoint(145.8543, -41.9982),
                    new RingIn(1), op);
            assertNull(segA.getIntersection(segB));
            assertNull(segB.getIntersection(segA));
        }
    }
}
