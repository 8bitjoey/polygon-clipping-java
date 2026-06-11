package com.bytejoey.polygonclip.sweep;

import java.util.Comparator;

/**
 * Parent-pointer red-black BST exposing the upstream splay-tree API shape.
 *
 * <p>Motivation: the sweep-line status structure was backed by a JDK
 * {@code TreeSet}, whose {@code lower}/{@code higher} calls each perform a full O(log n)
 * comparator descent. The upstream JavaScript implementation used a splay tree that
 * returns a <em>node handle</em> from {@code insert}/{@code find}, allowing
 * {@code prev(node)}/{@code next(node)} navigation via parent/child pointers — zero
 * comparator calls. This class restores that behaviour: the comparator runs only during
 * {@code insert}/{@code find}/{@code remove} descent; {@code prev}/{@code next} walk the
 * parent chain and never touch the comparator.
 *
 * <p>Implementation: standard CLRS left-leaning red-black tree with parent pointers and an
 * explicit nil sentinel (null-safe transplant / delete-fixup). The sentinel is never
 * exposed outside this class. A cached {@code leftmost} pointer gives O(1) {@code first()}.
 */
public final class RbTree<T> {

  // ---- node colour constants ----
  private static final boolean RED = true;
  private static final boolean BLACK = false;

  // ---- Node ----

  public static final class Node<T> {
    public final T key;
    Node<T> left;
    Node<T> right;
    Node<T> parent;
    boolean color;

    Node(T key, Node<T> nil) {
      this.key = key;
      this.left = nil;
      this.right = nil;
      this.parent = nil;
      this.color = RED;
    }

    /** Sentinel constructor — key is null, colour BLACK, all pointers self. */
    @SuppressWarnings("unchecked")
    Node() {
      this.key = null;
      this.color = BLACK;
    }

    void selfLink() {
      this.left = this;
      this.right = this;
      this.parent = this;
    }
  }

  // ---- fields ----

  private final Comparator<? super T> comparator;
  private final Node<T> nil;   // sentinel — never returned to callers
  private Node<T> root;
  private Node<T> leftmost;    // cached minimum for O(1) first()
  private int sz;

  // ---- constructor ----

  public RbTree(Comparator<? super T> comparator) {
    this.comparator = comparator;
    this.nil = new Node<>();
    this.nil.selfLink();
    this.root = nil;
    this.leftmost = nil;
    this.sz = 0;
  }

  // ---- public API ----

  /**
   * Inserts {@code key} and returns its new node.
   * Returns {@code null} (without modifying the tree) if a comparator-equal key already exists.
   */
  public Node<T> insert(T key) {
    Node<T> parent = nil;
    Node<T> x = root;
    boolean goLeft = false;
    boolean newLeftmost = true;   // true until we go right at least once

    while (x != nil) {
      int cmp = comparator.compare(key, x.key);
      if (cmp == 0) return null;   // already present
      parent = x;
      if (cmp < 0) {
        x = x.left;
        goLeft = true;
      } else {
        x = x.right;
        goLeft = false;
        newLeftmost = false;
      }
    }

    Node<T> node = new Node<>(key, nil);
    node.parent = parent;

    if (parent == nil) {
      root = node;
    } else if (goLeft) {
      parent.left = node;
    } else {
      parent.right = node;
    }

    if (newLeftmost) leftmost = node;

    sz++;
    insertFixup(node);
    return node;
  }

  /** Returns the node for {@code key}, or {@code null} if absent. */
  public Node<T> find(T key) {
    Node<T> x = root;
    while (x != nil) {
      int cmp = comparator.compare(key, x.key);
      if (cmp == 0) return x;
      x = cmp < 0 ? x.left : x.right;
    }
    return null;
  }

  /** Removes the node with comparator-equal {@code key}; no-op if absent. */
  public void remove(T key) {
    Node<T> node = find(key);
    if (node == null) return;
    deleteNode(node);
  }

  /**
   * Removes and returns the leftmost key, or {@code null} if empty. Structural removal
   * of the cached minimum — never calls the comparator (unlike a key-navigated remove).
   */
  public T pollFirst() {
    Node<T> f = first();
    if (f == null) return null;
    deleteNode(f);
    return f.key;
  }

  /**
   * Removes a node by handle — pure pointer surgery, no comparator calls, no key descent.
   * The caller owns handle validity: the node MUST be resident in this tree (delete is
   * relink-based, never key-copying, so handles stay valid while resident).
   */
  public void removeNode(Node<T> node) {
    deleteNode(node);
  }

  /**
   * In-order predecessor of {@code node} via child/parent pointers.
   * Never calls the comparator. Returns {@code null} if {@code node} is the minimum.
   */
  public Node<T> prev(Node<T> node) {
    if (node == null) return null;
    if (node.left != nil) {
      // rightmost of left subtree
      Node<T> x = node.left;
      while (x.right != nil) x = x.right;
      return x;
    }
    Node<T> y = node.parent;
    while (y != nil && node == y.left) {
      node = y;
      y = y.parent;
    }
    return y == nil ? null : y;
  }

  /**
   * In-order successor of {@code node} via child/parent pointers.
   * Never calls the comparator. Returns {@code null} if {@code node} is the maximum.
   */
  public Node<T> next(Node<T> node) {
    if (node == null) return null;
    if (node.right != nil) {
      // leftmost of right subtree
      Node<T> x = node.right;
      while (x.left != nil) x = x.left;
      return x;
    }
    Node<T> y = node.parent;
    while (y != nil && node == y.right) {
      node = y;
      y = y.parent;
    }
    return y == nil ? null : y;
  }

  /** Returns the leftmost (minimum) node, or {@code null} if the tree is empty. O(1). */
  public Node<T> first() {
    return leftmost == nil ? null : leftmost;
  }

  public boolean isEmpty() {
    return root == nil;
  }

  public int size() {
    return sz;
  }

  // ---- internal helpers ----

  private void rotateLeft(Node<T> x) {
    Node<T> y = x.right;
    x.right = y.left;
    if (y.left != nil) y.left.parent = x;
    y.parent = x.parent;
    if (x.parent == nil) root = y;
    else if (x == x.parent.left) x.parent.left = y;
    else x.parent.right = y;
    y.left = x;
    x.parent = y;
  }

  private void rotateRight(Node<T> x) {
    Node<T> y = x.left;
    x.left = y.right;
    if (y.right != nil) y.right.parent = x;
    y.parent = x.parent;
    if (x.parent == nil) root = y;
    else if (x == x.parent.right) x.parent.right = y;
    else x.parent.left = y;
    y.right = x;
    x.parent = y;
  }

  private void insertFixup(Node<T> z) {
    while (z.parent.color == RED) {
      if (z.parent == z.parent.parent.left) {
        Node<T> y = z.parent.parent.right;  // uncle
        if (y.color == RED) {
          // Case 1
          z.parent.color = BLACK;
          y.color = BLACK;
          z.parent.parent.color = RED;
          z = z.parent.parent;
        } else {
          if (z == z.parent.right) {
            // Case 2
            z = z.parent;
            rotateLeft(z);
          }
          // Case 3
          z.parent.color = BLACK;
          z.parent.parent.color = RED;
          rotateRight(z.parent.parent);
        }
      } else {
        Node<T> y = z.parent.parent.left;   // uncle (mirror)
        if (y.color == RED) {
          z.parent.color = BLACK;
          y.color = BLACK;
          z.parent.parent.color = RED;
          z = z.parent.parent;
        } else {
          if (z == z.parent.left) {
            z = z.parent;
            rotateRight(z);
          }
          z.parent.color = BLACK;
          z.parent.parent.color = RED;
          rotateLeft(z.parent.parent);
        }
      }
    }
    root.color = BLACK;
  }

  /** Replace subtree rooted at {@code u} with subtree rooted at {@code v}. */
  private void transplant(Node<T> u, Node<T> v) {
    if (u.parent == nil) root = v;
    else if (u == u.parent.left) u.parent.left = v;
    else u.parent.right = v;
    v.parent = u.parent;
  }

  private void deleteNode(Node<T> z) {
    // Advance the leftmost cache before unlinking
    if (z == leftmost) {
      Node<T> successor = next(z);
      leftmost = (successor == null) ? nil : successor;
    }

    sz--;

    Node<T> y = z;
    boolean yOriginalColor = y.color;
    Node<T> x;

    if (z.left == nil) {
      x = z.right;
      transplant(z, z.right);
    } else if (z.right == nil) {
      x = z.left;
      transplant(z, z.left);
    } else {
      // successor (minimum of right subtree)
      y = z.right;
      while (y.left != nil) y = y.left;
      yOriginalColor = y.color;
      x = y.right;
      if (y.parent == z) {
        x.parent = y;   // safe even when x == nil (sentinel)
      } else {
        transplant(y, y.right);
        y.right = z.right;
        y.right.parent = y;
      }
      transplant(z, y);
      y.left = z.left;
      y.left.parent = y;
      y.color = z.color;
    }

    if (yOriginalColor == BLACK) {
      deleteFixup(x);
    }
  }

  private void deleteFixup(Node<T> x) {
    while (x != root && x.color == BLACK) {
      if (x == x.parent.left) {
        Node<T> w = x.parent.right;  // sibling
        if (w.color == RED) {
          // Case 1
          w.color = BLACK;
          x.parent.color = RED;
          rotateLeft(x.parent);
          w = x.parent.right;
        }
        if (w.left.color == BLACK && w.right.color == BLACK) {
          // Case 2
          w.color = RED;
          x = x.parent;
        } else {
          if (w.right.color == BLACK) {
            // Case 3
            w.left.color = BLACK;
            w.color = RED;
            rotateRight(w);
            w = x.parent.right;
          }
          // Case 4
          w.color = x.parent.color;
          x.parent.color = BLACK;
          w.right.color = BLACK;
          rotateLeft(x.parent);
          x = root;
        }
      } else {
        Node<T> w = x.parent.left;  // sibling (mirror)
        if (w.color == RED) {
          w.color = BLACK;
          x.parent.color = RED;
          rotateRight(x.parent);
          w = x.parent.left;
        }
        if (w.right.color == BLACK && w.left.color == BLACK) {
          w.color = RED;
          x = x.parent;
        } else {
          if (w.left.color == BLACK) {
            w.right.color = BLACK;
            w.color = RED;
            rotateLeft(w);
            w = x.parent.left;
          }
          w.color = x.parent.color;
          x.parent.color = BLACK;
          w.left.color = BLACK;
          rotateRight(x.parent);
          x = root;
        }
      }
    }
    x.color = BLACK;
  }
}
