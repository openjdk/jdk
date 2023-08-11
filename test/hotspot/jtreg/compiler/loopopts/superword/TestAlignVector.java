/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8310190
 * @summary Test AlignVector with many scenarios
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.loopopts.superword.TestAlignVector
 */

public class TestAlignVector {
    static int RANGE = 1024*64;
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Random RANDOM = Utils.getRandomInstance();

    static int count = 0;

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                                   "-XX:LoopUnrollLimit=10000");
    }

    interface TestFunction {
        Object[] run();
    }

    @Warmup(0)
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
                 "test10a",
                 "test10b",
                 "test10c",
                 "test10d",
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
                 "test15aB",
                 "test15bB",
                 "test15cB",
//                 "test16a",
//                 "test16b",
                 "test17a",
                 "test17b",
                 "test17c",
                 "test17d"})
    public void runTests() {
        byte[] aB = generateB();
        byte[] bB = generateB();
        byte mB = (byte)31;
        short[] aS = generateS();
        short[] bS = generateS();
        short mS = (short)0xF0F0;

        Map<String,TestFunction> tests = new HashMap<String,TestFunction>();
        tests.put("test0",    () -> { return test0(aB.clone(), bB.clone(), mB); });
        tests.put("test1",    () -> { return test1(aB.clone(), bB.clone(), mB); });
        tests.put("test2",    () -> { return test2(aB.clone(), bB.clone(), mB); });
        tests.put("test3",    () -> { return test3(aB.clone(), bB.clone(), mB); });
        tests.put("test4",    () -> { return test4(aB.clone(), bB.clone(), mB); });
        tests.put("test5",    () -> { return test5(aB.clone(), bB.clone(), mB, 0); });
        tests.put("test6",    () -> { return test6(aB.clone(), bB.clone(), mB); });
        tests.put("test7",    () -> { return test7(aS.clone(), bS.clone(), mS); });
        tests.put("test8",    () -> { return test8(aB.clone(), bB.clone(), mB, 0); });
        tests.put("test8",    () -> { return test8(aB.clone(), bB.clone(), mB, 1); });
        tests.put("test9",    () -> { return test9(aB.clone(), bB.clone(), mB); });

	//tests.put("test0",    () -> { return test0(aB.clone(), bB.clone(), mB); });

        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            Object[] gold = test.run();
            for (int i = 0; i < 10; i++) {
                Object[] result = test.run();
                verify(name, gold, result);
            }
        }
    }

    static byte[] generateB() {
        byte[] a = new byte[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)RANDOM.nextInt(Byte.MAX_VALUE);
        }
        return a;
    }

    static short[] generateS() {
        short[] a = new short[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = (short)RANDOM.nextInt(Short.MAX_VALUE);
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
	    //} else if (c == int.class) {
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

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] test1(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE; i+=8) {
            // Safe to vectorize with AlignVector
            b[i+0] = (byte)(a[i+0] & mask); // offset 0, align 0
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
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0",
                  IRNode.AND_V, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
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
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0",
                  IRNode.AND_V, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
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
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0",
                  IRNode.AND_V, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
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
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0",
                  IRNode.AND_V, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
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
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0",
                  IRNode.AND_V, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
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
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AlignVector", "false"})
    @IR(counts = {IRNode.LOAD_VECTOR, "= 0",
                  IRNode.AND_V, "= 0",
                  IRNode.STORE_VECTOR, "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
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
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0",
                  IRNode.AND_V, "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
    static Object[] test10d(short[] a, short[] b, short mask) {
        for (int i = 13; i < RANGE-16; i+=8) {
            // init + offset -> aligned
            b[i+0+3] = (short)(a[i+0+3] & mask);
            b[i+1+3] = (short)(a[i+1+3] & mask);
            b[i+2+3] = (short)(a[i+2+3] & mask);
            b[i+3+3] = (short)(a[i+3+3] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11aB(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0] = (byte)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11aS(short[] a, short[] b, short mask) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0] = (short)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11aI(int[] a, int[] b, int mask) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0] = (int)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11aL(long[] a, long[] b, long mask) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0] = (long)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11bB(byte[] a, byte[] b, byte mask) {
        for (int i = 1; i < RANGE; i++) {
            b[i+0] = (byte)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11bS(short[] a, short[] b, short mask) {
        for (int i = 1; i < RANGE; i++) {
            b[i+0] = (short)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11bI(int[] a, int[] b, int mask) {
        for (int i = 1; i < RANGE; i++) {
            b[i+0] = (int)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11bL(long[] a, long[] b, long mask) {
        for (int i = 1; i < RANGE; i++) {
            b[i+0] = (long)(a[i+0] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11cB(byte[] a, byte[] b, byte mask) {
        for (int i = 1; i < RANGE-1; i++) {
            b[i+0] = (byte)(a[i+1] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11cS(short[] a, short[] b, short mask) {
        for (int i = 1; i < RANGE-1; i++) {
            b[i+0] = (short)(a[i+1] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11cI(int[] a, int[] b, int mask) {
        for (int i = 1; i < RANGE-1; i++) {
            b[i+0] = (int)(a[i+1] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11cL(long[] a, long[] b, long mask) {
        for (int i = 1; i < RANGE-1; i++) {
            b[i+0] = (long)(a[i+1] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11dB(byte[] a, byte[] b, byte mask, int invar) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0+invar] = (byte)(a[i+0+invar] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11dS(short[] a, short[] b, short mask, int invar) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0+invar] = (short)(a[i+0+invar] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11dI(int[] a, int[] b, int mask, int invar) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0+invar] = (int)(a[i+0+invar] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test11dL(long[] a, long[] b, long mask, int invar) {
        for (int i = 0; i < RANGE; i++) {
            b[i+0+invar] = (long)(a[i+0+invar] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test12(byte[] a, byte[] b, byte mask) {
        for (int i = 0; i < RANGE/16; i++) {
            b[i*6 + 0 ] = (byte)(a[i*6 + 0 ] & mask);
            b[i*6 + 1 ] = (byte)(a[i*6 + 1 ] & mask);
            b[i*6 + 2 ] = (byte)(a[i*6 + 2 ] & mask);
            b[i*6 + 3 ] = (byte)(a[i*6 + 3 ] & mask);
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test13aIL(int[] a, long[] b) {
        for (int i = 0; i < RANGE; i++) {
            a[i]++;
            b[i]++;
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test13aIB(int[] a, byte[] b) {
        for (int i = 0; i < RANGE; i++) {
            a[i]++;
            b[i]++;
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test13aIS(int[] a, short[] b) {
        for (int i = 0; i < RANGE; i++) {
            a[i]++;
            b[i]++;
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test13aBSIL(byte[] a, short[] b, int[] c, long[] d) {
        for (int i = 0; i < RANGE; i++) {
            a[i]++;
            b[i]++;
            c[i]++;
            d[i]++;
        }
        return new Object[]{ a, b, c, d };
    }

    @Test
    static Object[] test13bIL(int[] a, long[] b) {
        for (int i = 1; i < RANGE; i++) {
            a[i]++;
            b[i]++;
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test13bIB(int[] a, byte[] b) {
        for (int i = 1; i < RANGE; i++) {
            a[i]++;
            b[i]++;
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test13bIS(int[] a, short[] b) {
        for (int i = 1; i < RANGE; i++) {
            a[i]++;
            b[i]++;
        }
        return new Object[]{ a, b };
    }

    @Test
    static Object[] test13bBSIL(byte[] a, short[] b, int[] c, long[] d) {
        for (int i = 1; i < RANGE; i++) {
            a[i]++;
            b[i]++;
            c[i]++;
            d[i]++;
        }
        return new Object[]{ a, b, c, d };
    }

    @Test
    static Object[] test14aB(byte[] a) {
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
    static Object[] test14bB(byte[] a) {
        // non-power-of-2 stride
        for (int i = 0; i < RANGE-20; i+=3) {
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
    static Object[] test14cB(byte[] a) {
        // non-power-of-2 stride
        for (int i = 0; i < RANGE-20; i+=5) {
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
    static Object[] test15cB(byte[] a) {
        // non-power-of-2 scale
        for (int i = 0; i < RANGE/64-20; i++) {
            a[11*i+0]++;
            a[11*i+1]++;
            a[11*i+2]++;
            a[11*i+3]++;
            a[11*i+4]++;
            a[11*i+5]++;
            a[11*i+6]++;
            a[11*i+7]++;
            a[11*i+8]++;
            a[11*i+9]++;
            a[11*i+10]++;
            a[11*i+11]++;
            a[11*i+12]++;
            a[11*i+13]++;
            a[11*i+14]++;
            a[11*i+15]++;
	}
        return new Object[]{ a };
    }

// TODO add after fixing 8313717
//
//    @Test
//    static Object[] test16a(byte[] a, short[] b) {
//        // infinite loop issues
//        for (int i = 0; i < RANGE/2-20; i++) {
//            a[2*i+0]++;
//            a[2*i+1]++;
//            a[2*i+2]++;
//            a[2*i+3]++;
//            a[2*i+4]++;
//            a[2*i+5]++;
//            a[2*i+6]++;
//            a[2*i+7]++;
//            a[2*i+8]++;
//            a[2*i+9]++;
//            a[2*i+10]++;
//            a[2*i+11]++;
//            a[2*i+12]++;
//            a[2*i+13]++;
//            a[2*i+14]++;
//
//            b[2*i+0]++;
//            b[2*i+1]++;
//            b[2*i+2]++;
//            b[2*i+3]++;
//        }
//        return new Object[]{ a, b };
//    }
//
//    @Test
//    static Object[] test16b(byte[] a) {
//        // infinite loop issues
//        for (int i = 0; i < RANGE/2-20; i++) {
//            a[2*i+0]++;
//            a[2*i+1]++;
//            a[2*i+2]++;
//            a[2*i+3]++;
//            a[2*i+4]++;
//            a[2*i+5]++;
//            a[2*i+6]++;
//            a[2*i+7]++;
//            a[2*i+8]++;
//            a[2*i+9]++;
//            a[2*i+10]++;
//            a[2*i+11]++;
//            a[2*i+12]++;
//            a[2*i+13]++;
//            a[2*i+14]++;
//        }
//        return new Object[]{ a };
//    }

    @Test
    static Object[] test17a(long[] a) {
        // Unsafe: vectorizes with profiling (not xcomp)
        for (int i = 0; i < RANGE; i++) {
            int adr = UNSAFE.ARRAY_LONG_BASE_OFFSET + 8 * i;
            long v = UNSAFE.getLongUnaligned(a, adr);
            UNSAFE.putLongUnaligned(a, adr, v + 1);
        }
        return new Object[]{ a };
    }

    @Test
    static Object[] test17b(long[] a) {
        // Not alignable
        for (int i = 0; i < RANGE-1; i++) {
            int adr = UNSAFE.ARRAY_LONG_BASE_OFFSET + 8 * i + 1;
            long v = UNSAFE.getLongUnaligned(a, adr);
            UNSAFE.putLongUnaligned(a, adr, v + 1);
        }
        return new Object[]{ a };
    }

    @Test
    static Object[] test17c(long[] a) {
        // Unsafe: aligned vectorizes
        for (int i = 0; i < RANGE-1; i+=4) {
            int adr = UNSAFE.ARRAY_LONG_BASE_OFFSET + 8 * i;
            long v0 = UNSAFE.getLongUnaligned(a, adr + 0);
            long v1 = UNSAFE.getLongUnaligned(a, adr + 8);
            UNSAFE.putLongUnaligned(a, adr + 0, v0 + 1);
            UNSAFE.putLongUnaligned(a, adr + 8, v1 + 1);
        }
        return new Object[]{ a };
    }

    @Test
    static Object[] test17d(long[] a) {
        // Not alignable
        for (int i = 0; i < RANGE-1; i+=4) {
            int adr = UNSAFE.ARRAY_LONG_BASE_OFFSET + 8 * i + 1;
            long v0 = UNSAFE.getLongUnaligned(a, adr + 0);
            long v1 = UNSAFE.getLongUnaligned(a, adr + 8);
            UNSAFE.putLongUnaligned(a, adr + 0, v0 + 1);
            UNSAFE.putLongUnaligned(a, adr + 8, v1 + 1);
        }
        return new Object[]{ a };
    }
}
