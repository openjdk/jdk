/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug JDK-8310130
 * @summary Special test cases for PhaseIdealLoop::move_unordered_reduction_out_of_loop
 *          Here a case with partial vectorization of the reduction.
 * @requires vm.bits == "64"
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestUnorderedReductionPartialVectorization
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

public class TestUnorderedReductionPartialVectorization {
    static final int RANGE = 1024;
    static final int ITER  = 10;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1"})
    @Warmup(0)
    public void runTests() throws Exception {
        int[] data = new int[RANGE];

        init(data);
        for (int i = 0; i < ITER; i++) {
            long r1 = test1(data, i);
            long r2 = ref1(data, i);
            if (r1 != r2) {
                throw new RuntimeException("Wrong result test1: " + r1 + " != " + r2);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.VECTOR_CAST_I2L, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.OR_REDUCTION_V,                                                 "> 0",},
        applyIfCPUFeatureOr = {"avx2", "true"})
    static long test1(int[] data, long sum) {
        for (int i = 0; i < data.length; i+=2) {
            // Mixing int and long ops means we only end up allowing half of the int
            // loads in one pack, and we have two int packs. The first pack has one
            // of the pairs missing because of the store, which creates a dependency.
            // The first pack is rejected and left as scalar, the second pack succeeds
            // with vectorization. That means we have a mixed scalar/vector reduction
            // chain. This way it is possible that a vector-reduction has a scalar
            // reduction as input, which is neigher a phi nor a vector reduction.
            // In such a case, we must bail out of the optimization in
            // PhaseIdealLoop::move_unordered_reduction_out_of_loop
            int v = data[i]; // int read
            data[0] = 0;     // ruin the first pack
            sum |= v;        // long reduction (and implicit cast from int to long)

            // This example used to rely on that reductions were ignored in SuperWord::unrolling_analysis,
            // and hence the largest data type in the loop was the ints. This would then unroll the doubles
            // for twice the vector length, and this resulted in us having twice as many packs. Because of
            // the store "data[0] = 0", the first packs were destroyed, since they do not have power of 2
            // size.
            // Now, we no longer ignore reductions, and now we unroll half as much before SuperWord. This
            // means we would only get one pack per operation, and that one would get ruined, and we have
            // no vectorization. We now ensure there are again 2 packs per operation with a 2x hand unroll.
            int v2 = data[i + 1];
            sum |= v2;
        }
        return sum;
    }

    static long ref1(int[] data, long sum) {
        for (int i = 0; i < data.length; i++) {
            int v = data[i];
            data[0] = 0;
            sum |= v;
        }
        return sum;
    }

    static void init(int[] data) {
        for (int i = 0; i < RANGE; i++) {
            data[i] = i + 1;
        }
    }
}
