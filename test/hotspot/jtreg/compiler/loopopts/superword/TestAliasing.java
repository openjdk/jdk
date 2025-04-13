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

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Array;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.nio.ByteOrder;

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
    static int RANGE = 1024*8;
    static int RANGE_FINAL = 1024*8;
    private static final Random RANDOM = Utils.getRandomInstance();

    // Inputs
    byte[] aB;
    byte[] bB;
    byte mB = (byte)31;
    short[] aS;
    short[] bS;
    short mS = (short)0xF0F0;
    int[] aI;
    int[] bI;
    int mI = 0xF0F0F0F0;
    long[] aL;
    long[] bL;
    long mL = 0xF0F0F0F0F0F0F0F0L;

    // List of tests
    Map<String,TestFunction> tests = new HashMap<String,TestFunction>();

    // List of gold, the results from the first run before compilation
    Map<String,Object[]> golds = new HashMap<String,Object[]>();

    interface TestFunction {
        Object[] run();
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
        tests.put("test4a",      () -> { return test4a(aS.clone(), bS.clone()); });
        tests.put("test4a_alias",() -> { short[] x = aS.clone(); return test4a_alias(x, x); });
        // TODO: remove old tests, add new ones.
        //       Especially also the not-working one from the benchmark.
        //       And maybe fix up generation and verification too!

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            Object[] gold = test.run();
            golds.put(name, gold);
        }
    }

    @Warmup(100)
    @Run(test = {"test4a",
                 "test4a_alias"})
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
    static Object[] test4a(short[] a, short[] b) {
        for (int i = 0; i < RANGE-64; i++) {
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
    static Object[] test4a_alias(short[] a, short[] b) {
        for (int i = 0; i < RANGE-64; i++) {
          b[i+2] = a[i+0];
        }
        return new Object[]{ a, b };
    }
}
