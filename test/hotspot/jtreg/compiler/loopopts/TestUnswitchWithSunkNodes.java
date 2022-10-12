/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @bug 8271954
 * @requires vm.compiler2.enabled
 * @summary A pinned Cast node on the UCT projection of a predicate is as input for the UCT phi node for the original
 *          loop, the fast and slow loop resulting in a dominance failure. These data nodes need to be updated while
 *          unswitching a loop.
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,compiler.loopopts.TestUnswitchWithSunkNodes::test*
 *                   compiler.loopopts.TestUnswitchWithSunkNodes
 */

package compiler.loopopts;

public class TestUnswitchWithSunkNodes {
    static int iFld, iFld2, iFld3 = 1, iFld4 = 1;
    static long instanceCount;
    static double dFld;
    static boolean bFld;
    static int iArrFld[] = new int[10];

    static {
        init(iArrFld);
    }

    public static void main(String[] strArr) {
        // The testcases with Divisions have additional control inputs which need to be taken care of
        testNoDiamond();
        testNoDiamondDiv();
        testWithDiamond();
        testWithDiamondDiv1();
        testWithDiamondDiv2();
        testWithDiamondOneLongOneShortPath();
        testWithDiamondComplex();
        testWithDiamondComplexDiv();
    }

    static void testNoDiamond() {
        int i, i2 = 10, i21, i22, i24, i26 = 41724, iArr2[] = new int[10];
        double d;
        float f2;
        init(iArr2);
        i = 1;
        while (++i < 219) {
            for (d = 15; 305 > d; ++d) {
                if (bFld) {
                    instanceCount = i2;
                    i2 = (((i2 + 3) + iFld2) + iFld3) - iFld4;
                }
                for (f2 = 5; 87 > f2; ++f2) {
                    i2 = (int) instanceCount;
                    for (i22 = 1; 2 > i22; i22++) {
                        if (bFld) {
                            iArr2[1] += 190L;
                        }
                    }
                    for (i24 = 1; 2 > i24; i24++) {
                        switch (i) {
                            case 88:
                                i26 += (i24);
                        }
                    }
                }
            }
        }
    }

    static void testNoDiamondDiv() {
        int i, i2 = 10, i21, i22, i24, i26 = 41724, iArr2[] = new int[10];
        double d;
        float f2;
        init(iArr2);
        i = 1;
        while (++i < 219) {
            for (d = 15; 305 > d; ++d) {
                if (bFld) {
                    instanceCount = i2;
                    i2 = (((i2 + 3) + iFld2) + iFld3) / iFld4;
                }
                for (f2 = 5; 87 > f2; ++f2) {
                    i2 = (int) instanceCount;
                    for (i22 = 1; 2 > i22; i22++) {
                        if (bFld) {
                            iArr2[1] += 190L;
                        }
                    }
                    for (i24 = 1; 2 > i24; i24++) {
                        switch (i) {
                            case 88:
                                i26 += (i24);
                        }
                    }
                }
            }
        }
    }

    static void testWithDiamond() {
        int i, i2 = 10, i21, i22, i24, i26 = 41724, iArr2[] = new int[10];
        double d;
        float f2;
        init(iArr2);
        i = 1;
        while (++i < 219) {
            for (d = 15; 305 > d; ++d) {
                if (bFld) {
                    instanceCount = i2;
                    double d1 = (double) i2;
                    i2 = (int)((d1 + iFld4) - (d1 + iFld));
                }
                for (f2 = 5; 87 > f2; ++f2) {
                    i2 = (int) instanceCount;
                    for (i22 = 1; 2 > i22; i22++) {
                        if (bFld) {
                            iArr2[1] += 190L;
                        }
                    }
                    for (i24 = 1; 2 > i24; i24++) {
                        switch (i) {
                            case 88:
                                i26 += (i24);
                        }
                    }
                }
            }
        }
    }

    static void testWithDiamondDiv1() {
        int i, i2 = 10, i21, i22, i24, i26 = 41724, iArr2[] = new int[10];
        double d;
        float f2;
        init(iArr2);
        i = 1;
        while (++i < 219) {
            for (d = 15; 305 > d; ++d) {
                if (bFld) {
                    i2 = (i2 / iFld4) - (i2 / iFld3);
                }
                for (f2 = 5; 87 > f2; ++f2) {
                    i2 = (int) instanceCount;
                    for (i22 = 1; 2 > i22; i22++) {
                        if (bFld) {
                            iArr2[1] += 190L;
                        }
                    }
                    for (i24 = 1; 2 > i24; i24++) {
                        switch (i) {
                            case 88:
                                i26 += (i24);
                        }
                    }
                }
            }
        }
    }

    static void testWithDiamondDiv2() {
        int i, i2 = 10, i21, i22, i24, i26 = 41724, iArr2[] = new int[10];
        double d;
        float f2;
        init(iArr2);
        i = 1;
        while (++i < 219) {
            for (d = 15; 305 > d; ++d) {
                if (bFld) {
                    instanceCount = i2;
                    int i3 = (int)d;
                    i2 = (i3 / iFld4) - (i3 / iFld3);
                }
                for (f2 = 5; 87 > f2; ++f2) {
                    i2 = (int) instanceCount;
                    for (i22 = 1; 2 > i22; i22++) {
                        if (bFld) {
                            iArr2[1] += 190L;
                        }
                    }
                    for (i24 = 1; 2 > i24; i24++) {
                        switch (i) {
                            case 88:
                                i26 += (i24);
                        }
                    }
                }
            }
        }
    }

    static void testWithDiamondOneLongOneShortPath() {
        int i, i2 = 10, i21, i22, i24, i26 = 41724, iArr2[] = new int[10];
        double d;
        float f2;
        init(iArr2);
        i = 1;
        while (++i < 219) {
            for (d = 15; 305 > d; ++d) {
                if (bFld) {
                    instanceCount = i2;
                    double d1 = (double)i2;
                    i2 = (int)((d1 + 3 + iFld2 + iFld3 + iFld4) - (d1 + iFld));
                }
                for (f2 = 5; 87 > f2; ++f2) {
                    i2 = (int) instanceCount;
                    for (i22 = 1; 2 > i22; i22++) {
                        if (bFld) {
                            iArr2[1] += 190L;
                        }
                    }
                    for (i24 = 1; 2 > i24; i24++) {
                        switch (i) {
                            case 88:
                                i26 += (i24);
                        }
                    }
                }
            }
        }
    }

    static void testWithDiamondComplex() {
        int i, i2 = 10, i21, i22, i24, i26 = 41724, iArr2[] = new int[10];
        double d;
        float f2;
        init(iArr2);
        i = 1;
        while (++i < 219) {
            for (d = 15; 305 > d; ++d) {
                if (bFld) {
                    instanceCount = i2;
                    i2 += 4;
                    float l = (float)i2;
                    i2 = (int)((l + 3) - (l + iFld));
                    double d1 = (double)i2;
                    i2 = (int)((d1 + 3) - (d1 + iFld) - (d1 * iFld2));
                    i2 = (int)((l + 3) - (l + iFld) - (l * iFld2) + (d1 - iFld2) + (d1 / iFld3));
                    d1 = (double)i2;
                    i2 = (int)((d1 + 3) - (d1 + iFld) - (d1 * iFld2) + (d1 - iFld2) + (d1 / iFld3));
                    i2 += dFld;
                    l = (float)i2;
                    i2 = (int)((l + 3) - (l + iFld) - (l * iFld2) + (d1 - iFld2) + (d1 / iFld3) - (d1 + iFld) - (d1 * iFld2) + (d1 - iFld2) + (d1 / iFld3));
                    d1 = (double)i2;
                    i2 = (int)((d1 + 3) - (d1 + iFld) - (d1 * iFld2) + (d1 - iFld2) + (d1 / iFld3) + (d1 + 3) - (d1 + iFld) - (d1 * iFld2) + (d1 - iFld2) + (d1 / iFld3));
                    d1 = (double) i2;
                    i2 = (int) ((d1 + 3 + iFld2 + iFld3 + iFld4) - (d1 + iFld));
                }
                for (f2 = 5; 87 > f2; ++f2) {
                    i2 = (int) instanceCount;
                    for (i22 = 1; 2 > i22; i22++) {
                        if (bFld) {
                            iArr2[1] += 190L;
                        }
                    }
                    for (i24 = 1; 2 > i24; i24++) {
                        switch (i) {
                            case 88:
                                i26 += (i24);
                        }
                    }
                }
            }
        }
    }

    static void testWithDiamondComplexDiv() {
        int i, i2 = 10, i21, i22, i24, i26 = 41724, iArr2[] = new int[10];
        double d;
        float f2;
        init(iArr2);
        i = 1;
        while (++i < 219) {
            for (d = 15; 305 > d; ++d) {
                if (bFld) {
                    instanceCount = i2;
                    int i3 = (int)d;
                    i2 = (i3 / iFld4) - (i3 / iFld3);
                    double d1 = (double) i2;
                    i3 = (int)((d1 + iFld4) - (d1 + iFld));
                    i2 = (i3 / iFld4) - (i3 / iFld3);
                }
                for (f2 = 5; 87 > f2; ++f2) {
                    i2 = (int) instanceCount;
                    for (i22 = 1; 2 > i22; i22++) {
                        if (bFld) {
                            iArr2[1] += 190L;
                        }
                    }
                    for (i24 = 1; 2 > i24; i24++) {
                        switch (i) {
                            case 88:
                                i26 += (i24);
                        }
                    }
                }
            }
        }
    }

    public static void init(int[] a) {
        for (int j = 0; j < a.length; j++) {
            a[j] = j;
        }
    }
}
