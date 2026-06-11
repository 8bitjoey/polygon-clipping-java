package com.bytejoey.polygonclip.sweep;

import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.OpType;
import com.bytejoey.polygonclip.input.RingIn;
import com.bytejoey.polygonclip.output.RingOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Transliteration of upstream/test/sweep-event.test.js — all four describe blocks:
 * sweep event compare (:6), constructor (:117), sweep event link (:127),
 * sweep event get leftmost comparator (:189).
 *
 * Mapping notes (same conventions as SegmentTest):
 * - JS point literals {x, y} → new SweepPoint(x, y); duck rings {id: n} → new RingIn(n).
 * - JS `new SweepEvent({x, y})` with isLeft omitted (undefined, falsy) → isLeft false.
 * - JS `Segment.fromRing(p1, p2)` with the ring omitted → null ring.
 * - JS sloppy `new Segment(s1, s3, {id: 1})` (duck ring passed where the rings ARRAY
 *   goes, windings omitted) → empty rings + windings lists: the duck object's `.length`
 *   is undefined, so upstream's consume() merge loop never iterates; empty lists
 *   reproduce that effect without touching production code.
 * - JS `new Segment(e, se)` with rings and windings omitted → null, null.
 * - JS duck segments ({isInResult: () => b, ringOut: o}) → real fromRing Segments with
 *   isInResultMemo set (public field) and ringOut assigned.
 * - `toEqual([...])` on event lists → assertEquals(List.of(...), actual) — element
 *   equality is identity (SweepEvent does not override equals).
 *
 * Strengthened upstream assertion bugs (bare booleans inside expect() with no matcher,
 * which always pass in jest): sweep-event.test.js:97/:99 and :122-123 are ported as the
 * real assertions intended — see "events are linked as side effect" and the constructor
 * test below.
 */
class SweepEventTest {

    private Operation op;

    @BeforeEach
    void resetIds() {
        op = new Operation(OpType.UNION);
    }

    @Nested
    @DisplayName("sweep event compare")
    class SweepEventCompare {

        @Test
        @DisplayName("favor earlier x in point")
        void favorEarlierXInPoint() {
            SweepEvent s1 = new SweepEvent(new SweepPoint(-5, 4), false);
            SweepEvent s2 = new SweepEvent(new SweepPoint(5, 1), false);
            assertEquals(-1, SweepEvent.compare(s1, s2));
            assertEquals(1, SweepEvent.compare(s2, s1));
        }

        @Test
        @DisplayName("then favor earlier y in point")
        void thenFavorEarlierYInPoint() {
            SweepEvent s1 = new SweepEvent(new SweepPoint(5, -4), false);
            SweepEvent s2 = new SweepEvent(new SweepPoint(5, 4), false);
            assertEquals(-1, SweepEvent.compare(s1, s2));
            assertEquals(1, SweepEvent.compare(s2, s1));
        }

        @Test
        @DisplayName("then favor right events over left")
        void thenFavorRightEventsOverLeft() {
            Segment seg1 = Segment.fromRing(new SweepPoint(5, 4), new SweepPoint(3, 2), null, op);
            Segment seg2 = Segment.fromRing(new SweepPoint(5, 4), new SweepPoint(6, 5), null, op);
            assertEquals(-1, SweepEvent.compare(seg1.rightSE, seg2.leftSE));
            assertEquals(1, SweepEvent.compare(seg2.leftSE, seg1.rightSE));
        }

        @Test
        @DisplayName("then favor non-vertical segments for left events")
        void thenFavorNonVerticalSegmentsForLeftEvents() {
            Segment seg1 = Segment.fromRing(new SweepPoint(3, 2), new SweepPoint(3, 4), null, op);
            Segment seg2 = Segment.fromRing(new SweepPoint(3, 2), new SweepPoint(5, 4), null, op);
            assertEquals(-1, SweepEvent.compare(seg1.leftSE, seg2.rightSE));
            assertEquals(1, SweepEvent.compare(seg2.rightSE, seg1.leftSE));
        }

        @Test
        @DisplayName("then favor vertical segments for right events")
        void thenFavorVerticalSegmentsForRightEvents() {
            Segment seg1 = Segment.fromRing(new SweepPoint(3, 4), new SweepPoint(3, 2), null, op);
            Segment seg2 = Segment.fromRing(new SweepPoint(3, 4), new SweepPoint(1, 2), null, op);
            assertEquals(-1, SweepEvent.compare(seg1.leftSE, seg2.rightSE));
            assertEquals(1, SweepEvent.compare(seg2.rightSE, seg1.leftSE));
        }

        @Test
        @DisplayName("then favor lower segment")
        void thenFavorLowerSegment() {
            Segment seg1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), null, op);
            Segment seg2 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(5, 6), null, op);
            assertEquals(-1, SweepEvent.compare(seg1.leftSE, seg2.rightSE));
            assertEquals(1, SweepEvent.compare(seg2.rightSE, seg1.leftSE));
        }

        // Sometimes from one segment's perspective it appears colinear
        // to another segment, but from that other segment's perspective
        // they aren't colinear. This happens because a longer segment
        // is able to better determine what is and is not colinear.
        @Test
        @DisplayName("and favor barely lower segment")
        void andFavorBarelyLowerSegment() {
            Segment seg1 = Segment.fromRing(
                    new SweepPoint(-75.725, 45.357),
                    new SweepPoint(-75.72484615384616, 45.35723076923077),
                    null, op);
            Segment seg2 = Segment.fromRing(
                    new SweepPoint(-75.725, 45.357),
                    new SweepPoint(-75.723, 45.36),
                    null, op);
            assertEquals(1, SweepEvent.compare(seg1.leftSE, seg2.leftSE));
            assertEquals(-1, SweepEvent.compare(seg2.leftSE, seg1.leftSE));
        }

        @Test
        @DisplayName("then favor lower ring id")
        void thenFavorLowerRingId() {
            Segment seg1 =
                    Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(4, 4), new RingIn(1), op);
            Segment seg2 =
                    Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(5, 5), new RingIn(2), op);
            assertEquals(-1, SweepEvent.compare(seg1.leftSE, seg2.leftSE));
            assertEquals(1, SweepEvent.compare(seg2.leftSE, seg1.leftSE));
        }

        @Test
        @DisplayName("identical equal")
        void identicalEqual() {
            SweepEvent s1 = new SweepEvent(new SweepPoint(0, 0), false);
            SweepEvent s3 = new SweepEvent(new SweepPoint(3, 3), false);
            new Segment(s1, s3, new ArrayList<>(), new ArrayList<>(), op);
            new Segment(s1, s3, new ArrayList<>(), new ArrayList<>(), op);
            assertEquals(0, SweepEvent.compare(s1, s1));
        }

        @Test
        @DisplayName("totally equal but not identical events are consistent")
        void totallyEqualButNotIdenticalEventsAreConsistent() {
            SweepEvent s1 = new SweepEvent(new SweepPoint(0, 0), false);
            SweepEvent s2 = new SweepEvent(new SweepPoint(0, 0), false);
            SweepEvent s3 = new SweepEvent(new SweepPoint(3, 3), false);
            // shared right event s3: linking s1+s2 fires checkForConsuming -> real consume()
            new Segment(s1, s3, new ArrayList<>(), new ArrayList<>(), op);
            new Segment(s2, s3, new ArrayList<>(), new ArrayList<>(), op);
            int result = SweepEvent.compare(s1, s2);
            assertEquals(result, SweepEvent.compare(s1, s2));
            assertEquals(-result, SweepEvent.compare(s2, s1));
        }

        @Test
        @DisplayName("events are linked as side effect")
        void eventsAreLinkedAsSideEffect() {
            SweepEvent s1 = new SweepEvent(new SweepPoint(0, 0), false);
            SweepEvent s2 = new SweepEvent(new SweepPoint(0, 0), false);
            new Segment(s1, new SweepEvent(new SweepPoint(2, 2), false), null, null, op);
            new Segment(s2, new SweepEvent(new SweepPoint(3, 4), false), null, null, op);
            // upstream's expect(s1.point !== s2.point) / expect(s1.point === s2.point)
            // (sweep-event.test.js:97,:99) carry no matcher — always-pass assertion bugs.
            // Ported as the real pre/post-state assertions intended.
            assertNotSame(s1.point, s2.point);
            SweepEvent.compare(s1, s2);
            assertSame(s1.point, s2.point);
        }

        @Test
        @DisplayName("consistency edge case")
        void consistencyEdgeCase() {
            // harvested from https://github.com/mfogel/polygon-clipping/issues/62
            Segment seg1 = Segment.fromRing(
                    new SweepPoint(-71.0390933353125, 41.504475),
                    new SweepPoint(-71.0389879, 41.5037842),
                    null, op);
            Segment seg2 = Segment.fromRing(
                    new SweepPoint(-71.0390933353125, 41.504475),
                    new SweepPoint(-71.03906280974431, 41.504275),
                    null, op);
            assertEquals(-1, SweepEvent.compare(seg1.leftSE, seg2.leftSE));
            assertEquals(1, SweepEvent.compare(seg2.leftSE, seg1.leftSE));
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("events created from same point are already linked")
        void eventsCreatedFromSamePointAreAlreadyLinked() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepEvent s1 = new SweepEvent(p1, false);
            SweepEvent s2 = new SweepEvent(p1, false);
            // upstream's expect(s1.point === p1) / expect(s1.point.events === s2.point.events)
            // (sweep-event.test.js:122-123) carry no matcher — always-pass assertion bugs.
            // Ported as the real identity assertions intended.
            assertSame(p1, s1.point);
            assertSame(s1.point.events, s2.point.events);
        }
    }

    @Nested
    @DisplayName("sweep event link")
    class SweepEventLink {

        @Test
        @DisplayName("no linked events")
        void noLinkedEvents() {
            SweepEvent s1 = new SweepEvent(new SweepPoint(0, 0), false);
            assertEquals(List.of(s1), s1.point.events);
            assertEquals(List.of(), s1.getAvailableLinkedEvents());
        }

        @Test
        @DisplayName("link events already linked with others")
        void linkEventsAlreadyLinkedWithOthers() {
            SweepPoint p1 = new SweepPoint(1, 2);
            SweepPoint p2 = new SweepPoint(1, 2);
            SweepEvent se1 = new SweepEvent(p1, false);
            SweepEvent se2 = new SweepEvent(p1, false);
            SweepEvent se3 = new SweepEvent(p2, false);
            SweepEvent se4 = new SweepEvent(p2, false);
            new Segment(se1, new SweepEvent(new SweepPoint(5, 5), false), null, null, op);
            new Segment(se2, new SweepEvent(new SweepPoint(6, 6), false), null, null, op);
            new Segment(se3, new SweepEvent(new SweepPoint(7, 7), false), null, null, op);
            new Segment(se4, new SweepEvent(new SweepPoint(8, 8), false), null, null, op);
            se1.link(se3);
            assertEquals(4, se1.point.events.size());
            assertSame(se1.point, se2.point);
            assertSame(se1.point, se3.point);
            assertSame(se1.point, se4.point);
        }

        @Test
        @DisplayName("same event twice")
        void sameEventTwice() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepEvent s1 = new SweepEvent(p1, false);
            SweepEvent s2 = new SweepEvent(p1, false);
            assertThrows(IllegalStateException.class, () -> s2.link(s1));
            assertThrows(IllegalStateException.class, () -> s1.link(s2));
        }

        @Test
        @DisplayName("unavailable linked events do not show up")
        void unavailableLinkedEventsDoNotShowUp() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepEvent se = new SweepEvent(p1, false);
            SweepEvent seAlreadyProcessed = new SweepEvent(p1, false);
            SweepEvent seNotInResult = new SweepEvent(p1, false);
            // JS duck segment {isInResult: () => true, ringOut: {}} → real Segment with
            // the isInResultMemo seam and a non-null ringOut
            Segment processed =
                    Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), null, op);
            processed.isInResultMemo = true;
            seAlreadyProcessed.segment = processed;
            processed.ringOut = new RingOut(List.of(seAlreadyProcessed));
            // JS duck segment {isInResult: () => false, ringOut: null}
            Segment notInResult =
                    Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), null, op);
            notInResult.isInResultMemo = false;
            notInResult.ringOut = null;
            seNotInResult.segment = notInResult;
            assertEquals(List.of(), se.getAvailableLinkedEvents());
        }

        @Test
        @DisplayName("available linked events show up")
        void availableLinkedEventsShowUp() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepEvent se = new SweepEvent(p1, false);
            SweepEvent seOkay = new SweepEvent(p1, false);
            // JS duck segment {isInResult: () => true, ringOut: null}
            Segment okay = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), null, op);
            okay.isInResultMemo = true;
            okay.ringOut = null;
            seOkay.segment = okay;
            assertEquals(List.of(seOkay), se.getAvailableLinkedEvents());
        }

        @Test
        @DisplayName("link goes both ways")
        void linkGoesBothWays() {
            SweepPoint p1 = new SweepPoint(0, 0);
            SweepEvent seOkay1 = new SweepEvent(p1, false);
            SweepEvent seOkay2 = new SweepEvent(p1, false);
            Segment okay1 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), null, op);
            okay1.isInResultMemo = true;
            okay1.ringOut = null;
            seOkay1.segment = okay1;
            Segment okay2 = Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(1, 1), null, op);
            okay2.isInResultMemo = true;
            okay2.ringOut = null;
            seOkay2.segment = okay2;
            assertEquals(List.of(seOkay2), seOkay1.getAvailableLinkedEvents());
            assertEquals(List.of(seOkay1), seOkay2.getAvailableLinkedEvents());
        }
    }

    @Nested
    @DisplayName("sweep event get leftmost comparator")
    class SweepEventGetLeftmostComparator {

        @Test
        @DisplayName("after a segment straight to the right")
        void afterASegmentStraightToTheRight() {
            SweepEvent prevEvent = new SweepEvent(new SweepPoint(0, 0), false);
            SweepEvent event = new SweepEvent(new SweepPoint(1, 0), false);
            Comparator<SweepEvent> comparator = event.getLeftmostComparator(prevEvent);

            SweepEvent e1 = new SweepEvent(new SweepPoint(1, 0), false);
            new Segment(e1, new SweepEvent(new SweepPoint(0, 1), false), null, null, op);

            SweepEvent e2 = new SweepEvent(new SweepPoint(1, 0), false);
            new Segment(e2, new SweepEvent(new SweepPoint(1, 1), false), null, null, op);

            SweepEvent e3 = new SweepEvent(new SweepPoint(1, 0), false);
            new Segment(e3, new SweepEvent(new SweepPoint(2, 0), false), null, null, op);

            SweepEvent e4 = new SweepEvent(new SweepPoint(1, 0), false);
            new Segment(e4, new SweepEvent(new SweepPoint(1, -1), false), null, null, op);

            SweepEvent e5 = new SweepEvent(new SweepPoint(1, 0), false);
            new Segment(e5, new SweepEvent(new SweepPoint(0, -1), false), null, null, op);

            assertEquals(-1, comparator.compare(e1, e2));
            assertEquals(-1, comparator.compare(e2, e3));
            assertEquals(-1, comparator.compare(e3, e4));
            assertEquals(-1, comparator.compare(e4, e5));

            assertEquals(1, comparator.compare(e2, e1));
            assertEquals(1, comparator.compare(e3, e2));
            assertEquals(1, comparator.compare(e4, e3));
            assertEquals(1, comparator.compare(e5, e4));

            assertEquals(-1, comparator.compare(e1, e3));
            assertEquals(-1, comparator.compare(e1, e4));
            assertEquals(-1, comparator.compare(e1, e5));

            assertEquals(0, comparator.compare(e1, e1));
        }

        @Test
        @DisplayName("after a down and to the left")
        void afterADownAndToTheLeft() {
            SweepEvent prevEvent = new SweepEvent(new SweepPoint(1, 1), false);
            SweepEvent event = new SweepEvent(new SweepPoint(0, 0), false);
            Comparator<SweepEvent> comparator = event.getLeftmostComparator(prevEvent);

            SweepEvent e1 = new SweepEvent(new SweepPoint(0, 0), false);
            new Segment(e1, new SweepEvent(new SweepPoint(0, 1), false), null, null, op);

            SweepEvent e2 = new SweepEvent(new SweepPoint(0, 0), false);
            new Segment(e2, new SweepEvent(new SweepPoint(1, 0), false), null, null, op);

            SweepEvent e3 = new SweepEvent(new SweepPoint(0, 0), false);
            new Segment(e3, new SweepEvent(new SweepPoint(0, -1), false), null, null, op);

            SweepEvent e4 = new SweepEvent(new SweepPoint(0, 0), false);
            new Segment(e4, new SweepEvent(new SweepPoint(-1, 0), false), null, null, op);

            assertEquals(1, comparator.compare(e1, e2));
            assertEquals(1, comparator.compare(e1, e3));
            assertEquals(1, comparator.compare(e1, e4));

            assertEquals(-1, comparator.compare(e2, e1));
            assertEquals(-1, comparator.compare(e2, e3));
            assertEquals(-1, comparator.compare(e2, e4));

            assertEquals(-1, comparator.compare(e3, e1));
            assertEquals(1, comparator.compare(e3, e2));
            assertEquals(-1, comparator.compare(e3, e4));

            assertEquals(-1, comparator.compare(e4, e1));
            assertEquals(1, comparator.compare(e4, e2));
            assertEquals(1, comparator.compare(e4, e3));
        }
    }
}
