package com.bytejoey.polygonclip;

import com.bytejoey.polygonclip.geom.Geom;
import com.bytejoey.polygonclip.geom.MultiPolygon;

/**
 * Boolean polygon operations. Port of mfogel/polygon-clipping (MIT).
 *
 * <p>All four methods are safe for concurrent use — each call creates its own {@link Operation}
 * with no shared mutable state.
 */
public final class PolygonClip {
  private PolygonClip() {}

  /**
   * Computes the union of {@code subject} and all {@code others} (n-ary).
   *
   * @param subject the subject geometry; must be a {@link com.bytejoey.polygonclip.geom.Polygon}
   *     or {@link MultiPolygon}
   * @param others zero or more clip geometries
   * @return a {@link MultiPolygon} representing the union, possibly empty
   * @throws IllegalArgumentException if any geometry is invalid
   */
  public static MultiPolygon union(Geom subject, Geom... others) {
    return new Operation(OpType.UNION).run(subject, others);
  }

  /**
   * Computes the intersection of {@code subject} and all {@code others} (n-ary).
   *
   * @param subject the subject geometry
   * @param others one or more clip geometries
   * @return a {@link MultiPolygon} representing the intersection, possibly empty
   * @throws IllegalArgumentException if any geometry is invalid
   */
  public static MultiPolygon intersection(Geom subject, Geom... others) {
    return new Operation(OpType.INTERSECTION).run(subject, others);
  }

  /**
   * Computes the symmetric difference (XOR) of {@code subject} and all {@code others} (n-ary).
   *
   * @param subject the subject geometry
   * @param others one or more clip geometries
   * @return a {@link MultiPolygon} representing the XOR, possibly empty
   * @throws IllegalArgumentException if any geometry is invalid
   */
  public static MultiPolygon xor(Geom subject, Geom... others) {
    return new Operation(OpType.XOR).run(subject, others);
  }

  /**
   * Computes the difference of {@code subject} minus the union of all {@code clippings} (n-ary).
   *
   * @param subject the subject geometry
   * @param clippings one or more geometries to subtract from the subject
   * @return a {@link MultiPolygon} representing the difference, possibly empty
   * @throws IllegalArgumentException if any geometry is invalid
   */
  public static MultiPolygon difference(Geom subject, Geom... clippings) {
    return new Operation(OpType.DIFFERENCE).run(subject, clippings);
  }
}
