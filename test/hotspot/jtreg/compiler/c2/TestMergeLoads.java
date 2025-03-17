/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All rights reserved.
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
 * @summary Test merging of consecutive loads
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 *
 * @run main compiler.c2.TestMergeLoads unaligned
 * @run main compiler.c2.TestMergeLoads aligned
 *
 * @requires os.arch != "riscv64" | vm.cpu.features ~= ".*zbb.*"
 */

public class TestMergeLoads {
    static int RANGE = 1000;
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Random RANDOM = Utils.getRandomInstance();

    // Inputs
    byte[] aB = new byte[RANGE];

    interface TestFunction {
        Object[] run(boolean isWarmUp, int rnd);
    }

    Map<String, Map<String, TestFunction>> testGroups = new HashMap<String, Map<String, TestFunction>>();

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMergeLoads.class);
        framework.addFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");

        switch (args[0]) {
            case "aligned"     -> { framework.addFlags("-XX:-UseUnalignedAccesses"); }
            case "unaligned"   -> { framework.addFlags("-XX:+UseUnalignedAccesses"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        }
        framework.start();
    }

    public TestMergeLoads() {
        // Get int in little endian
        testGroups.put("test1", new HashMap<String,TestFunction>());
        testGroups.get("test1").put("test1R", (_,_) -> { return test1R(aB.clone()); });
        testGroups.get("test1").put("test1a", (_,_) -> { return test1a(aB.clone()); });
        testGroups.get("test1").put("test1b", (_,_) -> { return test1b(aB.clone()); });
        testGroups.get("test1").put("test1c", (_,_) -> { return test1c(aB.clone()); });

        // Get long in little endian
        testGroups.put("test2", new HashMap<String,TestFunction>());
        testGroups.get("test2").put("test2R", (_,_) -> { return test2R(aB.clone()); });
        testGroups.get("test2").put("test2a", (_,_) -> { return test2a(aB.clone()); });
        testGroups.get("test2").put("test2b", (_,_) -> { return test2b(aB.clone()); });
        testGroups.get("test2").put("test2c", (_,_) -> { return test2c(aB.clone()); });

        // Get int in big endian
        testGroups.put("test3", new HashMap<String,TestFunction>());
        testGroups.get("test3").put("test3R", (_,_) -> { return test3R(aB.clone()); });
        testGroups.get("test3").put("test3a", (_,_) -> { return test3a(aB.clone()); });
        testGroups.get("test3").put("test3b", (_,_) -> { return test3b(aB.clone()); });
        testGroups.get("test3").put("test3c", (_,_) -> { return test3c(aB.clone()); });

        // Get long in big endian
        testGroups.put("test4", new HashMap<String,TestFunction>());
        testGroups.get("test4").put("test4R", (_,_) -> { return test4R(aB.clone()); });
        testGroups.get("test4").put("test4a", (_,_) -> { return test4a(aB.clone()); });
        testGroups.get("test4").put("test4b", (_,_) -> { return test4b(aB.clone()); });
        testGroups.get("test4").put("test4c", (_,_) -> { return test4c(aB.clone()); });
    }

    static void set_random(byte[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)RANDOM.nextInt();
        }
    }

    @Warmup(100)
    @Run(test = {"test1a",
                 "test1b",
                 "test1c",
                 "test2a",
                 "test2b",
                 "test2c",
                 "test3a",
                 "test3b",
                 "test3c",
                 "test4a",
                 "test4b",
                 "test4c"
                })
    public void runTests(RunInfo info) {
        // Repeat many times, so that we also have multiple iterations for post-warmup to potentially recompile
        int iters = info.isWarmUp() ? 1_000 : 50_000;
        for (int iter = 0; iter < iters; iter++) {
            // Write random values to inputs
            set_random(aB);

            // Run all tests
            for (Map.Entry<String, Map<String,TestFunction>> group_entry : testGroups.entrySet()) {
                String group_name = group_entry.getKey();
                Map<String, TestFunction> group = group_entry.getValue();
                Object[] gold = null;
                String gold_name = "NONE";
                for (Map.Entry<String,TestFunction> entry : group.entrySet()) {
                    String name = entry.getKey();
                    TestFunction test = entry.getValue();
                    Object[] result = test.run(info.isWarmUp(), iter);
                    if (gold == null) {
                        gold = result;
                        gold_name = name;
                    } else {
                        verify("group " + group_name + ", gold " + gold_name + ", test " + name, gold, result);
                    }
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
                                           " = " + String.format("%02X", g[j] & 0xFF) +
                                           " result[" + i + "][" + j + "] = " + r[j] +
                                           " = " + String.format("%02X", r[j] & 0xFF));
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

    /**
     * Group 1
     *   get int in little endian mode
     */
    @DontCompile
    static Object[] test1R(byte[] a) {
      int i1 = (a[0] & 0xff)         |
              ((a[1] & 0xff) << 8 )  |
              ((a[2] & 0xff) << 16)  |
              ((a[3] & 0xff) << 24);
      int[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.REVERSE_BYTES_I, "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test1a(byte[] a) {
      int i1 = (a[0] & 0xff)         |
              ((a[1] & 0xff) << 8 )  |
              ((a[2] & 0xff) << 16)  |
              ((a[3] & 0xff) << 24);
      int[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test1b(byte[] a) {
      int i1 = UNSAFE.getIntUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, /* big-endian */ false);
      int[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.REVERSE_BYTES_I, "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test1c(byte[] a) {
      int i1 =  (UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)        |
               ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 8 ) |
               ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) << 16) |
               ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff) << 24);
      int[] ret = {i1};
      return new Object[]{ret};
    }

    /**
     * Group 2
     *   get long in little endian mode
     */
    @DontCompile
    static Object[] test2R(byte[] a) {
      long i1 =  ((long)(a[0] & 0xff)       )|
                (((long)(a[1] & 0xff)) << 8 )|
                (((long)(a[2] & 0xff)) << 16)|
                (((long)(a[3] & 0xff)) << 24)|
                (((long)(a[4] & 0xff)) << 32)|
                (((long)(a[5] & 0xff)) << 40)|
                (((long)(a[6] & 0xff)) << 48)|
                (((long)(a[7] & 0xff)) << 56);
      long[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.REVERSE_BYTES_L, "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"big-endian", "true"})
    static Object[] test2a(byte[] a) {
      long i1 =  ((long)(a[0] & 0xff)       )|
                (((long)(a[1] & 0xff)) << 8 )|
                (((long)(a[2] & 0xff)) << 16)|
                (((long)(a[3] & 0xff)) << 24)|
                (((long)(a[4] & 0xff)) << 32)|
                (((long)(a[5] & 0xff)) << 40)|
                (((long)(a[6] & 0xff)) << 48)|
                (((long)(a[7] & 0xff)) << 56);
      long[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test2b(byte[] a) {
      long i1 = UNSAFE.getLongUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, /* big-endian */ false);
      long[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.REVERSE_BYTES_L, "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test2c(byte[] a) {
      long i1 = ((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)       )|
               (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff)) << 8 )|
               (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff)) << 16)|
               (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)) << 24)|
               (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xff)) << 32)|
               (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5) & 0xff)) << 40)|
               (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xff)) << 48)|
               (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7) & 0xff)) << 56);
      long[] ret = {i1};
      return new Object[]{ret};
    }

    /**
     * Group 3
     *   get int in big endian mode
     */
    @DontCompile
    static Object[] test3R(byte[] a) {
      int i1 = ((a[0] & 0xff) << 24) |
               ((a[1] & 0xff) << 16) |
               ((a[2] & 0xff) <<  8) |
                (a[3] & 0xff);
      int[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.REVERSE_BYTES_I, "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test3a(byte[] a) {
      int i1 = ((a[0] & 0xff) << 24) |
               ((a[1] & 0xff) << 16) |
               ((a[2] & 0xff) <<  8) |
                (a[3] & 0xff);
      int[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test3b(byte[] a) {
      int i1 = UNSAFE.getIntUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, /* big-endian */ true);
      int[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.REVERSE_BYTES_I, "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {IRNode.LOAD_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test3c(byte[] a) {
      int i1 = ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff) << 24) |
               ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 16) |
               ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) <<  8) |
                (UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff);
      int[] ret = {i1};
      return new Object[]{ret};
    }

    /**
     * Group 4
     *   get long in big endian mode
     */
    @DontCompile
    static Object[] test4R(byte[] a) {
      long i1 = (((long)(a[0] & 0xff)) << 56)|
                (((long)(a[1] & 0xff)) << 48)|
                (((long)(a[2] & 0xff)) << 40)|
                (((long)(a[3] & 0xff)) << 32)|
                (((long)(a[4] & 0xff)) << 24)|
                (((long)(a[5] & 0xff)) << 16)|
                (((long)(a[6] & 0xff)) <<  8)|
                 ((long)(a[7] & 0xff));
      long[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.REVERSE_BYTES_L, "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"big-endian", "true"})
    static Object[] test4a(byte[] a) {
      long i1 = (((long)(a[0] & 0xff)) << 56)|
                (((long)(a[1] & 0xff)) << 48)|
                (((long)(a[2] & 0xff)) << 40)|
                (((long)(a[3] & 0xff)) << 32)|
                (((long)(a[4] & 0xff)) << 24)|
                (((long)(a[5] & 0xff)) << 16)|
                (((long)(a[6] & 0xff)) <<  8)|
                 ((long)(a[7] & 0xff));
      long[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test4b(byte[] a) {
      long i1 = UNSAFE.getLongUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, /* big-endian */ true);
      long[] ret = {i1};
      return new Object[]{ret};
    }

    @Test
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.REVERSE_BYTES_L, "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {IRNode.LOAD_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test4c(byte[] a) {
      long i1 = (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)) << 56)|
                (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff)) << 48)|
                (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff)) << 40)|
                (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)) << 32)|
                (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xff)) << 24)|
                (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5) & 0xff)) << 16)|
                (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xff)) <<  8)|
                 ((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7) & 0xff));
      long[] ret = {i1};
      return new Object[]{ret};
    }
}
