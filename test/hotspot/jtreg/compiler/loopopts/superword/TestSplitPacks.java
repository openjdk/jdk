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
 * @bug 8309267
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
        // TODO decide what types we keep

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
                 "test2d"})
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
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "= 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_4, "= 0",
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
    // The 4-pack does not vectorize. This is a technical limitation that
    // we can hopefully soon remove. Load and store offsets are different.
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
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "= 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_2, "= 0",
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
    // The 2-pack does not vectorize. This is a technical limitation that
    // we can hopefully soon remove. Load and store offsets are different.
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
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "= 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_4, "= 0",
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
    // The 4-pack does not vectorize. This is a technical limitation that
    // we can hopefully soon remove. Load and store offsets are different.
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
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_2, "= 0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VI,        IRNode.VECTOR_SIZE_2, "= 0",
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
    // The 2-pack does not vectorize. This is a technical limitation that
    // we can hopefully soon remove. Load and store offsets are different.
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
}
