/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8324751
 * @key randomness
 * @summary Test Speculative Aliasing checks in SuperWord
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestAliasing nCOH_nAV_ySAC
 * @run driver compiler.loopopts.superword.TestAliasing nCOH_yAV_ySAC
 * @run driver compiler.loopopts.superword.TestAliasing yCOH_nAV_ySAC
 * @run driver compiler.loopopts.superword.TestAliasing yCOH_yAV_ySAC
 * @run driver compiler.loopopts.superword.TestAliasing nCOH_nAV_nSAC
 * @run driver compiler.loopopts.superword.TestAliasing nCOH_yAV_nSAC
 * @run driver compiler.loopopts.superword.TestAliasing yCOH_nAV_nSAC
 * @run driver compiler.loopopts.superword.TestAliasing yCOH_yAV_nSAC
 */

package compiler.loopopts.superword;

import jdk.test.lib.Utils;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import static compiler.lib.generators.Generators.G;
import compiler.lib.generators.Generator;

/**
 * More complicated test cases can be found in {@link TestAliasingFuzzing}.
 */
public class TestAliasing {
    static int SIZE = 1024*8;
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final Generator INT_GEN = G.ints();

    // Invariants used in tests.
    public static int INVAR_ZERO = 0;

    // Original data.
    public static byte[] ORIG_AB = fillRandom(new byte[SIZE]);
    public static byte[] ORIG_BB = fillRandom(new byte[SIZE]);
    public static int[]  ORIG_AI = fillRandom(new int[SIZE]);
    public static int[]  ORIG_BI = fillRandom(new int[SIZE]);

    // The data we use in the tests. It is initialized from ORIG_* every time.
    public static byte[] AB = new byte[SIZE];
    public static byte[] BB = new byte[SIZE];
    public static int[]  AI = new int[SIZE];
    public static int[]  BI = new int[SIZE];

    // Parallel to data above, but for use in reference methods.
    public static byte[] AB_REFERENCE = new byte[SIZE];
    public static byte[] BB_REFERENCE = new byte[SIZE];
    public static int[]  AI_REFERENCE = new int[SIZE];
    public static int[]  BI_REFERENCE = new int[SIZE];

    interface TestFunction {
        void run();
    }

    // Map of goldTests, i.e. tests that work with a golds value generated from the same test method,
    // at the beginning when we are still executing in the interpreter.
    Map<String,TestFunction> goldTests = new HashMap<String,TestFunction>();

    // Map of gold, the results from the first run before compilation, one per goldTests entry.
    Map<String,Object> golds = new HashMap<String,Object>();

    // Map of referenceTests, i.e. tests that have a reference implementation that is run with the interpreter.
    // The TestFunction must run both the test and reference methods.
    Map<String,TestFunction> referenceTests = new HashMap<String,TestFunction>();

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestAliasing.class);
        switch (args[0]) {
            case "nCOH_nAV_ySAC" -> { framework.addFlags("-XX:+UnlockExperimentalVMOptions", "-XX:-UseCompactObjectHeaders", "-XX:-AlignVector", "-XX:+UseAutoVectorizationSpeculativeAliasingChecks"); }
            case "nCOH_yAV_ySAC" -> { framework.addFlags("-XX:+UnlockExperimentalVMOptions", "-XX:-UseCompactObjectHeaders", "-XX:+AlignVector", "-XX:+UseAutoVectorizationSpeculativeAliasingChecks"); }
            case "yCOH_nAV_ySAC" -> { framework.addFlags("-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders", "-XX:-AlignVector", "-XX:+UseAutoVectorizationSpeculativeAliasingChecks"); }
            case "yCOH_yAV_ySAC" -> { framework.addFlags("-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders", "-XX:+AlignVector", "-XX:+UseAutoVectorizationSpeculativeAliasingChecks"); }
            case "nCOH_nAV_nSAC" -> { framework.addFlags("-XX:+UnlockExperimentalVMOptions", "-XX:-UseCompactObjectHeaders", "-XX:-AlignVector", "-XX:-UseAutoVectorizationSpeculativeAliasingChecks"); }
            case "nCOH_yAV_nSAC" -> { framework.addFlags("-XX:+UnlockExperimentalVMOptions", "-XX:-UseCompactObjectHeaders", "-XX:+AlignVector", "-XX:-UseAutoVectorizationSpeculativeAliasingChecks"); }
            case "yCOH_nAV_nSAC" -> { framework.addFlags("-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders", "-XX:-AlignVector", "-XX:-UseAutoVectorizationSpeculativeAliasingChecks"); }
            case "yCOH_yAV_nSAC" -> { framework.addFlags("-XX:+UnlockExperimentalVMOptions", "-XX:+UseCompactObjectHeaders", "-XX:+AlignVector", "-XX:-UseAutoVectorizationSpeculativeAliasingChecks"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
    }

    public TestAliasing() {
        // Add all goldTests to list
        goldTests.put("copy_B_sameIndex_noalias",         () -> { copy_B_sameIndex_noalias(AB, BB); });
        goldTests.put("copy_B_sameIndex_alias",           () -> { copy_B_sameIndex_alias(AB, AB); });
        goldTests.put("copy_B_differentIndex_noalias",    () -> { copy_B_differentIndex_noalias(AB, BB); });
        goldTests.put("copy_B_differentIndex_noalias_v2", () -> { copy_B_differentIndex_noalias_v2(); });
        goldTests.put("copy_B_differentIndex_alias",      () -> { copy_B_differentIndex_alias(AB, AB); });

        goldTests.put("copy_I_sameIndex_noalias",         () -> { copy_I_sameIndex_noalias(AI, BI); });
        goldTests.put("copy_I_sameIndex_alias",           () -> { copy_I_sameIndex_alias(AI, AI); });
        goldTests.put("copy_I_differentIndex_noalias",    () -> { copy_I_differentIndex_noalias(AI, BI); });
        goldTests.put("copy_I_differentIndex_alias",      () -> { copy_I_differentIndex_alias(AI, AI); });

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : goldTests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            init();
            test.run();
            Object gold = snapshotCopy();
            golds.put(name, gold);
        }

        referenceTests.put("fill_B_sameArray_alias", () -> {
            int invar1 = RANDOM.nextInt(64);
            int invar2 = RANDOM.nextInt(64);
            test_fill_B_sameArray_alias(AB, AB, invar1, invar2);
            reference_fill_B_sameArray_alias(AB_REFERENCE, AB_REFERENCE, invar1, invar2);
        });
        referenceTests.put("fill_B_sameArray_noalias", () -> {
            // The accesses either start at the middle and go out,
            // or start from opposite sides and meet in the middle.
            // But they never overlap.
            //      <------|------>
            //      ------>|<------
            //
            // This tests that the checks we emit are not too relaxed.
            int middle = SIZE / 2 + RANDOM.nextInt(-256, 256);
            int limit = SIZE / 3 + RANDOM.nextInt(256);
            int invar1 = middle;
            int invar2 = middle;
            if (RANDOM.nextBoolean()) {
                invar1 -= limit;
                invar2 += limit;
            }
            test_fill_B_sameArray_noalias(AB, AB, invar1, invar2, limit);
            reference_fill_B_sameArray_noalias(AB_REFERENCE, AB_REFERENCE, invar1, invar2, limit);
        });
    }

    public static void init() {
        System.arraycopy(ORIG_AB, 0, AB, 0, SIZE);
        System.arraycopy(ORIG_BB, 0, BB, 0, SIZE);
        System.arraycopy(ORIG_AI, 0, AI, 0, SIZE);
        System.arraycopy(ORIG_BI, 0, BI, 0, SIZE);
    }

    public static void initReference() {
        System.arraycopy(ORIG_AB, 0, AB_REFERENCE, 0, SIZE);
        System.arraycopy(ORIG_BB, 0, BB_REFERENCE, 0, SIZE);
        System.arraycopy(ORIG_AI, 0, AI_REFERENCE, 0, SIZE);
        System.arraycopy(ORIG_BI, 0, BI_REFERENCE, 0, SIZE);
    }

    public static Object snapshotCopy() {
        return new Object[] {
            AB.clone(), BB.clone(),
            AI.clone(), BI.clone()
        };
    }

    public static Object snapshot() {
        return new Object[] {
            AB, BB,
            AI, BI
        };
    }

    public static Object snapshotReference() {
        return new Object[] {
            AB_REFERENCE, BB_REFERENCE,
            AI_REFERENCE, BI_REFERENCE
        };
    }

    @Warmup(100)
    @Run(test = {"copy_B_sameIndex_noalias",
                 "copy_B_sameIndex_alias",
                 "copy_B_differentIndex_noalias",
                 "copy_B_differentIndex_noalias_v2",
                 "copy_B_differentIndex_alias",
                 "copy_I_sameIndex_noalias",
                 "copy_I_sameIndex_alias",
                 "copy_I_differentIndex_noalias",
                 "copy_I_differentIndex_alias",
                 "test_fill_B_sameArray_alias",
                 "test_fill_B_sameArray_noalias"})
    public void runTests() {
        for (Map.Entry<String,TestFunction> entry : goldTests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            // Recall gold value from before compilation
            Object gold = golds.get(name);
            // Compute new result
            init();
            test.run();
            Object result = snapshot();
            // Compare gold and new result
            try {
                Verify.checkEQ(gold, result);
            } catch (VerifyException e) {
                throw new RuntimeException("Verify failed for " + name, e);
            }
        }

        for (Map.Entry<String,TestFunction> entry : referenceTests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            // Init data for test and reference
            init();
            initReference();
            // Run test and reference
            test.run();
            // Capture results from test and reference
            Object result = snapshot();
            Object expected = snapshotReference();
            // Compare expected and new result
            try {
                Verify.checkEQ(expected, result);
            } catch (VerifyException e) {
                throw new RuntimeException("Verify failed for " + name, e);
            }
        }
    }

    static byte[] fillRandom(byte[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte)(int)INT_GEN.next();
        }
        return a;
    }

    static int[] fillRandom(int[] a) {
        G.fill(INT_GEN, a);
        return a;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Should always vectorize, no speculative runtime check required.
    static void copy_B_sameIndex_noalias(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
          b[i] = a[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Should always vectorize, no speculative runtime check required.
    static void copy_B_sameIndex_alias(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
          b[i] = a[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.STORE_VECTOR, "= 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Without speculative runtime check we cannot know that there is no aliasing.
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // We use speculative runtime checks, they never fail, so no multiversioning required.
    // With AlignVector we cannot prove that both accesses are alignable.
    static void copy_B_differentIndex_noalias(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
          b[i] = a[i + INVAR_ZERO];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.STORE_VECTOR, "= 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Without speculative runtime check we cannot know that there is no aliasing.
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // We use speculative runtime checks, they never fail, so no multiversioning required.
    // With AlignVector we cannot prove that both accesses are alignable.
    //
    // Same as "copy_B_differentIndex_noalias, but somehow loading from fields rather
    // than arguments does not lead to vectorization.
    static void copy_B_differentIndex_noalias_v2() {
        for (int i = 0; i < AB.length; i++) {
            BB[i] = AB[i + INVAR_ZERO];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.STORE_VECTOR, "= 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Without speculative runtime check we cannot know that there is no aliasing.
    @IR(counts = {IRNode.LOAD_VECTOR_B,            "> 0",
                  IRNode.STORE_VECTOR,             "> 0",
                  ".*pre .* multiversion_fast.*",  "= 1",
                  ".*main .* multiversion_fast.*", "= 1",
                  ".*post .* multiversion_fast.*", "= 2", // vectorized and scalar versions
                  ".*multiversion_slow.*",         "= 2", // main and post (pre-loop only has a single iteration)
                  ".*multiversion.*",              "= 6"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true",
                      "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // We use speculative runtime checks, it fails and so we do need multiversioning.
    // With AlignVector we cannot prove that both accesses are alignable.
    static void copy_B_differentIndex_alias(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
          b[i] = a[i + INVAR_ZERO];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Should always vectorize, no speculative runtime check required.
    static void copy_I_sameIndex_noalias(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
          b[i] = a[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Should always vectorize, no speculative runtime check required.
    static void copy_I_sameIndex_alias(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
          b[i] = a[i];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.STORE_VECTOR, "= 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Without speculative runtime check we cannot know that there is no aliasing.
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // We use speculative runtime checks, they never fail, so no multiversioning required.
    // With AlignVector we cannot prove that both accesses are alignable.
    static void copy_I_differentIndex_noalias(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
          b[i] = a[i + INVAR_ZERO];
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.STORE_VECTOR, "= 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Without speculative runtime check we cannot know that there is no aliasing.
    @IR(counts = {IRNode.LOAD_VECTOR_I,            "> 0",
                  IRNode.STORE_VECTOR,             "> 0",
                  ".*pre .* multiversion_fast.*",  "= 1",
                  ".*main .* multiversion_fast.*", "= 1",
                  ".*post .* multiversion_fast.*", "= 2", // vectorized and scalar versions
                  ".*multiversion_slow.*",         "= 2", // main and post (pre-loop only has a single iteration)
                  ".*multiversion.*",              "= 6"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true",
                      "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // We use speculative runtime checks, it fails and so we do need multiversioning.
    // With AlignVector we cannot prove that both accesses are alignable.
    static void copy_I_differentIndex_alias(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            b[i] = a[i + INVAR_ZERO];
        }
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Without speculative runtime check we cannot know that there is no aliasing.
    @IR(counts = {IRNode.STORE_VECTOR,             "> 0",
                  ".*pre .* multiversion_fast.*",  "= 1",
                  ".*main .* multiversion_fast.*", "= 1",
                  ".*post .* multiversion_fast.*", "= 2", // vectorized and scalar versions
                  ".*multiversion_slow.*",         "= 2", // main and post (pre-loop only has a single iteration)
                  ".*multiversion.*",              "= 6"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true",
                      "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // We use speculative runtime checks, it fails and so we do need multiversioning.
    // With AlignVector we cannot prove that both accesses are alignable.
    //
    // FYI: invar1 and invar2 are small values, only used to test that everything runs
    //      correctly with at different offsets / with different alignment.
    static void test_fill_B_sameArray_alias(byte[] a, byte[] b, int invar1, int invar2) {
        for (int i = 0; i < a.length - 100; i++) {
            a[i + invar1] = (byte)0x0a;
            b[a.length - i - 1 - invar2] = (byte)0x0b;
        }
    }

    @DontCompile
    static void reference_fill_B_sameArray_alias(byte[] a, byte[] b, int invar1, int invar2) {
        for (int i = 0; i < a.length - 100; i++) {
            a[i + invar1] = (byte)0x0a;
            b[a.length - i - 1 - invar2] = (byte)0x0b;
        }
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR, "= 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Without speculative runtime check we cannot know that there is no aliasing.
    @IR(counts = {IRNode.STORE_VECTOR,             "> 0",
                  ".*multiversion.*",              "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true",
                      "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // We use speculative runtime checks, and they should not fail, so no multiversioning.
    static void test_fill_B_sameArray_noalias(byte[] a, byte[] b, int invar1, int invar2, int limit) {
        for (int i = 0; i < limit; i++) {
            a[invar1 + i] = (byte)0x0a;
            b[invar2 - i] = (byte)0x0b;
        }
    }

    @DontCompile
    static void reference_fill_B_sameArray_noalias(byte[] a, byte[] b, int invar1, int invar2, int limit) {
        for (int i = 0; i < limit; i++) {
            a[invar1 + i] = (byte)0x0a;
            b[invar2 - i] = (byte)0x0b;
        }
    }
}
