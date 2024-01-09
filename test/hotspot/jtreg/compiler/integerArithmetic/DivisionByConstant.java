/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package compiler.integerArithmetic;

import compiler.lib.ir_framework.*;
import java.util.Random;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8282365
 * @summary Test that Ideal transformations of division nodes provide correct
 * result.
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib /
 * @run driver compiler.integerArithmetic.DivisionByConstant
 */
public class DivisionByConstant {
    private static final int TRIALS = 10;
    private static final int INVOCATIONS = 100;

    private static final int I_DIV;
    private static final int I_1;
    private static final int I_2;
    private static final long L_DIV;
    private static final long L_1;;
    private static final long L_2;

    static {
        int iDiv = 0;
        int i1 = 0;
        int i2 = 0;
        long lDiv = 0;
        long l1 = 0;
        long l2 = 0;
        try {
            iDiv = Integer.parseUnsignedInt(System.getProperty("iDiv"));
            i1 = Integer.parseUnsignedInt(System.getProperty("i1"));
            i2 = Integer.parseUnsignedInt(System.getProperty("i2"));
            lDiv = Long.parseUnsignedLong(System.getProperty("lDiv"));
            l1 = Long.parseUnsignedLong(System.getProperty("l1"));
            l2 = Long.parseUnsignedLong(System.getProperty("l2"));
        } catch (Exception e) {}

        I_DIV = iDiv;
        I_1 = i1;
        I_2 = i2;
        L_DIV = lDiv;
        L_1 = l1;
        L_2 = l2;
    }
    private static final int I_LO = Math.min(I_1, I_2);
    private static final int I_HI = Math.max(I_1, I_2);
    private static final int I_UHI = I_1;
    private static final long L_LO = Math.min(L_1, L_2);
    private static final long L_HI = Math.max(L_1, L_2);
    private static final long L_UHI = L_1;

    public static void main(String[] args) {
        Random r = Utils.getRandomInstance();
        for (int i = 0; i < TRIALS; i++) {
            String iDiv = Long.toUnsignedString(logRandom(r, Integer.SIZE));
            String i1 = Long.toUnsignedString(logRandom(r, Integer.SIZE));
            String i2 = Long.toUnsignedString(logRandom(r, Integer.SIZE));
            String lDiv = Long.toUnsignedString(logRandom(r, Long.SIZE));
            String l1 = Long.toUnsignedString(logRandom(r, Long.SIZE));
            String l2 = Long.toUnsignedString(logRandom(r, Long.SIZE));

            var test = new TestFramework(DivisionByConstant.class);
            test.setDefaultWarmup(1);
            test.addFlags("-DiDiv=" + iDiv, "-Di1=" + i1, "-Di2=" + i2,
                    "-DlDiv=" + lDiv, "-Dl1=" + l1, "-Dl2=" + l2);
            test.start();
        }
    }

    static long logRandom(Random r, int bits) {
        int highestBit = r.nextInt(bits);
        long res = r.nextLong() & (-1L >>> (Long.SIZE - 1 - highestBit));
        return res == 0 ? 1 : res;
    }

    @Run(test = {"sDivInt", "uDivInt", "sDivLong", "uDivLong"})
    public void run() {
        Random r = Utils.getRandomInstance();
        for (int i = 0; i < INVOCATIONS; i++) {
            {
                int x;
                if (I_HI != Integer.MAX_VALUE) {
                    x = r.nextInt(I_LO, I_HI + 1);
                } else if (I_LO != Integer.MIN_VALUE) {
                    x = r.nextInt(I_LO - 1, I_HI) + 1;
                } else {
                    x = r.nextInt();
                }
                Asserts.assertEQ(sDiv(x, I_DIV), sDivInt(x));
            }
            {
                int x;
                if (I_UHI >= 0) {
                    x = r.nextInt(-1, I_UHI) + 1;
                } else {
                    x = r.nextInt(Integer.MIN_VALUE, I_UHI + 1);
                }
                Asserts.assertEQ(uDiv(x, I_DIV), uDivInt(x));
            }
            {
                long y; int x;
                if (L_LO >= Integer.MIN_VALUE && L_HI <= Integer.MAX_VALUE) {
                    y = r.nextLong(L_LO, L_HI + 1);
                    x = (int)y;
                } else {
                    x = r.nextInt();
                    if (x > 0) {
                        y = L_LO - 1 + x;
                    } else {
                        y = L_HI + x;
                    }
                }
                Asserts.assertEQ(sDiv(y, L_DIV), sDivLong(x));
            }
            {
                long y; int x;
                if (L_UHI >= 0 && L_UHI <= Integer.MAX_VALUE) {
                    y = r.nextLong(L_UHI + 1);
                    x = (int)y;
                } else {
                    x = r.nextInt();
                    if (x > 0) {
                        y = x;
                    } else {
                        y = L_UHI + x;
                    }
                }
                Asserts.assertEQ(uDiv(y, L_DIV), uDivLong(x));
            }
        }
    }

    @DontCompile
    static int sDiv(int x, int y) {
        return x / y;
    }

    @DontCompile
    static int uDiv(int x, int y) {
        return Integer.divideUnsigned(x, y);
    }

    @DontCompile
    static long sDiv(long x, long y) {
        return x / y;
    }

    @DontCompile
    static long uDiv(long x, long y) {
        return Long.divideUnsigned(x, y);
    }

    @Test
    @IR(failOn = IRNode.DIV)
    public int sDivInt(int x) {
        int dividend = Math.min(I_HI, Math.max(I_LO, x));
        return dividend / I_DIV;
    }

    @Test
    @IR(failOn = IRNode.UDIV_I, applyIfPlatform = {"x64", "true"})
    static int uDivInt(int x) {
        int dividend = I_UHI < 0
                ? Math.min(I_UHI, x)
                : Math.min(I_UHI, Math.max(0, x));
        return Integer.divideUnsigned(dividend, I_DIV);
    }

    @Test
    @IR(failOn = IRNode.DIV_L, applyIfPlatform = {"64-bit", "true"})
    static long sDivLong(int x) {
        long dividend;
        if (L_LO >= Integer.MIN_VALUE && L_HI <= Integer.MAX_VALUE) {
            dividend = Math.min((int)L_HI, Math.max((int)L_LO, x));
        } else if (x > 0) {
            dividend = L_LO - 1 + x;
        } else {
            dividend = L_HI + x;
        }
        return dividend / L_DIV;
    }

    @Test
    @IR(failOn = IRNode.UDIV_L, applyIfPlatform = {"x64", "true"})
    static long uDivLong(int x) {
        long dividend;
        if (L_UHI >= 0 && L_UHI <= Integer.MAX_VALUE) {
            dividend = Math.min((int)L_UHI, Math.max(0, x));
        } else if (x > 0) {
            dividend = x;
        } else {
            dividend = L_UHI + x;
        }
        return Long.divideUnsigned(dividend, L_DIV);
    }
}
