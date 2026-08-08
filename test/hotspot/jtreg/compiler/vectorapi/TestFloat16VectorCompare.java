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
* @bug 8388845
* @summary Test intrinsification of Float16Vector compare operation
* @modules jdk.incubator.vector
* @library /test/lib /
* @compile TestFloat16VectorCompare.java
* @run driver ${test.main.class}
*/

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import static java.lang.Float.float16ToFloat;
import jdk.test.lib.*;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

public class TestFloat16VectorCompare {
    short[] input1;
    short[] input2;
    boolean[] maskArr;
    boolean[] output;
    static final int LEN = 512;

    static final VectorSpecies<Float16> SPECIES = Float16Vector.SPECIES_PREFERRED;

    public static void main(String args[]) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    public TestFloat16VectorCompare() {
        input1 = new short[LEN];
        input2 = new short[LEN];
        maskArr = new boolean[LEN];
        output = new boolean[LEN];

        Generator<Short> gen = G.float16s();
        for (int i = 0; i < LEN; ++i) {
            input1[i] = gen.next();
            input2[i] = ((i & 1) == 0) ? input1[i] : gen.next();
            maskArr[i] = ((i % 3) != 0);
        }
    }

    static boolean scalarCmp(VectorOperators.Comparison op, short a, short b) {
        float x = float16ToFloat(a);
        float y = float16ToFloat(b);
        if (op == VectorOperators.EQ) return x == y;
        if (op == VectorOperators.NE) return x != y;
        if (op == VectorOperators.LT) return x <  y;
        if (op == VectorOperators.LE) return x <= y;
        if (op == VectorOperators.GT) return x >  y;
        return x >= y; // GE
    }

    void check(VectorOperators.Comparison op, boolean masked) {
        for (int i = 0; i < LEN; ++i) {
            boolean expected = scalarCmp(op, input1[i], input2[i]) && (!masked || maskArr[i]);
            if (expected != output[i]) {
                throw new AssertionError("Mismatch op=" + op + " masked=" + masked + " i=" + i
                    + " a=" + float16ToFloat(input1[i]) + " b=" + float16ToFloat(input2[i])
                    + " actual=" + output[i] + " expected=" + expected);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpEQ() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.EQ, Float16Vector.fromArray(SPECIES, input2, i)).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpEQ")
    void checkEQ() { check(VectorOperators.EQ, false); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpNE() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.NE, Float16Vector.fromArray(SPECIES, input2, i)).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpNE")
    void checkNE() { check(VectorOperators.NE, false); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpLT() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.LT, Float16Vector.fromArray(SPECIES, input2, i)).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpLT")
    void checkLT() { check(VectorOperators.LT, false); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpLE() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.LE, Float16Vector.fromArray(SPECIES, input2, i)).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpLE")
    void checkLE() { check(VectorOperators.LE, false); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpGT() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.GT, Float16Vector.fromArray(SPECIES, input2, i)).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpGT")
    void checkGT() { check(VectorOperators.GT, false); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpGE() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.GE, Float16Vector.fromArray(SPECIES, input2, i)).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpGE")
    void checkGE() { check(VectorOperators.GE, false); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpMaskedEQ() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, maskArr, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.EQ, Float16Vector.fromArray(SPECIES, input2, i), m).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpMaskedEQ")
    void checkMaskedEQ() { check(VectorOperators.EQ, true); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpMaskedNE() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, maskArr, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.NE, Float16Vector.fromArray(SPECIES, input2, i), m).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpMaskedNE")
    void checkMaskedNE() { check(VectorOperators.NE, true); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpMaskedLT() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, maskArr, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.LT, Float16Vector.fromArray(SPECIES, input2, i), m).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpMaskedLT")
    void checkMaskedLT() { check(VectorOperators.LT, true); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpMaskedLE() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, maskArr, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.LE, Float16Vector.fromArray(SPECIES, input2, i), m).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpMaskedLE")
    void checkMaskedLE() { check(VectorOperators.LE, true); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpMaskedGT() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, maskArr, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.GT, Float16Vector.fromArray(SPECIES, input2, i), m).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpMaskedGT")
    void checkMaskedGT() { check(VectorOperators.GT, true); }

    @Test
    @IR(counts = {IRNode.VECTOR_MASK_CMP_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    void vectorCmpMaskedGE() {
        for (int i = 0; i < SPECIES.loopBound(LEN); i += SPECIES.length()) {
            VectorMask<Float16> m = VectorMask.fromArray(SPECIES, maskArr, i);
            Float16Vector.fromArray(SPECIES, input1, i)
                .compare(VectorOperators.GE, Float16Vector.fromArray(SPECIES, input2, i), m).intoArray(output, i);
        }
    }
    @Check(test = "vectorCmpMaskedGE")
    void checkMaskedGE() { check(VectorOperators.GE, true); }
}
