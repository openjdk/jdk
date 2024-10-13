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
 * @bug 8303737
 * @summary C2: cast nodes from PhiNode::Ideal() cause "Base pointers must match" assert failure
 * @requires vm.gc.Parallel
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:-BackgroundCompilation -XX:LoopMaxUnroll=2 -XX:+UseParallelGC -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *                   -XX:-UseLoopPredicate -XX:-UseProfiledLoopPredicate -XX:StressSeed=2953783466 TestAddPChainMismatchedBase
 * @run main/othervm -XX:-BackgroundCompilation -XX:LoopMaxUnroll=2 -XX:+UseParallelGC -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN
 *                   -XX:-UseLoopPredicate -XX:-UseProfiledLoopPredicate TestAddPChainMismatchedBase
 */

public class TestAddPChainMismatchedBase {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test();
            testHelper(null, true);
            testHelper2(1000);
        }
    }

    private static void test() {
        int l;
        for (l = 0; l < 5; l++) {
            for (int i = 0; i < 2; i++) {
            }
        }
        testHelper2(l);
    }

    private static void testHelper2(int l) {
        int[] array = new int[1000];
        if (l == 5) {
            l = 4;
        } else {
            l = 1000;
        }
        for (int k = 0; k < 2; k++) {
            int v = 0;
            int i = 0;
            for (; ; ) {
                synchronized (new Object()) {
                }
                array = testHelper(array, false);
                v += array[i];
                int j = i;
                i++;
                if (i >= l) {
                    break;
                }
                array[j] = v;
            }
        }
    }

    private static int[] testHelper(int[] array, boolean flag) {
        if (flag) {
            return new int[1000];
        }
        return array;
    }
}
