/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @test
 * @bug 8281429
 * @summary PhiNode::Value() is too conservative for tripcount of CountedLoop
 * @run main/othervm -XX:-TieredCompilation -Xbatch TestCountedLoopPhiValue
 */

public class TestCountedLoopPhiValue {
    public static void main(String[] args) {
        test1();
        test2();
    }

    private static void test1() {
        for (long l = (Long.MAX_VALUE - 1); l != (Long.MIN_VALUE + 100_000); l++) {
            if (l == 0) {
                throw new RuntimeException("Test failed");
            }
        }
    }

    private static void test2() {
        for (int i = 1; i < 10 * 182 * 138; i++) {
            iMeth(-9, -9);
        }
    }

    public static final int N = 400;

    public static long instanceCount=-2L;
    public static long lArrFld[]=new long[N];

    public static void iMeth(int i6, int i7) {

        double d1;
        int i8, i10, i11=30785, i12=8;
        long l2;
        byte by=58;

        d1 = i7;
        i8 = 1;
        while (++i8 < 342) {
            for (l2 = 1; l2 < 5; l2++) {
                i6 -= (int)d1;
                for (i10 = 1; i10 < 2; i10++) {
                    i7 += (int)l2;
                    i12 *= i11;
                    i11 -= i12;
                    instanceCount += i10;
                    lArrFld[i8 - 1] = by;
                }
            }
        }
    }
}
