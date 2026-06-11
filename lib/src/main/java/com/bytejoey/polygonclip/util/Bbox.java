package com.bytejoey.polygonclip.util;

import com.bytejoey.polygonclip.num.Vec;

/**
 * A bounding box has the format:
 *
 *   { ll: { x: xmin, y: ymin }, ur: { x: xmax, y: ymax } }
 *
 * Port of bbox.js. All comparisons are raw primitive operators (no epsilon,
 * no Double.compare), matching the JS exactly.
 */
public final class Bbox {

    public final Vec ll;
    public final Vec ur;

    public Bbox(Vec ll, Vec ur) {
        this.ll = ll;
        this.ur = ur;
    }

    public static boolean isInBbox(Bbox bbox, Vec point) {
        return isInBbox(bbox.ll.x(), bbox.ll.y(), bbox.ur.x(), bbox.ur.y(), point.x(), point.y());
    }

    /** Primitive core of {@link #isInBbox(Bbox, Vec)} — same comparisons, zero allocation. */
    public static boolean isInBbox(
            double llx, double lly, double urx, double ury, double px, double py) {
        return llx <= px && px <= urx && lly <= py && py <= ury;
    }

    /* Returns either null, or a bbox (aka an ordered pair of points)
     * If there is only one point of overlap, a bbox with identical points
     * will be returned */
    public static Bbox getBboxOverlap(Bbox b1, Bbox b2) {
        // check if the bboxes overlap at all
        if (b2.ur.x() < b1.ll.x()
                || b1.ur.x() < b2.ll.x()
                || b2.ur.y() < b1.ll.y()
                || b1.ur.y() < b2.ll.y()) {
            return null;
        }

        // find the middle two X values
        double lowerX = b1.ll.x() < b2.ll.x() ? b2.ll.x() : b1.ll.x();
        double upperX = b1.ur.x() < b2.ur.x() ? b1.ur.x() : b2.ur.x();

        // find the middle two Y values
        double lowerY = b1.ll.y() < b2.ll.y() ? b2.ll.y() : b1.ll.y();
        double upperY = b1.ur.y() < b2.ur.y() ? b1.ur.y() : b2.ur.y();

        // put those middle values together to get the overlap
        return new Bbox(new Vec(lowerX, lowerY), new Vec(upperX, upperY));
    }
}
