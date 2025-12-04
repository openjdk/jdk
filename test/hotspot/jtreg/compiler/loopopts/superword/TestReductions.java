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
 * @test id=no-vectorization
 * @bug 8340093
 * @summary Test vectorization of reduction loops.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestReductions P0
 */

/*
 * @test id=vanilla
 * @bug 8340093
 * @summary Test vectorization of reduction loops.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestReductions P1
 */

/*
 * @test id=force-vectorization
 * @bug 8340093
 * @summary Test vectorization of reduction loops.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestReductions P2
 */

package compiler.loopopts.superword;

import java.util.Map;
import java.util.HashMap;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import static compiler.lib.generators.Generators.G;
import compiler.lib.generators.Generator;

/**
 * Note: there is a corresponding JMH benchmark:
 * test/micro/org/openjdk/bench/vm/compiler/VectorReduction2.java
 */
public class TestReductions {
    private static int SIZE = 1024*8;
    private static final Generator<Integer> GEN_I = G.ints();
    private static final Generator<Long>    GEN_L = G.longs();
    private static final Generator<Float>   GEN_F = G.floats();
    private static final Generator<Double>  GEN_D = G.doubles();

    private static byte[] in1B   = fillRandom(new byte[SIZE]);
    private static byte[] in2B   = fillRandom(new byte[SIZE]);
    private static byte[] in3B   = fillRandom(new byte[SIZE]);
    private static char[] in1C   = fillRandom(new char[SIZE]);
    private static char[] in2C   = fillRandom(new char[SIZE]);
    private static char[] in3C   = fillRandom(new char[SIZE]);
    private static short[] in1S  = fillRandom(new short[SIZE]);
    private static short[] in2S  = fillRandom(new short[SIZE]);
    private static short[] in3S  = fillRandom(new short[SIZE]);

    private static int[] in1I    = fillRandom(new int[SIZE]);
    private static int[] in2I    = fillRandom(new int[SIZE]);
    private static int[] in3I    = fillRandom(new int[SIZE]);
    private static long[] in1L   = fillRandom(new long[SIZE]);
    private static long[] in2L   = fillRandom(new long[SIZE]);
    private static long[] in3L   = fillRandom(new long[SIZE]);

    private static float[] in1F  = fillRandom(new float[SIZE]);
    private static float[] in2F  = fillRandom(new float[SIZE]);
    private static float[] in3F  = fillRandom(new float[SIZE]);
    private static double[] in1D = fillRandom(new double[SIZE]);
    private static double[] in2D = fillRandom(new double[SIZE]);
    private static double[] in3D = fillRandom(new double[SIZE]);

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
            case "P0" -> { framework.addFlags("-XX:+UnlockDiagnosticVMOptions", "-XX:AutoVectorizationOverrideProfitability=0"); }
            case "P1" -> { framework.addFlags("-XX:+UnlockDiagnosticVMOptions", "-XX:AutoVectorizationOverrideProfitability=1"); }
            // Note: increasing the node count limit also helps in some cases.
            case "P2" -> { framework.addFlags("-XX:+UnlockDiagnosticVMOptions", "-XX:AutoVectorizationOverrideProfitability=2", "-XX:LoopUnrollLimit=1000"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
    }

    public TestReductions() {
        // Add all tests to list
        tests.put("byteAndSimple",       TestReductions::byteAndSimple);
        tests.put("byteOrSimple",        TestReductions::byteOrSimple);
        tests.put("byteXorSimple",       TestReductions::byteXorSimple);
        tests.put("byteAddSimple",       TestReductions::byteAddSimple);
        tests.put("byteMulSimple",       TestReductions::byteMulSimple);
        tests.put("byteMinSimple",       TestReductions::byteMinSimple);
        tests.put("byteMaxSimple",       TestReductions::byteMaxSimple);
        tests.put("byteAndDotProduct",   TestReductions::byteAndDotProduct);
        tests.put("byteOrDotProduct",    TestReductions::byteOrDotProduct);
        tests.put("byteXorDotProduct",   TestReductions::byteXorDotProduct);
        tests.put("byteAddDotProduct",   TestReductions::byteAddDotProduct);
        tests.put("byteMulDotProduct",   TestReductions::byteMulDotProduct);
        tests.put("byteMinDotProduct",   TestReductions::byteMinDotProduct);
        tests.put("byteMaxDotProduct",   TestReductions::byteMaxDotProduct);
        tests.put("byteAndBig",          TestReductions::byteAndBig);
        tests.put("byteOrBig",           TestReductions::byteOrBig);
        tests.put("byteXorBig",          TestReductions::byteXorBig);
        tests.put("byteAddBig",          TestReductions::byteAddBig);
        tests.put("byteMulBig",          TestReductions::byteMulBig);
        tests.put("byteMinBig",          TestReductions::byteMinBig);
        tests.put("byteMaxBig",          TestReductions::byteMaxBig);

        tests.put("charAndSimple",       TestReductions::charAndSimple);
        tests.put("charOrSimple",        TestReductions::charOrSimple);
        tests.put("charXorSimple",       TestReductions::charXorSimple);
        tests.put("charAddSimple",       TestReductions::charAddSimple);
        tests.put("charMulSimple",       TestReductions::charMulSimple);
        tests.put("charMinSimple",       TestReductions::charMinSimple);
        tests.put("charMaxSimple",       TestReductions::charMaxSimple);
        tests.put("charAndDotProduct",   TestReductions::charAndDotProduct);
        tests.put("charOrDotProduct",    TestReductions::charOrDotProduct);
        tests.put("charXorDotProduct",   TestReductions::charXorDotProduct);
        tests.put("charAddDotProduct",   TestReductions::charAddDotProduct);
        tests.put("charMulDotProduct",   TestReductions::charMulDotProduct);
        tests.put("charMinDotProduct",   TestReductions::charMinDotProduct);
        tests.put("charMaxDotProduct",   TestReductions::charMaxDotProduct);
        tests.put("charAndBig",          TestReductions::charAndBig);
        tests.put("charOrBig",           TestReductions::charOrBig);
        tests.put("charXorBig",          TestReductions::charXorBig);
        tests.put("charAddBig",          TestReductions::charAddBig);
        tests.put("charMulBig",          TestReductions::charMulBig);
        tests.put("charMinBig",          TestReductions::charMinBig);
        tests.put("charMaxBig",          TestReductions::charMaxBig);

        tests.put("shortAndSimple",      TestReductions::shortAndSimple);
        tests.put("shortOrSimple",       TestReductions::shortOrSimple);
        tests.put("shortXorSimple",      TestReductions::shortXorSimple);
        tests.put("shortAddSimple",      TestReductions::shortAddSimple);
        tests.put("shortMulSimple",      TestReductions::shortMulSimple);
        tests.put("shortMinSimple",      TestReductions::shortMinSimple);
        tests.put("shortMaxSimple",      TestReductions::shortMaxSimple);
        tests.put("shortAndDotProduct",  TestReductions::shortAndDotProduct);
        tests.put("shortOrDotProduct",   TestReductions::shortOrDotProduct);
        tests.put("shortXorDotProduct",  TestReductions::shortXorDotProduct);
        tests.put("shortAddDotProduct",  TestReductions::shortAddDotProduct);
        tests.put("shortMulDotProduct",  TestReductions::shortMulDotProduct);
        tests.put("shortMinDotProduct",  TestReductions::shortMinDotProduct);
        tests.put("shortMaxDotProduct",  TestReductions::shortMaxDotProduct);
        tests.put("shortAndBig",         TestReductions::shortAndBig);
        tests.put("shortOrBig",          TestReductions::shortOrBig);
        tests.put("shortXorBig",         TestReductions::shortXorBig);
        tests.put("shortAddBig",         TestReductions::shortAddBig);
        tests.put("shortMulBig",         TestReductions::shortMulBig);
        tests.put("shortMinBig",         TestReductions::shortMinBig);
        tests.put("shortMaxBig",         TestReductions::shortMaxBig);

        tests.put("intAndSimple",        TestReductions::intAndSimple);
        tests.put("intOrSimple",         TestReductions::intOrSimple);
        tests.put("intXorSimple",        TestReductions::intXorSimple);
        tests.put("intAddSimple",        TestReductions::intAddSimple);
        tests.put("intMulSimple",        TestReductions::intMulSimple);
        tests.put("intMinSimple",        TestReductions::intMinSimple);
        tests.put("intMaxSimple",        TestReductions::intMaxSimple);
        tests.put("intAndDotProduct",    TestReductions::intAndDotProduct);
        tests.put("intOrDotProduct",     TestReductions::intOrDotProduct);
        tests.put("intXorDotProduct",    TestReductions::intXorDotProduct);
        tests.put("intAddDotProduct",    TestReductions::intAddDotProduct);
        tests.put("intMulDotProduct",    TestReductions::intMulDotProduct);
        tests.put("intMinDotProduct",    TestReductions::intMinDotProduct);
        tests.put("intMaxDotProduct",    TestReductions::intMaxDotProduct);
        tests.put("intAndBig",           TestReductions::intAndBig);
        tests.put("intOrBig",            TestReductions::intOrBig);
        tests.put("intXorBig",           TestReductions::intXorBig);
        tests.put("intAddBig",           TestReductions::intAddBig);
        tests.put("intMulBig",           TestReductions::intMulBig);
        tests.put("intMinBig",           TestReductions::intMinBig);
        tests.put("intMaxBig",           TestReductions::intMaxBig);

        tests.put("longAndSimple",       TestReductions::longAndSimple);
        tests.put("longOrSimple",        TestReductions::longOrSimple);
        tests.put("longXorSimple",       TestReductions::longXorSimple);
        tests.put("longAddSimple",       TestReductions::longAddSimple);
        tests.put("longMulSimple",       TestReductions::longMulSimple);
        tests.put("longMinSimple",       TestReductions::longMinSimple);
        tests.put("longMaxSimple",       TestReductions::longMaxSimple);
        tests.put("longAndDotProduct",   TestReductions::longAndDotProduct);
        tests.put("longOrDotProduct",    TestReductions::longOrDotProduct);
        tests.put("longXorDotProduct",   TestReductions::longXorDotProduct);
        tests.put("longAddDotProduct",   TestReductions::longAddDotProduct);
        tests.put("longMulDotProduct",   TestReductions::longMulDotProduct);
        tests.put("longMinDotProduct",   TestReductions::longMinDotProduct);
        tests.put("longMaxDotProduct",   TestReductions::longMaxDotProduct);
        tests.put("longAndBig",          TestReductions::longAndBig);
        tests.put("longOrBig",           TestReductions::longOrBig);
        tests.put("longXorBig",          TestReductions::longXorBig);
        tests.put("longAddBig",          TestReductions::longAddBig);
        tests.put("longMulBig",          TestReductions::longMulBig);
        tests.put("longMinBig",          TestReductions::longMinBig);
        tests.put("longMaxBig",          TestReductions::longMaxBig);

        tests.put("floatAddSimple",      TestReductions::floatAddSimple);
        tests.put("floatMulSimple",      TestReductions::floatMulSimple);
        tests.put("floatMinSimple",      TestReductions::floatMinSimple);
        tests.put("floatMaxSimple",      TestReductions::floatMaxSimple);
        tests.put("floatAddDotProduct",  TestReductions::floatAddDotProduct);
        tests.put("floatMulDotProduct",  TestReductions::floatMulDotProduct);
        tests.put("floatMinDotProduct",  TestReductions::floatMinDotProduct);
        tests.put("floatMaxDotProduct",  TestReductions::floatMaxDotProduct);
        tests.put("floatAddBig",         TestReductions::floatAddBig);
        tests.put("floatMulBig",         TestReductions::floatMulBig);
        tests.put("floatMinBig",         TestReductions::floatMinBig);
        tests.put("floatMaxBig",         TestReductions::floatMaxBig);

        tests.put("doubleAddSimple",     TestReductions::doubleAddSimple);
        tests.put("doubleMulSimple",     TestReductions::doubleMulSimple);
        tests.put("doubleMinSimple",     TestReductions::doubleMinSimple);
        tests.put("doubleMaxSimple",     TestReductions::doubleMaxSimple);
        tests.put("doubleAddDotProduct", TestReductions::doubleAddDotProduct);
        tests.put("doubleMulDotProduct", TestReductions::doubleMulDotProduct);
        tests.put("doubleMinDotProduct", TestReductions::doubleMinDotProduct);
        tests.put("doubleMaxDotProduct", TestReductions::doubleMaxDotProduct);
        tests.put("doubleAddBig",        TestReductions::doubleAddBig);
        tests.put("doubleMulBig",        TestReductions::doubleMulBig);
        tests.put("doubleMinBig",        TestReductions::doubleMinBig);
        tests.put("doubleMaxBig",        TestReductions::doubleMaxBig);

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            Object gold = test.run();
            golds.put(name, gold);
        }
    }

    @Warmup(100)
    @Run(test = {"byteAndSimple",
                 "byteOrSimple",
                 "byteXorSimple",
                 "byteAddSimple",
                 "byteMulSimple",
                 "byteMinSimple",
                 "byteMaxSimple",
                 "byteAndDotProduct",
                 "byteOrDotProduct",
                 "byteXorDotProduct",
                 "byteAddDotProduct",
                 "byteMulDotProduct",
                 "byteMinDotProduct",
                 "byteMaxDotProduct",
                 "byteAndBig",
                 "byteOrBig",
                 "byteXorBig",
                 "byteAddBig",
                 "byteMulBig",
                 "byteMinBig",
                 "byteMaxBig",

                 "charAndSimple",
                 "charOrSimple",
                 "charXorSimple",
                 "charAddSimple",
                 "charMulSimple",
                 "charMinSimple",
                 "charMaxSimple",
                 "charAndDotProduct",
                 "charOrDotProduct",
                 "charXorDotProduct",
                 "charAddDotProduct",
                 "charMulDotProduct",
                 "charMinDotProduct",
                 "charMaxDotProduct",
                 "charAndBig",
                 "charOrBig",
                 "charXorBig",
                 "charAddBig",
                 "charMulBig",
                 "charMinBig",
                 "charMaxBig",

                 "shortAndSimple",
                 "shortOrSimple",
                 "shortXorSimple",
                 "shortAddSimple",
                 "shortMulSimple",
                 "shortMinSimple",
                 "shortMaxSimple",
                 "shortAndDotProduct",
                 "shortOrDotProduct",
                 "shortXorDotProduct",
                 "shortAddDotProduct",
                 "shortMulDotProduct",
                 "shortMinDotProduct",
                 "shortMaxDotProduct",
                 "shortAndBig",
                 "shortOrBig",
                 "shortXorBig",
                 "shortAddBig",
                 "shortMulBig",
                 "shortMinBig",
                 "shortMaxBig",

                 "intAndSimple",
                 "intOrSimple",
                 "intXorSimple",
                 "intAddSimple",
                 "intMulSimple",
                 "intMinSimple",
                 "intMaxSimple",
                 "intAndDotProduct",
                 "intOrDotProduct",
                 "intXorDotProduct",
                 "intAddDotProduct",
                 "intMulDotProduct",
                 "intMinDotProduct",
                 "intMaxDotProduct",
                 "intAndBig",
                 "intOrBig",
                 "intXorBig",
                 "intAddBig",
                 "intMulBig",
                 "intMinBig",
                 "intMaxBig",

                 "longAndSimple",
                 "longOrSimple",
                 "longXorSimple",
                 "longAddSimple",
                 "longMulSimple",
                 "longMinSimple",
                 "longMaxSimple",
                 "longAndDotProduct",
                 "longOrDotProduct",
                 "longXorDotProduct",
                 "longAddDotProduct",
                 "longMulDotProduct",
                 "longMinDotProduct",
                 "longMaxDotProduct",
                 "longAndBig",
                 "longOrBig",
                 "longXorBig",
                 "longAddBig",
                 "longMulBig",
                 "longMinBig",
                 "longMaxBig",

                 "floatAddSimple",
                 "floatMulSimple",
                 "floatMinSimple",
                 "floatMaxSimple",
                 "floatAddDotProduct",
                 "floatMulDotProduct",
                 "floatMinDotProduct",
                 "floatMaxDotProduct",
                 "floatAddBig",
                 "floatMulBig",
                 "floatMinBig",
                 "floatMaxBig",

                 "doubleAddSimple",
                 "doubleMulSimple",
                 "doubleMinSimple",
                 "doubleMaxSimple",
                 "doubleAddDotProduct",
                 "doubleMulDotProduct",
                 "doubleMinDotProduct",
                 "doubleMaxDotProduct",
                 "doubleAddBig",
                 "doubleMulBig",
                 "doubleMinBig",
                 "doubleMaxBig"})
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

    static char[] fillRandom(char[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (char)(int)GEN_I.next();
        }
        return a;
    }

    static short[] fillRandom(short[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (short)(int)GEN_I.next();
        }
        return a;
    }

    static int[] fillRandom(int[] a) {
        G.fill(GEN_I, a);
        return a;
    }

    static long[] fillRandom(long[] a) {
        G.fill(GEN_L, a);
        return a;
    }

    static float[] fillRandom(float[] a) {
        G.fill(GEN_F, a);
        return a;
    }

    static double[] fillRandom(double[] a) {
        G.fill(GEN_D, a);
        return a;
    }

    // ---------byte***Simple ------------------------------------------------------------
    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteAndSimple() {
        byte acc = (byte)0xFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = in1B[i];
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteOrSimple() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = in1B[i];
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteXorSimple() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = in1B[i];
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteAddSimple() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = in1B[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteMulSimple() {
        byte acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = in1B[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteMinSimple() {
        byte acc = Byte.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = in1B[i];
            acc = (byte)Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteMaxSimple() {
        byte acc = Byte.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = in1B[i];
            acc = (byte)Math.max(acc, val);
        }
        return acc;
    }

    // ---------byte***DotProduct ------------------------------------------------------------
    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteAndDotProduct() {
        byte acc = (byte)0xFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)(in1B[i] * in2B[i]);
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteOrDotProduct() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)(in1B[i] * in2B[i]);
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteXorDotProduct() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)(in1B[i] * in2B[i]);
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteAddDotProduct() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)(in1B[i] * in2B[i]);
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteMulDotProduct() {
        byte acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)(in1B[i] * in2B[i]);
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteMinDotProduct() {
        byte acc = Byte.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)(in1B[i] * in2B[i]);
            acc = (byte)Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteMaxDotProduct() {
        byte acc = Byte.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)(in1B[i] * in2B[i]);
            acc = (byte)Math.max(acc, val);
        }
        return acc;
    }

    // ---------byte***Big ------------------------------------------------------------
    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteAndBig() {
        byte acc = (byte)0xFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)((in1B[i] * in2B[i]) + (in1B[i] * in3B[i]) + (in2B[i] * in3B[i]));
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteOrBig() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)((in1B[i] * in2B[i]) + (in1B[i] * in3B[i]) + (in2B[i] * in3B[i]));
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteXorBig() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)((in1B[i] * in2B[i]) + (in1B[i] * in3B[i]) + (in2B[i] * in3B[i]));
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteAddBig() {
        byte acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)((in1B[i] * in2B[i]) + (in1B[i] * in3B[i]) + (in2B[i] * in3B[i]));
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteMulBig() {
        byte acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)((in1B[i] * in2B[i]) + (in1B[i] * in3B[i]) + (in2B[i] * in3B[i]));
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteMinBig() {
        byte acc = Byte.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)((in1B[i] * in2B[i]) + (in1B[i] * in3B[i]) + (in2B[i] * in3B[i]));
            acc = (byte)Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_B) // does not vectorize for now, might in the future.
    private static byte byteMaxBig() {
        byte acc = Byte.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            byte val = (byte)((in1B[i] * in2B[i]) + (in1B[i] * in3B[i]) + (in2B[i] * in3B[i]));
            acc = (byte)Math.max(acc, val);
        }
        return acc;
    }

    // ---------char***Simple ------------------------------------------------------------
    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charAndSimple() {
        char acc = (char)0xFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = in1C[i];
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charOrSimple() {
        char acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = in1C[i];
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charXorSimple() {
        char acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = in1C[i];
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charAddSimple() {
        char acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = in1C[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charMulSimple() {
        char acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = in1C[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charMinSimple() {
        char acc = Character.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = in1C[i];
            acc = (char)Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charMaxSimple() {
        char acc = Character.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = in1C[i];
            acc = (char)Math.max(acc, val);
        }
        return acc;
    }

    // ---------char***DotProduct ------------------------------------------------------------
    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charAndDotProduct() {
        char acc = (char)0xFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)(in1C[i] * in2C[i]);
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charOrDotProduct() {
        char acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)(in1C[i] * in2C[i]);
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charXorDotProduct() {
        char acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)(in1C[i] * in2C[i]);
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charAddDotProduct() {
        char acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)(in1C[i] * in2C[i]);
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charMulDotProduct() {
        char acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)(in1C[i] * in2C[i]);
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charMinDotProduct() {
        char acc = Character.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)(in1C[i] * in2C[i]);
            acc = (char)Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charMaxDotProduct() {
        char acc = Character.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)(in1C[i] * in2C[i]);
            acc = (char)Math.max(acc, val);
        }
        return acc;
    }

    // ---------char***Big ------------------------------------------------------------
    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charAndBig() {
        char acc = (char)0xFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)((in1C[i] * in2C[i]) + (in1C[i] * in3C[i]) + (in2C[i] * in3C[i]));
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charOrBig() {
        char acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)((in1C[i] * in2C[i]) + (in1C[i] * in3C[i]) + (in2C[i] * in3C[i]));
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charXorBig() {
        char acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)((in1C[i] * in2C[i]) + (in1C[i] * in3C[i]) + (in2C[i] * in3C[i]));
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charAddBig() {
        char acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)((in1C[i] * in2C[i]) + (in1C[i] * in3C[i]) + (in2C[i] * in3C[i]));
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charMulBig() {
        char acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)((in1C[i] * in2C[i]) + (in1C[i] * in3C[i]) + (in2C[i] * in3C[i]));
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charMinBig() {
        char acc = Character.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)((in1C[i] * in2C[i]) + (in1C[i] * in3C[i]) + (in2C[i] * in3C[i]));
            acc = (char)Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_C) // does not vectorize for now, might in the future.
    private static char charMaxBig() {
        char acc = Character.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            char val = (char)((in1C[i] * in2C[i]) + (in1C[i] * in3C[i]) + (in2C[i] * in3C[i]));
            acc = (char)Math.max(acc, val);
        }
        return acc;
    }

    // ---------short***Simple ------------------------------------------------------------
    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortAndSimple() {
        short acc = (short)0xFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = in1S[i];
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortOrSimple() {
        short acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = in1S[i];
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortXorSimple() {
        short acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = in1S[i];
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortAddSimple() {
        short acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = in1S[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortMulSimple() {
        short acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = in1S[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortMinSimple() {
        short acc = Short.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = in1S[i];
            acc = (short)Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortMaxSimple() {
        short acc = Short.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = in1S[i];
            acc = (short)Math.max(acc, val);
        }
        return acc;
    }

    // ---------short***DotProduct ------------------------------------------------------------
    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortAndDotProduct() {
        short acc = (short)0xFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)(in1S[i] * in2S[i]);
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortOrDotProduct() {
        short acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)(in1S[i] * in2S[i]);
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortXorDotProduct() {
        short acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)(in1S[i] * in2S[i]);
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortAddDotProduct() {
        short acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)(in1S[i] * in2S[i]);
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortMulDotProduct() {
        short acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)(in1S[i] * in2S[i]);
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortMinDotProduct() {
        short acc = Short.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)(in1S[i] * in2S[i]);
            acc = (short)Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortMaxDotProduct() {
        short acc = Short.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)(in1S[i] * in2S[i]);
            acc = (short)Math.max(acc, val);
        }
        return acc;
    }

    // ---------short***Big ------------------------------------------------------------
    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortAndBig() {
        short acc = (short)0xFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)((in1S[i] * in2S[i]) + (in1S[i] * in3S[i]) + (in2S[i] * in3S[i]));
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortOrBig() {
        short acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)((in1S[i] * in2S[i]) + (in1S[i] * in3S[i]) + (in2S[i] * in3S[i]));
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortXorBig() {
        short acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)((in1S[i] * in2S[i]) + (in1S[i] * in3S[i]) + (in2S[i] * in3S[i]));
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortAddBig() {
        short acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)((in1S[i] * in2S[i]) + (in1S[i] * in3S[i]) + (in2S[i] * in3S[i]));
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortMulBig() {
        short acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)((in1S[i] * in2S[i]) + (in1S[i] * in3S[i]) + (in2S[i] * in3S[i]));
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortMinBig() {
        short acc = Short.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)((in1S[i] * in2S[i]) + (in1S[i] * in3S[i]) + (in2S[i] * in3S[i]));
            acc = (short)Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(failOn = IRNode.LOAD_VECTOR_S) // does not vectorize for now, might in the future.
    private static short shortMaxBig() {
        short acc = Short.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            short val = (short)((in1S[i] * in2S[i]) + (in1S[i] * in3S[i]) + (in2S[i] * in3S[i]));
            acc = (short)Math.max(acc, val);
        }
        return acc;
    }

    // ---------int***Simple ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.AND_REDUCTION_V, "> 0",
                  IRNode.AND_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intAndSimple() {
        int acc = 0xFFFFFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,  "> 0",
                  IRNode.OR_REDUCTION_V, "> 0",
                  IRNode.OR_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intOrSimple() {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.XOR_REDUCTION_V, "> 0",
                  IRNode.XOR_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intXorSimple() {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.ADD_REDUCTION_VI, "> 0",
                  IRNode.ADD_VI,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intAddSimple() {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.MUL_REDUCTION_VI, "> 0",
                  IRNode.MUL_VI,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intMulSimple() {
        int acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intMinSimple() {
        int acc = Integer.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intMaxSimple() {
        int acc = Integer.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------int***DotProduct ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.AND_REDUCTION_V, "> 0",
                  IRNode.AND_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intAndDotProduct() {
        int acc = 0xFFFFFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,  "> 0",
                  IRNode.OR_REDUCTION_V, "> 0",
                  IRNode.OR_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intOrDotProduct() {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.XOR_REDUCTION_V, "> 0",
                  IRNode.XOR_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intXorDotProduct() {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.ADD_REDUCTION_VI, "> 0",
                  IRNode.ADD_VI,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intAddDotProduct() {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.MUL_REDUCTION_VI, "> 0",
                  IRNode.MUL_VI,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intMulDotProduct() {
        int acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intMinDotProduct() {
        int acc = Integer.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intMaxDotProduct() {
        int acc = Integer.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------int***Big ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.AND_REDUCTION_V, "> 0",
                  IRNode.AND_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intAndBig() {
        int acc = 0xFFFFFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,  "> 0",
                  IRNode.OR_REDUCTION_V, "> 0",
                  IRNode.OR_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intOrBig() {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.XOR_REDUCTION_V, "> 0",
                  IRNode.XOR_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intXorBig() {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.ADD_REDUCTION_VI, "> 0",
                  IRNode.ADD_VI,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intAddBig() {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.MUL_REDUCTION_VI, "> 0",
                  IRNode.MUL_VI,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intMulBig() {
        int acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intMinBig() {
        int acc = Integer.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VI,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_I,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static int intMaxBig() {
        int acc = Integer.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------long***Simple ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.AND_REDUCTION_V, "> 0",
                  IRNode.AND_VL,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longAndSimple() {
        long acc = 0xFFFFFFFFFFFFFFFFL; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,  "> 0",
                  IRNode.OR_REDUCTION_V, "> 0",
                  IRNode.OR_VL,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longOrSimple() {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.XOR_REDUCTION_V, "> 0",
                  IRNode.XOR_VL,          "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longXorSimple() {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,    "> 0",
                  IRNode.ADD_REDUCTION_VL, "> 0",
                  IRNode.ADD_VL,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longAddSimple() {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,    "> 0",
                  IRNode.MUL_REDUCTION_VL, "> 0",
                  IRNode.MUL_VL,           "> 0"}, // vector accumulator
        applyIfCPUFeature = {"avx512dq", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"avx512dq", "false", "sse4.1", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370673
    @IR(counts = {IRNode.LOAD_VECTOR_L,    "> 0",
                  IRNode.MUL_REDUCTION_VL, "> 0",
                  IRNode.MUL_VL,           "= 0"}, // Reduction NOT moved out of loop
        applyIfCPUFeatureOr = {"asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    // Note: NEON does not support MulVL for auto vectorization. There is
    //       a scalarized implementation, but that is not profitable for
    //       auto vectorization in almost all cases, and would not be
    //       profitable here at any rate.
    //       Hence, we have to keep the reduction inside the loop, and
    //       cannot use the MulVL as the vector accumulator.
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longMulSimple() {
        long acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VL,          "> 0"},
        applyIfCPUFeatureOr = {"avx512", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"avx512", "false", "avx2", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370671
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longMinSimple() {
        long acc = Long.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VL,          "> 0"},
        applyIfCPUFeatureOr = {"avx512", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"avx512", "false", "avx2", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370671
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longMaxSimple() {
        long acc = Long.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------long***DotProduct ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.AND_REDUCTION_V, "> 0",
                  IRNode.AND_VL,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While AndReductionV is implemented in NEON (see longAndSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longAndDotProduct() {
        long acc = 0xFFFFFFFFFFFFFFFFL; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,  "> 0",
                  IRNode.OR_REDUCTION_V, "> 0",
                  IRNode.OR_VL,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While OrReductionV is implemented in NEON (see longOrSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longOrDotProduct() {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.XOR_REDUCTION_V, "> 0",
                  IRNode.XOR_VL,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While MaxReductionV is implemented in NEON (see longXorSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longXorDotProduct() {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,    "> 0",
                  IRNode.ADD_REDUCTION_VL, "> 0",
                  IRNode.ADD_VL,           "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While MaxReductionV is implemented in NEON (see longAddSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longAddDotProduct() {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,    "> 0",
                  IRNode.MUL_REDUCTION_VL, "> 0",
                  IRNode.MUL_VL,           "> 0"},
        applyIfCPUFeature = {"avx512dq", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"avx512dq", "false", "sse4.1", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370673
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // MulVL is not implemented on NEON, so we also not have the reduction.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longMulDotProduct() {
        long acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VL,          "> 0"},
        applyIfCPUFeature = {"avx512", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"avx512", "false", "avx2", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370671
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While MaxReductionV is implemented in NEON (see longMinSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longMinDotProduct() {
        long acc = Long.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VL,          "> 0"},
        applyIfCPUFeature = {"avx512", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"avx512", "false", "avx2", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370671
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While MaxReductionV is implemented in NEON (see longMaxSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longMaxDotProduct() {
        long acc = Long.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------long***Big ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.AND_REDUCTION_V, "> 0",
                  IRNode.AND_VL,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While AndReductionV is implemented in NEON (see longAndSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longAndBig() {
        long acc = 0xFFFFFFFFFFFFFFFFL; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc &= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,  "> 0",
                  IRNode.OR_REDUCTION_V, "> 0",
                  IRNode.OR_VL,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While OrReductionV is implemented in NEON (see longOrSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longOrBig() {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc |= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.XOR_REDUCTION_V, "> 0",
                  IRNode.XOR_VL,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While MaxReductionV is implemented in NEON (see longXorSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longXorBig() {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc ^= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,    "> 0",
                  IRNode.ADD_REDUCTION_VL, "> 0",
                  IRNode.ADD_VL,           "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While MaxReductionV is implemented in NEON (see longAddSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longAddBig() {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,    "> 0",
                  IRNode.MUL_REDUCTION_VL, "> 0",
                  IRNode.MUL_VL,           "> 0"},
        applyIfCPUFeature = {"avx512dq", "true"},
        applyIfAnd = {"AutoVectorizationOverrideProfitability", "> 0",
                      "LoopUnrollLimit", ">= 1000"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeature = {"avx512dq", "true"},
        applyIfAnd = {"AutoVectorizationOverrideProfitability", "> 0",
                      "LoopUnrollLimit", "< 1000"})
    // Increasing the body limit seems to help. Filed for investigation: JDK-8370685
    // If you can eliminate this exception for LoopUnrollLimit, please remove
    // the flag completely from the test, also the "addFlags" at the top.
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // MulVL is not implemented on NEON, so we also not have the reduction.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longMulBig() {
        long acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VL,          "> 0"},
        applyIfCPUFeature = {"avx512", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"avx512", "false", "avx2", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370671
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While MaxReductionV is implemented in NEON (see longMinSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longMinBig() {
        long acc = Long.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VL,          "> 0"},
        applyIfCPUFeature = {"avx512", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"avx512", "false", "avx2", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370671
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // While MaxReductionV is implemented in NEON (see longMaxSimple), MulVL is not.
    // Filed: JDK-8370686
    @IR(failOn = IRNode.LOAD_VECTOR_L,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static long longMaxBig() {
        long acc = Long.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------float***Simple ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.ADD_REDUCTION_V, "> 0",
                  IRNode.ADD_VF,          "= 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "= 2"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "< 2"})
    // Not considered profitable by cost model, but if forced we can vectorize.
    // Scalar: n loads + n adds
    // Vector: n loads + n adds + n extract (sequential order of reduction)
    private static float floatAddSimple() {
        float acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,    "> 0",
                  IRNode.MUL_REDUCTION_VF, "> 0",
                  IRNode.MUL_VF,           "= 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "= 2"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "< 2"})
    // Not considered profitable by cost model, but if forced we can vectorize.
    // Scalar: n loads + n mul
    // Vector: n loads + n mul + n extract (sequential order of reduction)
    private static float floatMulSimple() {
        float acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VF,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatMinSimple() {
        float acc = Float.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i];
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VF,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatMaxSimple() {
        float acc = Float.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i];
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------float***DotProduct ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.ADD_REDUCTION_V, "> 0",
                  IRNode.ADD_VF,          "= 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatAddDotProduct() {
        float acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i] * in2F[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,    "> 0",
                  IRNode.MUL_REDUCTION_VF, "> 0",
                  IRNode.MUL_VF,           "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatMulDotProduct() {
        float acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i] * in2F[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VF,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatMinDotProduct() {
        float acc = Float.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i] * in2F[i];
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VF,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatMaxDotProduct() {
        float acc = Float.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i] * in2F[i];
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------float***Big ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.ADD_REDUCTION_V, "> 0",
                  IRNode.ADD_VF,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatAddBig() {
        float acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = (in1F[i] * in2F[i]) + (in1F[i] * in3F[i]) + (in2F[i] * in3F[i]);
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,    "> 0",
                  IRNode.MUL_REDUCTION_VF, "> 0",
                  IRNode.MUL_VF,           "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatMulBig() {
        float acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = (in1F[i] * in2F[i]) + (in1F[i] * in3F[i]) + (in2F[i] * in3F[i]);
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VF,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatMinBig() {
        float acc = Float.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = (in1F[i] * in2F[i]) + (in1F[i] * in3F[i]) + (in2F[i] * in3F[i]);
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VF,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_F,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static float floatMaxBig() {
        float acc = Float.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = (in1F[i] * in2F[i]) + (in1F[i] * in3F[i]) + (in2F[i] * in3F[i]);
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------double***Simple ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,    "> 0",
                  IRNode.ADD_REDUCTION_VD, "> 0",
                  IRNode.ADD_VD,           "= 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "= 2"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "< 2"})
    // Not considered profitable by cost model, but if forced we can vectorize.
    // Scalar: n loads + n adds
    // Vector: n loads + n adds + n extract (sequential order of reduction)
    private static double doubleAddSimple() {
        double acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,    "> 0",
                  IRNode.MUL_REDUCTION_VD, "> 0",
                  IRNode.MUL_VD,           "= 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "= 2"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "< 2"})
    // Not considered profitable by cost model, but if forced we can vectorize.
    // Scalar: n loads + n mul
    // Vector: n loads + n mul + n extract (sequential order of reduction)
    private static double doubleMulSimple() {
        double acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VD,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleMinSimple() {
        double acc = Double.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i];
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VD,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleMaxSimple() {
        double acc = Double.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i];
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------double***DotProduct ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,   "> 0",
                  IRNode.ADD_REDUCTION_V, "> 0",
                  IRNode.ADD_VD,          "= 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleAddDotProduct() {
        double acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i] * in2D[i];
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,    "> 0",
                  IRNode.MUL_REDUCTION_VD, "> 0",
                  IRNode.MUL_VD,           "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleMulDotProduct() {
        double acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i] * in2D[i];
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VD,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleMinDotProduct() {
        double acc = Double.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i] * in2D[i];
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VD,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleMaxDotProduct() {
        double acc = Double.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i] * in2D[i];
            acc = Math.max(acc, val);
        }
        return acc;
    }

    // ---------double***Big ------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,   "> 0",
                  IRNode.ADD_REDUCTION_V, "> 0",
                  IRNode.ADD_VD,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleAddBig() {
        double acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = (in1D[i] * in2D[i]) + (in1D[i] * in3D[i]) + (in2D[i] * in3D[i]);
            acc += val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,    "> 0",
                  IRNode.MUL_REDUCTION_VD, "> 0",
                  IRNode.MUL_VD,           "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIfCPUFeatureAnd = {"asimd", "true"})
    // I think this could vectorize, but currently does not. Filed: JDK-8370677
    // But: it is not clear that it would be profitable, given the sequential reduction.
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleMulBig() {
        double acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = (in1D[i] * in2D[i]) + (in1D[i] * in3D[i]) + (in2D[i] * in3D[i]);
            acc *= val;
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,   "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.MIN_VD,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleMinBig() {
        double acc = Double.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = (in1D[i] * in2D[i]) + (in1D[i] * in3D[i]) + (in2D[i] * in3D[i]);
            acc = Math.min(acc, val);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D,   "> 0",
                  IRNode.MAX_REDUCTION_V, "> 0",
                  IRNode.MAX_VD,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        applyIf = {"AutoVectorizationOverrideProfitability", "> 0"})
    @IR(failOn = IRNode.LOAD_VECTOR_D,
        applyIf = {"AutoVectorizationOverrideProfitability", "= 0"})
    private static double doubleMaxBig() {
        double acc = Double.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = (in1D[i] * in2D[i]) + (in1D[i] * in3D[i]) + (in2D[i] * in3D[i]);
            acc = Math.max(acc, val);
        }
        return acc;
    }


}
