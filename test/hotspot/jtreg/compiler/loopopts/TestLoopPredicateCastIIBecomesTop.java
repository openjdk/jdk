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

/*
 * @test
 * @bug 8308504
 * @summary CastII for assert predicate becomes top but control flow path is not proven dead
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:RepeatCompilation=100
 *                   -XX:+StressIGVN
 *                   -XX:CompileOnly=compiler.loopopts.TestLoopPredicateCastIIBecomesTop::test
 *                   compiler.loopopts.TestLoopPredicateCastIIBecomesTop
 */

package compiler.loopopts;

public class TestLoopPredicateCastIIBecomesTop {
    static int N;

    static void test() {
        int arr[] = new int[N];
        short s = 1;
        double d = 1.0;
        for (int i = 49; i > 8; i--) {
            for (int j = 7; j > i; j -= 3) {
                if (i == 8) {
                    s *= arr[1];
                }
                d = arr[j];
            }
        }
        Double.doubleToLongBits(d);
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 10_000; i++) {
            test();
        }
    }
}
