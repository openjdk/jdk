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

package compiler.loopopts.superword;

import java.util.Map;
import java.util.HashMap;
import jdk.test.lib.Utils;
import java.util.Random;
import jdk.incubator.vector.*;

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

    private static final VectorSpecies<Integer> SPECIES_I = IntVector.SPECIES_PREFERRED;

    interface TestFunction {
        Object run();
    }

    Map<String, Map<String, TestFunction>> testGroups = new HashMap<String, Map<String, TestFunction>>();

    int[] aI;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags("--add-modules=jdk.incubator.vector");
        framework.start();
    }

    public TestVectorAlgorithms () {
        // IMPORTANT:
        //   If you want to use some array but do NOT modify it: just use it.
        //   If you want to use it and DO want to modify it: clone it. This
        //   ensures that each test gets a separate copy, and that when we
        //   capture the modified arrays they are different for every method
        //   and run.
        testGroups.put("reduceAddI", new HashMap<String,TestFunction>());
        testGroups.get("reduceAddI").put("reduceAddI_loop",                           () -> { return reduceAddI_loop(aI); });
        testGroups.get("reduceAddI").put("reduceAddI_reassociate",                    () -> { return reduceAddI_reassociate(aI); });
        testGroups.get("reduceAddI").put("reduceAddI_VectorAPI_naive",                () -> { return reduceAddI_VectorAPI_naive(aI); });
        testGroups.get("reduceAddI").put("reduceAddI_VectorAPI_reduction_after_loop", () -> { return reduceAddI_VectorAPI_reduction_after_loop(aI); });
    }

    @Warmup(100)
    @Run(test = {"reduceAddI_loop",
                 "reduceAddI_reassociate",
                 "reduceAddI_VectorAPI_naive",
                 "reduceAddI_VectorAPI_reduction_after_loop"})
    public void runTests(RunInfo info) {
        // Repeat many times, so that we also have multiple iterations for post-warmup to potentially recompile
        int iters = info.isWarmUp() ? 1 : 20;
        for (int iter = 0; iter < iters; iter++) {
            // Set up random inputs, random size is important to stress tails.
            int size = 100_000 + RANDOM.nextInt(10_000);
            aI = new int[size];
            G.fill(INT_GEN, aI);

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
        int sum = 0;
        for (int i = 0; i < a.length; i++) {
            // Relying on simple reduction loop should vectorize since JDK26.
            sum += a[i];
        }
        return sum;
    }

    @Test
    public int reduceAddI_reassociate(int[] a) {
        int sum = 0;
        int i;
        for (i = 0; i < a.length - 3; i+=4) {
            // Unroll 4x, reassociate inside.
            sum += a[i] + a[i + 1] + a[i + 2] + a[i + 3];
        }
        for (; i < a.length; i++) {
            // Tail
            sum += a[i];
        }
        return sum;
    }

    @Test
    public int reduceAddI_VectorAPI_naive(int[] a) {
        var sum = 0;
        int i;
        for (i = 0; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            // reduceLanes in loop is better than scalar performance, but still
            // relatively slow.
            sum += v.reduceLanes(VectorOperators.ADD);
        }
        for (; i < a.length; i++) {
            sum += a[i];
        }
        return sum;
    }

    @Test
    public int reduceAddI_VectorAPI_reduction_after_loop(int[] a) {
        var acc = IntVector.broadcast(SPECIES_I, 0);
        int i;
        for (i = 0; i < SPECIES_I.loopBound(a.length); i += SPECIES_I.length()) {
            IntVector v = IntVector.fromArray(SPECIES_I, a, i);
            // Element-wide addition into a vector of partial sums is much faster.
            // Now, we only need to do a reduceLanes after the loop.
            // This works because int-addition is associative and commutative.
            acc = acc.add(v);
        }
        int sum = acc.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) {
            sum += a[i];
        }
        return sum;
    }
}
