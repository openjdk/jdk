/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 */

/*
 * @test
 * @bug 8375502
 * @key randomness
 * @summary Unsigned widening (LoadUB/LoadUS feeding int, Byte/Short.toUnsignedInt shapes)
 *          vectorizes via an inserted unsigned cast.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestUnsignedCast
 */

package compiler.loopopts.superword;

import compiler.lib.generators.Generator;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import java.util.Map;
import java.util.HashMap;
import static compiler.lib.generators.Generators.G;

public class TestUnsignedCast {
    static int RANGE = 1024;
    private static final Generator<Integer> INT_GEN = G.ints();

    // Inputs
    byte[] aB;
    byte[] bB;
    short[] aS;

    // List of tests
    Map<String,TestFunction> tests = new HashMap<String,TestFunction>();

    // List of gold, the results from the first run before compilation
    Map<String,Object[]> golds = new HashMap<String,Object[]>();

    interface TestFunction {
        Object[] run();
    }

    public static void main(String[] args) {
        TestFramework.run();
    }

    public TestUnsignedCast() {
        // Generate input once
        aB = generateB();
        bB = generateB();
        aS = generateS();

        // Add all tests to list
        tests.put("unsignedWiden",           () -> { return unsignedWiden(aB.clone(), new int[RANGE]); });
        tests.put("unsignedWidenPlusOne",    () -> { return unsignedWidenPlusOne(aB.clone(), new int[RANGE]); });
        tests.put("unsignedWidenTwoSources", () -> { return unsignedWidenTwoSources(aB.clone(), bB.clone(), new int[RANGE]); });
        tests.put("unsignedWidenToUnsignedInt", () -> { return unsignedWidenToUnsignedInt(aB.clone(), new int[RANGE]); });
        tests.put("signedWiden",             () -> { return signedWiden(aB.clone(), new int[RANGE]); });
        tests.put("unsignedWidenToShort",    () -> { return unsignedWidenToShort(aB.clone(), new short[RANGE]); });
        tests.put("unsignedWidenToLong",     () -> { return unsignedWidenToLong(aB.clone(), new long[RANGE]); });
        tests.put("unsignedShortWiden",           () -> { return unsignedShortWiden(aS.clone(), new int[RANGE]); });
        tests.put("unsignedShortWidenToUnsignedInt", () -> { return unsignedShortWidenToUnsignedInt(aS.clone(), new int[RANGE]); });

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            Object[] gold = test.run();
            golds.put(name, gold);
        }
    }

    @Warmup(100)
    @Run(test = {"unsignedWiden",
                 "unsignedWidenPlusOne",
                 "unsignedWidenTwoSources",
                 "unsignedWidenToUnsignedInt",
                 "signedWiden",
                 "unsignedWidenToShort",
                 "unsignedWidenToLong",
                 "unsignedShortWiden",
                 "unsignedShortWidenToUnsignedInt"})
    public void runTests() {
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            // Recall gold value from before compilation
            Object[] gold = golds.get(name);
            // Compute new result
            Object[] result = test.run();
            // Compare gold and new result
            Verify.checkEQ(gold, result);
        }
    }

    static byte[] generateB() {
        int[] tmp = new int[RANGE];
        G.fill(INT_GEN, tmp);
        byte[] a = new byte[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)tmp[i];
        }
        // Boundary values for unsigned byte widening.
        a[0] = 0;
        a[1] = 1;
        a[2] = 0x7F;
        a[3] = (byte)0x80;
        a[4] = (byte)0xFF;
        return a;
    }

    static short[] generateS() {
        int[] tmp = new int[RANGE];
        G.fill(INT_GEN, tmp);
        short[] a = new short[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = (short)tmp[i];
        }
        // Boundary values for unsigned short widening.
        a[0] = 0;
        a[1] = 1;
        a[2] = 0x7FFF;
        a[3] = (short)0x8000;
        a[4] = (short)0xFFFF;
        return a;
    }

    @Test
    @IR(counts = {IRNode.VECTOR_UCAST_B2I, IRNode.VECTOR_SIZE + "min(max_byte, max_int)", ">0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_B, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    static Object[] unsignedWiden(byte[] src, int[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i] & 0xFF;
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.VECTOR_UCAST_B2I, IRNode.VECTOR_SIZE + "min(max_byte, max_int)", ">0",
                  IRNode.ADD_VI, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_B, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    static Object[] unsignedWidenPlusOne(byte[] src, int[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = (src[i] & 0xFF) + 1;
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.VECTOR_UCAST_B2I, IRNode.VECTOR_SIZE + "min(max_byte, max_int)", ">0",
                  IRNode.ADD_VI, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_B, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    static Object[] unsignedWidenTwoSources(byte[] a, byte[] b, int[] dst) {
        for (int i = 0; i < a.length; i++) {
            dst[i] = (a[i] & 0xFF) + (b[i] & 0xFF);
        }
        return new Object[]{ a, b, dst };
    }

    @Test
    @IR(counts = {IRNode.VECTOR_UCAST_B2I, IRNode.VECTOR_SIZE + "min(max_byte, max_int)", ">0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_B, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    static Object[] unsignedWidenToUnsignedInt(byte[] src, int[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = Byte.toUnsignedInt(src[i]);
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.VECTOR_CAST_B2I, IRNode.VECTOR_SIZE + "min(max_byte, max_int)", ">0",
                  IRNode.VECTOR_UCAST_B2I, "= 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    static Object[] signedWiden(byte[] src, int[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i];
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.VECTOR_UCAST_B2I, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    static Object[] unsignedWidenToShort(byte[] src, short[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = (short)(src[i] & 0xFF);
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.VECTOR_UCAST_B2I, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    static Object[] unsignedWidenToLong(byte[] src, long[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = (long)(src[i] & 0xFF);
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.VECTOR_UCAST_S2I, IRNode.VECTOR_SIZE + "min(max_short, max_int)", ">0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_S, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    static Object[] unsignedShortWiden(short[] src, int[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i] & 0xFFFF;
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.VECTOR_UCAST_S2I, IRNode.VECTOR_SIZE + "min(max_short, max_int)", ">0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_S, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    static Object[] unsignedShortWidenToUnsignedInt(short[] src, int[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = Short.toUnsignedInt(src[i]);
        }
        return new Object[]{ src, dst };
    }
}
