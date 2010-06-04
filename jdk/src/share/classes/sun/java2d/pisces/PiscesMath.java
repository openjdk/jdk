/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.java2d.pisces;

public class PiscesMath {

    private PiscesMath() {}

    private static final int SINTAB_LG_ENTRIES = 10;
    private static final int SINTAB_ENTRIES = 1 << SINTAB_LG_ENTRIES;
    private static int[] sintab;

    public static final int PI = (int)(Math.PI*65536.0);
    public static final int TWO_PI = (int)(2.0*Math.PI*65536.0);
    public static final int PI_OVER_TWO = (int)((Math.PI/2.0)*65536.0);
    public static final int SQRT_TWO = (int)(Math.sqrt(2.0)*65536.0);

    static {
        sintab = new int[SINTAB_ENTRIES + 1];
        for (int i = 0; i < SINTAB_ENTRIES + 1; i++) {
            double theta = i*(Math.PI/2.0)/SINTAB_ENTRIES;
            sintab[i] = (int)(Math.sin(theta)*65536.0);
        }
    }

    public static int sin(int theta) {
        int sign = 1;
        if (theta < 0) {
            theta = -theta;
            sign = -1;
        }
        // 0 <= theta
        while (theta >= TWO_PI) {
            theta -= TWO_PI;
        }
        // 0 <= theta < 2*PI
        if (theta >= PI) {
            theta = TWO_PI - theta;
            sign = -sign;
        }
        // 0 <= theta < PI
        if (theta > PI_OVER_TWO) {
            theta = PI - theta;
        }
        // 0 <= theta <= PI/2
        int itheta = (int)((long)theta*SINTAB_ENTRIES/(PI_OVER_TWO));
        return sign*sintab[itheta];
    }

    public static int cos(int theta) {
        return sin(PI_OVER_TWO - theta);
    }

//     public static double sqrt(double x) {
//         double dsqrt = Math.sqrt(x);
//         int ix = (int)(x*65536.0);
//         Int Isqrt = Isqrt(Ix);

//         Long Lx = (Long)(X*65536.0);
//         Long Lsqrt = Lsqrt(Lx);

//         System.Out.Println();
//         System.Out.Println("X = " + X);
//         System.Out.Println("Dsqrt = " + Dsqrt);

//         System.Out.Println("Ix = " + Ix);
//         System.Out.Println("Isqrt = " + Isqrt/65536.0);

//         System.Out.Println("Lx = " + Lx);
//         System.Out.Println("Lsqrt = " + Lsqrt/65536.0);

//         Return Dsqrt;
//     }

    // From Ken Turkowski, _Fixed-Point Square Root_, In Graphics Gems V
    public static int isqrt(int x) {
        int fracbits = 16;

        int root = 0;
        int remHi = 0;
        int remLo = x;
        int count = 15 + fracbits/2;

        do {
            remHi = (remHi << 2) | (remLo >>> 30); // N.B. - unsigned shift R
            remLo <<= 2;
            root <<= 1;
            int testdiv = (root << 1) + 1;
            if (remHi >= testdiv) {
                remHi -= testdiv;
                root++;
            }
        } while (count-- != 0);

        return root;
    }

    public static long lsqrt(long x) {
        int fracbits = 16;

        long root = 0;
        long remHi = 0;
        long remLo = x;
        int count = 31 + fracbits/2;

        do {
            remHi = (remHi << 2) | (remLo >>> 62); // N.B. - unsigned shift R
            remLo <<= 2;
            root <<= 1;
            long testDiv = (root << 1) + 1;
            if (remHi >= testDiv) {
                remHi -= testDiv;
                root++;
            }
        } while (count-- != 0);

        return root;
    }

    public static double hypot(double x, double y) {
        // new RuntimeException().printStackTrace();
        return Math.sqrt(x*x + y*y);
    }

    public static int hypot(int x, int y) {
        return (int)((lsqrt((long)x*x + (long)y*y) + 128) >> 8);
    }

    public static long hypot(long x, long y) {
        return (lsqrt(x*x + y*y) + 128) >> 8;
    }
}
