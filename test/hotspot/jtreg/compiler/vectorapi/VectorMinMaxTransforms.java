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

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import jdk.incubator.vector.*;

/**
 * @test
 * @bug 8372797
 * @key randomness
 * @library /test/lib /
 * @summary IR verification for MinV/MaxV Identity and Ideal transforms
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */
public class VectorMinMaxTransforms {
    private static final int LENGTH = 256;
    private static final Generators RD = Generators.G;

    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    private static int[] ia, ib, ir;

    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_PREFERRED;
    private static long[] la, lb, lr;

    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_PREFERRED;
    private static float[] fa, fb, fr;

    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_PREFERRED;
    private static double[] da, db, dr;

    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_PREFERRED;
    private static byte[] ba, bb, br;

    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;
    private static short[] sa, sb, sr;

    private static boolean[] m1arr, m2arr, m3arr;

    static {
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ir = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        lr = new long[LENGTH];
        fa = new float[LENGTH];
        fb = new float[LENGTH];
        fr = new float[LENGTH];
        da = new double[LENGTH];
        db = new double[LENGTH];
        dr = new double[LENGTH];
        ba = new byte[LENGTH];
        bb = new byte[LENGTH];
        br = new byte[LENGTH];
        sa = new short[LENGTH];
        sb = new short[LENGTH];
        sr = new short[LENGTH];
        m1arr = new boolean[LENGTH];
        m2arr = new boolean[LENGTH];
        m3arr = new boolean[LENGTH];

        Generator<Integer> iGen = RD.ints();
        Generator<Long> lGen = RD.longs();
        Generator<Float> fGen = RD.floats();
        Generator<Double> dGen = RD.doubles();

        RD.fill(iGen, ia);
        RD.fill(iGen, ib);
        RD.fill(lGen, la);
        RD.fill(lGen, lb);
        RD.fill(fGen, fa);
        RD.fill(fGen, fb);
        RD.fill(dGen, da);
        RD.fill(dGen, db);

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = iGen.next().byteValue();
            bb[i] = iGen.next().byteValue();
            sa[i] = iGen.next().shortValue();
            sb[i] = iGen.next().shortValue();
            m1arr[i] = (i % 2) == 0;
            m2arr[i] = (i % 2) != 0;
            m3arr[i] = (i % 3) == 0;
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    // ---------- Int: Identity min(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testIntMinIdentity(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, ia, index);
        v.lanewise(VectorOperators.MIN, v).intoArray(ir, index);
    }

    @Run(test = "testIntMinIdentity")
    public void runIntMinIdentity() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMinIdentity(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int expected = Math.min(ia[i], ia[i]);
            Verify.checkEQ(ir[i], expected);
        }
    }

    // ---------- Int: Identity max(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testIntMaxIdentity(int index) {
        IntVector v = IntVector.fromArray(I_SPECIES, ia, index);
        v.lanewise(VectorOperators.MAX, v).intoArray(ir, index);
    }

    @Run(test = "testIntMaxIdentity")
    public void runIntMaxIdentity() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaxIdentity(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int expected = Math.max(ia[i], ia[i]);
            Verify.checkEQ(ir[i], expected);
        }
    }

    // ---------- Int: Ideal min(min(a,b), max(a,b)) => min(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testIntMinIdeal(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(ir, index);
    }

    @Run(test = "testIntMinIdeal")
    public void runIntMinIdeal() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMinIdeal(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int expected = Math.min(Math.min(ia[i], ib[i]), Math.max(ia[i], ib[i]));
            Verify.checkEQ(ir[i], expected);
        }
    }

    // ---------- Int: Ideal max(min(a,b), max(a,b)) => max(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testIntMaxIdeal(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(ir, index);
    }

    @Run(test = "testIntMaxIdeal")
    public void runIntMaxIdeal() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaxIdeal(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int expected = Math.max(Math.min(ia[i], ib[i]), Math.max(ia[i], ib[i]));
            Verify.checkEQ(ir[i], expected);
        }
    }

    // ---------- Long: Identity and Ideal ----------
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    public void testLongMinIdentity(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, la, index);
        v.lanewise(VectorOperators.MIN, v).intoArray(lr, index);
    }

    @Run(test = "testLongMinIdentity")
    public void runLongMinIdentity() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMinIdentity(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(lr[i], la[i]);
        }
    }

    // ---------- Long: Identity max(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    public void testLongMaxIdentity(int index) {
        LongVector v = LongVector.fromArray(L_SPECIES, la, index);
        v.lanewise(VectorOperators.MAX, v).intoArray(lr, index);
    }

    @Run(test = "testLongMaxIdentity")
    public void runLongMaxIdentity() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaxIdentity(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(lr[i], la[i]);
        }
    }

    // ---------- Long: Ideal min(min(a,b), max(a,b)) => min(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    public void testLongMinIdeal(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(lr, index);
    }

    @Run(test = "testLongMinIdeal")
    public void runLongMinIdeal() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMinIdeal(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long expected = Math.min(Math.min(la[i], lb[i]), Math.max(la[i], lb[i]));
            Verify.checkEQ(lr[i], expected);
        }
    }

    // ---------- Long: Ideal max(min(a,b), max(a,b)) => max(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true", "rvv", "true"})
    public void testLongMaxIdeal(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(lr, index);
    }

    @Run(test = "testLongMaxIdeal")
    public void runLongMaxIdeal() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaxIdeal(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long expected = Math.max(Math.min(la[i], lb[i]), Math.max(la[i], lb[i]));
            Verify.checkEQ(lr[i], expected);
        }
    }

    // ---------- Float: Identity min(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testFloatMinIdentity(int index) {
        FloatVector v = FloatVector.fromArray(F_SPECIES, fa, index);
        v.lanewise(VectorOperators.MIN, v).intoArray(fr, index);
    }

    @Run(test = "testFloatMinIdentity")
    public void runFloatMinIdentity() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMinIdentity(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(fr[i], fa[i]);
        }
    }

    // ---------- Float: Identity max(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testFloatMaxIdentity(int index) {
        FloatVector v = FloatVector.fromArray(F_SPECIES, fa, index);
        v.lanewise(VectorOperators.MAX, v).intoArray(fr, index);
    }

    @Run(test = "testFloatMaxIdentity")
    public void runFloatMaxIdentity() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaxIdentity(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(fr[i], fa[i]);
        }
    }

    // ---------- Float: Ideal min(min(a,b), max(a,b)) => min(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testFloatMinIdeal(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMinIdeal")
    public void runFloatMinIdeal() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMinIdeal(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float expected = Math.min(Math.min(fa[i], fb[i]), Math.max(fa[i], fb[i]));
            Verify.checkEQ(fr[i], expected);
        }
    }

    // ---------- Float: Ideal max(min(a,b), max(a,b)) => max(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testFloatMaxIdeal(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMaxIdeal")
    public void runFloatMaxIdeal() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaxIdeal(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float expected = Math.max(Math.min(fa[i], fb[i]), Math.max(fa[i], fb[i]));
            Verify.checkEQ(fr[i], expected);
        }
    }

    // ---------- Double: Identity min(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testDoubleMinIdentity(int index) {
        DoubleVector v = DoubleVector.fromArray(D_SPECIES, da, index);
        v.lanewise(VectorOperators.MIN, v).intoArray(dr, index);
    }

    @Run(test = "testDoubleMinIdentity")
    public void runDoubleMinIdentity() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMinIdentity(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(dr[i], da[i]);
        }
    }

    // ---------- Double: Identity max(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testDoubleMaxIdentity(int index) {
        DoubleVector v = DoubleVector.fromArray(D_SPECIES, da, index);
        v.lanewise(VectorOperators.MAX, v).intoArray(dr, index);
    }

    @Run(test = "testDoubleMaxIdentity")
    public void runDoubleMaxIdentity() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaxIdentity(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(dr[i], da[i]);
        }
    }

    // ---------- Double: Ideal min(min(a,b), max(a,b)) => min(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testDoubleMinIdeal(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMinIdeal")
    public void runDoubleMinIdeal() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMinIdeal(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double expected = Math.min(Math.min(da[i], db[i]), Math.max(da[i], db[i]));
            Verify.checkEQ(dr[i], expected);
        }
    }

    // ---------- Double: Ideal max(min(a,b), max(a,b)) => max(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testDoubleMaxIdeal(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMaxIdeal")
    public void runDoubleMaxIdeal() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaxIdeal(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double expected = Math.max(Math.min(da[i], db[i]), Math.max(da[i], db[i]));
            Verify.checkEQ(dr[i], expected);
        }
    }

    // ---------- Byte: Identity min(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testByteMinIdentity(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, ba, index);
        v.lanewise(VectorOperators.MIN, v).intoArray(br, index);
    }

    @Run(test = "testByteMinIdentity")
    public void runByteMinIdentity() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMinIdentity(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(br[i], ba[i]);
        }
    }

    // ---------- Byte: Identity max(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testByteMaxIdentity(int index) {
        ByteVector v = ByteVector.fromArray(B_SPECIES, ba, index);
        v.lanewise(VectorOperators.MAX, v).intoArray(br, index);
    }

    @Run(test = "testByteMaxIdentity")
    public void runByteMaxIdentity() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaxIdentity(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(br[i], ba[i]);
        }
    }

    // ---------- Byte: Ideal min(min(a,b), max(a,b)) => min(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testByteMinIdeal(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(br, index);
    }

    @Run(test = "testByteMinIdeal")
    public void runByteMinIdeal() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMinIdeal(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte expected = (byte) Math.min(Math.min(ba[i], bb[i]), Math.max(ba[i], bb[i]));
            Verify.checkEQ(br[i], expected);
        }
    }

    // ---------- Byte: Ideal max(min(a,b), max(a,b)) => max(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testByteMaxIdeal(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(br, index);
    }

    @Run(test = "testByteMaxIdeal")
    public void runByteMaxIdeal() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaxIdeal(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte expected = (byte) Math.max(Math.min(ba[i], bb[i]), Math.max(ba[i], bb[i]));
            Verify.checkEQ(br[i], expected);
        }
    }

    // ---------- Short: Identity min(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testShortMinIdentity(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, sa, index);
        v.lanewise(VectorOperators.MIN, v).intoArray(sr, index);
    }

    @Run(test = "testShortMinIdentity")
    public void runShortMinIdentity() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMinIdentity(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(sr[i], sa[i]);
        }
    }

    // ---------- Short: Identity max(a,a)=>a ----------
    @Test
    @IR(counts = { IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testShortMaxIdentity(int index) {
        ShortVector v = ShortVector.fromArray(S_SPECIES, sa, index);
        v.lanewise(VectorOperators.MAX, v).intoArray(sr, index);
    }

    @Run(test = "testShortMaxIdentity")
    public void runShortMaxIdentity() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaxIdentity(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            Verify.checkEQ(sr[i], sa[i]);
        }
    }

    // ---------- Short: Ideal min(min(a,b), max(a,b)) => min(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testShortMinIdeal(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(sr, index);
    }

    @Run(test = "testShortMinIdeal")
    public void runShortMinIdeal() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMinIdeal(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short expected = (short) Math.min(Math.min(sa[i], sb[i]), Math.max(sa[i], sb[i]));
            Verify.checkEQ(sr[i], expected);
        }
    }

    // ---------- Short: Ideal max(min(a,b), max(a,b)) => max(a,b) ----------
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true", "rvv", "true"})
    public void testShortMaxIdeal(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        v1.lanewise(VectorOperators.MIN, v2)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2))
          .intoArray(sr, index);
    }

    @Run(test = "testShortMaxIdeal")
    public void runShortMaxIdeal() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaxIdeal(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short expected = (short) Math.max(Math.min(sa[i], sb[i]), Math.max(sa[i], sb[i]));
            Verify.checkEQ(sr[i], expected);
        }
    }

    // Predicated Int: min(min(a,b,m), max(a,b,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testIntMaskedMinIdealSameMask(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        VectorMask<Integer> m = VectorMask.fromArray(I_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(ir, index);
    }

    @Run(test = "testIntMaskedMinIdealSameMask")
    public void runIntMaskedMinIdealSameMask() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMinIdealSameMask(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int a = ia[i], b = ib[i];
            boolean mask = m1arr[i];
            int minAB = mask ? Math.min(a, b) : a;
            int maxAB = mask ? Math.max(a, b) : a;
            int expected = mask ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(ir[i], expected);
        }
    }

    // Predicated Int: max(min(a,b,m), max(a,b,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testIntMaskedMaxIdealSameMask(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        VectorMask<Integer> m = VectorMask.fromArray(I_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(ir, index);
    }

    @Run(test = "testIntMaskedMaxIdealSameMask")
    public void runIntMaskedMaxIdealSameMask() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMaxIdealSameMask(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int a = ia[i], b = ib[i];
            boolean mask = m1arr[i];
            int minAB = mask ? Math.min(a, b) : a;
            int maxAB = mask ? Math.max(a, b) : a;
            int expected = mask ? Math.max(minAB, maxAB) : minAB;
            Verify.checkEQ(ir[i], expected);
        }
    }

    // Predicated Int: max(min(a,b,m), max(b,a,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testIntMaskedMaxIdealFlippedInputs(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        VectorMask<Integer> m = VectorMask.fromArray(I_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(ir, index);
    }

    @Run(test = "testIntMaskedMaxIdealFlippedInputs")
    public void runIntMaskedMaxIdealFlippedInputs() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMaxIdealFlippedInputs(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int a = ia[i], b = ib[i];
            boolean mask = m1arr[i];
            int minAB = mask ? Math.min(a, b) : a;
            int maxBA = mask ? Math.max(b, a) : b;
            int expected = mask ? Math.max(minAB, maxBA) : minAB;
            Verify.checkEQ(ir[i], expected);
        }
    }

    // Predicated Int: min(min(a,b,m), max(b,a,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testIntMaskedMinIdealFlippedInputs(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        VectorMask<Integer> m = VectorMask.fromArray(I_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(ir, index);
    }

    @Run(test = "testIntMaskedMinIdealFlippedInputs")
    public void runIntMaskedMinIdealFlippedInputs() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMinIdealFlippedInputs(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int a = ia[i], b = ib[i];
            boolean mask = m1arr[i];
            int minAB = mask ? Math.min(a, b) : a;
            int maxBA = mask ? Math.max(b, a) : b;
            int expected = mask ? Math.min(minAB, maxBA) : minAB;
            Verify.checkEQ(ir[i], expected);
        }
    }

    // Predicated Int: min(min(a,b,m1), max(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testIntMaskedMinIdealDiffMaskMinMax(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(I_SPECIES, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(I_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask1)
          .intoArray(ir, index);
    }

    @Run(test = "testIntMaskedMinIdealDiffMaskMinMax")
    public void runIntMaskedMinIdealDiffMaskMinMax() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMinIdealDiffMaskMinMax(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int a = ia[i], b = ib[i];
            int minAB = m1arr[i] ? Math.min(a, b) : a;
            int maxAB = m2arr[i] ? Math.max(a, b) : a;
            int expected = m1arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(ir[i], expected);
        }
    }


    // Predicated Int: min(min(a,b,m2), max(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testIntMaskedMinIdealDiffMaskMinMaxSwapped(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(I_SPECIES, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(I_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask1)
          .intoArray(ir, index);
    }

    @Run(test = "testIntMaskedMinIdealDiffMaskMinMaxSwapped")
    public void runIntMaskedMinIdealDiffMaskMinMaxSwapped() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMinIdealDiffMaskMinMaxSwapped(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int a = ia[i], b = ib[i];
            int minAB = m2arr[i] ? Math.min(a, b) : a;
            int maxAB = m1arr[i] ? Math.max(a, b) : a;
            int expected = m1arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(ir[i], expected);
        }
    }
    // Predicated Int: min(min(a,b,m1), max(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testIntMaskedMinIdealDiffMaskOuter(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(I_SPECIES, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(I_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask2)
          .intoArray(ir, index);
    }

    @Run(test = "testIntMaskedMinIdealDiffMaskOuter")
    public void runIntMaskedMinIdealDiffMaskOuter() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMinIdealDiffMaskOuter(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int a = ia[i], b = ib[i];
            int minAB = m1arr[i] ? Math.min(a, b) : a;
            int maxAB = m1arr[i] ? Math.max(a, b) : a;
            int expected = m2arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(ir[i], expected);
        }
    }

    // Predicated Int: min(min(a,b,m1), max(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VI, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VI, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testIntMaskedMinIdealAllDiffMask(int index) {
        IntVector v1 = IntVector.fromArray(I_SPECIES, ia, index);
        IntVector v2 = IntVector.fromArray(I_SPECIES, ib, index);
        VectorMask<Integer> mask1 = VectorMask.fromArray(I_SPECIES, m1arr, index);
        VectorMask<Integer> mask2 = VectorMask.fromArray(I_SPECIES, m2arr, index);
        VectorMask<Integer> mask3 = VectorMask.fromArray(I_SPECIES, m3arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask3)
          .intoArray(ir, index);
    }

    @Run(test = "testIntMaskedMinIdealAllDiffMask")
    public void runIntMaskedMinIdealAllDiffMask() {
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i += I_SPECIES.length()) {
            testIntMaskedMinIdealAllDiffMask(i);
        }
        for (int i = 0; i < I_SPECIES.loopBound(LENGTH); i++) {
            int a = ia[i], b = ib[i];
            int minAB = m1arr[i] ? Math.min(a, b) : a;
            int maxAB = m2arr[i] ? Math.max(a, b) : a;
            int expected = m3arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(ir[i], expected);
        }
    }

    // Predicated Byte: min(min(a,b,m), max(a,b,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testByteMaskedMinIdealSameMask(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        VectorMask<Byte> m = VectorMask.fromArray(B_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(br, index);
    }

    @Run(test = "testByteMaskedMinIdealSameMask")
    public void runByteMaskedMinIdealSameMask() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMinIdealSameMask(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte a = ba[i], b = bb[i];
            boolean mask = m1arr[i];
            byte minAB = (byte)(mask ? Math.min(a, b) : a);
            byte maxAB = (byte)(mask ? Math.max(a, b) : a);
            byte expected = (byte)(mask ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(br[i], expected);
        }
    }

    // Predicated Byte: max(min(a,b,m), max(a,b,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testByteMaskedMaxIdealSameMask(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        VectorMask<Byte> m = VectorMask.fromArray(B_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(br, index);
    }

    @Run(test = "testByteMaskedMaxIdealSameMask")
    public void runByteMaskedMaxIdealSameMask() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMaxIdealSameMask(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte a = ba[i], b = bb[i];
            boolean mask = m1arr[i];
            byte minAB = (byte)(mask ? Math.min(a, b) : a);
            byte maxAB = (byte)(mask ? Math.max(a, b) : a);
            byte expected = (byte)(mask ? Math.max(minAB, maxAB) : minAB);
            Verify.checkEQ(br[i], expected);
        }
    }

    // Predicated Byte: max(min(a,b,m), max(b,a,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testByteMaskedMaxIdealFlippedInputs(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        VectorMask<Byte> m = VectorMask.fromArray(B_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(br, index);
    }

    @Run(test = "testByteMaskedMaxIdealFlippedInputs")
    public void runByteMaskedMaxIdealFlippedInputs() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMaxIdealFlippedInputs(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte a = ba[i], b = bb[i];
            boolean mask = m1arr[i];
            byte minAB = (byte)(mask ? Math.min(a, b) : a);
            byte maxBA = (byte)(mask ? Math.max(b, a) : b);
            byte expected = (byte)(mask ? Math.max(minAB, maxBA) : minAB);
            Verify.checkEQ(br[i], expected);
        }
    }

    // Predicated Byte: min(min(a,b,m), max(b,a,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testByteMaskedMinIdealFlippedInputs(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        VectorMask<Byte> m = VectorMask.fromArray(B_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(br, index);
    }

    @Run(test = "testByteMaskedMinIdealFlippedInputs")
    public void runByteMaskedMinIdealFlippedInputs() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMinIdealFlippedInputs(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte a = ba[i], b = bb[i];
            boolean mask = m1arr[i];
            byte minAB = (byte)(mask ? Math.min(a, b) : a);
            byte maxBA = (byte)(mask ? Math.max(b, a) : b);
            byte expected = (byte)(mask ? Math.min(minAB, maxBA) : minAB);
            Verify.checkEQ(br[i], expected);
        }
    }

    // Predicated Byte: min(min(a,b,m1), max(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testByteMaskedMinIdealDiffMaskMinMax(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(B_SPECIES, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(B_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask1)
          .intoArray(br, index);
    }

    @Run(test = "testByteMaskedMinIdealDiffMaskMinMax")
    public void runByteMaskedMinIdealDiffMaskMinMax() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMinIdealDiffMaskMinMax(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte a = ba[i], b = bb[i];
            byte minAB = (byte)(m1arr[i] ? Math.min(a, b) : a);
            byte maxAB = (byte)(m2arr[i] ? Math.max(a, b) : a);
            byte expected = (byte)(m1arr[i] ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(br[i], expected);
        }
    }

    // Predicated Byte: min(min(a,b,m2), max(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testByteMaskedMinIdealDiffMaskMinMaxSwapped(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(B_SPECIES, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(B_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask1)
          .intoArray(br, index);
    }

    @Run(test = "testByteMaskedMinIdealDiffMaskMinMaxSwapped")
    public void runByteMaskedMinIdealDiffMaskMinMaxSwapped() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMinIdealDiffMaskMinMaxSwapped(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte a = ba[i], b = bb[i];
            byte minAB = (byte)(m2arr[i] ? Math.min(a, b) : a);
            byte maxAB = (byte)(m1arr[i] ? Math.max(a, b) : a);
            byte expected = (byte)(m1arr[i] ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(br[i], expected);
        }
    }

    // Predicated Byte: min(min(a,b,m1), max(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testByteMaskedMinIdealDiffMaskOuter(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(B_SPECIES, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(B_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask2)
          .intoArray(br, index);
    }

    @Run(test = "testByteMaskedMinIdealDiffMaskOuter")
    public void runByteMaskedMinIdealDiffMaskOuter() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMinIdealDiffMaskOuter(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte a = ba[i], b = bb[i];
            byte minAB = (byte)(m1arr[i] ? Math.min(a, b) : a);
            byte maxAB = (byte)(m1arr[i] ? Math.max(a, b) : a);
            byte expected = (byte)(m2arr[i] ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(br[i], expected);
        }
    }

    // Predicated Byte: min(min(a,b,m1), max(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VB, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VB, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testByteMaskedMinIdealAllDiffMask(int index) {
        ByteVector v1 = ByteVector.fromArray(B_SPECIES, ba, index);
        ByteVector v2 = ByteVector.fromArray(B_SPECIES, bb, index);
        VectorMask<Byte> mask1 = VectorMask.fromArray(B_SPECIES, m1arr, index);
        VectorMask<Byte> mask2 = VectorMask.fromArray(B_SPECIES, m2arr, index);
        VectorMask<Byte> mask3 = VectorMask.fromArray(B_SPECIES, m3arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask3)
          .intoArray(br, index);
    }

    @Run(test = "testByteMaskedMinIdealAllDiffMask")
    public void runByteMaskedMinIdealAllDiffMask() {
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i += B_SPECIES.length()) {
            testByteMaskedMinIdealAllDiffMask(i);
        }
        for (int i = 0; i < B_SPECIES.loopBound(LENGTH); i++) {
            byte a = ba[i], b = bb[i];
            byte minAB = (byte)(m1arr[i] ? Math.min(a, b) : a);
            byte maxAB = (byte)(m2arr[i] ? Math.max(a, b) : a);
            byte expected = (byte)(m3arr[i] ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(br[i], expected);
        }
    }

    // Predicated Short: min(min(a,b,m), max(a,b,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testShortMaskedMinIdealSameMask(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        VectorMask<Short> m = VectorMask.fromArray(S_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(sr, index);
    }

    @Run(test = "testShortMaskedMinIdealSameMask")
    public void runShortMaskedMinIdealSameMask() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMinIdealSameMask(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short a = sa[i], b = sb[i];
            boolean mask = m1arr[i];
            short minAB = (short)(mask ? Math.min(a, b) : a);
            short maxAB = (short)(mask ? Math.max(a, b) : a);
            short expected = (short)(mask ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(sr[i], expected);
        }
    }

    // Predicated Short: max(min(a,b,m), max(a,b,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testShortMaskedMaxIdealSameMask(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        VectorMask<Short> m = VectorMask.fromArray(S_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(sr, index);
    }

    @Run(test = "testShortMaskedMaxIdealSameMask")
    public void runShortMaskedMaxIdealSameMask() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMaxIdealSameMask(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short a = sa[i], b = sb[i];
            boolean mask = m1arr[i];
            short minAB = (short)(mask ? Math.min(a, b) : a);
            short maxAB = (short)(mask ? Math.max(a, b) : a);
            short expected = (short)(mask ? Math.max(minAB, maxAB) : minAB);
            Verify.checkEQ(sr[i], expected);
        }
    }

    // Predicated Short: max(min(a,b,m), max(b,a,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testShortMaskedMaxIdealFlippedInputs(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        VectorMask<Short> m = VectorMask.fromArray(S_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(sr, index);
    }

    @Run(test = "testShortMaskedMaxIdealFlippedInputs")
    public void runShortMaskedMaxIdealFlippedInputs() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMaxIdealFlippedInputs(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short a = sa[i], b = sb[i];
            boolean mask = m1arr[i];
            short minAB = (short)(mask ? Math.min(a, b) : a);
            short maxBA = (short)(mask ? Math.max(b, a) : b);
            short expected = (short)(mask ? Math.max(minAB, maxBA) : minAB);
            Verify.checkEQ(sr[i], expected);
        }
    }

    // Predicated Short: min(min(a,b,m), max(b,a,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testShortMaskedMinIdealFlippedInputs(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        VectorMask<Short> m = VectorMask.fromArray(S_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(sr, index);
    }

    @Run(test = "testShortMaskedMinIdealFlippedInputs")
    public void runShortMaskedMinIdealFlippedInputs() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMinIdealFlippedInputs(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short a = sa[i], b = sb[i];
            boolean mask = m1arr[i];
            short minAB = (short)(mask ? Math.min(a, b) : a);
            short maxBA = (short)(mask ? Math.max(b, a) : b);
            short expected = (short)(mask ? Math.min(minAB, maxBA) : minAB);
            Verify.checkEQ(sr[i], expected);
        }
    }

    // Predicated Short: min(min(a,b,m1), max(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testShortMaskedMinIdealDiffMaskMinMax(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(S_SPECIES, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(S_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask1)
          .intoArray(sr, index);
    }

    @Run(test = "testShortMaskedMinIdealDiffMaskMinMax")
    public void runShortMaskedMinIdealDiffMaskMinMax() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMinIdealDiffMaskMinMax(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short a = sa[i], b = sb[i];
            short minAB = (short)(m1arr[i] ? Math.min(a, b) : a);
            short maxAB = (short)(m2arr[i] ? Math.max(a, b) : a);
            short expected = (short)(m1arr[i] ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(sr[i], expected);
        }
    }

    // Predicated Short: min(min(a,b,m2), max(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testShortMaskedMinIdealDiffMaskMinMaxSwapped(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(S_SPECIES, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(S_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask1)
          .intoArray(sr, index);
    }

    @Run(test = "testShortMaskedMinIdealDiffMaskMinMaxSwapped")
    public void runShortMaskedMinIdealDiffMaskMinMaxSwapped() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMinIdealDiffMaskMinMaxSwapped(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short a = sa[i], b = sb[i];
            short minAB = (short)(m2arr[i] ? Math.min(a, b) : a);
            short maxAB = (short)(m1arr[i] ? Math.max(a, b) : a);
            short expected = (short)(m1arr[i] ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(sr[i], expected);
        }
    }

    // Predicated Short: min(min(a,b,m1), max(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testShortMaskedMinIdealDiffMaskOuter(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(S_SPECIES, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(S_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask2)
          .intoArray(sr, index);
    }

    @Run(test = "testShortMaskedMinIdealDiffMaskOuter")
    public void runShortMaskedMinIdealDiffMaskOuter() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMinIdealDiffMaskOuter(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short a = sa[i], b = sb[i];
            short minAB = (short)(m1arr[i] ? Math.min(a, b) : a);
            short maxAB = (short)(m1arr[i] ? Math.max(a, b) : a);
            short expected = (short)(m2arr[i] ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(sr[i], expected);
        }
    }

    // Predicated Short: min(min(a,b,m1), max(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VS, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VS, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512bw", "true", "sve", "true", "rvv", "true"})
    public void testShortMaskedMinIdealAllDiffMask(int index) {
        ShortVector v1 = ShortVector.fromArray(S_SPECIES, sa, index);
        ShortVector v2 = ShortVector.fromArray(S_SPECIES, sb, index);
        VectorMask<Short> mask1 = VectorMask.fromArray(S_SPECIES, m1arr, index);
        VectorMask<Short> mask2 = VectorMask.fromArray(S_SPECIES, m2arr, index);
        VectorMask<Short> mask3 = VectorMask.fromArray(S_SPECIES, m3arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask3)
          .intoArray(sr, index);
    }

    @Run(test = "testShortMaskedMinIdealAllDiffMask")
    public void runShortMaskedMinIdealAllDiffMask() {
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i += S_SPECIES.length()) {
            testShortMaskedMinIdealAllDiffMask(i);
        }
        for (int i = 0; i < S_SPECIES.loopBound(LENGTH); i++) {
            short a = sa[i], b = sb[i];
            short minAB = (short)(m1arr[i] ? Math.min(a, b) : a);
            short maxAB = (short)(m2arr[i] ? Math.max(a, b) : a);
            short expected = (short)(m3arr[i] ? Math.min(minAB, maxAB) : minAB);
            Verify.checkEQ(sr[i], expected);
        }
    }

    // Predicated Long: min(min(a,b,m), max(a,b,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testLongMaskedMinIdealSameMask(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        VectorMask<Long> m = VectorMask.fromArray(L_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(lr, index);
    }

    @Run(test = "testLongMaskedMinIdealSameMask")
    public void runLongMaskedMinIdealSameMask() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMinIdealSameMask(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long a = la[i], b = lb[i];
            boolean mask = m1arr[i];
            long minAB = mask ? Math.min(a, b) : a;
            long maxAB = mask ? Math.max(a, b) : a;
            long expected = mask ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(lr[i], expected);
        }
    }

    // Predicated Long: max(min(a,b,m), max(a,b,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testLongMaskedMaxIdealSameMask(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        VectorMask<Long> m = VectorMask.fromArray(L_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(lr, index);
    }

    @Run(test = "testLongMaskedMaxIdealSameMask")
    public void runLongMaskedMaxIdealSameMask() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMaxIdealSameMask(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long a = la[i], b = lb[i];
            boolean mask = m1arr[i];
            long minAB = mask ? Math.min(a, b) : a;
            long maxAB = mask ? Math.max(a, b) : a;
            long expected = mask ? Math.max(minAB, maxAB) : minAB;
            Verify.checkEQ(lr[i], expected);
        }
    }

    // Predicated Long: max(min(a,b,m), max(b,a,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testLongMaskedMaxIdealFlippedInputs(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        VectorMask<Long> m = VectorMask.fromArray(L_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(lr, index);
    }

    @Run(test = "testLongMaskedMaxIdealFlippedInputs")
    public void runLongMaskedMaxIdealFlippedInputs() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMaxIdealFlippedInputs(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long a = la[i], b = lb[i];
            boolean mask = m1arr[i];
            long minAB = mask ? Math.min(a, b) : a;
            long maxBA = mask ? Math.max(b, a) : b;
            long expected = mask ? Math.max(minAB, maxBA) : minAB;
            Verify.checkEQ(lr[i], expected);
        }
    }

    // Predicated Long: min(min(a,b,m), max(b,a,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testLongMaskedMinIdealFlippedInputs(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        VectorMask<Long> m = VectorMask.fromArray(L_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(lr, index);
    }

    @Run(test = "testLongMaskedMinIdealFlippedInputs")
    public void runLongMaskedMinIdealFlippedInputs() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMinIdealFlippedInputs(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long a = la[i], b = lb[i];
            boolean mask = m1arr[i];
            long minAB = mask ? Math.min(a, b) : a;
            long maxBA = mask ? Math.max(b, a) : b;
            long expected = mask ? Math.min(minAB, maxBA) : minAB;
            Verify.checkEQ(lr[i], expected);
        }
    }

    // Predicated Long: min(min(a,b,m1), max(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testLongMaskedMinIdealDiffMaskMinMax(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(L_SPECIES, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(L_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask1)
          .intoArray(lr, index);
    }

    @Run(test = "testLongMaskedMinIdealDiffMaskMinMax")
    public void runLongMaskedMinIdealDiffMaskMinMax() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMinIdealDiffMaskMinMax(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long a = la[i], b = lb[i];
            long minAB = m1arr[i] ? Math.min(a, b) : a;
            long maxAB = m2arr[i] ? Math.max(a, b) : a;
            long expected = m1arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(lr[i], expected);
        }
    }

    // Predicated Long: min(min(a,b,m2), max(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testLongMaskedMinIdealDiffMaskMinMaxSwapped(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(L_SPECIES, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(L_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask1)
          .intoArray(lr, index);
    }

    @Run(test = "testLongMaskedMinIdealDiffMaskMinMaxSwapped")
    public void runLongMaskedMinIdealDiffMaskMinMaxSwapped() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMinIdealDiffMaskMinMaxSwapped(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long a = la[i], b = lb[i];
            long minAB = m2arr[i] ? Math.min(a, b) : a;
            long maxAB = m1arr[i] ? Math.max(a, b) : a;
            long expected = m1arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(lr[i], expected);
        }
    }

    // Predicated Long: min(min(a,b,m1), max(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testLongMaskedMinIdealDiffMaskOuter(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(L_SPECIES, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(L_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask2)
          .intoArray(lr, index);
    }

    @Run(test = "testLongMaskedMinIdealDiffMaskOuter")
    public void runLongMaskedMinIdealDiffMaskOuter() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMinIdealDiffMaskOuter(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long a = la[i], b = lb[i];
            long minAB = m1arr[i] ? Math.min(a, b) : a;
            long maxAB = m1arr[i] ? Math.max(a, b) : a;
            long expected = m2arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(lr[i], expected);
        }
    }

    // Predicated Long: min(min(a,b,m1), max(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VL, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VL, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx512f", "true", "sve", "true", "rvv", "true"})
    public void testLongMaskedMinIdealAllDiffMask(int index) {
        LongVector v1 = LongVector.fromArray(L_SPECIES, la, index);
        LongVector v2 = LongVector.fromArray(L_SPECIES, lb, index);
        VectorMask<Long> mask1 = VectorMask.fromArray(L_SPECIES, m1arr, index);
        VectorMask<Long> mask2 = VectorMask.fromArray(L_SPECIES, m2arr, index);
        VectorMask<Long> mask3 = VectorMask.fromArray(L_SPECIES, m3arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask3)
          .intoArray(lr, index);
    }

    @Run(test = "testLongMaskedMinIdealAllDiffMask")
    public void runLongMaskedMinIdealAllDiffMask() {
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i += L_SPECIES.length()) {
            testLongMaskedMinIdealAllDiffMask(i);
        }
        for (int i = 0; i < L_SPECIES.loopBound(LENGTH); i++) {
            long a = la[i], b = lb[i];
            long minAB = m1arr[i] ? Math.min(a, b) : a;
            long maxAB = m2arr[i] ? Math.max(a, b) : a;
            long expected = m3arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(lr[i], expected);
        }
    }

    // Predicated Float: min(min(a,b,m), max(a,b,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testFloatMaskedMinIdealSameMask(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        VectorMask<Float> m = VectorMask.fromArray(F_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMaskedMinIdealSameMask")
    public void runFloatMaskedMinIdealSameMask() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedMinIdealSameMask(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float a = fa[i], b = fb[i];
            boolean mask = m1arr[i];
            float minAB = mask ? Math.min(a, b) : a;
            float maxAB = mask ? Math.max(a, b) : a;
            float expected = mask ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(fr[i], expected);
        }
    }

    // Predicated Float: max(min(a,b,m), max(a,b,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testFloatMaskedMaxIdealSameMask(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        VectorMask<Float> m = VectorMask.fromArray(F_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMaskedMaxIdealSameMask")
    public void runFloatMaskedMaxIdealSameMask() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedMaxIdealSameMask(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float a = fa[i], b = fb[i];
            boolean mask = m1arr[i];
            float minAB = mask ? Math.min(a, b) : a;
            float maxAB = mask ? Math.max(a, b) : a;
            float expected = mask ? Math.max(minAB, maxAB) : minAB;
            Verify.checkEQ(fr[i], expected);
        }
    }

    // Predicated Float: max(min(a,b,m), max(b,a,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testFloatMaskedMaxIdealFlippedInputs(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        VectorMask<Float> m = VectorMask.fromArray(F_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMaskedMaxIdealFlippedInputs")
    public void runFloatMaskedMaxIdealFlippedInputs() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedMaxIdealFlippedInputs(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float a = fa[i], b = fb[i];
            boolean mask = m1arr[i];
            float minAB = mask ? Math.min(a, b) : a;
            float maxBA = mask ? Math.max(b, a) : b;
            float expected = mask ? Math.max(minAB, maxBA) : minAB;
            Verify.checkEQ(fr[i], expected);
        }
    }

    // Predicated Float: min(min(a,b,m), max(b,a,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testFloatMaskedMinIdealFlippedInputs(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        VectorMask<Float> m = VectorMask.fromArray(F_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMaskedMinIdealFlippedInputs")
    public void runFloatMaskedMinIdealFlippedInputs() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedMinIdealFlippedInputs(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float a = fa[i], b = fb[i];
            boolean mask = m1arr[i];
            float minAB = mask ? Math.min(a, b) : a;
            float maxBA = mask ? Math.max(b, a) : b;
            float expected = mask ? Math.min(minAB, maxBA) : minAB;
            Verify.checkEQ(fr[i], expected);
        }
    }

    // Predicated Float: min(min(a,b,m1), max(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testFloatMaskedMinIdealDiffMaskMinMax(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        VectorMask<Float> mask1 = VectorMask.fromArray(F_SPECIES, m1arr, index);
        VectorMask<Float> mask2 = VectorMask.fromArray(F_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask1)
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMaskedMinIdealDiffMaskMinMax")
    public void runFloatMaskedMinIdealDiffMaskMinMax() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedMinIdealDiffMaskMinMax(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float a = fa[i], b = fb[i];
            float minAB = m1arr[i] ? Math.min(a, b) : a;
            float maxAB = m2arr[i] ? Math.max(a, b) : a;
            float expected = m1arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(fr[i], expected);
        }
    }

    // Predicated Float: min(min(a,b,m2), max(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testFloatMaskedMinIdealDiffMaskMinMaxSwapped(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        VectorMask<Float> mask1 = VectorMask.fromArray(F_SPECIES, m1arr, index);
        VectorMask<Float> mask2 = VectorMask.fromArray(F_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask1)
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMaskedMinIdealDiffMaskMinMaxSwapped")
    public void runFloatMaskedMinIdealDiffMaskMinMaxSwapped() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedMinIdealDiffMaskMinMaxSwapped(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float a = fa[i], b = fb[i];
            float minAB = m2arr[i] ? Math.min(a, b) : a;
            float maxAB = m1arr[i] ? Math.max(a, b) : a;
            float expected = m1arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(fr[i], expected);
        }
    }

    // Predicated Float: min(min(a,b,m1), max(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testFloatMaskedMinIdealDiffMaskOuter(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        VectorMask<Float> mask1 = VectorMask.fromArray(F_SPECIES, m1arr, index);
        VectorMask<Float> mask2 = VectorMask.fromArray(F_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask2)
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMaskedMinIdealDiffMaskOuter")
    public void runFloatMaskedMinIdealDiffMaskOuter() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedMinIdealDiffMaskOuter(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float a = fa[i], b = fb[i];
            float minAB = m1arr[i] ? Math.min(a, b) : a;
            float maxAB = m1arr[i] ? Math.max(a, b) : a;
            float expected = m2arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(fr[i], expected);
        }
    }

    // Predicated Float: min(min(a,b,m1), max(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VF, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VF, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testFloatMaskedMinIdealAllDiffMask(int index) {
        FloatVector v1 = FloatVector.fromArray(F_SPECIES, fa, index);
        FloatVector v2 = FloatVector.fromArray(F_SPECIES, fb, index);
        VectorMask<Float> mask1 = VectorMask.fromArray(F_SPECIES, m1arr, index);
        VectorMask<Float> mask2 = VectorMask.fromArray(F_SPECIES, m2arr, index);
        VectorMask<Float> mask3 = VectorMask.fromArray(F_SPECIES, m3arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask3)
          .intoArray(fr, index);
    }

    @Run(test = "testFloatMaskedMinIdealAllDiffMask")
    public void runFloatMaskedMinIdealAllDiffMask() {
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i += F_SPECIES.length()) {
            testFloatMaskedMinIdealAllDiffMask(i);
        }
        for (int i = 0; i < F_SPECIES.loopBound(LENGTH); i++) {
            float a = fa[i], b = fb[i];
            float minAB = m1arr[i] ? Math.min(a, b) : a;
            float maxAB = m2arr[i] ? Math.max(a, b) : a;
            float expected = m3arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(fr[i], expected);
        }
    }

    // Predicated Double: min(min(a,b,m), max(a,b,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testDoubleMaskedMinIdealSameMask(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        VectorMask<Double> m = VectorMask.fromArray(D_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMaskedMinIdealSameMask")
    public void runDoubleMaskedMinIdealSameMask() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedMinIdealSameMask(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double a = da[i], b = db[i];
            boolean mask = m1arr[i];
            double minAB = mask ? Math.min(a, b) : a;
            double maxAB = mask ? Math.max(a, b) : a;
            double expected = mask ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(dr[i], expected);
        }
    }

    // Predicated Double: max(min(a,b,m), max(a,b,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testDoubleMaskedMaxIdealSameMask(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        VectorMask<Double> m = VectorMask.fromArray(D_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v1.lanewise(VectorOperators.MAX, v2, m), m)
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMaskedMaxIdealSameMask")
    public void runDoubleMaskedMaxIdealSameMask() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedMaxIdealSameMask(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double a = da[i], b = db[i];
            boolean mask = m1arr[i];
            double minAB = mask ? Math.min(a, b) : a;
            double maxAB = mask ? Math.max(a, b) : a;
            double expected = mask ? Math.max(minAB, maxAB) : minAB;
            Verify.checkEQ(dr[i], expected);
        }
    }

    // Predicated Double: max(min(a,b,m), max(b,a,m), m) => max(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 0 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testDoubleMaskedMaxIdealFlippedInputs(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        VectorMask<Double> m = VectorMask.fromArray(D_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MAX, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMaskedMaxIdealFlippedInputs")
    public void runDoubleMaskedMaxIdealFlippedInputs() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedMaxIdealFlippedInputs(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double a = da[i], b = db[i];
            boolean mask = m1arr[i];
            double minAB = mask ? Math.min(a, b) : a;
            double maxBA = mask ? Math.max(b, a) : b;
            double expected = mask ? Math.max(minAB, maxBA) : minAB;
            Verify.checkEQ(dr[i], expected);
        }
    }

    // Predicated Double: min(min(a,b,m), max(b,a,m), m) => min(a,b,m)
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 1 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 0 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testDoubleMaskedMinIdealFlippedInputs(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        VectorMask<Double> m = VectorMask.fromArray(D_SPECIES, m1arr, index);
        v1.lanewise(VectorOperators.MIN, v2, m)
          .lanewise(VectorOperators.MIN, v2.lanewise(VectorOperators.MAX, v1, m), m)
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMaskedMinIdealFlippedInputs")
    public void runDoubleMaskedMinIdealFlippedInputs() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedMinIdealFlippedInputs(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double a = da[i], b = db[i];
            boolean mask = m1arr[i];
            double minAB = mask ? Math.min(a, b) : a;
            double maxBA = mask ? Math.max(b, a) : b;
            double expected = mask ? Math.min(minAB, maxBA) : minAB;
            Verify.checkEQ(dr[i], expected);
        }
    }

    // Predicated Double: min(min(a,b,m1), max(a,b,m2), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testDoubleMaskedMinIdealDiffMaskMinMax(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        VectorMask<Double> mask1 = VectorMask.fromArray(D_SPECIES, m1arr, index);
        VectorMask<Double> mask2 = VectorMask.fromArray(D_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask1)
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMaskedMinIdealDiffMaskMinMax")
    public void runDoubleMaskedMinIdealDiffMaskMinMax() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedMinIdealDiffMaskMinMax(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double a = da[i], b = db[i];
            double minAB = m1arr[i] ? Math.min(a, b) : a;
            double maxAB = m2arr[i] ? Math.max(a, b) : a;
            double expected = m1arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(dr[i], expected);
        }
    }

    // Predicated Double: min(min(a,b,m2), max(a,b,m1), m1) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testDoubleMaskedMinIdealDiffMaskMinMaxSwapped(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        VectorMask<Double> mask1 = VectorMask.fromArray(D_SPECIES, m1arr, index);
        VectorMask<Double> mask2 = VectorMask.fromArray(D_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask2)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask1)
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMaskedMinIdealDiffMaskMinMaxSwapped")
    public void runDoubleMaskedMinIdealDiffMaskMinMaxSwapped() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedMinIdealDiffMaskMinMaxSwapped(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double a = da[i], b = db[i];
            double minAB = m2arr[i] ? Math.min(a, b) : a;
            double maxAB = m1arr[i] ? Math.max(a, b) : a;
            double expected = m1arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(dr[i], expected);
        }
    }

    // Predicated Double: min(min(a,b,m1), max(a,b,m1), m2) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testDoubleMaskedMinIdealDiffMaskOuter(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        VectorMask<Double> mask1 = VectorMask.fromArray(D_SPECIES, m1arr, index);
        VectorMask<Double> mask2 = VectorMask.fromArray(D_SPECIES, m2arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask1), mask2)
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMaskedMinIdealDiffMaskOuter")
    public void runDoubleMaskedMinIdealDiffMaskOuter() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedMinIdealDiffMaskOuter(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double a = da[i], b = db[i];
            double minAB = m1arr[i] ? Math.min(a, b) : a;
            double maxAB = m1arr[i] ? Math.max(a, b) : a;
            double expected = m2arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(dr[i], expected);
        }
    }

    // Predicated Double: min(min(a,b,m1), max(a,b,m2), m3) => NO transform
    @Test
    @IR(counts = { IRNode.MIN_VD, IRNode.VECTOR_SIZE_ANY, " 2 ",
                   IRNode.MAX_VD, IRNode.VECTOR_SIZE_ANY, " 1 " },
        applyIfCPUFeatureOr = {"avx10_2", "true", "sve", "true", "rvv", "true"})
    public void testDoubleMaskedMinIdealAllDiffMask(int index) {
        DoubleVector v1 = DoubleVector.fromArray(D_SPECIES, da, index);
        DoubleVector v2 = DoubleVector.fromArray(D_SPECIES, db, index);
        VectorMask<Double> mask1 = VectorMask.fromArray(D_SPECIES, m1arr, index);
        VectorMask<Double> mask2 = VectorMask.fromArray(D_SPECIES, m2arr, index);
        VectorMask<Double> mask3 = VectorMask.fromArray(D_SPECIES, m3arr, index);
        v1.lanewise(VectorOperators.MIN, v2, mask1)
          .lanewise(VectorOperators.MIN, v1.lanewise(VectorOperators.MAX, v2, mask2), mask3)
          .intoArray(dr, index);
    }

    @Run(test = "testDoubleMaskedMinIdealAllDiffMask")
    public void runDoubleMaskedMinIdealAllDiffMask() {
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i += D_SPECIES.length()) {
            testDoubleMaskedMinIdealAllDiffMask(i);
        }
        for (int i = 0; i < D_SPECIES.loopBound(LENGTH); i++) {
            double a = da[i], b = db[i];
            double minAB = m1arr[i] ? Math.min(a, b) : a;
            double maxAB = m2arr[i] ? Math.max(a, b) : a;
            double expected = m3arr[i] ? Math.min(minAB, maxAB) : minAB;
            Verify.checkEQ(dr[i], expected);
        }
    }
}
