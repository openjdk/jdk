/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8313720
 * @summary Test SuperWord with a CMove where the Bool nodes have different test codes (le and lt).
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:-TieredCompilation -XX:CompileCommand=compileonly,*TestCMoveWithDifferentBoolTest::test -Xbatch
 *                   compiler.vectorization.TestCMoveWithDifferentBoolTest
 * @run main/othervm -XX:-TieredCompilation -XX:CompileCommand=compileonly,*TestCMoveWithDifferentBoolTest::test -Xbatch
 *                   -XX:+UseVectorCmov -XX:+UseCMoveUnconditionally
 *                   compiler.vectorization.TestCMoveWithDifferentBoolTest
 */

package compiler.vectorization;

public class TestCMoveWithDifferentBoolTest {
    static final int RANGE = 1024;
    static final int ITER  = 100;

    static void init(float[] dataF, int scale) {
        for (int i = 0; i < RANGE; i++) {
            // produce in repetition: scale zeros, then scale ones
            dataF[i] = (i / scale) % 2;
        }
    }

    static void test(float[] a, float[] b, float[] r) {
        for (int i = 0; i < RANGE; i+=2) {
            r[i+0] = (a[i+0] >  b[i+0]) ? 1 : 0; // Everything could be packed, except
            r[i+1] = (a[i+1] >= b[i+1]) ? 1 : 0; // that we have ">" vs ">=".
        }
    }

    public static void main(String[] args) {
        float[] a = new float[RANGE];
        float[] b = new float[RANGE];
        float[] r = new float[RANGE];
        float[] r2 = new float[RANGE];
        init(a, 3); // different scales
        init(b, 5);
        test(a, b, r2); // run before compilation
        for (int i = 0; i < ITER; i++) {
            test(a, b, r); // at some point compiled
        }
        verify(r, r2, a, b);
    }

    static void verify(float[] r, float[] r2, float[] a, float[] b) {
        boolean failed = false;
        for (int i = 0; i < RANGE; i++) {
            if (r[i] != r2[i] && !( Float.isNaN(r[i]) && Float.isNaN(r2[i]) )) {
                System.out.println("fail: " + i + " " + r[i] + " != " + r2[i] + " : " + a[i] + " " + b[i]);
                failed = true;
            }
        }
        if (failed) {
            throw new RuntimeException("There were wrong results");
        }
    }
}
