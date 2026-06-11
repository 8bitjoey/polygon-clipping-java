package com.bytejoey.polygonclip.sweep;

import com.bytejoey.polygonclip.TraceLog;
import com.bytejoey.polygonclip.num.Vec;
import com.bytejoey.polygonclip.num.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of sweep-event.js. Null on segment/otherSE/consumedBy = upstream undefined.
 *
 * compare() links equal-coordinate events as a side effect, verbatim upstream behavior
 * (sweep-event.js:12). The event queue will additionally link at pop per the port's
 * deviation 1 (DEVIATIONS.md) — that lives in the queue, not here.
 */
public class SweepEvent {

  // for ordering sweep events in the sweep event queue
  public static int compare(SweepEvent a, SweepEvent b) {
    // favor event with a point that the sweep line hits first
    int ptCmp = comparePoints(a.point, b.point);
    if (ptCmp != 0) return ptCmp;

    // the points are the same, so link them if needed
    if (a.point != b.point) a.link(b);

    // favor right events over left
    if (a.isLeft != b.isLeft) return a.isLeft ? 1 : -1;

    // we have two matching left or right endpoints
    // ordering of this case is the same as for their segments
    return Segment.compare(a.segment, b.segment);
  }

  // for ordering points in sweep line order
  public static int comparePoints(SweepPoint aPt, SweepPoint bPt) {
    if (aPt.x < bPt.x) return -1;
    if (aPt.x > bPt.x) return 1;

    if (aPt.y < bPt.y) return -1;
    if (aPt.y > bPt.y) return 1;

    return 0;
  }

  public SweepPoint point;
  public boolean isLeft;
  public Segment segment;
  public SweepEvent otherSE;
  public SweepEvent consumedBy;

  // event-queue node handle while queued, null otherwise (perf round 2: queue
  // removals become pointer surgery instead of comparator descents; EventQueue
  // owns set/clear)
  RbTree.Node<SweepEvent> queueNode;

  /* Warning: 'point' input will be modified and re-used (for performance) */
  public SweepEvent(SweepPoint point, boolean isLeft) {
    if (point.events == null) point.events = new ArrayList<>();
    point.events.add(this);
    this.point = point;
    this.isLeft = isLeft;
    // this.segment, this.otherSE set by factory
  }

  public void link(SweepEvent other) {
    if (other.point == this.point) {
      throw new IllegalStateException("Tried to link already linked events");
    }
    if (TraceLog.enabled) TraceLog.line("LINK keep=" + this.segment.id + ":" + (this.isLeft ? "L" : "R") + " from=" + other.segment.id + ":" + (other.isLeft ? "L" : "R"));
    List<SweepEvent> otherEvents = other.point.events;
    for (int i = 0, iMax = otherEvents.size(); i < iMax; i++) {
      SweepEvent evt = otherEvents.get(i);
      this.point.events.add(evt);
      evt.point = this.point;
    }
    this.checkForConsuming();
  }

  /* Do a pass over our linked events and check to see if any pair
   * of segments match, and should be consumed. */
  public void checkForConsuming() {
    // FIXME: The loops in this method run O(n^2) => no good.
    //        Maintain little ordered sweep event trees?
    //        Can we maintaining an ordering that avoids the need
    //        for the re-sorting with getLeftmostComparator in geom-out?

    // Compare each pair of events to see if other events also match
    int numEvents = this.point.events.size();
    for (int i = 0; i < numEvents; i++) {
      SweepEvent evt1 = this.point.events.get(i);
      if (evt1.segment.consumedBy != null) continue;
      for (int j = i + 1; j < numEvents; j++) {
        SweepEvent evt2 = this.point.events.get(j);
        if (evt2.consumedBy != null) continue;
        // reference != is deliberate: consumption requires the far endpoints to share
        // the SAME events list (array identity, sweep-event.js:71 — see
        // DEVIATIONS.md deviation 1), i.e. to be linked, not merely
        // equal-valued
        if (evt1.otherSE.point.events != evt2.otherSE.point.events) continue;
        evt1.segment.consume(evt2.segment);
      }
    }
  }

  public List<SweepEvent> getAvailableLinkedEvents() {
    // point.events is always of length 2 or greater
    List<SweepEvent> events = new ArrayList<>();
    for (int i = 0, iMax = this.point.events.size(); i < iMax; i++) {
      SweepEvent evt = this.point.events.get(i);
      if (evt != this && evt.segment.ringOut == null && evt.segment.isInResult()) {
        events.add(evt);
      }
    }
    return events;
  }

  /**
   * Returns a comparator function for sorting linked events that will
   * favor the event that will give us the smallest left-side angle.
   * All ring construction starts as low as possible heading to the right,
   * so by always turning left as sharp as possible we'll get polygons
   * without uncessary loops &amp; holes.
   *
   * The comparator function has a compute cache such that it avoids
   * re-computing already-computed values.
   */
  public Comparator<SweepEvent> getLeftmostComparator(SweepEvent baseEvent) {
    Map<SweepEvent, Angles> cache = new HashMap<>();

    return (a, b) -> {
      if (!cache.containsKey(a)) fillCache(cache, baseEvent, a);
      if (!cache.containsKey(b)) fillCache(cache, baseEvent, b);

      Angles aAngles = cache.get(a);
      Angles bAngles = cache.get(b);
      double asine = aAngles.sine();
      double acosine = aAngles.cosine();
      double bsine = bAngles.sine();
      double bcosine = bAngles.cosine();

      // both on or above x-axis
      if (asine >= 0 && bsine >= 0) {
        if (acosine < bcosine) return 1;
        if (acosine > bcosine) return -1;
        return 0;
      }

      // both below x-axis
      if (asine < 0 && bsine < 0) {
        if (acosine < bcosine) return -1;
        if (acosine > bcosine) return 1;
        return 0;
      }

      // one above x-axis, one below
      if (bsine < asine) return -1;
      if (bsine > asine) return 1;
      return 0;
    };
  }

  /** Cache entry of getLeftmostComparator (the JS {sine, cosine} literal). */
  private record Angles(double sine, double cosine) {}

  private void fillCache(Map<SweepEvent, Angles> cache, SweepEvent baseEvent, SweepEvent linkedEvent) {
    SweepEvent nextEvent = linkedEvent.otherSE;
    cache.put(
        linkedEvent,
        new Angles(
            Vector.sineOfAngle(vec(this.point), vec(baseEvent.point), vec(nextEvent.point)),
            Vector.cosineOfAngle(vec(this.point), vec(baseEvent.point), vec(nextEvent.point))));
  }

  private static Vec vec(SweepPoint pt) {
    return new Vec(pt.x, pt.y);
  }
}
