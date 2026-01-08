/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8371464
 * @summary C2: assert(no_dead_loop) failed: dead loop detected
 * @run main/othervm -Xcomp -XX:CompileOnly=TestDeadLoopAtMergeMem::test TestDeadLoopAtMergeMem
 * @run main TestDeadLoopAtMergeMem
 */

public class TestDeadLoopAtMergeMem {
    static final int N = 400;
    static long instanceCount;
    boolean bFld;
    float fArrFld[];
    static int iArrFld[] = new int[N];
    long vMeth_check_sum;

    public static void main(String[] strArr) {
        TestDeadLoopAtMergeMem r = new TestDeadLoopAtMergeMem();
        for (int i = 0; i < 1000; i++) {
            r.test((short) 0, instanceCount);
        }
    }

    void test(short s, long l) {
        int i11 = 6, i12, i13 = 6, i14 = 2;
        byte byArr2[] = new byte[N];
        init(byArr2, (byte) 4);
        helper(66.118169, i11);
        for (i12 = 3; i12 < 23; i12++) {
            if (bFld) {
                instanceCount = 5;
            } else if (bFld) {
                fArrFld[i12] = s;
                do {
                    try {
                        i11 = i13 / i12 % i12;
                    } catch (ArithmeticException a_e) {
                    }
                } while (i14 < 8);
            }
        }
        for (int i15 : iArrFld) {
            try {
                i11 = 1 / i15;
            } catch (ArithmeticException a_e) {
            }
        }
        vMeth_check_sum += i11;
    }

    void helper(double d, int i) {
        int i1[] = new int[N];
    }

    public static void init(byte[] a, byte seed) {
        for (int j = 0; j < a.length; j++) {
            a[j] = (byte) ((j % 2 == 0) ? seed + j : seed - j);
        }
    }
}
