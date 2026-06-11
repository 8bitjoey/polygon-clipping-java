package com.bytejoey.polygonclip.output;

import com.bytejoey.polygonclip.OpType;
import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.input.RingIn;
import com.bytejoey.polygonclip.sweep.Segment;
import com.bytejoey.polygonclip.sweep.SweepPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transliteration of upstream/test/geom-out.test.js — 18 tests.
 *
 * <p>The "ring" describe-block (13 tests) is ported 1:1 using real Segment/SweepPoint objects.
 * The "poly" (3 tests) and "multipoly" (2 tests) blocks are adapted: upstream used duck objects
 * with integer getGeom() markers (e.g. {@code {poly: null, getGeom: () => 1}}). The typed port
 * uses REAL rings/polys built by the {@code realRing} and {@code collinearRing} helpers instead.
 * The asserted logic is identical: ordering, null propagation, poly back-refs.
 */
class GeomOutTest {

  // --- helpers ---

  private static void assertGeom2d(double[][] expected, double[][] actual) {
    if (expected == null) {
      assertNull(actual);
      return;
    }
    assertNotNull(actual);
    assertEquals(expected.length, actual.length, "row count");
    for (int i = 0; i < expected.length; i++) {
      assertArrayEquals(expected[i], actual[i], 0.0, "row " + i);
    }
  }

  private static RingOut realRing(Operation op, double ox, double oy) {
    SweepPoint p1 = new SweepPoint(ox, oy);
    SweepPoint p2 = new SweepPoint(ox + 1, oy + 1);
    SweepPoint p3 = new SweepPoint(ox, oy + 1);
    Segment s1 = Segment.fromRing(p1, p2, new RingIn(1), op);
    Segment s2 = Segment.fromRing(p2, p3, new RingIn(1), op);
    Segment s3 = Segment.fromRing(p3, p1, new RingIn(1), op);
    s1.isInResultMemo = true;
    s2.isInResultMemo = true;
    s3.isInResultMemo = true;
    return RingOut.factory(List.of(s1, s2, s3)).get(0);
  }

  private static RingOut collinearRingAt(Operation op, double ox, double oy) {
    SweepPoint p1 = new SweepPoint(ox, oy);
    SweepPoint p2 = new SweepPoint(ox + 1, oy + 1);
    SweepPoint p3 = new SweepPoint(ox + 2, oy + 2);
    SweepPoint p4 = new SweepPoint(ox + 3, oy + 3);
    Segment s1 = Segment.fromRing(p1, p2, new RingIn(1), op);
    Segment s2 = Segment.fromRing(p2, p3, new RingIn(1), op);
    Segment s3 = Segment.fromRing(p3, p4, new RingIn(1), op);
    Segment s4 = Segment.fromRing(p4, p1, new RingIn(1), op);
    s1.isInResultMemo = true;
    s2.isInResultMemo = true;
    s3.isInResultMemo = true;
    s4.isInResultMemo = true;
    return RingOut.factory(List.of(s1, s2, s3, s4)).get(0);
  }

  // --- ring describe ---

  @Nested
  @DisplayName("ring")
  class RingTests {

    @Nested
    @DisplayName("factory")
    class FactoryTests {

      @Test
      @DisplayName("simple triangle")
      void simpleTriangle() {
        Operation op = new Operation(OpType.UNION);
        SweepPoint p1 = new SweepPoint(0, 0);
        SweepPoint p2 = new SweepPoint(1, 1);
        SweepPoint p3 = new SweepPoint(0, 1);

        Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
        Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
        Segment seg3 = Segment.fromRing(p3, p1, new RingIn(1), op);

        seg1.isInResultMemo = true;
        seg2.isInResultMemo = true;
        seg3.isInResultMemo = true;

        List<RingOut> rings = RingOut.factory(List.of(seg1, seg2, seg3));

        assertEquals(1, rings.size());
        assertGeom2d(new double[][]{{0, 0}, {1, 1}, {0, 1}, {0, 0}}, rings.get(0).getGeom());
      }

      @Test
      @DisplayName("bow tie")
      void bowTie() {
        Operation op = new Operation(OpType.UNION);
        SweepPoint p1 = new SweepPoint(0, 0);
        SweepPoint p2 = new SweepPoint(1, 1);
        SweepPoint p3 = new SweepPoint(0, 2);

        Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
        Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
        Segment seg3 = Segment.fromRing(p3, p1, new RingIn(1), op);

        SweepPoint p4 = new SweepPoint(2, 0);
        SweepPoint p5 = p2;
        SweepPoint p6 = new SweepPoint(2, 2);

        Segment seg4 = Segment.fromRing(p4, p5, new RingIn(1), op);
        Segment seg5 = Segment.fromRing(p5, p6, new RingIn(1), op);
        Segment seg6 = Segment.fromRing(p6, p4, new RingIn(1), op);

        seg1.isInResultMemo = true;
        seg2.isInResultMemo = true;
        seg3.isInResultMemo = true;
        seg4.isInResultMemo = true;
        seg5.isInResultMemo = true;
        seg6.isInResultMemo = true;

        List<RingOut> rings = RingOut.factory(List.of(seg1, seg2, seg3, seg4, seg5, seg6));

        assertEquals(2, rings.size());
        assertGeom2d(new double[][]{{0, 0}, {1, 1}, {0, 2}, {0, 0}}, rings.get(0).getGeom());
        assertGeom2d(new double[][]{{1, 1}, {2, 0}, {2, 2}, {1, 1}}, rings.get(1).getGeom());
      }

      @Test
      @DisplayName("ringed ring")
      void ringedRing() {
        Operation op = new Operation(OpType.UNION);
        SweepPoint p1 = new SweepPoint(0, 0);
        SweepPoint p2 = new SweepPoint(3, -3);
        SweepPoint p3 = new SweepPoint(3, 0);
        SweepPoint p4 = new SweepPoint(3, 3);

        Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
        Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
        Segment seg3 = Segment.fromRing(p3, p4, new RingIn(1), op);
        Segment seg4 = Segment.fromRing(p4, p1, new RingIn(1), op);

        SweepPoint p5 = new SweepPoint(2, -1);
        SweepPoint p6 = p3;
        SweepPoint p7 = new SweepPoint(2, 1);

        Segment seg5 = Segment.fromRing(p5, p6, new RingIn(1), op);
        Segment seg6 = Segment.fromRing(p6, p7, new RingIn(1), op);
        Segment seg7 = Segment.fromRing(p7, p5, new RingIn(1), op);

        seg1.isInResultMemo = true;
        seg2.isInResultMemo = true;
        seg3.isInResultMemo = true;
        seg4.isInResultMemo = true;
        seg5.isInResultMemo = true;
        seg6.isInResultMemo = true;
        seg7.isInResultMemo = true;

        List<RingOut> rings = RingOut.factory(List.of(seg1, seg2, seg3, seg4, seg5, seg6, seg7));

        assertEquals(2, rings.size());
        assertGeom2d(new double[][]{{3, 0}, {2, 1}, {2, -1}, {3, 0}}, rings.get(0).getGeom());
        assertGeom2d(new double[][]{{0, 0}, {3, -3}, {3, 3}, {0, 0}}, rings.get(1).getGeom());
      }

      @Test
      @DisplayName("ringed ring interior ring starting point extraneous")
      void ringedRingInteriorRingStartingPointExtraneous() {
        Operation op = new Operation(OpType.UNION);
        SweepPoint p1 = new SweepPoint(0, 0);
        SweepPoint p2 = new SweepPoint(5, -5);
        SweepPoint p3 = new SweepPoint(4, 0);
        SweepPoint p4 = new SweepPoint(5, 5);

        Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
        Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
        Segment seg3 = Segment.fromRing(p3, p4, new RingIn(1), op);
        Segment seg4 = Segment.fromRing(p4, p1, new RingIn(1), op);

        SweepPoint p5 = new SweepPoint(1, 0);
        SweepPoint p6 = new SweepPoint(4, 1);
        SweepPoint p7 = p3;
        SweepPoint p8 = new SweepPoint(4, -1);

        Segment seg5 = Segment.fromRing(p5, p6, new RingIn(1), op);
        Segment seg6 = Segment.fromRing(p6, p7, new RingIn(1), op);
        Segment seg7 = Segment.fromRing(p7, p8, new RingIn(1), op);
        Segment seg8 = Segment.fromRing(p8, p5, new RingIn(1), op);

        seg1.isInResultMemo = true;
        seg2.isInResultMemo = true;
        seg3.isInResultMemo = true;
        seg4.isInResultMemo = true;
        seg5.isInResultMemo = true;
        seg6.isInResultMemo = true;
        seg7.isInResultMemo = true;
        seg8.isInResultMemo = true;

        List<Segment> segs = List.of(seg1, seg2, seg3, seg4, seg5, seg6, seg7, seg8);
        List<RingOut> rings = RingOut.factory(segs);

        assertEquals(2, rings.size());
        assertGeom2d(new double[][]{{4, 1}, {1, 0}, {4, -1}, {4, 1}}, rings.get(0).getGeom());
        assertGeom2d(new double[][]{{0, 0}, {5, -5}, {4, 0}, {5, 5}, {0, 0}}, rings.get(1).getGeom());
      }

      @Test
      @DisplayName("ringed ring and bow tie at same point")
      void ringedRingAndBowTieAtSamePoint() {
        Operation op = new Operation(OpType.UNION);
        SweepPoint p1 = new SweepPoint(0, 0);
        SweepPoint p2 = new SweepPoint(3, -3);
        SweepPoint p3 = new SweepPoint(3, 0);
        SweepPoint p4 = new SweepPoint(3, 3);

        Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
        Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
        Segment seg3 = Segment.fromRing(p3, p4, new RingIn(1), op);
        Segment seg4 = Segment.fromRing(p4, p1, new RingIn(1), op);

        SweepPoint p5 = new SweepPoint(2, -1);
        SweepPoint p6 = p3;
        SweepPoint p7 = new SweepPoint(2, 1);

        Segment seg5 = Segment.fromRing(p5, p6, new RingIn(1), op);
        Segment seg6 = Segment.fromRing(p6, p7, new RingIn(1), op);
        Segment seg7 = Segment.fromRing(p7, p5, new RingIn(1), op);

        SweepPoint p8 = p3;
        SweepPoint p9 = new SweepPoint(4, -1);
        SweepPoint p10 = new SweepPoint(4, 1);

        Segment seg8 = Segment.fromRing(p8, p9, new RingIn(1), op);
        Segment seg9 = Segment.fromRing(p9, p10, new RingIn(1), op);
        Segment seg10 = Segment.fromRing(p10, p8, new RingIn(1), op);

        seg1.isInResultMemo = true;
        seg2.isInResultMemo = true;
        seg3.isInResultMemo = true;
        seg4.isInResultMemo = true;
        seg5.isInResultMemo = true;
        seg6.isInResultMemo = true;
        seg7.isInResultMemo = true;
        seg8.isInResultMemo = true;
        seg9.isInResultMemo = true;
        seg10.isInResultMemo = true;

        List<Segment> segs = List.of(seg1, seg2, seg3, seg4, seg5, seg6, seg7, seg8, seg9, seg10);
        List<RingOut> rings = RingOut.factory(segs);

        assertEquals(3, rings.size());
        assertGeom2d(new double[][]{{3, 0}, {2, 1}, {2, -1}, {3, 0}}, rings.get(0).getGeom());
        assertGeom2d(new double[][]{{0, 0}, {3, -3}, {3, 3}, {0, 0}}, rings.get(1).getGeom());
        assertGeom2d(new double[][]{{3, 0}, {4, -1}, {4, 1}, {3, 0}}, rings.get(2).getGeom());
      }

      @Test
      @DisplayName("double bow tie")
      void doubleBowTie() {
        Operation op = new Operation(OpType.UNION);
        SweepPoint p1 = new SweepPoint(0, 0);
        SweepPoint p2 = new SweepPoint(1, -2);
        SweepPoint p3 = new SweepPoint(1, 2);

        Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
        Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
        Segment seg3 = Segment.fromRing(p3, p1, new RingIn(1), op);

        SweepPoint p4 = p2;
        SweepPoint p5 = new SweepPoint(2, -3);
        SweepPoint p6 = new SweepPoint(2, -1);

        Segment seg4 = Segment.fromRing(p4, p5, new RingIn(1), op);
        Segment seg5 = Segment.fromRing(p5, p6, new RingIn(1), op);
        Segment seg6 = Segment.fromRing(p6, p4, new RingIn(1), op);

        SweepPoint p7 = p3;
        SweepPoint p8 = new SweepPoint(2, 1);
        SweepPoint p9 = new SweepPoint(2, 3);

        Segment seg7 = Segment.fromRing(p7, p8, new RingIn(1), op);
        Segment seg8 = Segment.fromRing(p8, p9, new RingIn(1), op);
        Segment seg9 = Segment.fromRing(p9, p7, new RingIn(1), op);

        seg1.isInResultMemo = true;
        seg2.isInResultMemo = true;
        seg3.isInResultMemo = true;
        seg4.isInResultMemo = true;
        seg5.isInResultMemo = true;
        seg6.isInResultMemo = true;
        seg7.isInResultMemo = true;
        seg8.isInResultMemo = true;
        seg9.isInResultMemo = true;

        List<Segment> segs = List.of(seg1, seg2, seg3, seg4, seg5, seg6, seg7, seg8, seg9);
        List<RingOut> rings = RingOut.factory(segs);

        assertEquals(3, rings.size());
        assertGeom2d(new double[][]{{0, 0}, {1, -2}, {1, 2}, {0, 0}}, rings.get(0).getGeom());
        assertGeom2d(new double[][]{{1, -2}, {2, -3}, {2, -1}, {1, -2}}, rings.get(1).getGeom());
        assertGeom2d(new double[][]{{1, 2}, {2, 1}, {2, 3}, {1, 2}}, rings.get(2).getGeom());
      }

      @Test
      @DisplayName("double ringed ring")
      void doubleRingedRing() {
        Operation op = new Operation(OpType.UNION);
        SweepPoint p1 = new SweepPoint(0, 0);
        SweepPoint p2 = new SweepPoint(5, -5);
        SweepPoint p3 = new SweepPoint(5, 5);

        Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
        Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
        Segment seg3 = Segment.fromRing(p3, p1, new RingIn(1), op);

        SweepPoint p4 = new SweepPoint(1, -1);
        SweepPoint p5 = p2;
        SweepPoint p6 = new SweepPoint(2, -1);

        Segment seg4 = Segment.fromRing(p4, p5, new RingIn(1), op);
        Segment seg5 = Segment.fromRing(p5, p6, new RingIn(1), op);
        Segment seg6 = Segment.fromRing(p6, p4, new RingIn(1), op);

        SweepPoint p7 = new SweepPoint(1, 1);
        SweepPoint p8 = p3;
        SweepPoint p9 = new SweepPoint(2, 1);

        Segment seg7 = Segment.fromRing(p7, p8, new RingIn(1), op);
        Segment seg8 = Segment.fromRing(p8, p9, new RingIn(1), op);
        Segment seg9 = Segment.fromRing(p9, p7, new RingIn(1), op);

        seg1.isInResultMemo = true;
        seg2.isInResultMemo = true;
        seg3.isInResultMemo = true;
        seg4.isInResultMemo = true;
        seg5.isInResultMemo = true;
        seg6.isInResultMemo = true;
        seg7.isInResultMemo = true;
        seg8.isInResultMemo = true;
        seg9.isInResultMemo = true;

        List<Segment> segs = List.of(seg1, seg2, seg3, seg4, seg5, seg6, seg7, seg8, seg9);
        List<RingOut> rings = RingOut.factory(segs);

        assertEquals(3, rings.size());
        assertGeom2d(new double[][]{{5, -5}, {2, -1}, {1, -1}, {5, -5}}, rings.get(0).getGeom());
        assertGeom2d(new double[][]{{5, 5}, {1, 1}, {2, 1}, {5, 5}}, rings.get(1).getGeom());
        assertGeom2d(new double[][]{{0, 0}, {5, -5}, {5, 5}, {0, 0}}, rings.get(2).getGeom());
      }

      @Test
      @DisplayName("errors on on malformed ring")
      void errorsOnOnMalformedRing() {
        Operation op = new Operation(OpType.UNION);
        SweepPoint p1 = new SweepPoint(0, 0);
        SweepPoint p2 = new SweepPoint(1, 1);
        SweepPoint p3 = new SweepPoint(0, 1);

        Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
        Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
        Segment seg3 = Segment.fromRing(p3, p1, new RingIn(1), op);

        seg1.isInResultMemo = true;
        seg2.isInResultMemo = true;
        seg3.isInResultMemo = false; // broken ring

        assertThrows(RuntimeException.class, () -> RingOut.factory(List.of(seg1, seg2, seg3)));
      }
    }

    @Test
    @DisplayName("exterior ring")
    void exteriorRing() {
      Operation op = new Operation(OpType.UNION);
      SweepPoint p1 = new SweepPoint(0, 0);
      SweepPoint p2 = new SweepPoint(1, 1);
      SweepPoint p3 = new SweepPoint(0, 1);

      Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
      Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
      Segment seg3 = Segment.fromRing(p3, p1, new RingIn(1), op);

      seg1.isInResultMemo = true;
      seg2.isInResultMemo = true;
      seg3.isInResultMemo = true;

      RingOut ring = RingOut.factory(List.of(seg1, seg2, seg3)).get(0);

      assertNull(ring.enclosingRing());
      assertTrue(ring.isExteriorRing());
      assertGeom2d(new double[][]{{0, 0}, {1, 1}, {0, 1}, {0, 0}}, ring.getGeom());
    }

    @Test
    @DisplayName("interior ring points reversed")
    void interiorRingPointsReversed() {
      Operation op = new Operation(OpType.UNION);
      SweepPoint p1 = new SweepPoint(0, 0);
      SweepPoint p2 = new SweepPoint(1, 1);
      SweepPoint p3 = new SweepPoint(0, 1);

      Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
      Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
      Segment seg3 = Segment.fromRing(p3, p1, new RingIn(1), op);

      seg1.isInResultMemo = true;
      seg2.isInResultMemo = true;
      seg3.isInResultMemo = true;

      RingOut ring = RingOut.factory(List.of(seg1, seg2, seg3)).get(0);
      ring.isExteriorRingMemo = false;

      assertFalse(ring.isExteriorRing());
      assertGeom2d(new double[][]{{0, 0}, {0, 1}, {1, 1}, {0, 0}}, ring.getGeom());
    }

    @Test
    @DisplayName("removes colinear points successfully")
    void removesColinearPointsSuccessfully() {
      Operation op = new Operation(OpType.UNION);
      SweepPoint p1 = new SweepPoint(0, 0);
      SweepPoint p2 = new SweepPoint(1, 1);
      SweepPoint p3 = new SweepPoint(2, 2);
      SweepPoint p4 = new SweepPoint(0, 2);

      Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
      Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
      Segment seg3 = Segment.fromRing(p3, p4, new RingIn(1), op);
      Segment seg4 = Segment.fromRing(p4, p1, new RingIn(1), op);

      seg1.isInResultMemo = true;
      seg2.isInResultMemo = true;
      seg3.isInResultMemo = true;
      seg4.isInResultMemo = true;

      RingOut ring = RingOut.factory(List.of(seg1, seg2, seg3, seg4)).get(0);

      assertGeom2d(new double[][]{{0, 0}, {2, 2}, {0, 2}, {0, 0}}, ring.getGeom());
    }

    @Test
    @DisplayName("almost equal point handled ok")
    void almostEqualPointHandledOk() {
      // points harvested from https://github.com/mfogel/polygon-clipping/issues/37
      Operation op = new Operation(OpType.UNION);
      SweepPoint p1 = new SweepPoint(0.523985, 51.281651);
      SweepPoint p2 = new SweepPoint(0.5241, 51.2816);
      SweepPoint p3 = new SweepPoint(0.5240213684210527, 51.281687368421);
      SweepPoint p4 = new SweepPoint(0.5239850000000027, 51.281651000000004); // almost equal to p1

      Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
      Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
      Segment seg3 = Segment.fromRing(p3, p4, new RingIn(1), op);
      Segment seg4 = Segment.fromRing(p4, p1, new RingIn(1), op);

      seg1.isInResultMemo = true;
      seg2.isInResultMemo = true;
      seg3.isInResultMemo = true;
      seg4.isInResultMemo = true;

      RingOut ring = RingOut.factory(List.of(seg1, seg2, seg3, seg4)).get(0);

      assertGeom2d(
          new double[][]{
            {0.523985, 51.281651},
            {0.5241, 51.2816},
            {0.5240213684210527, 51.281687368421},
            {0.5239850000000027, 51.281651000000004},
            {0.523985, 51.281651},
          },
          ring.getGeom());
    }

    @Test
    @DisplayName("ring with all colinear points returns null")
    void ringWithAllColinearPointsReturnsNull() {
      Operation op = new Operation(OpType.UNION);
      SweepPoint p1 = new SweepPoint(0, 0);
      SweepPoint p2 = new SweepPoint(1, 1);
      SweepPoint p3 = new SweepPoint(2, 2);
      SweepPoint p4 = new SweepPoint(3, 3);

      Segment seg1 = Segment.fromRing(p1, p2, new RingIn(1), op);
      Segment seg2 = Segment.fromRing(p2, p3, new RingIn(1), op);
      Segment seg3 = Segment.fromRing(p3, p4, new RingIn(1), op);
      Segment seg4 = Segment.fromRing(p4, p1, new RingIn(1), op);

      seg1.isInResultMemo = true;
      seg2.isInResultMemo = true;
      seg3.isInResultMemo = true;
      seg4.isInResultMemo = true;

      RingOut ring = RingOut.factory(List.of(seg1, seg2, seg3, seg4)).get(0);

      assertNull(ring.getGeom());
    }
  }

  // --- poly describe ---
  // Adapted: upstream used duck objects {poly: null, getGeom: () => N}; the typed port
  // uses real RingOut instances built by the realRing / collinearRingAt helpers.

  @Nested
  @DisplayName("poly")
  class PolyTests {

    @Test
    @DisplayName("basic")
    void basic() {
      Operation op = new Operation(OpType.UNION);
      RingOut ring1 = realRing(op, 0, 0);
      RingOut ring2 = realRing(op, 10, 10);
      RingOut ring3 = realRing(op, 20, 20);

      PolyOut poly = new PolyOut(ring1);
      poly.addInterior(ring2);
      poly.addInterior(ring3);

      assertSame(poly, ring1.poly);
      assertSame(poly, ring2.poly);
      assertSame(poly, ring3.poly);

      double[][] ring1Geom = ring1.getGeom();
      double[][] ring2Geom = ring2.getGeom();
      double[][] ring3Geom = ring3.getGeom();

      double[][][] polyGeom = poly.getGeom();
      assertNotNull(polyGeom);
      assertEquals(3, polyGeom.length);
      assertGeom2d(ring1Geom, polyGeom[0]);
      assertGeom2d(ring2Geom, polyGeom[1]);
      assertGeom2d(ring3Geom, polyGeom[2]);
    }

    @Test
    @DisplayName("has all colinear exterior ring")
    void hasAllColinearExteriorRing() {
      Operation op = new Operation(OpType.UNION);
      RingOut ring1 = collinearRingAt(op, 0, 0);
      PolyOut poly = new PolyOut(ring1);

      assertSame(poly, ring1.poly);

      assertNull(poly.getGeom());
    }

    @Test
    @DisplayName("has all colinear interior ring")
    void hasAllColinearInteriorRing() {
      Operation op = new Operation(OpType.UNION);
      RingOut ring1 = realRing(op, 0, 0);
      // Use large offset to avoid point sharing with ring1 via op.rounder
      RingOut ring2 = collinearRingAt(op, 50, 50);
      RingOut ring3 = realRing(op, 20, 20);

      PolyOut poly = new PolyOut(ring1);
      poly.addInterior(ring2);
      poly.addInterior(ring3);

      assertSame(poly, ring1.poly);
      assertSame(poly, ring2.poly);
      assertSame(poly, ring3.poly);

      double[][] ring1Geom = ring1.getGeom();
      double[][] ring3Geom = ring3.getGeom();

      double[][][] polyGeom = poly.getGeom();
      assertNotNull(polyGeom);
      assertEquals(2, polyGeom.length);
      assertGeom2d(ring1Geom, polyGeom[0]);
      assertGeom2d(ring3Geom, polyGeom[1]);
    }
  }

  // --- multipoly describe ---
  // Adapted: upstream used duck objects {getGeom: () => N}; the typed port uses real
  // PolyOut instances backed by real rings. polys field is set directly as in the upstream.

  @Nested
  @DisplayName("multipoly")
  class MultiPolyTests {

    @Test
    @DisplayName("basic")
    void basic() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyOut multipoly = new MultiPolyOut(new ArrayList<>());
      PolyOut poly1 = new PolyOut(realRing(op, 0, 0));
      PolyOut poly2 = new PolyOut(realRing(op, 10, 10));
      multipoly.polys = List.of(poly1, poly2);

      double[][][] poly1Geom = poly1.getGeom();
      double[][][] poly2Geom = poly2.getGeom();

      double[][][][] result = multipoly.getGeom();
      assertNotNull(result);
      assertEquals(2, result.length);
      // assert poly1 geom
      assertNotNull(poly1Geom);
      assertEquals(poly1Geom.length, result[0].length);
      assertGeom2d(poly1Geom[0], result[0][0]);
      // assert poly2 geom
      assertNotNull(poly2Geom);
      assertEquals(poly2Geom.length, result[1].length);
      assertGeom2d(poly2Geom[0], result[1][0]);
    }

    @Test
    @DisplayName("has poly with all colinear exterior ring")
    void hasPolyWithAllColinearExteriorRing() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyOut multipoly = new MultiPolyOut(new ArrayList<>());
      PolyOut poly1 = new PolyOut(collinearRingAt(op, 0, 0));
      PolyOut poly2 = new PolyOut(realRing(op, 10, 10));
      multipoly.polys = List.of(poly1, poly2);

      double[][][] poly2Geom = poly2.getGeom();

      double[][][][] result = multipoly.getGeom();
      assertNotNull(result);
      assertEquals(1, result.length);
      assertNotNull(poly2Geom);
      assertEquals(poly2Geom.length, result[0].length);
      assertGeom2d(poly2Geom[0], result[0][0]);
    }
  }
}
