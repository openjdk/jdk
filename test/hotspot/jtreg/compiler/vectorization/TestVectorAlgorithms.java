/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=vanilla
 * @bug 8373026
 * @summary Test auto vectorization and Vector API with some vector
 *          algorithms. Related benchmark: VectorAlgorithms.java
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class}
 */

/*
 * @test id=noSuperWord
 * @bug 8373026
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class} -XX:-UseSuperWord
 */

/*
 * @test id=noOptimizeFill
 * @bug 8373026
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class} -XX:-OptimizeFill
 */

package compiler.vectorization;

import java.util.Map;
import java.util.HashMap;
import jdk.test.lib.Utils;
import java.util.Random;
import java.lang.foreign.*;

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
        Object run(int i);
    }

    Map<String, Map<String, TestFunction>> testGroups = new HashMap<String, Map<String, TestFunction>>();

    VectorAlgorithmsImpl.Data d;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags("--add-modules=jdk.incubator.vector",
                           "-XX:CompileCommand=inline,*VectorAlgorithmsImpl*::*");
        framework.addFlags(args);
        framework.start();
    }

    public TestVectorAlgorithms () {
        testGroups.put("fillI", new HashMap<String,TestFunction>());
        testGroups.get("fillI").put("fillI_loop",      i -> { return fillI_loop(d.rI1); });
        testGroups.get("fillI").put("fillI_VectorAPI", i -> { return fillI_VectorAPI(d.rI2); });
        testGroups.get("fillI").put("fillI_Arrays",    i -> { return fillI_Arrays(d.rI3); });

        testGroups.put("iotaI", new HashMap<String,TestFunction>());
        testGroups.get("iotaI").put("iotaI_loop",      i -> { return iotaI_loop(d.rI1); });
        testGroups.get("iotaI").put("iotaI_VectorAPI", i -> { return iotaI_VectorAPI(d.rI2); });

        testGroups.put("copyI", new HashMap<String,TestFunction>());
        testGroups.get("copyI").put("copyI_loop",             i -> { return copyI_loop(d.aI, d.rI1); });
        testGroups.get("copyI").put("copyI_VectorAPI",        i -> { return copyI_VectorAPI(d.aI, d.rI2); });
        testGroups.get("copyI").put("copyI_System_arraycopy", i -> { return copyI_System_arraycopy(d.aI, d.rI3); });

        testGroups.put("mapI", new HashMap<String,TestFunction>());
        testGroups.get("mapI").put("mapI_loop",      i -> { return mapI_loop(d.aI, d.rI1); });
        testGroups.get("mapI").put("mapI_VectorAPI", i -> { return mapI_VectorAPI(d.aI, d.rI2); });

        testGroups.put("reduceAddI", new HashMap<String,TestFunction>());
        testGroups.get("reduceAddI").put("reduceAddI_loop",                           i -> { return reduceAddI_loop(d.aI); });
        testGroups.get("reduceAddI").put("reduceAddI_reassociate",                    i -> { return reduceAddI_reassociate(d.aI); });
        testGroups.get("reduceAddI").put("reduceAddI_VectorAPI_naive",                i -> { return reduceAddI_VectorAPI_naive(d.aI); });
        testGroups.get("reduceAddI").put("reduceAddI_VectorAPI_reduction_after_loop", i -> { return reduceAddI_VectorAPI_reduction_after_loop(d.aI); });

        testGroups.put("dotProductF", new HashMap<String,TestFunction>());
        testGroups.get("dotProductF").put("dotProductF_loop",                           i -> { return dotProductF_loop(d.aF, d.bF); });
        testGroups.get("dotProductF").put("dotProductF_VectorAPI_naive",                i -> { return dotProductF_VectorAPI_naive(d.aF, d.bF); });
        testGroups.get("dotProductF").put("dotProductF_VectorAPI_reduction_after_loop", i -> { return dotProductF_VectorAPI_reduction_after_loop(d.aF, d.bF); });

        testGroups.put("hashCodeB", new HashMap<String,TestFunction>());
        testGroups.get("hashCodeB").put("hashCodeB_loop",         i -> { return hashCodeB_loop(d.aB); });
        testGroups.get("hashCodeB").put("hashCodeB_Arrays",       i -> { return hashCodeB_Arrays(d.aB); });
        testGroups.get("hashCodeB").put("hashCodeB_VectorAPI_v1", i -> { return hashCodeB_VectorAPI_v1(d.aB); });
        testGroups.get("hashCodeB").put("hashCodeB_VectorAPI_v2", i -> { return hashCodeB_VectorAPI_v2(d.aB); });

        testGroups.put("scanAddI", new HashMap<String,TestFunction>());
        testGroups.get("scanAddI").put("scanAddI_loop",                      i -> { return scanAddI_loop(d.aI, d.rI1); });
        testGroups.get("scanAddI").put("scanAddI_loop_reassociate",          i -> { return scanAddI_loop_reassociate(d.aI, d.rI2); });
        testGroups.get("scanAddI").put("scanAddI_VectorAPI_permute_add",     i -> { return scanAddI_VectorAPI_permute_add(d.aI, d.rI4); });

        testGroups.put("findMinIndexI", new HashMap<String,TestFunction>());
        testGroups.get("findMinIndexI").put("findMinIndexI_loop",      i -> { return findMinIndexI_loop(d.aI); });
        testGroups.get("findMinIndexI").put("findMinIndexI_VectorAPI", i -> { return findMinIndexI_VectorAPI(d.aI); });

        testGroups.put("findI", new HashMap<String,TestFunction>());
        testGroups.get("findI").put("findI_loop",      i -> { return findI_loop(d.aI, d.eI[i]); });
        testGroups.get("findI").put("findI_VectorAPI", i -> { return findI_VectorAPI(d.aI, d.eI[i]); });

        testGroups.put("reverseI", new HashMap<String,TestFunction>());
        testGroups.get("reverseI").put("reverseI_loop",      i -> { return reverseI_loop(d.aI, d.rI1); });
        testGroups.get("reverseI").put("reverseI_VectorAPI", i -> { return reverseI_VectorAPI(d.aI, d.rI2); });

        testGroups.put("filterI", new HashMap<String,TestFunction>());
        testGroups.get("filterI").put("filterI_loop",      i -> { return filterI_loop(d.aI, d.rI1, d.eI[i]); });
        testGroups.get("filterI").put("filterI_VectorAPI", i -> { return filterI_VectorAPI(d.aI, d.rI2, d.eI[i]); });

        testGroups.put("reduceAddIFieldsX4", new HashMap<String,TestFunction>());
        testGroups.get("reduceAddIFieldsX4").put("reduceAddIFieldsX4_loop",      i -> { return reduceAddIFieldsX4_loop(d.oopsX4, d.memX4); });
        testGroups.get("reduceAddIFieldsX4").put("reduceAddIFieldsX4_VectorAPI", i -> { return reduceAddIFieldsX4_VectorAPI(d.oopsX4, d.memX4); });

        testGroups.put("lowerCaseB", new HashMap<String,TestFunction>());
        testGroups.get("lowerCaseB").put("lowerCaseB_loop",         i -> { return lowerCaseB_loop(d.strB, d.rB1); });
        testGroups.get("lowerCaseB").put("lowerCaseB_VectorAPI_v1", i -> { return lowerCaseB_VectorAPI_v1(d.strB, d.rB2); });
        testGroups.get("lowerCaseB").put("lowerCaseB_VectorAPI_v2", i -> { return lowerCaseB_VectorAPI_v2(d.strB, d.rB3); });
    }

    @Warmup(100)
    @Run(test = {"fillI_loop",
                 "fillI_VectorAPI",
                 "fillI_Arrays",
                 "iotaI_loop",
                 "iotaI_VectorAPI",
                 "copyI_loop",
                 "copyI_VectorAPI",
                 "copyI_System_arraycopy",
                 "mapI_loop",
                 "mapI_VectorAPI",
                 "reduceAddI_loop",
                 "reduceAddI_reassociate",
                 "reduceAddI_VectorAPI_naive",
                 "reduceAddI_VectorAPI_reduction_after_loop",
                 "dotProductF_loop",
                 "dotProductF_VectorAPI_naive",
                 "dotProductF_VectorAPI_reduction_after_loop",
                 "hashCodeB_loop",
                 "hashCodeB_Arrays",
                 "hashCodeB_VectorAPI_v1",
                 "hashCodeB_VectorAPI_v2",
                 "scanAddI_loop",
                 "scanAddI_loop_reassociate",
                 "scanAddI_VectorAPI_permute_add",
                 "findMinIndexI_loop",
                 "findMinIndexI_VectorAPI",
                 "findI_loop",
                 "findI_VectorAPI",
                 "reverseI_loop",
                 "reverseI_VectorAPI",
                 "filterI_loop",
                 "filterI_VectorAPI",
                 "reduceAddIFieldsX4_loop",
                 "reduceAddIFieldsX4_VectorAPI",
                 "lowerCaseB_loop",
                 "lowerCaseB_VectorAPI_v1",
                 "lowerCaseB_VectorAPI_v2"})
    public void runTests(RunInfo info) {
        // Repeat many times, so that we also have multiple iterations for post-warmup to potentially recompile
        int iters = info.isWarmUp() ? 1 : 20;
        for (int iter = 0; iter < iters; iter++) {
            // Set up random inputs, random size is important to stress tails.
            int size = 100_000 + RANDOM.nextInt(10_000);
            int seed = RANDOM.nextInt();
            int numXObjects = 10_000;
            d = new VectorAlgorithmsImpl.Data(size, seed, numXObjects);

            // Run all tests
            for (Map.Entry<String, Map<String,TestFunction>> group_entry : testGroups.entrySet()) {
                String group_name = group_entry.getKey();
                Map<String, TestFunction> group = group_entry.getValue();
                Object gold = null;
                String gold_name = "NONE";
                for (Map.Entry<String,TestFunction> entry : group.entrySet()) {
                    String name = entry.getKey();
                    TestFunction test = entry.getValue();
                    Object result = test.run(iter);
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
    @IR(counts = {IRNode.REPLICATE_I,  "= 1",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIfAnd = {"UseSuperWord", "true", "OptimizeFill", "false"})
    @IR(counts = {".*CallLeafNoFP.*jint_fill.*", "= 1"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"OptimizeFill", "true"})
    // By default, the fill intrinsic "jint_fill" is used, but we can disable
    // the detection of the fill loop, and then we auto vectorize.
    public Object fillI_loop(int[] r) {
        return VectorAlgorithmsImpl.fillI_loop(r);
    }

    @Test
    @IR(counts = {IRNode.REPLICATE_I,  "= 1",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public Object fillI_VectorAPI(int[] r) {
        return VectorAlgorithmsImpl.fillI_VectorAPI(r);
    }

    @Test
    // Arrays.fill is not necessarily inlined, so we can't check
    // for vectors in the IR.
    public Object fillI_Arrays(int[] r) {
        return VectorAlgorithmsImpl.fillI_Arrays(r);
    }

    @Test
    @IR(counts = {IRNode.POPULATE_INDEX, "> 0",
                  IRNode.STORE_VECTOR,   "> 0"},
        applyIfCPUFeatureOr = {"avx2", "true", "sve", "true"},
        applyIf = {"UseSuperWord", "true"})
    // Note: the Vector API example below can also vectorize for AVX,
    //       because it does not use a PopulateIndex.
    public Object iotaI_loop(int[] r) {
        return VectorAlgorithmsImpl.iotaI_loop(r);
    }

    @Test
    @IR(counts = {IRNode.ADD_VI,       "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"})
    // Note: also works with NEON/asimd, but only with TieredCompilation.
    public Object iotaI_VectorAPI(int[] r) {
        return VectorAlgorithmsImpl.iotaI_VectorAPI(r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"UseSuperWord", "true"})
    public Object copyI_loop(int[] a, int[] r) {
        return VectorAlgorithmsImpl.copyI_loop(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public Object copyI_VectorAPI(int[] a, int[] r) {
        return VectorAlgorithmsImpl.copyI_VectorAPI(a, r);
    }

    @Test
    @IR(counts = {".*CallLeafNoFP.*jint_disjoint_arraycopy.*", "= 1"},
        phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public Object copyI_System_arraycopy(int[] a, int[] r) {
        return VectorAlgorithmsImpl.copyI_System_arraycopy(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.MUL_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"UseSuperWord", "true"})
    public Object mapI_loop(int[] a, int[] r) {
        return VectorAlgorithmsImpl.mapI_loop(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.MUL_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public Object mapI_VectorAPI(int[] a, int[] r) {
        return VectorAlgorithmsImpl.mapI_VectorAPI(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.ADD_REDUCTION_VI, "> 0",
                  IRNode.ADD_VI,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"UseSuperWord", "true"})
    public int reduceAddI_loop(int[] a) {
        return VectorAlgorithmsImpl.reduceAddI_loop(a);
    }

    @Test
    public int reduceAddI_reassociate(int[] a) {
        return VectorAlgorithmsImpl.reduceAddI_reassociate(a);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.ADD_REDUCTION_VI, "> 0"}, // reduceLanes inside loop
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public int reduceAddI_VectorAPI_naive(int[] a) {
        return VectorAlgorithmsImpl.reduceAddI_VectorAPI_naive(a);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.ADD_REDUCTION_V, "> 0",
                  IRNode.MUL_VF,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"UseSuperWord", "true"})
    // See also TestReduction.floatAddDotProduct
    public float dotProductF_loop(float[] a, float[] b) {
        return VectorAlgorithmsImpl.dotProductF_loop(a, b);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.ADD_REDUCTION_V, "> 0",
                  IRNode.MUL_VF,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"UseSuperWord", "true"})
    public float dotProductF_VectorAPI_naive(float[] a, float[] b) {
        return VectorAlgorithmsImpl.dotProductF_VectorAPI_naive(a, b);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.ADD_REDUCTION_V, "> 0",
                  IRNode.MUL_VF,          "> 0"},
        applyIfCPUFeature = {"sse4.1", "true"},
        applyIf = {"UseSuperWord", "true"})
    public float dotProductF_VectorAPI_reduction_after_loop(float[] a, float[] b) {
        return VectorAlgorithmsImpl.dotProductF_VectorAPI_reduction_after_loop(a, b);
    }

    @Test
    public int hashCodeB_loop(byte[] a) {
        return VectorAlgorithmsImpl.hashCodeB_loop(a);
    }

    @Test
    public int hashCodeB_Arrays(byte[] a) {
        return VectorAlgorithmsImpl.hashCodeB_Arrays(a);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B,    IRNode.VECTOR_SIZE_8, "> 0",
                  IRNode.VECTOR_CAST_B2I,  IRNode.VECTOR_SIZE_8, "> 0",
                  IRNode.MUL_VI,           IRNode.VECTOR_SIZE_8, "> 0",
                  IRNode.ADD_VI,           IRNode.VECTOR_SIZE_8, "> 0",
                  IRNode.ADD_REDUCTION_VI,                       "> 0"},
        applyIfCPUFeature = {"avx2", "true"})
    public int hashCodeB_VectorAPI_v1(byte[] a) {
        return VectorAlgorithmsImpl.hashCodeB_VectorAPI_v1(a);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B,    "> 0",
                  IRNode.MUL_VI,           "> 0",
                  IRNode.ADD_VI,           "> 0",
                  IRNode.ADD_REDUCTION_VI, "> 0"},
        applyIfCPUFeature = {"avx2", "true"})
    public int hashCodeB_VectorAPI_v2(byte[] a) {
        return VectorAlgorithmsImpl.hashCodeB_VectorAPI_v2(a);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.ADD_REDUCTION_VI, "> 0",
                  IRNode.ADD_VI,           "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public int reduceAddI_VectorAPI_reduction_after_loop(int[] a) {
        return VectorAlgorithmsImpl.reduceAddI_VectorAPI_reduction_after_loop(a);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.STORE_VECTOR,  "= 0"})
    // Currently does not vectorize, but might in the future.
    public Object scanAddI_loop(int[] a, int[] r) {
        return VectorAlgorithmsImpl.scanAddI_loop(a, r);
    }

    @Test
    public Object scanAddI_loop_reassociate(int[] a, int[] r) {
        return VectorAlgorithmsImpl.scanAddI_loop_reassociate(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.REARRANGE_VI,     "> 0",
                  IRNode.AND_VI,           "> 0",
                  IRNode.ADD_VI,           "> 0",
                  IRNode.STORE_VECTOR,     "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"MaxVectorSize", ">=64"})
    public Object scanAddI_VectorAPI_permute_add(int[] a, int[] r) {
        return VectorAlgorithmsImpl.scanAddI_VectorAPI_permute_add(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0"})
    // Currently does not vectorize, but might in the future.
    public int findMinIndexI_loop(int[] a) {
        return VectorAlgorithmsImpl.findMinIndexI_loop(a);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.VECTOR_MASK_CMP, "> 0",
                  IRNode.VECTOR_BLEND_I,  "> 0",
                  IRNode.MIN_REDUCTION_V, "> 0",
                  IRNode.ADD_VI,          "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public int findMinIndexI_VectorAPI(int[] a) {
        return VectorAlgorithmsImpl.findMinIndexI_VectorAPI(a);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0"})
    // Currently does not vectorize, but might in the future.
    public int findI_loop(int[] a, int e) {
        return VectorAlgorithmsImpl.findI_loop(a, e);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   "> 0",
                  IRNode.VECTOR_MASK_CMP, "> 0",
                  IRNode.VECTOR_TEST,     "> 0"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public int findI_VectorAPI(int[] a, int e) {
        return VectorAlgorithmsImpl.findI_VectorAPI(a, e);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.STORE_VECTOR,  "= 0"})
    // Currently does not vectorize, but might in the future.
    public Object reverseI_loop(int[] a, int[] r) {
        return VectorAlgorithmsImpl.reverseI_loop(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,    "> 0",
                  IRNode.REARRANGE_VI,     "> 0",
                  IRNode.AND_VI,           "> 0",
                  IRNode.STORE_VECTOR,     "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public Object reverseI_VectorAPI(int[] a, int[] r) {
        return VectorAlgorithmsImpl.reverseI_VectorAPI(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.STORE_VECTOR,  "= 0"})
    public Object filterI_loop(int[] a, int[] r, int threshold) {
        return VectorAlgorithmsImpl.filterI_loop(a, r, threshold);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,       "> 0",
                  IRNode.VECTOR_MASK_CMP,     "> 0",
                  IRNode.VECTOR_TEST,         "> 0",
                  IRNode.COMPRESS_VI,         "> 0",
                  IRNode.STORE_VECTOR_MASKED, "> 0"},
        applyIfCPUFeature = {"avx2", "true"})
    public Object filterI_VectorAPI(int[] a, int[] r, int threshold) {
        return VectorAlgorithmsImpl.filterI_VectorAPI(a, r, threshold);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0"})
    // Currently does not vectorize, but might in the future.
    public int reduceAddIFieldsX4_loop(int[] oops, int[] mem) {
        return VectorAlgorithmsImpl.reduceAddIFieldsX4_loop(oops, mem);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,             "> 0",
                  IRNode.VECTOR_MASK_CMP,           "> 0",
                  IRNode.VECTOR_TEST,               "> 0",
                  IRNode.LOAD_VECTOR_GATHER_MASKED, "> 0",
                  IRNode.OR_V_MASK,                 "> 0",
                  IRNode.ADD_VI,                    "> 0",
                  IRNode.ADD_REDUCTION_VI,          "> 0"},
        applyIfCPUFeatureOr = {"avx512", "true", "sve", "true"})
    public int reduceAddIFieldsX4_VectorAPI(int[] oops, int[] mem) {
        return VectorAlgorithmsImpl.reduceAddIFieldsX4_VectorAPI(oops, mem);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0"})
    // Currently does not vectorize, but might in the future.
    public Object lowerCaseB_loop(byte[] a, byte[] r) {
        return VectorAlgorithmsImpl.lowerCaseB_loop(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public Object lowerCaseB_VectorAPI_v1(byte[] a, byte[] r) {
        return VectorAlgorithmsImpl.lowerCaseB_VectorAPI_v1(a, r);
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public Object lowerCaseB_VectorAPI_v2(byte[] a, byte[] r) {
        return VectorAlgorithmsImpl.lowerCaseB_VectorAPI_v2(a, r);
    }
}
