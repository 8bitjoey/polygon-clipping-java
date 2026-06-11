package com.bytejoey.polygonclip.geom;

/**
 * GeoJSON-style Polygon: {@code [ring][position][x,y]}.
 * The first ring is the exterior; subsequent rings are holes.
 * Rings need not be explicitly closed on input.
 */
public final class Polygon implements Geom {
  private final double[][][] coordinates;

  public Polygon(double[][][] coordinates) {
    this.coordinates = coordinates;
  }

  public double[][][] coordinates() {
    return coordinates;
  }
}
