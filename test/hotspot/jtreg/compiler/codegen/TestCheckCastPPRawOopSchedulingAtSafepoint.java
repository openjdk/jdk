/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8372649
 * @summary CheckCastPP with RawPtr input can be scheduled below a safepoint during C2
 *          post-regalloc block-local scheduling, causing a live raw oop at the safepoint
 *          instead of a corresponding oop in the OopMap.
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:TrackedInitializationLimit=0
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   -XX:+OptoScheduling ${test.main.class}
 */

package compiler.codegen;

import jdk.test.lib.Asserts;

public class TestCheckCastPPRawOopSchedulingAtSafepoint {

    static public void main(String[] args) {
        for (int j = 6; 116 > j; ++j) {
            test();
        }
        Asserts.assertEQ(Test.c,43560L);
    }

    static class Test {
        static int a = 256;
        float[] b = new float[256];
        static long c;
    }

    static void test() {
        float[][] g = new float[Test.a][Test.a];
        for (int d = 7; d < 16; d++) {
            long e = 1;
            do {
                g[d][(int) e] = d;
                synchronized (new Test()) {}
            } while (++e < 5);
        }
        for (int i = 0; i < Test.a; ++i) {
            for (int j = 0; j < Test.a ; ++j) {
                Test.c += g[i][j];
            }
        }
    }
}
