/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8281429
 * @summary PhiNode::Value() is too conservative for tripcount of CountedLoop
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestCountedLoopPhiValue
 */

public class TestCountedLoopPhiValue {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:LoopUnrollLimit=0");
    }

    @Test
    @IR(counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(failOn = { IRNode.IF })
    public static float test1() {
        int i = 0;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i++;
        } while (i < 10);
        if (j < 10) {
            v *= 2;
        }
        return v;
    }

    @Check(test = "test1")
    public static void checkTest1(float res) {
        if (res != Math.pow(2.0, 11)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(failOn = { IRNode.IF })
    public static float test2() {
        int i = 0;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i += 2;
        } while (i < 10);
        if (j < 9) {
            v *= 2;
        }
        return v;
    }

    @Check(test = "test2")
    public static void checkTest2(float res) {
        if (res != Math.pow(2.0, 6)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(failOn = { IRNode.IF })
    public static float test3() {
        int i = 10;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i--;
        } while (i > 0);
        if (j > 0) {
            v *= 2;
        }
        return v;
    }

    @Check(test = "test3")
    public static void checkTest3(float res) {
        if (res != Math.pow(2.0, 11)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    @Test
    @IR(counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(failOn = { IRNode.IF })
    public static float test4() {
        int i = 10;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i -= 2;
        } while (i > 0);
        if (j > 1) {
            v *= 2;
        }
        return v;
    }

    @Check(test = "test4")
    public static void checkTest4(float res) {
        if (res != Math.pow(2.0, 6)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    final static int int_stride = Integer.MAX_VALUE / 5;
    final static int test5_limit = Integer.MAX_VALUE - int_stride;
    final static int test5_iterations = Integer.divideUnsigned(test5_limit - Integer.MIN_VALUE + (int_stride - 1), int_stride);
    final static int last_test5_iteration = Integer.MIN_VALUE + Integer.divideUnsigned(test5_limit - Integer.MIN_VALUE, int_stride) * int_stride;

    @Test
    @IR(counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(failOn = { IRNode.IF })
    public static float test5() {
        int i = Integer.MIN_VALUE;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i += int_stride;
        } while (i < test5_limit);
        if (j < last_test5_iteration + 1) {
            v *= 2;
        }
        return v;
    }

    @Check(test = "test5")
    public static void checkTest5(float res) {
        if (res != Math.pow(2.0, test5_iterations+1)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    final static int test6_limit = Integer.MIN_VALUE + int_stride;
    final static int test6_iterations = Integer.divideUnsigned(Integer.MAX_VALUE - test6_limit + (int_stride - 1), int_stride);
    final static int last_test6_iteration = Integer.MAX_VALUE - Integer.divideUnsigned(Integer.MAX_VALUE - test6_limit, int_stride) * int_stride;

    @Test
    @IR(counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(failOn = { IRNode.IF })
    public static float test6() {
        int i = Integer.MAX_VALUE;
        int j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i -= int_stride;
        } while (i > test6_limit);
        if (j > last_test6_iteration - 1) {
            v *= 2;
        }
        return v;
    }

    @Check(test = "test6")
    public static void checkTest6(float res) {
        if (res != Math.pow(2.0, test6_iterations+1)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    final static long long_stride = Long.MAX_VALUE / 5;
    final static long test7_limit = Long.MAX_VALUE - long_stride;
    final static long test7_iterations = Long.divideUnsigned(test7_limit - Long.MIN_VALUE + (long_stride - 1), long_stride);
    final static long last_test7_iteration = Long.MIN_VALUE + Long.divideUnsigned(test7_limit - Long.MIN_VALUE, long_stride) * long_stride;

    @Test
    @IR(counts = { IRNode.LONGCOUNTEDLOOP, "1", IRNode.IF, "1" })
    public static float test7() {
        long i = Long.MIN_VALUE;
        long j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i += long_stride;
        } while (i < test7_limit);
        if (j < last_test7_iteration + 1) {
            v *= 2;
        }
        return v;
    }

    @Check(test = "test7")
    public static void checkTest7(float res) {
        if (res != Math.pow(2.0, test7_iterations+1)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }

    final static long test8_limit = Long.MIN_VALUE + long_stride;
    final static long test8_iterations = Long.divideUnsigned(Long.MAX_VALUE - test8_limit + (long_stride - 1), long_stride);
    final static long last_test8_iteration = Long.MAX_VALUE - Long.divideUnsigned(Long.MAX_VALUE - test8_limit, long_stride) * long_stride;

    @Test
    @IR(counts = { IRNode.LONGCOUNTEDLOOP, "1", IRNode.IF, "1" })
    public static float test8() {
        long i = Long.MAX_VALUE;
        long j;
        float v = 1;
        do {
            v *= 2;
            j = i;
            i -= long_stride;
        } while (i > test8_limit);
        if (j > last_test8_iteration - 1) {
            v *= 2;
        }
        return v;
    }

    @Check(test = "test8")
    public static void checkTest8(float res) {
        if (res != Math.pow(2.0, test8_iterations+1)) {
            throw new RuntimeException("incorrect result: " + res);
        }
    }
}
