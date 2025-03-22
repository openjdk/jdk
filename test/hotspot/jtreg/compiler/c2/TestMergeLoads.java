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

    interface TestFunction {
        Object run(boolean isWarmUp, int rnd);
    }

    Map<String, Map<String, TestFunction>> testGroups = new HashMap<String, Map<String, TestFunction>>();

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMergeLoads.class);
        framework.addFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");

        for (String arg: args) {
            switch (args[0]) {
                case "aligned"     -> { framework.addFlags("-XX:-UseUnalignedAccesses"); }
                case "unaligned"   -> { framework.addFlags("-XX:+UseUnalignedAccesses"); }
                case "StressIGVN"   -> { framework.addFlags("-XX:+StressIGVN"); }
                default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
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

        // Get long in little endian
        testGroups.put("test2", new HashMap<String,TestFunction>());
        testGroups.get("test2").put("test2R", (_,_) -> { return test2R(aB.clone()); });
        testGroups.get("test2").put("test2a", (_,_) -> { return test2a(aB.clone()); });
        testGroups.get("test2").put("test2b", (_,_) -> { return test2b(aB.clone()); });
        testGroups.get("test2").put("test2c", (_,_) -> { return test2c(aB.clone()); });
        testGroups.get("test2").put("test2d", (_,_) -> { return test2d(aB.clone()); });
        testGroups.get("test2").put("test2e", (_,_) -> { return test2e(aB.clone()); });

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
    }

    static void set_random(byte[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)RANDOM.nextInt();
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

    @Warmup(100)
    @Run(test = {"test1a",
                 "test1b",
                 "test1c",
                 "test1d",
                 "test1e",

                 "test2a",
                 "test2b",
                 "test2c",
                 "test2d",
                 "test2e",

                 "test3a",
                 "test3b",
                 "test3c",

                 "test4a",
                 "test4b",
                 "test4c",

                 "test5a",
                 "test5b",

                 "test6a",
                 "test6b",

                 "test7a",
                 "test7b",

                 "test8a",
                 "test8b",
                })
    public void runTests(RunInfo info) {
        // Repeat many times, so that we also have multiple iterations for post-warmup to potentially recompile
        int iters = info.isWarmUp() ? 1_000 : 50_000;
        for (int iter = 0; iter < iters; iter++) {
            // Write random values to inputs
            set_random(aB);
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
                        checkEQ(gold, result, "group " + group_name + ", gold " + gold_name + ", test " + name);
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
}
