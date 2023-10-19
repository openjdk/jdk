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

package compiler.c2;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import jdk.internal.misc.Unsafe;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

/*
 * @test
 * @bug 8318446
 * @summary Test merging of consecutive stores
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStores
 */

public class TestMergeStores {
    static int RANGE = 1000;
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Random RANDOM = Utils.getRandomInstance();

    // Inputs
    byte[] aB = new byte[RANGE];
    byte[] bB = new byte[RANGE];
    short[] aS = new short[RANGE];
    short[] bS = new short[RANGE];
    int[] aI = new int[RANGE];
    int[] bI = new int[RANGE];
    long[] aL = new long[RANGE];
    long[] bL = new long[RANGE];

    interface TestFunction {
        Object[] run();
    }

    Map<String, Map<String, TestFunction>> test_groups = new HashMap<String, Map<String, TestFunction>>();

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules", "java.base",
                                   "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
    }

    public TestMergeStores() {
        test_groups.put("test1", new HashMap<String,TestFunction>());
        test_groups.get("test1").put("test1R", () -> { return test1R(aB.clone()); });
        test_groups.get("test1").put("test1a", () -> { return test1a(aB.clone()); });
        test_groups.get("test1").put("test1b", () -> { return test1b(aB.clone()); });
    }

    @Run(test = {"test1a",
                 //"test1b",
                 "test1b"})
    public void runTests() {
        // Write random values to inputs
        set_random(aB);
        set_random(bB);
        set_random(aS);
        set_random(bS);
        set_random(aI);
        set_random(bI);
        set_random(aL);
        set_random(bL);

        // Run all tests
        for (Map.Entry<String, Map<String,TestFunction>> group_entry : test_groups.entrySet()) {
            String group_name = group_entry.getKey();
            Map<String, TestFunction> group = group_entry.getValue();
            Object[] gold = null;
            String gold_name = "NONE";
            for (Map.Entry<String,TestFunction> entry : group.entrySet()) {
                String name = entry.getKey();
                TestFunction test = entry.getValue();
                Object[] result = test.run();
                if (gold == null) {
                    gold = result;
                    gold_name = name;
                } else {
                    verify("group " + group_name + ", gold " + gold_name + ", test " + name, gold, result);
                }
            }
	}
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

    static void set_random(byte[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)RANDOM.nextInt();
        }
    }

    static void set_random(short[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (short)RANDOM.nextInt();
        }
    }

    static void set_random(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = RANDOM.nextInt();
        }
    }

    static void set_random(long[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = RANDOM.nextLong();
        }
    }

    @DontCompile
    static Object[] test1R(byte[] a) {
        a[0] = (byte)0xbe;
        a[1] = (byte)0xba;
        a[2] = (byte)0xad;
        a[3] = (byte)0xba;
        a[4] = (byte)0xef;
        a[5] = (byte)0xbe;
        a[6] = (byte)0xad;
        a[7] = (byte)0xde;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"})
    static Object[] test1a(byte[] a) {
        a[0] = (byte)0xbe;
        a[1] = (byte)0xba;
        a[2] = (byte)0xad;
        a[3] = (byte)0xba;
        a[4] = (byte)0xef;
        a[5] = (byte)0xbe;
        a[6] = (byte)0xad;
        a[7] = (byte)0xde;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"})
    static Object[] test1b(byte[] a) {
        UNSAFE.putLongUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, 0xdeadbeefbaadbabeL);
        return new Object[]{ a };
    }
}
