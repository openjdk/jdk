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

import java.util.Arrays;
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

    static final long[] ONES_L = new long[64];
    static { Arrays.fill(ONES_L, 1); }

    static final boolean[] TRUES = new boolean[64];
    static { Arrays.fill(TRUES, true); }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    // -------------------------------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "> 0",
                  IRNode.VECTOR_LOAD_MASK,                      "> 0", // Not yet optimized away
                  IRNode.VECTOR_STORE_MASK,                     "> 0", // Not yet optimized away
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Not yet optimized away
                  IRNode.VECTOR_BLEND_I,  IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "> 0",
                  IRNode.VECTOR_LOAD_MASK,                      "= 0", // Optimized away
                  IRNode.VECTOR_STORE_MASK,                     "= 0", // Optimized away
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Cast I->J
                  IRNode.VECTOR_BLEND_I,                        "= 0", // Not needed
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeature = {"avx512", "true"})
    // This is the original reproducer for JDK-8378968, which failed on AVX2 with a wrong result.
    public static Object test1() {
        // There was a bug here with AVX2:
        var ones = LongVector.broadcast(LongVector.SPECIES_256, 1);
        var trues_L256 = ones.compare(VectorOperators.NE, 0);
        // VectorMaskCmp #vectory<J,4>   -> (L-mask=0x0..0/0xF..F)

        var trues_I128 = VectorMask.fromLong(IntVector.SPECIES_128, trues_L256.toLong());
        // VectorStoreMask(L-mask to 0/1)
        // VectorMaskToLong
        // AndL(truncate)
        // VectorLongToMask -> 0/1
        //
        // VectorLongToMaskNode::Ideal transforms this into:
        // VectorMaskCmp #vectory<J,4>   -> (L-mask=0x0..0/0xF..F)
        // VectorMaskCastNode -> (L-mask=0x0..0/0xF..F to 0/1)
        //   But VectorMaskCastNode is not made for such mask conversion to boolean mask,
        //   and so it wrongly produces a 0x00/0xFF byte mask, instead of bytes 0x00/01.
        //   See: vector_mask_cast
        //
        // The correct transformation would have been to:
        // VectorMaskCmp #vectory<J,4>   -> (L-mask=0x0..0/0xF..F)
        // VectorStoreMask(L-mask to 0/1, i.e. 0x00/0x01 bytes)

        var zeros = IntVector.zero(IntVector.SPECIES_128);
        var m1s = zeros.lanewise(VectorOperators.NOT, trues_I128);
        // The rest of the code is:
        // VectorLoadMask (0/1 to I-mask=0x0..0/0xF..F)
        //   It expects x=0x00/0x01 bytes, and does a subtraction 0-x to get values 0x00/0xFF
        //   that are then widened to int-length.
        //   But if it instead gets 0x00/0xFF, the subtraction produces 0x00/0x01 values, which
        //   are widened to int 0x0..0/0..01 values.
        //   See: load_vector_mask
        // Blend, which expects I-mask (0x0..0/0xF..F)
        //   It looks at the 7th (uppermost) bit of every byte to determine which byte is taken.
        //   If it instead gets the 0x0..0/0x0..01 mask, it interprets both as "false".

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
    @Test
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_L,   IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "> 0",
                  IRNode.VECTOR_LOAD_MASK,                      "> 0", // Not yet optimized away
                  IRNode.VECTOR_STORE_MASK,                     "> 0", // Not yet optimized away
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Not yet optimized away: Cast Z->Z
                  IRNode.VECTOR_BLEND_I,  IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_L,   IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "> 0",
                  IRNode.VECTOR_LOAD_MASK,                      "= 0", // Optimized away
                  IRNode.VECTOR_STORE_MASK,                     "= 0", // Optimized away
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0", // Cast I->J
                  IRNode.VECTOR_BLEND_I,                        "= 0", // Not needed
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeature = {"avx512", "true"})
    // The original reproducer test1 could eventually constant-fold the comparison
    // with zero and trues. So let's make sure we load the data from a mutable array.
    public static Object test1b() {
        // load instead of broadcast:
        var ones = LongVector.fromArray(LongVector.SPECIES_256, ONES_L, 0);
        var trues_L256 = ones.compare(VectorOperators.NE, 0);

        var trues_I128 = VectorMask.fromLong(IntVector.SPECIES_128, trues_L256.toLong());

        var zeros = IntVector.zero(IntVector.SPECIES_128);
        var m1s = zeros.lanewise(VectorOperators.NOT, trues_I128);

        int[] out = new int[64];
        m1s.intoArray(out, 0);
        return out;
    }

    static final Object GOLD_TEST1B = test1b();

    @Check(test = "test1b")
    public static void check_test1b(Object out) {
        Verify.checkEQ(GOLD_TEST1B, out);
    }
    // -------------------------------------------------------------------------------------
    @Test
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "= 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_Z,   IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "= 0",
                  IRNode.VECTOR_LOAD_MASK,                      "> 0",
                  IRNode.VECTOR_STORE_MASK,                     "= 0",
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0",
                  IRNode.VECTOR_BLEND_I,  IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(counts = {IRNode.REPLICATE_L,     IRNode.VECTOR_SIZE_4, "= 0",
                  IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.LOAD_VECTOR_Z,   IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.VECTOR_MASK_CMP,                       "= 0",
                  IRNode.VECTOR_LOAD_MASK,                      "> 0",
                  IRNode.VECTOR_STORE_MASK,                     "= 0",
                  IRNode.VECTOR_LONG_TO_MASK,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_TO_LONG,                   "= 0", // Optimized away
                  IRNode.VECTOR_MASK_CAST,                      "> 0",
                  IRNode.VECTOR_BLEND_I,                        "= 0", // Not needed
                  IRNode.XOR_VI,          IRNode.VECTOR_SIZE_4, "> 0",
                  IRNode.STORE_VECTOR,                          "> 0"},
                  applyIfCPUFeature = {"avx512", "true"})
    // And now let's try a case where we load the mask from boolean array, so we don't
    // have the VectorStoreMask before the VectorMaskToLong.
    public static Object test1c() {
        // Load true mask from array directly.
        var trues_L256 = VectorMask.fromArray(LongVector.SPECIES_256, TRUES, 0);

        var trues_I128 = VectorMask.fromLong(IntVector.SPECIES_128, trues_L256.toLong());

        var zeros = IntVector.zero(IntVector.SPECIES_128);
        var m1s = zeros.lanewise(VectorOperators.NOT, trues_I128);

        int[] out = new int[64];
        m1s.intoArray(out, 0);
        return out;
    }

    static final Object GOLD_TEST1C = test1c();

    @Check(test = "test1c")
    public static void check_test1c(Object out) {
        Verify.checkEQ(GOLD_TEST1C, out);
    }
    // -------------------------------------------------------------------------------------
}
