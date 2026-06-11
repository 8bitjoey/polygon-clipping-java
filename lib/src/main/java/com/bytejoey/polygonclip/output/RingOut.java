package com.bytejoey.polygonclip.output;

import com.bytejoey.polygonclip.TraceLog;
import com.bytejoey.polygonclip.num.Vec;
import com.bytejoey.polygonclip.num.Vector;
import com.bytejoey.polygonclip.sweep.Segment;
import com.bytejoey.polygonclip.sweep.SweepEvent;
import com.bytejoey.polygonclip.sweep.SweepPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RingOut {

  /* Given the segments from the sweep line pass, compute & return a series
   * of closed rings from all the segments marked to be part of the result */
  public static List<RingOut> factory(List<Segment> allSegments) {
    List<RingOut> ringsOut = new ArrayList<>();

    for (int i = 0, iMax = allSegments.size(); i < iMax; i++) {
      Segment segment = allSegments.get(i);
      if (!segment.isInResult() || segment.ringOut != null) continue;
      if (TraceLog.enabled) TraceLog.line("RING start=" + segment.id);

      SweepEvent prevEvent = null;
      SweepEvent event = segment.leftSE;
      SweepEvent nextEvent = segment.rightSE;
      List<SweepEvent> events = new ArrayList<>();
      events.add(event);

      SweepPoint startingPoint = event.point;
      List<IntersectionLE> intersectionLEs = new ArrayList<>();

      /* Walk the chain of linked events to form a closed ring */
      while (true) {
        prevEvent = event;
        event = nextEvent;
        events.add(event);

        /* Is the ring complete? */
        if (event.point == startingPoint) break;

        while (true) {
          List<SweepEvent> availableLEs = event.getAvailableLinkedEvents();

          /* Did we hit a dead end? This shouldn't happen.
           * Indicates some earlier part of the algorithm malfunctioned. */
          if (availableLEs.size() == 0) {
            SweepPoint firstPt = events.get(0).point;
            SweepPoint lastPt = events.get(events.size() - 1).point;
            throw new IllegalStateException(
                "Unable to complete output ring starting at ["
                    + firstPt.x
                    + ", "
                    + firstPt.y
                    + "]. Last matching segment found ends at ["
                    + lastPt.x
                    + ", "
                    + lastPt.y
                    + "].");
          }

          /* Only one way to go, so cotinue on the path */
          if (availableLEs.size() == 1) {
            nextEvent = availableLEs.get(0).otherSE;
            break;
          }

          /* We must have an intersection. Check for a completed loop */
          int indexLE = -1;
          for (int j = 0, jMax = intersectionLEs.size(); j < jMax; j++) {
            if (intersectionLEs.get(j).point == event.point) {
              indexLE = j;
              break;
            }
          }
          /* Found a completed loop. Cut that off and make a ring */
          if (indexLE != -1) {
            // upstream: intersectionLEs.splice(indexLE)[0] — capture the entry, then
            // remove it AND everything after it; deeper registrations index into the
            // events suffix being cut off below, so remove-to-end is load-bearing
            IntersectionLE intersectionLE = intersectionLEs.get(indexLE);
            if (TraceLog.enabled) TraceLog.line("RINGCUT idx=" + intersectionLE.index);
            intersectionLEs.subList(indexLE, intersectionLEs.size()).clear();
            // upstream: events.splice(intersectionLE.index) — cut the sub-loop out
            List<SweepEvent> suffix = events.subList(intersectionLE.index, events.size());
            List<SweepEvent> ringEvents = new ArrayList<>(suffix);
            suffix.clear();
            ringEvents.add(0, ringEvents.get(0).otherSE);
            Collections.reverse(ringEvents);
            ringsOut.add(new RingOut(ringEvents));
            if (TraceLog.enabled) TraceLog.line("RINGDONE size=" + ringEvents.size());
            continue;
          }
          /* register the intersection */
          intersectionLEs.add(new IntersectionLE(events.size(), event.point));
          /* Choose the left-most option to continue the walk */
          Comparator<SweepEvent> comparator = event.getLeftmostComparator(prevEvent);
          availableLEs.sort(comparator);
          nextEvent = availableLEs.get(0).otherSE;
          break;
        }
      }

      ringsOut.add(new RingOut(events));
      if (TraceLog.enabled) TraceLog.line("RINGDONE size=" + events.size());
    }
    return ringsOut;
  }

  /** upstream's {index, point} intersection registration literal */
  private static final class IntersectionLE {
    final int index;
    final SweepPoint point;

    IntersectionLE(int index, SweepPoint point) {
      this.index = index;
      this.point = point;
    }
  }

  public final List<SweepEvent> events;
  public PolyOut poly;
  public Boolean isExteriorRingMemo;
  public RingOut enclosingRingMemo;
  public boolean enclosingRingComputed;

  public RingOut(List<SweepEvent> events) {
    this.events = events;
    for (int i = 0, iMax = events.size(); i < iMax; i++) {
      events.get(i).segment.ringOut = this;
    }
    this.poly = null;
  }

  public double[][] getGeom() {
    // Remove superfluous points (ie extra points along a straight line),
    SweepPoint prevPt = this.events.get(0).point;
    List<SweepPoint> points = new ArrayList<>();
    points.add(prevPt);
    for (int i = 1, iMax = this.events.size() - 1; i < iMax; i++) {
      SweepPoint pt = this.events.get(i).point;
      SweepPoint nextPt = this.events.get(i + 1).point;
      if (Vector.compareVectorAngles(
              new Vec(pt.x, pt.y), new Vec(prevPt.x, prevPt.y), new Vec(nextPt.x, nextPt.y))
          == 0) continue;
      points.add(pt);
      prevPt = pt;
    }

    // ring was all (within rounding error of angle calc) colinear points
    if (points.size() == 1) return null;

    // check if the starting point is necessary
    SweepPoint pt = points.get(0);
    SweepPoint nextPt = points.get(1);
    if (Vector.compareVectorAngles(
            new Vec(pt.x, pt.y), new Vec(prevPt.x, prevPt.y), new Vec(nextPt.x, nextPt.y))
        == 0) points.remove(0);

    points.add(points.get(0));
    int step = this.isExteriorRing() ? 1 : -1;
    int iStart = this.isExteriorRing() ? 0 : points.size() - 1;
    int iEnd = this.isExteriorRing() ? points.size() : -1;
    List<double[]> orderedPoints = new ArrayList<>();
    for (int i = iStart; i != iEnd; i += step)
      orderedPoints.add(new double[] {points.get(i).x, points.get(i).y});
    return orderedPoints.toArray(new double[0][]);
  }

  public boolean isExteriorRing() {
    if (this.isExteriorRingMemo == null) {
      RingOut enclosing = this.enclosingRing();
      this.isExteriorRingMemo = enclosing != null ? !enclosing.isExteriorRing() : true;
    }
    return this.isExteriorRingMemo;
  }

  public RingOut enclosingRing() {
    if (!this.enclosingRingComputed) {
      this.enclosingRingMemo = this.calcEnclosingRing();
      this.enclosingRingComputed = true;
    }
    return this.enclosingRingMemo;
  }

  /* Returns the ring that encloses this one, if any */
  private RingOut calcEnclosingRing() {
    // start with the ealier sweep line event so that the prevSeg
    // chain doesn't lead us inside of a loop of ours
    SweepEvent leftMostEvt = this.events.get(0);
    for (int i = 1, iMax = this.events.size(); i < iMax; i++) {
      SweepEvent evt = this.events.get(i);
      if (SweepEvent.compare(leftMostEvt, evt) > 0) leftMostEvt = evt;
    }

    Segment prevSeg = leftMostEvt.segment.prevInResult();
    Segment prevPrevSeg = prevSeg != null ? prevSeg.prevInResult() : null;

    while (true) {
      // no segment found, thus no ring can enclose us
      if (prevSeg == null) return null;

      // no segments below prev segment found, thus the ring of the prev
      // segment must loop back around and enclose us
      if (prevPrevSeg == null) return prevSeg.ringOut;

      // if the two segments are of different rings, the ring of the prev
      // segment must either loop around us or the ring of the prev prev
      // seg, which would make us and the ring of the prev peers
      if (prevPrevSeg.ringOut != prevSeg.ringOut) {
        if (prevPrevSeg.ringOut.enclosingRing() != prevSeg.ringOut) {
          return prevSeg.ringOut;
        } else return prevSeg.ringOut.enclosingRing();
      }

      // two segments are from the same ring, so this was a penisula
      // of that ring. iterate downward, keep searching
      prevSeg = prevPrevSeg.prevInResult();
      prevPrevSeg = prevSeg != null ? prevSeg.prevInResult() : null;
    }
  }
}
