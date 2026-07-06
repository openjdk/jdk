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
 * @bug 8372649 8376189
 * @summary CheckCastPP with RawPtr input can be scheduled below a safepoint during C2
 *          post-regalloc block-local scheduling, causing a live raw oop at the safepoint
 *          instead of a corresponding oop in the OopMap.
 * @library /test/lib
 * @modules jdk.incubator.vector
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:TrackedInitializationLimit=0
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test8372649
 *                   -XX:+OptoScheduling ${test.main.class} 8372649
 * @run main/othervm --add-modules=jdk.incubator.vector
 *                   -XX:+OptoScheduling ${test.main.class} 8376189
 */

package compiler.codegen;

import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

public class TestCheckCastPPRawOopSchedulingAtSafepoint {
    private static final VectorSpecies<Integer> SPECIES_I = IntVector.SPECIES_64;

    static public void main(String[] args) {
        String mode = args[0];
        if ("8372649".equals(mode)) {
            run8372649();
        } else if ("8376189".equals(mode)) {
            run8376189();
        }
    }

    // JDK-8372649
    static void run8372649(){
        for (int j = 6; 116 > j; ++j) {
            test8372649();
        }
        Asserts.assertEQ(Test.c, 43560L);
    }

    static class Test {
        static int a = 256;
        float[] b = new float[256];
        static long c;
    }

    static void test8372649() {
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

    // JDK-8376189
    static void run8376189(){
        int[] a = new int[10_000];
        int r = 0;
        for (int i = 0; i < 10_000; i++) {
            r = test8376189(a);
        }
        Asserts.assertEQ(r, 0);
    }

    public static int test8376189(int[] a) {
        var mins = IntVector.broadcast(SPECIES_I, a[0]);
        for (int i = 0; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            mins = IntVector.fromArray(SPECIES_I, a, 0);
        }
        return mins.reduceLanes(VectorOperators.MIN);
    }
}