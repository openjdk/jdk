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

/*
 * @test
 * @bug 8361608
 * @summary crash during unrolling in some rare cases where loop cloning
 *          (typically from peeling) breaks an invariant in do_unroll
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+StressLoopPeeling
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TooStrictAssertForUnrollAfterPeeling::test1
 *                   -XX:-TieredCompilation
 *                   -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   compiler.loopopts.TooStrictAssertForUnrollAfterPeeling
 *
 * @run main/othervm -XX:CompileOnly=compiler.loopopts.TooStrictAssertForUnrollAfterPeeling::test2
 *                   -XX:-TieredCompilation
 *                   -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=inline,compiler.loopopts.TooStrictAssertForUnrollAfterPeeling::foo2
 *                   compiler.loopopts.TooStrictAssertForUnrollAfterPeeling
 *
 * @run main/othervm -XX:CompileOnly=compiler.loopopts.TooStrictAssertForUnrollAfterPeeling::test3
 *                   -XX:-TieredCompilation
 *                   -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=inline,compiler.loopopts.TooStrictAssertForUnrollAfterPeeling::foo3
 *                   -XX:-RangeCheckElimination
 *                   compiler.loopopts.TooStrictAssertForUnrollAfterPeeling
 *
 * @run main compiler.loopopts.TooStrictAssertForUnrollAfterPeeling
 */
package compiler.loopopts;


/* Cases 2 and 3 can use the additional flags
 *   -XX:+UnlockDiagnosticVMOptions
 *   -XX:-LoopMultiversioning
 *   -XX:-RangeCheckElimination
 *   -XX:-SplitIfBlocks
 *   -XX:-UseOnStackReplacement
 *   -XX:LoopMaxUnroll=2
 * to disable more optimizations and give a simpler graph, while still reproducing. It can be useful to debug, investigate...
 */
public class TooStrictAssertForUnrollAfterPeeling {
    static int iArr[] = new int[400];
    static boolean flag;

    public static void main(String[] args) {
        run1();
        run2();
        run3();
    }

    // Case 1

    public static void run1() {
        for (int i = 1; i < 1000; i++) {
            test1();
        }
    }

    static long test1() {
        int s = 0;
        int iArr[] = new int[400];
        for (int i = 0; i < 70; i++) {}

        for (int i = 0; i < 36; i++) {
            for (int j = 0; j < 3; j++) {
                s += iArr[0] = 7;
                if (s != 0) {
                    return s + foo1(iArr);
                }
            }
        }
        return 0;
    }

    public static long foo1(int[] a) {
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i];
        }
        return sum;
    }

    // Case 2

    public static void run2() {
        for (int i = 1; i < 10000; i++) {
            test2();
        }
    }

    // Lx: Optimized in loop opts round x.

    static int test2() {
        int x = 5;
        for (int i = 1; i < 37; i++) { // L3: Peeled
            for (int a = 0; a < 2; a++) { // L2: Max unrolled
                for (int b = 0; b < 300; b++) {
                } // L1: Empty -> removed
            }
            int j = 1;
            x *= 12;
            while (++j < 5) { // L1: Max unrolled: peel + unroll
                iArr[0] += 2;
                if (iArr[0] > 0) {
                    // foo(): everything outside loop.
                    return foo2(iArr);
                }
            }
        }
        return 3;
    }

    public static int foo2(int[] a) {
        int sum = 0;
        for (int i = 0; i < a.length; i++) { // L2: Pre/main/post, L3: Unrolled -> hit assert!
            for (int j = 0; j < 34; j++) {
            } // L1: Empty -> removed
            if (flag) {
                // Ensure not directly unrolled in L2 but only in L3.
                return 3;
            }
            sum += a[i];
        }
        return sum;
    }

    public static void run3() {
        for (int i = 1; i < 10000; i++) {
            test3();
        }
    }

    // Case 3

    static int test3() {
        int x = 5;
        for (int i = 1; i < 37; i++) { // L3: Peeled
            for (int a = 0; a < 2; a++) { // L2: Max unrolled
                for (int b = 0; b < 300; b++) {
                } // L1: Empty -> removed
            }
            int j = 1;
            x *= 12;
            while (++j < 5) { // L1: Max unrolled: peel + unroll
                iArr[0] += 2;
                if (iArr[0] > 0) {
                    // foo(): everything outside loop.
                    return foo3(iArr, x);
                }
            }
        }
        return 3;
    }

    public static int foo3(int[] a, int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) { // L2: Pre/main/post, L3: Unrolled -> hit assert!
            for (int j = 0; j < 34; j++) {
            } // L1: Empty -> removed
            if (flag) {
                // Ensure not directly unrolled in L2 but only in L3.
                return 3;
            }
            sum += a[i];
        }
        return sum;
    }
}
