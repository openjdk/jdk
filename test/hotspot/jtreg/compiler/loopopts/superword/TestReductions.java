/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8340093
 * @summary Test vectorization of reduction loops.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestReductions xxxx
 */

package compiler.loopopts.superword;

import jdk.test.lib.Utils;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import static compiler.lib.generators.Generators.G;
import compiler.lib.generators.Generator;

/**
 * Note: there is a corresponding JMH benchmark:
 * test/micro/org/openjdk/bench/vm/compiler/VectorReduction2.java
 */
public class TestReductions {
    static int SIZE = 1024*8;
    private static final Random RANDOM = Utils.getRandomInstance();
    public static final Generator<Integer> GEN_I = G.ints();
    public static final Generator<Float>   GEN_F = G.floats();

    private static byte[] in1B   = fillRandom(new byte[SIZE]);
    private static byte[] in2B   = fillRandom(new byte[SIZE]);
    private static byte[] in3B   = fillRandom(new byte[SIZE]);
    //private static char[] in1C   = fillRandom(new char[SIZE]);
    //private static char[] in2C   = fillRandom(new char[SIZE]);
    //private static char[] in3C   = fillRandom(new char[SIZE]);
    //private static short[] in1S  = fillRandom(new short[SIZE]);
    //private static short[] in2S  = fillRandom(new short[SIZE]);
    //private static short[] in3S  = fillRandom(new short[SIZE]);

    private static int[] in1I    = fillRandom(new int[SIZE]);
    private static int[] in2I    = fillRandom(new int[SIZE]);
    private static int[] in3I    = fillRandom(new int[SIZE]);
    //private static long[] in1L   = fillRandom(new long[SIZE]);
    //private static long[] in2L   = fillRandom(new long[SIZE]);
    //private static long[] in3L   = fillRandom(new long[SIZE]);

    //private static float[] in1F  = fillRandom(new float[SIZE]);
    //private static float[] in2F  = fillRandom(new float[SIZE]);
    //private static float[] in3F  = fillRandom(new float[SIZE]);
    //private static double[] in1D = fillRandom(new doulbe[SIZE]);
    //private static double[] in2D = fillRandom(new doulbe[SIZE]);
    //private static double[] in3D = fillRandom(new doulbe[SIZE]);

    interface TestFunction {
        Object run();
    }

    // Map of test names to tests.
    Map<String,TestFunction> tests = new HashMap<String,TestFunction>();

    // Map of gold, the results from the first run (before compilation), one per tests entry.
    Map<String,Object> golds = new HashMap<String,Object>();

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestReductions.class);
        switch (args[0]) {
            case "xxxx" -> { framework.addFlags("-XX:-AlignVector"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
    }

    public TestReductions() {
        // Add all tests to list
        tests.put("test1", TestReductions::test1);
        tests.put("test2", TestReductions::test2);

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            Object gold = test.run();
            golds.put(name, gold);
        }
    }

    @Warmup(100)
    @Run(test = {"test1",
                 "test2"})
    public void runTests() {
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            // Recall gold value from before compilation
            Object gold = golds.get(name);
            // Compute new result
            Object result = test.run();
            // Compare gold and new result
            try {
                Verify.checkEQ(gold, result);
            } catch (VerifyException e) {
                throw new RuntimeException("Verify failed for " + name, e);
            }
        }
    }

    static byte[] fillRandom(byte[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)(int)GEN_I.next();
        }
        return a;
    }

    static int[] fillRandom(int[] a) {
        G.fill(GEN_I, a);
        return a;
    }

    @Test
    // @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
    //               IRNode.STORE_VECTOR, "> 0",
    //               ".*multiversion.*", "= 0"},
    //     phase = CompilePhase.PRINT_IDEAL,
    //     applyIfPlatform = {"64-bit", "true"},
    //     applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // // Should always vectorize, no speculative runtime check required.
    static byte test1() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = in1B[i];
            acc += val;
        }
        return acc;
    }

    @Test
    // @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
    //               IRNode.STORE_VECTOR, "> 0",
    //               ".*multiversion.*", "= 0"},
    //     phase = CompilePhase.PRINT_IDEAL,
    //     applyIfPlatform = {"64-bit", "true"},
    //     applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // // Should always vectorize, no speculative runtime check required.
    static byte test2() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = in1B[i];
            acc *= val;
        }
        return acc;
    }
}
