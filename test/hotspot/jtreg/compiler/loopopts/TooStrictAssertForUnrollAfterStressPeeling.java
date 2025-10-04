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
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+StressLoopPeeling
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling::test
 *                   -XX:-TieredCompilation
 *                   -Xbatch
 *                   -XX:PerMethodTrapLimit=0
 *                   compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling
 * @run driver compiler.loopopts.TooStrictAssertForUnrollAfterStressPeeling
 */
package compiler.loopopts;

public class TooStrictAssertForUnrollAfterStressPeeling {
    public static void main(String[] args) {
        for (int i = 1; i < 1000; i++) {
            test();
        }
    }

    static long test() {
        int s = 0;
        int iArr[] = new int[400];
        for (int i = 0; i < 70; i++) {}

        for (int i = 0; i < 36; i++) {
            for (int j = 0; j < 3; j++) {
                s += iArr[0] = 7;
                if (s != 0) {
                    return s + foo(iArr);
                }
            }
        }
        return 0;
    }

    public static long foo(int[] a) {
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i];
        }
        return sum;
    }
}
