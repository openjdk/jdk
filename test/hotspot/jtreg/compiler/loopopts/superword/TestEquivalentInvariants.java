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
 * @bug 8330274
 * @summary Test vectorization with various invariants that are equivalent, but not trivially so,
 *          i.e. where the invariants have the same summands, but in different order.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestEquivalentInvariants
 */

public class TestEquivalentInvariants {
    static int RANGE = 1024*64;
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
        TestFramework framework = new TestFramework(TestEquivalentInvariants.class);
        framework.addFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
        framework.start();
    }

    public TestEquivalentInvariants() {
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
        tests.put("testArrayBB", () -> {
          return testArrayBB(aB.clone(), bB.clone());
        });
        tests.put("testArrayBBInvar3", () -> {
          return testArrayBBInvar3(aB.clone(), bB.clone(), 0, 0, 0);
        });
        tests.put("testMemorySegmentB", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentB(data);
        });
        tests.put("testMemorySegmentBInvarI", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarI(data, 101, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarL", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarL(data, 101, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarIAdr", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarIAdr(data, 101, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarLAdr", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarLAdr(data, 101, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarI3a", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarI3a(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarI3b", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarI3b(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarI3c", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarI3c(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarI3d", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarI3d(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarI3e", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarI3e(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarI3f", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarI3f(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarL3g", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarL3g(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarL3h", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarL3h(data, -1, -2, -3, RANGE-200);
        });
        tests.put("testMemorySegmentBInvarL3k", () -> {
          MemorySegment data = MemorySegment.ofArray(aB.clone());
          return testMemorySegmentBInvarL3k(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentIInvarL3a", () -> {
          MemorySegment data = MemorySegment.ofArray(aI.clone());
          return testMemorySegmentIInvarL3a(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentIInvarL3b", () -> {
          MemorySegment data = MemorySegment.ofArray(aI.clone());
          return testMemorySegmentIInvarL3b(data, -1, -2, -3, RANGE-200);
        });
        tests.put("testMemorySegmentIInvarL3c", () -> {
          MemorySegment data = MemorySegment.ofArray(aI.clone());
          return testMemorySegmentIInvarL3c(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentIInvarL3d", () -> {
          MemorySegment data = MemorySegment.ofArray(aI.clone());
          return testMemorySegmentIInvarL3d(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentIInvarL3e", () -> {
          MemorySegment data = MemorySegment.ofArray(aI.clone());
          return testMemorySegmentIInvarL3e(data, 1, 2, 3, RANGE-200);
        });
        tests.put("testMemorySegmentIInvarL3f", () -> {
          MemorySegment data = MemorySegment.ofArray(aI.clone());
          return testMemorySegmentIInvarL3f(data, 1, 2, 3, RANGE-200);
        });

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            Object[] gold = test.run();
            golds.put(name, gold);
        }
    }

    @Run(test = {"testArrayBB",
                 "testArrayBBInvar3",
                 "testMemorySegmentB",
                 "testMemorySegmentBInvarI",
                 "testMemorySegmentBInvarL",
                 "testMemorySegmentBInvarIAdr",
                 "testMemorySegmentBInvarLAdr",
                 "testMemorySegmentBInvarI3a",
                 "testMemorySegmentBInvarI3b",
                 "testMemorySegmentBInvarI3c",
                 "testMemorySegmentBInvarI3d",
                 "testMemorySegmentBInvarI3e",
                 "testMemorySegmentBInvarI3f",
                 "testMemorySegmentBInvarL3g",
                 "testMemorySegmentBInvarL3h",
                 "testMemorySegmentBInvarL3k",
                 "testMemorySegmentIInvarL3a",
                 "testMemorySegmentIInvarL3b",
                 "testMemorySegmentIInvarL3c",
                 "testMemorySegmentIInvarL3d",
                 "testMemorySegmentIInvarL3e",
                 "testMemorySegmentIInvarL3f"})
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

            // Wrap everything in MemorySegments, this allows simple value verification of Array as well as MemorySegment.
            MemorySegment mg = null;
            MemorySegment mr = null;
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
                    mg = MemorySegment.ofArray((byte[])g);
                    mr = MemorySegment.ofArray((byte[])r);
                } else if (c == short.class) {
                    mg = MemorySegment.ofArray((short[])g);
                    mr = MemorySegment.ofArray((short[])r);
                } else if (c == int.class) {
                    mg = MemorySegment.ofArray((int[])g);
                    mr = MemorySegment.ofArray((int[])r);
                } else if (c == long.class) {
                    mg = MemorySegment.ofArray((long[])g);
                    mr = MemorySegment.ofArray((long[])r);
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

            if (mg.byteSize() != mr.byteSize()) {
                throw new RuntimeException("verify " + name + ": memory segment must have same length:" +
                                       " gold[" + i + "].length = " + mg.byteSize() +
                                       " result[" + i + "].length = " + mr.byteSize());
            }
            verifyMS(name, i, mg, mr);
        }
    }

    static void verifyMS(String name, int i, MemorySegment g, MemorySegment r) {
        for (long j = 0; j < g.byteSize(); j++) {
            byte vg = g.get(ValueLayout.JAVA_BYTE, j);
            byte vr = r.get(ValueLayout.JAVA_BYTE, j);
            if (vg != vr) {
                throw new RuntimeException("verify " + name + ": arrays must have same content:" +
                                           " gold[" + i + "][" + j + "] = " + vg +
                                           " result[" + i + "][" + j + "] = " + vr);
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
    // Same int invariant summands, but added in a different order.
    static Object[] testArrayBBInvar3(byte[] a, byte[] b, int invar1, int invar2, int invar3) {
        int i1 = invar1 + invar2 + invar3;
        int i2 = invar2 + invar3 + invar1;
        for (int i = 0; i < a.length; i++) {
            b[i + i1] = (byte)(a[i + i2] + 1);
        }
        return new Object[]{ a, b };
    }

    // TODO: add some unsafe cases, and some other types than byte!

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Just a simple pattern, without any (explicit) invariant.
    static Object[] testMemorySegmentB(MemorySegment m) {
        for (int i = 0; i < (int)m.byteSize(); i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i);
            m.set(ValueLayout.JAVA_BYTE, i, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Does not vectorize: RangeChecks are not eliminated.
    static Object[] testMemorySegmentBInvarI(MemorySegment m, int invar, int size) {
        for (int i = 0; i < size; i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i + invar);
            m.set(ValueLayout.JAVA_BYTE, i + invar, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Has different invariants, before sorting:
    //
    //   3125 AddL = ((CastLL(Param 11) + ConvI2L(1460  Phi)) + 530  LoadL)
    //   3127 AddL = (ConvI2L(1460  Phi) + (11 Param + 530  LoadL))
    //
    static Object[] testMemorySegmentBInvarL(MemorySegment m, long invar, int size) {
        for (int i = 0; i < size; i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i + invar);
            m.set(ValueLayout.JAVA_BYTE, i + invar, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Does not vectorize: RangeChecks are not eliminated.
    static Object[] testMemorySegmentBInvarIAdr(MemorySegment m, int invar, int size) {
        for (int i = 0; i < size; i++) {
            long adr = i + invar;
            byte v = m.get(ValueLayout.JAVA_BYTE, adr);
            m.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Since we add "i + invar", the invariant is already equivalent without sorting.
    static Object[] testMemorySegmentBInvarLAdr(MemorySegment m, long invar, int size) {
        for (int i = 0; i < size; i++) {
            long adr = i + invar;
            byte v = m.get(ValueLayout.JAVA_BYTE, adr);
            m.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentBInvarI3a(MemorySegment m, int invar1, int invar2, int invar3, int size) {
        long i1 = (long)(invar1 + invar2 + invar3);
        long i2 = (long)(invar2 + invar3 + invar1); // equivalent
        for (int i = 0; i < size; i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i + i1);
            m.set(ValueLayout.JAVA_BYTE, i + i2, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentBInvarI3b(MemorySegment m, int invar1, int invar2, int invar3, int size) {
        long i1 = (long)(invar1 + invar2 + invar3);
        long i2 = (long)(invar2 + invar3 + invar1); // equivalent
        for (int i = 0; i < size; i+=2) {
            byte v0 = m.get(ValueLayout.JAVA_BYTE, i + i1 + 0);
            byte v1 = m.get(ValueLayout.JAVA_BYTE, i + i2 + 1);
            m.set(ValueLayout.JAVA_BYTE, i + i1 + 0, (byte)(v0 + 1));
            m.set(ValueLayout.JAVA_BYTE, i + i2 + 1, (byte)(v1 + 1));
        }
        return new Object[]{ m };
    }

    @Test
    static Object[] testMemorySegmentBInvarI3c(MemorySegment m, int invar1, int invar2, int invar3, int size) {
        long i1 = (long)(invar1 + invar2 + invar3);
        long i2 = (long)(invar2 + invar3) + (long)(invar1); // not equivalent!
        for (int i = 0; i < size; i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i + i1);
            m.set(ValueLayout.JAVA_BYTE, i + i2, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Should never vectorize, because "adjacent" looking loads and stores might not be adjacent if there is an int-overflow!
    static Object[] testMemorySegmentBInvarI3d(MemorySegment m, int invar1, int invar2, int invar3, int size) {
        long i1 = (long)(invar1 + invar2 + invar3);
        long i2 = (long)(invar2 + invar3) + (long)(invar1); // not equivalent!
        // invar1 = invar2 = minint
        // invar3 = 0
        // i1 = 0           -> int-overflow, before ConvI2L.
        // i2 = 2 * minint  -> first ConvI2L, then no overflow.
        for (int i = 0; i < size; i+=2) {
            byte v0 = m.get(ValueLayout.JAVA_BYTE, i + i1 + 0);
            byte v1 = m.get(ValueLayout.JAVA_BYTE, i + i2 + 1);
            m.set(ValueLayout.JAVA_BYTE, i + i1 + 0, (byte)(v0 + 1));
            m.set(ValueLayout.JAVA_BYTE, i + i2 + 1, (byte)(v1 + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentBInvarI3e(MemorySegment m, int invar1, int invar2, int invar3, int size) {
        long i1 = (long)(invar1 + invar2 - invar3);
        long i2 = (long)(invar2 - invar3 + invar1); // equivalent
        for (int i = 0; i < size; i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i + i1);
            m.set(ValueLayout.JAVA_BYTE, i + i2, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentBInvarI3f(MemorySegment m, int invar1, int invar2, int invar3, int size) {
        long i1 = (long)(invar1 - (invar2 - invar3));
        long i2 = (long)(-invar2 + invar3 + invar1); // equivalent
        for (int i = 0; i < size; i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i + i1);
            m.set(ValueLayout.JAVA_BYTE, i + i2, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentBInvarL3g(MemorySegment m, long invar1, long invar2, long invar3, int size) {
        long i1 = invar1 - (invar2 - invar3);
        long i2 = -invar2 + invar3 + invar1; // equivalent
        for (int i = 0; i < size; i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i + i1);
            m.set(ValueLayout.JAVA_BYTE, i + i2, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentBInvarL3h(MemorySegment m, long invar1, long invar2, long invar3, int size) {
        long i1 = -invar1 - invar2 - invar3;
        long i2 = -invar2 - invar3 - invar1; // equivalent
        for (int i = 0; i < size; i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i + i1);
            m.set(ValueLayout.JAVA_BYTE, i + i2, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentBInvarL3k(MemorySegment m, long invar1, long invar2, long invar3, int size) {
        long i1 = -invar1 + invar2 + invar3;
        long i2 = invar2 + invar3 - invar1; // equivalent
        for (int i = 0; i < size; i++) {
            byte v = m.get(ValueLayout.JAVA_BYTE, i + i1);
            m.set(ValueLayout.JAVA_BYTE, i + i2, (byte)(v + 1));
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentIInvarL3a(MemorySegment m, long invar1, long invar2, long invar3, int size) {
        long i1 = invar1 + invar2 + invar3;
        long i2 = invar2 + invar3 + invar1; // equivalent
        for (int i = 0; i < size; i++) {
            int v = m.getAtIndex(ValueLayout.JAVA_INT, i + i1);
            m.setAtIndex(ValueLayout.JAVA_INT, i + i2, v + 1);
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentIInvarL3b(MemorySegment m, long invar1, long invar2, long invar3, int size) {
        long i1 = -invar1 - invar2 - invar3;
        long i2 = -invar2 - invar3 - invar1; // equivalent
        for (int i = 0; i < size; i++) {
            int v = m.getAtIndex(ValueLayout.JAVA_INT, i + i1);
            m.setAtIndex(ValueLayout.JAVA_INT, i + i2, v + 1);
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentIInvarL3c(MemorySegment m, long invar1, long invar2, long invar3, int size) {
        long i1 = -invar1 + invar2 + invar3;
        long i2 = invar2 + invar3 - invar1; // equivalent
        for (int i = 0; i < size; i++) {
            int v = m.getAtIndex(ValueLayout.JAVA_INT, i + i1);
            m.setAtIndex(ValueLayout.JAVA_INT, i + i2, v + 1);
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Would be nice if it vectorized.
    // Fails because of control flow. Somehow the "offsetPlain" check (checks for alignment) is not folded away.
    static Object[] testMemorySegmentIInvarL3d(MemorySegment m, int invar1, int invar2, int invar3, int size) {
        long i1 = (long)(-invar1 + invar2 + invar3);
        long i2 = (long)(invar2 + invar3 - invar1); // equivalent
        for (int i = 0; i < size; i+=2) {
            int v0 = m.getAtIndex(ValueLayout.JAVA_INT, i + i1 + 0);
            int v1 = m.getAtIndex(ValueLayout.JAVA_INT, i + i2 + 1);
            m.setAtIndex(ValueLayout.JAVA_INT, i + i1 + 0, v0 + 1);
            m.setAtIndex(ValueLayout.JAVA_INT, i + i2 + 1, v1 + 1);
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Should never vectorize, since i1 and i2 are not guaranteed to be adjacent
    static Object[] testMemorySegmentIInvarL3e(MemorySegment m, int invar1, int invar2, int invar3, int size) {
        long i1 = (long)(-invar1 + invar2 + invar3);
        long i2 = (long)(invar2 + invar3) - (long)(invar1); // not equivalent
        for (int i = 0; i < size; i+=2) {
            int v0 = m.getAtIndex(ValueLayout.JAVA_INT, i + i1 + 0);
            int v1 = m.getAtIndex(ValueLayout.JAVA_INT, i + i2 + 1);
            m.setAtIndex(ValueLayout.JAVA_INT, i + i1 + 0, v0 + 1);
            m.setAtIndex(ValueLayout.JAVA_INT, i + i2 + 1, v1 + 1);
        }
        return new Object[]{ m };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testMemorySegmentIInvarL3f(MemorySegment m, long invar1, long invar2, long invar3, int size) {
        long i1 = -invar1 + invar2 + invar3;
        long i2 = invar2 + invar3 - invar1; // equivalent
        for (int i = 0; i < size; i++) {
            // Scale the index manually
            int v = m.get(ValueLayout.JAVA_INT, 4 * (i + i1));
            m.set(ValueLayout.JAVA_INT, 4 * (i + i2), v + 1);
        }
        return new Object[]{ m };
    }
}
