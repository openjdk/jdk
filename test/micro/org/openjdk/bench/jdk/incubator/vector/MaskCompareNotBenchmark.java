/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

package org.openjdk.bench.jdk.incubator.vector;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import jdk.incubator.vector.*;
import java.lang.invoke.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgs = { "--add-modules=jdk.incubator.vector" })
public abstract class MaskCompareNotBenchmark {
    @Param({"4096"})
    protected int ARRAYLEN;

    // Abstract method to get comparison operator from subclasses
    protected abstract String getComparisonOperatorName();

    // To get compile-time constants for comparison operation
    static final MutableCallSite MUTABLE_COMPARISON_CONSTANT = new MutableCallSite(MethodType.methodType(VectorOperators.Comparison.class));
    static final MethodHandle MUTABLE_COMPARISON_CONSTANT_HANDLE = MUTABLE_COMPARISON_CONSTANT.dynamicInvoker();

    private static Random r = new Random();

    protected static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    protected static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_MAX;
    protected static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;
    protected static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_MAX;
    protected static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;
    protected static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;

    protected boolean[] mr;
    protected byte[] ba;
    protected byte[] bb;
    protected short[] sa;
    protected short[] sb;
    protected int[] ia;
    protected int[] ib;
    protected long[] la;
    protected long[] lb;
    protected float[] fa;
    protected float[] fb;
    protected double[] da;
    protected double[] db;

    @Setup
    public void init() throws Throwable {
        mr = new boolean[ARRAYLEN];
        ba = new byte[ARRAYLEN];
        bb = new byte[ARRAYLEN];
        sa = new short[ARRAYLEN];
        sb = new short[ARRAYLEN];
        ia = new int[ARRAYLEN];
        ib = new int[ARRAYLEN];
        la = new long[ARRAYLEN];
        lb = new long[ARRAYLEN];
        fa = new float[ARRAYLEN];
        fb = new float[ARRAYLEN];
        da = new double[ARRAYLEN];
        db = new double[ARRAYLEN];

        for (int i = 0; i < ARRAYLEN; i++) {
            mr[i] = r.nextBoolean();
            ba[i] = (byte) r.nextInt();
            bb[i] = (byte) r.nextInt();
            sa[i] = (short) r.nextInt();
            sb[i] = (short) r.nextInt();
            ia[i] = r.nextInt();
            ib[i] = r.nextInt();
            la[i] = r.nextLong();
            lb[i] = r.nextLong();
            fa[i] = r.nextFloat();
            fb[i] = r.nextFloat();
            da[i] = r.nextDouble();
            db[i] = r.nextDouble();
        }

        VectorOperators.Comparison comparisonOp = getComparisonOperator(getComparisonOperatorName());
        MethodHandle constant = MethodHandles.constant(VectorOperators.Comparison.class, comparisonOp);
        MUTABLE_COMPARISON_CONSTANT.setTarget(constant);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private static VectorOperators.Comparison getComparisonOperator(String op) {
        switch (op) {
            case "EQ": return VectorOperators.EQ;
            case "NE": return VectorOperators.NE;
            case "LT": return VectorOperators.LT;
            case "LE": return VectorOperators.LE;
            case "GT": return VectorOperators.GT;
            case "GE": return VectorOperators.GE;
            case "ULT": return VectorOperators.ULT;
            case "ULE": return VectorOperators.ULE;
            case "UGT": return VectorOperators.UGT;
            case "UGE": return VectorOperators.UGE;
            default: throw new IllegalArgumentException("Unknown comparison operator: " + op);
        }
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    protected VectorOperators.Comparison comparison_con() throws Throwable {
        return (VectorOperators.Comparison) MUTABLE_COMPARISON_CONSTANT_HANDLE.invokeExact();
    }

    // Subclasses with different comparison operators
    public static class IntegerComparisons extends MaskCompareNotBenchmark {
        @Param({"EQ", "NE", "LT", "LE", "GT", "GE", "ULT", "ULE", "UGT", "UGE"})
        public String COMPARISON_OP;

        @Override
        protected String getComparisonOperatorName() {
            return COMPARISON_OP;
        }

        @Benchmark
        public void testCompareMaskNotByte() throws Throwable {
            VectorOperators.Comparison op = comparison_con();
            ByteVector bv = ByteVector.fromArray(B_SPECIES, bb, 0);
            for (int j = 0; j < ARRAYLEN; j += B_SPECIES.length()) {
                ByteVector av = ByteVector.fromArray(B_SPECIES, ba, j);
                VectorMask<Byte> m = av.compare(op, bv).not();
                m.intoArray(mr, j);
            }
        }

        @Benchmark
        public void testCompareMaskNotShort() throws Throwable {
            VectorOperators.Comparison op = comparison_con();
            ShortVector bv = ShortVector.fromArray(S_SPECIES, sb, 0);
            for (int j = 0; j < ARRAYLEN; j += S_SPECIES.length()) {
                ShortVector av = ShortVector.fromArray(S_SPECIES, sa, j);
                VectorMask<Short> m = av.compare(op, bv).not();
                m.intoArray(mr, j);
            }
        }

        @Benchmark
        public void testCompareMaskNotInt() throws Throwable {
            VectorOperators.Comparison op = comparison_con();
            IntVector bv = IntVector.fromArray(I_SPECIES, ib, 0);
            for (int j = 0; j < ARRAYLEN; j += I_SPECIES.length()) {
                IntVector av = IntVector.fromArray(I_SPECIES, ia, j);
                VectorMask<Integer> m = av.compare(op, bv).not();
                m.intoArray(mr, j);
            }
        }

        @Benchmark
        public void testCompareMaskNotLong() throws Throwable {
            VectorOperators.Comparison op = comparison_con();
            LongVector bv = LongVector.fromArray(L_SPECIES, lb, 0);
            for (int j = 0; j < ARRAYLEN; j += L_SPECIES.length()) {
                LongVector av = LongVector.fromArray(L_SPECIES, la, j);
                VectorMask<Long> m = av.compare(op, bv).not();
                m.intoArray(mr, j);
            }
        }
    }

    public static class FloatingPointComparisons extends MaskCompareNotBenchmark {
        // "ULT", "ULE", "UGT", "UGE" are not supported for floating point types
        @Param({"EQ", "NE", "LT", "LE", "GT", "GE"})
        public String COMPARISON_OP;

        @Override
        protected String getComparisonOperatorName() {
            return COMPARISON_OP;
        }

        @Benchmark
        public void testCompareMaskNotFloat() throws Throwable {
            VectorOperators.Comparison op = comparison_con();
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, 0);
            for (int j = 0; j < ARRAYLEN; j += F_SPECIES.length()) {
                FloatVector av = FloatVector.fromArray(F_SPECIES, fa, j);
                VectorMask<Float> m = av.compare(op, bv).not();
                m.intoArray(mr, j);
            }
        }

        @Benchmark
        public void testCompareMaskNotDouble() throws Throwable {
            VectorOperators.Comparison op = comparison_con();
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, 0);
            for (int j = 0; j < ARRAYLEN; j += D_SPECIES.length()) {
                DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, j);
                VectorMask<Double> m = av.compare(op, bv).not();
                m.intoArray(mr, j);
            }
        }
    }
}
