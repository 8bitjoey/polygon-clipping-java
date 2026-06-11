package com.bytejoey.polygonclip.sweep;

import com.bytejoey.polygonclip.TraceLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * NOTE:  We must be careful not to change any segments while
 *        they are in the SplayTree. AFAIK, there's no way to tell
 *        the tree to rebalance itself - thus before splitting
 *        a segment that's in the tree, we remove it from the tree,
 *        do the split, then re-insert it. (Even though splitting a
 *        segment *shouldn't* change its correct position in the
 *        sweep line tree, the reality is because of rounding errors,
 *        it sometimes does.)
 *
 * <p>Port note: node-handle navigation is now restored via {@link RbTree}.
 * {@code tree.insert}/{@code find} return a node handle; {@code prev(node)}/{@code next(node)}
 * walk parent/child pointers — zero comparator calls (the O(log n) lower/higher caveat
 * no longer applies).
 */
public class SweepLine {

  public final EventQueue queue;
  public final RbTree<Segment> tree;
  public final List<Segment> segments;

  public SweepLine(EventQueue queue) {
    this(queue, Segment::compare);
  }

  public SweepLine(EventQueue queue, Comparator<Segment> comparator) {
    this.queue = queue;
    this.tree = new RbTree<>(comparator);
    this.segments = new ArrayList<>();
  }

  public List<SweepEvent> process(SweepEvent event) {
    Segment segment = event.segment;
    List<SweepEvent> newEvents = new ArrayList<>();

    // if we've already been consumed by another segment,
    // clean up our body parts and get out
    if (event.consumedBy != null) {
      if (event.isLeft) this.queue.remove(event.otherSE);
      else removeFromTree(segment);
      return newEvents;
    }

    RbTree.Node<Segment> node;
    if (event.isLeft) {
      node = this.tree.insert(segment);
      segment.statusNode = node;
    } else {
      // perf round 2: the node handle stored at insertion replaces the find descent
      node = segment.statusNode;
    }

    if (node == null)
      throw new IllegalStateException(
          "Unable to find segment #"
              + segment.id
              + " ["
              + segment.leftSE.point.x
              + ", "
              + segment.leftSE.point.y
              + "] -> ["
              + segment.rightSE.point.x
              + ", "
              + segment.rightSE.point.y
              + "] in SweepLine tree.");

    RbTree.Node<Segment> prevNode = node;
    RbTree.Node<Segment> nextNode = node;
    Segment prevSeg = null;
    Segment nextSeg = null;

    // skip consumed segments still in tree
    while (true) {
      prevNode = this.tree.prev(prevNode);
      if (prevNode == null) { prevSeg = null; break; }
      if (prevNode.key.consumedBy == null) { prevSeg = prevNode.key; break; }
    }

    // skip consumed segments still in tree
    while (true) {
      nextNode = this.tree.next(nextNode);
      if (nextNode == null) { nextSeg = null; break; }
      if (nextNode.key.consumedBy == null) { nextSeg = nextNode.key; break; }
    }

    if (event.isLeft) {
      // Check for intersections against the previous segment in the sweep line
      SweepPoint prevMySplitter = null;
      if (prevSeg != null) {
        SweepPoint prevInter = prevSeg.getIntersection(segment);
        if (prevInter != null) {
          if (!segment.isAnEndpoint(prevInter)) prevMySplitter = prevInter;
          if (!prevSeg.isAnEndpoint(prevInter)) {
            List<SweepEvent> newEventsFromSplit = this.splitSafely(prevSeg, prevInter);
            for (int i = 0, iMax = newEventsFromSplit.size(); i < iMax; i++) {
              newEvents.add(newEventsFromSplit.get(i));
            }
          }
        }
      }

      // Check for intersections against the next segment in the sweep line
      SweepPoint nextMySplitter = null;
      if (nextSeg != null) {
        SweepPoint nextInter = nextSeg.getIntersection(segment);
        if (nextInter != null) {
          if (!segment.isAnEndpoint(nextInter)) nextMySplitter = nextInter;
          if (!nextSeg.isAnEndpoint(nextInter)) {
            List<SweepEvent> newEventsFromSplit = this.splitSafely(nextSeg, nextInter);
            for (int i = 0, iMax = newEventsFromSplit.size(); i < iMax; i++) {
              newEvents.add(newEventsFromSplit.get(i));
            }
          }
        }
      }

      // For simplicity, even if we find more than one intersection we only
      // spilt on the 'earliest' (sweep-line style) of the intersections.
      // The other intersection will be handled in a future process().
      if (prevMySplitter != null || nextMySplitter != null) {
        SweepPoint mySplitter = null;
        if (prevMySplitter == null) mySplitter = nextMySplitter;
        else if (nextMySplitter == null) mySplitter = prevMySplitter;
        else {
          int cmpSplitters = SweepEvent.comparePoints(prevMySplitter, nextMySplitter);
          mySplitter = cmpSplitters <= 0 ? prevMySplitter : nextMySplitter;
        }

        // Rounding errors can cause changes in ordering,
        // so remove afected segments and right sweep events before splitting
        this.queue.remove(segment.rightSE);
        newEvents.add(segment.rightSE);

        // PORT DEVIATION (deviation 5): remove the segment from the status tree
        // BEFORE the split mutates its sort key (replaceRightSE moves the right
        // endpoint to the splitter). Upstream splits first and removes afterwards
        // (sweep-line.js:115/:125) — safe there only because the splay tree moved
        // this segment to the ROOT at the add (:35), so the remove finds it without
        // key navigation. A red-black TreeSet navigates by the mutated key, can miss
        // the node, and leaves a stale ghost resident (observed: windmill-4-blades,
        // ghost seg -> wrong prev wiring -> empty result). The remove at the bottom
        // of this branch stays for the neighbor-split-only path (segment unmutated
        // there) and is a no-op on this path.
        removeFromTree(segment);

        List<SweepEvent> newEventsFromSplit = segment.split(mySplitter);
        for (int i = 0, iMax = newEventsFromSplit.size(); i < iMax; i++) {
          newEvents.add(newEventsFromSplit.get(i));
        }
      }

      if (newEvents.size() > 0) {
        // We found some intersections, so re-do the current event to
        // make sure sweep line ordering is totally consistent for later
        // use with the segment 'prev' pointers
        removeFromTree(segment);
        newEvents.add(event);
      } else {
        // done with left event
        this.segments.add(segment);
        segment.prev = prevSeg;
        if (TraceLog.enabled) TraceLog.line("PREV seg=" + segment.id + " prev=" + (prevSeg == null ? "null" : String.valueOf(prevSeg.id)));
      }
    } else {
      // event.isRight

      // since we're about to be removed from the sweep line, check for
      // intersections between our previous and next segments
      if (prevSeg != null && nextSeg != null) {
        SweepPoint inter = prevSeg.getIntersection(nextSeg);
        if (inter != null) {
          if (!prevSeg.isAnEndpoint(inter)) {
            List<SweepEvent> newEventsFromSplit = this.splitSafely(prevSeg, inter);
            for (int i = 0, iMax = newEventsFromSplit.size(); i < iMax; i++) {
              newEvents.add(newEventsFromSplit.get(i));
            }
          }
          if (!nextSeg.isAnEndpoint(inter)) {
            List<SweepEvent> newEventsFromSplit = this.splitSafely(nextSeg, inter);
            for (int i = 0, iMax = newEventsFromSplit.size(); i < iMax; i++) {
              newEvents.add(newEventsFromSplit.get(i));
            }
          }
        }
      }

      removeFromTree(segment);
    }

    return newEvents;
  }

  /* Safely split a segment that is currently in the datastructures
   * IE - a segment other than the one that is currently being processed. */
  private List<SweepEvent> splitSafely(Segment seg, SweepPoint pt) {
    // Rounding errors can cause changes in ordering,
    // so remove afected segments and right sweep events before splitting
    removeFromTree(seg);
    SweepEvent rightSE = seg.rightSE;
    this.queue.remove(rightSE);
    List<SweepEvent> newEvents = seg.split(pt);
    newEvents.add(rightSE);
    // splitting can trigger consumption
    if (seg.consumedBy == null) seg.statusNode = this.tree.insert(seg);
    return newEvents;
  }

  /** Handle-based removal: pointer surgery via the stored node, no key descent; no-op
   *  when the segment is not tree-resident (matching remove-by-key of an absent key). */
  private void removeFromTree(Segment seg) {
    if (seg.statusNode != null) {
      this.tree.removeNode(seg.statusNode);
      seg.statusNode = null;
    }
  }
}
