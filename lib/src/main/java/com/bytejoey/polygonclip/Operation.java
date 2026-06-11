package com.bytejoey.polygonclip;

import com.bytejoey.polygonclip.geom.Geom;
import com.bytejoey.polygonclip.geom.MultiPolygon;
import com.bytejoey.polygonclip.input.MultiPolyIn;
import com.bytejoey.polygonclip.num.Rounder;
import com.bytejoey.polygonclip.output.MultiPolyOut;
import com.bytejoey.polygonclip.output.RingOut;
import com.bytejoey.polygonclip.sweep.EventQueue;
import com.bytejoey.polygonclip.sweep.Segment;
import com.bytejoey.polygonclip.sweep.SweepEvent;
import com.bytejoey.polygonclip.sweep.SweepLine;
import com.bytejoey.polygonclip.util.Bbox;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-call operation context. Upstream keeps a module-level singleton
 * (operation.js:129) plus module-level rounder and segment-id counter; the port
 * makes all of them per-call state so concurrent operations don't share anything.
 * One Operation per run; never reuse.
 */
public final class Operation {

  public final OpType type;

  /** Per-operation point snapper (upstream module singleton, rounder.js). */
  public final Rounder rounder = new Rounder();

  /** Set during input ingestion (operation.js:29); settable directly in unit tests. */
  public int numMultiPolys;

  // Give segments unique ID's to get consistent sorting of
  // segments and sweep events when all else is identical (segment.js:7-9)
  private int segmentId = 0;

  public Operation(OpType type) {
    this.type = type;
  }

  public int nextSegmentId() {
    return ++segmentId;
  }

  // Limits on iterative processes to prevent infinite loops - usually caused by
  // floating-point math round-off errors. (operation.js:9-17; env overrides kept)
  private static final long MAX_QUEUE_SIZE = envLimit("POLYGON_CLIPPING_MAX_QUEUE_SIZE");
  private static final long MAX_SWEEPLINE_SEGMENTS =
      envLimit("POLYGON_CLIPPING_MAX_SWEEPLINE_SEGMENTS");

  private static long envLimit(String name) {
    String value = System.getenv(name);
    if (value == null) return 1_000_000L;
    return Long.parseLong(value);
  }

  public MultiPolygon run(Geom geom, Geom[] moreGeoms) {
    /* Convert inputs to MultiPoly objects */
    List<MultiPolyIn> multipolys = new ArrayList<>();
    multipolys.add(new MultiPolyIn(geom, true, this));
    for (int i = 0, iMax = moreGeoms.length; i < iMax; i++) {
      multipolys.add(new MultiPolyIn(moreGeoms[i], false, this));
    }
    this.numMultiPolys = multipolys.size();

    /* BBox optimization for difference operation
     * If the bbox of a multipolygon that's part of the clipping doesn't
     * intersect the bbox of the subject at all, we can just drop that
     * multiploygon. */
    if (this.type == OpType.DIFFERENCE) {
      // in place removal
      MultiPolyIn subject = multipolys.get(0);
      int i = 1;
      while (i < multipolys.size()) {
        if (Bbox.getBboxOverlap(multipolys.get(i).bbox, subject.bbox) != null) i++;
        else multipolys.remove(i);
      }
    }

    /* BBox optimization for intersection operation
     * If we can find any pair of multipolygons whose bbox does not overlap,
     * then the result will be empty. */
    if (this.type == OpType.INTERSECTION) {
      // TODO: this is O(n^2) in number of polygons. By sorting the bboxes,
      //       it could be optimized to O(n * ln(n))
      for (int i = 0, iMax = multipolys.size(); i < iMax; i++) {
        MultiPolyIn mpA = multipolys.get(i);
        for (int j = i + 1, jMax = multipolys.size(); j < jMax; j++) {
          if (Bbox.getBboxOverlap(mpA.bbox, multipolys.get(j).bbox) == null) {
            return new MultiPolygon(new double[0][][][]);
          }
        }
      }
    }

    /* Put segment endpoints in a priority queue */
    EventQueue queue = new EventQueue();
    for (int i = 0, iMax = multipolys.size(); i < iMax; i++) {
      List<SweepEvent> sweepEvents = multipolys.get(i).getSweepEvents();
      for (int j = 0, jMax = sweepEvents.size(); j < jMax; j++) {
        queue.insert(sweepEvents.get(j));

        if (queue.size() > MAX_QUEUE_SIZE) {
          // prevents an infinite loop, an otherwise common manifestation of bugs
          throw new IllegalStateException(
              "Infinite loop when putting segment endpoints in a priority queue "
                  + "(queue size too big).");
        }
      }
    }

    /* Pass the sweep line over those endpoints */
    SweepLine sweepLine = new SweepLine(queue);
    long prevQueueSize = queue.size();
    SweepEvent evt = queue.pop();
    while (evt != null) {
      if (queue.size() == prevQueueSize) {
        // prevents an infinite loop, an otherwise common manifestation of bugs
        Segment seg = evt.segment;
        throw new IllegalStateException(
            "Unable to pop() "
                + (evt.isLeft ? "left" : "right")
                + " SweepEvent ["
                + evt.point.x
                + ", "
                + evt.point.y
                + "] from segment #"
                + seg.id
                + " ["
                + seg.leftSE.point.x
                + ", "
                + seg.leftSE.point.y
                + "] -> ["
                + seg.rightSE.point.x
                + ", "
                + seg.rightSE.point.y
                + "] from queue.");
      }

      if (queue.size() > MAX_QUEUE_SIZE) {
        // prevents an infinite loop, an otherwise common manifestation of bugs
        throw new IllegalStateException(
            "Infinite loop when passing sweep line over endpoints (queue size too big).");
      }

      if (sweepLine.segments.size() > MAX_SWEEPLINE_SEGMENTS) {
        // prevents an infinite loop, an otherwise common manifestation of bugs
        throw new IllegalStateException(
            "Infinite loop when passing sweep line over endpoints "
                + "(too many sweep line segments).");
      }

      List<SweepEvent> newEvents = sweepLine.process(evt);
      for (int i = 0, iMax = newEvents.size(); i < iMax; i++) {
        SweepEvent newEvt = newEvents.get(i);
        if (newEvt.consumedBy == null) queue.insert(newEvt);
      }
      prevQueueSize = queue.size();
      evt = queue.pop();
    }

    /* Collect and compile segments we're keeping into a multipolygon */
    List<RingOut> ringsOut = RingOut.factory(sweepLine.segments);
    MultiPolyOut result = new MultiPolyOut(ringsOut);
    return new MultiPolygon(result.getGeom());
  }
}
