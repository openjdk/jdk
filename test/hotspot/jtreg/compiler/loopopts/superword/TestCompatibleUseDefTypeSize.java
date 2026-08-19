/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

/*
 * @test
 * @bug 8325155 8342095
 * @key randomness
 * @summary Test some cases that vectorize after the removal of the alignment boundaries code.
 *          Now, we instead check if use-def connections have compatible type size.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestCompatibleUseDefTypeSize
 */

public class TestCompatibleUseDefTypeSize {
    static int RANGE = 1024*8;
    private static final Random RANDOM = Utils.getRandomInstance();

    // Inputs
    byte[] aB;
    byte[] bB;
    short[] aS;
    short[] bS;
    char[] aC;
    char[] bC;
    int[] aI;
    int[] bI;
    long[] aL;
    long[] bL;
    float[] aF;
    float[] bF;
    double[] aD;
    double[] bD;
    MemorySegment aMSF;
    MemorySegment aMSD;

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

    public TestCompatibleUseDefTypeSize() {
        // Generate input once
        aB = generateB();
        bB = generateB();
        aS = generateS();
        bS = generateS();
        aC = generateC();
        bC = generateC();
        aI = generateI();
        bI = generateI();
        aL = generateL();
        bL = generateL();
        aF = generateF();
        bF = generateF();
        aD = generateD();
        bD = generateD();
        aMSF = generateMemorySegmentF();
        aMSD = generateMemorySegmentD();

        // Add all tests to list
        tests.put("test0",       () -> { return test0(aB.clone(), bC.clone()); });
        tests.put("test1",       () -> { return test1(aB.clone(), bC.clone()); });
        tests.put("test2",       () -> { return test2(aB.clone(), bC.clone()); });
        tests.put("test3",       () -> { return test3(aI.clone(), bI.clone()); });
        tests.put("test4",       () -> { return test4(aI.clone(), bI.clone()); });
        tests.put("test5",       () -> { return test5(aI.clone(), bF.clone()); });
        tests.put("test6",       () -> { return test6(aI.clone(), bF.clone()); });
        tests.put("test7",       () -> { return test7(aI.clone(), bF.clone()); });
        tests.put("test8",       () -> { return test8(aL.clone(), bD.clone()); });
        tests.put("test9",       () -> { return test9(aL.clone(), bD.clone()); });
        tests.put("test10",      () -> { return test10(aL.clone(), bD.clone()); });
        tests.put("test11",      () -> { return test11(aC.clone()); });
        tests.put("testByteToInt",   () -> { return testByteToInt(aB.clone(), bI.clone()); });
        tests.put("testByteToShort", () -> { return testByteToShort(aB.clone(), bS.clone()); });
        tests.put("testByteToChar",  () -> { return testByteToChar(aB.clone(), bC.clone()); });
        tests.put("testByteToLong",  () -> { return testByteToLong(aB.clone(), bL.clone()); });
        tests.put("testShortToByte", () -> { return testShortToByte(aS.clone(), bB.clone()); });
        tests.put("testShortToChar", () -> { return testShortToChar(aS.clone(), bC.clone()); });
        tests.put("testShortToInt",  () -> { return testShortToInt(aS.clone(), bI.clone()); });
        tests.put("testShortToLong", () -> { return testShortToLong(aS.clone(), bL.clone()); });
        tests.put("testIntToShort",  () -> { return testIntToShort(aI.clone(), bS.clone()); });
        tests.put("testIntToChar",   () -> { return testIntToChar(aI.clone(), bC.clone()); });
        tests.put("testIntToByte",   () -> { return testIntToByte(aI.clone(), bB.clone()); });
        tests.put("testIntToLong",   () -> { return testIntToLong(aI.clone(), bL.clone()); });
        tests.put("testLongToByte",  () -> { return testLongToByte(aL.clone(), bB.clone()); });
        tests.put("testLongToShort", () -> { return testLongToShort(aL.clone(), bS.clone()); });
        tests.put("testLongToChar",  () -> { return testLongToChar(aL.clone(), bC.clone()); });
        tests.put("testLongToInt",   () -> { return testLongToInt(aL.clone(), bI.clone()); });
        tests.put("testFloatToIntMemorySegment",   () -> { return testFloatToIntMemorySegment(copyF(aMSF), bF.clone()); });
        tests.put("testDoubleToLongMemorySegment", () -> { return testDoubleToLongMemorySegment(copyD(aMSD), bD.clone()); });
        tests.put("testIntToFloatMemorySegment",   () -> { return testIntToFloatMemorySegment(copyF(aMSF), bF.clone()); });
        tests.put("testLongToDoubleMemorySegment", () -> { return testLongToDoubleMemorySegment(copyD(aMSD), bD.clone()); });

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
                 "test1",
                 "test2",
                 "test3",
                 "test4",
                 "test5",
                 "test6",
                 "test7",
                 "test8",
                 "test9",
                 "test10",
                 "test11",
                 "testByteToInt",
                 "testByteToShort",
                 "testByteToChar",
                 "testByteToLong",
                 "testShortToByte",
                 "testShortToChar",
                 "testShortToInt",
                 "testShortToLong",
                 "testIntToShort",
                 "testIntToChar",
                 "testIntToByte",
                 "testIntToLong",
                 "testLongToByte",
                 "testLongToShort",
                 "testLongToChar",
                 "testLongToInt",
                 "testFloatToIntMemorySegment",
                 "testDoubleToLongMemorySegment",
                 "testIntToFloatMemorySegment",
                 "testLongToDoubleMemorySegment"})
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

    static char[] generateC() {
        char[] a = new char[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = (char)RANDOM.nextInt();
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

    static float[] generateF() {
        float[] a = new float[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = Float.intBitsToFloat(RANDOM.nextInt());
        }
        return a;
    }

    static double[] generateD() {
        double[] a = new double[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = Double.longBitsToDouble(RANDOM.nextLong());
        }
        return a;
    }

    static MemorySegment generateMemorySegmentF() {
        MemorySegment a = MemorySegment.ofArray(new float[RANGE]);
        for (int i = 0; i < (int) a.byteSize(); i += 8) {
            a.set(ValueLayout.JAVA_LONG_UNALIGNED, i, RANDOM.nextLong());
        }
        return a;
    }

    MemorySegment copyF(MemorySegment src) {
        MemorySegment dst = generateMemorySegmentF();
        MemorySegment.copy(src, 0, dst, 0, src.byteSize());
        return dst;
    }

    static MemorySegment generateMemorySegmentD() {
        MemorySegment a = MemorySegment.ofArray(new double[RANGE]);
        for (int i = 0; i < (int) a.byteSize(); i += 8) {
            a.set(ValueLayout.JAVA_LONG_UNALIGNED, i, RANDOM.nextLong());
        }
        return a;
    }

    MemorySegment copyD(MemorySegment src) {
        MemorySegment dst = generateMemorySegmentD();
        MemorySegment.copy(src, 0, dst, 0, src.byteSize());
        return dst;
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // "inflate"  method: 1 byte -> 2 byte.
    // Java scalar code has no explicit conversion.
    // Vector code would need a conversion. We may add this in the future.
    static Object[] test0(byte[] src, char[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = (char)(src[i] & 0xff);
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    // "inflate"  method: 1 byte -> 2 byte.
    // Java scalar code has no explicit conversion.
    static Object[] test1(byte[] src, char[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = (char)(src[i]);
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // "deflate"  method: 2 byte -> 1 byte.
    // Java scalar code has no explicit conversion.
    // Vector code would need a conversion. We may add this in the future.
    static Object[] test2(byte[] src, char[] dst) {
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte)(dst[i]);
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"}, // a[i] and a[i+1] cannot both be aligned.
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // Used to not vectorize because of "alignment boundaries".
    // Assume 64 byte vector width:
    // a[i+0:i+15] and a[i+1:i+16], each are 4 * 16 = 64 byte.
    // The alignment boundary is every 64 byte, so one of the two vectors gets cut up.
    static Object[] test3(int[] a, int[] b) {
        for (int i = 0; i < a.length-1; i++) {
            a[i] = (int)(b[i] + a[i+1]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"}, // a[i] and a[i+1] cannot both be aligned.
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // same as test3, but hand-unrolled
    static Object[] test4(int[] a, int[] b) {
        for (int i = 0; i < a.length-2; i+=2) {
            a[i+0] = (int)(b[i+0] + a[i+1]);
            a[i+1] = (int)(b[i+1] + a[i+2]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // In theory, one would expect this to be a simple 4byte -> 4byte conversion.
    // But there is a CmpF and CMove here because we check for isNaN. Plus a MoveF2I.
    //
    // Would be nice to vectorize: Missing support for CmpF and CMove.
    static Object[] test5(int[] a, float[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Float.floatToIntBits(b[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test6(int[] a, float[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Float.floatToRawIntBits(b[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_F, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test7(int[] a, float[] b) {
        for (int i = 0; i < a.length; i++) {
            b[i] = Float.intBitsToFloat(a[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // Missing support to vectorize CmpD and CMove
    static Object[] test8(long[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Double.doubleToLongBits(b[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, IRNode.VECTOR_SIZE + "min(max_long, max_double)", "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test9(long[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Double.doubleToRawLongBits(b[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_D, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] test10(long[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            b[i] = Double.longBitsToDouble(a[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    // MaxI reduction is with char type, but the MaxI char vector is not implemented.
    static Object[] test11(char[] a) {
        char m = 0;
        for (int i = 0; i < a.length; i++) {
            m = (char)Math.max(m, a[i]);
            a[i] = 0;
        }
        return new Object[]{ a, new char[] { m } };
    }

    // Narrowing

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_I2S, IRNode.VECTOR_SIZE + "min(max_int, max_short)", ">0" })
    public Object[] testIntToShort(int[] ints, short[] res) {
        for (int i = 0; i < ints.length; i++) {
            res[i] = (short) ints[i];
        }

        return new Object[] { ints, res };
    }


    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_I2S, IRNode.VECTOR_SIZE + "min(max_int, max_char)", ">0" })
    public Object[] testIntToChar(int[] ints, char[] res) {
        for (int i = 0; i < ints.length; i++) {
            res[i] = (char) ints[i];
        }

        return new Object[] { ints, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_I2B, IRNode.VECTOR_SIZE + "min(max_int, max_byte)", ">0" })
    public Object[] testIntToByte(int[] ints, byte[] res) {
        for (int i = 0; i < ints.length; i++) {
            res[i] = (byte) ints[i];
        }

        return new Object[] { ints, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_S2B, IRNode.VECTOR_SIZE + "min(max_short, max_byte)", ">0" })
    public Object[] testShortToByte(short[] shorts, byte[] res) {
        for (int i = 0; i < shorts.length; i++) {
            res[i] = (byte) shorts[i];
        }

        return new Object[] { shorts, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx2", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_L2B, IRNode.VECTOR_SIZE + "min(max_long, max_byte)", ">0" })
    public Object[] testLongToByte(long[] longs, byte[] res) {
        for (int i = 0; i < longs.length; i++) {
            res[i] = (byte) longs[i];
        }

        return new Object[] { longs, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_L2S, IRNode.VECTOR_SIZE + "min(max_long, max_short)", ">0" })
    public Object[] testLongToShort(long[] longs, short[] res) {
        for (int i = 0; i < longs.length; i++) {
            res[i] = (short) longs[i];
        }

        return new Object[] { longs, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_L2S, IRNode.VECTOR_SIZE + "min(max_long, max_char)", ">0" })
    public Object[] testLongToChar(long[] longs, char[] res) {
        for (int i = 0; i < longs.length; i++) {
            res[i] = (char) longs[i];
        }

        return new Object[] { longs, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_L2I, IRNode.VECTOR_SIZE + "min(max_long, max_int)", ">0" })
    public Object[] testLongToInt(long[] longs, int[] res) {
        for (int i = 0; i < longs.length; i++) {
            res[i] = (int) longs[i];
        }

        return new Object[] { longs, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.STORE_VECTOR, ">0" })
    public Object[] testShortToChar(short[] shorts, char[] res) {
        for (int i = 0; i < shorts.length; i++) {
            res[i] = (char) shorts[i];
        }

        return new Object[] { shorts, res };
    }

    // Widening

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_S2I, IRNode.VECTOR_SIZE + "min(max_short, max_int)", ">0" })
    public Object[] testShortToInt(short[] shorts, int[] res) {
        for (int i = 0; i < shorts.length; i++) {
            res[i] = shorts[i];
        }

        return new Object[] { shorts, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_B2I, IRNode.VECTOR_SIZE + "min(max_byte, max_int)", ">0" })
    public Object[] testByteToInt(byte[] bytes, int[] res) {
        for (int i = 0; i < bytes.length; i++) {
            res[i] = bytes[i];
        }

        return new Object[] { bytes, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_B2S, IRNode.VECTOR_SIZE + "min(max_byte, max_short)", ">0" })
    public Object[] testByteToShort(byte[] bytes, short[] res) {
        for (int i = 0; i < bytes.length; i++) {
            res[i] = bytes[i];
        }

        return new Object[] { bytes, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_B2S, IRNode.VECTOR_SIZE + "min(max_byte, max_char)", ">0" })
    public Object[] testByteToChar(byte[] bytes, char[] res) {
        for (int i = 0; i < bytes.length; i++) {
            res[i] = (char) bytes[i];
        }

        return new Object[] { bytes, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx2", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_B2L, IRNode.VECTOR_SIZE + "min(max_byte, max_long)", ">0" })
    public Object[] testByteToLong(byte[] bytes, long[] res) {
        for (int i = 0; i < bytes.length; i++) {
            res[i] = bytes[i];
        }

        return new Object[] { bytes, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_S2L, IRNode.VECTOR_SIZE + "min(max_short, max_long)", ">0" })
    public Object[] testShortToLong(short[] shorts, long[] res) {
        for (int i = 0; i < shorts.length; i++) {
            res[i] = shorts[i];
        }

        return new Object[] { shorts, res };
    }

    @Test
    @IR(applyIfCPUFeatureOr = { "avx", "true", "asimd", "true", "rvv", "true" },
        applyIfOr = {"AlignVector", "false", "UseCompactObjectHeaders", "false"},
        counts = { IRNode.VECTOR_CAST_I2L, IRNode.VECTOR_SIZE + "min(max_int, max_long)", ">0" })
    public Object[] testIntToLong(int[] ints, long[] res) {
        for (int i = 0; i < ints.length; i++) {
            res[i] = ints[i];
        }

        return new Object[] { ints, res };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testFloatToIntMemorySegment(MemorySegment a, float[] b) {
        for (int i = 0; i < RANGE; i++) {
            a.set(ValueLayout.JAVA_FLOAT_UNALIGNED, 4L * i, b[i]);
        }

        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_D, IRNode.VECTOR_SIZE + "min(max_long, max_double)", "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_L, IRNode.VECTOR_SIZE + "min(max_long, max_double)", "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testDoubleToLongMemorySegment(MemorySegment a, double[] b) {
        for (int i = 0; i < RANGE; i++) {
            a.set(ValueLayout.JAVA_DOUBLE_UNALIGNED, 8L * i, b[i]);
        }

        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_F, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntToFloatMemorySegment(MemorySegment a, float[] b) {
        for (int i = 0; i < RANGE; i++) {
            b[i] = a.get(ValueLayout.JAVA_FLOAT_UNALIGNED, 4L * i);
        }

        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_L, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  IRNode.VECTOR_REINTERPRET_D, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testLongToDoubleMemorySegment(MemorySegment a, double[] b) {
        for (int i = 0; i < RANGE; i++) {
            b[i] = a.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, 8L * i);
        }

        return new Object[]{ a, b };
    }
}
