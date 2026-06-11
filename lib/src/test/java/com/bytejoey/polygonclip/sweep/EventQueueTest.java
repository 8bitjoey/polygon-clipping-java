package com.bytejoey.polygonclip.sweep;

import com.bytejoey.polygonclip.Operation;
import com.bytejoey.polygonclip.OpType;
import com.bytejoey.polygonclip.input.RingIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fresh design tests (no upstream equivalent — upstream's queue is an anonymous splay tree
 * inside operation.js). Geometries are chosen so checkForConsuming never triggers consume():
 * no two segments share both endpoints.
 */
class EventQueueTest {

  private Operation op;

  @BeforeEach
  void resetIds() {
    op = new Operation(OpType.UNION);
  }

  private Segment seg(double x1, double y1, double x2, double y2) {
    return Segment.fromRing(new SweepPoint(x1, y1), new SweepPoint(x2, y2), new RingIn(1), op);
  }

  private static void insertBoth(EventQueue q, Segment s) {
    q.insert(s.leftSE);
    q.insert(s.rightSE);
  }

  @Test
  @DisplayName("pops in sweep order: x, then y, then right before left")
  void popOrder() {
    EventQueue q = new EventQueue();
    Segment a = seg(0, 0, 2, 0); // left (0,0), right (2,0)
    Segment b = seg(1, 5, 3, 5); // left (1,5), right (3,5)
    Segment c = seg(2, 0, 4, 1); // left (2,0) == a.right coords, right (4,1)
    insertBoth(q, a);
    insertBoth(q, b);
    insertBoth(q, c);

    assertSame(a.leftSE, q.pop()); // x=0
    assertSame(b.leftSE, q.pop()); // x=1
    assertSame(a.rightSE, q.pop()); // x=2: right events before left
    assertSame(c.leftSE, q.pop()); // x=2 left
    assertSame(b.rightSE, q.pop()); // x=3
    assertSame(c.rightSE, q.pop()); // x=4
    assertNull(q.pop());
    assertTrue(q.isEmpty());
  }

  @Test
  @DisplayName("supports arbitrary removal of queued events")
  void arbitraryRemove() {
    EventQueue q = new EventQueue();
    Segment a = seg(0, 0, 5, 5);
    Segment b = seg(1, 1, 6, 6);
    insertBoth(q, a);
    insertBoth(q, b);
    assertEquals(4, q.size());

    q.remove(a.rightSE); // upstream does exactly this when splitting
    assertEquals(3, q.size());

    List<SweepEvent> popped = new ArrayList<>();
    for (SweepEvent e = q.pop(); e != null; e = q.pop()) popped.add(e);
    assertEquals(List.of(a.leftSE, b.leftSE, b.rightSE), popped);
  }

  @Test
  @DisplayName("equal-coordinate events share one point after their batch pops")
  void popLinksEqualCoordinateEvents() {
    EventQueue q = new EventQueue();
    // Distinct SweepPoint objects with identical coordinates; far endpoints distinct.
    Segment a = seg(0, 0, 5, 1);
    Segment b = seg(0, 0, 6, -2);
    assertNotSame(a.leftSE.point, b.leftSE.point);
    insertBoth(q, a);
    insertBoth(q, b);

    SweepEvent first = q.pop();
    SweepEvent second = q.pop();
    assertEquals(0, SweepEvent.comparePoints(first.point, second.point));
    assertSame(first.point, second.point);
    assertEquals(2, first.point.events.size());
  }

  @Test
  @DisplayName("mid-batch insert still links (pop backstop, no tree comparison possible)")
  void midBatchInsertLinks() {
    EventQueue q = new EventQueue();
    Segment a = seg(0, 0, 5, 1);
    insertBoth(q, a);
    SweepEvent first = q.pop(); // canonical = a.leftSE; tree no longer contains it

    // New equal-coordinate event arrives after the batch started: the tree can never
    // compare it against the already-popped canonical — only pop() can link it.
    Segment b = seg(0, 0, 6, -2);
    assertNotSame(first.point, b.leftSE.point);
    q.insert(b.leftSE);

    SweepEvent second = q.pop();
    assertSame(b.leftSE, second);
    assertSame(first.point, second.point);
    assertEquals(2, first.point.events.size());
  }

  @Test
  @DisplayName("canonical batch resets when the coordinate changes")
  void batchResetsOnNewCoordinate() {
    EventQueue q = new EventQueue();
    Segment a = seg(0, 0, 5, 1);
    Segment b = seg(1, 1, 6, 2);
    Segment c = seg(1, 1, 7, -3);
    insertBoth(q, a);
    insertBoth(q, b);
    insertBoth(q, c);

    assertSame(a.leftSE, q.pop()); // batch (0,0)
    SweepEvent first11 = q.pop(); // batch (1,1) starts
    SweepEvent second11 = q.pop();
    assertEquals(0, SweepEvent.comparePoints(first11.point, second11.point));
    assertSame(first11.point, second11.point);
    // (1,1) batch linked to itself, never to the (0,0) point
    assertNotSame(a.leftSE.point, first11.point);
  }
}
