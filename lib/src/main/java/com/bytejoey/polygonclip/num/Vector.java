package com.bytejoey.polygonclip.num;

/** Transliteration of upstream/src/vector.js — static utility methods only. */
public final class Vector {

    private Vector() {}

    /** crossProduct(a, b) = a.x * b.y - a.y * b.x */
    public static double crossProduct(Vec a, Vec b) {
        return a.x() * b.y() - a.y() * b.x();
    }

    /** dotProduct(a, b) = a.x * b.x + a.y * b.y */
    public static double dotProduct(Vec a, Vec b) {
        return a.x() * b.x() + a.y() * b.y();
    }

    /** compareVectorAngles — delegates to Orient.orient2d; res>0 → -1, res<0 → 1, else 0. */
    public static int compareVectorAngles(Vec basePt, Vec endPt1, Vec endPt2) {
        double res = Orient.orient2d(
                basePt.x(), basePt.y(),
                endPt1.x(), endPt1.y(),
                endPt2.x(), endPt2.y());
        if (res > 0) return -1;
        if (res < 0) return 1;
        return 0;
    }

    /** length(v) = Math.sqrt(dotProduct(v, v)) */
    public static double length(Vec v) {
        return Math.sqrt(dotProduct(v, v));
    }

    /**
     * sineOfAngle(pShared, pBase, pAngle):
     *   vBase  = pBase  - pShared
     *   vAngle = pAngle - pShared
     *   result = crossProduct(vAngle, vBase) / length(vAngle) / length(vBase)
     * Division is left-to-right: (cross / len_vAngle) / len_vBase.
     */
    public static double sineOfAngle(Vec pShared, Vec pBase, Vec pAngle) {
        Vec vBase = new Vec(pBase.x() - pShared.x(), pBase.y() - pShared.y());
        Vec vAngle = new Vec(pAngle.x() - pShared.x(), pAngle.y() - pShared.y());
        return crossProduct(vAngle, vBase) / length(vAngle) / length(vBase);
    }

    /**
     * cosineOfAngle(pShared, pBase, pAngle):
     *   vBase  = pBase  - pShared
     *   vAngle = pAngle - pShared
     *   result = dotProduct(vAngle, vBase) / length(vAngle) / length(vBase)
     * Division is left-to-right: (dot / len_vAngle) / len_vBase.
     */
    public static double cosineOfAngle(Vec pShared, Vec pBase, Vec pAngle) {
        Vec vBase = new Vec(pBase.x() - pShared.x(), pBase.y() - pShared.y());
        Vec vAngle = new Vec(pAngle.x() - pShared.x(), pAngle.y() - pShared.y());
        return dotProduct(vAngle, vBase) / length(vAngle) / length(vBase);
    }

    /**
     * closestPoint(ptA1, ptA2, ptB) — closest point on the line through ptA1/ptA2 to ptB.
     * Steps follow the upstream implementation exactly.
     */
    public static Vec closestPoint(Vec ptA1, Vec ptA2, Vec ptB) {
        // step 1 — vertical shortcut
        if (ptA1.x() == ptA2.x()) return new Vec(ptA1.x(), ptB.y());
        // step 2 — horizontal shortcut
        if (ptA1.y() == ptA2.y()) return new Vec(ptB.x(), ptA1.y());

        // step 3 — pick farther endpoint
        Vec v1 = new Vec(ptB.x() - ptA1.x(), ptB.y() - ptA1.y());
        Vec v2 = new Vec(ptB.x() - ptA2.x(), ptB.y() - ptA2.y());
        Vec vFar, vA;
        Vec farPt;
        if (dotProduct(v1, v1) > dotProduct(v2, v2)) {
            vFar = v1;
            vA = new Vec(ptA2.x() - ptA1.x(), ptA2.y() - ptA1.y());
            farPt = ptA1;
        } else {
            vFar = v2;
            vA = new Vec(ptA1.x() - ptA2.x(), ptA1.y() - ptA2.y());
            farPt = ptA2;
        }

        // step 4 — on-line test via X
        double xDist = (ptB.x() - farPt.x()) / vA.x();
        if (ptB.y() == farPt.y() + xDist * vA.y()) return ptB;

        // step 5 — on-line test via Y
        double yDist = (ptB.y() - farPt.y()) / vA.y();
        if (ptB.x() == farPt.x() + yDist * vA.x()) return ptB;

        // step 6 — general projection
        double dist = dotProduct(vA, vFar) / dotProduct(vA, vA);
        return new Vec(farPt.x() + dist * vA.x(), farPt.y() + dist * vA.y());
    }

    /**
     * horizontalIntersection(pt, v, y) — x where line (pt,v) crosses horizontal y.
     * Returns null when v.y == 0 (parallel/overlapping).
     */
    public static Vec horizontalIntersection(Vec pt, Vec v, double y) {
        return horizontalIntersection(pt.x(), pt.y(), v.x(), v.y(), y);
    }

    /** Primitive core of {@link #horizontalIntersection(Vec, Vec, double)} — same arithmetic. */
    public static Vec horizontalIntersection(double ptx, double pty, double vx, double vy, double y) {
        if (vy == 0) return null;
        return new Vec(ptx + (vx / vy) * (y - pty), y);
    }

    /**
     * verticalIntersection(pt, v, x) — y where line (pt,v) crosses vertical x.
     * Returns null when v.x == 0 (parallel/overlapping).
     */
    public static Vec verticalIntersection(Vec pt, Vec v, double x) {
        return verticalIntersection(pt.x(), pt.y(), v.x(), v.y(), x);
    }

    /** Primitive core of {@link #verticalIntersection(Vec, Vec, double)} — same arithmetic. */
    public static Vec verticalIntersection(double ptx, double pty, double vx, double vy, double x) {
        if (vx == 0) return null;
        return new Vec(x, pty + (vy / vx) * (x - ptx));
    }

    /**
     * intersection(pt1, v1, pt2, v2) — intersection of two lines.
     * Returns null for parallel/overlapping lines.
     *
     * Shortcut priority (prevents out-of-bbox results):
     *   v1.x==0 → verticalIntersection(pt2, v2, pt1.x)
     *   v2.x==0 → verticalIntersection(pt1, v1, pt2.x)
     *   v1.y==0 → horizontalIntersection(pt2, v2, pt1.y)
     *   v2.y==0 → horizontalIntersection(pt1, v1, pt2.y)
     *
     * General case (Schneider-Eberly pg 244):
     *   d1/d2 cross-application is intentional — verified against upstream.
     */
    public static Vec intersection(Vec pt1, Vec v1, Vec pt2, Vec v2) {
        return intersection(pt1.x(), pt1.y(), v1.x(), v1.y(), pt2.x(), pt2.y(), v2.x(), v2.y());
    }

    /** Primitive core of {@link #intersection(Vec, Vec, Vec, Vec)} — same arithmetic, same order. */
    public static Vec intersection(
            double pt1x, double pt1y, double v1x, double v1y,
            double pt2x, double pt2y, double v2x, double v2y) {
        if (v1x == 0) return verticalIntersection(pt2x, pt2y, v2x, v2y, pt1x);
        if (v2x == 0) return verticalIntersection(pt1x, pt1y, v1x, v1y, pt2x);
        if (v1y == 0) return horizontalIntersection(pt2x, pt2y, v2x, v2y, pt1y);
        if (v2y == 0) return horizontalIntersection(pt1x, pt1y, v1x, v1y, pt2y);

        double kross = v1x * v2y - v1y * v2x;
        if (kross == 0) return null;

        double vex = pt2x - pt1x;
        double vey = pt2y - pt1y;
        double d1 = (vex * v1y - vey * v1x) / kross;
        double d2 = (vex * v2y - vey * v2x) / kross;

        // cross-application: x1 uses d2*v1, x2 uses d1*v2 — intentional, verified
        double x1 = pt1x + d2 * v1x;
        double x2 = pt2x + d1 * v2x;
        double y1 = pt1y + d2 * v1y;
        double y2 = pt2y + d1 * v2y;
        double x = (x1 + x2) / 2;
        double y = (y1 + y2) / 2;
        return new Vec(x, y);
    }

}
