/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.loopopts;

/**
 * @test
 * @bug 8366990
 * @summary Loop optimizations verification results in hitting the memory limit.
 *          This is caused by the high number of verification passes triggered
 *          in PhaseIdealLoop::split_if_with_blocks_post and repetitive memory
 *          allocations while building the ideal loop tree in preparation for
 *          the verification.
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *      -XX:CompileCommand=compileonly,compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit::test
 *      -XX:CompileCommand=memlimit,compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit::test,100M~crash
 *      -XX:-TieredCompilation -Xcomp -XX:PerMethodTrapLimit=0
 *      -XX:+StressLoopPeeling -XX:+VerifyLoopOptimizations
 *      -XX:StressSeed=1870557292 -XX:-StressDuplicateBackedge
 *      compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *      -XX:CompileCommand=compileonly,compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit::test
 *      -XX:CompileCommand=memlimit,compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit::test,100M~crash
 *      -XX:-TieredCompilation -Xcomp -XX:PerMethodTrapLimit=0
 *      -XX:+StressLoopPeeling -XX:+VerifyLoopOptimizations -XX:-StressDuplicateBackedge
 *      compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-StressDuplicateBackedge
 *                   compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit
 */

public class TestVerifyLoopOptimizationsHitsMemLimit {
    final int a = 400;
    int b;
    float c;
    static double d;
    static byte f;
    long g[];
    volatile int h[];

    void test() {
        int j, k = 2, l, o[] = new int[a];
        short m = 10492;
        for (j = 1;; ++j) {
            l = 1;
            do {
                g[j] = l;
                switch (j) {
                    case 45:
                        o[1] = b;
                    case 163:
                    case 62:
                    case 72:
                    case 319:
                        h[1] -= k;
                    case 109:
                    case 47:
                    case 91:
                    case 68:
                    case 162:
                    case 76:
                    case 60:
                    case 66:
                    case 83:
                        d = m;
                    case 2314:
                        f = (byte) c;
                }
            } while (++l < 4);
        }
    }

    public static void main(String[] args) {
        try {
            TestVerifyLoopOptimizationsHitsMemLimit test = new TestVerifyLoopOptimizationsHitsMemLimit();
            test.test();
            throw new RuntimeException("Expected a NPE for uninitialized array");
        } catch (NullPointerException e) {
            // expected
        }
    }
}
