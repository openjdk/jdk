/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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

import java.util.Random;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8282431
 * @key randomness
 * @library /test/lib /
 * @requires vm.cpu.features ~= ".*sve.*" | vm.cpu.features ~= ".*rvv.*"
 * @summary Add optimized rules for masked vector multiply-add/sub for SVE and RVV
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorFusedMultiplyAddSubTest
 */

public class VectorFusedMultiplyAddSubTest {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;

    private static int LENGTH = 128;
    private static final Random RD = Utils.getRandomInstance();

    private static byte[] ba;
    private static byte[] bb;
    private static byte[] bc;
    private static byte[] br;
    private static short[] sa;
    private static short[] sb;
    private static short[] sc;
    private static short[] sr;
    private static int[] ia;
    private static int[] ib;
    private static int[] ic;
    private static int[] ir;
    private static long[] la;
    private static long[] lb;
    private static long[] lc;
    private static long[] lr;
    private static float[] fa;
    private static float[] fb;
    private static float[] fc;
    private static float[] fr;
    private static double[] da;
    private static double[] db;
    private static double[] dc;
    private static double[] dr;
    private static boolean[] m;

    static {
        ba = new byte[LENGTH];
        bb = new byte[LENGTH];
        bc = new byte[LENGTH];
        br = new byte[LENGTH];
        sa = new short[LENGTH];
        sb = new short[LENGTH];
        sc = new short[LENGTH];
        sr = new short[LENGTH];
        ia = new int[LENGTH];
        ib = new int[LENGTH];
        ic = new int[LENGTH];
        ir = new int[LENGTH];
        la = new long[LENGTH];
        lb = new long[LENGTH];
        lc = new long[LENGTH];
        lr = new long[LENGTH];
        fa = new float[LENGTH];
        fb = new float[LENGTH];
        fc = new float[LENGTH];
        fr = new float[LENGTH];
        da = new double[LENGTH];
        db = new double[LENGTH];
        dc = new double[LENGTH];
        dr = new double[LENGTH];
        m = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            ba[i] = (byte) RD.nextInt(25);
            bb[i] = (byte) RD.nextInt(25);
            bc[i] = (byte) RD.nextInt(25);
            sa[i] = (short) RD.nextInt(25);
            sb[i] = (short) RD.nextInt(25);
            sc[i] = (short) RD.nextInt(25);
            ia[i] = RD.nextInt(25);
            ib[i] = RD.nextInt(25);
            ic[i] = RD.nextInt(25);
            la[i] = RD.nextLong(25);
            lb[i] = RD.nextLong(25);
            lc[i] = RD.nextLong(25);
            fa[i] = RD.nextFloat((float) 25.0);
            fb[i] = RD.nextFloat((float) 25.0);
            fc[i] = RD.nextFloat((float) 25.0);
            da[i] = RD.nextDouble(25.0);
            db[i] = RD.nextDouble(25.0);
            dc[i] = RD.nextDouble(25.0);
            m[i] = RD.nextBoolean();
        }
    }

    interface BTenOp {
        byte apply(byte a, byte b, byte c);
    }

    interface STenOp {
        short apply(short a, short b, short c);
    }

    interface ITenOp {
        int apply(int a, int b, int c);
    }

    interface LTenOp {
        long apply(long a, long b, long c);
    }

    interface FTenOp {
        float apply(float a, float b, float c);
    }

    interface DTenOp {
        double apply(double a, double b, double c);
    }

    private static void assertArrayEquals(byte[] r, byte[] a, byte[] b, byte[] c, boolean[] m, BTenOp f) {
       for (int i = 0; i < LENGTH; i++) {
           if (m[i % B_SPECIES.length()]) {
               Asserts.assertEquals(f.apply(a[i], b[i], c[i]), r[i]);
           } else {
               Asserts.assertEquals(a[i], r[i]);
           }
       }
    }

    private static void assertArrayEquals(short[] r, short[] a, short[] b, short[] c, boolean[] m, STenOp f) {
       for (int i = 0; i < LENGTH; i++) {
           if (m[i % S_SPECIES.length()]) {
               Asserts.assertEquals(f.apply(a[i], b[i], c[i]), r[i]);
           } else {
               Asserts.assertEquals(a[i], r[i]);
           }
       }
    }

    private static void assertArrayEquals(int[] r, int[] a, int[] b, int[] c, boolean[] m, ITenOp f) {
       for (int i = 0; i < LENGTH; i++) {
           if (m[i % I_SPECIES.length()]) {
               Asserts.assertEquals(f.apply(a[i], b[i], c[i]), r[i]);
           } else {
               Asserts.assertEquals(a[i], r[i]);
           }
       }
    }

    private static void assertArrayEquals(long[] r, long[] a, long[] b, long[] c, boolean[] m, LTenOp f) {
       for (int i = 0; i < LENGTH; i++) {
           if (m[i % L_SPECIES.length()]) {
               Asserts.assertEquals(f.apply(a[i], b[i], c[i]), r[i]);
           } else {
               Asserts.assertEquals(a[i], r[i]);
           }
       }
    }

    private static void assertArrayEquals(float[] r, float[] a, float[] b, float[] c, boolean[] m, FTenOp f) {
       for (int i = 0; i < LENGTH; i++) {
           if (m[i % F_SPECIES.length()]) {
               Asserts.assertEquals(f.apply(a[i], b[i], c[i]), r[i]);
           } else {
               Asserts.assertEquals(a[i], r[i]);
           }
       }
    }

    private static void assertArrayEquals(double[] r, double[] a, double[] b, double[] c, boolean[] m, DTenOp f) {
       for (int i = 0; i < LENGTH; i++) {
           if (m[i % D_SPECIES.length()]) {
               Asserts.assertEquals(f.apply(a[i], b[i], c[i]), r[i]);
           } else {
               Asserts.assertEquals(a[i], r[i]);
           }
       }
    }

    private static void assertArrayEqualsNeg(float[] r, float[] a, float[] b, float[] c, boolean[] m, FTenOp f) {
       for (int i = 0; i < LENGTH; i++) {
           if (m[i % F_SPECIES.length()]) {
               Asserts.assertEquals(f.apply(a[i], b[i], c[i]), r[i]);
           } else {
               Asserts.assertEquals(-a[i], r[i]);
           }
       }
    }

    private static void assertArrayEqualsNeg(double[] r, double[] a, double[] b, double[] c, boolean[] m, DTenOp f) {
       for (int i = 0; i < LENGTH; i++) {
           if (m[i % D_SPECIES.length()]) {
               Asserts.assertEquals(f.apply(a[i], b[i], c[i]), r[i]);
           } else {
               Asserts.assertEquals(-a[i], r[i]);
           }
       }
    }

    @Test
    @IR(applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"}, counts = { IRNode.VMLA_MASKED, ">= 1" })
    public static void testByteMultiplyAddMasked() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, ba, i);
            ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, i);
            ByteVector cv = ByteVector.fromArray(B_SPECIES, bc, i);
            av.add(bv.mul(cv), mask).intoArray(br, i);
        }
        assertArrayEquals(br, ba, bb, bc, m, (a, b, c) -> (byte) (a + b * c));
    }

    @Test
    @IR(applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"}, counts = { IRNode.VMLS_MASKED, ">= 1" })
    public static void testByteMultiplySubMasked() {
        VectorMask<Byte> mask = VectorMask.fromArray(B_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, ba, i);
            ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, i);
            ByteVector cv = ByteVector.fromArray(B_SPECIES, bc, i);
            av.sub(bv.mul(cv), mask).intoArray(br, i);
        }
        assertArrayEquals(br, ba, bb, bc, m, (a, b, c) -> (byte) (a - b * c));
    }

    @Test
    @IR(applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"}, counts = { IRNode.VMLA_MASKED, ">= 1" })
    public static void testShortMultiplyAddMasked() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector av = ShortVector.fromArray(S_SPECIES, sa, i);
            ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, i);
            ShortVector cv = ShortVector.fromArray(S_SPECIES, sc, i);
            av.add(bv.mul(cv), mask).intoArray(sr, i);
        }
        assertArrayEquals(sr, sa, sb, sc, m, (a, b, c) -> (short) (a + b * c));
    }

    @Test
    @IR(applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"}, counts = { IRNode.VMLS_MASKED, ">= 1" })
    public static void testShortMultiplySubMasked() {
        VectorMask<Short> mask = VectorMask.fromArray(S_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            ShortVector av = ShortVector.fromArray(S_SPECIES, sa, i);
            ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, i);
            ShortVector cv = ShortVector.fromArray(S_SPECIES, sc, i);
            av.sub(bv.mul(cv), mask).intoArray(sr, i);
        }
        assertArrayEquals(sr, sa, sb, sc, m, (a, b, c) -> (short) (a - b * c));
    }

    @Test
    @IR(applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"}, counts = { IRNode.VMLA_MASKED, ">= 1" })
    public static void testIntMultiplyAddMasked() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            IntVector cv = IntVector.fromArray(I_SPECIES, ic, i);
            av.add(bv.mul(cv), mask).intoArray(ir, i);
        }
        assertArrayEquals(ir, ia, ib, ic, m, (a, b, c) -> (int) (a + b * c));
    }

    @Test
    @IR(applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"}, counts = { IRNode.VMLS_MASKED, ">= 1" })
    public static void testIntMultiplySubMasked() {
        VectorMask<Integer> mask = VectorMask.fromArray(I_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector av = IntVector.fromArray(I_SPECIES, ia, i);
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, i);
            IntVector cv = IntVector.fromArray(I_SPECIES, ic, i);
            av.sub(bv.mul(cv), mask).intoArray(ir, i);
        }
        assertArrayEquals(ir, ia, ib, ic, m, (a, b, c) -> (int) (a - b * c));
    }

    @Test
    @IR(applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"}, counts = { IRNode.VMLA_MASKED, ">= 1" })
    public static void testLongMultiplyAddMasked() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            LongVector cv = LongVector.fromArray(L_SPECIES, lc, i);
            av.add(bv.mul(cv), mask).intoArray(lr, i);
        }
        assertArrayEquals(lr, la, lb, lc, m, (a, b, c) -> (long) (a + b * c));
    }

    @Test
    @IR(applyIfPlatformOr = {"aarch64", "true", "riscv64", "true"}, counts = { IRNode.VMLS_MASKED, ">= 1" })
    public static void testLongMultiplySubMasked() {
        VectorMask<Long> mask = VectorMask.fromArray(L_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += L_SPECIES.length()) {
            LongVector av = LongVector.fromArray(L_SPECIES, la, i);
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, i);
            LongVector cv = LongVector.fromArray(L_SPECIES, lc, i);
            av.sub(bv.mul(cv), mask).intoArray(lr, i);
        }
        assertArrayEquals(lr, la, lb, lc, m, (a, b, c) -> (long) (a - b * c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFMSB_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFNMSUB_MASKED, ">= 1" })
    public static void testFloatMultiplySubMasked() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += F_SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(F_SPECIES, fa, i);
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, i);
            av.lanewise(VectorOperators.FMA, bv.neg(), cv, mask).intoArray(fr, i);
        }
        assertArrayEquals(fr, fa, fb, fc, m, (a, b, c) -> (float) Math.fma(a, -b, c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFMAD_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFMADD_MASKED, ">= 1" })
    public static void testFloatMultiplyNegAMasked() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += F_SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(F_SPECIES, fa, i);
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, i);
            av.neg().lanewise(VectorOperators.FMA, bv, cv, mask).intoArray(fr, i);
        }
        assertArrayEqualsNeg(fr, fa, fb, fc, m, (a, b, c) -> (float) Math.fma(-a, b, c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFNMAD_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFNMADD_MASKED, ">= 1" })
    public static void testFloatNegatedMultiplyAddMasked() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += F_SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(F_SPECIES, fa, i);
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, i);
            av.lanewise(VectorOperators.FMA, bv.neg(), cv.neg(), mask).intoArray(fr, i);
        }
        assertArrayEquals(fr, fa, fb, fc, m, (a, b, c) -> (float) Math.fma(a, -b, -c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFNMSB_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFMSUB_MASKED, ">= 1" })
    public static void testFloatNegatedMultiplyNegAMasked() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += F_SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(F_SPECIES, fa, i);
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, i);
            av.neg().lanewise(VectorOperators.FMA, bv, cv.neg(), mask).intoArray(fr, i);
        }
        assertArrayEqualsNeg(fr, fa, fb, fc, m, (a, b, c) -> (float) Math.fma(-a, b, -c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFNMSB_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFMSUB_MASKED, ">= 1" })
    public static void testFloatNegatedMultiplySubMasked() {
        VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += F_SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(F_SPECIES, fa, i);
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            FloatVector cv = FloatVector.fromArray(F_SPECIES, fc, i);
            av.lanewise(VectorOperators.FMA, bv, cv.neg(), mask).intoArray(fr, i);
        }
        assertArrayEquals(fr, fa, fb, fc, m, (a, b, c) -> (float) Math.fma(a, b, -c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFMSB_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFNMSUB_MASKED, ">= 1" })
    public static void testDoubleMultiplySubMasked() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += D_SPECIES.length()) {
            DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, i);
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, i);
            av.lanewise(VectorOperators.FMA, bv.neg(), cv, mask).intoArray(dr, i);
        }
        assertArrayEquals(dr, da, db, dc, m, (a, b, c) -> (double) Math.fma(a, -b, c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFMAD_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFMADD_MASKED, ">= 1" })
    public static void testDoubleMultiplyNegAMasked() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += D_SPECIES.length()) {
            DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, i);
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, i);
            av.neg().lanewise(VectorOperators.FMA, bv, cv, mask).intoArray(dr, i);
        }
        assertArrayEqualsNeg(dr, da, db, dc, m, (a, b, c) -> (double) Math.fma(-a, b, c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFNMAD_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFNMADD_MASKED, ">= 1" })
    public static void testDoubleNegatedMultiplyAddMasked() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += D_SPECIES.length()) {
            DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, i);
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, i);
            av.lanewise(VectorOperators.FMA, bv.neg(), cv.neg(), mask).intoArray(dr, i);
        }
        assertArrayEquals(dr, da, db, dc, m, (a, b, c) -> (double) Math.fma(a, -b, -c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFNMSB_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFMSUB_MASKED, ">= 1" })
    public static void testDoubleNegatedMultiplyNegAMasked() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += D_SPECIES.length()) {
            DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, i);
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, i);
            av.neg().lanewise(VectorOperators.FMA, bv, cv.neg(), mask).intoArray(dr, i);
        }
        assertArrayEqualsNeg(dr, da, db, dc, m, (a, b, c) -> (double) Math.fma(-a, b, -c));
    }

    @Test
    @IR(applyIfPlatform = {"aarch64", "true"}, counts = { IRNode.VFNMSB_MASKED, ">= 1" })
    @IR(applyIfPlatform = {"riscv64", "true"}, counts = { IRNode.RISCV_VFMSUB_MASKED, ">= 1" })
    public static void testDoubleNegatedMultiplySubMasked() {
        VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, 0);
        for (int i = 0; i < LENGTH; i += D_SPECIES.length()) {
            DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, i);
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            DoubleVector cv = DoubleVector.fromArray(D_SPECIES, dc, i);
            av.lanewise(VectorOperators.FMA, bv, cv.neg(), mask).intoArray(dr, i);
        }
        assertArrayEquals(dr, da, db, dc, m, (a, b, c) -> (double) Math.fma(a, b, -c));
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(5000).addFlags("--add-modules=jdk.incubator.vector");
        if (Platform.isAArch64()) {
            testFramework.addFlags("-XX:UseSVE=1");
        }
        testFramework.start();
    }
}
