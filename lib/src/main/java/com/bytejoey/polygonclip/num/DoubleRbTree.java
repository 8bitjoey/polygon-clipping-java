package com.bytejoey.polygonclip.num;

/**
 * Intrusive red-black tree over primitive {@code double} keys.
 *
 * <p>No boxing, no value column. All allocation is confined to node creation on {@link #insert}.
 *
 * <h3>Ordering semantics</h3>
 * Keys are compared with primitive {@code <} / {@code >}: this is the default JS comparator
 * semantics used by the upstream splay tree ({@code a > b ? 1 : a < b ? -1 : 0}).
 *
 * <p>Consequences:
 * <ul>
 *   <li>{@code -0.0} and {@code 0.0} are the <em>same</em> key — neither satisfies {@code -0.0 <
 *       0.0} nor {@code -0.0 > 0.0}, so they compare equal. This is intentional: the old
 *       {@link java.util.TreeMap} backed by {@link Double#compare} treated them as distinct keys
 *       (it defines {@code -0.0 < 0.0}), which was slightly unfaithful to the JS upstream.
 *   <li>NaN keys are unsupported — NaN comparisons are always {@code false}, making every NaN
 *       act like a key that is neither less-than nor greater-than any other, including itself.
 *       Callers must never insert NaN. NaN is therefore safe as the "absent" sentinel returned by
 *       {@link #lowerOrNaN} and {@link #higherOrNaN}.
 * </ul>
 *
 * <h3>Algorithm</h3>
 * CLRS Chapter 13 insert / delete with parent pointers and a {@code nil} sentinel node.
 * lowerOrNaN / higherOrNaN use a standard BST descent that tracks the best candidate seen so
 * far; no allocation is performed.
 */
public final class DoubleRbTree {

    // ------------------------------------------------------------------
    // Node
    // ------------------------------------------------------------------

    private static final boolean RED   = true;
    private static final boolean BLACK = false;

    private static final class Node {
        double key;
        boolean color;
        Node left, right, parent;

        Node(double key, boolean color, Node nil) {
            this.key    = key;
            this.color  = color;
            this.left   = nil;
            this.right  = nil;
            this.parent = nil;
        }
    }

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    /** Sentinel for nil leaves and the root's parent. */
    private final Node nil;
    private Node root;
    private int size;

    public DoubleRbTree() {
        // nil is a BLACK node; its fields are never meaningful.
        nil       = new Node(Double.NaN, BLACK, null);
        nil.left  = nil;
        nil.right = nil;
        nil.parent = nil;
        root      = nil;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Returns the number of distinct keys currently in the tree.
     */
    public int size() {
        return size;
    }

    /**
     * Insert {@code key}. Returns {@code false} (and leaves the tree unchanged) if the key is
     * already present; returns {@code true} on a new insertion.
     */
    public boolean insert(double key) {
        // Find insertion point.
        Node p = nil;
        Node cur = root;
        while (cur != nil) {
            p = cur;
            if (key < cur.key) {
                cur = cur.left;
            } else if (key > cur.key) {
                cur = cur.right;
            } else {
                return false; // already present
            }
        }

        Node z = new Node(key, RED, nil);
        z.parent = p;

        if (p == nil) {
            root = z;
        } else if (key < p.key) {
            p.left = z;
        } else {
            p.right = z;
        }

        size++;
        insertFixup(z);
        return true;
    }

    /**
     * Returns {@code true} if {@code key} is present in the tree.
     */
    public boolean contains(double key) {
        Node cur = root;
        while (cur != nil) {
            if (key < cur.key) {
                cur = cur.left;
            } else if (key > cur.key) {
                cur = cur.right;
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove {@code key}. No-op if not present.
     */
    public void remove(double key) {
        Node z = findNode(key);
        if (z == nil) return;
        deleteNode(z);
        size--;
    }

    /**
     * Single-descent snap-or-insert (the rounder's round(), rounder.js:49-65): one walk finds
     * the insertion point AND the strict predecessor/successor (last right-turn / last
     * left-turn on the path). If {@code coord} is already present it is returned as-is —
     * coexisting keys are never within {@link Flp} epsilon of each other, because the
     * later-inserted one would have snapped at its own insertion ({@code Flp.cmp} is
     * symmetric). If the predecessor — checked FIRST, the load-bearing order — or the
     * successor matches within epsilon, that key is returned and the tree is left untouched
     * (equivalent to the three-call sequence's insert-then-remove, minus two rebalances).
     * Otherwise the coordinate is attached at the already-found insertion point.
     *
     * <p>Behaviorally identical to {@code insert} + {@code lowerOrNaN} + {@code higherOrNaN}
     * (+ {@code remove} on a snap hit) — one descent instead of three.
     */
    public double snapRound(double coord) {
        Node p = nil;
        Node cur = root;
        Node pred = nil;
        Node succ = nil;
        while (cur != nil) {
            p = cur;
            if (coord < cur.key) {
                succ = cur;
                cur = cur.left;
            } else if (coord > cur.key) {
                pred = cur;
                cur = cur.right;
            } else {
                return coord; // already present
            }
        }

        if (pred != nil && Flp.cmp(coord, pred.key) == 0) return pred.key;
        if (succ != nil && Flp.cmp(coord, succ.key) == 0) return succ.key;

        Node z = new Node(coord, RED, nil);
        z.parent = p;
        if (p == nil) {
            root = z;
        } else if (coord < p.key) {
            p.left = z;
        } else {
            p.right = z;
        }
        size++;
        insertFixup(z);
        return coord;
    }

    /**
     * Returns the greatest key strictly less than {@code key}, or {@link Double#NaN} if none.
     * No allocation.
     */
    public double lowerOrNaN(double key) {
        Node cur = root;
        double best = Double.NaN;
        while (cur != nil) {
            if (cur.key < key) {
                best = cur.key;
                cur  = cur.right;
            } else {
                cur = cur.left;
            }
        }
        return best;
    }

    /**
     * Returns the least key strictly greater than {@code key}, or {@link Double#NaN} if none.
     * No allocation.
     */
    public double higherOrNaN(double key) {
        Node cur = root;
        double best = Double.NaN;
        while (cur != nil) {
            if (cur.key > key) {
                best = cur.key;
                cur  = cur.left;
            } else {
                cur = cur.right;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private Node findNode(double key) {
        Node cur = root;
        while (cur != nil) {
            if (key < cur.key) {
                cur = cur.left;
            } else if (key > cur.key) {
                cur = cur.right;
            } else {
                return cur;
            }
        }
        return nil;
    }

    // --- Rotations ---

    private void rotateLeft(Node x) {
        Node y = x.right;
        x.right = y.left;
        if (y.left != nil) y.left.parent = x;
        y.parent = x.parent;
        if (x.parent == nil) {
            root = y;
        } else if (x == x.parent.left) {
            x.parent.left = y;
        } else {
            x.parent.right = y;
        }
        y.left   = x;
        x.parent = y;
    }

    private void rotateRight(Node x) {
        Node y = x.left;
        x.left = y.right;
        if (y.right != nil) y.right.parent = x;
        y.parent = x.parent;
        if (x.parent == nil) {
            root = y;
        } else if (x == x.parent.right) {
            x.parent.right = y;
        } else {
            x.parent.left = y;
        }
        y.right  = x;
        x.parent = y;
    }

    // --- Insert fixup (CLRS §13.3) ---

    private void insertFixup(Node z) {
        while (z.parent.color == RED) {
            if (z.parent == z.parent.parent.left) {
                Node y = z.parent.parent.right; // uncle
                if (y.color == RED) {
                    // Case 1
                    z.parent.color         = BLACK;
                    y.color                = BLACK;
                    z.parent.parent.color  = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.right) {
                        // Case 2 → Case 3
                        z = z.parent;
                        rotateLeft(z);
                    }
                    // Case 3
                    z.parent.color        = BLACK;
                    z.parent.parent.color = RED;
                    rotateRight(z.parent.parent);
                }
            } else {
                Node y = z.parent.parent.left; // uncle
                if (y.color == RED) {
                    // Case 1 (mirror)
                    z.parent.color        = BLACK;
                    y.color               = BLACK;
                    z.parent.parent.color = RED;
                    z = z.parent.parent;
                } else {
                    if (z == z.parent.left) {
                        // Case 2 → Case 3 (mirror)
                        z = z.parent;
                        rotateRight(z);
                    }
                    // Case 3 (mirror)
                    z.parent.color        = BLACK;
                    z.parent.parent.color = RED;
                    rotateLeft(z.parent.parent);
                }
            }
        }
        root.color = BLACK;
    }

    // --- Delete helpers (CLRS §13.4) ---

    /** Replaces subtree rooted at u with subtree rooted at v. */
    private void transplant(Node u, Node v) {
        if (u.parent == nil) {
            root = v;
        } else if (u == u.parent.left) {
            u.parent.left = v;
        } else {
            u.parent.right = v;
        }
        v.parent = u.parent;
    }

    private Node treeMinimum(Node x) {
        while (x.left != nil) x = x.left;
        return x;
    }

    private void deleteNode(Node z) {
        Node y          = z;
        boolean yOrigColor = y.color;
        Node x;

        if (z.left == nil) {
            x = z.right;
            transplant(z, z.right);
        } else if (z.right == nil) {
            x = z.left;
            transplant(z, z.left);
        } else {
            y = treeMinimum(z.right);
            yOrigColor = y.color;
            x = y.right;
            if (y.parent == z) {
                x.parent = y;
            } else {
                transplant(y, y.right);
                y.right        = z.right;
                y.right.parent = y;
            }
            transplant(z, y);
            y.left        = z.left;
            y.left.parent = y;
            y.color       = z.color;
        }

        if (yOrigColor == BLACK) {
            deleteFixup(x);
        }
    }

    private void deleteFixup(Node x) {
        while (x != root && x.color == BLACK) {
            if (x == x.parent.left) {
                Node w = x.parent.right;
                if (w.color == RED) {
                    // Case 1
                    w.color         = BLACK;
                    x.parent.color  = RED;
                    rotateLeft(x.parent);
                    w = x.parent.right;
                }
                if (w.left.color == BLACK && w.right.color == BLACK) {
                    // Case 2
                    w.color = RED;
                    x       = x.parent;
                } else {
                    if (w.right.color == BLACK) {
                        // Case 3 → Case 4
                        w.left.color = BLACK;
                        w.color      = RED;
                        rotateRight(w);
                        w = x.parent.right;
                    }
                    // Case 4
                    w.color        = x.parent.color;
                    x.parent.color = BLACK;
                    w.right.color  = BLACK;
                    rotateLeft(x.parent);
                    x = root;
                }
            } else {
                Node w = x.parent.left;
                if (w.color == RED) {
                    // Case 1 (mirror)
                    w.color        = BLACK;
                    x.parent.color = RED;
                    rotateRight(x.parent);
                    w = x.parent.left;
                }
                if (w.right.color == BLACK && w.left.color == BLACK) {
                    // Case 2 (mirror)
                    w.color = RED;
                    x       = x.parent;
                } else {
                    if (w.left.color == BLACK) {
                        // Case 3 → Case 4 (mirror)
                        w.right.color = BLACK;
                        w.color       = RED;
                        rotateLeft(w);
                        w = x.parent.left;
                    }
                    // Case 4 (mirror)
                    w.color        = x.parent.color;
                    x.parent.color = BLACK;
                    w.left.color   = BLACK;
                    rotateRight(x.parent);
                    x = root;
                }
            }
        }
        x.color = BLACK;
    }
}
