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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.*;

/*
 * @test
 * @bug 8277997 8378968
 * @summary Testing some optimizations in VectorLongToMaskNode::Ideal
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestVectorLongToMaskNodeIdealization {
    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    // -------------------------------------------------------------------------------------
    @Test
    //@IR(counts = {IRNode.URSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
    //              IRNode.LSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
    //              IRNode.OR_VL, IRNode.VECTOR_SIZE_2, "1"},
    //              applyIfCPUFeatureAnd = {"avx2", "true", "avx512f", "false", "avx512vl", "false"})
    // TODO: IR rule
    public static Object test1() {
        // There was a bug here with AVX2:
        var ones = LongVector.broadcast(LongVector.SPECIES_256, 1);
        var trues_L256 = ones.compare(VectorOperators.NE, 0);
        // VectorMaskCmp #vectory<J,4>   -> (L-mask=0xF..F/0x0..0)

        var trues_I128 = VectorMask.fromLong(IntVector.SPECIES_128, trues_L256.toLong());
        // VectorStoreMask(L-mask to 0/1)
        // VectorMaskToLong
        // AndL(truncate)
        // VectorLongToMask -> 0/1
        // VectorLoadMask (0/1 to I-mask)
        //
        // TODO: transform

        var zeros = IntVector.zero(IntVector.SPECIES_128);
        var m1s = zeros.lanewise(VectorOperators.NOT, trues_I128);
        // Blend, expects I-mask (0xF..F*0x0..0)
        // TODO: how we got wrong result here

        int[] out = new int[64];
        m1s.intoArray(out, 0);
        return out;
    }

    static final Object GOLD_TEST1 = test1();

    @Check(test = "test1")
    public static void check_test1(Object out) {
        Verify.checkEQ(GOLD_TEST1, out);
    }
    // -------------------------------------------------------------------------------------
}
