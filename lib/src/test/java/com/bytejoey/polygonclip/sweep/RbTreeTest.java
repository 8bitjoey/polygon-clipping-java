package com.bytejoey.polygonclip.sweep;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential oracle test for {@link RbTree}.
 * Oracle = {@link TreeSet}. Exercises insert/remove/find semantics and
 * in-order / reverse traversal via first()+next() / prev().
 */
class RbTreeTest {

  private static RbTree<Integer> makeTree() {
    return new RbTree<>(Integer::compare);
  }

  // ---- empty-tree sanity ----

  @Test
  @DisplayName("empty tree: first()==null, isEmpty(), size()==0")
  void emptyTree() {
    RbTree<Integer> tree = makeTree();
    assertNull(tree.first());
    assertTrue(tree.isEmpty());
    assertEquals(0, tree.size());
  }

  // ---- differential fuzz ----

  @ParameterizedTest
  @ValueSource(longs = {1L, 42L, 4242L})
  @DisplayName("20 000 random ops match TreeSet oracle (seed={0})")
  void fuzzAgainstOracle(long seed) {
    RbTree<Integer> tree = makeTree();
    TreeSet<Integer> oracle = new TreeSet<>();
    Random rng = new Random(seed);
    final int KEY_SPACE = 500;
    final int OPS = 20_000;
    final int CHECKPOINT = 1_000;

    for (int op = 0; op < OPS; op++) {
      int r = rng.nextInt(10);
      int key = rng.nextInt(KEY_SPACE);

      if (r < 5) {
        // 50% insert
        RbTree.Node<Integer> node = tree.insert(key);
        boolean added = oracle.add(key);
        if (added) {
          assertNotNull(node, "insert should return a node for new key " + key);
          assertEquals(key, node.key);
        } else {
          assertNull(node, "insert should return null for duplicate key " + key);
        }
      } else if (r < 8) {
        // 30% remove
        tree.remove(key);
        oracle.remove(key);
      } else {
        // 20% find
        RbTree.Node<Integer> node = tree.find(key);
        boolean present = oracle.contains(key);
        if (present) {
          assertNotNull(node, "find should return node for present key " + key);
          assertEquals(key, node.key);
        } else {
          assertNull(node, "find should return null for absent key " + key);
        }
      }

      // structural checkpoint every 1 000 ops
      if ((op + 1) % CHECKPOINT == 0) {
        verifyStructure(tree, oracle, op + 1);
      }
    }

    // final check
    verifyStructure(tree, oracle, OPS);
  }

  // ---- helpers ----

  private static void verifyStructure(RbTree<Integer> tree, TreeSet<Integer> oracle, int atOp) {
    String ctx = " at op " + atOp;

    // size
    assertEquals(oracle.size(), tree.size(), "size mismatch" + ctx);
    assertEquals(oracle.isEmpty(), tree.isEmpty(), "isEmpty mismatch" + ctx);

    // first / minimum
    if (oracle.isEmpty()) {
      assertNull(tree.first(), "first() should be null for empty tree" + ctx);
    } else {
      assertNotNull(tree.first(), "first() null but oracle non-empty" + ctx);
      assertEquals(oracle.first(), tree.first().key, "first() key mismatch" + ctx);
    }

    // forward traversal via first() + next()
    List<Integer> forward = new ArrayList<>();
    RbTree.Node<Integer> cur = tree.first();
    while (cur != null) {
      forward.add(cur.key);
      cur = tree.next(cur);
    }
    List<Integer> oracleForward = new ArrayList<>(oracle);
    assertEquals(oracleForward, forward, "forward traversal mismatch" + ctx);

    // backward traversal via prev() from the maximum
    List<Integer> backward = new ArrayList<>();
    if (!forward.isEmpty()) {
      // find max node by walking next from first until null
      RbTree.Node<Integer> maxNode = tree.first();
      RbTree.Node<Integer> tmp = maxNode;
      while (tmp != null) { maxNode = tmp; tmp = tree.next(tmp); }

      RbTree.Node<Integer> rev = maxNode;
      while (rev != null) {
        backward.add(rev.key);
        rev = tree.prev(rev);
      }
    }

    Iterator<Integer> descIt = oracle.descendingIterator();
    List<Integer> oracleBackward = new ArrayList<>();
    while (descIt.hasNext()) oracleBackward.add(descIt.next());
    assertEquals(oracleBackward, backward, "backward traversal mismatch" + ctx);
  }
}
