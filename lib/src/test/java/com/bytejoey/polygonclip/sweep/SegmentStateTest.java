package com.bytejoey.polygonclip.sweep;

import com.bytejoey.polygonclip.OpType;
import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.input.MultiPolyIn;
import com.bytejoey.polygonclip.input.PolyIn;
import com.bytejoey.polygonclip.input.RingIn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fresh design tests (no upstream equivalent — upstream's state chain is covered only
 * by the end-to-end suite). Each assertion mirrors a specific branch of
 * segment.js:444-578; segments are wired manually (prev links, rings) the way the
 * sweep would leave them.
 */
class SegmentStateTest {

  private static Segment seg(Operation op, double x1, double y1, double x2, double y2, RingIn ring) {
    return Segment.fromRing(new SweepPoint(x1, y1), new SweepPoint(x2, y2), ring, op);
  }

  /** An exterior ring belonging to a fresh poly in a fresh multipoly. */
  private static RingIn exteriorRing(MultiPolyIn mp) {
    return new RingIn(new PolyIn(mp), true);
  }

  @Nested
  class BeforeState {
    @Test
    @DisplayName("no prev: empty state (segment.js:455-460)")
    void noPrev() {
      Operation op = new Operation(OpType.UNION);
      Segment s = seg(op, 0, 0, 1, 1, exteriorRing(new MultiPolyIn(true)));
      Segment.State st = s.beforeState();
      assertTrue(st.rings.isEmpty());
      assertTrue(st.windings.isEmpty());
      assertTrue(st.multiPolys.isEmpty());
    }

    @Test
    @DisplayName("with prev: prev's afterState (segment.js:461-463)")
    void withPrev() {
      Operation op = new Operation(OpType.UNION);
      RingIn ring = exteriorRing(new MultiPolyIn(true));
      Segment below = seg(op, 0, 0, 10, 0, ring);
      Segment above = seg(op, 0, 5, 10, 5, ring);
      above.prev = below;
      assertSame(below.afterState(), above.beforeState());
    }

    @Test
    @DisplayName("consumed prev: consumer's afterState (segment.js:462)")
    void consumedPrev() {
      Operation op = new Operation(OpType.UNION);
      RingIn ring = exteriorRing(new MultiPolyIn(true));
      SweepPoint p1 = new SweepPoint(0, 0);
      SweepPoint p2 = new SweepPoint(10, 0);
      Segment consumer = Segment.fromRing(p1, p2, ring, op);
      Segment consumee = Segment.fromRing(p1, p2, ring, op);
      consumer.consume(consumee);
      Segment above = seg(op, 0, 5, 10, 5, ring);
      above.prev = consumee;
      assertSame(consumer.afterState(), above.beforeState());
    }
  }

  @Nested
  class AfterState {
    @Test
    @DisplayName("merges own rings into beforeState copy (segment.js:482-490)")
    void mergesRings() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyIn mp = new MultiPolyIn(true);
      RingIn ring = exteriorRing(mp);
      Segment below = seg(op, 0, 0, 10, 0, ring);
      Segment above = seg(op, 0, 5, 10, 5, ring);
      above.prev = below;
      // below (winding +1) then above (winding +1) on the same ring: winding sums to 2
      Segment.State st = above.afterState();
      assertEquals(List.of(ring), st.rings);
      assertEquals(List.of(2), st.windings);
      // beforeState of `above` must not have been mutated by the merge
      assertEquals(List.of(1), above.beforeState().windings);
    }

    @Test
    @DisplayName("winding zero drops the poly — non-zero rule (segment.js:496)")
    void nonZeroRule() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyIn mp = new MultiPolyIn(true);
      RingIn ring = exteriorRing(mp);
      SweepPoint p1 = new SweepPoint(0, 0);
      SweepPoint p2 = new SweepPoint(10, 0);
      // fromRing assigns winding -1 when points arrive right-to-left
      Segment below = Segment.fromRing(p1, p2, ring, op); // winding +1
      Segment above = Segment.fromRing(p2, p1, ring, op); // winding -1
      above.prev = below;
      Segment.State st = above.afterState();
      assertEquals(List.of(0), st.windings);
      assertTrue(st.multiPolys.isEmpty());
    }

    @Test
    @DisplayName("exterior ring puts its poly's multipoly in state (segment.js:500,509-511)")
    void exteriorAddsPoly() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyIn mp = new MultiPolyIn(true);
      RingIn ring = exteriorRing(mp);
      Segment s = seg(op, 0, 0, 10, 0, ring);
      assertEquals(List.of(mp), s.afterState().multiPolys);
    }

    @Test
    @DisplayName("interior ring removes its poly (segment.js:501-505)")
    void interiorRemovesPoly() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyIn mp = new MultiPolyIn(true);
      PolyIn poly = new PolyIn(mp);
      RingIn exterior = new RingIn(poly, true);
      RingIn interior = new RingIn(poly, false);
      Segment below = seg(op, 0, 0, 10, 0, exterior);
      Segment above = seg(op, 0, 5, 10, 5, interior);
      above.prev = below;
      // inside the hole: the poly is excluded, no multipolys
      assertTrue(above.afterState().multiPolys.isEmpty());
    }
  }

  @Nested
  class PrevInResult {
    @Test
    @DisplayName("no prev: null (segment.js:447)")
    void noPrev() {
      Operation op = new Operation(OpType.UNION);
      Segment s = seg(op, 0, 0, 1, 1, exteriorRing(new MultiPolyIn(true)));
      assertNull(s.prevInResult());
    }

    @Test
    @DisplayName("prev in result: prev (segment.js:448)")
    void directHit() {
      Operation op = new Operation(OpType.UNION);
      RingIn ring = exteriorRing(new MultiPolyIn(true));
      Segment a = seg(op, 0, 0, 10, 0, ring);
      Segment b = seg(op, 0, 5, 10, 5, ring);
      b.prev = a;
      a.isInResultMemo = true; // pin via the upstream memo (segment.js:522)
      assertSame(a, b.prevInResult());
    }

    @Test
    @DisplayName("skips non-result prevs, memoizes the walk (segment.js:449)")
    void skipsNonResult() {
      Operation op = new Operation(OpType.UNION);
      RingIn ring = exteriorRing(new MultiPolyIn(true));
      Segment a = seg(op, 0, 0, 10, 0, ring);
      Segment b = seg(op, 0, 5, 10, 5, ring);
      Segment c = seg(op, 0, 9, 10, 9, ring);
      c.prev = b;
      b.prev = a;
      a.isInResultMemo = true;
      b.isInResultMemo = false;
      assertSame(a, c.prevInResult());
      assertSame(a, b.prevInResult()); // intermediate node was memoized by the walk
    }
  }

  @Nested
  class IsInResult {
    private Segment loneSegment(Operation op) {
      // a single exterior-ring segment: mpsBefore = [], mpsAfter = [mp]
      return seg(op, 0, 0, 10, 0, exteriorRing(new MultiPolyIn(true)));
    }

    @Test
    @DisplayName("consumed segments are never in the result (segment.js:520)")
    void consumedNeverInResult() {
      Operation op = new Operation(OpType.UNION);
      RingIn ring = exteriorRing(new MultiPolyIn(true));
      SweepPoint p1 = new SweepPoint(0, 0);
      SweepPoint p2 = new SweepPoint(10, 0);
      Segment consumer = Segment.fromRing(p1, p2, ring, op);
      Segment consumee = Segment.fromRing(p1, p2, ring, op);
      consumer.consume(consumee);
      assertFalse(consumee.isInResult());
    }

    @Test
    @DisplayName("union: in iff exactly one side has no multipolys (segment.js:528-535)")
    void union() {
      Operation op = new Operation(OpType.UNION);
      op.numMultiPolys = 1;
      assertTrue(loneSegment(op).isInResult());
    }

    @Test
    @DisplayName("intersection: in iff most == numMultiPolys and least < most (segment.js:538-553)")
    void intersection() {
      Operation op = new Operation(OpType.INTERSECTION);
      op.numMultiPolys = 2;
      // one multipoly on the after side, none before: most=1 != numMultiPolys=2
      assertFalse(loneSegment(op).isInResult());
      Operation op1 = new Operation(OpType.INTERSECTION);
      op1.numMultiPolys = 1;
      assertTrue(loneSegment(op1).isInResult());
    }

    @Test
    @DisplayName("xor: in iff multipoly-count difference is odd (segment.js:556-562)")
    void xor() {
      Operation op = new Operation(OpType.XOR);
      op.numMultiPolys = 1;
      assertTrue(loneSegment(op).isInResult());
    }

    @Test
    @DisplayName("difference: in iff exactly one side is just-the-subject (segment.js:565-570)")
    void difference() {
      Operation op = new Operation(OpType.DIFFERENCE);
      op.numMultiPolys = 2;
      // after side = [subject mp], before side = []: just-subject on exactly one side
      Segment subjectSeg = seg(op, 0, 0, 10, 0, exteriorRing(new MultiPolyIn(true)));
      assertTrue(subjectSeg.isInResult());
      // a clipping segment alone: after side = [non-subject mp] -> not just-subject
      Operation op2 = new Operation(OpType.DIFFERENCE);
      op2.numMultiPolys = 2;
      Segment clipSeg = seg(op2, 0, 0, 10, 0, exteriorRing(new MultiPolyIn(false)));
      assertFalse(clipSeg.isInResult());
    }
  }
}
