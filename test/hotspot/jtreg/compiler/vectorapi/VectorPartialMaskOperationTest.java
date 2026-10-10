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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;

/*
 * @test
 * @bug 8386154
 * @summary C2 VectorAPI: wrong result with short-to-int cast
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class}
 */
public class VectorPartialMaskOperationTest {

    private static short[] POS_S   = { 1, 2, 3, 4 };
    private static short[] MIXED_S = { 1, 0, 2, 0 };
    private static short[] ZERO_S  = { 0, 0, 0, 0 };
    private static byte[]  ZERO_B  = { 0, 0, 0, 0, 0, 0, 0, 0 };

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags("--add-modules=jdk.incubator.vector");
        framework.setDefaultWarmup(10000);
        framework.start();
    }

    // ==== short64 -> int128, empty mask via compare(EQ, 0) on POS_S ====

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static int shortEmptyLastTrue(ShortVector v) {
        return v.compare(VectorOperators.EQ, (short) 0).cast(IntVector.SPECIES_128).lastTrue();
    }

    @Run(test = "shortEmptyLastTrue")
    static void runShortEmptyLastTrue() {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_64, POS_S, 0);
        Verify.checkEQ(shortEmptyLastTrue(v), -1);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static int shortEmptyTrueCount(ShortVector v) {
        return v.compare(VectorOperators.EQ, (short) 0).cast(IntVector.SPECIES_128).trueCount();
    }

    @Run(test = "shortEmptyTrueCount")
    static void runShortEmptyTrueCount() {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_64, POS_S, 0);
        Verify.checkEQ(shortEmptyTrueCount(v), 0);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static long shortEmptyToLong(ShortVector v) {
        return v.compare(VectorOperators.EQ, (short) 0).cast(IntVector.SPECIES_128).toLong();
    }

    @Run(test = "shortEmptyToLong")
    static void runShortEmptyToLong() {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_64, POS_S, 0);
        Verify.checkEQ(shortEmptyToLong(v), 0L);
    }

    // ==== short64 -> int128, mixed mask via test(IS_DEFAULT) on MIXED_S ====

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static int shortMixedLastTrue(ShortVector v) {
        return v.test(VectorOperators.IS_DEFAULT).cast(IntVector.SPECIES_128).lastTrue();
    }

    @Run(test = "shortMixedLastTrue")
    static void runShortMixedLastTrue() {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_64, MIXED_S, 0);
        Verify.checkEQ(shortMixedLastTrue(v), 3);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static int shortMixedTrueCount(ShortVector v) {
        return v.test(VectorOperators.IS_DEFAULT).cast(IntVector.SPECIES_128).trueCount();
    }

    @Run(test = "shortMixedTrueCount")
    static void runShortMixedTrueCount() {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_64, MIXED_S, 0);
        Verify.checkEQ(shortMixedTrueCount(v), 2);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static long shortMixedToLong(ShortVector v) {
        return v.test(VectorOperators.IS_DEFAULT).cast(IntVector.SPECIES_128).toLong();
    }

    @Run(test = "shortMixedToLong")
    static void runShortMixedToLong() {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_64, MIXED_S, 0);
        Verify.checkEQ(shortMixedToLong(v), 0b1010L);
    }

    // ==== short64 -> int128, all-true mask via compare(EQ, 0) on ZERO_S ====

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static int shortAllTrueLastTrue(ShortVector v) {
        return v.compare(VectorOperators.EQ, (short) 0).cast(IntVector.SPECIES_128).lastTrue();
    }

    @Run(test = "shortAllTrueLastTrue")
    static void runShortAllTrueLastTrue() {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_64, ZERO_S, 0);
        Verify.checkEQ(shortAllTrueLastTrue(v), 3);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static int shortAllTrueTrueCount(ShortVector v) {
        return v.compare(VectorOperators.EQ, (short) 0).cast(IntVector.SPECIES_128).trueCount();
    }

    @Run(test = "shortAllTrueTrueCount")
    static void runShortAllTrueTrueCount() {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_64, ZERO_S, 0);
        Verify.checkEQ(shortAllTrueTrueCount(v), 4);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static long shortAllTrueToLong(ShortVector v) {
        return v.compare(VectorOperators.EQ, (short) 0).cast(IntVector.SPECIES_128).toLong();
    }

    @Run(test = "shortAllTrueToLong")
    static void runShortAllTrueToLong() {
        ShortVector v = ShortVector.fromArray(ShortVector.SPECIES_64, ZERO_S, 0);
        Verify.checkEQ(shortAllTrueToLong(v), 0b1111L);
    }

    // ==== byte64 -> short128, all-true mask via compare(EQ, 0) on ZERO_B ====

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static int byteAllTrueLastTrue(ByteVector v) {
        return v.compare(VectorOperators.EQ, (byte) 0).cast(ShortVector.SPECIES_128).lastTrue();
    }

    @Run(test = "byteAllTrueLastTrue")
    static void runByteAllTrueLastTrue() {
        ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_64, ZERO_B, 0);
        Verify.checkEQ(byteAllTrueLastTrue(v), 7);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static int byteAllTrueTrueCount(ByteVector v) {
        return v.compare(VectorOperators.EQ, (byte) 0).cast(ShortVector.SPECIES_128).trueCount();
    }

    @Run(test = "byteAllTrueTrueCount")
    static void runByteAllTrueTrueCount() {
        ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_64, ZERO_B, 0);
        Verify.checkEQ(byteAllTrueTrueCount(v), 8);
    }

    @Test
    @IR(counts = { IRNode.VECTOR_MASK_CAST, "> 0" }, applyIfCPUFeatureOr = { "avx2", "true", "avx512", "true" })
    static long byteAllTrueToLong(ByteVector v) {
        return v.compare(VectorOperators.EQ, (byte) 0).cast(ShortVector.SPECIES_128).toLong();
    }

    @Run(test = "byteAllTrueToLong")
    static void runByteAllTrueToLong() {
        ByteVector v = ByteVector.fromArray(ByteVector.SPECIES_64, ZERO_B, 0);
        Verify.checkEQ(byteAllTrueToLong(v), 0xFFL);
    }
}
