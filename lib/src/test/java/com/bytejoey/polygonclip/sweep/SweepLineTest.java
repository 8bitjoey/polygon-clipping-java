package com.bytejoey.polygonclip.sweep;

import com.bytejoey.polygonclip.OpType;
import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.input.RingIn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fresh design tests (upstream's sweep-line.test.js exercises the vendor splay-tree
 * node API on raw integers — N/A for the JDK TreeSet; the JDK TreeSet contract covers that behavior).
 * These drive process() exactly the way operation.run()'s sweep loop does.
 */
class SweepLineTest {

  /** Drain the queue through a SweepLine like operation.run()'s sweep loop. */
  private static void drain(EventQueue queue, SweepLine sweepLine) {
    SweepEvent evt = queue.pop();
    while (evt != null) {
      List<SweepEvent> newEvents = sweepLine.process(evt);
      for (int i = 0, iMax = newEvents.size(); i < iMax; i++) {
        SweepEvent newEvt = newEvents.get(i);
        if (newEvt.consumedBy == null) queue.insert(newEvt);
      }
      evt = queue.pop();
    }
  }

  @Test
  @DisplayName("clean triangle: all segments finalized, prev chain set, tree drained")
  void cleanTriangle() {
    Operation op = new Operation(OpType.UNION);
    RingIn ring = new RingIn(new double[][] {{0, 0}, {10, 0}, {10, 10}}, null, true, op);
    EventQueue queue = new EventQueue();
    for (SweepEvent evt : ring.getSweepEvents()) queue.insert(evt);

    SweepLine sweepLine = new SweepLine(queue);
    drain(queue, sweepLine);

    assertEquals(3, sweepLine.segments.size());
    assertTrue(sweepLine.tree.isEmpty());
    // bottom-to-top at the left edge: (0,0)->(10,0) is below (0,0)->(10,10)
    Segment bottom = null;
    for (Segment seg : sweepLine.segments) {
      if (seg.leftSE.point.x == 0.0 && seg.leftSE.point.y == 0.0
          && seg.rightSE.point.x == 10.0 && seg.rightSE.point.y == 0.0) {
        bottom = seg;
        break;
      }
    }
    assertTrue(bottom != null, "could not locate bottom segment (0,0)->(10,0)");
    assertNull(bottom.prev);
  }

  @Test
  @DisplayName("crossing pair splits both segments at the intersection")
  void crossingPair() {
    Operation op = new Operation(OpType.UNION);
    // two segments crossing at (5, 5); distinct rings, like two input polygons' edges
    Segment a =
        Segment.fromRing(new SweepPoint(0, 0), new SweepPoint(10, 10), new RingIn(1), op);
    Segment b =
        Segment.fromRing(new SweepPoint(0, 10), new SweepPoint(10, 0), new RingIn(2), op);
    EventQueue queue = new EventQueue();
    queue.insert(a.leftSE);
    queue.insert(a.rightSE);
    queue.insert(b.leftSE);
    queue.insert(b.rightSE);

    SweepLine sweepLine = new SweepLine(queue);
    drain(queue, sweepLine);

    // each original segment was split once -> 4 result segments
    assertEquals(4, sweepLine.segments.size());
    assertTrue(sweepLine.tree.isEmpty());
    // every finalized segment touches the intersection point (5, 5)
    for (Segment seg : sweepLine.segments) {
      assertTrue(
          (seg.leftSE.point.x == 5.0 && seg.leftSE.point.y == 5.0)
              || (seg.rightSE.point.x == 5.0 && seg.rightSE.point.y == 5.0),
          "segment does not touch the splitter: #" + seg.id);
    }
  }

  @Test
  @DisplayName("consumed left event cleans up its right event and is skipped")
  void consumedEventSkipped() {
    Operation op = new Operation(OpType.UNION);
    SweepPoint p1 = new SweepPoint(0, 0);
    SweepPoint p2 = new SweepPoint(10, 0);
    Segment s1 = Segment.fromRing(p1, p2, new RingIn(1), op);
    Segment s2 = Segment.fromRing(p1, p2, new RingIn(2), op);
    s1.consume(s2);

    EventQueue queue = new EventQueue();
    queue.insert(s1.leftSE);
    queue.insert(s1.rightSE);
    queue.insert(s2.leftSE);
    queue.insert(s2.rightSE);

    SweepLine sweepLine = new SweepLine(queue);
    drain(queue, sweepLine);

    // only the consumer is finalized; the consumee never enters the segments list
    assertEquals(1, sweepLine.segments.size());
    assertSame(s1, sweepLine.segments.get(0));
    assertTrue(sweepLine.tree.isEmpty());
  }
}
