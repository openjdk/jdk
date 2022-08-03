/*
 *  Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.jdk.incubator.vector;

import java.util.Random;
import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class VectorFPtoIntCastOperations {
    @Param({"512", "1024"})
    static int SIZE;

    static final float [] float_sp_vals = {
       Float.NaN,
       Float.POSITIVE_INFINITY,
       Float.NEGATIVE_INFINITY,
       0.0f,
       -0.0f
    };

    static final double [] double_sp_vals = {
       Double.NaN,
       Double.POSITIVE_INFINITY,
       Double.NEGATIVE_INFINITY,
       0.0,
       -0.0
    };

    static float [] float_arr;

    static double [] double_arr;

    static long [] long_res;

    static int [] int_res;

    static short [] short_res;

    static byte [] byte_res;

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(1024);
        float_arr = new float[SIZE];
        double_arr = new double[SIZE];
        long_res = new long[SIZE];
        int_res = new int[SIZE * 2];
        short_res = new short[SIZE * 4];
        byte_res = new byte[SIZE * 8];
        for(int i = 0; i < SIZE; i++) {
            float_arr[i] = SIZE * r.nextFloat();
            double_arr[i] = SIZE * r.nextDouble();
        }
        for(int i = 0 ; i < SIZE; i += 100) {
            System.arraycopy(float_sp_vals, 0, float_arr, i, float_sp_vals.length);
            System.arraycopy(double_sp_vals, 0, double_arr, i, double_sp_vals.length);
        }
    }

    @Benchmark
    public void microFloat128ToByte128() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2B, ByteVector.SPECIES_128, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToByte256() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2B, ByteVector.SPECIES_256, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToByte512() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2B, ByteVector.SPECIES_512, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToShort128() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2S, ShortVector.SPECIES_128, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToShort256() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2S, ShortVector.SPECIES_256, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToShort512() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2S, ShortVector.SPECIES_512, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToInteger128() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2I, IntVector.SPECIES_128, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToInteger256() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2I, IntVector.SPECIES_256, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToInteger512() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2I, IntVector.SPECIES_512, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToLong128() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2L, LongVector.SPECIES_128, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToLong256() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2L, LongVector.SPECIES_256, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microFloat128ToLong512() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2L, LongVector.SPECIES_512, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToByte128() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2B, ByteVector.SPECIES_128, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToByte256() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2B, ByteVector.SPECIES_256, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToByte512() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2B, ByteVector.SPECIES_512, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToShort128() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2S, ShortVector.SPECIES_128, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToShort256() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2S, ShortVector.SPECIES_256, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToShort512() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2S, ShortVector.SPECIES_512, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToInteger128() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2I, IntVector.SPECIES_128, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToInteger256() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2I, IntVector.SPECIES_256, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToInteger512() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2I, IntVector.SPECIES_512, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToLong128() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2L, LongVector.SPECIES_128, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToLong256() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2L, LongVector.SPECIES_256, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microFloat256ToLong512() {
        VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            FloatVector.fromArray(SPECIES, float_arr, i)
                .convertShape(VectorOperators.F2L, LongVector.SPECIES_512, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToByte128() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2B, ByteVector.SPECIES_128, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToByte256() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2B, ByteVector.SPECIES_256, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToByte512() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2B, ByteVector.SPECIES_512, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToShort128() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2S, ShortVector.SPECIES_128, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToShort256() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2S, ShortVector.SPECIES_256, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToShort512() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2S, ShortVector.SPECIES_512, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToInteger128() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2I, IntVector.SPECIES_128, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToInteger256() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2I, IntVector.SPECIES_256, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToInteger512() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2I, IntVector.SPECIES_512, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToLong128() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2L, LongVector.SPECIES_128, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToLong256() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2L, LongVector.SPECIES_256, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microDouble128ToLong512() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_128;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2L, LongVector.SPECIES_512, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToByte128() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2B, ByteVector.SPECIES_128, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToByte256() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2B, ByteVector.SPECIES_256, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToByte512() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2B, ByteVector.SPECIES_512, 0)
                .reinterpretAsBytes()
                .intoArray(byte_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToShort128() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2S, ShortVector.SPECIES_128, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToShort256() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2S, ShortVector.SPECIES_256, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToShort512() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2S, ShortVector.SPECIES_512, 0)
                .reinterpretAsShorts()
                .intoArray(short_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToInteger128() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2I, IntVector.SPECIES_128, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToInteger256() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2I, IntVector.SPECIES_256, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToInteger512() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2I, IntVector.SPECIES_512, 0)
                .reinterpretAsInts()
                .intoArray(int_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToLong128() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2L, LongVector.SPECIES_128, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToLong256() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2L, LongVector.SPECIES_256, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }

    @Benchmark
    public void microDouble256ToLong512() {
        VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;
        for (int i = 0; i < SPECIES.loopBound(SIZE); i += SPECIES.length()) {
            DoubleVector.fromArray(SPECIES, double_arr, i)
                .convertShape(VectorOperators.D2L, LongVector.SPECIES_512, 0)
                .reinterpretAsLongs()
                .intoArray(long_res, i);
        }
    }
}
