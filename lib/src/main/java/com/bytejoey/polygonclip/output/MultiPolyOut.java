package com.bytejoey.polygonclip.output;

import java.util.ArrayList;
import java.util.List;

public class MultiPolyOut {
  public final List<RingOut> rings;
  public List<PolyOut> polys;

  public MultiPolyOut(List<RingOut> rings) {
    this.rings = rings;
    this.polys = composePolys(rings);
  }

  public double[][][][] getGeom() {
    List<double[][][]> geom = new ArrayList<>();
    for (int i = 0, iMax = this.polys.size(); i < iMax; i++) {
      double[][][] polyGeom = this.polys.get(i).getGeom();
      // exterior ring was all (within rounding error of angle calc) colinear points
      if (polyGeom == null) continue;
      geom.add(polyGeom);
    }
    return geom.toArray(new double[0][][][]);
  }

  private static List<PolyOut> composePolys(List<RingOut> rings) {
    List<PolyOut> polys = new ArrayList<>();
    for (int i = 0, iMax = rings.size(); i < iMax; i++) {
      RingOut ring = rings.get(i);
      if (ring.poly != null) continue;
      if (ring.isExteriorRing()) polys.add(new PolyOut(ring));
      else {
        RingOut enclosingRing = ring.enclosingRing();
        if (enclosingRing.poly == null) polys.add(new PolyOut(enclosingRing));
        enclosingRing.poly.addInterior(ring);
      }
    }
    return polys;
  }
}
