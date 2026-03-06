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
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import java.util.Random;
import jdk.incubator.vector.*;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8372797
 * @key randomness
 * @library /test/lib /
 * @summary IR verification for MinV/MaxV Identity and Ideal transforms
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorMinMaxTransforms
 */
public class VectorMinMaxTransforms {
    private static final int LENGTH = 256;
    private static final Random RD = Utils.getRandomInstance();

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

        for (int i = 0; i < LENGTH; i++) {
            ia[i] = RD.nextInt();
            ib[i] = RD.nextInt();
            la[i] = RD.nextLong();
            lb[i] = RD.nextLong();
            fa[i] = RD.nextFloat();
            fb[i] = RD.nextFloat();
            da[i] = RD.nextDouble();
            db[i] = RD.nextDouble();
            ba[i] = (byte) RD.nextInt();
            bb[i] = (byte) RD.nextInt();
            sa[i] = (short) RD.nextInt();
            sb[i] = (short) RD.nextInt();
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
    @Warmup(10000)
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
    @Warmup(10000)
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
            byte expected = (byte) Math.min(Math.min(ba[i] & 0xFF, bb[i] & 0xFF), Math.max(ba[i] & 0xFF, bb[i] & 0xFF));
            if (ba[i] >= 0 && bb[i] >= 0) {
                expected = (byte) Math.min(ba[i], bb[i]);
            } else {
                expected = (byte) Math.min(Math.min(ba[i], bb[i]), Math.max(ba[i], bb[i]));
            }
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
}
