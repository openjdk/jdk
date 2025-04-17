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
 */

public class TestAliasing {
    static int SIZE = 1024*8;
    static int SIZE_FINAL = 1024*8;
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final Generator INT_GEN = G.ints();

    // Original data.
    public static int[] ORIG_AI = fillRandom(new int[SIZE]);
    public static int[] ORIG_BI = fillRandom(new int[SIZE]);

    // The data we use in the tests. It is initialized from ORIG_* every time.
    public static int[] AI = new int[SIZE];
    public static int[] BI = new int[SIZE];

    // List of tests
    Map<String,TestFunction> tests = new HashMap<String,TestFunction>();

    // List of gold, the results from the first run before compilation
    Map<String,Object> golds = new HashMap<String,Object>();

    interface TestFunction {
        Object run();
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
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
    }

    public TestAliasing() {
        // Add all tests to list
        tests.put("test4a",      () -> { return test4a(AI, BI); });
        tests.put("test4a_alias",() -> { return test4a_alias(AI, AI); });
        // TODO: remove old tests, add new ones.
        //       Especially also the not-working one from the benchmark.

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
        System.arraycopy(ORIG_AI, 0, AI, 0, SIZE);
        System.arraycopy(ORIG_BI, 0, BI, 0, SIZE);
    }

    public static Object snapshotCopy() {
        return new Object[] { AI.clone(), BI.clone() };
    }

    public static Object snapshot() {
        return new Object[] { AI, BI };
    }

    @Warmup(100)
    @Run(test = {"test4a",
                 "test4a_alias"})
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

    static int[] fillRandom(int[] a) {
        G.fill(INT_GEN, a);
        return a;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true"})
    // Cyclic dependency with distance 2 -> split into 2-packs
    @IR(counts = {IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true"})
    // Speculative aliasing check -> full vectorization.
    static Object[] test4a(int[] a, int[] b) {
        for (int i = 0; i < SIZE-64; i++) {
          b[i+2] = a[i+0];
        }
        return new Object[]{ a, b };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "= 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true"})
    // Cyclic dependency with distance 2 -> split into 2-packs
    @IR(counts = {IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_2, "> 0",
                  IRNode.STORE_VECTOR, "> 0",
                  ".*multiversion.*", "> 0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true"})
    // Speculative aliasing check with multiversioning -> full vectorization & split packs.
    static Object[] test4a_alias(int[] a, int[] b) {
        for (int i = 0; i < SIZE-64; i++) {
          b[i+2] = a[i+0];
        }
        return new Object[]{ a, b };
    }
}
