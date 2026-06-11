package com.bytejoey.polygonclip.sweep;

import com.bytejoey.polygonclip.OpType;
import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.TraceLog;
import com.bytejoey.polygonclip.input.MultiPolyIn;
import com.bytejoey.polygonclip.input.PolyIn;
import com.bytejoey.polygonclip.input.RingIn;
import com.bytejoey.polygonclip.num.Vec;
import com.bytejoey.polygonclip.num.Vector;
import com.bytejoey.polygonclip.output.RingOut;
import com.bytejoey.polygonclip.util.Bbox;

import java.util.ArrayList;
import java.util.List;

/** Port of segment.js. Fully ported. */
public class Segment {

  public final Operation op;
  public final int id;
  public SweepEvent leftSE;
  public SweepEvent rightSE;
  public List<RingIn> rings;
  public List<Integer> windings;

  // left unset for performance, set later in algorithm (segment.js:146-147);
  // null = upstream undefined
  public Segment prev; // sweep-line.js:130
  public Segment consumedBy; // consume(), segment.js:437
  public RingOut ringOut; // geom-out.js:88

  // status-tree node handle while tree-resident, null otherwise (perf round 2:
  // replaces the right-event find descent and all remove-by-key descents with
  // pointer surgery; SweepLine owns set/clear)
  RbTree.Node<Segment> statusNode;

  // lazy memos of the state chain (segment.js:446,454,469,522); null = uncomputed.
  // prevInResult is tri-state upstream (undefined | null | Segment), hence the
  // separate computed flag.
  Segment prevInResultMemo;
  boolean prevInResultComputed;
  State beforeStateMemo;
  State afterStateMemo;
  // upstream tests write segment._isInResult from outside the module; JS underscore privacy
  // is convention only, the port mirrors the actual accessibility (geom-out.test.js)
  public Boolean isInResultMemo;

  /** Sweep-position state: which rings/multipolys are open below->here. */
  public static final class State {
    public final List<RingIn> rings;
    public final List<Integer> windings;
    public final List<MultiPolyIn> multiPolys;

    State(List<RingIn> rings, List<Integer> windings, List<MultiPolyIn> multiPolys) {
      this.rings = rings;
      this.windings = windings;
      this.multiPolys = multiPolys;
    }
  }

  /* This compare() function is for ordering segments in the sweep
   * line tree, and does so according to the following criteria:
   *
   * Consider the vertical line that lies an infinestimal step to the
   * right of the right-more of the two left endpoints of the input
   * segments. Imagine slowly moving a point up from negative infinity
   * in the increasing y direction. Which of the two segments will that
   * point intersect first? That segment comes 'before' the other one.
   *
   * If neither segment would be intersected by such a line, (if one
   * or more of the segments are vertical) then the line to be considered
   * is directly on the right-more of the two left inputs.
   */
  public static int compare(Segment a, Segment b) {
    double alx = a.leftSE.point.x;
    double blx = b.leftSE.point.x;
    double arx = a.rightSE.point.x;
    double brx = b.rightSE.point.x;

    // check if they're even in the same vertical plane
    if (brx < alx) return 1;
    if (arx < blx) return -1;

    double aly = a.leftSE.point.y;
    double bly = b.leftSE.point.y;
    double ary = a.rightSE.point.y;
    double bry = b.rightSE.point.y;

    // is left endpoint of segment B the right-more?
    if (alx < blx) {
      // are the two segments in the same horizontal plane?
      if (bly < aly && bly < ary) return 1;
      if (bly > aly && bly > ary) return -1;

      // is the B left endpoint colinear to segment A?
      int aCmpBLeft = a.comparePoint(b.leftSE.point);
      if (aCmpBLeft < 0) return 1;
      if (aCmpBLeft > 0) return -1;

      // is the A right endpoint colinear to segment B ?
      int bCmpARight = b.comparePoint(a.rightSE.point);
      if (bCmpARight != 0) return bCmpARight;

      // colinear segments, consider the one with left-more
      // left endpoint to be first (arbitrary?)
      return -1;
    }

    // is left endpoint of segment A the right-more?
    if (alx > blx) {
      if (aly < bly && aly < bry) return -1;
      if (aly > bly && aly > bry) return 1;

      // is the A left endpoint colinear to segment B?
      int bCmpALeft = b.comparePoint(a.leftSE.point);
      if (bCmpALeft != 0) return bCmpALeft;

      // is the B right endpoint colinear to segment A?
      int aCmpBRight = a.comparePoint(b.rightSE.point);
      if (aCmpBRight < 0) return 1;
      if (aCmpBRight > 0) return -1;

      // colinear segments, consider the one with left-more
      // left endpoint to be first (arbitrary?)
      return 1;
    }

    // if we get here, the two left endpoints are in the same
    // vertical plane, ie alx === blx

    // consider the lower left-endpoint to come first
    if (aly < bly) return -1;
    if (aly > bly) return 1;

    // left endpoints are identical
    // check for colinearity by using the left-more right endpoint

    // is the A right endpoint more left-more?
    if (arx < brx) {
      int bCmpARight = b.comparePoint(a.rightSE.point);
      if (bCmpARight != 0) return bCmpARight;
    }

    // is the B right endpoint more left-more?
    if (arx > brx) {
      int aCmpBRight = a.comparePoint(b.rightSE.point);
      if (aCmpBRight < 0) return 1;
      if (aCmpBRight > 0) return -1;
    }

    if (arx != brx) {
      // are these two [almost] vertical segments with opposite orientation?
      // if so, the one with the lower right endpoint comes first
      double ay = ary - aly;
      double ax = arx - alx;
      double by = bry - bly;
      double bx = brx - blx;
      if (ay > ax && by < bx) return 1;
      if (ay < ax && by > bx) return -1;
    }

    // we have colinear segments with matching orientation
    // consider the one with more left-more right endpoint to be first
    if (arx > brx) return 1;
    if (arx < brx) return -1;

    // if we get here, two two right endpoints are in the same
    // vertical plane, ie arx === brx

    // consider the lower right-endpoint to come first
    if (ary < bry) return -1;
    if (ary > bry) return 1;

    // right endpoints identical as well, so the segments are idential
    // fall back on creation order as consistent tie-breaker
    if (a.id < b.id) return -1;
    if (a.id > b.id) return 1;

    // identical segment, ie a === b
    return 0;
  }

  /* Warning: a reference to ringWindings input will be stored,
   *  and possibly will be later modified */
  public Segment(
      SweepEvent leftSE,
      SweepEvent rightSE,
      List<RingIn> rings,
      List<Integer> windings,
      Operation op) {
    this.op = op;
    this.id = op.nextSegmentId();
    this.leftSE = leftSE;
    leftSE.segment = this;
    leftSE.otherSE = rightSE;
    this.rightSE = rightSE;
    rightSE.segment = this;
    rightSE.otherSE = leftSE;
    this.rings = rings;
    this.windings = windings;
    // left unset for performance, set later in algorithm
    // this.ringOut, this.consumedBy, this.prev
  }

  public static Segment fromRing(SweepPoint pt1, SweepPoint pt2, RingIn ring, Operation op) {
    SweepPoint leftPt;
    SweepPoint rightPt;
    int winding;

    // ordering the two points according to sweep line ordering
    int cmpPts = SweepEvent.comparePoints(pt1, pt2);
    if (cmpPts < 0) {
      leftPt = pt1;
      rightPt = pt2;
      winding = 1;
    } else if (cmpPts > 0) {
      leftPt = pt2;
      rightPt = pt1;
      winding = -1;
    } else {
      throw new IllegalArgumentException(
          "Tried to create degenerate segment at [" + pt1.x + ", " + pt1.y + "]");
    }

    SweepEvent leftSE = new SweepEvent(leftPt, true);
    SweepEvent rightSE = new SweepEvent(rightPt, false);
    List<RingIn> rings = new ArrayList<>();
    rings.add(ring);
    List<Integer> windings = new ArrayList<>();
    windings.add(winding);
    return new Segment(leftSE, rightSE, rings, windings, op);
  }

  /* When a segment is split, the rightSE is replaced with a new sweep event */
  public void replaceRightSE(SweepEvent newRightSE) {
    this.rightSE = newRightSE;
    this.rightSE.segment = this;
    this.rightSE.otherSE = this.leftSE;
    this.leftSE.otherSE = this.rightSE;
  }

  public Bbox bbox() {
    double y1 = this.leftSE.point.y;
    double y2 = this.rightSE.point.y;
    return new Bbox(
        new Vec(this.leftSE.point.x, y1 < y2 ? y1 : y2),
        new Vec(this.rightSE.point.x, y1 > y2 ? y1 : y2));
  }

  /* A vector from the left point to the right */
  public Vec vector() {
    return new Vec(
        this.rightSE.point.x - this.leftSE.point.x,
        this.rightSE.point.y - this.leftSE.point.y);
  }

  public boolean isAnEndpoint(SweepPoint pt) {
    return (pt.x == this.leftSE.point.x && pt.y == this.leftSE.point.y)
        || (pt.x == this.rightSE.point.x && pt.y == this.rightSE.point.y);
  }

  /* Compare this segment with a point.
   *
   * A point P is considered to be colinear to a segment if there
   * exists a distance D such that if we travel along the segment
   * from one * endpoint towards the other a distance D, we find
   * ourselves at point P.
   *
   * Return value indicates:
   *
   *   1: point lies above the segment (to the left of vertical)
   *   0: point is colinear to segment
   *  -1: point lies below the segment (to the right of vertical)
   */
  public int comparePoint(SweepPoint point) {
    if (isAnEndpoint(point)) return 0;

    SweepPoint lPt = this.leftSE.point;
    SweepPoint rPt = this.rightSE.point;
    // vector(), inlined to avoid allocating on the comparator hot path
    double vx = rPt.x - lPt.x;
    double vy = rPt.y - lPt.y;

    // Exactly vertical segments.
    if (lPt.x == rPt.x) {
      if (point.x == lPt.x) return 0;
      return point.x < lPt.x ? 1 : -1;
    }

    // Nearly vertical segments with an intersection.
    // Check to see where a point on the line with matching Y coordinate is.
    double yDist = (point.y - lPt.y) / vy;
    double xFromYDist = lPt.x + yDist * vx;
    if (point.x == xFromYDist) return 0;

    // General case.
    // Check to see where a point on the line with matching X coordinate is.
    double xDist = (point.x - lPt.x) / vx;
    double yFromXDist = lPt.y + xDist * vy;
    if (point.y == yFromXDist) return 0;
    return point.y < yFromXDist ? -1 : 1;
  }

  /* Given another segment, returns the first non-trivial intersection
   * between the two segments (in terms of sweep line ordering), if it exists.
   *
   * A 'non-trivial' intersection is one that will cause one or both of the
   * segments to be split(). As such, 'trivial' vs. 'non-trivial' intersection:
   *
   *   * endpoint of segA with endpoint of segB --> trivial
   *   * endpoint of segA with point along segB --> non-trivial
   *   * endpoint of segB with point along segA --> non-trivial
   *   * point along segA with point along segB --> non-trivial
   *
   * If no non-trivial intersection exists, return null
   * Else, return null.
   */
  public SweepPoint getIntersection(Segment other) {
    SweepPoint tlp = this.leftSE.point;
    SweepPoint trp = this.rightSE.point;
    SweepPoint olp = other.leftSE.point;
    SweepPoint orp = other.rightSE.point;

    // If bboxes don't overlap, there can't be any intersections.
    // bbox() and getBboxOverlap(), inlined to local doubles — same comparisons and
    // ternaries, zero allocation on this hot path (perf step 1).
    double tlly = tlp.y < trp.y ? tlp.y : trp.y;
    double tury = tlp.y > trp.y ? tlp.y : trp.y;
    double olly = olp.y < orp.y ? olp.y : orp.y;
    double oury = olp.y > orp.y ? olp.y : orp.y;
    if (orp.x < tlp.x || trp.x < olp.x || oury < tlly || tury < olly) return null;

    // find the middle two X / Y values — the overlap bbox
    double lowerX = tlp.x < olp.x ? olp.x : tlp.x;
    double upperX = trp.x < orp.x ? trp.x : orp.x;
    double lowerY = tlly < olly ? olly : tlly;
    double upperY = tury < oury ? tury : oury;

    // We first check to see if the endpoints can be considered intersections.
    // This will 'snap' intersections to endpoints if possible, and will
    // handle cases of colinearity.

    // does each endpoint touch the other segment?
    // note that we restrict the 'touching' definition to only allow segments
    // to touch endpoints that lie forward from where we are in the sweep line pass
    boolean touchesOtherLSE =
        Bbox.isInBbox(tlp.x, tlly, trp.x, tury, olp.x, olp.y) && this.comparePoint(olp) == 0;
    boolean touchesThisLSE =
        Bbox.isInBbox(olp.x, olly, orp.x, oury, tlp.x, tlp.y) && other.comparePoint(tlp) == 0;
    boolean touchesOtherRSE =
        Bbox.isInBbox(tlp.x, tlly, trp.x, tury, orp.x, orp.y) && this.comparePoint(orp) == 0;
    boolean touchesThisRSE =
        Bbox.isInBbox(olp.x, olly, orp.x, oury, trp.x, trp.y) && other.comparePoint(trp) == 0;

    // do left endpoints match?
    if (touchesThisLSE && touchesOtherLSE) {
      // these two cases are for colinear segments with matching left
      // endpoints, and one segment being longer than the other
      if (touchesThisRSE && !touchesOtherRSE) return trp;
      if (!touchesThisRSE && touchesOtherRSE) return orp;
      // either the two segments match exactly (two trival intersections)
      // or just on their left endpoint (one trivial intersection
      return null;
    }

    // does this left endpoint matches (other doesn't)
    if (touchesThisLSE) {
      // check for segments that just intersect on opposing endpoints
      if (touchesOtherRSE) {
        if (tlp.x == orp.x && tlp.y == orp.y) return null;
      }
      // t-intersection on left endpoint
      return tlp;
    }

    // does other left endpoint matches (this doesn't)
    if (touchesOtherLSE) {
      // check for segments that just intersect on opposing endpoints
      if (touchesThisRSE) {
        if (trp.x == olp.x && trp.y == olp.y) return null;
      }
      // t-intersection on left endpoint
      return olp;
    }

    // trivial intersection on right endpoints
    if (touchesThisRSE && touchesOtherRSE) return null;

    // t-intersections on just one right endpoint
    if (touchesThisRSE) return trp;
    if (touchesOtherRSE) return orp;

    // None of our endpoints intersect. Look for a general intersection between
    // infinite lines laid over the segments
    Vec pt =
        Vector.intersection(
            tlp.x, tlp.y, trp.x - tlp.x, trp.y - tlp.y,
            olp.x, olp.y, orp.x - olp.x, orp.y - olp.y);

    // are the segments parrallel? Note that if they were colinear with overlap,
    // they would have an endpoint intersection and that case was already handled above
    if (pt == null) return null;

    // is the intersection found between the lines not on the segments?
    if (!Bbox.isInBbox(lowerX, lowerY, upperX, upperY, pt.x(), pt.y())) return null;

    // round the the computed point if needed
    return op.rounder.round(pt.x(), pt.y());
  }

  /* Split the given segment into multiple segments on the given points.
   *  * Each existing segment will retain its leftSE and a new rightSE will be
   *    generated for it.
   *  * A new segment will be generated which will adopt the original segment's
   *    rightSE, and a new leftSE will be generated for it.
   *  * If there are more than two points given to split on, new segments
   *    in the middle will be generated with new leftSE and rightSE's.
   *  * An array of the newly generated SweepEvents will be returned.
   */
  public List<SweepEvent> split(SweepPoint point) {
    List<SweepEvent> newEvents = new ArrayList<>();
    boolean alreadyLinked = point.events != null;

    SweepEvent newLeftSE = new SweepEvent(point, true);
    SweepEvent newRightSE = new SweepEvent(point, false);
    SweepEvent oldRightSE = this.rightSE;
    this.replaceRightSE(newRightSE);
    newEvents.add(newRightSE);
    newEvents.add(newLeftSE);
    Segment newSeg =
        new Segment(
            newLeftSE,
            oldRightSE,
            new ArrayList<>(this.rings),
            new ArrayList<>(this.windings),
            this.op);
    if (TraceLog.enabled) TraceLog.line("SPLIT seg=" + this.id + " x=" + TraceLog.bits(point.x) + " y=" + TraceLog.bits(point.y) + " new=" + newSeg.id);

    // when splitting a nearly vertical downward-facing segment,
    // sometimes one of the resulting new segments is vertical, in which
    // case its left and right events may need to be swapped
    if (SweepEvent.comparePoints(newSeg.leftSE.point, newSeg.rightSE.point) > 0) {
      newSeg.swapEvents();
    }
    if (SweepEvent.comparePoints(this.leftSE.point, this.rightSE.point) > 0) {
      this.swapEvents();
    }

    // in the point we just used to create new sweep events with was already
    // linked to other events, we need to check if either of the affected
    // segments should be consumed
    if (alreadyLinked) {
      newLeftSE.checkForConsuming();
      newRightSE.checkForConsuming();
    }

    return newEvents;
  }

  /* Swap which event is left and right */
  public void swapEvents() {
    if (TraceLog.enabled) TraceLog.line("SWAP seg=" + this.id);
    SweepEvent tmpEvt = this.rightSE;
    this.rightSE = this.leftSE;
    this.leftSE = tmpEvt;
    this.leftSE.isLeft = true;
    this.rightSE.isLeft = false;
    for (int i = 0, iMax = this.windings.size(); i < iMax; i++) {
      this.windings.set(i, this.windings.get(i) * -1);
    }
  }

  /* Consume another segment. We take their rings under our wing
   * and mark them as consumed. Use for perfectly overlapping segments */
  public void consume(Segment other) {
    Segment consumer = this;
    Segment consumee = other;
    while (consumer.consumedBy != null) consumer = consumer.consumedBy;
    while (consumee.consumedBy != null) consumee = consumee.consumedBy;

    int cmp = Segment.compare(consumer, consumee);
    if (cmp == 0) return; // already consumed
    // the winner of the consumption is the earlier segment
    // according to sweep line ordering
    if (cmp > 0) {
      Segment tmp = consumer;
      consumer = consumee;
      consumee = tmp;
    }

    // make sure a segment doesn't consume it's prev
    if (consumer.prev == consumee) {
      Segment tmp = consumer;
      consumer = consumee;
      consumee = tmp;
    }

    if (TraceLog.enabled) TraceLog.line("CONSUME consumer=" + consumer.id + " consumee=" + consumee.id);
    for (int i = 0, iMax = consumee.rings.size(); i < iMax; i++) {
      RingIn ring = consumee.rings.get(i);
      Integer winding = consumee.windings.get(i);
      int index = consumer.rings.indexOf(ring);
      if (index == -1) {
        consumer.rings.add(ring);
        consumer.windings.add(winding);
      } else consumer.windings.set(index, consumer.windings.get(index) + winding);
    }
    consumee.rings = null;
    consumee.windings = null;
    consumee.consumedBy = consumer;

    // mark sweep events consumed as to maintain ordering in sweep event queue
    consumee.leftSE.consumedBy = consumer.leftSE;
    consumee.rightSE.consumedBy = consumer.rightSE;
  }

  /* The first segment previous segment chain that is in the result */
  public Segment prevInResult() {
    if (this.prevInResultComputed) return this.prevInResultMemo;
    List<Segment> pending = new ArrayList<>();
    Segment cur = this;
    Segment answer;
    while (true) {
      if (cur.prevInResultComputed) {
        answer = cur.prevInResultMemo;
        break;
      }
      pending.add(cur);
      if (cur.prev == null) {
        answer = null;
        break;
      }
      if (cur.prev.isInResult()) {
        answer = cur.prev;
        break;
      }
      cur = cur.prev;
    }
    for (Segment s : pending) {
      s.prevInResultMemo = answer;
      s.prevInResultComputed = true;
    }
    return answer;
  }

  public State beforeState() {
    if (this.beforeStateMemo != null) return this.beforeStateMemo;
    if (this.prev == null)
      this.beforeStateMemo = new State(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    else {
      Segment seg = this.prev.consumedBy != null ? this.prev.consumedBy : this.prev;
      this.beforeStateMemo = seg.afterState();
    }
    return this.beforeStateMemo;
  }

  public State afterState() {
    if (this.afterStateMemo != null) return this.afterStateMemo;

    State beforeState = this.beforeState();
    this.afterStateMemo =
        new State(
            new ArrayList<>(beforeState.rings),
            new ArrayList<>(beforeState.windings),
            new ArrayList<>());
    List<RingIn> ringsAfter = this.afterStateMemo.rings;
    List<Integer> windingsAfter = this.afterStateMemo.windings;
    List<MultiPolyIn> mpsAfter = this.afterStateMemo.multiPolys;

    // calculate ringsAfter, windingsAfter
    for (int i = 0, iMax = this.rings.size(); i < iMax; i++) {
      RingIn ring = this.rings.get(i);
      Integer winding = this.windings.get(i);
      int index = ringsAfter.indexOf(ring);
      if (index == -1) {
        ringsAfter.add(ring);
        windingsAfter.add(winding);
      } else windingsAfter.set(index, windingsAfter.get(index) + winding);
    }

    // calcualte polysAfter
    List<PolyIn> polysAfter = new ArrayList<>();
    List<PolyIn> polysExclude = new ArrayList<>();
    for (int i = 0, iMax = ringsAfter.size(); i < iMax; i++) {
      if (windingsAfter.get(i) == 0) continue; // non-zero rule
      RingIn ring = ringsAfter.get(i);
      PolyIn poly = ring.poly;
      if (polysExclude.indexOf(poly) != -1) continue;
      if (ring.isExterior) polysAfter.add(poly);
      else {
        if (polysExclude.indexOf(poly) == -1) polysExclude.add(poly);
        int index = polysAfter.indexOf(ring.poly);
        if (index != -1) polysAfter.remove(index);
      }
    }

    // calculate multiPolysAfter
    for (int i = 0, iMax = polysAfter.size(); i < iMax; i++) {
      MultiPolyIn mp = polysAfter.get(i).multiPoly;
      if (mpsAfter.indexOf(mp) == -1) mpsAfter.add(mp);
    }

    return this.afterStateMemo;
  }

  /* Is this segment part of the final result? */
  public boolean isInResult() {
    // if we've been consumed, we're not in the result
    if (this.consumedBy != null) return false;

    if (this.isInResultMemo != null) return this.isInResultMemo;

    int mpsBefore = this.beforeState().multiPolys.size();
    int mpsAfter = this.afterState().multiPolys.size();

    switch (this.op.type) {
      case UNION: {
        // UNION - included iff:
        //  * On one side of us there is 0 poly interiors AND
        //  * On the other side there is 1 or more.
        boolean noBefores = mpsBefore == 0;
        boolean noAfters = mpsAfter == 0;
        this.isInResultMemo = noBefores != noAfters;
        break;
      }

      case INTERSECTION: {
        // INTERSECTION - included iff:
        //  * on one side of us all multipolys are rep. with poly interiors AND
        //  * on the other side of us, not all multipolys are repsented
        //    with poly interiors
        int least;
        int most;
        if (mpsBefore < mpsAfter) {
          least = mpsBefore;
          most = mpsAfter;
        } else {
          least = mpsAfter;
          most = mpsBefore;
        }
        this.isInResultMemo = most == this.op.numMultiPolys && least < most;
        break;
      }

      case XOR: {
        // XOR - included iff:
        //  * the difference between the number of multipolys represented
        //    with poly interiors on our two sides is an odd number
        int diff = Math.abs(mpsBefore - mpsAfter);
        this.isInResultMemo = diff % 2 == 1;
        break;
      }

      case DIFFERENCE: {
        // DIFFERENCE included iff:
        //  * on exactly one side, we have just the subject
        this.isInResultMemo =
            isJustSubject(this.beforeState().multiPolys)
                != isJustSubject(this.afterState().multiPolys);
        break;
      }
    }

    if (TraceLog.enabled) TraceLog.line("INRESULT seg=" + this.id + " val=" + (this.isInResultMemo ? "t" : "f"));
    return this.isInResultMemo;
  }

  private static boolean isJustSubject(List<MultiPolyIn> mps) {
    return mps.size() == 1 && mps.get(0).isSubject;
  }
}
