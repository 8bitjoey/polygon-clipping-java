package com.bytejoey.polygonclip.input;

import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.num.Vec;
import com.bytejoey.polygonclip.sweep.SweepEvent;
import com.bytejoey.polygonclip.util.Bbox;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of geom-in.js PolyIn. Represents one polygon (exterior ring + holes) within a multipolygon.
 *
 * <p>The field-only constructor {@link #PolyIn(MultiPolyIn)} is a test convenience retained from
 * earlier phases; the real geometry-parsing constructor
 * {@link #PolyIn(double[][][], MultiPolyIn, Operation)} is the production entry point.
 */
public class PolyIn {
  public final MultiPolyIn multiPoly;
  public RingIn exteriorRing;
  public List<RingIn> interiorRings;
  public Bbox bbox;

  public PolyIn(MultiPolyIn multiPoly) {
    this.multiPoly = multiPoly;
  }

  public PolyIn(double[][][] geomPoly, MultiPolyIn multiPoly, Operation op) {
    // upstream reaches this throw via RingIn(undefined) when geomPoly is empty;
    // the typed port must check explicitly to raise the same error
    if (geomPoly == null || geomPoly.length == 0) {
      throw new IllegalArgumentException("Input geometry is not a valid Polygon or MultiPolygon");
    }
    this.exteriorRing = new RingIn(geomPoly[0], this, true, op);
    // copy by value
    double llx = this.exteriorRing.bbox.ll.x();
    double lly = this.exteriorRing.bbox.ll.y();
    double urx = this.exteriorRing.bbox.ur.x();
    double ury = this.exteriorRing.bbox.ur.y();
    this.interiorRings = new ArrayList<>();
    for (int i = 1, iMax = geomPoly.length; i < iMax; i++) {
      RingIn ring = new RingIn(geomPoly[i], this, false, op);
      if (ring.bbox.ll.x() < llx) llx = ring.bbox.ll.x();
      if (ring.bbox.ll.y() < lly) lly = ring.bbox.ll.y();
      if (ring.bbox.ur.x() > urx) urx = ring.bbox.ur.x();
      if (ring.bbox.ur.y() > ury) ury = ring.bbox.ur.y();
      this.interiorRings.add(ring);
    }
    this.bbox = new Bbox(new Vec(llx, lly), new Vec(urx, ury));
    this.multiPoly = multiPoly;
  }

  public List<SweepEvent> getSweepEvents() {
    List<SweepEvent> sweepEvents = this.exteriorRing.getSweepEvents();
    for (int i = 0, iMax = this.interiorRings.size(); i < iMax; i++) {
      List<SweepEvent> ringSweepEvents = this.interiorRings.get(i).getSweepEvents();
      for (int j = 0, jMax = ringSweepEvents.size(); j < jMax; j++) {
        sweepEvents.add(ringSweepEvents.get(j));
      }
    }
    return sweepEvents;
  }
}
