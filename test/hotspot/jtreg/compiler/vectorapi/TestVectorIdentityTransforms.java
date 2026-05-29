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

/**
 * @test
 * @bug 8361403
 * @summary Verify Vector API identity/ideal transforms for AddV, SubV, MulV
 * @key randomness
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestVectorIdentityTransforms
 */

package compiler.vectorapi;

import jdk.incubator.vector.*;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import compiler.lib.generators.*;

public class TestVectorIdentityTransforms {
    static final VectorSpecies<Byte>    B_SPECIES = ByteVector.SPECIES_PREFERRED;
    static final VectorSpecies<Short>   S_SPECIES = ShortVector.SPECIES_PREFERRED;
    static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    static final VectorSpecies<Long>    L_SPECIES = LongVector.SPECIES_PREFERRED;
    static final VectorSpecies<Float>   F_SPECIES = FloatVector.SPECIES_PREFERRED;
    static final VectorSpecies<Double>  D_SPECIES = DoubleVector.SPECIES_PREFERRED;
    static final int LENGTH = 1024;

    private byte[]    byteInput,   byteOutput;
    private short[]   shortInput,  shortOutput;
    private int[]     intInput,    intOutput;
    private long[]    longInput,   longOutput;
    private float[]   floatInput,  floatOutput;
    private double[]  doubleInput, doubleOutput;
    private boolean[] maskArr;

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    public TestVectorIdentityTransforms() {
        Generators g = Generators.G;
        Generator<Integer> iGen = g.ints();
        Generator<Long>    lGen = g.longs();
        Generator<Float>   fGen = g.floats();
        Generator<Double>  dGen = g.doubles();

        byteInput   = new byte[LENGTH];
        byteOutput  = new byte[LENGTH];
        shortInput  = new short[LENGTH];
        shortOutput = new short[LENGTH];
        intInput    = new int[LENGTH];
        intOutput   = new int[LENGTH];
        longInput   = new long[LENGTH];
        longOutput  = new long[LENGTH];
        floatInput  = new float[LENGTH];
        floatOutput = new float[LENGTH];
        doubleInput  = new double[LENGTH];
        doubleOutput = new double[LENGTH];
        maskArr = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            byteInput[i]  = iGen.next().byteValue();
            shortInput[i] = iGen.next().shortValue();
            maskArr[i] = (i % 3) != 0;
        }
        g.fill(iGen, intInput);
        g.fill(lGen, longInput);
        g.fill(fGen, floatInput);
        g.fill(dGen, doubleInput);
    }
    // ========================= Byte Tests =========================

    // AddV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.ADD_VB})
    public void testByteAddZero(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        v.add(ByteVector.broadcast(B_SPECIES, (byte) 0))
         .intoArray(byteOutput, index);
    }

    @Run(test = "testByteAddZero")
    public void runByteAddZero() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteAddZero(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], byteInput[i]);
        }
    }

    // AddV(Replicate(0), X) => X
    @Test
    @IR(failOn = {IRNode.ADD_VB})
    public void testByteZeroAddX(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        ByteVector.broadcast(B_SPECIES, (byte) 0).add(v)
                 .intoArray(byteOutput, index);
    }

    @Run(test = "testByteZeroAddX")
    public void runByteZeroAddX() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteZeroAddX(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], byteInput[i]);
        }
    }

    // SubV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.SUB_VB})
    public void testByteSubZero(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        v.sub(ByteVector.broadcast(B_SPECIES, (byte) 0))
         .intoArray(byteOutput, index);
    }

    @Run(test = "testByteSubZero")
    public void runByteSubZero() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteSubZero(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], byteInput[i]);
        }
    }

    // SubV(X, X) => Replicate(0) [integral only]
    @Test
    @IR(failOn = {IRNode.SUB_VB})
    public void testByteSubSelf(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        v.sub(v).intoArray(byteOutput, index);
    }

    @Run(test = "testByteSubSelf")
    public void runByteSubSelf() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteSubSelf(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], (byte) 0);
        }
    }

    // MulV(X, Replicate(1)) => X
    @Test
    @IR(failOn = {IRNode.MUL_VB})
    public void testByteMulOne(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        v.mul(ByteVector.broadcast(B_SPECIES, (byte) 1))
         .intoArray(byteOutput, index);
    }

    @Run(test = "testByteMulOne")
    public void runByteMulOne() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMulOne(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], byteInput[i]);
        }
    }

    // MulV(Replicate(1), X) => X
    @Test
    @IR(failOn = {IRNode.MUL_VB})
    public void testByteOneMulX(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        ByteVector.broadcast(B_SPECIES, (byte) 1).mul(v)
                 .intoArray(byteOutput, index);
    }

    @Run(test = "testByteOneMulX")
    public void runByteOneMulX() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteOneMulX(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], byteInput[i]);
        }
    }

    // MulV(X, Replicate(0)) => Replicate(0) [integral only]
    @Test
    @IR(failOn = {IRNode.MUL_VB})
    public void testByteMulZero(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        v.mul(ByteVector.broadcast(B_SPECIES, (byte) 0))
         .intoArray(byteOutput, index);
    }

    @Run(test = "testByteMulZero")
    public void runByteMulZero() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMulZero(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], (byte) 0);
        }
    }

    // Predicated: AddV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.ADD_VB},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testByteMaskedAddZero(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.ADD, ByteVector.broadcast(B_SPECIES, (byte) 0), mask)
         .intoArray(byteOutput, index);
    }

    @Run(test = "testByteMaskedAddZero")
    public void runByteMaskedAddZero() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedAddZero(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], byteInput[i]);
        }
    }

    // Predicated: SubV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.SUB_VB},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testByteMaskedSubZero(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, ByteVector.broadcast(B_SPECIES, (byte) 0), mask)
         .intoArray(byteOutput, index);
    }

    @Run(test = "testByteMaskedSubZero")
    public void runByteMaskedSubZero() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedSubZero(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], byteInput[i]);
        }
    }

    // Predicated: MulV(X, Replicate(1), mask) => X
    @Test
    @IR(failOn = {IRNode.MUL_VB},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testByteMaskedMulOne(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, ByteVector.broadcast(B_SPECIES, (byte) 1), mask)
         .intoArray(byteOutput, index);
    }

    @Run(test = "testByteMaskedMulOne")
    public void runByteMaskedMulOne() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMulOne(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], byteInput[i]);
        }
    }

    // Negative: predicated AddV(Replicate(0), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.ADD_VB, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testByteMaskedZeroAddX(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, maskArr, index);
        ByteVector.broadcast(B_SPECIES, (byte) 0)
                 .lanewise(VectorOperators.ADD, v, mask)
                 .intoArray(byteOutput, index);
    }

    @Run(test = "testByteMaskedZeroAddX")
    public void runByteMaskedZeroAddX() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedZeroAddX(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], maskArr[i] ? byteInput[i] : (byte) 0);
        }
    }

    // Negative: predicated SubV(X, X, mask) must NOT be folded [integral only]
    @Test
    @IR(counts = {IRNode.SUB_VB, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testByteMaskedSubSelf(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, v, mask)
         .intoArray(byteOutput, index);
    }

    @Run(test = "testByteMaskedSubSelf")
    public void runByteMaskedSubSelf() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedSubSelf(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], maskArr[i] ? (byte) 0 : byteInput[i]);
        }
    }

    // Negative: predicated MulV(Replicate(1), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.MUL_VB, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testByteMaskedOneMulX(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, maskArr, index);
        ByteVector.broadcast(B_SPECIES, (byte) 1)
                 .lanewise(VectorOperators.MUL, v, mask)
                 .intoArray(byteOutput, index);
    }

    @Run(test = "testByteMaskedOneMulX")
    public void runByteMaskedOneMulX() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedOneMulX(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], maskArr[i] ? byteInput[i] : (byte) 1);
        }
    }

    // Negative: predicated MulV(X, Replicate(0), mask) must NOT be folded [integral only]
    @Test
    @IR(counts = {IRNode.MUL_VB, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testByteMaskedMulZero(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, byteInput, index);
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, ByteVector.broadcast(B_SPECIES, (byte) 0), mask)
         .intoArray(byteOutput, index);
    }

    @Run(test = "testByteMaskedMulZero")
    public void runByteMaskedMulZero() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMulZero(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(byteOutput[i], maskArr[i] ? (byte) 0 : byteInput[i]);
        }
    }

    // ========================= Short Tests =========================

    // AddV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.ADD_VS})
    public void testShortAddZero(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        v.add(ShortVector.broadcast(S_SPECIES, (short) 0))
         .intoArray(shortOutput, index);
    }

    @Run(test = "testShortAddZero")
    public void runShortAddZero() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortAddZero(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], shortInput[i]);
        }
    }

    // AddV(Replicate(0), X) => X
    @Test
    @IR(failOn = {IRNode.ADD_VS})
    public void testShortZeroAddX(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        ShortVector.broadcast(S_SPECIES, (short) 0).add(v)
                 .intoArray(shortOutput, index);
    }

    @Run(test = "testShortZeroAddX")
    public void runShortZeroAddX() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortZeroAddX(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], shortInput[i]);
        }
    }

    // MulV(X, Replicate(1)) => X
    @Test
    @IR(failOn = {IRNode.MUL_VS})
    public void testShortMulOne(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        v.mul(ShortVector.broadcast(S_SPECIES, (short) 1))
         .intoArray(shortOutput, index);
    }

    @Run(test = "testShortMulOne")
    public void runShortMulOne() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMulOne(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], shortInput[i]);
        }
    }

    // MulV(Replicate(1), X) => X
    @Test
    @IR(failOn = {IRNode.MUL_VS})
    public void testShortOneMulX(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        ShortVector.broadcast(S_SPECIES, (short) 1).mul(v)
                 .intoArray(shortOutput, index);
    }

    @Run(test = "testShortOneMulX")
    public void runShortOneMulX() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortOneMulX(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], shortInput[i]);
        }
    }

    // SubV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.SUB_VS})
    public void testShortSubZero(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        v.sub(ShortVector.broadcast(S_SPECIES, (short) 0))
         .intoArray(shortOutput, index);
    }

    @Run(test = "testShortSubZero")
    public void runShortSubZero() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortSubZero(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], shortInput[i]);
        }
    }

    // SubV(X, X) => Replicate(0) [integral only]
    @Test
    @IR(failOn = {IRNode.SUB_VS})
    public void testShortSubSelf(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        v.sub(v).intoArray(shortOutput, index);
    }

    @Run(test = "testShortSubSelf")
    public void runShortSubSelf() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortSubSelf(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], (short) 0);
        }
    }

    // MulV(X, Replicate(0)) => Replicate(0) [integral only]
    @Test
    @IR(failOn = {IRNode.MUL_VS})
    public void testShortMulZero(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        v.mul(ShortVector.broadcast(S_SPECIES, (short) 0))
         .intoArray(shortOutput, index);
    }

    @Run(test = "testShortMulZero")
    public void runShortMulZero() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMulZero(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], (short) 0);
        }
    }

    // Predicated: AddV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.ADD_VS},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testShortMaskedAddZero(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.ADD, ShortVector.broadcast(S_SPECIES, (short) 0), mask)
         .intoArray(shortOutput, index);
    }

    @Run(test = "testShortMaskedAddZero")
    public void runShortMaskedAddZero() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedAddZero(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], shortInput[i]);
        }
    }

    // Predicated: SubV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.SUB_VS},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testShortMaskedSubZero(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, ShortVector.broadcast(S_SPECIES, (short) 0), mask)
         .intoArray(shortOutput, index);
    }

    @Run(test = "testShortMaskedSubZero")
    public void runShortMaskedSubZero() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedSubZero(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], shortInput[i]);
        }
    }

    // Predicated: MulV(X, Replicate(1), mask) => X
    @Test
    @IR(failOn = {IRNode.MUL_VS},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testShortMaskedMulOne(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, ShortVector.broadcast(S_SPECIES, (short) 1), mask)
         .intoArray(shortOutput, index);
    }

    @Run(test = "testShortMaskedMulOne")
    public void runShortMaskedMulOne() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMulOne(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], shortInput[i]);
        }
    }

    // Negative: predicated AddV(Replicate(0), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.ADD_VS, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testShortMaskedZeroAddX(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, maskArr, index);
        ShortVector.broadcast(S_SPECIES, (short) 0)
                 .lanewise(VectorOperators.ADD, v, mask)
                 .intoArray(shortOutput, index);
    }

    @Run(test = "testShortMaskedZeroAddX")
    public void runShortMaskedZeroAddX() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedZeroAddX(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], maskArr[i] ? shortInput[i] : (short) 0);
        }
    }

    // Negative: predicated MulV(Replicate(1), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.MUL_VS, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testShortMaskedOneMulX(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, maskArr, index);
        ShortVector.broadcast(S_SPECIES, (short) 1)
                 .lanewise(VectorOperators.MUL, v, mask)
                 .intoArray(shortOutput, index);
    }

    @Run(test = "testShortMaskedOneMulX")
    public void runShortMaskedOneMulX() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedOneMulX(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], maskArr[i] ? shortInput[i] : (short) 1);
        }
    }

    // Negative: predicated SubV(X, X, mask) must NOT be folded [integral only]
    @Test
    @IR(counts = {IRNode.SUB_VS, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testShortMaskedSubSelf(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, v, mask)
         .intoArray(shortOutput, index);
    }

    @Run(test = "testShortMaskedSubSelf")
    public void runShortMaskedSubSelf() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedSubSelf(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], maskArr[i] ? (short) 0 : shortInput[i]);
        }
    }

    // Negative: predicated MulV(X, Replicate(0), mask) must NOT be folded [integral only]
    @Test
    @IR(counts = {IRNode.MUL_VS, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testShortMaskedMulZero(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, shortInput, index);
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, ShortVector.broadcast(S_SPECIES, (short) 0), mask)
         .intoArray(shortOutput, index);
    }

    @Run(test = "testShortMaskedMulZero")
    public void runShortMaskedMulZero() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMulZero(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(shortOutput[i], maskArr[i] ? (short) 0 : shortInput[i]);
        }
    }

    // ========================= Int Tests =========================

    // AddV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.ADD_VI})
    public void testIntAddZero(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        v.add(IntVector.broadcast(I_SPECIES, 0))
         .intoArray(intOutput, index);
    }

    @Run(test = "testIntAddZero")
    public void runIntAddZero() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntAddZero(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], intInput[i]);
        }
    }

    // AddV(Replicate(0), X) => X
    @Test
    @IR(failOn = {IRNode.ADD_VI})
    public void testIntZeroAddX(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        IntVector.broadcast(I_SPECIES, 0).add(v)
                 .intoArray(intOutput, index);
    }

    @Run(test = "testIntZeroAddX")
    public void runIntZeroAddX() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntZeroAddX(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], intInput[i]);
        }
    }

    // MulV(X, Replicate(1)) => X
    @Test
    @IR(failOn = {IRNode.MUL_VI})
    public void testIntMulOne(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        v.mul(IntVector.broadcast(I_SPECIES, 1))
         .intoArray(intOutput, index);
    }

    @Run(test = "testIntMulOne")
    public void runIntMulOne() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMulOne(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], intInput[i]);
        }
    }

    // MulV(Replicate(1), X) => X
    @Test
    @IR(failOn = {IRNode.MUL_VI})
    public void testIntOneMulX(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        IntVector.broadcast(I_SPECIES, 1).mul(v)
                 .intoArray(intOutput, index);
    }

    @Run(test = "testIntOneMulX")
    public void runIntOneMulX() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntOneMulX(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], intInput[i]);
        }
    }

    // SubV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.SUB_VI})
    public void testIntSubZero(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        v.sub(IntVector.broadcast(I_SPECIES, 0))
         .intoArray(intOutput, index);
    }

    @Run(test = "testIntSubZero")
    public void runIntSubZero() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntSubZero(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], intInput[i]);
        }
    }

    // SubV(X, X) => Replicate(0) [integral only]
    @Test
    @IR(failOn = {IRNode.SUB_VI})
    public void testIntSubSelf(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        v.sub(v).intoArray(intOutput, index);
    }

    @Run(test = "testIntSubSelf")
    public void runIntSubSelf() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntSubSelf(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], (int) 0);
        }
    }

    // MulV(X, Replicate(0)) => Replicate(0) [integral only]
    @Test
    @IR(failOn = {IRNode.MUL_VI})
    public void testIntMulZero(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        v.mul(IntVector.broadcast(I_SPECIES, 0))
         .intoArray(intOutput, index);
    }

    @Run(test = "testIntMulZero")
    public void runIntMulZero() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMulZero(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], (int) 0);
        }
    }

    // Predicated: AddV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.ADD_VI},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testIntMaskedAddZero(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.ADD, IntVector.broadcast(I_SPECIES, 0), mask)
         .intoArray(intOutput, index);
    }

    @Run(test = "testIntMaskedAddZero")
    public void runIntMaskedAddZero() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedAddZero(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], intInput[i]);
        }
    }

    // Predicated: SubV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.SUB_VI},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testIntMaskedSubZero(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, IntVector.broadcast(I_SPECIES, 0), mask)
         .intoArray(intOutput, index);
    }

    @Run(test = "testIntMaskedSubZero")
    public void runIntMaskedSubZero() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedSubZero(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], intInput[i]);
        }
    }

    // Predicated: MulV(X, Replicate(1), mask) => X
    @Test
    @IR(failOn = {IRNode.MUL_VI},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testIntMaskedMulOne(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, IntVector.broadcast(I_SPECIES, 1), mask)
         .intoArray(intOutput, index);
    }

    @Run(test = "testIntMaskedMulOne")
    public void runIntMaskedMulOne() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMulOne(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], intInput[i]);
        }
    }

    // Negative: predicated AddV(Replicate(0), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.ADD_VI, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testIntMaskedZeroAddX(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, maskArr, index);
        IntVector.broadcast(I_SPECIES, 0)
                 .lanewise(VectorOperators.ADD, v, mask)
                 .intoArray(intOutput, index);
    }

    @Run(test = "testIntMaskedZeroAddX")
    public void runIntMaskedZeroAddX() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedZeroAddX(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], maskArr[i] ? intInput[i] : 0);
        }
    }

    // Negative: predicated MulV(Replicate(1), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.MUL_VI, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testIntMaskedOneMulX(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, maskArr, index);
        IntVector.broadcast(I_SPECIES, 1)
                 .lanewise(VectorOperators.MUL, v, mask)
                 .intoArray(intOutput, index);
    }

    @Run(test = "testIntMaskedOneMulX")
    public void runIntMaskedOneMulX() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedOneMulX(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], maskArr[i] ? intInput[i] : 1);
        }
    }

    // Negative: predicated SubV(X, X, mask) must NOT be folded [integral only]
    @Test
    @IR(counts = {IRNode.SUB_VI, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testIntMaskedSubSelf(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, v, mask)
         .intoArray(intOutput, index);
    }

    @Run(test = "testIntMaskedSubSelf")
    public void runIntMaskedSubSelf() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedSubSelf(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], maskArr[i] ? (int) 0 : intInput[i]);
        }
    }

    // Negative: predicated MulV(X, Replicate(0), mask) must NOT be folded [integral only]
    @Test
    @IR(counts = {IRNode.MUL_VI, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testIntMaskedMulZero(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, intInput, index);
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, IntVector.broadcast(I_SPECIES, 0), mask)
         .intoArray(intOutput, index);
    }

    @Run(test = "testIntMaskedMulZero")
    public void runIntMaskedMulZero() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMulZero(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(intOutput[i], maskArr[i] ? (int) 0 : intInput[i]);
        }
    }

    // ========================= Long Tests =========================

    // AddV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.ADD_VL})
    public void testLongAddZero(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        v.add(LongVector.broadcast(L_SPECIES, 0L))
         .intoArray(longOutput, index);
    }

    @Run(test = "testLongAddZero")
    public void runLongAddZero() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongAddZero(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], longInput[i]);
        }
    }

    // AddV(Replicate(0), X) => X
    @Test
    @IR(failOn = {IRNode.ADD_VL})
    public void testLongZeroAddX(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        LongVector.broadcast(L_SPECIES, 0L).add(v)
                 .intoArray(longOutput, index);
    }

    @Run(test = "testLongZeroAddX")
    public void runLongZeroAddX() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongZeroAddX(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], longInput[i]);
        }
    }

    // MulV(X, Replicate(1)) => X
    @Test
    @IR(failOn = {IRNode.MUL_VL})
    public void testLongMulOne(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        v.mul(LongVector.broadcast(L_SPECIES, 1L))
         .intoArray(longOutput, index);
    }

    @Run(test = "testLongMulOne")
    public void runLongMulOne() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMulOne(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], longInput[i]);
        }
    }

    // MulV(Replicate(1), X) => X
    @Test
    @IR(failOn = {IRNode.MUL_VL})
    public void testLongOneMulX(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        LongVector.broadcast(L_SPECIES, 1L).mul(v)
                 .intoArray(longOutput, index);
    }

    @Run(test = "testLongOneMulX")
    public void runLongOneMulX() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongOneMulX(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], longInput[i]);
        }
    }

    // SubV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.SUB_VL})
    public void testLongSubZero(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        v.sub(LongVector.broadcast(L_SPECIES, 0L))
         .intoArray(longOutput, index);
    }

    @Run(test = "testLongSubZero")
    public void runLongSubZero() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongSubZero(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], longInput[i]);
        }
    }

    // SubV(X, X) => Replicate(0) [integral only]
    @Test
    @IR(failOn = {IRNode.SUB_VL})
    public void testLongSubSelf(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        v.sub(v).intoArray(longOutput, index);
    }

    @Run(test = "testLongSubSelf")
    public void runLongSubSelf() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongSubSelf(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], (long) 0);
        }
    }

    // MulV(X, Replicate(0)) => Replicate(0) [integral only]
    @Test
    @IR(failOn = {IRNode.MUL_VL})
    public void testLongMulZero(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        v.mul(LongVector.broadcast(L_SPECIES, 0L))
         .intoArray(longOutput, index);
    }

    @Run(test = "testLongMulZero")
    public void runLongMulZero() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMulZero(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], (long) 0);
        }
    }

    // Predicated: AddV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.ADD_VL},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testLongMaskedAddZero(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.ADD, LongVector.broadcast(L_SPECIES, 0L), mask)
         .intoArray(longOutput, index);
    }

    @Run(test = "testLongMaskedAddZero")
    public void runLongMaskedAddZero() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedAddZero(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], longInput[i]);
        }
    }

    // Predicated: SubV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.SUB_VL},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testLongMaskedSubZero(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, LongVector.broadcast(L_SPECIES, 0L), mask)
         .intoArray(longOutput, index);
    }

    @Run(test = "testLongMaskedSubZero")
    public void runLongMaskedSubZero() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedSubZero(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], longInput[i]);
        }
    }

    // Predicated: MulV(X, Replicate(1), mask) => X
    @Test
    @IR(failOn = {IRNode.MUL_VL},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testLongMaskedMulOne(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, LongVector.broadcast(L_SPECIES, 1L), mask)
         .intoArray(longOutput, index);
    }

    @Run(test = "testLongMaskedMulOne")
    public void runLongMaskedMulOne() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMulOne(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], longInput[i]);
        }
    }

    // Negative: predicated AddV(Replicate(0), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.ADD_VL, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testLongMaskedZeroAddX(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, maskArr, index);
        LongVector.broadcast(L_SPECIES, 0L)
                 .lanewise(VectorOperators.ADD, v, mask)
                 .intoArray(longOutput, index);
    }

    @Run(test = "testLongMaskedZeroAddX")
    public void runLongMaskedZeroAddX() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedZeroAddX(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], maskArr[i] ? longInput[i] : 0L);
        }
    }

    // Negative: predicated MulV(Replicate(1), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.MUL_VL, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testLongMaskedOneMulX(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, maskArr, index);
        LongVector.broadcast(L_SPECIES, 1L)
                 .lanewise(VectorOperators.MUL, v, mask)
                 .intoArray(longOutput, index);
    }

    @Run(test = "testLongMaskedOneMulX")
    public void runLongMaskedOneMulX() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedOneMulX(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], maskArr[i] ? longInput[i] : 1L);
        }
    }

    // Negative: predicated SubV(X, X, mask) must NOT be folded [integral only]
    @Test
    @IR(counts = {IRNode.SUB_VL, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testLongMaskedSubSelf(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, v, mask)
         .intoArray(longOutput, index);
    }

    @Run(test = "testLongMaskedSubSelf")
    public void runLongMaskedSubSelf() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedSubSelf(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], maskArr[i] ? (long) 0 : longInput[i]);
        }
    }

    // Negative: predicated MulV(X, Replicate(0), mask) must NOT be folded [integral only]
    @Test
    @IR(counts = {IRNode.MUL_VL, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testLongMaskedMulZero(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, longInput, index);
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, LongVector.broadcast(L_SPECIES, 0L), mask)
         .intoArray(longOutput, index);
    }

    @Run(test = "testLongMaskedMulZero")
    public void runLongMaskedMulZero() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMulZero(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(longOutput[i], maskArr[i] ? (long) 0 : longInput[i]);
        }
    }

    // ========================= Float Tests =========================

    // MulV(X, Replicate(1)) => X
    @Test
    @IR(failOn = {IRNode.MUL_VF})
    public void testFloatMulOne(int index) {
        FloatVector v = FloatVector.fromArray(F_SPECIES, floatInput, index);
        v.mul(FloatVector.broadcast(F_SPECIES, 1.0f))
         .intoArray(floatOutput, index);
    }

    @Run(test = "testFloatMulOne")
    public void runFloatMulOne() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMulOne(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(floatOutput[i], floatInput[i]);
        }
    }

    // MulV(Replicate(1), X) => X
    @Test
    @IR(failOn = {IRNode.MUL_VF})
    public void testFloatOneMulX(int index) {
        FloatVector v = FloatVector.fromArray(F_SPECIES, floatInput, index);
        FloatVector.broadcast(F_SPECIES, 1.0f).mul(v)
                 .intoArray(floatOutput, index);
    }

    @Run(test = "testFloatOneMulX")
    public void runFloatOneMulX() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatOneMulX(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(floatOutput[i], floatInput[i]);
        }
    }

    // SubV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.SUB_VF})
    public void testFloatSubZero(int index) {
        FloatVector v = FloatVector.fromArray(F_SPECIES, floatInput, index);
        v.sub(FloatVector.broadcast(F_SPECIES, 0.0f))
         .intoArray(floatOutput, index);
    }

    @Run(test = "testFloatSubZero")
    public void runFloatSubZero() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatSubZero(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(floatOutput[i], floatInput[i]);
        }
    }

    // Predicated: SubV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.SUB_VF},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testFloatMaskedSubZero(int index) {
        FloatVector v = FloatVector.fromArray(F_SPECIES, floatInput, index);
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, FloatVector.broadcast(F_SPECIES, 0.0f), mask)
         .intoArray(floatOutput, index);
    }

    @Run(test = "testFloatMaskedSubZero")
    public void runFloatMaskedSubZero() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedSubZero(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(floatOutput[i], floatInput[i]);
        }
    }

    // Predicated: MulV(X, Replicate(1), mask) => X
    @Test
    @IR(failOn = {IRNode.MUL_VF},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testFloatMaskedMulOne(int index) {
        FloatVector v = FloatVector.fromArray(F_SPECIES, floatInput, index);
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, FloatVector.broadcast(F_SPECIES, 1.0f), mask)
         .intoArray(floatOutput, index);
    }

    @Run(test = "testFloatMaskedMulOne")
    public void runFloatMaskedMulOne() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedMulOne(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(floatOutput[i], floatInput[i]);
        }
    }

    // Negative: predicated MulV(Replicate(1), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.MUL_VF, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testFloatMaskedOneMulX(int index) {
        FloatVector v = FloatVector.fromArray(F_SPECIES, floatInput, index);
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, maskArr, index);
        FloatVector.broadcast(F_SPECIES, 1.0f)
                 .lanewise(VectorOperators.MUL, v, mask)
                 .intoArray(floatOutput, index);
    }

    @Run(test = "testFloatMaskedOneMulX")
    public void runFloatMaskedOneMulX() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedOneMulX(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(floatOutput[i], maskArr[i] ? floatInput[i] : 1.0f);
        }
    }

    // ========================= Double Tests =========================

    // MulV(X, Replicate(1)) => X
    @Test
    @IR(failOn = {IRNode.MUL_VD})
    public void testDoubleMulOne(int index) {
        DoubleVector v = DoubleVector.fromArray(D_SPECIES, doubleInput, index);
        v.mul(DoubleVector.broadcast(D_SPECIES, 1.0))
         .intoArray(doubleOutput, index);
    }

    @Run(test = "testDoubleMulOne")
    public void runDoubleMulOne() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMulOne(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(doubleOutput[i], doubleInput[i]);
        }
    }

    // MulV(Replicate(1), X) => X
    @Test
    @IR(failOn = {IRNode.MUL_VD})
    public void testDoubleOneMulX(int index) {
        DoubleVector v = DoubleVector.fromArray(D_SPECIES, doubleInput, index);
        DoubleVector.broadcast(D_SPECIES, 1.0).mul(v)
                 .intoArray(doubleOutput, index);
    }

    @Run(test = "testDoubleOneMulX")
    public void runDoubleOneMulX() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleOneMulX(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(doubleOutput[i], doubleInput[i]);
        }
    }

    // SubV(X, Replicate(0)) => X
    @Test
    @IR(failOn = {IRNode.SUB_VD})
    public void testDoubleSubZero(int index) {
        DoubleVector v = DoubleVector.fromArray(D_SPECIES, doubleInput, index);
        v.sub(DoubleVector.broadcast(D_SPECIES, 0.0))
         .intoArray(doubleOutput, index);
    }

    @Run(test = "testDoubleSubZero")
    public void runDoubleSubZero() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleSubZero(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(doubleOutput[i], doubleInput[i]);
        }
    }

    // Predicated: SubV(X, Replicate(0), mask) => X
    @Test
    @IR(failOn = {IRNode.SUB_VD},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testDoubleMaskedSubZero(int index) {
        DoubleVector v = DoubleVector.fromArray(D_SPECIES, doubleInput, index);
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.SUB, DoubleVector.broadcast(D_SPECIES, 0.0), mask)
         .intoArray(doubleOutput, index);
    }

    @Run(test = "testDoubleMaskedSubZero")
    public void runDoubleMaskedSubZero() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedSubZero(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(doubleOutput[i], doubleInput[i]);
        }
    }

    // Predicated: MulV(X, Replicate(1), mask) => X
    @Test
    @IR(failOn = {IRNode.MUL_VD},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testDoubleMaskedMulOne(int index) {
        DoubleVector v = DoubleVector.fromArray(D_SPECIES, doubleInput, index);
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, maskArr, index);
        v.lanewise(VectorOperators.MUL, DoubleVector.broadcast(D_SPECIES, 1.0), mask)
         .intoArray(doubleOutput, index);
    }

    @Run(test = "testDoubleMaskedMulOne")
    public void runDoubleMaskedMulOne() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedMulOne(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(doubleOutput[i], doubleInput[i]);
        }
    }

    // Negative: predicated MulV(Replicate(1), X, mask) must NOT be folded
    @Test
    @IR(counts = {IRNode.MUL_VD, IRNode.VECTOR_SIZE_ANY, " >= 1 "},
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true"})
    public void testDoubleMaskedOneMulX(int index) {
        DoubleVector v = DoubleVector.fromArray(D_SPECIES, doubleInput, index);
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, maskArr, index);
        DoubleVector.broadcast(D_SPECIES, 1.0)
                 .lanewise(VectorOperators.MUL, v, mask)
                 .intoArray(doubleOutput, index);
    }

    @Run(test = "testDoubleMaskedOneMulX")
    public void runDoubleMaskedOneMulX() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedOneMulX(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(doubleOutput[i], maskArr[i] ? doubleInput[i] : 1.0);
        }
    }

}
