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
 * @test
 * @bug 8358521
 * @summary Optimize vector operations by reassociating broadcasted inputs
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestVectorBroadcastTransforms
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.*;

import jdk.test.lib.Utils;
import java.util.Random;

public class TestVectorBroadcastTransforms {

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    private static final Random R = Utils.getRandomInstance();

    /* =======================
     * INT
     * ======================= */

    static final VectorSpecies<Integer> ISP = IntVector.SPECIES_PREFERRED;

    @Test
    @IR(failOn = IRNode.ADD_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_add(int ia, int ib) {
        return IntVector.broadcast(ISP, ia)
                .add(IntVector.broadcast(ISP, ib))
                .lane(0);
    }

    @Run(test = "int_add")
    static void run_int_add() {
        int ia = R.nextInt();
        int ib = R.nextInt();
        int ir = int_add(ia, ib);
        Verify.checkEQ(ir, ia + ib);
    }

    @Test
    @IR(failOn = IRNode.SUB_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_sub(int ia, int ib) {
        return IntVector.broadcast(ISP, ia)
                .sub(IntVector.broadcast(ISP, ib))
                .lane(0);
    }

    @Run(test = "int_sub")
    static void run_int_sub() {
        int ia = R.nextInt();
        int ib = R.nextInt();
        int ir = int_sub(ia, ib);
        Verify.checkEQ(ir, ia - ib);
    }

    @Test
    @IR(failOn = IRNode.MUL_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_mul(int ia, int ib) {
        return IntVector.broadcast(ISP, ia)
                .mul(IntVector.broadcast(ISP, ib))
                .lane(0);
    }

    @Run(test = "int_mul")
    static void run_int_mul() {
        int ia = R.nextInt();
        int ib = R.nextInt();
        int ir = int_mul(ia, ib);
        Verify.checkEQ(ir, ia * ib);
    }

    @Test
    @IR(failOn = IRNode.AND_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.AND_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_and(int ia, int ib) {
        return IntVector.broadcast(ISP, ia)
                .and(IntVector.broadcast(ISP, ib))
                .lane(0);
    }

    @Run(test = "int_and")
    static void run_int_and() {
        int ia = R.nextInt();
        int ib = R.nextInt();
        int ir = int_and(ia, ib);
        Verify.checkEQ(ir, ia & ib);
    }

    @Test
    @IR(failOn = IRNode.OR_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.OR_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_or(int ia, int ib) {
        return IntVector.broadcast(ISP, ia)
                .or(IntVector.broadcast(ISP, ib))
                .lane(0);
    }

    @Run(test = "int_or")
    static void run_int_or() {
        int ia = R.nextInt();
        int ib = R.nextInt();
        int ir = int_or(ia, ib);
        Verify.checkEQ(ir, ia | ib);
    }

    @Test
    @IR(failOn = IRNode.XOR_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.XOR_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_xor(int ia, int ib) {
        return IntVector.broadcast(ISP, ia)
                .lanewise(VectorOperators.XOR, IntVector.broadcast(ISP, ib))
                .lane(0);
    }

    @Run(test = "int_xor")
    static void run_int_xor() {
        int ia = R.nextInt();
        int ib = R.nextInt();
        int ir = int_xor(ia, ib);
        Verify.checkEQ(ir, ia ^ ib);
    }

    @Test
    @IR(failOn = IRNode.MIN_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MIN_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_min(int ia, int ib) {
        return IntVector.broadcast(ISP, ia)
                .min(IntVector.broadcast(ISP, ib))
                .lane(0);
    }

    @Run(test = "int_min")
    static void run_int_min() {
        int ia = R.nextInt();
        int ib = R.nextInt();
        int ir = int_min(ia, ib);
        Verify.checkEQ(ir, Math.min(ia, ib));
    }

    @Test
    @IR(failOn = IRNode.MAX_VI,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MAX_I, ">= 1", IRNode.REPLICATE_I, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static int int_max(int ia, int ib) {
        return IntVector.broadcast(ISP, ia)
                .max(IntVector.broadcast(ISP, ib))
                .lane(0);
    }

    @Run(test = "int_max")
    static void run_int_max() {
        int ia = R.nextInt();
        int ib = R.nextInt();
        int ir = int_max(ia, ib);
        Verify.checkEQ(ir, Math.max(ia, ib));
    }

    /* =======================
     * LONG
     * ======================= */

    static final VectorSpecies<Long> LSP = LongVector.SPECIES_PREFERRED;

    @Test
    @IR(failOn = IRNode.ADD_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_L, ">= 1", IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_add(long la, long lb) {
        return LongVector.broadcast(LSP, la)
                .add(LongVector.broadcast(LSP, lb))
                .lane(0);
    }

    @Run(test = "long_add")
    static void run_long_add() {
        long la = R.nextLong();
        long lb = R.nextLong();
        long lr = long_add(la, lb);
        Verify.checkEQ(lr, la + lb);
    }

    @Test
    @IR(failOn = IRNode.SUB_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_L, ">= 1", IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_sub(long la, long lb) {
        return LongVector.broadcast(LSP, la)
                .sub(LongVector.broadcast(LSP, lb))
                .lane(0);
    }

    @Run(test = "long_sub")
    static void run_long_sub() {
        long la = R.nextLong();
        long lb = R.nextLong();
        long lr = long_sub(la, lb);
        Verify.checkEQ(lr, la - lb);
    }

    @Test
    @IR(failOn = IRNode.MUL_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_L, ">= 1", IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_mul(long la, long lb) {
        return LongVector.broadcast(LSP, la)
                .mul(LongVector.broadcast(LSP, lb))
                .lane(0);
    }

    @Run(test = "long_mul")
    static void run_long_mul() {
        long la = R.nextLong();
        long lb = R.nextLong();
        long lr = long_mul(la, lb);
        Verify.checkEQ(lr, la * lb);
    }

    @Test
    @IR(failOn = IRNode.AND_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.AND_L, ">= 1", IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_and(long la, long lb) {
        return LongVector.broadcast(LSP, la)
                .and(LongVector.broadcast(LSP, lb))
                .lane(0);
    }

    @Run(test = "long_and")
    static void run_long_and() {
        long la = R.nextLong();
        long lb = R.nextLong();
        long lr = long_and(la, lb);
        Verify.checkEQ(lr, la & lb);
    }

    @Test
    @IR(failOn = IRNode.OR_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.OR_L, ">= 1", IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_or(long la, long lb) {
        return LongVector.broadcast(LSP, la)
                .or(LongVector.broadcast(LSP, lb))
                .lane(0);
    }

    @Run(test = "long_or")
    static void run_long_or() {
        long la = R.nextLong();
        long lb = R.nextLong();
        long lr = long_or(la, lb);
        Verify.checkEQ(lr, la | lb);
    }

    @Test
    @IR(failOn = IRNode.XOR_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.XOR_L, ">= 1", IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_xor(long la, long lb) {
        return LongVector.broadcast(LSP, la)
                .lanewise(VectorOperators.XOR, LongVector.broadcast(LSP, lb))
                .lane(0);
    }

    @Run(test = "long_xor")
    static void run_long_xor() {
        long la = R.nextLong();
        long lb = R.nextLong();
        long lr = long_xor(la, lb);
        Verify.checkEQ(lr, la ^ lb);
    }

    @Test
    @IR(failOn = IRNode.MIN_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = {IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_min(long la, long lb) {
        return LongVector.broadcast(LSP, la)
                .min(LongVector.broadcast(LSP, lb))
                .lane(0);
    }

    @Run(test = "long_min")
    static void run_long_min() {
        long la = R.nextLong();
        long lb = R.nextLong();
        long lr = long_min(la, lb);
        Verify.checkEQ(lr, Math.min(la, lb));
    }

    @Test
    @IR(failOn = IRNode.MAX_VL,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = {IRNode.REPLICATE_L, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static long long_max(long la, long lb) {
        return LongVector.broadcast(LSP, la)
                .max(LongVector.broadcast(LSP, lb))
                .lane(0);
    }

    @Run(test = "long_max")
    static void run_long_max() {
        long la = R.nextLong();
        long lb = R.nextLong();
        long lr = long_max(la, lb);
        Verify.checkEQ(lr, Math.max(la, lb));
    }

    /* =======================
     * FLOAT
     * ======================= */

    static final VectorSpecies<Float> FSP = FloatVector.SPECIES_PREFERRED;

    @Test
    @IR(failOn = IRNode.ADD_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_add(float fa, float fb) {
        return FloatVector.broadcast(FSP, fa)
                .add(FloatVector.broadcast(FSP, fb))
                .lane(0);
    }

    @Run(test = "float_add")
    static void run_float_add() {
        float fa = R.nextFloat();
        float fb = R.nextFloat();
        float fr = float_add(fa, fb);
        Verify.checkEQ(fr, fa + fb);
    }

    @Test
    @IR(failOn = IRNode.SUB_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_sub(float fa, float fb) {
        return FloatVector.broadcast(FSP, fa)
                .sub(FloatVector.broadcast(FSP, fb))
                .lane(0);
    }

    @Run(test = "float_sub")
    static void run_float_sub() {
        float fa = R.nextFloat();
        float fb = R.nextFloat();
        float fr = float_sub(fa, fb);
        Verify.checkEQ(fr, fa - fb);
    }

    @Test
    @IR(failOn = IRNode.MUL_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_mul(float fa, float fb) {
        return FloatVector.broadcast(FSP, fa)
                .mul(FloatVector.broadcast(FSP, fb))
                .lane(0);
    }

    @Run(test = "float_mul")
    static void run_float_mul() {
        float fa = R.nextFloat();
        float fb = R.nextFloat();
        float fr = float_mul(fa, fb);
        Verify.checkEQ(fr, fa * fb);
    }

    @Test
    @IR(failOn = IRNode.DIV_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.DIV_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_div(float fa, float fb) {
        return FloatVector.broadcast(FSP, fa)
                .div(FloatVector.broadcast(FSP, fb))
                .lane(0);
    }

    @Run(test = "float_div")
    static void run_float_div() {
        float fa = R.nextFloat();
        float fb = R.nextFloat();
        if (fb == 0f) fb = 1f;
        float fr = float_div(fa, fb);
        Verify.checkEQ(fr, fa / fb);
    }

    @Test
    @IR(failOn = IRNode.MIN_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MIN_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_min(float fa, float fb) {
        return FloatVector.broadcast(FSP, fa)
                .min(FloatVector.broadcast(FSP, fb))
                .lane(0);
    }

    @Run(test = "float_min")
    static void run_float_min() {
        float fa = R.nextFloat();
        float fb = R.nextFloat();
        float fr = float_min(fa, fb);
        Verify.checkEQ(fr, Math.min(fa, fb));
    }

    @Test
    @IR(failOn = IRNode.MAX_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MAX_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_max(float fa, float fb) {
        return FloatVector.broadcast(FSP, fa)
                .max(FloatVector.broadcast(FSP, fb))
                .lane(0);
    }

    @Run(test = "float_max")
    static void run_float_max() {
        float fa = R.nextFloat();
        float fb = R.nextFloat();
        float fr = float_max(fa, fb);
        Verify.checkEQ(fr, Math.max(fa, fb));
    }

    @Test
    @IR(failOn = IRNode.SQRT_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SQRT_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_sqrt(float fa) {
        return FloatVector.broadcast(FSP, fa)
                .sqrt()
                .lane(0);
    }

    @Run(test = "float_sqrt")
    static void run_float_sqrt() {
        float fa = Math.abs(R.nextFloat()) + Float.MIN_VALUE;
        float fr = float_sqrt(fa);
        Verify.checkEQ(fr, (float) Math.sqrt(fa));
    }

    @Test
    @IR(failOn = IRNode.FMA_VF,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.FMA_F, ">= 1", IRNode.REPLICATE_F, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static float float_fma(float fa, float fb, float fc) {
        return FloatVector.broadcast(FSP, fa)
                .fma(FloatVector.broadcast(FSP, fb),
                     FloatVector.broadcast(FSP, fc))
                .lane(0);
    }

    @Run(test = "float_fma")
    static void run_float_fma() {
        float fa = R.nextFloat();
        float fb = R.nextFloat();
        float fc = R.nextFloat();
        float fr = float_fma(fa, fb, fc);
        Verify.checkEQ(fr, Math.fma(fa, fb, fc));
    }

    /* =======================
     * DOUBLE
     * ======================= */

    static final VectorSpecies<Double> DSP = DoubleVector.SPECIES_PREFERRED;

    @Test
    @IR(failOn = IRNode.ADD_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_add(double da, double db) {
        return DoubleVector.broadcast(DSP, da)
                .add(DoubleVector.broadcast(DSP, db))
                .lane(0);
    }

    @Run(test = "double_add")
    static void run_double_add() {
        double da = R.nextDouble();
        double db = R.nextDouble();
        double dr = double_add(da, db);
        Verify.checkEQ(dr, da + db);
    }

    @Test
    @IR(failOn = IRNode.SUB_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_sub(double da, double db) {
        return DoubleVector.broadcast(DSP, da)
                .sub(DoubleVector.broadcast(DSP, db))
                .lane(0);
    }

    @Run(test = "double_sub")
    static void run_double_sub() {
        double da = R.nextDouble();
        double db = R.nextDouble();
        double dr = double_sub(da, db);
        Verify.checkEQ(dr, da - db);
    }

    @Test
    @IR(failOn = IRNode.MUL_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_mul(double da, double db) {
        return DoubleVector.broadcast(DSP, da)
                .mul(DoubleVector.broadcast(DSP, db))
                .lane(0);
    }

    @Run(test = "double_mul")
    static void run_double_mul() {
        double da = R.nextDouble();
        double db = R.nextDouble();
        double dr = double_mul(da, db);
        Verify.checkEQ(dr, da * db);
    }

    @Test
    @IR(failOn = IRNode.DIV_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.DIV_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_div(double da, double db) {
        return DoubleVector.broadcast(DSP, da)
                .div(DoubleVector.broadcast(DSP, db))
                .lane(0);
    }

    @Run(test = "double_div")
    static void run_double_div() {
        double da = R.nextDouble();
        double db = R.nextDouble();
        if (db == 0d) db = 1d;
        double dr = double_div(da, db);
        Verify.checkEQ(dr, da / db);
    }

    @Test
    @IR(failOn = IRNode.MIN_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MIN_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_min(double da, double db) {
        return DoubleVector.broadcast(DSP, da)
                .min(DoubleVector.broadcast(DSP, db))
                .lane(0);
    }

    @Run(test = "double_min")
    static void run_double_min() {
        double da = R.nextDouble();
        double db = R.nextDouble();
        double dr = double_min(da, db);
        Verify.checkEQ(dr, Math.min(da, db));
    }

    @Test
    @IR(failOn = IRNode.MAX_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MAX_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_max(double da, double db) {
        return DoubleVector.broadcast(DSP, da)
                .max(DoubleVector.broadcast(DSP, db))
                .lane(0);
    }

    @Run(test = "double_max")
    static void run_double_max() {
        double da = R.nextDouble();
        double db = R.nextDouble();
        double dr = double_max(da, db);
        Verify.checkEQ(dr, Math.max(da, db));
    }

    @Test
    @IR(failOn = IRNode.SQRT_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SQRT_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_sqrt(double da) {
        return DoubleVector.broadcast(DSP, da)
                .sqrt()
                .lane(0);
    }

    @Run(test = "double_sqrt")
    static void run_double_sqrt() {
        double da = Math.abs(R.nextDouble()) + Double.MIN_VALUE;
        double dr = double_sqrt(da);
        Verify.checkEQ(dr, Math.sqrt(da));
    }

    @Test
    @IR(failOn = IRNode.FMA_VD,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.FMA_D, ">= 1", IRNode.REPLICATE_D, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static double double_fma(double da, double db, double dc) {
        return DoubleVector.broadcast(DSP, da)
                .fma(DoubleVector.broadcast(DSP, db),
                     DoubleVector.broadcast(DSP, dc))
                .lane(0);
    }

    @Run(test = "double_fma")
    static void run_double_fma() {
        double da = R.nextDouble();
        double db = R.nextDouble();
        double dc = R.nextDouble();
        double dr = double_fma(da, db, dc);
        Verify.checkEQ(dr, Math.fma(da, db, dc));
    }

    /* =======================
     * BYTE
     * ======================= */

    static final VectorSpecies<Byte> BSP = ByteVector.SPECIES_PREFERRED;
    static byte B_MAX = Byte.MAX_VALUE, B_MIN = Byte.MIN_VALUE;
    static byte B_ONE = (byte) 1, B_NEG_ONE = (byte) -1;

    @Test
    @IR(failOn = IRNode.ADD_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_add(byte ba, byte bb) {
        return ByteVector.broadcast(BSP, ba)
                .add(ByteVector.broadcast(BSP, bb))
                .lane(0);
    }

    @Run(test = "byte_add")
    static void run_byte_add() {
        byte ba = (byte) R.nextInt();
        byte bb = (byte) R.nextInt();
        byte br = byte_add(ba, bb);
        Verify.checkEQ(br, (byte) (ba + bb));
    }

    @Test
    @IR(failOn = IRNode.SUB_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_sub(byte ba, byte bb) {
        return ByteVector.broadcast(BSP, ba)
                .sub(ByteVector.broadcast(BSP, bb))
                .lane(0);
    }

    @Run(test = "byte_sub")
    static void run_byte_sub() {
        byte ba = (byte) R.nextInt();
        byte bb = (byte) R.nextInt();
        byte br = byte_sub(ba, bb);
        Verify.checkEQ(br, (byte) (ba - bb));
    }

    @Test
    @IR(failOn = IRNode.ADD_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_add_overflow() {
        return ByteVector.broadcast(BSP, B_MAX)
                .add(ByteVector.broadcast(BSP, B_ONE))
                .lane(0);
    }

    @Run(test = "byte_add_overflow")
    static void run_byte_add_overflow() {
        byte br = byte_add_overflow();
        Verify.checkEQ(br, (byte) (B_MAX + B_ONE));
    }

    @Test
    @IR(failOn = IRNode.ADD_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_add_underflow() {
        return ByteVector.broadcast(BSP, B_MIN)
                .add(ByteVector.broadcast(BSP, B_NEG_ONE))
                .lane(0);
    }

    @Run(test = "byte_add_underflow")
    static void run_byte_add_underflow() {
        byte br = byte_add_underflow();
        Verify.checkEQ(br, (byte) (B_MIN + B_NEG_ONE));
    }

    @Test
    @IR(failOn = IRNode.SUB_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_sub_overflow() {
        return ByteVector.broadcast(BSP, B_MAX)
                .sub(ByteVector.broadcast(BSP, B_NEG_ONE))
                .lane(0);
    }

    @Run(test = "byte_sub_overflow")
    static void run_byte_sub_overflow() {
        byte br = byte_sub_overflow();
        Verify.checkEQ(br, (byte) (B_MAX - B_NEG_ONE));
    }

    @Test
    @IR(failOn = IRNode.SUB_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_sub_underflow() {
        return ByteVector.broadcast(BSP, B_MIN)
                .sub(ByteVector.broadcast(BSP, B_ONE))
                .lane(0);
    }

    @Run(test = "byte_sub_underflow")
    static void run_byte_sub_underflow() {
        byte br = byte_sub_underflow();
        Verify.checkEQ(br, (byte) (B_MIN - B_ONE));
    }

    @Test
    @IR(failOn = IRNode.MUL_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_mul(byte ba, byte bb) {
        return ByteVector.broadcast(BSP, ba)
                .mul(ByteVector.broadcast(BSP, bb))
                .lane(0);
    }

    @Run(test = "byte_mul")
    static void run_byte_mul() {
        byte ba = (byte) R.nextInt();
        byte bb = (byte) R.nextInt();
        byte br = byte_mul(ba, bb);
        Verify.checkEQ(br, (byte) (ba * bb));
    }

    @Test
    @IR(failOn = IRNode.AND_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.AND_I, ">= 1", IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_and(byte ba, byte bb) {
        return ByteVector.broadcast(BSP, ba)
                .and(ByteVector.broadcast(BSP, bb))
                .lane(0);
    }

    @Run(test = "byte_and")
    static void run_byte_and() {
        byte ba = (byte) R.nextInt();
        byte bb = (byte) R.nextInt();
        byte br = byte_and(ba, bb);
        Verify.checkEQ(br, (byte) (ba & bb));
    }

    @Test
    @IR(failOn = IRNode.OR_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.OR_I, ">= 1", IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_or(byte ba, byte bb) {
        return ByteVector.broadcast(BSP, ba)
                .or(ByteVector.broadcast(BSP, bb))
                .lane(0);
    }

    @Run(test = "byte_or")
    static void run_byte_or() {
        byte ba = (byte) R.nextInt();
        byte bb = (byte) R.nextInt();
        byte br = byte_or(ba, bb);
        Verify.checkEQ(br, (byte) (ba | bb));
    }

    @Test
    @IR(failOn = IRNode.XOR_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.XOR_I, ">= 1", IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_xor(byte ba, byte bb) {
        return ByteVector.broadcast(BSP, ba)
                .lanewise(VectorOperators.XOR, ByteVector.broadcast(BSP, bb))
                .lane(0);
    }

    @Run(test = "byte_xor")
    static void run_byte_xor() {
        byte ba = (byte) R.nextInt();
        byte bb = (byte) R.nextInt();
        byte br = byte_xor(ba, bb);
        Verify.checkEQ(br, (byte) (ba ^ bb));
    }

    @Test
    @IR(failOn = IRNode.MIN_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MIN_I, ">= 1", IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_min(byte ba, byte bb) {
        return ByteVector.broadcast(BSP, ba)
                .min(ByteVector.broadcast(BSP, bb))
                .lane(0);
    }

    @Run(test = "byte_min")
    static void run_byte_min() {
        byte ba = (byte) R.nextInt();
        byte bb = (byte) R.nextInt();
        byte br = byte_min(ba, bb);
        Verify.checkEQ(br, (byte) Math.min(ba, bb));
    }

    @Test
    @IR(failOn = IRNode.MAX_VB,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MAX_I, ">= 1", IRNode.REPLICATE_B, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static byte byte_max(byte ba, byte bb) {
        return ByteVector.broadcast(BSP, ba)
                .max(ByteVector.broadcast(BSP, bb))
                .lane(0);
    }

    @Run(test = "byte_max")
    static void run_byte_max() {
        byte ba = (byte) R.nextInt();
        byte bb = (byte) R.nextInt();
        byte br = byte_max(ba, bb);
        Verify.checkEQ(br, (byte) Math.max(ba, bb));
    }

    /* =======================
     * SHORT
     * ======================= */

    static final VectorSpecies<Short> SSP = ShortVector.SPECIES_PREFERRED;
    static short S_MAX = Short.MAX_VALUE, S_MIN = Short.MIN_VALUE;
    static short S_ONE = (short) 1, S_NEG_ONE = (short) -1;

    @Test
    @IR(failOn = IRNode.ADD_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_add(short sa, short sb) {
        return ShortVector.broadcast(SSP, sa)
                .add(ShortVector.broadcast(SSP, sb))
                .lane(0);
    }

    @Run(test = "short_add")
    static void run_short_add() {
        short sa = (short) R.nextInt();
        short sb = (short) R.nextInt();
        short sr = short_add(sa, sb);
        Verify.checkEQ(sr, (short) (sa + sb));
    }

    @Test
    @IR(failOn = IRNode.SUB_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_sub(short sa, short sb) {
        return ShortVector.broadcast(SSP, sa)
                .sub(ShortVector.broadcast(SSP, sb))
                .lane(0);
    }

    @Run(test = "short_sub")
    static void run_short_sub() {
        short sa = (short) R.nextInt();
        short sb = (short) R.nextInt();
        short sr = short_sub(sa, sb);
        Verify.checkEQ(sr, (short) (sa - sb));
    }

    @Test
    @IR(failOn = IRNode.ADD_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_add_overflow() {
        return ShortVector.broadcast(SSP, S_MAX)
                .add(ShortVector.broadcast(SSP, S_ONE))
                .lane(0);
    }

    @Run(test = "short_add_overflow")
    static void run_short_add_overflow() {
        short sr = short_add_overflow();
        Verify.checkEQ(sr, (short) (S_MAX + S_ONE));
    }

    @Test
    @IR(failOn = IRNode.ADD_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_add_underflow() {
        return ShortVector.broadcast(SSP, S_MIN)
                .add(ShortVector.broadcast(SSP, S_NEG_ONE))
                .lane(0);
    }

    @Run(test = "short_add_underflow")
    static void run_short_add_underflow() {
        short sr = short_add_underflow();
        Verify.checkEQ(sr, (short) (S_MIN + S_NEG_ONE));
    }

    @Test
    @IR(failOn = IRNode.SUB_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_sub_overflow() {
        return ShortVector.broadcast(SSP, S_MAX)
                .sub(ShortVector.broadcast(SSP, S_NEG_ONE))
                .lane(0);
    }

    @Run(test = "short_sub_overflow")
    static void run_short_sub_overflow() {
        short sr = short_sub_overflow();
        Verify.checkEQ(sr, (short) (S_MAX - S_NEG_ONE));
    }

    @Test
    @IR(failOn = IRNode.SUB_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_sub_underflow() {
        return ShortVector.broadcast(SSP, S_MIN)
                .sub(ShortVector.broadcast(SSP, S_ONE))
                .lane(0);
    }

    @Run(test = "short_sub_underflow")
    static void run_short_sub_underflow() {
        short sr = short_sub_underflow();
        Verify.checkEQ(sr, (short) (S_MIN - S_ONE));
    }

    @Test
    @IR(failOn = IRNode.MUL_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1",
                   IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_mul(short sa, short sb) {
        return ShortVector.broadcast(SSP, sa)
                .mul(ShortVector.broadcast(SSP, sb))
                .lane(0);
    }

    @Run(test = "short_mul")
    static void run_short_mul() {
        short sa = (short) R.nextInt();
        short sb = (short) R.nextInt();
        short sr = short_mul(sa, sb);
        Verify.checkEQ(sr, (short) (sa * sb));
    }

    @Test
    @IR(failOn = IRNode.AND_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.AND_I, ">= 1", IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_and(short sa, short sb) {
        return ShortVector.broadcast(SSP, sa)
                .and(ShortVector.broadcast(SSP, sb))
                .lane(0);
    }

    @Run(test = "short_and")
    static void run_short_and() {
        short sa = (short) R.nextInt();
        short sb = (short) R.nextInt();
        short sr = short_and(sa, sb);
        Verify.checkEQ(sr, (short) (sa & sb));
    }

    @Test
    @IR(failOn = IRNode.OR_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.OR_I, ">= 1", IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_or(short sa, short sb) {
        return ShortVector.broadcast(SSP, sa)
                .or(ShortVector.broadcast(SSP, sb))
                .lane(0);
    }

    @Run(test = "short_or")
    static void run_short_or() {
        short sa = (short) R.nextInt();
        short sb = (short) R.nextInt();
        short sr = short_or(sa, sb);
        Verify.checkEQ(sr, (short) (sa | sb));
    }

    @Test
    @IR(failOn = IRNode.XOR_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.XOR_I, ">= 1", IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_xor(short sa, short sb) {
        return ShortVector.broadcast(SSP, sa)
                .lanewise(VectorOperators.XOR, ShortVector.broadcast(SSP, sb))
                .lane(0);
    }

    @Run(test = "short_xor")
    static void run_short_xor() {
        short sa = (short) R.nextInt();
        short sb = (short) R.nextInt();
        short sr = short_xor(sa, sb);
        Verify.checkEQ(sr, (short) (sa ^ sb));
    }

    @Test
    @IR(failOn = IRNode.MIN_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MIN_I, ">= 1", IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_min(short sa, short sb) {
        return ShortVector.broadcast(SSP, sa)
                .min(ShortVector.broadcast(SSP, sb))
                .lane(0);
    }

    @Run(test = "short_min")
    static void run_short_min() {
        short sa = (short) R.nextInt();
        short sb = (short) R.nextInt();
        short sr = short_min(sa, sb);
        Verify.checkEQ(sr, (short) Math.min(sa, sb));
    }

    @Test
    @IR(failOn = IRNode.MAX_VS,
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MAX_I, ">= 1", IRNode.REPLICATE_S, IRNode.VECTOR_SIZE_ANY, ">= 1" })
    static short short_max(short sa, short sb) {
        return ShortVector.broadcast(SSP, sa)
                .max(ShortVector.broadcast(SSP, sb))
                .lane(0);
    }

    @Run(test = "short_max")
    static void run_short_max() {
        short sa = (short) R.nextInt();
        short sb = (short) R.nextInt();
        short sr = short_max(sa, sb);
        Verify.checkEQ(sr, (short) Math.max(sa, sb));
    }

}
