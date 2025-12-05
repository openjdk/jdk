/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8373026
 * @summary Test auto vectorization and Vector API with some vector
 *          algorithms. Related benchmark: VectorAlgorithms.java
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class}
 */

package compiler.vectorization;

import java.util.Map;
import java.util.HashMap;
import jdk.test.lib.Utils;
import java.util.Random;

import compiler.lib.ir_framework.*;
import compiler.lib.generators.*;
import static compiler.lib.generators.Generators.G;
import compiler.lib.verify.*;

/**
 * The goal of this benchmark is to show the power of auto vectorization
 * and the Vector API.
 *
 * Please only modify this benchark in synchronization with the JMH benchmark:
 *   micro/org/openjdk/bench/vm/compiler/VectorAlgorithms.java
 */
public class TestVectorAlgorithms {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final RestrictableGenerator<Integer> INT_GEN = Generators.G.ints();

    interface TestFunction {
        Object run();
    }

    Map<String, Map<String, TestFunction>> testGroups = new HashMap<String, Map<String, TestFunction>>();

    int[] aI;
    int[] rI1;
    int[] rI2;
    int[] rI3;
    int[] rI4;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags("--add-modules=jdk.incubator.vector", "-XX:CompileCommand=inline,*VectorAlgorithmsImpl::*");
        framework.start();
    }

    public TestVectorAlgorithms () {
        // IMPORTANT:
        //   If you want to use some array but do NOT modify it: just use it.
        //   If you want to use it and DO want to modify it: clone it. This
        //   ensures that each test gets a separate copy, and that when we
        //   capture the modified arrays they are different for every method
        //   and run.
        //   An alternative to cloning is to use different return arrays for
        //   different implementations of the same group, e.g. rI1, rI2, ...
        testGroups.put("reduceAddI", new HashMap<String,TestFunction>());
        testGroups.get("reduceAddI").put("reduceAddI_loop",                           () -> { return reduceAddI_loop(aI); });
        testGroups.get("reduceAddI").put("reduceAddI_reassociate",                    () -> { return reduceAddI_reassociate(aI); });
        testGroups.get("reduceAddI").put("reduceAddI_VectorAPI_naive",                () -> { return reduceAddI_VectorAPI_naive(aI); });
        testGroups.get("reduceAddI").put("reduceAddI_VectorAPI_reduction_after_loop", () -> { return reduceAddI_VectorAPI_reduction_after_loop(aI); });

        testGroups.put("scanAddI", new HashMap<String,TestFunction>());
        testGroups.get("scanAddI").put("scanAddI_loop",                      () -> { return scanAddI_loop(aI, rI1); });
        testGroups.get("scanAddI").put("scanAddI_loop_reassociate",          () -> { return scanAddI_loop_reassociate(aI, rI2); });
        testGroups.get("scanAddI").put("scanAddI_VectorAPI_permute_add",     () -> { return scanAddI_VectorAPI_permute_add(aI, rI4); });

        testGroups.put("findMinIndex", new HashMap<String,TestFunction>());
        testGroups.get("findMinIndex").put("findMinIndex_loop",      () -> { return findMinIndex_loop(aI); });
        testGroups.get("findMinIndex").put("findMinIndex_VectorAPI", () -> { return findMinIndex_VectorAPI(aI); });

        testGroups.put("reverse", new HashMap<String,TestFunction>());
        testGroups.get("reverse").put("reverse_loop",      () -> { return reverse_loop(aI, rI1); });
        testGroups.get("reverse").put("reverse_VectorAPI", () -> { return reverse_VectorAPI(aI, rI2); });
    }

    @Warmup(100)
    @Run(test = {"reduceAddI_loop",
                 "reduceAddI_reassociate",
                 "reduceAddI_VectorAPI_naive",
                 "reduceAddI_VectorAPI_reduction_after_loop",
                 "scanAddI_loop",
                 "scanAddI_loop_reassociate",
                 "scanAddI_VectorAPI_permute_add",
                 "findMinIndex_loop",
                 "findMinIndex_VectorAPI",
                 "reverse_loop",
                 "reverse_VectorAPI"})
    public void runTests(RunInfo info) {
        // Repeat many times, so that we also have multiple iterations for post-warmup to potentially recompile
        int iters = info.isWarmUp() ? 1 : 20;
        for (int iter = 0; iter < iters; iter++) {
            // Set up random inputs, random size is important to stress tails.
            int size = 100_000 + RANDOM.nextInt(10_000);
            aI = new int[size];
            G.fill(INT_GEN, aI);
            //for (int i = 0; i < aI.length; i++) { aI[i] = i; }
            rI1 = new int[size];
            rI2 = new int[size];
            rI3 = new int[size];
            rI4 = new int[size];

            // Run all tests
            for (Map.Entry<String, Map<String,TestFunction>> group_entry : testGroups.entrySet()) {
                String group_name = group_entry.getKey();
                Map<String, TestFunction> group = group_entry.getValue();
                Object gold = null;
                String gold_name = "NONE";
                for (Map.Entry<String,TestFunction> entry : group.entrySet()) {
                    String name = entry.getKey();
                    TestFunction test = entry.getValue();
                    Object result = test.run();
                    if (gold == null) {
                        gold = result;
                        gold_name = name;
                    } else {
                        try {
                            Verify.checkEQ(gold, result);
                        } catch (VerifyException e) {
                            throw new RuntimeException("Verify.checkEQ failed for group " + group_name +
                                                       ", gold " + gold_name + ", test " + name, e);
                        }
                    }
                }
            }
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.ADD_REDUCTION_VI, "> 0",
                  IRNode.ADD_VI,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public int reduceAddI_loop(int[] a) {
        return VectorAlgorithmsImpl.reduceAddI_loop(a);
    }

    @Test
    public int reduceAddI_reassociate(int[] a) {
        return VectorAlgorithmsImpl.reduceAddI_reassociate(a);
    }

    @Test
    public int reduceAddI_VectorAPI_naive(int[] a) {
        return VectorAlgorithmsImpl.reduceAddI_VectorAPI_naive(aI);
    }

    @Test
    public int reduceAddI_VectorAPI_reduction_after_loop(int[] a) {
        return VectorAlgorithmsImpl.reduceAddI_VectorAPI_reduction_after_loop(aI);
    }

    @Test
    public Object scanAddI_loop(int[] a, int[] r) {
        return VectorAlgorithmsImpl.scanAddI_loop(a, r);
    }

    @Test
    public Object scanAddI_loop_reassociate(int[] a, int[] r) {
        return VectorAlgorithmsImpl.scanAddI_loop_reassociate(a, r);
    }

    @Test
    public Object scanAddI_VectorAPI_permute_add(int[] a, int[] r) {
        return VectorAlgorithmsImpl.scanAddI_VectorAPI_permute_add(a, r);
    }

    @Test
    public int findMinIndex_loop(int[] a) {
        return VectorAlgorithmsImpl.findMinIndex_loop(a);
    }

    @Test
    public int findMinIndex_VectorAPI(int[] a) {
        return VectorAlgorithmsImpl.findMinIndex_VectorAPI(a);
    }

    @Test
    public Object reverse_loop(int[] a, int[] r) {
        return VectorAlgorithmsImpl.reverse_loop(a, r);
    }

    @Test
    public Object reverse_VectorAPI(int[] a, int[] r) {
        return VectorAlgorithmsImpl.reverse_VectorAPI(a, r);
    }
}
