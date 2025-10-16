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
 * @summary assert in do_unroll does not hold in some cases when peeling comes
 *          just before unrolling. It seems to happen only with stress peeling
 *
 * @run main/othervm -XX:CompileOnly=compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling3::test
 *                   -XX:-TieredCompilation
 *                   -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=inline,compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling3::foo
 *                   -XX:-RangeCheckElimination
 *                   compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling3
 */

/*
 * @test
 * @bug 8361608
 * @summary assert in do_unroll does not hold in some cases when peeling comes
 *          just before unrolling. It seems to happen only with stress peeling
 * @run main/othervm -XX:CompileOnly=compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling3::test
 *                   -XX:-TieredCompilation
 *                   -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-LoopMultiversioning
 *                   -XX:-RangeCheckElimination
 *                   -XX:-SplitIfBlocks
 *                   -XX:-UseOnStackReplacement
 *                   -XX:LoopMaxUnroll=2
 *                   -XX:CompileCommand=inline,compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling3::foo
 *                   compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling3
 */

/*
 * @test
 * @bug 8361608
 * @summary assert in do_unroll does not hold in some cases when peeling comes
 *          just before unrolling. It seems to happen only with stress peeling
 * @run main compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling3
 */
package compiler.loopopts;

public class TooStrictAssertForUnrollAfterStressPeeling3 {
    static int iArr[] = new int[400];
    static boolean flag;

    public static void main(String[] args) {
        for (int i = 1; i < 10000; i++) {
            test();
        }
    }

    // Lx: Optimized in loop opts round x.

    static int test() {
        int x = 5;
        for (int i = 1; i < 37; i++) { // L3: Peeled
            for (int a = 0; a < 2; a++) { // L2: Max unrolled
                for (int b = 0; b < 300; b++) {} // L1: Empty -> removed
            }
            int j = 1;
            x *= 12;
            while (++j < 5) { // L1: Max unrolled: peel + unroll
                iArr[0] += 2;
                if (iArr[0] > 0) {
                    // foo(): everything outside loop.
                    return foo(iArr, x);
                }
            }
        }
        return 3;
    }

    public static int foo(int[] a, int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) { // L2: Pre/main/post, L3: Unrolled -> hit assert!
            for (int j = 0; j < 34; j++) {} // L1: Empty -> removed
            if (flag) {
                // Ensure not directly unrolled in L2 but only in L3.
                return 3;
            }
            sum += a[i];
        }
        return sum;
    }
}
