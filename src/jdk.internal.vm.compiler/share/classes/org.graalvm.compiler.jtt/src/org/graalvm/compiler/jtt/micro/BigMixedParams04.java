/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 */


package org.graalvm.compiler.jtt.micro;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/**
 * Tests different alignment on the stack with extended parameters (index > 5).
 */
public class BigMixedParams04 extends JTTTest {

    @SuppressWarnings("unused")
    public static long test(int choice, int i0, int i1, int i2, int i3, double d1, double d2, boolean bo1, boolean bo2, byte by, short sh, char ch, int in) {
        switch (choice) {
            case 0:
                return bo1 ? 1L : 2L;
            case 1:
                return bo2 ? 1L : 2L;
            case 2:
                return by;
            case 3:
                return sh;
            case 4:
                return ch;
            case 5:
                return in;
        }
        return 42;
    }

    /**
     * Test SPARC mixed params with double/single float register overlapping.
     *
     * @param f1
     * @param d2
     * @param f3
     * @return Must always return the argument d2
     */
    @SuppressWarnings("all")
    public static double test2(int i1, float f1, double d2, float f3,
// @formatter:off
                    double ad1,
                    double ad2,
                    double ad3,
                    double ad4,
                    double ad5,
                    double ad6,
                    double ad7,
                    double ad8,
                    double ad9,
                    double ad10,
                    double ad11,
                    double ad12,
                    double ad13,
                    double ad14,
                    double ad15,
                    double ad16,
                    float  af1,
                    float  af2,
                    float  af3,
                    float  af4,
                    float  af5,
                    float  af6,
                    float  af7,
                    float  af8,
                    float  af9,
                    float  af10,
                    float  af11,
                    float  af12,
                    float  af13,
                    float  af14,
                    float  af15,
                    float  af16
    // @formatter:on
    ) {

        // now do something with the locals to make sure the locals don't get optimized away.
        for (int i = 0; i < i1; i++) {
            af1 += f1;
            af2 += f1;
            af3 += f1;
            af4 += f1;
            af5 += f1;
            af6 += f1;
            af7 += f1;
            af8 += f1;
            af9 += f1;
            af10 += f1;
            af11 += f1;
            af12 += f1;
            af13 += f1;
            af14 += f1;
            af15 += f1;
            af16 += f1;
            ad1 += f1;
            ad2 += f1;
            ad3 += f1;
            ad4 += f1;
            ad5 += f1;
            ad6 += f1;
            ad7 += f1;
            ad8 += f1;
            ad9 += f1;
            ad10 += f1;
            ad11 += f1;
            ad12 += f1;
            ad13 += f1;
            ad14 += f1;
            ad15 += f1;
            ad16 += f1;
        }
        // @formatter:off
        boolean orderFloat =
                        af1  < af2  &&
                        af2  < af3  &&
                        af3  < af4  &&
                        af4  < af5  &&
                        af5  < af6  &&
                        af6  < af7  &&
                        af7  < af8  &&
                        af8  < af9  &&
                        af9  < af10 &&
                        af10 < af11 &&
                        af11 < af12 &&
                        af12 < af13 &&
                        af13 < af14 &&
                        af14 < af15 &&
                        af15 < af16;
        boolean orderDouble =
                        ad1  < ad2  &&
                        ad2  < ad3  &&
                        ad3  < ad4  &&
                        ad4  < ad5  &&
                        ad5  < ad6  &&
                        ad6  < ad7  &&
                        ad7  < ad8  &&
                        ad8  < ad9  &&
                        ad9  < ad10 &&
                        ad10 < ad11 &&
                        ad11 < ad12 &&
                        ad12 < ad13 &&
                        ad13 < ad14 &&
                        ad14 < ad15 &&
                        ad15 < ad16;
        // @formatter:on
        if (orderDouble && orderFloat) {
            return f1 + d2 + f3; // this should not be destroyed
        }
        Assert.fail();
        return 0.0;
    }

    /**
     * Test SPARC mixed params with double/single float register overlapping.
     *
     * @param f1
     * @param d2
     * @param f3
     * @return Must always return the argument d2
     */
    @SuppressWarnings("all")
    public static double test3(boolean f, int idx,
// @formatter:off
                    double ad1,
                    double ad2,
                    double ad3,
                    double ad4,
                    double ad5,
                    double ad6,
                    double ad7,
                    double ad8,
                    double ad9,
                    double ad10,
                    double ad11,
                    double ad12,
                    double ad13,
                    double ad14,
                    double ad15,
                    double ad16,
                    float  af1,
                    float  af2,
                    float  af3,
                    float  af4,
                    float  af5,
                    float  af6,
                    float  af7,
                    float  af8,
                    float  af9,
                    float  af10,
                    float  af11,
                    float  af12,
                    float  af13,
                    float  af14,
                    float  af15,
                    float  af16
    ) {
        switch(f ? idx + 16 : idx) {
            case 1 : return ad1;
            case 2 : return ad2;
            case 3 : return ad3;
            case 4 : return ad4;
            case 5 : return ad5;
            case 6 : return ad6;
            case 7 : return ad7;
            case 8 : return ad8;
            case 9 : return ad9;
            case 10: return ad10;
            case 11: return ad11;
            case 12: return ad12;
            case 13: return ad13;
            case 14: return ad14;
            case 15: return ad15;
            case 16: return ad16;
            case 1  + 16: return af1;
            case 2  + 16: return af2;
            case 3  + 16: return af3;
            case 4  + 16: return af4;
            case 5  + 16: return af5;
            case 6  + 16: return af6;
            case 7  + 16: return af7;
            case 8  + 16: return af8;
            case 9  + 16: return af9;
            case 10 + 16: return af10;
            case 11 + 16: return af11;
            case 12 + 16: return af12;
            case 13 + 16: return af13;
            case 14 + 16: return af14;
            case 15 + 16: return af15;
            case 16 + 16: return af16;
        }
        Assert.fail(); // should not reach here
        return 0;

    }
    // @formatter:on

    @Test
    public void run0() throws Throwable {
        runTest("test", 0, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 4, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 5, -1, -1, -1, -1, 1d, 2d, true, false, (byte) -128, (short) -0x7FFF, (char) 0xFFFF, -0x7FFFFFF);
    }

    @Test
    public void run6() throws Throwable {
        // @formatter:off
        runTest("test2", 20, 1.0f, -3.2912948246387967943231233d, 3.0f,
                        1d,
                        2d,
                        3d,
                        4d,
                        5d,
                        6d,
                        7d,
                        8d,
                        9d,
                        10d,
                        11d,
                        12d,
                        13d,
                        14d,
                        15d,
                        16d,
                        1f,
                        2f,
                        3f,
                        4f,
                        5f,
                        6f,
                        7f,
                        8f,
                        9f,
                        10f,
                        11f,
                        12f,
                        13f,
                        14f,
                        15f,
                        16f
                        );
        // @formatter:on
    }

    @Test
    public void run7() throws Throwable {
        // @formatter:off
        for (int i = 0; i < 32 * 2; i++) {
        runTest("test3", i % 2 == 0, i / 2,
                        1d,
                        2d,
                        3d,
                        4d,
                        5d,
                        6d,
                        7d,
                        8d,
                        9d,
                        10d,
                        11d,
                        12d,
                        13d,
                        14d,
                        15d,
                        16d,
                        1f,
                        2f,
                        3f,
                        4f,
                        5f,
                        6f,
                        7f,
                        8f,
                        9f,
                        10f,
                        11f,
                        12f,
                        13f,
                        14f,
                        15f,
                        16f
                        );
        }
        // @formatter:on
    }

}
