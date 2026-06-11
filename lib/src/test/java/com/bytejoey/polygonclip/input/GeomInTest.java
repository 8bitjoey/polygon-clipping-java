package com.bytejoey.polygonclip.input;

import com.bytejoey.polygonclip.OpType;
import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.geom.Geom;
import com.bytejoey.polygonclip.geom.MultiPolygon;
import com.bytejoey.polygonclip.geom.Polygon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Port of upstream geom-in.test.js.
 *
 * <p>N/A ledger — upstream tests not ported because they are precluded by the typed Java API
 * (double[] cannot hold non-numbers; Geom cannot be a bare point/ring/string):
 * <ul>
 *   <li>"creation with invalid input" — string geometry
 *   <li>"creation with point" — bare number array as geometry
 *   <li>"creation with ring" — bare ring array as geometry
 *   <li>"creation with polygon with invalid coordiante" — string coordinate value
 *   <li>"creation with multipolygon with invalid coordiante" — array-as-coordinate
 * </ul>
 */
class GeomInTest {

  @Nested
  @DisplayName("RingIn")
  class RingInTests {

    @Test
    void create_exterior_ring() {
      Operation op = new Operation(OpType.UNION);
      PolyIn poly = new PolyIn((MultiPolyIn) null);
      RingIn ring = new RingIn(new double[][] {{0, 0}, {1, 0}, {1, 1}}, poly, true, op);
      poly.exteriorRing = ring;

      assertSame(poly, ring.poly);
      assertTrue(ring.isExterior);
      assertEquals(3, ring.segments.size());
      assertEquals(6, ring.getSweepEvents().size());

      assertEquals(0.0, ring.segments.get(0).leftSE.point.x);
      assertEquals(0.0, ring.segments.get(0).leftSE.point.y);
      assertEquals(1.0, ring.segments.get(0).rightSE.point.x);
      assertEquals(0.0, ring.segments.get(0).rightSE.point.y);
      assertEquals(1.0, ring.segments.get(1).leftSE.point.x);
      assertEquals(0.0, ring.segments.get(1).leftSE.point.y);
      assertEquals(1.0, ring.segments.get(1).rightSE.point.x);
      assertEquals(1.0, ring.segments.get(1).rightSE.point.y);
      assertEquals(0.0, ring.segments.get(2).leftSE.point.x);
      assertEquals(0.0, ring.segments.get(2).leftSE.point.y);
      assertEquals(1.0, ring.segments.get(2).rightSE.point.x);
      assertEquals(1.0, ring.segments.get(2).rightSE.point.y);
    }

    @Test
    void create_an_interior_ring() {
      Operation op = new Operation(OpType.UNION);
      RingIn ring = new RingIn(new double[][] {{0, 0}, {1, 1}, {1, 0}}, new PolyIn((MultiPolyIn) null), false, op);
      assertFalse(ring.isExterior);
    }
  }

  @Nested
  @DisplayName("PolyIn")
  class PolyInTests {

    @Test
    void creation() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyIn multiPoly = new MultiPolyIn(false);
      PolyIn poly = new PolyIn(
        new double[][][] {
          {{0, 0}, {10, 0}, {10, 10}, {0, 10}},
          {{0, 0}, {1, 1}, {1, 0}},
          {{2, 2}, {2, 3}, {3, 3}, {3, 2}},
        },
        multiPoly,
        op
      );

      assertSame(multiPoly, poly.multiPoly);
      assertEquals(4, poly.exteriorRing.segments.size());
      assertEquals(2, poly.interiorRings.size());
      assertEquals(3, poly.interiorRings.get(0).segments.size());
      assertEquals(4, poly.interiorRings.get(1).segments.size());
      assertEquals(22, poly.getSweepEvents().size());
    }
  }

  @Nested
  @DisplayName("MultiPolyIn")
  class MultiPolyInTests {

    @Test
    void creation_with_multipoly() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyIn multipoly = new MultiPolyIn(
        new MultiPolygon(new double[][][][] {
          {{{0, 0}, {1, 1}, {0, 1}}},
          {
            {{0, 0}, {4, 0}, {4, 9}},
            {{2, 2}, {3, 3}, {3, 2}},
          },
        }),
        false,
        op
      );

      assertEquals(2, multipoly.polys.size());
      assertEquals(18, multipoly.getSweepEvents().size());
    }

    @Test
    void creation_with_poly() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyIn multipoly = new MultiPolyIn(
        new Polygon(new double[][][] {{{0, 0}, {1, 1}, {0, 1}, {0, 0}}}),
        false,
        op
      );

      assertEquals(1, multipoly.polys.size());
      assertEquals(6, multipoly.getSweepEvents().size());
    }

    @Test
    void third_or_more_coordinates_are_ignored() {
      Operation op = new Operation(OpType.UNION);
      MultiPolyIn multipoly = new MultiPolyIn(
        new Polygon(new double[][][] {{{0, 0, 42}, {1, 1, 128}, {0, 1, 84}, {0, 0, 42}}}),
        false,
        op
      );

      assertEquals(1, multipoly.polys.size());
      assertEquals(6, multipoly.getSweepEvents().size());
    }

    @Test
    @DisplayName("creation with empty polygon / ring ")
    void creation_with_empty_polygon_or_ring_multipoly() {
      Operation op = new Operation(OpType.UNION);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        new MultiPolyIn(new MultiPolygon(new double[][][][] {{{}}}), false, op)
      );
      assertTrue(ex.getMessage().contains("not a valid Polygon or MultiPolygon"));
    }

    @Test
    @DisplayName("creation with empty polygon / ring (polygon-typed)")
    void creation_with_empty_polygon_or_ring_polygon() {
      Operation op = new Operation(OpType.UNION);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        new MultiPolyIn(new Polygon(new double[][][] {{}}), false, op)
      );
      assertTrue(ex.getMessage().contains("not a valid Polygon or MultiPolygon"));
    }

    @Test
    @DisplayName("creation with empty ring / point ")
    void creation_with_empty_ring_or_point() {
      Operation op = new Operation(OpType.UNION);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        new MultiPolyIn(new Polygon(new double[][][] {{{}}}), false, op)
      );
      assertTrue(ex.getMessage().contains("not a valid Polygon or MultiPolygon"));
    }

    @Test
    @DisplayName("creation with polygon with missing coordiante")
    void creation_with_polygon_with_missing_coordiante() {
      Operation op = new Operation(OpType.UNION);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        new MultiPolyIn(new Polygon(new double[][][] {{{0, 0}, {1}, {1, 1}}}), false, op)
      );
      assertTrue(ex.getMessage().contains("not a valid Polygon or MultiPolygon"));
    }

    @Test
    @DisplayName("creation with multipolygon with missing coordiante")
    void creation_with_multipolygon_with_missing_coordiante() {
      Operation op = new Operation(OpType.UNION);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        new MultiPolyIn(new MultiPolygon(new double[][][][] {{{{0}, {0, 1}, {1, 0}}}}), false, op)
      );
      assertTrue(ex.getMessage().contains("not a valid Polygon or MultiPolygon"));
    }

    @Test
    @DisplayName("creation with null geometry")
    void creation_with_null_geometry() {
      Operation op = new Operation(OpType.UNION);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
        new MultiPolyIn((Geom) null, false, op)
      );
      assertTrue(ex.getMessage().contains("not a valid Polygon or MultiPolygon"));
    }
  }
}
