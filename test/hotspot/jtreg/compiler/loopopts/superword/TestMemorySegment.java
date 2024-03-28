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
import jdk.internal.misc.Unsafe;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.lang.foreign.*;

/*
 * @test
 * @bug 8310190
 * @summary Test vectorization of loops over MemorySegment
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main compiler.loopopts.superword.TestMemorySegment
 */

public class TestMemorySegment {
    static int RANGE = 1024*8;
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Random RANDOM = Utils.getRandomInstance();

    // Inputs
    byte[] aB;
    byte[] bB;
    short[] aS;
    short[] bS;
    int[] aI;
    int[] bI;
    long[] aL;
    long[] bL;

    // List of tests
    Map<String,TestFunction> tests = new HashMap<String,TestFunction>();

    // List of gold, the results from the first run before compilation
    Map<String,Object[]> golds = new HashMap<String,Object[]>();

    interface TestFunction {
        Object[] run();
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMemorySegment.class);
        framework.addFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
        framework.start();
    }

    public TestMemorySegment() {
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
        tests.put("testArrayBB", () -> { return testArrayBB(aB.clone(), bB.clone()); });
        tests.put("testMemorySegmentB", () -> { return testMemorySegmentB(MemorySegment.ofArray(aB.clone())); });

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            Object[] gold = test.run();
            golds.put(name, gold);
        }
    }

    @Warmup(100)
    @Run(test = {"testArrayBB",
                 "testMemorySegmentB"})
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
            if (g == r) {
                throw new RuntimeException("verify " + name + ": should be two separate objects (with identical content):" +
                                           " gold[" + i + "] == result[" + i + "]");
            }

            MemorySegment mg;
            MemorySegment mr;
            if (g.getClass().isArray()) {
                if (g.getClass() != r.getClass() || !g.getClass().isArray() || !r.getClass().isArray()) {
                    throw new RuntimeException("verify " + name + ": must both be array of same type:" +
                                               " gold[" + i + "].getClass() = " + g.getClass().getSimpleName() +
                                               " result[" + i + "].getClass() = " + r.getClass().getSimpleName());
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
	    } else if (g instanceof MemorySegment) {
                mg = (MemorySegment)g;
                if (!(r instanceof MemorySegment)) {
                    throw new RuntimeException("verify " + name + ": was not both MemorySegment:" +
                                           " gold[" + i + "].getClass() = " + g.getClass().getSimpleName() +
                                           " result[" + i + "].getClass() = " + r.getClass().getSimpleName());
                }
                mr = (MemorySegment)r;
            }
            // TODO verify MemorySegment, size and content. Also do the same for the arrays?
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
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testArrayBB(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            b[i+0] = (byte)(a[i] + 1);
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentB(MemorySegment m) {
        for (int i = 0; i < m.byteSize(); i++) {
            byte v = (byte)(m.get(ValueLayout.JAVA_BYTE, i) + 1);
            m.set(ValueLayout.JAVA_BYTE, i, v);
        }
        return new Object[]{ m };
    }
}
