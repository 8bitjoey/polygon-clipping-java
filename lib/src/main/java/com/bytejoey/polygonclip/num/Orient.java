package com.bytejoey.polygonclip.num;

/**
 * Robust orientation predicate, transliterated from robust-predicates v3.0.2
 * (upstream/robust-predicates/orient2d.js + util.js — the exact dependency of
 * upstream polygon-clipping). The JS sources use build-time macros
 * ($Cross_Product, $Two_Diff_Tail, ...); the bodies below follow the expanded
 * form as shipped (verifiable in upstream/dist/polygon-clipping.umd.js).
 *
 * Sign convention: returns a positive value if a, b, c occur in clockwise
 * order (c lies to the right of the directed line a->b in y-up coordinates),
 * a negative value if counterclockwise, and exactly zero if collinear. This
 * is the negation of Shewchuk's classic orient2d.
 *
 * Deliberate deviations from the JS source (flagged for the port record):
 * 1. The module-level scratch Float64Arrays (B, C1, C2, D, u) are allocated
 *    locally inside orient2dadapt for thread-safety.
 * 2. Scratch arrays used as inputs to sum() are sized one element larger
 *    than in JS (B,u: 5; C1: 9; C2: 13). sum() reads one slot past the live
 *    region via e[++eindex] / f[++findex]; in JS that out-of-bounds
 *    Float64Array read yields undefined (never used in arithmetic — loop
 *    guards exit first), but in Java it would throw. The padded slot's value
 *    is likewise never used in arithmetic.
 * All constants, expression order, and parenthesization are otherwise exact.
 */
public final class Orient {

    // util.js
    private static final double epsilon = 1.1102230246251565e-16;
    private static final double splitter = 134217729;
    private static final double resulterrbound = (3 + 8 * epsilon) * epsilon;

    // orient2d.js
    private static final double ccwerrboundA = (3 + 16 * epsilon) * epsilon;
    private static final double ccwerrboundB = (2 + 12 * epsilon) * epsilon;
    private static final double ccwerrboundC = (9 + 64 * epsilon) * epsilon * epsilon;

    private Orient() {}

    public static double orient2d(double ax, double ay, double bx, double by, double cx, double cy) {
        final double detleft = (ay - cy) * (bx - cx);
        final double detright = (ax - cx) * (by - cy);
        final double det = detleft - detright;

        final double detsum = Math.abs(detleft + detright);
        if (Math.abs(det) >= ccwerrboundA * detsum) return det;

        return -orient2dadapt(ax, ay, bx, by, cx, cy, detsum);
    }

    private static double orient2dadapt(double ax, double ay, double bx, double by, double cx, double cy, double detsum) {
        // JS: module-level vec(4)/vec(8)/vec(12)/vec(16)/vec(4); see class javadoc.
        final double[] B = new double[5];
        final double[] C1 = new double[9];
        final double[] C2 = new double[13];
        final double[] D = new double[16];
        final double[] u = new double[5];

        double acxtail, acytail, bcxtail, bcytail;
        double bvirt, c, ahi, alo, bhi, blo, _i, _j, _0, s1, s0, t1, t0, u3;

        final double acx = ax - cx;
        final double bcx = bx - cx;
        final double acy = ay - cy;
        final double bcy = by - cy;

        s1 = acx * bcy;
        c = splitter * acx;
        ahi = c - (c - acx);
        alo = acx - ahi;
        c = splitter * bcy;
        bhi = c - (c - bcy);
        blo = bcy - bhi;
        s0 = alo * blo - (s1 - ahi * bhi - alo * bhi - ahi * blo);
        t1 = acy * bcx;
        c = splitter * acy;
        ahi = c - (c - acy);
        alo = acy - ahi;
        c = splitter * bcx;
        bhi = c - (c - bcx);
        blo = bcx - bhi;
        t0 = alo * blo - (t1 - ahi * bhi - alo * bhi - ahi * blo);
        _i = s0 - t0;
        bvirt = s0 - _i;
        B[0] = s0 - (_i + bvirt) + (bvirt - t0);
        _j = s1 + _i;
        bvirt = _j - s1;
        _0 = s1 - (_j - bvirt) + (_i - bvirt);
        _i = _0 - t1;
        bvirt = _0 - _i;
        B[1] = _0 - (_i + bvirt) + (bvirt - t1);
        u3 = _j + _i;
        bvirt = u3 - _j;
        B[2] = _j - (u3 - bvirt) + (_i - bvirt);
        B[3] = u3;

        double det = estimate(4, B);
        double errbound = ccwerrboundB * detsum;
        if (det >= errbound || -det >= errbound) {
            return det;
        }

        bvirt = ax - acx;
        acxtail = ax - (acx + bvirt) + (bvirt - cx);
        bvirt = bx - bcx;
        bcxtail = bx - (bcx + bvirt) + (bvirt - cx);
        bvirt = ay - acy;
        acytail = ay - (acy + bvirt) + (bvirt - cy);
        bvirt = by - bcy;
        bcytail = by - (bcy + bvirt) + (bvirt - cy);

        if (acxtail == 0 && acytail == 0 && bcxtail == 0 && bcytail == 0) {
            return det;
        }

        errbound = ccwerrboundC * detsum + resulterrbound * Math.abs(det);
        det += (acx * bcytail + bcy * acxtail) - (acy * bcxtail + bcx * acytail);
        if (det >= errbound || -det >= errbound) return det;

        s1 = acxtail * bcy;
        c = splitter * acxtail;
        ahi = c - (c - acxtail);
        alo = acxtail - ahi;
        c = splitter * bcy;
        bhi = c - (c - bcy);
        blo = bcy - bhi;
        s0 = alo * blo - (s1 - ahi * bhi - alo * bhi - ahi * blo);
        t1 = acytail * bcx;
        c = splitter * acytail;
        ahi = c - (c - acytail);
        alo = acytail - ahi;
        c = splitter * bcx;
        bhi = c - (c - bcx);
        blo = bcx - bhi;
        t0 = alo * blo - (t1 - ahi * bhi - alo * bhi - ahi * blo);
        _i = s0 - t0;
        bvirt = s0 - _i;
        u[0] = s0 - (_i + bvirt) + (bvirt - t0);
        _j = s1 + _i;
        bvirt = _j - s1;
        _0 = s1 - (_j - bvirt) + (_i - bvirt);
        _i = _0 - t1;
        bvirt = _0 - _i;
        u[1] = _0 - (_i + bvirt) + (bvirt - t1);
        u3 = _j + _i;
        bvirt = u3 - _j;
        u[2] = _j - (u3 - bvirt) + (_i - bvirt);
        u[3] = u3;
        final int C1len = sum(4, B, 4, u, C1);

        s1 = acx * bcytail;
        c = splitter * acx;
        ahi = c - (c - acx);
        alo = acx - ahi;
        c = splitter * bcytail;
        bhi = c - (c - bcytail);
        blo = bcytail - bhi;
        s0 = alo * blo - (s1 - ahi * bhi - alo * bhi - ahi * blo);
        t1 = acy * bcxtail;
        c = splitter * acy;
        ahi = c - (c - acy);
        alo = acy - ahi;
        c = splitter * bcxtail;
        bhi = c - (c - bcxtail);
        blo = bcxtail - bhi;
        t0 = alo * blo - (t1 - ahi * bhi - alo * bhi - ahi * blo);
        _i = s0 - t0;
        bvirt = s0 - _i;
        u[0] = s0 - (_i + bvirt) + (bvirt - t0);
        _j = s1 + _i;
        bvirt = _j - s1;
        _0 = s1 - (_j - bvirt) + (_i - bvirt);
        _i = _0 - t1;
        bvirt = _0 - _i;
        u[1] = _0 - (_i + bvirt) + (bvirt - t1);
        u3 = _j + _i;
        bvirt = u3 - _j;
        u[2] = _j - (u3 - bvirt) + (_i - bvirt);
        u[3] = u3;
        final int C2len = sum(C1len, C1, 4, u, C2);

        s1 = acxtail * bcytail;
        c = splitter * acxtail;
        ahi = c - (c - acxtail);
        alo = acxtail - ahi;
        c = splitter * bcytail;
        bhi = c - (c - bcytail);
        blo = bcytail - bhi;
        s0 = alo * blo - (s1 - ahi * bhi - alo * bhi - ahi * blo);
        t1 = acytail * bcxtail;
        c = splitter * acytail;
        ahi = c - (c - acytail);
        alo = acytail - ahi;
        c = splitter * bcxtail;
        bhi = c - (c - bcxtail);
        blo = bcxtail - bhi;
        t0 = alo * blo - (t1 - ahi * bhi - alo * bhi - ahi * blo);
        _i = s0 - t0;
        bvirt = s0 - _i;
        u[0] = s0 - (_i + bvirt) + (bvirt - t0);
        _j = s1 + _i;
        bvirt = _j - s1;
        _0 = s1 - (_j - bvirt) + (_i - bvirt);
        _i = _0 - t1;
        bvirt = _0 - _i;
        u[1] = _0 - (_i + bvirt) + (bvirt - t1);
        u3 = _j + _i;
        bvirt = u3 - _j;
        u[2] = _j - (u3 - bvirt) + (_i - bvirt);
        u[3] = u3;
        final int Dlen = sum(C2len, C2, 4, u, D);

        return D[Dlen - 1];
    }

    // fast_expansion_sum_zeroelim routine from oritinal code
    private static int sum(int elen, double[] e, int flen, double[] f, double[] h) {
        double Q, Qnew, hh, bvirt;
        double enow = e[0];
        double fnow = f[0];
        int eindex = 0;
        int findex = 0;
        if ((fnow > enow) == (fnow > -enow)) {
            Q = enow;
            enow = e[++eindex];
        } else {
            Q = fnow;
            fnow = f[++findex];
        }
        int hindex = 0;
        if (eindex < elen && findex < flen) {
            if ((fnow > enow) == (fnow > -enow)) {
                Qnew = enow + Q;
                hh = Q - (Qnew - enow);
                enow = e[++eindex];
            } else {
                Qnew = fnow + Q;
                hh = Q - (Qnew - fnow);
                fnow = f[++findex];
            }
            Q = Qnew;
            if (hh != 0) {
                h[hindex++] = hh;
            }
            while (eindex < elen && findex < flen) {
                if ((fnow > enow) == (fnow > -enow)) {
                    Qnew = Q + enow;
                    bvirt = Qnew - Q;
                    hh = Q - (Qnew - bvirt) + (enow - bvirt);
                    enow = e[++eindex];
                } else {
                    Qnew = Q + fnow;
                    bvirt = Qnew - Q;
                    hh = Q - (Qnew - bvirt) + (fnow - bvirt);
                    fnow = f[++findex];
                }
                Q = Qnew;
                if (hh != 0) {
                    h[hindex++] = hh;
                }
            }
        }
        while (eindex < elen) {
            Qnew = Q + enow;
            bvirt = Qnew - Q;
            hh = Q - (Qnew - bvirt) + (enow - bvirt);
            enow = e[++eindex];
            Q = Qnew;
            if (hh != 0) {
                h[hindex++] = hh;
            }
        }
        while (findex < flen) {
            Qnew = Q + fnow;
            bvirt = Qnew - Q;
            hh = Q - (Qnew - bvirt) + (fnow - bvirt);
            fnow = f[++findex];
            Q = Qnew;
            if (hh != 0) {
                h[hindex++] = hh;
            }
        }
        if (Q != 0 || hindex == 0) {
            h[hindex++] = Q;
        }
        return hindex;
    }

    private static double estimate(int elen, double[] e) {
        double Q = e[0];
        for (int i = 1; i < elen; i++) Q += e[i];
        return Q;
    }
}
