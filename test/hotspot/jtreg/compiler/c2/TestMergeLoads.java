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
import static compiler.lib.verify.Verify.*;
import jdk.test.lib.Utils;
import jdk.internal.misc.Unsafe;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

/*
 * @test
 * @bug 8345845
 * @summary Test merging of consecutive loads
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 *
 * @run main compiler.c2.TestMergeLoads unaligned
 * @run main compiler.c2.TestMergeLoads aligned
 *
 */

/*
 * @test
 * @bug 8345845
 * @summary Test merging of consecutive loads
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 *
 * @run main compiler.c2.TestMergeLoads unaligned StressIGVN
 * @run main compiler.c2.TestMergeLoads aligned StressIGVN
 *
 */
public class TestMergeLoads {
    static int RANGE = 1000;
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Random RANDOM = Utils.getRandomInstance();

    // Inputs
    byte[] aB = new byte[RANGE];
    char[] aC = new char[RANGE];
    short[] aS = new short[RANGE];
    int[] aI = new int[RANGE];

    long aN = UNSAFE.allocateMemory(RANGE);

    interface TestFunction {
        Object run(boolean isWarmUp, int rnd);
    }

    Map<String, Map<String, TestFunction>> testGroups = new HashMap<String, Map<String, TestFunction>>();

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMergeLoads.class);
        framework.addFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");

        for (String arg: args) {
            switch (arg) {
                case "aligned"     -> { framework.addFlags("-XX:-UseUnalignedAccesses"); }
                case "unaligned"   -> { framework.addFlags("-XX:+UseUnalignedAccesses"); }
                case "StressIGVN"   -> { framework.addFlags("-XX:+StressIGVN"); }
                default -> { throw new RuntimeException("Test argument not recognized: " + arg); }
            }
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
        testGroups.get("test1").put("test1d", (_,_) -> { return test1d(aB.clone()); });
        testGroups.get("test1").put("test1e", (_,_) -> { return test1e(aB.clone()); });
        testGroups.get("test1").put("test1f", (_,_) -> { return test1f(aB.clone()); });
        testGroups.get("test1").put("test1g", (_,_) -> { return test1g(aB.clone()); });
        testGroups.get("test1").put("test1h", (_,_) -> { return test1h(aN); });

        // Get long in little endian
        testGroups.put("test2", new HashMap<String,TestFunction>());
        testGroups.get("test2").put("test2R", (_,_) -> { return test2R(aB.clone()); });
        testGroups.get("test2").put("test2a", (_,_) -> { return test2a(aB.clone()); });
        testGroups.get("test2").put("test2b", (_,_) -> { return test2b(aB.clone()); });
        testGroups.get("test2").put("test2c", (_,_) -> { return test2c(aB.clone()); });
        testGroups.get("test2").put("test2d", (_,_) -> { return test2d(aB.clone()); });
        testGroups.get("test2").put("test2e", (_,_) -> { return test2e(aB.clone()); });
        testGroups.get("test2").put("test2f", (_,_) -> { return test2f(aB.clone()); });
        testGroups.get("test2").put("test2g", (_,_) -> { return test2g(aB.clone()); });
        testGroups.get("test2").put("test2h", (_,_) -> { return test2h(aN); });

        // Get int in big endian
        testGroups.put("test3", new HashMap<String,TestFunction>());
        testGroups.get("test3").put("test3R", (_,_) -> { return test3R(aB.clone()); });
        testGroups.get("test3").put("test3a", (_,_) -> { return test3a(aB.clone()); });
        testGroups.get("test3").put("test3b", (_,_) -> { return test3b(aB.clone()); });
        testGroups.get("test3").put("test3c", (_,_) -> { return test3c(aB.clone()); });
        testGroups.get("test3").put("test3d", (_,_) -> { return test3d(aB.clone()); });
        testGroups.get("test3").put("test3e", (_,_) -> { return test3e(aB.clone()); });
        testGroups.get("test3").put("test3f", (_,_) -> { return test3f(aB.clone()); });
        testGroups.get("test3").put("test3g", (_,_) -> { return test3g(aB.clone()); });

        // Get long in big endian
        testGroups.put("test4", new HashMap<String,TestFunction>());
        testGroups.get("test4").put("test4R", (_,_) -> { return test4R(aB.clone()); });
        testGroups.get("test4").put("test4a", (_,_) -> { return test4a(aB.clone()); });
        testGroups.get("test4").put("test4b", (_,_) -> { return test4b(aB.clone()); });
        testGroups.get("test4").put("test4c", (_,_) -> { return test4c(aB.clone()); });
        testGroups.get("test4").put("test4d", (_,_) -> { return test4d(aB.clone()); });
        testGroups.get("test4").put("test4e", (_,_) -> { return test4e(aB.clone()); });
        testGroups.get("test4").put("test4f", (_,_) -> { return test4f(aB.clone()); });

        // Merge char as int
        testGroups.put("test5", new HashMap<String,TestFunction>());
        testGroups.get("test5").put("test5R", (_,_) -> { return test5R(aC.clone()); });
        testGroups.get("test5").put("test5a", (_,_) -> { return test5a(aC.clone()); });
        testGroups.get("test5").put("test5b", (_,_) -> { return test5b(aC.clone()); });

        // Merge char as long
        testGroups.put("test6", new HashMap<String,TestFunction>());
        testGroups.get("test6").put("test6R", (_,_) -> { return test6R(aC.clone()); });
        testGroups.get("test6").put("test6a", (_,_) -> { return test6a(aC.clone()); });
        testGroups.get("test6").put("test6b", (_,_) -> { return test6b(aC.clone()); });

        // Merge short as int
        testGroups.put("test7", new HashMap<String,TestFunction>());
        testGroups.get("test7").put("test7R", (_,_) -> { return test7R(aS.clone()); });
        testGroups.get("test7").put("test7a", (_,_) -> { return test7a(aS.clone()); });
        testGroups.get("test7").put("test7b", (_,_) -> { return test7b(aS.clone()); });

        // Merge short as long
        testGroups.put("test8", new HashMap<String,TestFunction>());
        testGroups.get("test8").put("test8R", (_,_) -> { return test8R(aS.clone()); });
        testGroups.get("test8").put("test8a", (_,_) -> { return test8a(aS.clone()); });
        testGroups.get("test8").put("test8b", (_,_) -> { return test8b(aS.clone()); });

        // Merge int as long
        testGroups.put("test9", new HashMap<String,TestFunction>());
        testGroups.get("test9").put("test9R", (_,_) -> { return test9R(aI.clone()); });
        testGroups.get("test9").put("test9a", (_,_) -> { return test9a(aI.clone()); });
        testGroups.get("test9").put("test9b", (_,_) -> { return test9b(aI.clone()); });

        // Shift value is not aligned
        testGroups.put("test10", new HashMap<String,TestFunction>());
        testGroups.get("test10").put("test10R", (_,_) -> { return test10R(aB.clone(), aC.clone(), aS.clone(), aI.clone()); });
        testGroups.get("test10").put("test10a", (_,_) -> { return test10a(aB.clone(), aC.clone(), aS.clone(), aI.clone()); });

        // Mask value is not aligned
        testGroups.put("test11", new HashMap<String,TestFunction>());
        testGroups.get("test11").put("test11R", (_,_) -> { return test11R(aB.clone(), aC.clone(), aS.clone(), aI.clone()); });
        testGroups.get("test11").put("test11a", (_,_) -> { return test11a(aB.clone(), aC.clone(), aS.clone(), aI.clone()); });

        // Load value has other usage
        testGroups.put("test12", new HashMap<String,TestFunction>());
        testGroups.get("test12").put("test12R", (_,_) -> { return test12R(aB.clone()); });
        testGroups.get("test12").put("test12a", (_,_) -> { return test12a(aB.clone()); });

        // Load value is not masked
        testGroups.put("test13", new HashMap<String,TestFunction>());
        testGroups.get("test13").put("test13R", (_,_) -> { return test13R(aB.clone()); });
        testGroups.get("test13").put("test13a", (_,_) -> { return test13a(aB.clone()); });

        // Merged value is combined with other operator
        testGroups.put("test14", new HashMap<String,TestFunction>());
        testGroups.get("test14").put("test14R", (_,_) -> { return test14R(aS.clone()); });
        testGroups.get("test14").put("test14a", (_,_) -> { return test14a(aS.clone()); });
        testGroups.get("test14").put("test14b", (_,_) -> { return test14b(aS.clone()); });

        // Mix different loads
        testGroups.put("test100", new HashMap<String,TestFunction>());
        testGroups.get("test100").put("test100R", (_,_) -> { return test100R(aB.clone(), aC.clone(), aS.clone(), aI.clone()); });
        testGroups.get("test100").put("test100a", (_,_) -> { return test100a(aB.clone(), aC.clone(), aS.clone(), aI.clone()); });
    }

    static void set_random(byte[] a, long addr) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)RANDOM.nextInt();
            UNSAFE.putByte(addr + i, a[i]);
        }
    }

    static void set_random(char[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (char)RANDOM.nextInt();
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

    @Warmup(100)
    @Run(test = {"test1a",
                 "test1b",
                 "test1c",
                 "test1d",
                 "test1e",
                 "test1f",
                 "test1g",
                 "test1h",

                 "test2a",
                 "test2b",
                 "test2c",
                 "test2d",
                 "test2e",
                 "test2f",
                 "test2g",
                 "test2h",

                 "test3a",
                 "test3b",
                 "test3c",
                 "test3d",
                 "test3e",
                 "test3f",
                 "test3g",

                 "test4a",
                 "test4b",
                 "test4c",
                 "test4d",
                 "test4e",
                 "test4f",

                 "test5a",
                 "test5b",

                 "test6a",
                 "test6b",

                 "test7a",
                 "test7b",

                 "test8a",
                 "test8b",

                 "test9a",
                 "test9b",

                 "test10a",

                 "test11a",

                 "test12a",

                 "test13a",

                 "test14a",
                 "test14b",

                 "test100a",
                })
    public void runTests(RunInfo info) {
        // Repeat many times, so that we also have multiple iterations for post-warmup to potentially recompile
        int iters = info.isWarmUp() ? 1_000 : 50_000;
        for (int iter = 0; iter < iters; iter++) {
            // Write random values to inputs
            set_random(aB, aN);     // setup for both byte array and natvie
            set_random(aC);
            set_random(aS);

            // Run all tests
            for (Map.Entry<String, Map<String,TestFunction>> group_entry : testGroups.entrySet()) {
                String group_name = group_entry.getKey();
                Map<String, TestFunction> group = group_entry.getValue();
                Object gold = null;
                String gold_name = "NONE";
                for (Map.Entry<String,TestFunction> entry : group.entrySet()) {
                    String name = entry.getKey();
                    TestFunction test = entry.getValue();
                    Object result = test.run(info.isWarmUp(), iter);
                    if (gold == null) {
                        gold = result;
                        gold_name = name;
                    } else {
                        checkEQ(gold, result);
                    }
                }
            }
        }
    }

    /**
     * Group 1: get int in little endian mode
     */
    @DontCompile
    static int test1R(byte[] a) {
      return  (a[0] & 0xff)         |
             ((a[1] & 0xff) << 8 )  |
             ((a[2] & 0xff) << 16)  |
             ((a[3] & 0xff) << 24);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I,  "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static int test1a(byte[] a) {
      return  (a[0] & 0xff)         |
             ((a[1] & 0xff) << 8 )  |
             ((a[2] & 0xff) << 16)  |
             ((a[3] & 0xff) << 24);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int test1b(byte[] a) {
      return UNSAFE.getIntUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, /* big-endian */ false);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I,  "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static int test1c(byte[] a) {
      return (UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)        |
            ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 8 ) |
            ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) << 16) |
            ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff) << 24);
    }

    // Shuffle order test
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I,  "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static int test1d(byte[] a) {
      return ((a[3] & 0xff) << 24) |
              (a[0] & 0xff)        |
             ((a[2] & 0xff) << 16) |
             ((a[1] & 0xff) << 8 );
    }

    // Shuffle order test
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I,  "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static int test1e(byte[] a) {
      return ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 8 ) |
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff) << 24) |
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) << 16) |
              (UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff);
    }

    // volatile loads can not be merged
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int test1f(byte[] a) {
      return  (UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)       |
             ((UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 8 )|
             ((UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) << 16)|
             ((UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff) << 24);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int test1g(byte[] a) {
      return  (UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)       |
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 8 )|
             ((UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) << 16)|
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff) << 24);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B,  "0",
          IRNode.LOAD_UB, "0",
          IRNode.LOAD_S,  "0",
          IRNode.LOAD_US, "0",
          IRNode.LOAD_I,  "1",
          IRNode.LOAD_L,  "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int test1h(long address) {
      return  (UNSAFE.getByte(address + 0) & 0xff)       |
             ((UNSAFE.getByte(address + 1) & 0xff) << 8 )|
             ((UNSAFE.getByte(address + 2) & 0xff) << 16)|
             ((UNSAFE.getByte(address + 3) & 0xff) << 24);
    }

    /**
     * Group 2: get long in little endian mode
     */
    @DontCompile
    static long test2R(byte[] a) {
      return ((long)(a[0] & 0xff)       )|
            (((long)(a[1] & 0xff)) << 8 )|
            (((long)(a[2] & 0xff)) << 16)|
            (((long)(a[3] & 0xff)) << 24)|
            (((long)(a[4] & 0xff)) << 32)|
            (((long)(a[5] & 0xff)) << 40)|
            (((long)(a[6] & 0xff)) << 48)|
            (((long)(a[7] & 0xff)) << 56);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"big-endian", "true"})
    static long test2a(byte[] a) {
      return ((long)(a[0] & 0xff)       )|
            (((long)(a[1] & 0xff)) << 8 )|
            (((long)(a[2] & 0xff)) << 16)|
            (((long)(a[3] & 0xff)) << 24)|
            (((long)(a[4] & 0xff)) << 32)|
            (((long)(a[5] & 0xff)) << 40)|
            (((long)(a[6] & 0xff)) << 48)|
            (((long)(a[7] & 0xff)) << 56);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long test2b(byte[] a) {
      return UNSAFE.getLongUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, /* big-endian */ false);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static long test2c(byte[] a) {
      return ((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)       )|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff)) << 8 )|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff)) << 16)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)) << 24)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xff)) << 32)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5) & 0xff)) << 40)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xff)) << 48)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7) & 0xff)) << 56);
    }

    // Shuffle test
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"big-endian", "true"})
    static long test2d(byte[] a) {
      return (((long)(a[3] & 0xff)) << 24)|
             (((long)(a[6] & 0xff)) << 48)|
             (((long)(a[2] & 0xff)) << 16)|
             (((long)(a[1] & 0xff)) << 8 )|
             (((long)(a[4] & 0xff)) << 32)|
              ((long)(a[0] & 0xff)       )|
             (((long)(a[5] & 0xff)) << 40)|
             (((long)(a[7] & 0xff)) << 56);
    }

    // Shuffle test
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static long test2e(byte[] a) {
      return ((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)       )|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7) & 0xff)) << 56)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)) << 24)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5) & 0xff)) << 40)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xff)) << 32)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff)) << 16)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xff)) << 48)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff)) << 8 );
    }

    // can not merge volatile load
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long test2f(byte[] a) {
      return ((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)       )|
            (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff)) << 8 )|
            (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff)) << 16)|
            (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)) << 24)|
            (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xff)) << 32)|
            (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5) & 0xff)) << 40)|
            (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xff)) << 48)|
            (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7) & 0xff)) << 56);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "7",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long test2g(byte[] a) {
      return ((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)       )|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff)) << 8 )|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff)) << 16)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)) << 24)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xff)) << 32)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5) & 0xff)) << 40)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xff)) << 48)|
            (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7) & 0xff)) << 56);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B,  "0",
          IRNode.LOAD_UB, "0",
          IRNode.LOAD_S,  "0",
          IRNode.LOAD_US, "0",
          IRNode.LOAD_I,  "0",
          IRNode.LOAD_L,  "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long test2h(long address) {
      return  ((long)(UNSAFE.getByte(address + 0) & 0xff))       |
             (((long)(UNSAFE.getByte(address + 1) & 0xff)) << 8 )|
             (((long)(UNSAFE.getByte(address + 2) & 0xff)) << 16)|
             (((long)(UNSAFE.getByte(address + 3) & 0xff)) << 24)|
             (((long)(UNSAFE.getByte(address + 4) & 0xff)) << 32)|
             (((long)(UNSAFE.getByte(address + 5) & 0xff)) << 40)|
             (((long)(UNSAFE.getByte(address + 6) & 0xff)) << 48)|
             (((long)(UNSAFE.getByte(address + 7) & 0xff)) << 56);
    }

    /**
     * Group 3: get int in big endian mode
     */
    @DontCompile
    static int test3R(byte[] a) {
      return ((a[0] & 0xff) << 24) |
             ((a[1] & 0xff) << 16) |
             ((a[2] & 0xff) <<  8) |
              (a[3] & 0xff);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true", "riscv64", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "1"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "0"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static int test3a(byte[] a) {
      return ((a[0] & 0xff) << 24) |
             ((a[1] & 0xff) << 16) |
             ((a[2] & 0xff) <<  8) |
              (a[3] & 0xff);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int test3b(byte[] a) {
      return UNSAFE.getIntUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, /* big-endian */ true);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true", "riscv64", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "1"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "0"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static int test3c(byte[] a) {
      return ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff) << 24) |
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 16) |
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) <<  8) |
              (UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff);
    }

    // Shuffle test
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true", "riscv64", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "1"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "0"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static int test3d(byte[] a) {
      return  (a[3] & 0xff)        |
             ((a[2] & 0xff) <<  8) |
             ((a[1] & 0xff) << 16) |
             ((a[0] & 0xff) << 24);
    }

    // Shuffle test
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true", "riscv64", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "1"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "3",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "0"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_I, "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static int test3e(byte[] a) {
      return ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 16) |
              (UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)        |
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff) << 24) |
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) <<  8);
    }

    // Can not merge volatile load
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int test3f(byte[] a) {
      return ((UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff) << 24) |
             ((UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 16) |
             ((UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) <<  8) |
              (UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff);
    }

    // Can not merge volatile load
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int test3g(byte[] a) {
      return ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff) << 24) |
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff) << 16) |
             ((UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff) <<  8) |
              (UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff);
    }

    /**
     * Group 4: get long in big endian mode
     */
    @DontCompile
    static long test4R(byte[] a) {
      return (((long)(a[0] & 0xff)) << 56)|
             (((long)(a[1] & 0xff)) << 48)|
             (((long)(a[2] & 0xff)) << 40)|
             (((long)(a[3] & 0xff)) << 32)|
             (((long)(a[4] & 0xff)) << 24)|
             (((long)(a[5] & 0xff)) << 16)|
             (((long)(a[6] & 0xff)) <<  8)|
              ((long)(a[7] & 0xff));
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true", "riscv64", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_L, "0"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static long test4a(byte[] a) {
      return (((long)(a[0] & 0xff)) << 56)|
             (((long)(a[1] & 0xff)) << 48)|
             (((long)(a[2] & 0xff)) << 40)|
             (((long)(a[3] & 0xff)) << 32)|
             (((long)(a[4] & 0xff)) << 24)|
             (((long)(a[5] & 0xff)) << 16)|
             (((long)(a[6] & 0xff)) <<  8)|
              ((long)(a[7] & 0xff));
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"
        })
    static long test4b(byte[] a) {
      return UNSAFE.getLongUnaligned(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET, /* big-endian */ true);
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true", "riscv64", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_L, "0"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static long test4c(byte[] a) {
      return (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)) << 56)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff)) << 48)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff)) << 40)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)) << 32)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xff)) << 24)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5) & 0xff)) << 16)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xff)) <<  8)|
              ((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7) & 0xff));
    }

    // Shuffle test
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true", "riscv64", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_L, "0"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static long test4d(byte[] a) {
      return (((long)(a[0] & 0xff)) << 56)|
             (((long)(a[5] & 0xff)) << 16)|
             (((long)(a[2] & 0xff)) << 40)|
             (((long)(a[1] & 0xff)) << 48)|
             (((long)(a[4] & 0xff)) << 24)|
             (((long)(a[6] & 0xff)) <<  8)|
             (((long)(a[3] & 0xff)) << 32)|
              ((long)(a[7] & 0xff));
    }

    // Shuffle test
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatformAnd = {"little-endian", "true", "riscv64", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "1"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "true"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_L, "0"
        },
        applyIfPlatform   = {"riscv64", "true"},
        applyIfAnd = {"UseUnalignedAccesses", "true", "UseZbb", "false"})
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.REVERSE_BYTES_L, "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"},
        applyIfPlatform = {"big-endian", "true"})
    static long test4e(byte[] a) {
      return  ((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7) & 0xff))       |
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)) << 56)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff)) << 40)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xff)) <<  8)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)) << 32)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xff)) << 24)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5) & 0xff)) << 16)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff)) << 48);
    }

    // Can not merge volatile load
    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.REVERSE_BYTES_L, "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long test4f(byte[] a) {
      return (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xff)) << 56)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1) & 0xff)) << 48)|
             (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xff)) << 40)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 3) & 0xff)) << 32)|
             (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xff)) << 24)|
             (((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 5) & 0xff)) << 16)|
             (((long)(UNSAFE.getByteVolatile(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xff)) <<  8)|
              ((long)(UNSAFE.getByte(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 7) & 0xff));
    }

    /**
     * Group 5: merge char as int
     */
    @DontCompile
    static int[] test5R(char[] a) {
      int i1 = (((int)(a[0] & 0xffff)) << 16)|
                ((int)(a[1] & 0xffff));
      int i2 =  ((int)(a[2] & 0xffff))       |
               (((int)(a[3] & 0xffff)) << 16);
      return new int[]{i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_I_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int[] test5a(char[] a) {
      /* only one group which access array in platform order can be merged */
      int i1 = (((int)(a[0] & 0xffff)) << 16)|
                ((int)(a[1] & 0xffff));
      int i2 =  ((int)(a[2] & 0xffff))       |
               (((int)(a[3] & 0xffff)) << 16);
      return new int[]{i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_I_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int[] test5b(char[] a) {
      /* only one group which access array in platform order can be merged */
      int i1 = (((int)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xffff)) << 16)|
                ((int)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xffff));
      int i2 =  ((int)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xffff))       |
               (((int)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xffff)) << 16);
      return new int[]{i1, i2};
    }

    /**
     * Group 6: merge char as long
     */
    @DontCompile
    static long[] test6R(char[] a) {
      long i1 = (((long)(a[0] & 0xffff)) << 48)|
                (((long)(a[1] & 0xffff)) << 32)|
                (((long)(a[2] & 0xffff)) << 16)|
                 ((long)(a[3] & 0xffff));
      long i2 =  ((long)(a[4] & 0xffff))       |
                (((long)(a[5] & 0xffff)) << 16)|
                (((long)(a[6] & 0xffff)) << 32)|
                (((long)(a[7] & 0xffff)) << 48);
      return new long[] {i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_I_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test6a(char[] a) {
      /* only one group which access array in platform order can be merged */
      long i1 = (((long)(a[0] & 0xffff)) << 48)|
                (((long)(a[1] & 0xffff)) << 32)|
                (((long)(a[2] & 0xffff)) << 16)|
                 ((long)(a[3] & 0xffff));
      long i2 =  ((long)(a[4] & 0xffff))       |
                (((long)(a[5] & 0xffff)) << 16)|
                (((long)(a[6] & 0xffff)) << 32)|
                (((long)(a[7] & 0xffff)) << 48);
      return new long[] {i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_I_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test6b(char[] a) {
      /* only one group which access array in platform order can be merged */
      long i1 = (((long)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0 ) & 0xffff)) << 48)|
                (((long)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2 ) & 0xffff)) << 32)|
                (((long)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4 ) & 0xffff)) << 16)|
                 ((long)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6 ) & 0xffff));
      long i2 =  ((long)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 ) & 0xffff))       |
                (((long)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 10) & 0xffff)) << 16)|
                (((long)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 12) & 0xffff)) << 32)|
                (((long)(UNSAFE.getChar(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 14) & 0xffff)) << 48);
      return new long[] {i1, i2};
    }

    /**
     * Group 7: merge shorts as int
     */
    @DontCompile
    static int[] test7R(short[] a) {
      int i1 = (((int)(a[0] & 0xffff)) << 16)|
                ((int)(a[1] & 0xffff));
      int i2 =  ((int)(a[2] & 0xffff))       |
               (((int)(a[3] & 0xffff)) << 16);
      return new int[] {i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_US_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_I_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int[] test7a(short[] a) {
      /* only one group which access array in platform order can be merged */
      int i1 = (((int)(a[0] & 0xffff)) << 16)|
                ((int)(a[1] & 0xffff));
      int i2 =  ((int)(a[2] & 0xffff))       |
               (((int)(a[3] & 0xffff)) << 16);
      return new int[] {i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_US_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_I_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_L_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static int[] test7b(short[] a) {
      /* only one group which access array in platform order can be merged */
      int i1 = (((int)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0) & 0xffff)) << 16)|
                ((int)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2) & 0xffff));
      int i2 =  ((int)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4) & 0xffff))       |
               (((int)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6) & 0xffff)) << 16);
      return new int[] {i1, i2};
    }

    /**
     * Group 8: merge short as long
     */
    @DontCompile
    static long[] test8R(short[] a) {
      long i1 = (((long)(a[0] & 0xffff)) << 48)|
                (((long)(a[1] & 0xffff)) << 32)|
                (((long)(a[2] & 0xffff)) << 16)|
                 ((long)(a[3] & 0xffff));
      long i2 =  ((long)(a[4] & 0xffff))       |
                (((long)(a[5] & 0xffff)) << 16)|
                (((long)(a[6] & 0xffff)) << 32)|
                (((long)(a[7] & 0xffff)) << 48);
      return new long[] {i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_I_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test8a(short[] a) {
      /* only one group which access array in platform order can be merged */
      long i1 = (((long)(a[0] & 0xffff)) << 48)|
                (((long)(a[1] & 0xffff)) << 32)|
                (((long)(a[2] & 0xffff)) << 16)|
                 ((long)(a[3] & 0xffff));
      long i2 =  ((long)(a[4] & 0xffff))       |
                (((long)(a[5] & 0xffff)) << 16)|
                (((long)(a[6] & 0xffff)) << 32)|
                (((long)(a[7] & 0xffff)) << 48);
      return new long[] {i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_I_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test8b(short[] a) {
      /* only one group which access array in platform order can be merged */
      long i1 = (((long)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0 ) & 0xffff)) << 48)|
                (((long)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 2 ) & 0xffff)) << 32)|
                (((long)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4 ) & 0xffff)) << 16)|
                 ((long)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 6 ) & 0xffff));
      long i2 =  ((long)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 ) & 0xffff))       |
                (((long)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 10) & 0xffff)) << 16)|
                (((long)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 12) & 0xffff)) << 32)|
                (((long)(UNSAFE.getShort(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 14) & 0xffff)) << 48);
      return new long[] {i1, i2};
    }

    /**
     * Group 9: merge int as long
     */
    @DontCompile
    static long[] test9R(int[] a) {
      long i1 = (((long)(a[0] & 0xffffffff)) << 32)|
                 ((long)(a[1] & 0xffffffff));
      long i2 =  ((long)(a[2] & 0xffffffff))       |
                (((long)(a[3] & 0xffffffff)) << 32);
      return new long[] {i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_L_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test9a(int[] a) {
      long i1 = (((long)(a[0] & 0xffffffff)) << 32)|
                 ((long)(a[1] & 0xffffffff));
      long i2 =  ((long)(a[2] & 0xffffffff))       |
                (((long)(a[3] & 0xffffffff)) << 32);
      return new long[] {i1, i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_L_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0"
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test9b(int[] a) {
      /* only one group which access array in platform order can be merged */
      long i1 = (((long)(UNSAFE.getInt(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 0 ) & 0xffffffff)) << 32)|
                 ((long)(UNSAFE.getInt(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 4 ) & 0xffffffff));
      long i2 =  ((long)(UNSAFE.getInt(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 ) & 0xffffffff))       |
                (((long)(UNSAFE.getInt(a, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 12) & 0xffffffff)) << 32);
      return new long[] {i1, i2};
    }

    /**
     * Group 10: shift value is not aligned
     */
    @DontCompile
    static long[] test10R(byte[] aB, char[] aC, short[] aS, int[] aI) {
      long i1 = ((long)(aB[4]  & 0xff))        |
               (((long)(aB[5]  & 0xff)) << 8 ) |
               (((long)(aB[6]  & 0xff)) << 16) |
               (((long)(aB[7]  & 0xff)) << 24) |
               (((long)(aB[8]  & 0xff)) << 32) |
               (((long)(aB[9]  & 0xff)) << 40) |
               (((long)(aB[10] & 0xff)) << 47) |          // unaligned shift
               (((long)(aB[11] & 0xff)) << 56);

      long i2 = ((long)(aC[0]  & 0xffff))        |
               (((long)(aC[1]  & 0xffff)) << 16) |
               (((long)(aC[2]  & 0xffff)) << 32) |
               (((long)(aC[3]  & 0xffff)) << 47);         // unaligned shift

      long i3 = ((long)(aS[0]  & 0xffff))        |
               (((long)(aS[1]  & 0xffff)) << 16) |
               (((long)(aS[2]  & 0xffff)) << 33) |        // unaligned shift
               (((long)(aS[3]  & 0xffff)) << 48);

      long i4 = ((long)(aI[0]  & 0xffffffff))        |
               (((long)(aI[1]  & 0xffffffff)) << 30);     // unaligned shift
      return new long[] {i1, i2, i3, i4};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",

          IRNode.LOAD_S_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_I_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",

          IRNode.LOAD_S_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_I_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",

          IRNode.LOAD_I_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_L_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test10a(byte[] aB, char[] aC, short[] aS, int[] aI) {
      long i1 = ((long)(aB[4]  & 0xff))        |
               (((long)(aB[5]  & 0xff)) << 8 ) |
               (((long)(aB[6]  & 0xff)) << 16) |
               (((long)(aB[7]  & 0xff)) << 24) |
               (((long)(aB[8]  & 0xff)) << 32) |
               (((long)(aB[9]  & 0xff)) << 40) |
               (((long)(aB[10] & 0xff)) << 47) |          // unaligned shift
               (((long)(aB[11] & 0xff)) << 56);

      long i2 = ((long)(aC[0]  & 0xffff))        |
               (((long)(aC[1]  & 0xffff)) << 16) |
               (((long)(aC[2]  & 0xffff)) << 32) |
               (((long)(aC[3]  & 0xffff)) << 47);         // unaligned shift

      long i3 = ((long)(aS[0]  & 0xffff))        |
               (((long)(aS[1]  & 0xffff)) << 16) |
               (((long)(aS[2]  & 0xffff)) << 33) |        // unaligned shift
               (((long)(aS[3]  & 0xffff)) << 48);

      long i4 = ((long)(aI[0]  & 0xffffffff))        |
               (((long)(aI[1]  & 0xffffffff)) << 30);     // unaligned shift
      return new long[] {i1, i2, i3, i4};
    }

    /**
     * Group 11: mask value is not aligned
     */
    @DontCompile
    static long[] test11R(byte[] aB, char[] aC, short[] aS, int[] aI) {
      long i1 = ((long)(aB[4]  & 0xff))        |
               (((long)(aB[5]  & 0xff)) << 8 ) |
               (((long)(aB[6]  & 0xff)) << 16) |
               (((long)(aB[7]  & 0xff)) << 24) |
               (((long)(aB[8]  & 0xff)) << 32) |
               (((long)(aB[9]  & 0xff)) << 40) |
               (((long)(aB[10] & 0xfe)) << 48) |          // unaligned mask
               (((long)(aB[11] & 0xff)) << 56);

      long i2 = ((long)(aC[0]  & 0xfffe))        |        // unaligned mask
               (((long)(aC[1]  & 0xffff)) << 16) |
               (((long)(aC[2]  & 0xffff)) << 32) |
               (((long)(aC[3]  & 0xffff)) << 48);

      long i3 = ((long)(aS[0]  & 0xffff))        |
               (((long)(aS[1]  & 0xffff)) << 16) |
               (((long)(aS[2]  & 0xefff)) << 32) |        // unaligned mask
               (((long)(aS[3]  & 0xffff)) << 48);

      long i4 = ((long)(aI[0]  & 0xffffffff))        |
               (((long)(aI[1]  & 0xfffffff0)) << 32);     // unaligned mask
      return new long[] {i1, i2, i3, i4};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "8",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",

          IRNode.LOAD_S_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_I_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",

          IRNode.LOAD_S_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_I_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",

          IRNode.LOAD_I_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_L_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test11a(byte[] aB, char[] aC, short[] aS, int[] aI) {
      long i1 = ((long)(aB[4]  & 0xff))        |
               (((long)(aB[5]  & 0xff)) << 8 ) |
               (((long)(aB[6]  & 0xff)) << 16) |
               (((long)(aB[7]  & 0xff)) << 24) |
               (((long)(aB[8]  & 0xff)) << 32) |
               (((long)(aB[9]  & 0xff)) << 40) |
               (((long)(aB[10] & 0xfe)) << 48) |          // unaligned mask
               (((long)(aB[11] & 0xff)) << 56);

      long i2 = ((long)(aC[0]  & 0xfffe))        |        // unaligned mask
               (((long)(aC[1]  & 0xffff)) << 16) |
               (((long)(aC[2]  & 0xffff)) << 32) |
               (((long)(aC[3]  & 0xffff)) << 48);

      long i3 = ((long)(aS[0]  & 0xffff))        |
               (((long)(aS[1]  & 0xffff)) << 16) |
               (((long)(aS[2]  & 0xefff)) << 32) |        // unaligned mask
               (((long)(aS[3]  & 0xffff)) << 48);

      long i4 = ((long)(aI[0]  & 0xffffffff))        |
               (((long)(aI[1]  & 0xfffffff0)) << 32);     // unaligned mask
      return new long[] {i1, i2, i3, i4};
    }

    /**
     * Group 12: load value has other usage
     */
    @DontCompile
    static long[] test12R(byte[] aB) {
      long i1 = ((long)(aB[0] & 0xff))        |
               (((long)(aB[1] & 0xff)) << 8 ) |
               (((long)(aB[2] & 0xff)) << 16) |
               (((long)(aB[3] & 0xff)) << 24) |
               (((long)(aB[4] & 0xff)) << 32) |
               (((long)(aB[5] & 0xff)) << 40) |
               (((long)(aB[6] & 0xff)) << 48) |
               (((long)(aB[7] & 0xff)) << 56);
      return new long[] {i1, aB[7]};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "7",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test12a(byte[] aB) {
      byte tmp = aB[7];
      long i1 = ((long)(aB[0] & 0xff))        |
               (((long)(aB[1] & 0xff)) << 8 ) |
               (((long)(aB[2] & 0xff)) << 16) |
               (((long)(aB[3] & 0xff)) << 24) |
               (((long)(aB[4] & 0xff)) << 32) |
               (((long)(aB[5] & 0xff)) << 40) |
               (((long)(aB[6] & 0xff)) << 48) |
               (((long)(tmp & 0xff)) << 56);
      return new long[] {i1, tmp};
    }

    /**
     * Group 13: load value is not masked
     */
    @DontCompile
    static long[] test13R(byte[] aB) {
      long i1 = ((long)(aB[0] & 0xff))        |
                ((long)(aB[1] )        << 8 ) |
               (((long)(aB[2] & 0xff)) << 16) |
               (((long)(aB[3] & 0xff)) << 24) |
               (((long)(aB[4] & 0xff)) << 32) |
               (((long)(aB[5] & 0xff)) << 40) |
               (((long)(aB[6] & 0xff)) << 48) |
               (((long)(aB[7] )) << 56);
      return new long[] {i1, aB[7]};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "6",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test13a(byte[] aB) {
      long i1 = ((long)(aB[0] & 0xff))        |
                ((long)(aB[1]        ) << 8 ) |
               (((long)(aB[2] & 0xff)) << 16) |
               (((long)(aB[3] & 0xff)) << 24) |
               (((long)(aB[4] & 0xff)) << 32) |
               (((long)(aB[5] & 0xff)) << 40) |
               (((long)(aB[6] & 0xff)) << 48) |
               (((long)(aB[7] )) << 56);
      return new long[] {i1, aB[7]};
    }

    /**
     * Group 14: merged value is combined with other opeartor
     */
    @DontCompile
    static long[] test14R(short[] a) {
      /* only one group which access array in platform order can be merged */
      long i1 = (((long)(a[0] & 0xffff)) << 48)|
                (((long)(a[1] & 0xffff)) << 32)|
                (((long)(a[2] & 0xffff)) << 16)|
                 ((long)(a[3] & 0xffff))       |
                 ((long)(a[4] & 0xffff));
      long i2 =  ((long)(a[5] & 0xffff))       |
                (((long)(a[6] & 0xffff)) << 16)|
                (((long)(a[7] & 0xffff)) << 32)|
                (((long)(a[8] & 0xffff)) << 48)|
                (((long)(a[9] & 0xffff)));
      return new long[] {i1 | i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "6",
          IRNode.LOAD_I_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test14a(short[] a) {
      /* only one group which access array in platform order can be merged */
      long i1 = (((long)(a[0] & 0xffff)) << 48)|
                (((long)(a[1] & 0xffff)) << 32)|
                (((long)(a[2] & 0xffff)) << 16)|
                 ((long)(a[3] & 0xffff))       |
                 ((long)(a[4] & 0xffff));
      long i2 =  ((long)(a[5] & 0xffff))       |
                (((long)(a[6] & 0xffff)) << 16)|
                (((long)(a[7] & 0xffff)) << 32)|
                (((long)(a[8] & 0xffff)) << 48)|
                (((long)(a[9] & 0xffff)));
      return new long[] {i1 | i2};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_UB_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_S_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "6",
          IRNode.LOAD_I_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test14b(short[] a) {
      /* only one group which access array in platform order can be merged */
      long i1 = (((long)(a[0] & 0xffff)) << 48)|
                (((long)(a[1] & 0xffff)) << 32)|
                (((long)(a[2] & 0xffff)) << 16)|
                 ((long)(a[3] & 0xffff));
      long i2 =  ((long)(a[5] & 0xffff))       |
                (((long)(a[6] & 0xffff)) << 16)|
                (((long)(a[7] & 0xffff)) << 32)|
                (((long)(a[8] & 0xffff)) << 48);
      long i3 =  ((long)(a[4] & 0xffff)) | ((long)(a[9] & 0xffff));
      return new long[] {i1 | i2 |i3};
    }
    /**
     * Group 100: Mix different patterns
     */
    @DontCompile
    static long[] test100R(byte[] aB, char[] aC, short[] aS, int[] aI) {
      long i1 = ((long)(aB[0] & 0xff))        |
               (((long)(aB[1] & 0xff)) << 8 ) |
               (((long)(aB[2] & 0xff)) << 16) |
               (((long)(aB[3] & 0xff)) << 24) |
               (((long)(aB[4] & 0xff)) << 32) |
               (((long)(aB[5] & 0xff)) << 40) |
               (((long)(aB[6] & 0xff)) << 48) |
               (((long)(aB[7] & 0xff)) << 56);
      long i2 = ((long)(aB[2] & 0xff))        |
               (((long)(aB[3] & 0xff)) << 8 ) |
               (((long)(aB[4] & 0xff)) << 16) |
               (((long)(aB[5] & 0xff)) << 24) |
               (((long)(aB[6] & 0xff)) << 32) |
               (((long)(aB[7] & 0xff)) << 40) |
               (((long)(aB[8] & 0xff)) << 48) |
               (((long)(aB[9] & 0xff)) << 56);
      int i3 =  (aB[10] & 0xff)        +
               ((aB[11] & 0xff) << 8 ) +
               ((aB[12] & 0xff) << 16) +
               ((aB[13] & 0xff) << 24);
      int i4 =  (aB[14] & 0xff)        |
               ((aB[15] & 0xff) << 8 ) |
               ((aB[16] & 0xff) << 16) |
               ((aB[17] & 0xff) << 24);
      int i5 = (UNSAFE.getByte(aB, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 20) & 0xff)        |  // it can be merged
              ((UNSAFE.getByte(aB, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 21) & 0xff) << 8 ) |
              ((UNSAFE.getByte(aB, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 22) & 0xff) << 16) |
              ((UNSAFE.getByte(aB, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 23) & 0xff) << 24);
      long i6 = (((long)(aC[0] & 0xffff)) << 48)|
                (((long)(aC[1] & 0xffff)) << 32)|
                (((long)(aC[2] & 0xffff)) << 16)|
                 ((long)(aC[3] & 0xffff));
      long i7 =  ((long)(aC[4] & 0xffff))       |
                (((long)(aC[5] & 0xffff)) << 16)|
                (((long)(aC[6] & 0xffff)) << 32)|
                (((long)(aC[7] & 0xffff)) << 48);
      return new long[] {i1, i2, i3, i4, i5, i6, i7};
    }

    @Test
    @IR(counts = {
          IRNode.LOAD_B_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",
          IRNode.LOAD_UB_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "13",
          IRNode.LOAD_S_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "2",
          IRNode.LOAD_L_OF_CLASS,  "byte\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",

          IRNode.LOAD_S_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "4",
          IRNode.LOAD_I_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "char\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "1",

          IRNode.LOAD_S_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_US_OF_CLASS, "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_I_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "short\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",

          IRNode.LOAD_I_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
          IRNode.LOAD_L_OF_CLASS,  "int\\\\[int:>=0] \\\\(java/lang/Cloneable,java/io/Serializable\\\\)", "0",
        },
        applyIf = {"UseUnalignedAccesses", "true"})
    static long[] test100a(byte[] aB, char[] aC, short[] aS, int[] aI) {
      long i1 = ((long)(aB[0] & 0xff))        |
               (((long)(aB[1] & 0xff)) << 8 ) |
               (((long)(aB[2] & 0xff)) << 16) |
               (((long)(aB[3] & 0xff)) << 24) |
               (((long)(aB[4] & 0xff)) << 32) |
               (((long)(aB[5] & 0xff)) << 40) |
               (((long)(aB[6] & 0xff)) << 48) |
               (((long)(aB[7] & 0xff)) << 56);
      long i2 = ((long)(aB[2] & 0xff))        |
               (((long)(aB[3] & 0xff)) << 8 ) |
               (((long)(aB[4] & 0xff)) << 16) |
               (((long)(aB[5] & 0xff)) << 24) |
               (((long)(aB[6] & 0xff)) << 32) |
               (((long)(aB[7] & 0xff)) << 40) |
               (((long)(aB[8] & 0xff)) << 48) |
               (((long)(aB[9] & 0xff)) << 56);
      int i3 =  (aB[10] & 0xff)        +
               ((aB[11] & 0xff) << 8 ) +
               ((aB[12] & 0xff) << 16) +
               ((aB[13] & 0xff) << 24);
      int i4 =  (aB[14] & 0xff)        |     // it can be merged
               ((aB[15] & 0xff) << 8 ) |
               ((aB[16] & 0xff) << 16) |
               ((aB[17] & 0xff) << 24);
      int i5 = (UNSAFE.getByte(aB, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 20) & 0xff)        |  // it can be merged
              ((UNSAFE.getByte(aB, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 21) & 0xff) << 8 ) |
              ((UNSAFE.getByte(aB, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 22) & 0xff) << 16) |
              ((UNSAFE.getByte(aB, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 23) & 0xff) << 24);
      long i6 = (((long)(aC[0] & 0xffff)) << 48)|
                (((long)(aC[1] & 0xffff)) << 32)|
                (((long)(aC[2] & 0xffff)) << 16)|
                 ((long)(aC[3] & 0xffff));
      long i7 =  ((long)(aC[4] & 0xffff))       |
                (((long)(aC[5] & 0xffff)) << 16)|
                (((long)(aC[6] & 0xffff)) << 32)|
                (((long)(aC[7] & 0xffff)) << 48);
      return new long[] {i1, i2, i3, i4, i5, i6, i7};
    }
}
