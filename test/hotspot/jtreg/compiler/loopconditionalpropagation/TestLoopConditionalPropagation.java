/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

package compiler.loopconditionalpropagation;

import compiler.lib.ir_framework.*;

import java.util.Objects;

/*
 * @test
 * @bug 8275202
 * @summary C2: optimize out more redundant conditions
 * @library /test/lib /
 * @run driver compiler.loopconditionalpropagation.TestLoopConditionalPropagation
 */

public class TestLoopConditionalPropagation {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+LoopConditionalPropagationALot", "-XX:-LoopUnswitching", "-XX:-RangeCheckElimination", "-XX:+UseLoopPredicate");
        TestFramework.runWithFlags("-XX:+LoopConditionalPropagationALot", "-XX:-LoopUnswitching", "-XX:+RangeCheckElimination", "-XX:-UseLoopPredicate");
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    @Arguments(values={Argument.NUMBER_42})
    @Warmup(10_000)
    private void test1(int i) {
        if (i < 42) {
            throw new RuntimeException("never taken");
        }
        if (i < 10) {
            throw new RuntimeException("never taken");
        }
    }

    @Test
    @IR(counts = {IRNode.IF, "3"})
    @Arguments(values={Argument.NUMBER_42, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    @Warmup(10_000)
    private static void test2(int i, boolean flag) {
        if (flag) {
            if (i < 42) {
                throw new RuntimeException("never taken");
            }
        } else {
            if (i < 42) {
                throw new RuntimeException("never taken");
            }
        }
        if (i < 10) {
            throw new RuntimeException("never taken");
        }
    }


    @DontInline
    private static void notInlined() {

    }

    @Test
    @IR(counts = {IRNode.IF, "2"})
    @Arguments(values={Argument.NUMBER_42, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    @Warmup(10_000)
    private static void test3(int i, boolean flag) {
        if (flag) {
            if (i < 42) {
                throw new RuntimeException("never taken");
            }
        } else {
            i = 100;
        }
        notInlined();
        if (i < 10) {
            throw new RuntimeException("never taken");
        }
    }


    static volatile int volatileField;

    @Test
    @IR(counts = {IRNode.IF, "3"})
    @Arguments(values={Argument.NUMBER_42, Argument.NUMBER_42})
    @Warmup(10_000)
    private static void test4(int i, int k) {
        if (i < 42) {
            throw new RuntimeException("never taken");
        }
        for (int j = 1; j < 4; j *= 2) {
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            if (i < 10) {
                throw new RuntimeException("never taken");
            }
            if (k < 42) {
                throw new RuntimeException("never taken");
            }
            i = k;
        }
    }


    @Test
    @IR(counts = {IRNode.IF, "2"})
    @IR(failOn = {IRNode.ADD_I, IRNode.MUL_I})
    @Arguments(values={Argument.NUMBER_42})
    @Warmup(10_000)
    private static int test5(int i) {
        if (i < 42) {
            throw new RuntimeException("never taken");
        }
        notInlined();
        if (i > 42) {
            throw new RuntimeException("never taken");
        }
        return (i + 5) * 100;
    }


    @Test
    @IR(counts = {IRNode.IF, "3"})
    @Arguments(values={Argument.NUMBER_42, Argument.NUMBER_42, Argument.NUMBER_42})
    @Warmup(10_000)
    private static void test6(int i, int j, int k) {
        if (i < 42) {
            throw new RuntimeException("never taken");
        }
        if (j < i) {
            throw new RuntimeException("never taken");
        }
        if (k < j) {
            throw new RuntimeException("never taken");
        }
        if (k < 10) {
            throw new RuntimeException("never taken");
        }
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    @Arguments(values={Argument.NUMBER_42})
    @Warmup(10_000)
    private static void test7(int i) {
        if (i < 0 || i >= 43) {
            throw new RuntimeException("never taken");
        }
        if (i < -1) {
            throw new RuntimeException("never taken");
        }
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    @Arguments(values={Argument.NUMBER_42})
    @Warmup(10_000)
    private static void test8(int i) {
        if (i < 0 || i >= 43) {
            throw new RuntimeException("never taken");
        }
        if (i > 42) {
            throw new RuntimeException("never taken");
        }
    }


    @Test
    @IR(counts = {IRNode.IF, "1"})
    @Arguments(values={Argument.NUMBER_42})
    @Warmup(10_000)
    private static void test9(long i) {
        if (i < 42) {
            throw new RuntimeException("never taken");
        }
        if (i < 10) {
            throw new RuntimeException("never taken");
        }
    }


    @Test
    @IR(counts = {IRNode.IF, "1"})
    @Arguments(values={Argument.NUMBER_42})
    @Warmup(10_000)
    private static void test10(int i) {
        if (i - 1 <= 0) {
            throw new RuntimeException("never taken");
        }
        if (i == 0) {
            throw new RuntimeException("never taken");
        }
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    @Arguments(values={Argument.BOOLEAN_TOGGLE_FIRST_TRUE, Argument.NUMBER_42})
    @Warmup(10_000)
    private static void test11(boolean flag, int i) {
        if (i - 1 <= 0) {
            throw new RuntimeException("never taken");
        }
        if (flag) {
            if (i == 0) {
                throw new RuntimeException("never taken");
            }
        } else {
            if (i == 0) {
                throw new RuntimeException("never taken");
            }
        }
    }

    @Test
    @IR(counts = {IRNode.IF, "2"})
    @Arguments(values={Argument.NUMBER_42, Argument.NUMBER_42})
    @Warmup(10_000)
    private static void test12(int i, int j) {
        if (i < 42) {
            throw new RuntimeException("never taken");
        }
        // i >= 42
        if (i > j) {
            throw new RuntimeException("never taken");
        }
        // i <= j => j >= 42
        if (j < 10) {
            throw new RuntimeException("never taken");
        }
    }

    static volatile int barrier;

    static class C {
        float field;
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "2"})
    private static int test13(int[] array, int i, C c, boolean flag) {
        int dummy = array[0];
        int v = 0;
        int j = 1;

        for (; j < 2; j *= 2) ;

        test13Helper(j, c);

        if (flag) {
            if (array.length > 42) {
                if (i >= 0) {
                    if (i <= 42) {
                        float f = c.field;
                        v = array[i];
                    }
                }
            }
        } else {
            if (array.length > 42) {
                if (i >= 0) {
                    if (i <= 42) {
                        float f = c.field;
                        v = array[i];
                    }
                }
            }
        }

        return v;
    }

    @ForceInline
    private static void test13Helper(int j, C c) {
        if (j == 2) {
            float f = c.field;
        } else {
            barrier = 0x42;
        }
    }

    @Run(test = "test13")
    @Warmup(10_000)
    public static void test13Runner() {
        C c = new C();
        test13Helper(42, c);
        test13Helper(2, c);

        int[] array1 = new int[100];
        int[] array2 = new int[1];
        test13(array1, 0, c, true);
        test13(array1, 99, c, true);
        test13(array2, 0, c, true);
        test13(array1, 0, c, false);
        test13(array1, 99, c, false);
        test13(array2, 0, c, false);
    }

    @Test
    @IR(counts = {IRNode.IF, "4"})
    @Arguments(values={Argument.NUMBER_42, Argument.NUMBER_42, Argument.NUMBER_42})
    @Warmup(10_000)
    private static void test14(int i, int k, int l) {
        if (i < 42) {
            throw new RuntimeException("never taken");
        }
        for (int j = 1; j < 4; j *= 2) {
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            // i >= 42
            if (l < i) {
                throw new RuntimeException("never taken");
            }
            // l >= 42
            if (l < 10) {
                throw new RuntimeException("never taken");
            }
            if (k < 42) {
                throw new RuntimeException("never taken");
            }
            i = k;
        }
    }

    @Test
    @IR(counts = {IRNode.IF, "6"})
    @Arguments(values={Argument.NUMBER_42, Argument.NUMBER_42, Argument.NUMBER_42, Argument.RANDOM_EACH})
    @Warmup(10_000)
    private static void test15(int i, int k, int l, boolean flag) {
        if (i < 42) {
            throw new RuntimeException("never taken");
        }
        for (int j = 1; j < 4; j *= 2) {
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            volatileField = 42;
            // i >= 42
            if (flag) {
                if (l < i) {
                    throw new RuntimeException("never taken");
                }
            } else {
                if (l < i) {
                    throw new RuntimeException("never taken");
                }
            }
            // l >= 42
            if (l < 10) {
                throw new RuntimeException("never taken");
            }
            if (k < 42) {
                throw new RuntimeException("never taken");
            }
            i = k;
        }
    }

    @Test
    @IR(applyIf = {"UseLoopPredicate", "true"}, failOn = {IRNode.COUNTED_LOOP})
    @IR(applyIf = {"UseLoopPredicate", "false"}, counts = {IRNode.COUNTED_LOOP, "2"}, failOn = {IRNode.COUNTED_LOOP_MAIN})
    private static float test16(int start, int stop) {
        float[] array = new float[1000];
        if (start < 0) {

        }
        if (stop > 1000) {

        }
        float v = 0;
        for (int i = start; i < stop; i++) {
            v = array[i];
        }
        return v;
    }

    @Run(test = "test16")
    @Warmup(10_000)
    public static void test16Runner() {
        test16(0, 1000);
    }

    @Test
    @IR(failOn = {IRNode.COUNTED_LOOP})
    private static float test17(int start, int stop, boolean flag) {
        float[] array = new float[1000];
        float v = 0;
        if (start >= 0 && stop <=  1000) {
            for (int i = start; i < stop; i++) {
                if (flag) {
                    v = array[i];
                } else {
                    v = array[i];
                }
            }
        }
        return v;
    }

    @Run(test = "test17")
    @Warmup(10_000)
    public static void test17Runner() {
        test17(0, 1000, true);
        test17(0, 1000, false);
    }

    @Test
    @IR(failOn = {IRNode.COUNTED_LOOP, IRNode.LONG_COUNTED_LOOP}, counts = {IRNode.LOOP, "1"})
    private static float test18(long start, long stop) {
        if (start < 0) {
            throw new RuntimeException("never taken");
        }
        if (stop > 1000) {
            throw new RuntimeException("never taken");
        }
        float v = 0;
        for (long i = start; i < stop; i++) {
            Objects.checkIndex(i, 1000);
        }
        return v;
    }

    @Run(test = "test18")
    @Warmup(10_000)
    public static void test18Runner() {
        test18(0, 1000);
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "1"})
    private static int test19(int i) {
        int[] array = new int[10];
        if (i < 0) {
            throw new RuntimeException("never taken");
        }
        volatileField = 42;
        if (i >= 10) {
            throw new RuntimeException("never taken");
        }
        int v = 0;
        for (int j = 0; j < 100; j++) {
            v += array[i];
        }
        return v;
    }

    @Run(test = "test19")
    @Warmup(10_000)
    public static void test19Runner() {
        test19(0);
    }
}
