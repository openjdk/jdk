/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.nio.ByteOrder;

/*
 * @test
 * @bug 8326139
 * @summary Test splitting packs in SuperWord
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.loopopts.superword.TestSplitPacks
 */

public class TestSplitPacks {
    static int RANGE = 1024*8;
    static int RANGE_FINAL = 1024*8;
    private static final Random RANDOM = Utils.getRandomInstance();

    // Inputs
    byte[] aB;
    byte[] bB;
    byte mB = (byte)31;
    short[] aS;
    short[] bS;
    short mS = (short)0xF0F0;
    int[] aI;
    int[] bI;
    int mI = 0xF0F0F0F0;
    long[] aL;
    long[] bL;
    long mL = 0xF0F0F0F0F0F0F0F0L;

    // List of tests
    Map<String,TestFunction> tests = new HashMap<String,TestFunction>();

    // List of gold, the results from the first run before compilation
    Map<String,Object[]> golds = new HashMap<String,Object[]>();

    interface TestFunction {
        Object[] run();
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:LoopUnrollLimit=1000");
    }

    public TestSplitPacks() {
        // Generate input once
        aB = generateB();
        bB = generateB();
        aS = generateS();
        bS = generateS();
        aI = generateI();
        bI = generateI();
        aL = generateL();
        bL = generateL();

        // Add all tests to list
        tests.put("test0",       () -> { return test0(aI.clone(), bI.clone(), mI); });
        tests.put("test1a",      () -> { return test1a(aI.clone(), bI.clone(), mI); });
        tests.put("test1b",      () -> { return test1b(aI.clone(), bI.clone(), mI); });
        tests.put("test1c",      () -> { return test1c(aI.clone(), bI.clone(), mI); });
        tests.put("test1d",      () -> { return test1d(aI.clone(), bI.clone(), mI); });
        tests.put("test2a",      () -> { return test2a(aI.clone(), bI.clone(), mI); });
        tests.put("test2b",      () -> { return test2b(aI.clone(), bI.clone(), mI); });
        tests.put("test2c",      () -> { return test2c(aI.clone(), bI.clone(), mI); });
        tests.put("test2d",      () -> { return test2d(aI.clone(), bI.clone(), mI); });
        tests.put("test3a",      () -> { return test3a(aS.clone(), bS.clone(), mS); });
        tests.put("test4a",      () -> { return test4a(aS.clone(), bS.clone()); });
        tests.put("test4b",      () -> { return test4b(aS.clone(), bS.clone()); });
        tests.put("test4c",      () -> { return test4c(aS.clone(), bS.clone()); });
        tests.put("test4d",      () -> { return test4d(aS.clone(), bS.clone()); });
        tests.put("test4e",      () -> { return test4e(aS.clone(), bS.clone()); });
        tests.put("test4f",      () -> { return test4f(aS.clone(), bS.clone()); });
        tests.put("test4g",      () -> { return test4g(aS.clone(), bS.clone()); });
        tests.put("test5a",      () -> { return test5a(aS.clone(), bS.clone(), mS); });
        tests.put("test6a",      () -> { return test6a(aI.clone(), bI.clone()); });
        tests.put("test7a",      () -> { return test7a(aI.clone(), bI.clone()); });

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            Object[] gold = test.run();
            golds.put(name, gold);
        }
    }

    @Warmup(100)
    @Run(test = {"test0",
                 "test1a",
                 "test1b",
                 "test1c",
                 "test1d",
                 "test2a",
                 "test2b",
                 "test2c",
                 "test2d",
                 "test3a",
                 "test4a",
                 "test4b",
                 "test4c",
                 "test4d",
                 "test4e",
                 "test4f",
                 "test4g",
                 "test5a",
                 "test6a",
                 "test7a"})
    public void runTests() {
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            // Recall gold value from before compilation
            Object[] gold = golds.get(name);
            // Compute new result
            Object[] result = test.run();
            // Compare gold and new result
            verify(name, gold, result);
        }
    }

    static byte[] generateB() {
        byte[] a = new byte[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)RANDOM.nextInt();
        }
        return a;
    }

    static short[] generateS() {
        short[] a = new short[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = (short)RANDOM.nextInt();
        }
        return a;
    }

    static int[] generateI() {
        int[] a = new int[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = RANDOM.nextInt();
        }
        return a;
    }

    static long[] generateL() {
        long[] a = new long[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = RANDOM.nextLong();
        }
        return a;
    }

    static void verify(String name, Object[] gold, Object[] result) {
        if (gold.length != result.length) {
            throw new RuntimeException("verify " + name + ": not the same number of outputs: gold.length = " +
                                       gold.length + ", result.length = " + result.length);
        }
        for (int i = 0; i < gold.length; i++) {
            Object g = gold[i];
            Object r = result[i];
            if (g.getClass() != r.getClass() || !g.getClass().isArray() || !r.getClass().isArray()) {
                throw new RuntimeException("verify " + name + ": must both be array of same type:" +
                                           " gold[" + i + "].getClass() = " + g.getClass().getSimpleName() +
                                           " result[" + i + "].getClass() = " + r.getClass().getSimpleName());
            }
            if (g == r) {
                throw new RuntimeException("verify " + name + ": should be two separate arrays (with identical content):" +
                                           " gold[" + i + "] == result[" + i + "]");
            }
            if (Array.getLength(g) != Array.getLength(r)) {
                    throw new RuntimeException("verify " + name + ": arrays must have same length:" +
                                           " gold[" + i + "].length = " + Array.getLength(g) +
                                           " result[" + i + "].length = " + Array.getLength(r));
            }
            Class c = g.getClass().getComponentType();
            if (c == byte.class) {
                verifyB(name, i, (byte[])g, (byte[])r);
            } else if (c == short.class) {
                verifyS(name, i, (short[])g, (short[])r);
            } else if (c == int.class) {
                verifyI(name, i, (int[])g, (int[])r);
            } else if (c == long.class) {
                verifyL(name, i, (long[])g, (long[])r);
            } else {
                throw new RuntimeException("verify " + name + ": array type not supported for verify:" +
                                       " gold[" + i + "].getClass() = " + g.getClass().getSimpleName() +
                                       " result[" + i + "].getClass() = " + r.getClass().getSimpleName());
            }
        }
    }

    static void verifyB(String name, int i, byte[] g, byte[] r) {
        for (int j = 0; j < g.length; j++) {
            if (g[j] != r[j]) {
                throw new RuntimeException("verify " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    static void verifyS(String name, int i, short[] g, short[] r) {
        for (int j = 0; j < g.length; j++) {
            if (g[j] != r[j]) {
                throw new RuntimeException("verify " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    static void verifyI(String name, int i, int[] g, int[] r) {
        for (int j = 0; j < g.length; j++) {
            if (g[j] != r[j]) {
                throw new RuntimeException("verify " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    static void verifyL(String name, int i, long[] g, long[] r) {
        for (int j = 0; j < g.length; j++) {
            if (g[j] != r[j]) {
                throw new RuntimeException("verify " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Load and store are already split
    //
    //  0 1 - - 4 5 6 7
    //  | |     | | | |
    //  0 1 - - 4 5 6 7
    static Object[] test0(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i+=8) {
            int b0 = a[i+0] & mask;
            int b1 = a[i+1] & mask;

            int b4 = a[i+4] & mask;
            int b5 = a[i+5] & mask;
            int b6 = a[i+6] & mask;
            int b7 = a[i+7] & mask;

            b[i+0] = b0;
            b[i+1] = b1;

            b[i+4] = b4;
            b[i+5] = b5;
            b[i+6] = b6;
            b[i+7] = b7;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.ADD_VI,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.MUL_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Adjacent Load and Store, but split by Add/Mul
    static Object[] test1a(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i+=8) {
            b[i+0] = a[i+0] + mask; // Add
            b[i+1] = a[i+1] + mask;
            b[i+2] = a[i+2] + mask;
            b[i+3] = a[i+3] + mask;

            b[i+4] = a[i+4] * mask; // Mul
            b[i+5] = a[i+5] * mask;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.ADD_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.MUL_VI,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Adjacent Load and Store, but split by Add/Mul
    static Object[] test1b(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i+=8) {
            b[i+0] = a[i+0] * mask; // Mul
            b[i+1] = a[i+1] * mask;
            b[i+2] = a[i+2] * mask;
            b[i+3] = a[i+3] * mask;

            b[i+4] = a[i+4] + mask; // Add
            b[i+5] = a[i+5] + mask;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.ADD_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.MUL_VI,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    // Adjacent Load and Store, but split by Add/Mul
    static Object[] test1c(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i+=8) {
            b[i+0] = a[i+0] + mask; // Add
            b[i+1] = a[i+1] + mask;

            b[i+2] = a[i+2] * mask; // Mul
            b[i+3] = a[i+3] * mask;
            b[i+4] = a[i+4] * mask;
            b[i+5] = a[i+5] * mask;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.ADD_VI,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.MUL_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    // Adjacent Load and Store, but split by Add/Mul
    static Object[] test1d(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i+=8) {
            b[i+0] = a[i+0] * mask; // Mul
            b[i+1] = a[i+1] * mask;

            b[i+2] = a[i+2] + mask; // Add
            b[i+3] = a[i+3] + mask;
            b[i+4] = a[i+4] + mask;
            b[i+5] = a[i+5] + mask;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Split the load
    //
    //  0 1 2 3 4 5 - -
    //  | |  \ \ \ \
    //  | |   \ \ \ \
    //  | |    \ \ \ \
    //  0 1 - - 4 5 6 7
    //
    static Object[] test2a(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i+=8) {
            int b0 = a[i+0] & mask;
            int b1 = a[i+1] & mask;
            int b2 = a[i+2] & mask;
            int b3 = a[i+3] & mask;
            int b4 = a[i+4] & mask;
            int b5 = a[i+5] & mask;

            b[i+0] = b0;
            b[i+1] = b1;

            b[i+4] = b2;
            b[i+5] = b3;
            b[i+6] = b4;
            b[i+7] = b5;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Split the load
    //
    //  0 1 2 3 4 5 - -
    //  | | | |  \ \
    //  | | | |   \ \
    //  | | | |    \ \
    //  0 1 2 3 -- 6 7
    //
    static Object[] test2b(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i+=8) {
            int b0 = a[i+0] & mask;
            int b1 = a[i+1] & mask;
            int b2 = a[i+2] & mask;
            int b3 = a[i+3] & mask;
            int b4 = a[i+4] & mask;
            int b5 = a[i+5] & mask;

            b[i+0] = b0;
            b[i+1] = b1;
            b[i+2] = b2;
            b[i+3] = b3;

            b[i+6] = b4;
            b[i+7] = b5;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Split the load
    //
    //  0 1 - - 4 5 6 7
    //  | |    / / / /
    //  | |   / / / /
    //  | |  / / / /
    //  0 1 2 3 4 5 - -
    //
    static Object[] test2c(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i+=8) {
            int b0 = a[i+0] & mask;
            int b1 = a[i+1] & mask;

            int b4 = a[i+4] & mask;
            int b5 = a[i+5] & mask;
            int b6 = a[i+6] & mask;
            int b7 = a[i+7] & mask;

            b[i+0] = b0;
            b[i+1] = b1;
            b[i+2] = b4;
            b[i+3] = b5;
            b[i+4] = b6;
            b[i+5] = b7;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Split the load
    //
    //  0 1 2 3 - - 6 7
    //  | | | |    / /
    //  | | | |   / /
    //  | | | |  / /
    //  0 1 2 3 4 5 - -
    //
    static Object[] test2d(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i+=8) {
            int b0 = a[i+0] & mask;
            int b1 = a[i+1] & mask;
            int b2 = a[i+2] & mask;
            int b3 = a[i+3] & mask;

            int b6 = a[i+6] & mask;
            int b7 = a[i+7] & mask;

            b[i+0] = b0;
            b[i+1] = b1;
            b[i+2] = b2;
            b[i+3] = b3;
            b[i+4] = b6;
            b[i+5] = b7;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // 0 1 2 3 4 5 6 7 -
    // | | | | | | | |
    // | + + + | | | |
    // |       | | | |
    // |     v | | | | v
    // |     | | | | | |
    // 1 - - 3 4 5 6 7 8
    static Object[] test3a(short[] a, short[] b, short val) {
        int sum = 0;
        for (int i = 0; i < RANGE; i+=16) {
          short a0 = a[i+0]; // required for alignment / offsets, technical limitation.

          short a1 = a[i+1]; // adjacent to 4-pack, but need to be split off
          short a2 = a[i+2];
          short a3 = a[i+3];

          short a4 = a[i+4]; // 4-pack
          short a5 = a[i+5];
          short a6 = a[i+6];
          short a7 = a[i+7];


          b[i+0] = a0; // required for alignment / offsets, technical limitation.

          sum += a1 + a2 + a3; // not packed

          b[i+3] = val; // adjacent to 4-pack but needs to be split off

          b[i+4] = a4; // 4-pack
          b[i+5] = a5;
          b[i+6] = a6;
          b[i+7] = a7;

          b[i+8] = val; // adjacent to 4-pack but needs to be split off
        }
        return new Object[]{ a, b, new int[]{ sum } };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true"})
    // Cyclic dependency with distance 2 -> split into 2-packs
    static Object[] test4a(short[] a, short[] b) {
        for (int i = 0; i < RANGE-64; i++) {
          b[i+2] = a[i+0];
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true"})
    // Cyclic dependency with distance 3 -> split into 2-packs
    static Object[] test4b(short[] a, short[] b) {
        for (int i = 0; i < RANGE-64; i++) {
          b[i+3] = a[i+0];
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=8"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Cyclic dependency with distance 4 -> split into 4-packs
    static Object[] test4c(short[] a, short[] b) {
        for (int i = 0; i < RANGE-64; i++) {
          b[i+4] = a[i+0];
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"MaxVectorSize", ">=8", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Cyclic dependency with distance 5 -> split into 4-packs
    static Object[] test4d(short[] a, short[] b) {
        for (int i = 0; i < RANGE-64; i++) {
          b[i+5] = a[i+0];
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"MaxVectorSize", ">=8", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Cyclic dependency with distance 6 -> split into 4-packs
    static Object[] test4e(short[] a, short[] b) {
        for (int i = 0; i < RANGE-64; i++) {
          b[i+6] = a[i+0];
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"MaxVectorSize", ">=8", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Cyclic dependency with distance 7 -> split into 4-packs
    static Object[] test4f(short[] a, short[] b) {
        for (int i = 0; i < RANGE-64; i++) {
          b[i+7] = a[i+0];
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_8, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Cyclic dependency with distance 8 -> split into 8-packs
    static Object[] test4g(short[] a, short[] b) {
        for (int i = 0; i < RANGE-64; i++) {
          b[i+8] = a[i+0];
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_8, "> 0",
                  IRNode.ADD_VS,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.ADD_VS,        IRNode.VECTOR_SIZE_8, "> 0",
                  IRNode.ADD_VS,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"MaxVectorSize", ">=32", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Split pack into power-of-2 sizes
    static Object[] test5a(short[] a, short[] b, short val) {
        for (int i = 0; i < RANGE; i+=16) {
            b[i+ 0] = (short)(a[i+ 0] + val); // 8 pack
            b[i+ 1] = (short)(a[i+ 1] + val);
            b[i+ 2] = (short)(a[i+ 2] + val);
            b[i+ 3] = (short)(a[i+ 3] + val);
            b[i+ 4] = (short)(a[i+ 4] + val);
            b[i+ 5] = (short)(a[i+ 5] + val);
            b[i+ 6] = (short)(a[i+ 6] + val);
            b[i+ 7] = (short)(a[i+ 7] + val);

            b[i+ 8] = (short)(a[i+ 8] + val); // 4-pack
            b[i+ 9] = (short)(a[i+ 9] + val);
            b[i+10] = (short)(a[i+10] + val);
            b[i+11] = (short)(a[i+11] + val);

            b[i+12] = (short)(a[i+12] + val); // 2-pack
            b[i+13] = (short)(a[i+13] + val);

            b[i+14] = (short)(a[i+14] + val);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.MUL_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.ADD_VI,          IRNode.VECTOR_SIZE_4, "> 0", // reduction moved out of loop
                  IRNode.ADD_REDUCTION_V,                       "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Split packs including reductions
    static Object[] test6a(int[] a, int[] b) {
        int s = 0;
        for (int i = 0; i < RANGE; i+=8) {
            s += a[i+0] * b[i+0];
            s += a[i+1] * b[i+1];
            s += a[i+2] * b[i+2];
            s += a[i+3] * b[i+3];

            s += a[i+4] & b[i+4];
            s += a[i+5] & b[i+5];
            s += a[i+6] & b[i+6];
            s += a[i+7] & b[i+7];
        }
        return new Object[]{ a, b, new int[]{ s } };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,  "> 0",
                  IRNode.MUL_VI,         "> 0",
                  IRNode.POPULATE_INDEX, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"})
    // Index Populate:
    // There can be an issue when all the (iv + 1), (iv + 2), ...
    // get packed, but not (iv). Then we have a pack that is one element
    // too short, and we start splitting everything in a bad way.
    static Object[] test7a(int[] a, int[] b) {
        for (int i = 0; i < RANGE; i++) {
            a[i] = b[i] * i;
        }
        return new Object[]{ a, b };
    }
}
