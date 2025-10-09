package compiler.loopopts;

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

/**
 * @test
 * @bug 8366990
 * @summary Loop optimizations verification results in hitting the memory limit.
 *          This is caused by the high number of verification passes triggered
 *          in PhaseIdealLoop::split_if_with_blocks_post and repetitive memory
 *          allocations while building the ideal Loop tree in preparation for
 *          the verification.
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit::test
 *      -XX:-TieredCompilation -Xcomp -XX:CompileCommand=dontinline,*::*
 *      -XX:+StressLoopPeeling -XX:PerMethodTrapLimit=0 -XX:+VerifyLoopOptimizations
 *      -XX:StressSeed=1870557292
 *      compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit
 * @run main compiler.loopopts.TestVerifyLoopOptimizationsHitsMemLimit
 *
 */

public class TestVerifyLoopOptimizationsHitsMemLimit {
    static final int a = 400;
    static long b;
    static int c;
    static float k;
    static double d;
    static long e;
    static byte f;
    static boolean l;
    static long g[];
    static volatile int h[];

    static void j(int i) {
    }

    static void test(String[] m) {
        int n, o = 2, p, q[] = new int[a];
        short r = 10492;
        boolean s[] = new boolean[a];
        j(0);
        for (n = 1; n < a; ++n) {
            p = 1;
            do {
                g[n] -= p;
                switch (n) {
                    case 133:
                    case 85:
                    case 93:
                        e = 1;
                    case 45:
                        q[1] = c;
                        break;
                    case 163:
                    case 62:
                    case 304:
                    case 72:
                    case 319:
                        h[1] -= o;
                    case 109:
                    case 47:
                    case 91:
                    case 68:
                    case 162:
                        k += b;
                    case 76:
                    case 60:
                    case 66:
                        s[1] = l;
                    case 83:
                    case 339:
                    case 365:
                        d = r;
                    case 219:
                    case 42:
                    case 314:
                        k = 2;
                    case 215:
                        f = (byte) k;
                        break;
                    case 212:
                    case 53:
                    case 74:
                        d -= o;
                    case 89:
                    case 210:
                    case 208:
                    case 128:
                    case 52:
                    case 56:
                    case 144:
                        h[1] |= e;
                }
            } while (++p < 24);
        }
    }

    public static void main(String[] t) {
        try {
            test(t);
        } catch (NullPointerException e) {
            // expected
        }
    }
}
