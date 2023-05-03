/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8306933
 * @summary C2: "assert(false) failed: infinite loop" failure
 * @run main/othervm -Xcomp -XX:CompileOnly=TestInfiniteLoopCompilationFailure::test -XX:-UseLoopPredicate -XX:-UseProfiledLoopPredicate
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:StressSeed=675320863 TestInfiniteLoopCompilationFailure
 * @run main/othervm -Xcomp -XX:CompileOnly=TestInfiniteLoopCompilationFailure::test -XX:-UseLoopPredicate -XX:-UseProfiledLoopPredicate
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN TestInfiniteLoopCompilationFailure
 */

public class TestInfiniteLoopCompilationFailure {
    public static void main(String[] args) {
        int[][] array = { new int[100] };
        test(false, 0, array);
    }

    private static void test(boolean flag, int i, int[][] array) {
        if (flag) {
            int[] array2;

            array2 = array[i];
            int j;
            for (j = 0; j < 10; j++) {
            }
            int k;
            if (j == 10) {
                k = i;
            } else {
                k = 0;
            }
            int v = array2[k];
            for (;;) {
                v += array[i][i];
            }
        }
    }

}
