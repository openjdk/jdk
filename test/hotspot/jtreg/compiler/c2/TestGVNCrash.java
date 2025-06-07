/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All Rights Reserved.
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
 *
 */

/**
 * @test
 * @key stress randomness
 * @bug 8288204
 * @summary GVN Crash: assert() failed: correct memory chain
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:CompileCommand=compileonly,compiler.c2.TestGVNCrash::test compiler.c2.TestGVNCrash
 */

package compiler.c2;

public class TestGVNCrash {
    public static int iField = 0;
    public static double[] dArrFld = new double[256];
    public static int[] iArrFld = new int[256];
    public int[][] iArrFld1 = new int[256][256];

    public void test() {
        int x = 0;
        for (int i = 0; i < 10; i++) {
            do {
                for (float j = 0; j < 0; j++) {
                    iArrFld[x] = 3;
                    iArrFld1[1][x] -= iField;
                    dArrFld = new double[256];
                    for (int k = 0; k < dArrFld.length; k++) {
                        dArrFld[k] = (k % 2 == 0) ? k + 1 : k - 1;
                    }
                }
            } while (++x < 5);
            for (int j = 0; j < 100_000; j++) {
                String s = "test";
                s = s + s;
                s = s + s;
            }
        }
    }

    public static void main(String[] args) {
        TestGVNCrash t = new TestGVNCrash();
        t.test();
    }
}