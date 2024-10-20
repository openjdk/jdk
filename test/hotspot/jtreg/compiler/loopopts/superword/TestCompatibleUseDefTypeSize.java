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
 * @bug 8325155
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
                 "test11"})
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
            } else if (c == char.class) {
                verifyC(name, i, (char[])g, (char[])r);
            } else if (c == int.class) {
                verifyI(name, i, (int[])g, (int[])r);
            } else if (c == long.class) {
                verifyL(name, i, (long[])g, (long[])r);
            } else if (c == float.class) {
                verifyF(name, i, (float[])g, (float[])r);
            } else if (c == double.class) {
                verifyD(name, i, (double[])g, (double[])r);
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

    static void verifyC(String name, int i, char[] g, char[] r) {
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

    static void verifyF(String name, int i, float[] g, float[] r) {
        for (int j = 0; j < g.length; j++) {
            if (Float.floatToIntBits(g[j]) != Float.floatToIntBits(r[j])) {
                throw new RuntimeException("verify " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    static void verifyD(String name, int i, double[] g, double[] r) {
        for (int j = 0; j < g.length; j++) {
            if (Double.doubleToLongBits(g[j]) != Double.doubleToLongBits(r[j])) {
                throw new RuntimeException("verify " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + g[j] +
                                           " result[" + i + "][" + j + "] = " + r[j]);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // "inflate"  method: 1 byte -> 2 byte.
    // Java scalar code has no explicit conversion.
    // Vector code would need a conversion. We may add this in the future.
    static Object[] test1(byte[] src, char[] dst) {
        for (int i = 0; i < src.length; i++) {
            dst[i] = (char)(src[i]);
        }
        return new Object[]{ src, dst };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // In theory, one would expect this to be a simple 4byte -> 4byte conversion.
    // But there is a CmpF and CMove here because we check for isNaN. Plus a MoveF2I.
    //
    // Would be nice to vectorize: Missing support for CmpF, CMove and MoveF2I.
    static Object[] test5(int[] a, float[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Float.floatToIntBits(b[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Missing support for MoveF2I
    static Object[] test6(int[] a, float[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Float.floatToRawIntBits(b[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Missing support for MoveI2F
    static Object[] test7(int[] a, float[] b) {
        for (int i = 0; i < a.length; i++) {
            b[i] = Float.intBitsToFloat(a[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Missing support for Needs CmpD, CMove and MoveD2L
    static Object[] test8(long[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Double.doubleToLongBits(b[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Missing support for MoveD2L
    static Object[] test9(long[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = Double.doubleToRawLongBits(b[i]);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Missing support for MoveL2D
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
}
