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

package compiler.loopopts.superword;

import jdk.test.lib.Utils;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import static compiler.lib.generators.Generators.G;
import compiler.lib.generators.Generator;

/*
 * @test
 * @bug 8324751
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
 * @run driver compiler.loopopts.superword.TestAliasing noSlowLoopOptimizations
 */

public class TestAliasing {
    static int SIZE = 1024*8;
    static int SIZE_FINAL = 1024*8;
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final Generator INT_GEN = G.ints();

    // Invariants used in tests.
    public static int INVAR_ZERO = 0;

    // Original data.
    public static byte[] ORIG_AB = fillRandom(new byte[SIZE]);
    public static byte[] ORIG_BB = fillRandom(new byte[SIZE]);
    public static int[] ORIG_AI = fillRandom(new int[SIZE]);
    public static int[] ORIG_BI = fillRandom(new int[SIZE]);

    // The data we use in the tests. It is initialized from ORIG_* every time.
    public static byte[] AB = new byte[SIZE];
    public static byte[] BB = new byte[SIZE];
    public static int[] AI = new int[SIZE];
    public static int[] BI = new int[SIZE];

    // List of tests
    Map<String,TestFunction> tests = new HashMap<String,TestFunction>();

    // List of gold, the results from the first run before compilation
    Map<String,Object> golds = new HashMap<String,Object>();

    interface TestFunction {
        void run();
    }

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
            case "noSlowLoopOptimizations" -> { framework.addFlags("-XX:+UnlockExperimentalVMOptions", "-XX:-LoopMultiversioningOptimizeSlowLoop"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
    }

    public TestAliasing() {
        // Add all tests to list
        tests.put("copy_B_sameIndex_noalias",         () -> { copy_B_sameIndex_noalias(AB, BB); });
        tests.put("copy_B_sameIndex_alias",           () -> { copy_B_sameIndex_alias(AB, AB); });
        tests.put("copy_B_differentIndex_noalias",    () -> { copy_B_differentIndex_noalias(AB, BB); });
        tests.put("copy_B_differentIndex_noalias_v2", () -> { copy_B_differentIndex_noalias_v2(); });
        tests.put("copy_B_differentIndex_alias",      () -> { copy_B_differentIndex_alias(AB, AB); });

        tests.put("copy_I_sameIndex_noalias",         () -> { copy_I_sameIndex_noalias(AI, BI); });
        tests.put("copy_I_sameIndex_alias",           () -> { copy_I_sameIndex_alias(AI, AI); });
        tests.put("copy_I_differentIndex_noalias",    () -> { copy_I_differentIndex_noalias(AI, BI); });
        tests.put("copy_I_differentIndex_alias",      () -> { copy_I_differentIndex_alias(AI, AI); });

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            init();
            test.run();
            Object gold = snapshotCopy();
            golds.put(name, gold);
        }
    }

    public static void init() {
        System.arraycopy(ORIG_AB, 0, AB, 0, SIZE);
        System.arraycopy(ORIG_BB, 0, BB, 0, SIZE);
        System.arraycopy(ORIG_AI, 0, AI, 0, SIZE);
        System.arraycopy(ORIG_BI, 0, BI, 0, SIZE);
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

    @Warmup(100)
    @Run(test = {"copy_B_sameIndex_noalias",
                 "copy_B_sameIndex_alias",
                 "copy_B_differentIndex_noalias",
                 "copy_B_differentIndex_noalias_v2",
                 "copy_B_differentIndex_alias",
                 "copy_I_sameIndex_noalias",
                 "copy_I_sameIndex_alias",
                 "copy_I_differentIndex_noalias",
                 "copy_I_differentIndex_alias"})
    public void runTests() {
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
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
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Same as "copy_B_differentIndex_noalias, but somehow loading from fields rather
    // than arguments does not lead to vectorization.
    // Probably related to JDK-8348096, issue with RangeCheck elimination.
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
                      "LoopMultiversioningOptimizeSlowLoop", "true",
                      "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // We use speculative runtime checks, it fails and so we do need multiversioning.
    // With AlignVector we cannot prove that both accesses are alignable.
    @IR(counts = {IRNode.LOAD_VECTOR_B,            "> 0",
                  IRNode.STORE_VECTOR,             "> 0",
                  ".*pre .* multiversion_fast.*",  "= 1",
                  ".*main .* multiversion_fast.*", "= 1",
                  ".*post .* multiversion_fast.*", "= 2", // vectorized and scalar versions
                  ".*multiversion_delayed_slow.*", "= 1", // effect from flag -> stays delayed
                  ".*multiversion.*",              "= 5"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true",
                      "LoopMultiversioningOptimizeSlowLoop", "false", // slow_loop stays delayed
                      "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
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
                      "LoopMultiversioningOptimizeSlowLoop", "true",
                      "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // We use speculative runtime checks, it fails and so we do need multiversioning.
    // With AlignVector we cannot prove that both accesses are alignable.
    @IR(counts = {IRNode.LOAD_VECTOR_I,            "> 0",
                  IRNode.STORE_VECTOR,             "> 0",
                  ".*pre .* multiversion_fast.*",  "= 1",
                  ".*main .* multiversion_fast.*", "= 1",
                  ".*post .* multiversion_fast.*", "= 2", // vectorized and scalar versions
                  ".*multiversion_delayed_slow.*", "= 1", // effect from flag -> stays delayed
                  ".*multiversion.*",              "= 5"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true",
                      "LoopMultiversioningOptimizeSlowLoop", "false", // slow_loop stays delayed
                      "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void copy_I_differentIndex_alias(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
          b[i] = a[i + INVAR_ZERO];
        }
    }
}
