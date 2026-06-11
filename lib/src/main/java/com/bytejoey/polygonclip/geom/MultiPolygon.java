package com.bytejoey.polygonclip.geom;

/**
 * GeoJSON-style MultiPolygon: {@code [polygon][ring][position][x,y]}.
 * Within each polygon the first ring is the exterior; subsequent rings are holes.
 * Rings need not be explicitly closed on input; output rings are closed.
 */
public final class MultiPolygon implements Geom {
  private final double[][][][] coordinates;

  public MultiPolygon(double[][][][] coordinates) {
    this.coordinates = coordinates;
  }

  public double[][][][] coordinates() {
    return coordinates;
  }
}
