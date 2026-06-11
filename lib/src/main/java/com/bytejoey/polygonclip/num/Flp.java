package com.bytejoey.polygonclip.num;

public final class Flp {

    private static final double EPSILON = Math.ulp(1.0);
    private static final double EPSILON_SQ = EPSILON * EPSILON;

    private Flp() {}

    public static int cmp(double a, double b) {
        // check if they're both 0
        if (-EPSILON < a && a < EPSILON) {
            if (-EPSILON < b && b < EPSILON) {
                return 0;
            }
        }

        // check if they're flp equal
        double ab = a - b;
        if (ab * ab < EPSILON_SQ * a * b) {
            return 0;
        }

        // normal comparison
        return a < b ? -1 : 1;
    }
}
