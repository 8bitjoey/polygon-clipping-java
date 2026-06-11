package com.bytejoey.polygonclip.sweep;

import com.bytejoey.polygonclip.TraceLog;

/**
 * Priority queue of sweep events, ordered by {@link SweepEvent#compare} (the paper's §4-5
 * ordering rules). Backed by an {@link RbTree}: upstream removes queued right events when
 * splitting (sweep-line.js:112, :170), which rules out a binary heap; the cached-leftmost
 * pollFirst makes pop() comparator-free.
 *
 * <p>Linking (deviation 1, DEVIATIONS.md): {@code SweepEvent.compare} links
 * equal-coordinate events opportunistically during tree operations — upstream-verbatim
 * mechanism, but its timing is tree-shape-dependent and a red-black tree cannot reproduce
 * the splay tree's compare sequence. {@code pop()} therefore adds the deterministic
 * backstop: an event popping at the same coordinate as the current batch's canonical point
 * is linked to it. By the time any event at a coordinate is processed, every event at that
 * coordinate shares one {@link SweepPoint}, regardless of which comparisons the tree
 * happened to perform. {@code checkForConsuming} fires inside {@code link} (C1); the check
 * runs per pop, so events inserted mid-batch at the same coordinate still link (C2).
 */
public final class EventQueue {

  private final RbTree<SweepEvent> tree = new RbTree<>(SweepEvent::compare);

  /** First-popped event of the current equal-coordinate batch. */
  private SweepEvent canonical;

  public void insert(SweepEvent evt) {
    evt.queueNode = tree.insert(evt);
  }

  public void remove(SweepEvent evt) {
    // handle-based removal (perf round 2): pointer surgery via the node stored at
    // insertion; null handle = not queued, matching remove-by-key of an absent key
    if (evt.queueNode != null) {
      tree.removeNode(evt.queueNode);
      evt.queueNode = null;
    }
  }

  public int size() {
    return tree.size();
  }

  public boolean isEmpty() {
    return tree.isEmpty();
  }

  public SweepEvent pop() {
    SweepEvent evt = tree.pollFirst();
    if (evt == null) return null;
    evt.queueNode = null;
    if (canonical != null && SweepEvent.comparePoints(evt.point, canonical.point) == 0) {
      if (evt.point != canonical.point) {
        canonical.link(evt);
      }
    } else {
      canonical = evt;
    }
    if (TraceLog.enabled) TraceLog.line("POP seg=" + evt.segment.id + " side=" + (evt.isLeft ? "L" : "R") + " x=" + TraceLog.bits(evt.point.x) + " y=" + TraceLog.bits(evt.point.y));
    return evt;
  }
}
