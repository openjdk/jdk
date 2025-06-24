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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;
import jdk.internal.misc.Unsafe;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.nio.ByteOrder;

/*
 * @test id=NoAlignVector
 * @bug 8310190
 * @summary Test AlignVector with various loop init, stride, scale, invar, etc.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestAlignVector NoAlignVector
 */

/*
 * @test id=AlignVector
 * @bug 8310190
 * @summary Test AlignVector with various loop init, stride, scale, invar, etc.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestAlignVector AlignVector
 */

/*
 * @test id=VerifyAlignVector
 * @bug 8310190
 * @summary Test AlignVector with various loop init, stride, scale, invar, etc.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestAlignVector VerifyAlignVector
 */

/*
 * @test id=NoAlignVector-COH
 * @bug 8310190
 * @summary Test AlignVector with various loop init, stride, scale, invar, etc.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestAlignVector NoAlignVector-COH
 */

/*
 * @test id=VerifyAlignVector-COH
 * @bug 8310190
 * @summary Test AlignVector with various loop init, stride, scale, invar, etc.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestAlignVector VerifyAlignVector-COH
 */

public class TestAlignVector {
    static int RANGE = 1024*8;
    static int RANGE_FINAL = 1024*8;
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
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
        TestFramework framework = new TestFramework(TestAlignVector.class);
        framework.addFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                           "-XX:+IgnoreUnrecognizedVMOptions", "-XX:LoopUnrollLimit=250");

        switch (args[0]) {
            case "NoAlignVector"         -> { framework.addFlags("-XX:-UseCompactObjectHeaders", "-XX:-AlignVector"); }
            case "AlignVector"           -> { framework.addFlags("-XX:-UseCompactObjectHeaders", "-XX:+AlignVector"); }
            case "VerifyAlignVector"     -> { framework.addFlags("-XX:-UseCompactObjectHeaders", "-XX:+AlignVector", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+VerifyAlignVector"); }
            case "NoAlignVector-COH"     -> { framework.addFlags("-XX:+UseCompactObjectHeaders", "-XX:-AlignVector"); }
            case "VerifyAlignVector-COH" -> { framework.addFlags("-XX:+UseCompactObjectHeaders", "-XX:+AlignVector", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+VerifyAlignVector"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        }
        framework.start();
    }

    public TestAlignVector() {
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
        tests.put("test0",       () -> { return test0(aB.clone(), bB.clone(), mB); });
        tests.put("test1a",      () -> { return test1a(aB.clone(), bB.clone(), mB); });
        tests.put("test1b",      () -> { return test1b(aB.clone(), bB.clone(), mB); });
        tests.put("test2",       () -> { return test2(aB.clone(), bB.clone(), mB); });
        tests.put("test3",       () -> { return test3(aB.clone(), bB.clone(), mB); });
        tests.put("test4",       () -> { return test4(aB.clone(), bB.clone(), mB); });
        tests.put("test5",       () -> { return test5(aB.clone(), bB.clone(), mB, 0); });
        tests.put("test6",       () -> { return test6(aB.clone(), bB.clone(), mB); });
        tests.put("test7",       () -> { return test7(aS.clone(), bS.clone(), mS); });
        tests.put("test8",       () -> { return test8(aB.clone(), bB.clone(), mB, 0); });
        tests.put("test8",       () -> { return test8(aB.clone(), bB.clone(), mB, 1); });
        tests.put("test9",       () -> { return test9(aB.clone(), bB.clone(), mB); });

        tests.put("test10a",     () -> { return test10a(aB.clone(), bB.clone(), mB); });
        tests.put("test10b",     () -> { return test10b(aB.clone(), bB.clone(), mB); });
        tests.put("test10c",     () -> { return test10c(aS.clone(), bS.clone(), mS); });
        tests.put("test10d",     () -> { return test10d(aS.clone(), bS.clone(), mS); });
        tests.put("test10e",     () -> { return test10e(aS.clone(), bS.clone(), mS); });

        tests.put("test11aB",    () -> { return test11aB(aB.clone(), bB.clone(), mB); });
        tests.put("test11aS",    () -> { return test11aS(aS.clone(), bS.clone(), mS); });
        tests.put("test11aI",    () -> { return test11aI(aI.clone(), bI.clone(), mI); });
        tests.put("test11aL",    () -> { return test11aL(aL.clone(), bL.clone(), mL); });

        tests.put("test11bB",    () -> { return test11bB(aB.clone(), bB.clone(), mB); });
        tests.put("test11bS",    () -> { return test11bS(aS.clone(), bS.clone(), mS); });
        tests.put("test11bI",    () -> { return test11bI(aI.clone(), bI.clone(), mI); });
        tests.put("test11bL",    () -> { return test11bL(aL.clone(), bL.clone(), mL); });

        tests.put("test11cB",    () -> { return test11cB(aB.clone(), bB.clone(), mB); });
        tests.put("test11cS",    () -> { return test11cS(aS.clone(), bS.clone(), mS); });
        tests.put("test11cI",    () -> { return test11cI(aI.clone(), bI.clone(), mI); });
        tests.put("test11cL",    () -> { return test11cL(aL.clone(), bL.clone(), mL); });

        tests.put("test11dB",    () -> { return test11dB(aB.clone(), bB.clone(), mB, 0); });
        tests.put("test11dS",    () -> { return test11dS(aS.clone(), bS.clone(), mS, 0); });
        tests.put("test11dI",    () -> { return test11dI(aI.clone(), bI.clone(), mI, 0); });
        tests.put("test11dL",    () -> { return test11dL(aL.clone(), bL.clone(), mL, 0); });

        tests.put("test12",      () -> { return test12(aB.clone(), bB.clone(), mB); });

        tests.put("test13aIL",   () -> { return test13aIL(aI.clone(), aL.clone()); });
        tests.put("test13aIB",   () -> { return test13aIB(aI.clone(), aB.clone()); });
        tests.put("test13aIS",   () -> { return test13aIS(aI.clone(), aS.clone()); });
        tests.put("test13aBSIL", () -> { return test13aBSIL(aB.clone(), aS.clone(), aI.clone(), aL.clone()); });

        tests.put("test13bIL",   () -> { return test13bIL(aI.clone(), aL.clone()); });
        tests.put("test13bIB",   () -> { return test13bIB(aI.clone(), aB.clone()); });
        tests.put("test13bIS",   () -> { return test13bIS(aI.clone(), aS.clone()); });
        tests.put("test13bBSIL", () -> { return test13bBSIL(aB.clone(), aS.clone(), aI.clone(), aL.clone()); });

        tests.put("test14aB",    () -> { return test14aB(aB.clone()); });
        tests.put("test14bB",    () -> { return test14bB(aB.clone()); });
        tests.put("test14cB",    () -> { return test14cB(aB.clone()); });
        tests.put("test14dB",    () -> { return test14dB(aB.clone()); });
        tests.put("test14eB",    () -> { return test14eB(aB.clone()); });
        tests.put("test14fB",    () -> { return test14fB(aB.clone()); });

        tests.put("test15aB",    () -> { return test15aB(aB.clone()); });
        tests.put("test15bB",    () -> { return test15bB(aB.clone()); });
        tests.put("test15cB",    () -> { return test15cB(aB.clone()); });

        tests.put("test16a",     () -> { return test16a(aB.clone(), aS.clone()); });
        tests.put("test16b",     () -> { return test16b(aB.clone()); });

        tests.put("test17a",     () -> { return test17a(aL.clone()); });
        tests.put("test17b",     () -> { return test17b(aL.clone()); });
        tests.put("test17c",     () -> { return test17c(aL.clone()); });
        tests.put("test17d",     () -> { return test17d(aL.clone()); });

        tests.put("test18a",     () -> { return test18a(aB.clone(), aI.clone()); });
        tests.put("test18b",     () -> { return test18b(aB.clone(), aI.clone()); });

        tests.put("test19",      () -> { return test19(aI.clone(), bI.clone()); });
        tests.put("test20",      () -> { return test20(aB.clone()); });

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
                 "test2",
                 "test3",
                 "test4",
                 "test5",
                 "test6",
                 "test7",
                 "test8",
                 "test9",
                 "test10a",
                 "test10b",
                 "test10c",
                 "test10d",
                 "test10e",
                 "test11aB",
                 "test11aS",
                 "test11aI",
                 "test11aL",
                 "test11bB",
                 "test11bS",
                 "test11bI",
                 "test11bL",
                 "test11cB",
                 "test11cS",
                 "test11cI",
                 "test11cL",
                 "test11dB",
                 "test11dS",
                 "test11dI",
                 "test11dL",
                 "test12",
                 "test13aIL",
                 "test13aIB",
                 "test13aIS",
                 "test13aBSIL",
                 "test13bIL",
                 "test13bIB",
                 "test13bIS",
                 "test13bBSIL",
                 "test14aB",
                 "test14bB",
                 "test14cB",
                 "test14dB",
                 "test14eB",
                 "test14fB",
                 "test15aB",
                 "test15bB",
                 "test15cB",
                 "test16a",
                 "test16b",
                 "test17a",
                 "test17b",
                 "test17c",
                 "test17d",
                 "test18a",
                 "test18b",
                 "test19",
                 "test20"})
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
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=8"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test0(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE; i+=8) {
            // Safe to vectorize with AlignVector
            b[i+0] = (byte)(a[i+0] & mask); // offset 0, align 0
            b[i+1] = (byte)(a[i+1] & mask);
            b[i+2] = (byte)(a[i+2] & mask);
            b[i+3] = (byte)(a[i+3] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.AND_VB, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        // UNSAFE.ARRAY_BYTE_BASE_OFFSET = 16, but with compact object headers UNSAFE.ARRAY_BYTE_BASE_OFFSET=12.
        // If AlignVector=true, we need the offset to be 8-byte aligned, else the vectors are filtered out.
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test1a(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE; i+=8) {
            b[i+0] = (byte)(a[i+0] & mask); // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0 + iter*8
            b[i+1] = (byte)(a[i+1] & mask);
            b[i+2] = (byte)(a[i+2] & mask);
            b[i+3] = (byte)(a[i+3] & mask);
            b[i+4] = (byte)(a[i+4] & mask);
            b[i+5] = (byte)(a[i+5] & mask);
            b[i+6] = (byte)(a[i+6] & mask);
            b[i+7] = (byte)(a[i+7] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.AND_VB, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "true", "AlignVector", "false"},
        // UNSAFE.ARRAY_BYTE_BASE_OFFSET = 16, but with compact object headers UNSAFE.ARRAY_BYTE_BASE_OFFSET=12.
        // If AlignVector=true, we need the offset to be 8-byte aligned, else the vectors are filtered out.
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test1b(byte[] a, byte[] b, byte mask) {
        for (int i = 4; i < RANGE-8; i+=8) {
            b[i+0] = (byte)(a[i+0] & mask); // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4 + iter*8
            b[i+1] = (byte)(a[i+1] & mask);
            b[i+2] = (byte)(a[i+2] & mask);
            b[i+3] = (byte)(a[i+3] & mask);
            b[i+4] = (byte)(a[i+4] & mask);
            b[i+5] = (byte)(a[i+5] & mask);
            b[i+6] = (byte)(a[i+6] & mask);
            b[i+7] = (byte)(a[i+7] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=8"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.AND_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test2(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE; i+=8) {
            // Cannot align with AlignVector: 3 + x * 8 % 8 = 3
            b[i+3] = (byte)(a[i+3] & mask); // at alignment 3
            b[i+4] = (byte)(a[i+4] & mask);
            b[i+5] = (byte)(a[i+5] & mask);
            b[i+6] = (byte)(a[i+6] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=8"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.AND_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test3(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE; i+=8) {
            // Cannot align with AlignVector: 3 + x * 8 % 8 = 3

            // Problematic for AlignVector
            b[i+0] = (byte)(a[i+0] & mask); // best_memref, align 0

            b[i+3] = (byte)(a[i+3] & mask); // pack at offset 3 bytes
            b[i+4] = (byte)(a[i+4] & mask);
            b[i+5] = (byte)(a[i+5] & mask);
            b[i+6] = (byte)(a[i+6] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_8, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_8, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=16"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_8, "= 0",// unaligned
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_8, "= 0",// unaligned
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "true", "MaxVectorSize", ">=16"})
    static Object[] test4(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE/16; i++) {
            // Problematic for AlignVector
            b[i*16 + 0 ] = (byte)(a[i*16 + 0 ] & mask); // 4 pack, 0 aligned
            b[i*16 + 1 ] = (byte)(a[i*16 + 1 ] & mask);
            b[i*16 + 2 ] = (byte)(a[i*16 + 2 ] & mask);
            b[i*16 + 3 ] = (byte)(a[i*16 + 3 ] & mask);

            b[i*16 + 5 ] = (byte)(a[i*16 + 5 ] & mask); // 8 pack, 5 aligned
            b[i*16 + 6 ] = (byte)(a[i*16 + 6 ] & mask);
            b[i*16 + 7 ] = (byte)(a[i*16 + 7 ] & mask);
            b[i*16 + 8 ] = (byte)(a[i*16 + 8 ] & mask);
            b[i*16 + 9 ] = (byte)(a[i*16 + 9 ] & mask);
            b[i*16 + 10] = (byte)(a[i*16 + 10] & mask);
            b[i*16 + 11] = (byte)(a[i*16 + 11] & mask);
            b[i*16 + 12] = (byte)(a[i*16 + 12] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=8"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.AND_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test5(byte[] a, byte[] b, byte mask, int inv) {
        for (int i = 0; i < RANGE; i+=8) {
            // Cannot align with AlignVector because of invariant
            b[i+inv+0] = (byte)(a[i+inv+0] & mask);

            b[i+inv+3] = (byte)(a[i+inv+3] & mask);
            b[i+inv+4] = (byte)(a[i+inv+4] & mask);
            b[i+inv+5] = (byte)(a[i+inv+5] & mask);
            b[i+inv+6] = (byte)(a[i+inv+6] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=8"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.AND_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test6(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE/8; i+=2) {
            // Cannot align with AlignVector because offset is odd
            b[i*4+0] = (byte)(a[i*4+0] & mask);

            b[i*4+3] = (byte)(a[i*4+3] & mask);
            b[i*4+4] = (byte)(a[i*4+4] & mask);
            b[i*4+5] = (byte)(a[i*4+5] & mask);
            b[i*4+6] = (byte)(a[i*4+6] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VS,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=16"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_S, "= 0",
                  IRNode.AND_VS, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test7(short[] a, short[] b, short mask) {
        for (int i = 0; i < RANGE/8; i+=2) {
            // Cannot align with AlignVector because offset is odd
            b[i*4+0] = (short)(a[i*4+0] & mask);

            b[i*4+3] = (short)(a[i*4+3] & mask);
            b[i*4+4] = (short)(a[i*4+4] & mask);
            b[i*4+5] = (short)(a[i*4+5] & mask);
            b[i*4+6] = (short)(a[i*4+6] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=8"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.AND_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test8(byte[] a, byte[] b, byte mask, int init) {
        for (int i = init; i < RANGE; i+=8) {
            // Cannot align with AlignVector because of invariant (variable init becomes invar)
            b[i+0] = (byte)(a[i+0] & mask);

            b[i+3] = (byte)(a[i+3] & mask);
            b[i+4] = (byte)(a[i+4] & mask);
            b[i+5] = (byte)(a[i+5] & mask);
            b[i+6] = (byte)(a[i+6] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=8"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test9(byte[] a, byte[] b, byte mask) {
        // known non-zero init value does not affect offset, but has implicit effect on iv
        for (int i = 13; i < RANGE-8; i+=8) {
            b[i+0] = (byte)(a[i+0] & mask);

            b[i+3] = (byte)(a[i+3] & mask);
            b[i+4] = (byte)(a[i+4] & mask);
            b[i+5] = (byte)(a[i+5] & mask);
            b[i+6] = (byte)(a[i+6] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=8"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.AND_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test10a(byte[] a, byte[] b, byte mask) {
        // This is not alignable with pre-loop, because of odd init.
        for (int i = 3; i < RANGE-8; i+=8) {
            b[i+0] = (byte)(a[i+0] & mask);
            b[i+1] = (byte)(a[i+1] & mask);
            b[i+2] = (byte)(a[i+2] & mask);
            b[i+3] = (byte)(a[i+3] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=8"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.AND_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test10b(byte[] a, byte[] b, byte mask) {
        // This is not alignable with pre-loop, because of odd init.
        // Seems not correctly handled.
        for (int i = 13; i < RANGE-8; i+=8) {
            b[i+0] = (byte)(a[i+0] & mask);
            b[i+1] = (byte)(a[i+1] & mask);
            b[i+2] = (byte)(a[i+2] & mask);
            b[i+3] = (byte)(a[i+3] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VS,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=16"})
    @IR(counts = {IRNode.LOAD_VECTOR_S, "= 0",
                  IRNode.AND_VS, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test10c(short[] a, short[] b, short mask) {
        // This is not alignable with pre-loop, because of odd init.
        // Seems not correctly handled with MaxVectorSize >= 32.
        for (int i = 13; i < RANGE-8; i+=8) {
            b[i+0] = (short)(a[i+0] & mask);
            b[i+1] = (short)(a[i+1] & mask);
            b[i+2] = (short)(a[i+2] & mask);
            b[i+3] = (short)(a[i+3] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VS,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"MaxVectorSize", ">=16", "UseCompactObjectHeaders", "false"},
        // UNSAFE.ARRAY_BYTE_BASE_OFFSET = 16, but with compact object headers UNSAFE.ARRAY_BYTE_BASE_OFFSET=12.
        // If AlignVector=true, we need the offset to be 8-byte aligned, else the vectors are filtered out.
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test10d(short[] a, short[] b, short mask) {
        for (int i = 13; i < RANGE-16; i+=8) {
            // adr = base + UNSAFE.ARRAY_SHORT_BASE_OFFSET + 2*(3 + 13) + iter*16
            b[i+0+3] = (short)(a[i+0+3] & mask);
            b[i+1+3] = (short)(a[i+1+3] & mask);
            b[i+2+3] = (short)(a[i+2+3] & mask);
            b[i+3+3] = (short)(a[i+3+3] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.AND_VS,        IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"MaxVectorSize", ">=16", "UseCompactObjectHeaders", "true"},
        // UNSAFE.ARRAY_BYTE_BASE_OFFSET = 16, but with compact object headers UNSAFE.ARRAY_BYTE_BASE_OFFSET=12.
        // If AlignVector=true, we need the offset to be 8-byte aligned, else the vectors are filtered out.
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test10e(short[] a, short[] b, short mask) {
        for (int i = 11; i < RANGE-16; i+=8) {
            // adr = base + UNSAFE.ARRAY_SHORT_BASE_OFFSET + 2*(3 + 11) + iter*16
            b[i+0+3] = (short)(a[i+0+3] & mask);
            b[i+1+3] = (short)(a[i+1+3] & mask);
            b[i+2+3] = (short)(a[i+2+3] & mask);
            b[i+3+3] = (short)(a[i+3+3] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.AND_VB, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11aB(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE; i++) {
            // always alignable
            b[i+0] = (byte)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.AND_VS, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11aS(short[] a, short[] b, short mask) {
        for (int i = 0; i < RANGE; i++) {
            // always alignable
            b[i+0] = (short)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.AND_VI, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11aI(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i++) {
            // always alignable
            b[i+0] = (int)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, "> 0",
                  IRNode.AND_VL, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11aL(long[] a, long[] b, long mask) {
        for (int i = 0; i < RANGE; i++) {
            // always alignable
            b[i+0] = (long)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.AND_VB, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11bB(byte[] a, byte[] b, byte mask) {
        for (int i = 1; i < RANGE; i++) {
            // always alignable
            b[i+0] = (byte)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.AND_VS, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11bS(short[] a, short[] b, short mask) {
        for (int i = 1; i < RANGE; i++) {
            // always alignable
            b[i+0] = (short)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.AND_VI, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11bI(int[] a, int[] b, int mask) {
        for (int i = 1; i < RANGE; i++) {
            // always alignable
            b[i+0] = (int)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, "> 0",
                  IRNode.AND_VL, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11bL(long[] a, long[] b, long mask) {
        for (int i = 1; i < RANGE; i++) {
            // always alignable
            b[i+0] = (long)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.AND_VB, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.AND_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test11cB(byte[] a, byte[] b, byte mask) {
        for (int i = 1; i < RANGE-1; i++) {
            // 1 byte offset -> not alignable with AlignVector
            b[i+0] = (byte)(a[i+1] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.AND_VS, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_S, "= 0",
                  IRNode.AND_VS, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test11cS(short[] a, short[] b, short mask) {
        for (int i = 1; i < RANGE-1; i++) {
            // 2 byte offset -> not alignable with AlignVector
            b[i+0] = (short)(a[i+1] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.AND_VI, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.AND_VI, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test11cI(int[] a, int[] b, int mask) {
        for (int i = 1; i < RANGE-1; i++) {
            // 4 byte offset -> not alignable with AlignVector
            b[i+0] = (int)(a[i+1] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, "> 0",
                  IRNode.AND_VL, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11cL(long[] a, long[] b, long mask) {
        for (int i = 1; i < RANGE-1; i++) {
            // always alignable (8 byte offset)
            b[i+0] = (long)(a[i+1] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.AND_VB, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11dB(byte[] a, byte[] b, byte mask, int invar) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0+invar] = (byte)(a[i+0+invar] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.AND_VS, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11dS(short[] a, short[] b, short mask, int invar) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0+invar] = (short)(a[i+0+invar] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.AND_VI, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11dI(int[] a, int[] b, int mask, int invar) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0+invar] = (int)(a[i+0+invar] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, "> 0",
                  IRNode.AND_VL, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test11dL(long[] a, long[] b, long mask, int invar) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0+invar] = (long)(a[i+0+invar] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE + "min(max_byte, 4)", "> 0",
                  IRNode.AND_VB,        IRNode.VECTOR_SIZE + "min(max_byte, 4)", "> 0",
                  IRNode.STORE_VECTOR,                                           "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test12(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE/16; i++) {
            // Non-power-of-2 stride. Vectorization of 4 bytes, then 2-bytes gap.
            b[i*6 + 0 ] = (byte)(a[i*6 + 0 ] & mask);
            b[i*6 + 1 ] = (byte)(a[i*6 + 1 ] & mask);
            b[i*6 + 2 ] = (byte)(a[i*6 + 2 ] & mask);
            b[i*6 + 3 ] = (byte)(a[i*6 + 3 ] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.LOAD_VECTOR_L, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.ADD_VI, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.ADD_VL, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "rvv", "true"})
    // require avx to ensure vectors are larger than what unrolling produces
    static Object[] test13aIL(int[] a, long[] b) {
        for (int i = 0; i < RANGE; i++) {
            a[i]++;
            b[i]++;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VB, "> 0",
                  IRNode.ADD_VI, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test13aIB(int[] a, byte[] b) {
        for (int i = 0; i < RANGE; i++) {
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            a[i]++;
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET  + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            b[i]++;
            // For AlignVector, all adr must be 8-byte aligned. Let's see for which iteration this can hold:
            // If UseCompactObjectHeaders=false:
            //   a: 0, 8, 16, 24, 32, ...
            //   b: 0, 2,  4,  6,  8, ...
            //   -> Ok, aligns every 8th iteration.
            // If UseCompactObjectHeaders=true:
            //   a: 4, 12, 20, 28, 36, ...
            //   b: 1,  3,  5,  7,  9, ...
            //   -> we can never align both vectors!
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.ADD_VI, "> 0",
                  IRNode.ADD_VS, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test13aIS(int[] a, short[] b) {
        for (int i = 0; i < RANGE; i++) {
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            a[i]++;
            // adr = base + UNSAFE.ARRAY_SHORT_BASE_OFFSET + 2*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            b[i]++;
            // For AlignVector, all adr must be 8-byte aligned. Let's see for which iteration this can hold:
            // If UseCompactObjectHeaders=false:
            //   a: iter % 2 == 0
            //   b: iter % 4 == 0
            //   -> Ok, aligns every 4th iteration.
            // If UseCompactObjectHeaders=true:
            //   a: iter % 2 = 1
            //   b: iter % 4 = 2
            //   -> we can never align both vectors!
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.LOAD_VECTOR_L, "> 0",
                  IRNode.ADD_VB, "> 0",
                  IRNode.ADD_VS, "> 0",
                  IRNode.ADD_VI, "> 0",
                  IRNode.ADD_VL, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test13aBSIL(byte[] a, short[] b, int[] c, long[] d) {
        for (int i = 0; i < RANGE; i++) {
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            a[i]++;
            // adr = base + UNSAFE.ARRAY_SHORT_BASE_OFFSET + 2*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            b[i]++;
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            c[i]++;
            // adr = base + UNSAFE.ARRAY_LONG_BASE_OFFSET + 8*iter
            //              = 16 (always)
            d[i]++;
            // If AlignVector and UseCompactObjectHeaders, and we want all adr 8-byte aligned:
            //   a: iter % 8 = 4
            //   c: iter % 2 = 1
            //   -> can never align both vectors!
        }
        return new Object[]{ a, b, c, d };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.LOAD_VECTOR_L, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.ADD_VI, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.ADD_VL, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "rvv", "true"})
    // require avx to ensure vectors are larger than what unrolling produces
    static Object[] test13bIL(int[] a, long[] b) {
        for (int i = 1; i < RANGE; i++) {
            a[i]++;
            b[i]++;
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VB, "> 0",
                  IRNode.ADD_VI, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test13bIB(int[] a, byte[] b) {
        for (int i = 1; i < RANGE; i++) {
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 4 + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            a[i]++;
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1 + 1*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            b[i]++;
            // If AlignVector and UseCompactObjectHeaders, and we want all adr 8-byte aligned:
            //   a: iter % 2 = 0
            //   b: iter % 8 = 3
            //   -> can never align both vectors!
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.ADD_VI, "> 0",
                  IRNode.ADD_VS, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test13bIS(int[] a, short[] b) {
        for (int i = 1; i < RANGE; i++) {
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 4 + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            a[i]++;
            // adr = base + UNSAFE.ARRAY_SHORT_BASE_OFFSET + 2 + 2*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            b[i]++;
            // If AlignVector and UseCompactObjectHeaders, and we want all adr 8-byte aligned:
            //   a: iter % 2 = 0
            //   b: iter % 4 = 1
            //   -> can never align both vectors!
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.LOAD_VECTOR_L, "> 0",
                  IRNode.ADD_VB, "> 0",
                  IRNode.ADD_VS, "> 0",
                  IRNode.ADD_VI, "> 0",
                  IRNode.ADD_VL, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfOr = {"UseCompactObjectHeaders", "false", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    static Object[] test13bBSIL(byte[] a, short[] b, int[] c, long[] d) {
        for (int i = 1; i < RANGE; i++) {
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1 + 1*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            a[i]++;
            // adr = base + UNSAFE.ARRAY_SHORT_BASE_OFFSET + 2 + 2*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            b[i]++;
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 4 + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            c[i]++;
            // adr = base + UNSAFE.ARRAY_LONG_BASE_OFFSET + 8 + 8*iter
            //              = 16 (always)
            d[i]++;
            // If AlignVector and UseCompactObjectHeaders, and we want all adr 8-byte aligned:
            //   a: iter % 8 = 3
            //   c: iter % 2 = 0
            //   -> can never align both vectors!
        }
        return new Object[]{ a, b, c, d };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test14aB(byte[] a) {
        // non-power-of-2 stride
        for (int i = 0; i < RANGE-20; i+=9) {
            // Since the stride is shorter than the vector length, there will be always
            // partial overlap of loads with previous stores, this leads to failure in
            // store-to-load-forwarding -> vectorization not profitable.
            a[i+0]++;
            a[i+1]++;
            a[i+2]++;
            a[i+3]++;
            a[i+4]++;
            a[i+5]++;
            a[i+6]++;
            a[i+7]++;
            a[i+8]++;
            a[i+9]++;
            a[i+10]++;
            a[i+11]++;
            a[i+12]++;
            a[i+13]++;
            a[i+14]++;
            a[i+15]++;
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test14bB(byte[] a) {
        // non-power-of-2 stride
        for (int i = 0; i < RANGE-20; i+=3) {
            // Since the stride is shorter than the vector length, there will be always
            // partial overlap of loads with previous stores, this leads to failure in
            // store-to-load-forwarding -> vectorization not profitable.
            a[i+0]++;
            a[i+1]++;
            a[i+2]++;
            a[i+3]++;
            a[i+4]++;
            a[i+5]++;
            a[i+6]++;
            a[i+7]++;
            a[i+8]++;
            a[i+9]++;
            a[i+10]++;
            a[i+11]++;
            a[i+12]++;
            a[i+13]++;
            a[i+14]++;
            a[i+15]++;
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test14cB(byte[] a) {
        // non-power-of-2 stride
        for (int i = 0; i < RANGE-20; i+=5) {
            // Since the stride is shorter than the vector length, there will be always
            // partial overlap of loads with previous stores, this leads to failure in
            // store-to-load-forwarding -> vectorization not profitable.
            a[i+0]++;
            a[i+1]++;
            a[i+2]++;
            a[i+3]++;
            a[i+4]++;
            a[i+5]++;
            a[i+6]++;
            a[i+7]++;
            a[i+8]++;
            a[i+9]++;
            a[i+10]++;
            a[i+11]++;
            a[i+12]++;
            a[i+13]++;
            a[i+14]++;
            a[i+15]++;
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE + "min(max_byte, 8)", "> 0",
                  IRNode.ADD_VB,        IRNode.VECTOR_SIZE + "min(max_byte, 8)", "> 0",
                  IRNode.STORE_VECTOR,                                           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test14dB(byte[] a) {
        // non-power-of-2 stride
        for (int i = 0; i < RANGE-20; i+=9) {
            a[i+0]++;
            a[i+1]++;
            a[i+2]++;
            a[i+3]++;
            a[i+4]++;
            a[i+5]++;
            a[i+6]++;
            a[i+7]++;
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE + "min(max_byte, 8)", "> 0",
                  IRNode.ADD_VB,        IRNode.VECTOR_SIZE + "min(max_byte, 8)", "> 0",
                  IRNode.STORE_VECTOR,                                           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test14eB(byte[] a) {
        // non-power-of-2 stride
        for (int i = 0; i < RANGE-32; i+=11) {
            a[i+0]++;
            a[i+1]++;
            a[i+2]++;
            a[i+3]++;
            a[i+4]++;
            a[i+5]++;
            a[i+6]++;
            a[i+7]++;
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, IRNode.VECTOR_SIZE + "min(max_byte, 8)", "> 0",
                  IRNode.ADD_VB,        IRNode.VECTOR_SIZE + "min(max_byte, 8)", "> 0",
                  IRNode.STORE_VECTOR,                                           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test14fB(byte[] a) {
        // non-power-of-2 stride
        for (int i = 0; i < RANGE-40; i+=12) {
            a[i+0]++;
            a[i+1]++;
            a[i+2]++;
            a[i+3]++;
            a[i+4]++;
            a[i+5]++;
            a[i+6]++;
            a[i+7]++;
        }
        return new Object[]{ a };
    }

    @Test
    // IR rules difficult because of modulo wrapping with offset after peeling.
    static Object[] test15aB(byte[] a) {
        // non-power-of-2 scale
        for (int i = 0; i < RANGE/64-20; i++) {
            a[53*i+0]++;
            a[53*i+1]++;
            a[53*i+2]++;
            a[53*i+3]++;
            a[53*i+4]++;
            a[53*i+5]++;
            a[53*i+6]++;
            a[53*i+7]++;
            a[53*i+8]++;
            a[53*i+9]++;
            a[53*i+10]++;
            a[53*i+11]++;
            a[53*i+12]++;
            a[53*i+13]++;
            a[53*i+14]++;
            a[53*i+15]++;
        }
        return new Object[]{ a };
    }

    @Test
    // IR rules difficult because of modulo wrapping with offset after peeling.
    static Object[] test15bB(byte[] a) {
        // non-power-of-2 scale
        for (int i = 0; i < RANGE/64-20; i++) {
            a[25*i+0]++;
            a[25*i+1]++;
            a[25*i+2]++;
            a[25*i+3]++;
            a[25*i+4]++;
            a[25*i+5]++;
            a[25*i+6]++;
            a[25*i+7]++;
            a[25*i+8]++;
            a[25*i+9]++;
            a[25*i+10]++;
            a[25*i+11]++;
            a[25*i+12]++;
            a[25*i+13]++;
            a[25*i+14]++;
            a[25*i+15]++;
        }
        return new Object[]{ a };
    }

    @Test
    // IR rules difficult because of modulo wrapping with offset after peeling.
    static Object[] test15cB(byte[] a) {
        // non-power-of-2 scale
        for (int i = 0; i < RANGE/64-20; i++) {
            a[19*i+0]++;
            a[19*i+1]++;
            a[19*i+2]++;
            a[19*i+3]++;
            a[19*i+4]++;
            a[19*i+5]++;
            a[19*i+6]++;
            a[19*i+7]++;
            a[19*i+8]++;
            a[19*i+9]++;
            a[19*i+10]++;
            a[19*i+11]++;
            a[19*i+12]++;
            a[19*i+13]++;
            a[19*i+14]++;
            a[19*i+15]++;
        }
        return new Object[]{ a };
    }

    @Test
    static Object[] test16a(byte[] a, short[] b) {
        // infinite loop issues
        for (int i = 0; i < RANGE/2-20; i++) {
            a[2*i+0]++;
            a[2*i+1]++;
            a[2*i+2]++;
            a[2*i+3]++;
            a[2*i+4]++;
            a[2*i+5]++;
            a[2*i+6]++;
            a[2*i+7]++;
            a[2*i+8]++;
            a[2*i+9]++;
            a[2*i+10]++;
            a[2*i+11]++;
            a[2*i+12]++;
            a[2*i+13]++;
            a[2*i+14]++;

            b[2*i+0]++;
            b[2*i+1]++;
            b[2*i+2]++;
            b[2*i+3]++;
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test16b(byte[] a) {
        // infinite loop issues
        for (int i = 0; i < RANGE/2-20; i++) {
            a[2*i+0]++;
            a[2*i+1]++;
            a[2*i+2]++;
            a[2*i+3]++;
            a[2*i+4]++;
            a[2*i+5]++;
            a[2*i+6]++;
            a[2*i+7]++;
            a[2*i+8]++;
            a[2*i+9]++;
            a[2*i+10]++;
            a[2*i+11]++;
            a[2*i+12]++;
            a[2*i+13]++;
            a[2*i+14]++;
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, "> 0",
                  IRNode.ADD_VL, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test17a(long[] a) {
        // Unsafe: vectorizes with profiling (not xcomp)
        for (int i = 0; i < RANGE; i++) {
            long adr = UNSAFE.ARRAY_LONG_BASE_OFFSET + 8L * i;
            long v = UNSAFE.getLongUnaligned(a, adr);
            UNSAFE.putLongUnaligned(a, adr, v + 1);
        }
        return new Object[]{ a };
    }

    @Test
    // Difficult to write good IR rule. Modulo calculus overflow can create non-power-of-2 packs.
    static Object[] test17b(long[] a) {
        // Not alignable
        for (int i = 0; i < RANGE-1; i++) {
            long adr = UNSAFE.ARRAY_LONG_BASE_OFFSET + 8L * i + 1;
            long v = UNSAFE.getLongUnaligned(a, adr);
            UNSAFE.putLongUnaligned(a, adr, v + 1);
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.ADD_VL,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"MaxVectorSize", ">=32"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test17c(long[] a) {
        // Unsafe: aligned vectorizes
        for (int i = 0; i < RANGE-1; i+=4) {
            long adr = UNSAFE.ARRAY_LONG_BASE_OFFSET + 8L * i;
            long v0 = UNSAFE.getLongUnaligned(a, adr + 0);
            long v1 = UNSAFE.getLongUnaligned(a, adr + 8);
            UNSAFE.putLongUnaligned(a, adr + 0, v0 + 1);
            UNSAFE.putLongUnaligned(a, adr + 8, v1 + 1);
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.ADD_VL,        IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"avx512", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "MaxVectorSize", ">=64"})
    // Ensure vector width is large enough to fit 64 byte for longs:
    // The offsets are: 25, 33, 57, 65
    // In modulo 32:    25,  1, 25,  1  -> does not vectorize
    // In modulo 64:    25, 33, 57,  1  -> at least first pair vectorizes
    // This problem is because we compute modulo vector width in memory_alignment.
    @IR(counts = {IRNode.LOAD_VECTOR_L, "= 0",
                  IRNode.ADD_VL, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "true"})
    static Object[] test17d(long[] a) {
        // Not alignable
        for (int i = 0; i < RANGE-1; i+=4) {
            long adr = UNSAFE.ARRAY_LONG_BASE_OFFSET + 8L * i + 1;
            long v0 = UNSAFE.getLongUnaligned(a, adr + 0);
            long v1 = UNSAFE.getLongUnaligned(a, adr + 8);
            UNSAFE.putLongUnaligned(a, adr + 0, v0 + 1);
            UNSAFE.putLongUnaligned(a, adr + 8, v1 + 1);
        }
        return new Object[]{ a };
    }

    @Test
    static Object[] test18a(byte[] a, int[] b) {
        // scale = 0  -->  no iv
        for (int i = 0; i < RANGE; i++) {
            a[0] = 1;
            b[i] = 2;
            a[1] = 1;
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test18b(byte[] a, int[] b) {
        // scale = 0  -->  no iv
        for (int i = 0; i < RANGE; i++) {
            a[1] = 1;
            b[i] = 2;
            a[2] = 1;
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test19(int[] a, int[] b) {
        for (int i = 5000; i > 0; i--) {
            a[RANGE_FINAL - i] = b[RANGE_FINAL - i];
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test20(byte[] a) {
        // Example where it is easy to pass alignment check,
        // but used to fail the alignment calculation
        for (int i = 1; i < RANGE/2-50; i++) {
            a[2*i+0+30]++;
            a[2*i+1+30]++;
            a[2*i+2+30]++;
            a[2*i+3+30]++;
        }
        return new Object[]{ a };
    }
}
