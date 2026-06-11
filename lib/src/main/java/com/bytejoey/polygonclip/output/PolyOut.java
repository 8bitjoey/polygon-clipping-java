package com.bytejoey.polygonclip.output;

import java.util.ArrayList;
import java.util.List;

public class PolyOut {
  public final RingOut exteriorRing;
  public final List<RingOut> interiorRings;

  public PolyOut(RingOut exteriorRing) {
    this.exteriorRing = exteriorRing;
    exteriorRing.poly = this;
    this.interiorRings = new ArrayList<>();
  }

  public void addInterior(RingOut ring) {
    this.interiorRings.add(ring);
    ring.poly = this;
  }

  public double[][][] getGeom() {
    double[][] exterior = this.exteriorRing.getGeom();
    // exterior ring was all (within rounding error of angle calc) colinear points
    if (exterior == null) return null;
    List<double[][]> geom = new ArrayList<>();
    geom.add(exterior);
    for (int i = 0, iMax = this.interiorRings.size(); i < iMax; i++) {
      double[][] ringGeom = this.interiorRings.get(i).getGeom();
      // interior ring was all (within rounding error of angle calc) colinear points
      if (ringGeom == null) continue;
      geom.add(ringGeom);
    }
    return geom.toArray(new double[0][][]);
  }
}
