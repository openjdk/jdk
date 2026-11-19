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
 * @bug 8397248
 * @summary Preserve counted outer loops when nested loops share their header.
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.loopopts;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Platform;
import static jdk.test.lib.Asserts.assertEQ;

public class TestSharedLoopHeader {
    static boolean flag;
    static final int[] OUTPUT = new int[4];

    public static void main(String[] args) {
        TestFramework f = new TestFramework();
        if (Platform.isDebugBuild()) {
            f.addFlags("-XX:+VerifyLoopOptimizations", "-XX:+VerifyGraphEdges");
        }
        f.addScenarios(new Scenario(0),
                       new Scenario(1, "-XX:-UseLoopPredicate", "-XX:-UseProfiledLoopPredicate",
                                    "-XX:LoopStripMiningIter=0"));
        f.start();
    }

    @Test
    @IR(failOn = {IRNode.LOOP})
    public static int testOriginal() {
        int i = 12, j = 0, k = 0;
        do {
            for (j = 3; j > 1; j -= 2) {
                for (k = 1; k < 3; k++) {
                    if (flag) {}
                }
            }
        } while (++i < 210);
        return i + j + k;
    }

    @Test
    @IR(failOn = {IRNode.LOOP})
    public static int testForControl() {
        int i = 12, j = 0, k = 0;
        for (; i < 210; i++) {
            for (j = 3; j > 1; j -= 2) {
                for (k = 1; k < 3; k++) {
                    if (flag) {}
                }
            }
        }
        return i + j + k;
    }

    @Test
    @IR(failOn = {IRNode.LOOP})
    public static int testNegativeStride() {
        int i = 210, j = 0, k = 0;
        do {
            for (j = 3; j > 1; j -= 2) {
                for (k = 1; k < 3; k++) {
                    if (flag) {}
                }
            }
        } while ((i -= 2) > 12);
        return i + j + k;
    }

    @Test
    @IR(failOn = {IRNode.LOOP})
    public static long testLongCounter() {
        long i = 12;
        int j = 0, k = 0;
        do {
            for (j = 3; j > 1; j -= 2) {
                for (k = 1; k < 3; k++) {
                    if (flag) {}
                }
            }
        } while (++i < 210);
        return i + j + k;
    }

    @Test
    public static void testObservable(int[] out, int start, int limit) {
        int i = start, j = 0, k = 0, sum = 0;
        do {
            for (j = 3; j > 1; j -= 2) {
                for (k = 1; k < 3; k++) {
                    sum += i ^ k;
                    if (flag) {}
                }
            }
        } while (++i < limit);
        out[0] = i;
        out[1] = j;
        out[2] = k;
        out[3] = sum;
    }

    @Test
    public static long testOverflow(int start, int limit) {
        int i = start, j = 0, k = 0, count = 0;
        long sum = 0;
        do {
            for (j = 3; j > 1; j -= 2) {
                for (k = 1; k < 3; k++) {
                    if (flag) {}
                }
            }
            sum = sum * 31 + i;
            if (++count == 6) {
                break;
            }
        } while ((i += 2) < limit);
        return sum ^ ((long)i << 32) ^ count ^ j ^ k;
    }

    @Test
    public static int testOtherBackedges(int start, int limit) {
        int i = start, sum = 0;
        do {
            if ((i & 3) == 0) {
                sum += i;
                i += 2;
                continue;
            }
            sum -= i;
            i++;
        } while (i < limit);
        return sum + i;
    }

    @DontCompile
    static int expectedOtherBackedges(int start, int limit) {
        int i = start, sum = 0;
        while (true) {
            int old = i;
            boolean evenStep = (i % 4) == 0;
            i += evenStep ? 2 : 1;
            sum += evenStep ? old : -old;
            if (i >= limit) {
                return sum + i;
            }
        }
    }

    @DontCompile
    static long expectedOverflow(int start, int limit) {
        int i = start;
        long sum = 0;
        for (int count = 1; ; count++) {
            sum = 31 * sum + i;
            if (count == 6) {
                return sum ^ ((long)i << 32) ^ count ^ 1 ^ 3;
            }
            i += 2;
            if (i >= limit) {
                return sum ^ ((long)i << 32) ^ count ^ 1 ^ 3;
            }
        }
    }

    @Run(test = {"testOriginal", "testForControl", "testNegativeStride", "testLongCounter",
                 "testObservable", "testOverflow", "testOtherBackedges"})
    public static void run() {
        assertEQ(testOriginal(), 214);
        assertEQ(testForControl(), 214);
        assertEQ(testNegativeStride(), 16);
        assertEQ(testLongCounter(), 214L);
        int[][] cases = {{12, 210}, {3, 4}, {7, 7}, {9, 5}, {-7, 11},
                         {Integer.MAX_VALUE - 2, Integer.MAX_VALUE},
                         {Integer.MAX_VALUE, Integer.MIN_VALUE}};
        for (int[] c : cases) {
            int start = c[0], limit = c[1];
            long count = Math.max(1L, (long)limit - start);
            int sum = 0;
            for (int n = 0; n < count; n++) {
                int i = start + n;
                sum += (i ^ 1) + (i ^ 2);
            }
            testObservable(OUTPUT, start, limit);
            assertEQ(OUTPUT[0], start + (int)count);
            assertEQ(OUTPUT[1], 1);
            assertEQ(OUTPUT[2], 3);
            assertEQ(OUTPUT[3], sum);
        }
        for (int start : new int[] {0, 7, Integer.MAX_VALUE - 2, Integer.MIN_VALUE}) {
            for (int limit : new int[] {0, 12, Integer.MAX_VALUE}) {
                assertEQ(testOverflow(start, limit), expectedOverflow(start, limit));
            }
        }
        for (int start = 0; start <= 3; start++) {
            for (int limit = 1; limit <= 33; limit++) {
                assertEQ(testOtherBackedges(start, limit), expectedOtherBackedges(start, limit));
            }
        }
    }
}
