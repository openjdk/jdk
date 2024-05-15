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
 * @bug 8318446 8331054 8331311
 * @summary Test merging of consecutive stores
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main compiler.c2.TestMergeStores aligned
 */

/*
 * @test
 * @bug 8318446 8331054 8331311
 * @summary Test merging of consecutive stores
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main compiler.c2.TestMergeStores unaligned
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

    int offset1;
    int offset2;
    byte vB1;
    byte vB2;
    short vS1;
    short vS2;
    int vI1;
    int vI2;
    long vL1;
    long vL2;

    interface TestFunction {
        Object[] run(boolean isWarmUp, int rnd);
    }

    Map<String, Map<String, TestFunction>> testGroups = new HashMap<String, Map<String, TestFunction>>();

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMergeStores.class);
        framework.addFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");

        switch (args[0]) {
            case "aligned"     -> { framework.addFlags("-XX:-UseUnalignedAccesses"); }
            case "unaligned"   -> { framework.addFlags("-XX:+UseUnalignedAccesses"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        }
        framework.start();
    }

    public TestMergeStores() {
        testGroups.put("test1", new HashMap<String,TestFunction>());
        testGroups.get("test1").put("test1R", (_,_) -> { return test1R(aB.clone()); });
        testGroups.get("test1").put("test1a", (_,_) -> { return test1a(aB.clone()); });
        testGroups.get("test1").put("test1b", (_,_) -> { return test1b(aB.clone()); });
        testGroups.get("test1").put("test1c", (_,_) -> { return test1c(aB.clone()); });
        testGroups.get("test1").put("test1d", (_,_) -> { return test1d(aB.clone()); });
        testGroups.get("test1").put("test1e", (_,_) -> { return test1e(aB.clone()); });
        testGroups.get("test1").put("test1f", (_,_) -> { return test1f(aB.clone()); });
        testGroups.get("test1").put("test1g", (_,_) -> { return test1g(aB.clone()); });
        testGroups.get("test1").put("test1h", (_,_) -> { return test1h(aB.clone()); });
        testGroups.get("test1").put("test1i", (_,_) -> { return test1i(aB.clone()); });

        testGroups.put("test2", new HashMap<String,TestFunction>());
        testGroups.get("test2").put("test2R", (_,_) -> { return test2R(aB.clone(), offset1, vL1); });
        testGroups.get("test2").put("test2a", (_,_) -> { return test2a(aB.clone(), offset1, vL1); });
        testGroups.get("test2").put("test2b", (_,_) -> { return test2b(aB.clone(), offset1, vL1); });
        testGroups.get("test2").put("test2c", (_,_) -> { return test2c(aB.clone(), offset1, vL1); });
        testGroups.get("test2").put("test2d", (_,_) -> { return test2d(aB.clone(), offset1, vL1); });
        testGroups.get("test2").put("test2e", (_,_) -> { return test2e(aB.clone(), offset1, vL1); });

        testGroups.put("test2BE", new HashMap<String,TestFunction>());
        testGroups.get("test2BE").put("test2RBE", (_,_) -> { return test2RBE(aB.clone(), offset1, vL1); });
        testGroups.get("test2BE").put("test2aBE", (_,_) -> { return test2aBE(aB.clone(), offset1, vL1); });
        testGroups.get("test2BE").put("test2bBE", (_,_) -> { return test2bBE(aB.clone(), offset1, vL1); });
        testGroups.get("test2BE").put("test2cBE", (_,_) -> { return test2cBE(aB.clone(), offset1, vL1); });
        testGroups.get("test2BE").put("test2dBE", (_,_) -> { return test2dBE(aB.clone(), offset1, vL1); });
        testGroups.get("test2BE").put("test2eBE", (_,_) -> { return test2eBE(aB.clone(), offset1, vL1); });

        testGroups.put("test3", new HashMap<String,TestFunction>());
        testGroups.get("test3").put("test3R", (_,_) -> { return test3R(aB.clone(), offset1, vL1); });
        testGroups.get("test3").put("test3a", (_,_) -> { return test3a(aB.clone(), offset1, vL1); });

        testGroups.put("test3BE", new HashMap<String,TestFunction>());
        testGroups.get("test3BE").put("test3RBE", (_,_) -> { return test3RBE(aB.clone(), offset1, vL1); });
        testGroups.get("test3BE").put("test3aBE", (_,_) -> { return test3aBE(aB.clone(), offset1, vL1); });

        testGroups.put("test4", new HashMap<String,TestFunction>());
        testGroups.get("test4").put("test4R", (_,_) -> { return test4R(aB.clone(), offset1, vL1, vI1, vS1, vB1); });
        testGroups.get("test4").put("test4a", (_,_) -> { return test4a(aB.clone(), offset1, vL1, vI1, vS1, vB1); });

        testGroups.put("test4BE", new HashMap<String,TestFunction>());
        testGroups.get("test4BE").put("test4RBE", (_,_) -> { return test4RBE(aB.clone(), offset1, vL1, vI1, vS1, vB1); });
        testGroups.get("test4BE").put("test4aBE", (_,_) -> { return test4aBE(aB.clone(), offset1, vL1, vI1, vS1, vB1); });

        testGroups.put("test5", new HashMap<String,TestFunction>());
        testGroups.get("test5").put("test5R", (_,_) -> { return test5R(aB.clone(), offset1); });
        testGroups.get("test5").put("test5a", (_,_) -> { return test5a(aB.clone(), offset1); });

        testGroups.put("test6", new HashMap<String,TestFunction>());
        testGroups.get("test6").put("test6R", (_,_) -> { return test6R(aB.clone(), bB.clone(), offset1, offset2); });
        testGroups.get("test6").put("test6a", (_,_) -> { return test6a(aB.clone(), bB.clone(), offset1, offset2); });

        testGroups.put("test7", new HashMap<String,TestFunction>());
        testGroups.get("test7").put("test7R", (_,_) -> { return test7R(aB.clone(), offset1, vI1); });
        testGroups.get("test7").put("test7a", (_,_) -> { return test7a(aB.clone(), offset1, vI1); });

        testGroups.put("test7BE", new HashMap<String,TestFunction>());
        testGroups.get("test7BE").put("test7RBE", (_,_) -> { return test7RBE(aB.clone(), offset1, vI1); });
        testGroups.get("test7BE").put("test7aBE", (_,_) -> { return test7aBE(aB.clone(), offset1, vI1); });

        testGroups.put("test100", new HashMap<String,TestFunction>());
        testGroups.get("test100").put("test100R", (_,_) -> { return test100R(aS.clone(), offset1); });
        testGroups.get("test100").put("test100a", (_,_) -> { return test100a(aS.clone(), offset1); });

        testGroups.put("test101", new HashMap<String,TestFunction>());
        testGroups.get("test101").put("test101R", (_,_) -> { return test101R(aS.clone(), offset1); });
        testGroups.get("test101").put("test101a", (_,_) -> { return test101a(aS.clone(), offset1); });

        testGroups.put("test102", new HashMap<String,TestFunction>());
        testGroups.get("test102").put("test102R", (_,_) -> { return test102R(aS.clone(), offset1, vL1, vI1, vS1); });
        testGroups.get("test102").put("test102a", (_,_) -> { return test102a(aS.clone(), offset1, vL1, vI1, vS1); });

        testGroups.put("test102BE", new HashMap<String,TestFunction>());
        testGroups.get("test102BE").put("test102RBE", (_,_) -> { return test102RBE(aS.clone(), offset1, vL1, vI1, vS1); });
        testGroups.get("test102BE").put("test102aBE", (_,_) -> { return test102aBE(aS.clone(), offset1, vL1, vI1, vS1); });

        testGroups.put("test200", new HashMap<String,TestFunction>());
        testGroups.get("test200").put("test200R", (_,_) -> { return test200R(aI.clone(), offset1); });
        testGroups.get("test200").put("test200a", (_,_) -> { return test200a(aI.clone(), offset1); });

        testGroups.put("test201", new HashMap<String,TestFunction>());
        testGroups.get("test201").put("test201R", (_,_) -> { return test201R(aI.clone(), offset1); });
        testGroups.get("test201").put("test201a", (_,_) -> { return test201a(aI.clone(), offset1); });

        testGroups.put("test202", new HashMap<String,TestFunction>());
        testGroups.get("test202").put("test202R", (_,_) -> { return test202R(aI.clone(), offset1, vL1, vI1); });
        testGroups.get("test202").put("test202a", (_,_) -> { return test202a(aI.clone(), offset1, vL1, vI1); });

        testGroups.put("test202BE", new HashMap<String,TestFunction>());
        testGroups.get("test202BE").put("test202RBE", (_,_) -> { return test202RBE(aI.clone(), offset1, vL1, vI1); });
        testGroups.get("test202BE").put("test202aBE", (_,_) -> { return test202aBE(aI.clone(), offset1, vL1, vI1); });

        testGroups.put("test300", new HashMap<String,TestFunction>());
        testGroups.get("test300").put("test300R", (_,_) -> { return test300R(aI.clone()); });
        testGroups.get("test300").put("test300a", (_,_) -> { return test300a(aI.clone()); });

        testGroups.put("test400", new HashMap<String,TestFunction>());
        testGroups.get("test400").put("test400R", (_,_) -> { return test400R(aI.clone()); });
        testGroups.get("test400").put("test400a", (_,_) -> { return test400a(aI.clone()); });

        testGroups.put("test500", new HashMap<String,TestFunction>());
        testGroups.get("test500").put("test500R", (_,_) -> { return test500R(aB.clone(), offset1, vL1); });
        testGroups.get("test500").put("test500a", (_,_) -> { return test500a(aB.clone(), offset1, vL1); });

        testGroups.put("test501", new HashMap<String,TestFunction>());
        testGroups.get("test501").put("test500R", (_,i) -> { return test500R(aB.clone(), RANGE - 20 + (i % 30), vL1); });
        testGroups.get("test501").put("test501a", (_,i) -> { return test501a(aB.clone(), RANGE - 20 + (i % 30), vL1); });
        //                                                                               +-------------------+
        // Create offsets that are sometimes going to pass all RangeChecks, and sometimes one, and sometimes none.
        // Consequence: all RangeChecks stay in the final compilation.

        testGroups.put("test502", new HashMap<String,TestFunction>());
        testGroups.get("test502").put("test500R", (w,i) -> { return test500R(aB.clone(), w ? offset1 : RANGE - 20 + (i % 30), vL1); });
        testGroups.get("test502").put("test502a", (w,i) -> { return test502a(aB.clone(), w ? offset1 : RANGE - 20 + (i % 30), vL1); });
        //                                                                                   +-----+   +-------------------+
        // First use something in range, and after warmup randomize going outside the range.
        // Consequence: all RangeChecks stay in the final compilation.

        testGroups.put("test500BE", new HashMap<String,TestFunction>());
        testGroups.get("test500BE").put("test500RBE", (_,_) -> { return test500RBE(aB.clone(), offset1, vL1); });
        testGroups.get("test500BE").put("test500aBE", (_,_) -> { return test500aBE(aB.clone(), offset1, vL1); });

        testGroups.put("test501BE", new HashMap<String,TestFunction>());
        testGroups.get("test501BE").put("test500RBE", (_,i) -> { return test500RBE(aB.clone(), RANGE - 20 + (i % 30), vL1); });
        testGroups.get("test501BE").put("test501aBE", (_,i) -> { return test501aBE(aB.clone(), RANGE - 20 + (i % 30), vL1); });
        //                                                                               +-------------------+
        // Create offsets that are sometimes going to pass all RangeChecks, and sometimes one, and sometimes none.
        // Consequence: all RangeChecks stay in the final compilation.

        testGroups.put("test502BE", new HashMap<String,TestFunction>());
        testGroups.get("test502BE").put("test500RBE", (w,i) -> { return test500RBE(aB.clone(), w ? offset1 : RANGE - 20 + (i % 30), vL1); });
        testGroups.get("test502BE").put("test502aBE", (w,i) -> { return test502aBE(aB.clone(), w ? offset1 : RANGE - 20 + (i % 30), vL1); });
        //                                                                                   +-----+   +-------------------+
        // First use something in range, and after warmup randomize going outside the range.
        // Consequence: all RangeChecks stay in the final compilation.

        testGroups.put("test600", new HashMap<String,TestFunction>());
        testGroups.get("test600").put("test600R", (_,i) -> { return test600R(aB.clone(), aI.clone(), i); });
        testGroups.get("test600").put("test600a", (_,i) -> { return test600a(aB.clone(), aI.clone(), i); });

        testGroups.put("test700", new HashMap<String,TestFunction>());
        testGroups.get("test700").put("test700R", (_,i) -> { return test700R(aI.clone(), i); });
        testGroups.get("test700").put("test700a", (_,i) -> { return test700a(aI.clone(), i); });

        testGroups.put("test800", new HashMap<String,TestFunction>());
        testGroups.get("test800").put("test800R", (_,_) -> { return test800R(aB.clone(), offset1, vL1); });
        testGroups.get("test800").put("test800a", (_,_) -> { return test800a(aB.clone(), offset1, vL1); });

        testGroups.put("test800BE", new HashMap<String,TestFunction>());
        testGroups.get("test800BE").put("test800RBE", (_,_) -> { return test800RBE(aB.clone(), offset1, vL1); });
        testGroups.get("test800BE").put("test800aBE", (_,_) -> { return test800aBE(aB.clone(), offset1, vL1); });
    }

    @Warmup(100)
    @Run(test = {"test1a",
                 "test1b",
                 "test1c",
                 "test1d",
                 "test1e",
                 "test1f",
                 "test1g",
                 "test1h",
                 "test1i",
                 "test2a",
                 "test2b",
                 "test2c",
                 "test2d",
                 "test2e",
                 "test2aBE",
                 "test2bBE",
                 "test2cBE",
                 "test2dBE",
                 "test2eBE",
                 "test3a",
                 "test3aBE",
                 "test4a",
                 "test4aBE",
                 "test5a",
                 "test6a",
                 "test7a",
                 "test7aBE",
                 "test100a",
                 "test101a",
                 "test102a",
                 "test102aBE",
                 "test200a",
                 "test201a",
                 "test202a",
                 "test202aBE",
                 "test300a",
                 "test400a",
                 "test500a",
                 "test501a",
                 "test502a",
                 "test500aBE",
                 "test501aBE",
                 "test502aBE",
                 "test600a",
                 "test700a",
                 "test800a",
                 "test800aBE"})
    public void runTests(RunInfo info) {
        // Repeat many times, so that we also have multiple iterations for post-warmup to potentially recompile
        int iters = info.isWarmUp() ? 1_000 : 50_000;
        for (int iter = 0; iter < iters; iter++) {
            // Write random values to inputs
            set_random(aB);
            set_random(bB);
            set_random(aS);
            set_random(bS);
            set_random(aI);
            set_random(bI);
            set_random(aL);
            set_random(bL);

            offset1 = Math.abs(RANDOM.nextInt()) % 100;
            offset2 = Math.abs(RANDOM.nextInt()) % 100;
            vB1 = (byte)RANDOM.nextInt();
            vB2 = (byte)RANDOM.nextInt();
            vS1 = (short)RANDOM.nextInt();
            vS2 = (short)RANDOM.nextInt();
            vI1 = RANDOM.nextInt();
            vI2 = RANDOM.nextInt();
            vL1 = RANDOM.nextLong();
            vL2 = RANDOM.nextLong();

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

    // -------------------------------------------
    // -------     Little-Endian API    ----------
    // -------------------------------------------
    // Note: I had to add @ForceInline because otherwise it would sometimes
    //       not inline nested method calls.

    // Store a short LE into an array using store bytes in an array
    @ForceInline
    static void storeShortLE(byte[] bytes, int offset, short value) {
        storeBytes(bytes, offset, (byte)(value >> 0),
                                  (byte)(value >> 8));
    }

    // Store an int LE into an array using store bytes in an array
    @ForceInline
    static void storeIntLE(byte[] bytes, int offset, int value) {
        storeBytes(bytes, offset, (byte)(value >> 0 ),
                                  (byte)(value >> 8 ),
                                  (byte)(value >> 16),
                                  (byte)(value >> 24));
    }

    // Store an int LE into an array using store bytes in an array
    @ForceInline
    static void storeLongLE(byte[] bytes, int offset, long value) {
        storeBytes(bytes, offset, (byte)(value >> 0 ),
                                  (byte)(value >> 8 ),
                                  (byte)(value >> 16),
                                  (byte)(value >> 24),
                                  (byte)(value >> 32),
                                  (byte)(value >> 40),
                                  (byte)(value >> 48),
                                  (byte)(value >> 56));
    }

    // -------------------------------------------
    // -------      Big-Endian API      ----------
    // -------------------------------------------

    // Store a short BE into an array using store bytes in an array
    @ForceInline
    static void storeShortBE(byte[] bytes, int offset, short value) {
        storeBytes(bytes, offset, (byte)(value >> 8),
                                  (byte)(value >> 0));
    }

    // Store an int BE into an array using store bytes in an array
    @ForceInline
    static void storeIntBE(byte[] bytes, int offset, int value) {
        storeBytes(bytes, offset, (byte)(value >> 24),
                                  (byte)(value >> 16),
                                  (byte)(value >> 8 ),
                                  (byte)(value >> 0 ));
    }

    // Store an int BE into an array using store bytes in an array
    @ForceInline
    static void storeLongBE(byte[] bytes, int offset, long value) {
        storeBytes(bytes, offset, (byte)(value >> 56),
                                  (byte)(value >> 48),
                                  (byte)(value >> 40),
                                  (byte)(value >> 32),
                                  (byte)(value >> 24),
                                  (byte)(value >> 16),
                                  (byte)(value >> 8 ),
                                  (byte)(value >> 0 ));
    }

    // Store 2 bytes into an array
    @ForceInline
    static void storeBytes(byte[] bytes, int offset, byte b0, byte b1) {
        bytes[offset + 0] = b0;
        bytes[offset + 1] = b1;
    }

    // Store 4 bytes into an array
    @ForceInline
    static void storeBytes(byte[] bytes, int offset, byte b0, byte b1, byte b2, byte b3) {
        bytes[offset + 0] = b0;
        bytes[offset + 1] = b1;
        bytes[offset + 2] = b2;
        bytes[offset + 3] = b3;
    }

    // Store 8 bytes into an array
    @ForceInline
    static void storeBytes(byte[] bytes, int offset, byte b0, byte b1, byte b2, byte b3,
                                                     byte b4, byte b5, byte b6, byte b7) {
        bytes[offset + 0] = b0;
        bytes[offset + 1] = b1;
        bytes[offset + 2] = b2;
        bytes[offset + 3] = b3;
        bytes[offset + 4] = b4;
        bytes[offset + 5] = b5;
        bytes[offset + 6] = b6;
        bytes[offset + 7] = b7;
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
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
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
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test1b(byte[] a) {
        // Add custom null check, to ensure the unsafe access always recognizes its type as an array store
        if (a == null) {return null;}
        UNSAFE.putLongUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, 0xdeadbeefbaadbabeL, false /* bigEndian */);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test1c(byte[] a) {
        storeLongLE(a, 0, 0xdeadbeefbaadbabeL);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test1d(byte[] a) {
        storeIntLE(a, 0, 0xbaadbabe);
        storeIntLE(a, 4, 0xdeadbeef);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test1e(byte[] a) {
        storeShortLE(a, 0, (short)0xbabe);
        storeShortLE(a, 2, (short)0xbaad);
        storeShortLE(a, 4, (short)0xbeef);
        storeShortLE(a, 6, (short)0xdead);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test1f(byte[] a) {
        UNSAFE.putByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0, (byte)0xbe);
        UNSAFE.putByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1, (byte)0xba);
        UNSAFE.putByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2, (byte)0xad);
        UNSAFE.putByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3, (byte)0xba);
        UNSAFE.putByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4, (byte)0xef);
        UNSAFE.putByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5, (byte)0xbe);
        UNSAFE.putByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6, (byte)0xad);
        UNSAFE.putByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7, (byte)0xde);
        return new Object[]{ a };
    }

    @Test
    // Do not optimize these, just to be sure we do not mess with store ordering.
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test1g(byte[] a) {
        UNSAFE.putByteRelease(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0, (byte)0xbe);
        UNSAFE.putByteRelease(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1, (byte)0xba);
        UNSAFE.putByteRelease(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2, (byte)0xad);
        UNSAFE.putByteRelease(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3, (byte)0xba);
        UNSAFE.putByteRelease(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4, (byte)0xef);
        UNSAFE.putByteRelease(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5, (byte)0xbe);
        UNSAFE.putByteRelease(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6, (byte)0xad);
        UNSAFE.putByteRelease(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7, (byte)0xde);
        return new Object[]{ a };
    }

    @Test
    // Do not optimize these, just to be sure we do not mess with store ordering.
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test1h(byte[] a) {
        UNSAFE.putByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0, (byte)0xbe);
        UNSAFE.putByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1, (byte)0xba);
        UNSAFE.putByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2, (byte)0xad);
        UNSAFE.putByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3, (byte)0xba);
        UNSAFE.putByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4, (byte)0xef);
        UNSAFE.putByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5, (byte)0xbe);
        UNSAFE.putByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6, (byte)0xad);
        UNSAFE.putByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7, (byte)0xde);
        return new Object[]{ a };
    }

    @Test
    // Do not optimize these, just to be sure we do not mess with store ordering.
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test1i(byte[] a) {
        UNSAFE.putByteOpaque(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0, (byte)0xbe);
        UNSAFE.putByteOpaque(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1, (byte)0xba);
        UNSAFE.putByteOpaque(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2, (byte)0xad);
        UNSAFE.putByteOpaque(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3, (byte)0xba);
        UNSAFE.putByteOpaque(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4, (byte)0xef);
        UNSAFE.putByteOpaque(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5, (byte)0xbe);
        UNSAFE.putByteOpaque(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6, (byte)0xad);
        UNSAFE.putByteOpaque(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7, (byte)0xde);
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test2R(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 0);
        a[offset + 1] = (byte)(v >> 8);
        a[offset + 2] = (byte)(v >> 16);
        a[offset + 3] = (byte)(v >> 24);
        a[offset + 4] = (byte)(v >> 32);
        a[offset + 5] = (byte)(v >> 40);
        a[offset + 6] = (byte)(v >> 48);
        a[offset + 7] = (byte)(v >> 56);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test2a(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 0);
        a[offset + 1] = (byte)(v >> 8);
        a[offset + 2] = (byte)(v >> 16);
        a[offset + 3] = (byte)(v >> 24);
        a[offset + 4] = (byte)(v >> 32);
        a[offset + 5] = (byte)(v >> 40);
        a[offset + 6] = (byte)(v >> 48);
        a[offset + 7] = (byte)(v >> 56);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test2b(byte[] a, int offset, long v) {
        // Add custom null check, to ensure the unsafe access always recognizes its type as an array store
        if (a == null) {return null;}
        UNSAFE.putLongUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + offset, v, false /* bigEndian */);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test2c(byte[] a, int offset, long v) {
        storeLongLE(a, offset, v);
        return new Object[]{ a };
    }

    @Test
    // No optimization, casting long -> int -> byte does not work
    static Object[] test2d(byte[] a, int offset, long v) {
        storeIntLE(a, offset + 0, (int)(v >> 0));
        storeIntLE(a, offset + 4, (int)(v >> 32));
        return new Object[]{ a };
    }

    @Test
    // No optimization, casting long -> short -> byte does not work
    static Object[] test2e(byte[] a, int offset, long v) {
        storeShortLE(a, offset + 0, (short)(v >> 0));
        storeShortLE(a, offset + 2, (short)(v >> 16));
        storeShortLE(a, offset + 4, (short)(v >> 32));
        storeShortLE(a, offset + 6, (short)(v >> 48));
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test2RBE(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 56);
        a[offset + 1] = (byte)(v >> 48);
        a[offset + 2] = (byte)(v >> 40);
        a[offset + 3] = (byte)(v >> 32);
        a[offset + 4] = (byte)(v >> 24);
        a[offset + 5] = (byte)(v >> 16);
        a[offset + 6] = (byte)(v >> 8);
        a[offset + 7] = (byte)(v >> 0);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test2aBE(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 56);
        a[offset + 1] = (byte)(v >> 48);
        a[offset + 2] = (byte)(v >> 40);
        a[offset + 3] = (byte)(v >> 32);
        a[offset + 4] = (byte)(v >> 24);
        a[offset + 5] = (byte)(v >> 16);
        a[offset + 6] = (byte)(v >> 8);
        a[offset + 7] = (byte)(v >> 0);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test2bBE(byte[] a, int offset, long v) {
        // Add custom null check, to ensure the unsafe access always recognizes its type as an array store
        if (a == null) {return null;}
        UNSAFE.putLongUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + offset, v, true /* bigEndian */);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test2cBE(byte[] a, int offset, long v) {
        storeLongBE(a, offset, v);
        return new Object[]{ a };
    }

    @Test
    // No optimization, casting long -> int -> byte does not work
    static Object[] test2dBE(byte[] a, int offset, long v) {
        storeIntBE(a, offset + 0, (int)(v >> 32));
        storeIntBE(a, offset + 4, (int)(v >> 0));
        return new Object[]{ a };
    }

    @Test
    // No optimization, casting long -> short -> byte does not work
    static Object[] test2eBE(byte[] a, int offset, long v) {
        storeShortBE(a, offset + 0, (short)(v >> 48));
        storeShortBE(a, offset + 2, (short)(v >> 32));
        storeShortBE(a, offset + 4, (short)(v >> 16));
        storeShortBE(a, offset + 6, (short)(v >> 0));
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test3R(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 0);
        a[offset + 1] = (byte)(v >> 8);
        a[offset + 2] = (byte)(v >> 16);
        a[offset + 3] = (byte)(v >> 24);
        a[offset + 4] = (byte)(v >> 0);
        a[offset + 5] = (byte)(v >> 8);
        a[offset + 6] = (byte)(v >> 16);
        a[offset + 7] = (byte)(v >> 24);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test3a(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 0);
        a[offset + 1] = (byte)(v >> 8);
        a[offset + 2] = (byte)(v >> 16);
        a[offset + 3] = (byte)(v >> 24);
        a[offset + 4] = (byte)(v >> 0);
        a[offset + 5] = (byte)(v >> 8);
        a[offset + 6] = (byte)(v >> 16);
        a[offset + 7] = (byte)(v >> 24);
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test3RBE(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 24);
        a[offset + 1] = (byte)(v >> 16);
        a[offset + 2] = (byte)(v >> 8);
        a[offset + 3] = (byte)(v >> 0);
        a[offset + 4] = (byte)(v >> 24);
        a[offset + 5] = (byte)(v >> 16);
        a[offset + 6] = (byte)(v >> 8);
        a[offset + 7] = (byte)(v >> 0);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test3aBE(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 24);
        a[offset + 1] = (byte)(v >> 16);
        a[offset + 2] = (byte)(v >> 8);
        a[offset + 3] = (byte)(v >> 0);
        a[offset + 4] = (byte)(v >> 24);
        a[offset + 5] = (byte)(v >> 16);
        a[offset + 6] = (byte)(v >> 8);
        a[offset + 7] = (byte)(v >> 0);
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test4R(byte[] a, int offset, long v1, int v2, short v3, byte v4) {
        a[offset +  0] = (byte)0x00;
        a[offset +  1] = (byte)0xFF;
        a[offset +  2] = v4;
        a[offset +  3] = (byte)0x42;
        a[offset +  4] = (byte)(v1 >> 0);
        a[offset +  5] = (byte)(v1 >> 8);
        a[offset +  6] = (byte)0xAB;
        a[offset +  7] = (byte)0xCD;
        a[offset +  8] = (byte)0xEF;
        a[offset +  9] = (byte)0x01;
        a[offset + 10] = (byte)(v2 >> 0);
        a[offset + 11] = (byte)(v2 >> 8);
        a[offset + 12] = (byte)(v2 >> 16);
        a[offset + 13] = (byte)(v2 >> 24);
        a[offset + 14] = (byte)(v3 >> 0);
        a[offset + 15] = (byte)(v3 >> 8);
        a[offset + 16] = (byte)0xEF;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4", // 3 (+ 1 for uncommon trap)
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "12",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",  // Stores of constants can be merged
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test4a(byte[] a, int offset, long v1, int v2, short v3, byte v4) {
        a[offset +  0] = (byte)0x00; // individual load expected to go into state of RC
        a[offset +  1] = (byte)0xFF;
        a[offset +  2] = v4;
        a[offset +  3] = (byte)0x42;
        a[offset +  4] = (byte)(v1 >> 0);
        a[offset +  5] = (byte)(v1 >> 8);
        a[offset +  6] = (byte)0xAB;
        a[offset +  7] = (byte)0xCD;
        a[offset +  8] = (byte)0xEF;
        a[offset +  9] = (byte)0x01;
        a[offset + 10] = (byte)(v2 >> 0);
        a[offset + 11] = (byte)(v2 >> 8);
        a[offset + 12] = (byte)(v2 >> 16);
        a[offset + 13] = (byte)(v2 >> 24);
        a[offset + 14] = (byte)(v3 >> 0);
        a[offset + 15] = (byte)(v3 >> 8);
        a[offset + 16] = (byte)0xEF;
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test4RBE(byte[] a, int offset, long v1, int v2, short v3, byte v4) {
        a[offset +  0] = (byte)0x00;
        a[offset +  1] = (byte)0xFF;
        a[offset +  2] = v4;
        a[offset +  3] = (byte)0x42;
        a[offset +  4] = (byte)(v1 >> 8);
        a[offset +  5] = (byte)(v1 >> 0);
        a[offset +  6] = (byte)0xAB;
        a[offset +  7] = (byte)0xCD;
        a[offset +  8] = (byte)0xEF;
        a[offset +  9] = (byte)0x01;
        a[offset + 10] = (byte)(v2 >> 24);
        a[offset + 11] = (byte)(v2 >> 16);
        a[offset + 12] = (byte)(v2 >> 8);
        a[offset + 13] = (byte)(v2 >> 0);
        a[offset + 14] = (byte)(v3 >> 8);
        a[offset + 15] = (byte)(v3 >> 0);
        a[offset + 16] = (byte)0xEF;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "12",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",  // Stores of constants can be merged
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4", // 3 (+ 1 for uncommon trap)
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test4aBE(byte[] a, int offset, long v1, int v2, short v3, byte v4) {
        a[offset +  0] = (byte)0x00; // individual load expected to go into state of RC
        a[offset +  1] = (byte)0xFF;
        a[offset +  2] = v4;
        a[offset +  3] = (byte)0x42;
        a[offset +  4] = (byte)(v1 >> 8);
        a[offset +  5] = (byte)(v1 >> 0);
        a[offset +  6] = (byte)0xAB;
        a[offset +  7] = (byte)0xCD;
        a[offset +  8] = (byte)0xEF;
        a[offset +  9] = (byte)0x01;
        a[offset + 10] = (byte)(v2 >> 24);
        a[offset + 11] = (byte)(v2 >> 16);
        a[offset + 12] = (byte)(v2 >> 8);
        a[offset + 13] = (byte)(v2 >> 0);
        a[offset + 14] = (byte)(v3 >> 8);
        a[offset + 15] = (byte)(v3 >> 0);
        a[offset + 16] = (byte)0xEF;
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test5R(byte[] a, int offset) {
        a[offset +  0] = (byte)0x01;
        a[offset +  1] = (byte)0x02;
        a[offset +  2] = (byte)0x03;
        a[offset +  3] = (byte)0x04;
        a[offset +  4] = (byte)0x11;
        a[offset +  5] = (byte)0x22;
        a[offset +  6] = (byte)0x33;
        a[offset +  7] = (byte)0x44;
        a[offset +  8] = (byte)0x55;
        a[offset +  9] = (byte)0x66;
        a[offset + 10] = (byte)0x77;
        a[offset + 11] = (byte)0xAA;
        a[offset + 12] = (byte)0xBB;
        a[offset + 13] = (byte)0xCC;
        a[offset + 14] = (byte)0xDD;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test5a(byte[] a, int offset) {
        a[offset +  0] = (byte)0x01;
        a[offset +  1] = (byte)0x02;
        a[offset +  2] = (byte)0x03;
        a[offset +  3] = (byte)0x04;
        a[offset +  4] = (byte)0x11;
        a[offset +  5] = (byte)0x22;
        a[offset +  6] = (byte)0x33;
        a[offset +  7] = (byte)0x44;
        a[offset +  8] = (byte)0x55;
        a[offset +  9] = (byte)0x66;
        a[offset + 10] = (byte)0x77;
        a[offset + 11] = (byte)0xAA;
        a[offset + 12] = (byte)0xBB;
        a[offset + 13] = (byte)0xCC;
        a[offset + 14] = (byte)0xDD;
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test6R(byte[] a, byte[] b, int offset1, int offset2) {
        a[offset1 +  1] = (byte)0x02;
        a[offset1 +  3] = (byte)0x04;
        b[offset1 +  4] = (byte)0x11;
        a[offset1 +  5] = (byte)0x22;
        a[offset2 +  6] = (byte)0x33;
        a[offset1 +  7] = (byte)0x44;
        b[offset1 +  8] = (byte)0x55;
        b[offset1 + 10] = (byte)0x66;
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"})
    static Object[] test6a(byte[] a, byte[] b, int offset1, int offset2) {
        a[offset1 +  1] = (byte)0x02;
        a[offset1 +  3] = (byte)0x04;
        b[offset1 +  4] = (byte)0x11;
        a[offset1 +  5] = (byte)0x22;
        a[offset2 +  6] = (byte)0x33;
        a[offset1 +  7] = (byte)0x44;
        b[offset1 +  8] = (byte)0x55;
        b[offset1 + 10] = (byte)0x66;
        return new Object[]{ a, b };
    }

    @DontCompile
    static Object[] test7R(byte[] a, int offset1, int v1) {
        a[offset1 +  1] = (byte)(v1 >> 8);
        a[offset1 +  2] = (byte)(v1 >> 16);
        a[offset1 +  3] = (byte)(v1 >> 24);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"})
    static Object[] test7a(byte[] a, int offset1, int v1) {
        a[offset1 +  1] = (byte)(v1 >> 8);
        a[offset1 +  2] = (byte)(v1 >> 16);
        a[offset1 +  3] = (byte)(v1 >> 24);
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test7RBE(byte[] a, int offset1, int v1) {
        a[offset1 +  1] = (byte)(v1 >> 24);
        a[offset1 +  2] = (byte)(v1 >> 16);
        a[offset1 +  3] = (byte)(v1 >> 8);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"})
    static Object[] test7aBE(byte[] a, int offset1, int v1) {
        a[offset1 +  1] = (byte)(v1 >> 24);
        a[offset1 +  2] = (byte)(v1 >> 16);
        a[offset1 +  3] = (byte)(v1 >> 8);
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test100R(short[] a, int offset) {
        a[offset +  0] = (short)0x0100;
        a[offset +  1] = (short)0x0200;
        a[offset +  2] = (short)0x0311;
        a[offset +  3] = (short)0x0400;
        a[offset +  4] = (short)0x1100;
        a[offset +  5] = (short)0x2233;
        a[offset +  6] = (short)0x3300;
        a[offset +  7] = (short)0x4400;
        a[offset +  8] = (short)0x5599;
        a[offset +  9] = (short)0x6600;
        a[offset + 10] = (short)0x7700;
        a[offset + 11] = (short)0xAACC;
        a[offset + 12] = (short)0xBB00;
        a[offset + 13] = (short)0xCC00;
        a[offset + 14] = (short)0xDDFF;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_I_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_L_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test100a(short[] a, int offset) {
        a[offset +  0] = (short)0x0100; // stays unchanged -> both used for RC and Return path
        a[offset +  1] = (short)0x0200; //    I
        a[offset +  2] = (short)0x0311; //    I
        a[offset +  3] = (short)0x0400; //   L
        a[offset +  4] = (short)0x1100; //   L
        a[offset +  5] = (short)0x2233; //   L
        a[offset +  6] = (short)0x3300; //   L
        a[offset +  7] = (short)0x4400; //  L
        a[offset +  8] = (short)0x5599; //  L
        a[offset +  9] = (short)0x6600; //  L
        a[offset + 10] = (short)0x7700; //  L
        a[offset + 11] = (short)0xAACC; // L
        a[offset + 12] = (short)0xBB00; // L
        a[offset + 13] = (short)0xCC00; // L
        a[offset + 14] = (short)0xDDFF; // L
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test101R(short[] a, int offset) {
        a[offset +  0] = (short)0x0100;
        a[offset +  1] = (short)0x0200;
        a[offset +  2] = (short)0x0311;
        a[offset +  3] = (short)0x0400;
        a[offset +  4] = (short)0x1100;
        a[offset +  5] = (short)0x2233;
        a[offset +  6] = (short)0x3300;
        a[offset +  7] = (short)0x4400;
        a[offset +  8] = (short)0x5599;
        a[offset +  9] = (short)0x6600;
        a[offset + 10] = (short)0x7700;
        a[offset + 11] = (short)0xAACC;
        a[offset + 12] = (short)0xBB00;
        a[offset + 13] = (short)0xCC00;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1", // only for RC
                  IRNode.STORE_I_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_L_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test101a(short[] a, int offset) {
        a[offset +  0] = (short)0x0100; //    I plus kept unchanged for RC
        a[offset +  1] = (short)0x0200; //    I
        a[offset +  2] = (short)0x0311; //   L
        a[offset +  3] = (short)0x0400; //   L
        a[offset +  4] = (short)0x1100; //   L
        a[offset +  5] = (short)0x2233; //   L
        a[offset +  6] = (short)0x3300; //  L
        a[offset +  7] = (short)0x4400; //  L
        a[offset +  8] = (short)0x5599; //  L
        a[offset +  9] = (short)0x6600; //  L
        a[offset + 10] = (short)0x7700; // L
        a[offset + 11] = (short)0xAACC; // L
        a[offset + 12] = (short)0xBB00; // L
        a[offset + 13] = (short)0xCC00; // L
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test102R(short[] a, int offset, long v1, int v2, short v3) {
        a[offset +  0] = (short)0x0000;
        a[offset +  1] = (short)0xFFFF;
        a[offset +  2] = v3;
        a[offset +  3] = (short)0x4242;
        a[offset +  4] = (short)(v1 >>  0);
        a[offset +  5] = (short)(v1 >> 16);
        a[offset +  6] = (short)0xAB11;
        a[offset +  7] = (short)0xCD36;
        a[offset +  8] = (short)0xEF89;
        a[offset +  9] = (short)0x0156;
        a[offset + 10] = (short)(v1 >> 0);
        a[offset + 11] = (short)(v1 >> 16);
        a[offset + 12] = (short)(v1 >> 32);
        a[offset + 13] = (short)(v1 >> 48);
        a[offset + 14] = (short)(v2 >> 0);
        a[offset + 15] = (short)(v2 >> 16);
        a[offset + 16] = (short)0xEFEF;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4", // 3 (+1 that goes into RC)
                  IRNode.STORE_I_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
                  IRNode.STORE_L_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "12",
                  IRNode.STORE_I_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",  // Stores of constants can be merged
                  IRNode.STORE_L_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test102a(short[] a, int offset, long v1, int v2, short v3) {
        a[offset +  0] = (short)0x0000; // store goes into RC
        a[offset +  1] = (short)0xFFFF;
        a[offset +  2] = v3;
        a[offset +  3] = (short)0x4242;
        a[offset +  4] = (short)(v1 >>  0);
        a[offset +  5] = (short)(v1 >> 16);
        a[offset +  6] = (short)0xAB11;
        a[offset +  7] = (short)0xCD36;
        a[offset +  8] = (short)0xEF89;
        a[offset +  9] = (short)0x0156;
        a[offset + 10] = (short)(v1 >> 0);
        a[offset + 11] = (short)(v1 >> 16);
        a[offset + 12] = (short)(v1 >> 32);
        a[offset + 13] = (short)(v1 >> 48);
        a[offset + 14] = (short)(v2 >> 0);
        a[offset + 15] = (short)(v2 >> 16);
        a[offset + 16] = (short)0xEFEF;
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test102RBE(short[] a, int offset, long v1, int v2, short v3) {
        a[offset +  0] = (short)0x0000;
        a[offset +  1] = (short)0xFFFF;
        a[offset +  2] = v3;
        a[offset +  3] = (short)0x4242;
        a[offset +  4] = (short)(v1 >> 16);
        a[offset +  5] = (short)(v1 >>  0);
        a[offset +  6] = (short)0xAB11;
        a[offset +  7] = (short)0xCD36;
        a[offset +  8] = (short)0xEF89;
        a[offset +  9] = (short)0x0156;
        a[offset + 10] = (short)(v1 >> 48);
        a[offset + 11] = (short)(v1 >> 32);
        a[offset + 12] = (short)(v1 >> 16);
        a[offset + 13] = (short)(v1 >> 0);
        a[offset + 14] = (short)(v2 >> 16);
        a[offset + 15] = (short)(v2 >> 0);
        a[offset + 16] = (short)0xEFEF;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "12",
                  IRNode.STORE_I_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",  // Stores of constants can be merged
                  IRNode.STORE_L_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4", // 3 (+1 that goes into RC)
                  IRNode.STORE_I_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
                  IRNode.STORE_L_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test102aBE(short[] a, int offset, long v1, int v2, short v3) {
        a[offset +  0] = (short)0x0000; // store goes into RC
        a[offset +  1] = (short)0xFFFF;
        a[offset +  2] = v3;
        a[offset +  3] = (short)0x4242;
        a[offset +  4] = (short)(v1 >> 16);
        a[offset +  5] = (short)(v1 >>  0);
        a[offset +  6] = (short)0xAB11;
        a[offset +  7] = (short)0xCD36;
        a[offset +  8] = (short)0xEF89;
        a[offset +  9] = (short)0x0156;
        a[offset + 10] = (short)(v1 >> 48);
        a[offset + 11] = (short)(v1 >> 32);
        a[offset + 12] = (short)(v1 >> 16);
        a[offset + 13] = (short)(v1 >> 0);
        a[offset + 14] = (short)(v2 >> 16);
        a[offset + 15] = (short)(v2 >> 0);
        a[offset + 16] = (short)0xEFEF;
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test200R(int[] a, int offset) {
        a[offset +  0] = 0x01001236;
        a[offset +  1] = 0x02001284;
        a[offset +  2] = 0x03111235;
        a[offset +  3] = 0x04001294;
        a[offset +  4] = 0x11001234;
        a[offset +  5] = 0x22331332;
        a[offset +  6] = 0x33001234;
        a[offset +  7] = 0x44001432;
        a[offset +  8] = 0x55991234;
        a[offset +  9] = 0x66001233;
        a[offset + 10] = 0x77001434;
        a[offset + 11] = 0xAACC1234;
        a[offset + 12] = 0xBB001434;
        a[offset + 13] = 0xCC001236;
        a[offset + 14] = 0xDDFF1534;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_L_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "7"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test200a(int[] a, int offset) {
        a[offset +  0] = 0x01001236; // stays unchanged -> both used for RC and Return path
        a[offset +  1] = 0x02001284; //       L
        a[offset +  2] = 0x03111235; //       L
        a[offset +  3] = 0x04001294; //      L
        a[offset +  4] = 0x11001234; //      L
        a[offset +  5] = 0x22331332; //     L
        a[offset +  6] = 0x33001234; //     L
        a[offset +  7] = 0x44001432; //    L
        a[offset +  8] = 0x55991234; //    L
        a[offset +  9] = 0x66001233; //   L
        a[offset + 10] = 0x77001434; //   L
        a[offset + 11] = 0xAACC1234; //  L
        a[offset + 12] = 0xBB001434; //  L
        a[offset + 13] = 0xCC001236; // L
        a[offset + 14] = 0xDDFF1534; // L
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test201R(int[] a, int offset) {
        a[offset +  0] = 0x01001236;
        a[offset +  1] = 0x02001284;
        a[offset +  2] = 0x03111235;
        a[offset +  3] = 0x04001294;
        a[offset +  4] = 0x11001234;
        a[offset +  5] = 0x22331332;
        a[offset +  6] = 0x33001234;
        a[offset +  7] = 0x44001432;
        a[offset +  8] = 0x55991234;
        a[offset +  9] = 0x66001233;
        a[offset + 10] = 0x77001434;
        a[offset + 11] = 0xAACC1234;
        a[offset + 12] = 0xBB001434;
        a[offset + 13] = 0xCC001236;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1", // only for RC
                  IRNode.STORE_L_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "7"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test201a(int[] a, int offset) {
        a[offset +  0] = 0x01001236; //       L and also kept unchanged for RC
        a[offset +  1] = 0x02001284; //       L
        a[offset +  2] = 0x03111235; //      L
        a[offset +  3] = 0x04001294; //      L
        a[offset +  4] = 0x11001234; //     L
        a[offset +  5] = 0x22331332; //     L
        a[offset +  6] = 0x33001234; //    L
        a[offset +  7] = 0x44001432; //    L
        a[offset +  8] = 0x55991234; //   L
        a[offset +  9] = 0x66001233; //   L
        a[offset + 10] = 0x77001434; //  L
        a[offset + 11] = 0xAACC1234; //  L
        a[offset + 12] = 0xBB001434; // L
        a[offset + 13] = 0xCC001236; // L
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test202R(int[] a, int offset, long v1, int v2) {
        a[offset +  0] = 0x00000000;
        a[offset +  1] = 0xFFFFFFFF;
        a[offset +  2] = v2;
        a[offset +  3] = 0x42424242;
        a[offset +  4] = (int)(v1 >>  0);
        a[offset +  5] = (int)(v1 >> 32);
        a[offset +  6] = 0xAB110129;
        a[offset +  7] = 0xCD360183;
        a[offset +  8] = 0xEF890173;
        a[offset +  9] = 0x01560124;
        a[offset + 10] = (int)(v1 >> 0);
        a[offset + 11] = (int)(v1 >> 32);
        a[offset + 12] = (int)(v1 >> 0);
        a[offset + 13] = (int)(v1 >> 32);
        a[offset + 14] = v2;
        a[offset + 15] = v2;
        a[offset + 16] = 0xEFEFEFEF;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "6", // 5 (+1 that goes into RC)
                  IRNode.STORE_L_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "6"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "10",
                  IRNode.STORE_L_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4"}, // Stores of constants can be merged
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test202a(int[] a, int offset, long v1, int v2) {
        a[offset +  0] = 0x00000000; // merged with store below, but also kept unchanged for RC
        a[offset +  1] = 0xFFFFFFFF;
        a[offset +  2] = v2;
        a[offset +  3] = 0x42424242;
        a[offset +  4] = (int)(v1 >>  0);
        a[offset +  5] = (int)(v1 >> 32);
        a[offset +  6] = 0xAB110129;
        a[offset +  7] = 0xCD360183;
        a[offset +  8] = 0xEF890173;
        a[offset +  9] = 0x01560124;
        a[offset + 10] = (int)(v1 >> 0);
        a[offset + 11] = (int)(v1 >> 32); // Stores to +11 and +12 can be merged also on big-endian
        a[offset + 12] = (int)(v1 >> 0);
        a[offset + 13] = (int)(v1 >> 32);
        a[offset + 14] = v2;
        a[offset + 15] = v2;
        a[offset + 16] = 0xEFEFEFEF;
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test202RBE(int[] a, int offset, long v1, int v2) {
        a[offset +  0] = 0x00000000;
        a[offset +  1] = 0xFFFFFFFF;
        a[offset +  2] = v2;
        a[offset +  3] = 0x42424242;
        a[offset +  4] = (int)(v1 >> 32);
        a[offset +  5] = (int)(v1 >>  0);
        a[offset +  6] = 0xAB110129;
        a[offset +  7] = 0xCD360183;
        a[offset +  8] = 0xEF890173;
        a[offset +  9] = 0x01560124;
        a[offset + 10] = (int)(v1 >> 32);
        a[offset + 11] = (int)(v1 >> 0);
        a[offset + 12] = (int)(v1 >> 32);
        a[offset + 13] = (int)(v1 >> 0);
        a[offset + 14] = v2;
        a[offset + 15] = v2;
        a[offset + 16] = 0xEFEFEFEF;
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "10",
                  IRNode.STORE_L_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4"}, // Stores of constants can be merged
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "6", // 5 (+1 that goes into RC)
                  IRNode.STORE_L_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "6"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test202aBE(int[] a, int offset, long v1, int v2) {
        a[offset +  0] = 0x00000000; // merged with store below, but also kept unchanged for RC
        a[offset +  1] = 0xFFFFFFFF;
        a[offset +  2] = v2;
        a[offset +  3] = 0x42424242;
        a[offset +  4] = (int)(v1 >> 32);
        a[offset +  5] = (int)(v1 >>  0);
        a[offset +  6] = 0xAB110129;
        a[offset +  7] = 0xCD360183;
        a[offset +  8] = 0xEF890173;
        a[offset +  9] = 0x01560124;
        a[offset + 10] = (int)(v1 >> 32);
        a[offset + 11] = (int)(v1 >> 0);  // Stores to +11 and +12 can be merged also on little-endian
        a[offset + 12] = (int)(v1 >> 32);
        a[offset + 13] = (int)(v1 >> 0);
        a[offset + 14] = v2;
        a[offset + 15] = v2;
        a[offset + 16] = 0xEFEFEFEF;
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test300R(int[] a) {
        a[2] = 42;
        a[3] = 42;
        a[4] = 42;
        a[5] = 42;
        int x = a[3]; // dependent load
        return new Object[]{ a, new int[]{ x } };
    }

    @Test
    @IR(counts = {IRNode.STORE_L_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2"},
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test300a(int[] a) {
        a[2] = 42;
        a[3] = 42;
        a[4] = 42;
        a[5] = 42;
        int x = a[3]; // dependent load
        return new Object[]{ a, new int[]{ x } };
    }

    @DontCompile
    static Object[] test400R(int[] a) {
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 0, (byte)0xbe);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 1, (byte)0xba);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 2, (byte)0xad);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 3, (byte)0xba);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 4, (byte)0xef);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 5, (byte)0xbe);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 6, (byte)0xad);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 7, (byte)0xde);
        return new Object[]{ a };
    }

    @Test
    // We must be careful with mismatched accesses on arrays:
    // An int-array can have about 2x max_int size, and hence if we address bytes in it, we can have int-overflows.
    // We might consider addresses (x + 0) and (x + 1) as adjacent, even if x = max_int, and therefore the second
    // address overflows and is not adjacent at all.
    // Therefore, we should only consider stores that have the same size as the element type of the array.
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8", // no merging
                  IRNode.STORE_C_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"})
    static Object[] test400a(int[] a) {
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 0, (byte)0xbe);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 1, (byte)0xba);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 2, (byte)0xad);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 3, (byte)0xba);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 4, (byte)0xef);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 5, (byte)0xbe);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 6, (byte)0xad);
        UNSAFE.putByte(a, UNSAFE.ARRAY_INT_BASE_OFFSET + 7, (byte)0xde);
        return new Object[]{ a };
    }

    @DontCompile
    // The 500-series has all the same code, but is executed with different inputs:
    // 500a: never violate a RangeCheck -> expect will always merge stores
    // 501a: randomly violate RangeCheck, also during warmup -> never merge stores
    // 502a: during warmup never violate RangeCheck -> compile once with merged stores
    //       but then after warmup violate RangeCheck -> recompile without merged stores
    static Object[] test500R(byte[] a, int offset, long v) {
        int idx = 0;
        try {
            a[offset + 0] = (byte)(v >> 0);
            idx = 1;
            a[offset + 1] = (byte)(v >> 8);
            idx = 2;
            a[offset + 2] = (byte)(v >> 16);
            idx = 3;
            a[offset + 3] = (byte)(v >> 24);
            idx = 4;
            a[offset + 4] = (byte)(v >> 32);
            idx = 5;
            a[offset + 5] = (byte)(v >> 40);
            idx = 6;
            a[offset + 6] = (byte)(v >> 48);
            idx = 7;
            a[offset + 7] = (byte)(v >> 56);
            idx = 8;
        } catch (ArrayIndexOutOfBoundsException _) {}
        return new Object[]{ a, new int[]{ idx } };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1", // for RangeCheck trap
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"}, // expect merged
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test500a(byte[] a, int offset, long v) {
        int idx = 0;
        try {
            a[offset + 0] = (byte)(v >> 0);
            idx = 1;
            a[offset + 1] = (byte)(v >> 8);
            idx = 2;
            a[offset + 2] = (byte)(v >> 16);
            idx = 3;
            a[offset + 3] = (byte)(v >> 24);
            idx = 4;
            a[offset + 4] = (byte)(v >> 32);
            idx = 5;
            a[offset + 5] = (byte)(v >> 40);
            idx = 6;
            a[offset + 6] = (byte)(v >> 48);
            idx = 7;
            a[offset + 7] = (byte)(v >> 56);
            idx = 8;
        } catch (ArrayIndexOutOfBoundsException _) {}
        return new Object[]{ a, new int[]{ idx } };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8", // No optimization because of too many RangeChecks
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"})
    static Object[] test501a(byte[] a, int offset, long v) {
        int idx = 0;
        try {
            a[offset + 0] = (byte)(v >> 0);
            idx = 1;
            a[offset + 1] = (byte)(v >> 8);
            idx = 2;
            a[offset + 2] = (byte)(v >> 16);
            idx = 3;
            a[offset + 3] = (byte)(v >> 24);
            idx = 4;
            a[offset + 4] = (byte)(v >> 32);
            idx = 5;
            a[offset + 5] = (byte)(v >> 40);
            idx = 6;
            a[offset + 6] = (byte)(v >> 48);
            idx = 7;
            a[offset + 7] = (byte)(v >> 56);
            idx = 8;
        } catch (ArrayIndexOutOfBoundsException _) {}
        return new Object[]{ a, new int[]{ idx } };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8", // No optimization because of too many RangeChecks
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"})
    static Object[] test502a(byte[] a, int offset, long v) {
        int idx = 0;
        try {
            a[offset + 0] = (byte)(v >> 0);
            idx = 1;
            a[offset + 1] = (byte)(v >> 8);
            idx = 2;
            a[offset + 2] = (byte)(v >> 16);
            idx = 3;
            a[offset + 3] = (byte)(v >> 24);
            idx = 4;
            a[offset + 4] = (byte)(v >> 32);
            idx = 5;
            a[offset + 5] = (byte)(v >> 40);
            idx = 6;
            a[offset + 6] = (byte)(v >> 48);
            idx = 7;
            a[offset + 7] = (byte)(v >> 56);
            idx = 8;
        } catch (ArrayIndexOutOfBoundsException _) {}
        return new Object[]{ a, new int[]{ idx } };
    }

    @DontCompile
    // The 500-series has all the same code, but is executed with different inputs:
    // 500a: never violate a RangeCheck -> expect will always merge stores
    // 501a: randomly violate RangeCheck, also during warmup -> never merge stores
    // 502a: during warmup never violate RangeCheck -> compile once with merged stores
    //       but then after warmup violate RangeCheck -> recompile without merged stores
    static Object[] test500RBE(byte[] a, int offset, long v) {
        int idx = 0;
        try {
            a[offset + 0] = (byte)(v >> 56);
            idx = 1;
            a[offset + 1] = (byte)(v >> 48);
            idx = 2;
            a[offset + 2] = (byte)(v >> 40);
            idx = 3;
            a[offset + 3] = (byte)(v >> 32);
            idx = 4;
            a[offset + 4] = (byte)(v >> 24);
            idx = 5;
            a[offset + 5] = (byte)(v >> 16);
            idx = 6;
            a[offset + 6] = (byte)(v >> 8);
            idx = 7;
            a[offset + 7] = (byte)(v >> 0);
            idx = 8;
        } catch (ArrayIndexOutOfBoundsException _) {}
        return new Object[]{ a, new int[]{ idx } };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1", // for RangeCheck trap
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"}, // expect merged
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test500aBE(byte[] a, int offset, long v) {
        int idx = 0;
        try {
            a[offset + 0] = (byte)(v >> 56);
            idx = 1;
            a[offset + 1] = (byte)(v >> 48);
            idx = 2;
            a[offset + 2] = (byte)(v >> 40);
            idx = 3;
            a[offset + 3] = (byte)(v >> 32);
            idx = 4;
            a[offset + 4] = (byte)(v >> 24);
            idx = 5;
            a[offset + 5] = (byte)(v >> 16);
            idx = 6;
            a[offset + 6] = (byte)(v >> 8);
            idx = 7;
            a[offset + 7] = (byte)(v >> 0);
            idx = 8;
        } catch (ArrayIndexOutOfBoundsException _) {}
        return new Object[]{ a, new int[]{ idx } };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "7",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test501aBE(byte[] a, int offset, long v) {
        int idx = 0;
        try {
            a[offset + 0] = (byte)(v >> 56);
            idx = 1;
            a[offset + 1] = (byte)(v >> 48);
            idx = 2;
            a[offset + 2] = (byte)(v >> 40);
            idx = 3;
            a[offset + 3] = (byte)(v >> 32);
            idx = 4;
            a[offset + 4] = (byte)(v >> 24);
            idx = 5;
            a[offset + 5] = (byte)(v >> 16);
            idx = 6;
            a[offset + 6] = (byte)(v >> 8);
            idx = 7;
            a[offset + 7] = (byte)(v >> 0);
            idx = 8;
        } catch (ArrayIndexOutOfBoundsException _) {}
        return new Object[]{ a, new int[]{ idx } };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "7",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test502aBE(byte[] a, int offset, long v) {
        int idx = 0;
        try {
            a[offset + 0] = (byte)(v >> 56);
            idx = 1;
            a[offset + 1] = (byte)(v >> 48);
            idx = 2;
            a[offset + 2] = (byte)(v >> 40);
            idx = 3;
            a[offset + 3] = (byte)(v >> 32);
            idx = 4;
            a[offset + 4] = (byte)(v >> 24);
            idx = 5;
            a[offset + 5] = (byte)(v >> 16);
            idx = 6;
            a[offset + 6] = (byte)(v >> 8);
            idx = 7;
            a[offset + 7] = (byte)(v >> 0);
            idx = 8;
        } catch (ArrayIndexOutOfBoundsException _) {}
        return new Object[]{ a, new int[]{ idx } };
    }

    @DontCompile
    static Object[] test600R(byte[] aB, int[] aI, int i) {
        Object a = null;
        long base = 0;
        if (i % 2 == 0) {
            a = aB;
            base = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
        } else {
            a = aI;
            base = UNSAFE.ARRAY_INT_BASE_OFFSET;
        }
        UNSAFE.putByte(a, base + 0, (byte)0xbe);
        UNSAFE.putByte(a, base + 1, (byte)0xba);
        UNSAFE.putByte(a, base + 2, (byte)0xad);
        UNSAFE.putByte(a, base + 3, (byte)0xba);
        UNSAFE.putByte(a, base + 4, (byte)0xef);
        UNSAFE.putByte(a, base + 5, (byte)0xbe);
        UNSAFE.putByte(a, base + 6, (byte)0xad);
        UNSAFE.putByte(a, base + 7, (byte)0xde);
        return new Object[]{ aB, aI };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "bottom\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8"}) // note: bottom type
    static Object[] test600a(byte[] aB, int[] aI, int i) {
        Object a = null;
        long base = 0;
        if (i % 2 == 0) {
            a = aB;
            base = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
        } else {
            a = aI;
            base = UNSAFE.ARRAY_INT_BASE_OFFSET;
        }
        // array a is an aryptr, but its element type is unknown, i.e. bottom.
        UNSAFE.putByte(a, base + 0, (byte)0xbe);
        UNSAFE.putByte(a, base + 1, (byte)0xba);
        UNSAFE.putByte(a, base + 2, (byte)0xad);
        UNSAFE.putByte(a, base + 3, (byte)0xba);
        UNSAFE.putByte(a, base + 4, (byte)0xef);
        UNSAFE.putByte(a, base + 5, (byte)0xbe);
        UNSAFE.putByte(a, base + 6, (byte)0xad);
        UNSAFE.putByte(a, base + 7, (byte)0xde);
        return new Object[]{ aB, aI };
    }

    @DontCompile
    static Object[] test700R(int[] a, long v1) {
        a[0] = (int)(v1 >> -1);
        a[1] = (int)(v1 >> -2);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_C_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
                  IRNode.STORE_L_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"})
    static Object[] test700a(int[] a, long v1) {
        // Negative shift: cannot optimize
        a[0] = (int)(v1 >> -1);
        a[1] = (int)(v1 >> -2);
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test800R(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 0);
        a[offset + 1] = (byte)(v >> 8);
        a[offset + 2] = (byte)(v >> 16);
        a[offset + 3] = (byte)(v >> 24);
        a[offset + 4] = (byte)(v >> 32);
        a[offset + 5] = (byte)(v >> 40);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "6",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"})
    static Object[] test800a(byte[] a, int offset, long v) {
        // Merge attempts begin at the lowest store in the Memory chain.
        // Candidates are found following the chain. The list is trimmed to a
        // power of 2 length by removing higher stores.
        a[offset + 0] = (byte)(v >> 0);  // Removed from candidate list
        a[offset + 1] = (byte)(v >> 8);  // Removed from candidate list
        a[offset + 2] = (byte)(v >> 16); // The 4 following stores are on the candidate list.
        a[offset + 3] = (byte)(v >> 24); // The current logic does not merge them
        a[offset + 4] = (byte)(v >> 32); // since it would require shifting the input.
        a[offset + 5] = (byte)(v >> 40);
        return new Object[]{ a };
    }

    @DontCompile
    static Object[] test800RBE(byte[] a, int offset, long v) {
        a[offset + 0] = (byte)(v >> 40);
        a[offset + 1] = (byte)(v >> 32);
        a[offset + 2] = (byte)(v >> 24);
        a[offset + 3] = (byte)(v >> 16);
        a[offset + 4] = (byte)(v >> 8);
        a[offset + 5] = (byte)(v >> 0);
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "6",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIfPlatform = {"little-endian", "true"})
    @IR(counts = {IRNode.STORE_B_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
                  IRNode.STORE_C_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
                  IRNode.STORE_I_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
                  IRNode.STORE_L_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"},
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static Object[] test800aBE(byte[] a, int offset, long v) {
        // Merge attempts begin at the lowest store in the Memory chain.
        // Candidates are found following the chain. The list is trimmed to a
        // power of 2 length by removing higher stores.
        a[offset + 0] = (byte)(v >> 40); // Removed from candidate list
        a[offset + 1] = (byte)(v >> 32); // Removed from candidate list
        a[offset + 2] = (byte)(v >> 24); // The 4 following stores are on the candidate list
        a[offset + 3] = (byte)(v >> 16); // and they are successfully merged on big endian platforms.
        a[offset + 4] = (byte)(v >> 8);
        a[offset + 5] = (byte)(v >> 0);
        return new Object[]{ a };
    }
}
