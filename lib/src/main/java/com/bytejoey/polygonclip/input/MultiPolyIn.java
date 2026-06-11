package com.bytejoey.polygonclip.input;

import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.geom.Geom;
import com.bytejoey.polygonclip.geom.MultiPolygon;
import com.bytejoey.polygonclip.geom.Polygon;
import com.bytejoey.polygonclip.num.Vec;
import com.bytejoey.polygonclip.sweep.SweepEvent;
import com.bytejoey.polygonclip.util.Bbox;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of geom-in.js MultiPolyIn. Represents the full subject or clip geometry.
 *
 * <p>The field-only constructor {@link #MultiPolyIn(boolean)} is a test convenience retained from
 * earlier phases; the real geometry-parsing constructor
 * {@link #MultiPolyIn(Geom, boolean, Operation)} is the production entry point.
 */
public class MultiPolyIn {
  public final boolean isSubject;
  public List<PolyIn> polys;
  public Bbox bbox;

  public MultiPolyIn(boolean isSubject) {
    this.isSubject = isSubject;
  }

  public MultiPolyIn(Geom geom, boolean isSubject, Operation op) {
    if (geom == null) {
      throw new IllegalArgumentException("Input geometry is not a valid Polygon or MultiPolygon");
    }

    // if the input looks like a polygon, convert it to a multipolygon
    // (upstream sniffs geom[0][0][0]; the typed API makes this an instanceof)
    double[][][][] coords;
    if (geom instanceof Polygon p) coords = new double[][][][] {p.coordinates()};
    else coords = ((MultiPolygon) geom).coordinates();

    this.polys = new ArrayList<>();
    double llx = Double.POSITIVE_INFINITY;
    double lly = Double.POSITIVE_INFINITY;
    double urx = Double.NEGATIVE_INFINITY;
    double ury = Double.NEGATIVE_INFINITY;
    for (int i = 0, iMax = coords.length; i < iMax; i++) {
      PolyIn poly = new PolyIn(coords[i], this, op);
      if (poly.bbox.ll.x() < llx) llx = poly.bbox.ll.x();
      if (poly.bbox.ll.y() < lly) lly = poly.bbox.ll.y();
      if (poly.bbox.ur.x() > urx) urx = poly.bbox.ur.x();
      if (poly.bbox.ur.y() > ury) ury = poly.bbox.ur.y();
      this.polys.add(poly);
    }
    this.bbox = new Bbox(new Vec(llx, lly), new Vec(urx, ury));
    this.isSubject = isSubject;
  }

  public List<SweepEvent> getSweepEvents() {
    List<SweepEvent> sweepEvents = new ArrayList<>();
    for (int i = 0, iMax = this.polys.size(); i < iMax; i++) {
      List<SweepEvent> polySweepEvents = this.polys.get(i).getSweepEvents();
      for (int j = 0, jMax = polySweepEvents.size(); j < jMax; j++) {
        sweepEvents.add(polySweepEvents.get(j));
      }
    }
    return sweepEvents;
  }
}
