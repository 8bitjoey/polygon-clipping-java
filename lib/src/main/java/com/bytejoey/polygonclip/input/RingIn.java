package com.bytejoey.polygonclip.input;

import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.num.Vec;
import com.bytejoey.polygonclip.sweep.Segment;
import com.bytejoey.polygonclip.sweep.SweepEvent;
import com.bytejoey.polygonclip.sweep.SweepPoint;
import com.bytejoey.polygonclip.util.Bbox;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of geom-in.js RingIn. Represents one ring (exterior or hole) of a polygon.
 *
 * <p>The int-id and field-only constructors ({@link #RingIn(int)}, {@link #RingIn(PolyIn, boolean)})
 * are test conveniences retained from earlier phases; the real geometry-parsing constructor
 * {@link #RingIn(double[][], PolyIn, boolean, Operation)} is the production entry point.
 */
public class RingIn {
  public final int id;
  public PolyIn poly;
  public boolean isExterior;
  public List<Segment> segments;
  public Bbox bbox;

  public RingIn(int id) {
    this.id = id;
  }

  public RingIn(PolyIn poly, boolean isExterior) {
    this.id = 0;
    this.poly = poly;
    this.isExterior = isExterior;
  }

  public RingIn(double[][] geomRing, PolyIn poly, boolean isExterior, Operation op) {
    this.id = 0;
    if (geomRing == null || geomRing.length == 0) {
      throw new IllegalArgumentException("Input geometry is not a valid Polygon or MultiPolygon");
    }

    this.poly = poly;
    this.isExterior = isExterior;
    this.segments = new ArrayList<>();

    if (geomRing[0] == null || geomRing[0].length < 2) {
      throw new IllegalArgumentException("Input geometry is not a valid Polygon or MultiPolygon");
    }

    SweepPoint firstPoint = op.rounder.round(geomRing[0][0], geomRing[0][1]);
    double llx = firstPoint.x;
    double lly = firstPoint.y;
    double urx = firstPoint.x;
    double ury = firstPoint.y;

    SweepPoint prevPoint = firstPoint;
    for (int i = 1, iMax = geomRing.length; i < iMax; i++) {
      if (geomRing[i] == null || geomRing[i].length < 2) {
        throw new IllegalArgumentException("Input geometry is not a valid Polygon or MultiPolygon");
      }
      SweepPoint point = op.rounder.round(geomRing[i][0], geomRing[i][1]);
      // skip repeated points
      if (point.x == prevPoint.x && point.y == prevPoint.y) continue;
      this.segments.add(Segment.fromRing(prevPoint, point, this, op));
      if (point.x < llx) llx = point.x;
      if (point.y < lly) lly = point.y;
      if (point.x > urx) urx = point.x;
      if (point.y > ury) ury = point.y;
      prevPoint = point;
    }
    // add segment from last to first if last is not the same as first
    if (firstPoint.x != prevPoint.x || firstPoint.y != prevPoint.y) {
      this.segments.add(Segment.fromRing(prevPoint, firstPoint, this, op));
    }
    this.bbox = new Bbox(new Vec(llx, lly), new Vec(urx, ury));
  }

  public List<SweepEvent> getSweepEvents() {
    List<SweepEvent> sweepEvents = new ArrayList<>();
    for (int i = 0, iMax = this.segments.size(); i < iMax; i++) {
      Segment segment = this.segments.get(i);
      sweepEvents.add(segment.leftSE);
      sweepEvents.add(segment.rightSE);
    }
    return sweepEvents;
  }
}
