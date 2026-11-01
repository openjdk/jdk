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

package org.openjdk.bench.jdk.incubator.vector;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector"})
@State(Scope.Thread)
public class VectorTranscendentalBenchmark {
    // TANH is intentionally excluded because the SLEEF VectorMathLibrary backend rejects it.
    @Param({"1024", "4096", "16384"})
    private int size;

    @Param({"128", "256"})
    private int bits;

    private VectorSpecies<Float> fsp;
    private VectorSpecies<Double> dsp;

    private FloatVector[] anyF;
    private FloatVector[] smallF;
    private FloatVector[] unitF;
    private FloatVector[] positiveF;
    private FloatVector[] log1pF;
    private FloatVector[] powBaseF;
    private FloatVector[] powExpF;

    private DoubleVector[] anyD;
    private DoubleVector[] smallD;
    private DoubleVector[] unitD;
    private DoubleVector[] positiveD;
    private DoubleVector[] log1pD;
    private DoubleVector[] powBaseD;
    private DoubleVector[] powExpD;

    @Setup
    public void setup() {
        fsp = switch (bits) {
            case 128 -> FloatVector.SPECIES_128;
            case 256 -> FloatVector.SPECIES_256;
            default -> throw new IllegalArgumentException("Unsupported vector size: " + bits);
        };
        dsp = switch (bits) {
            case 128 -> DoubleVector.SPECIES_128;
            case 256 -> DoubleVector.SPECIES_256;
            default -> throw new IllegalArgumentException("Unsupported vector size: " + bits);
        };

        float[] anyFArr = new float[size];
        float[] smallFArr = new float[size];
        float[] unitFArr = new float[size];
        float[] positiveFArr = new float[size];
        float[] log1pFArr = new float[size];
        float[] powBaseFArr = new float[size];
        float[] powExpFArr = new float[size];

        double[] anyDArr = new double[size];
        double[] smallDArr = new double[size];
        double[] unitDArr = new double[size];
        double[] positiveDArr = new double[size];
        double[] log1pDArr = new double[size];
        double[] powBaseDArr = new double[size];
        double[] powExpDArr = new double[size];

        Random random = new Random(42);
        for (int i = 0; i < size; i++) {
            double any = (random.nextDouble() - 0.5d) * Math.PI * 4.0d;
            double small = (random.nextDouble() - 0.5d) * 2.0d;
            double unit = (random.nextDouble() - 0.5d) * 1.8d;
            double positive = random.nextDouble() * 10.0d + 0.1d;
            double log1p = random.nextDouble() * 4.5d - 0.5d;
            double powBase = random.nextDouble() * 10.0d + 0.1d;
            double powExp = (random.nextDouble() - 0.5d) * 4.0d;

            anyFArr[i] = (float) any;
            smallFArr[i] = (float) small;
            unitFArr[i] = (float) unit;
            positiveFArr[i] = (float) positive;
            log1pFArr[i] = (float) log1p;
            powBaseFArr[i] = (float) powBase;
            powExpFArr[i] = (float) powExp;

            anyDArr[i] = any;
            smallDArr[i] = small;
            unitDArr[i] = unit;
            positiveDArr[i] = positive;
            log1pDArr[i] = log1p;
            powBaseDArr[i] = powBase;
            powExpDArr[i] = powExp;
        }

        anyF = loadFloatVectors(anyFArr);
        smallF = loadFloatVectors(smallFArr);
        unitF = loadFloatVectors(unitFArr);
        positiveF = loadFloatVectors(positiveFArr);
        log1pF = loadFloatVectors(log1pFArr);
        powBaseF = loadFloatVectors(powBaseFArr);
        powExpF = loadFloatVectors(powExpFArr);

        anyD = loadDoubleVectors(anyDArr);
        smallD = loadDoubleVectors(smallDArr);
        unitD = loadDoubleVectors(unitDArr);
        positiveD = loadDoubleVectors(positiveDArr);
        log1pD = loadDoubleVectors(log1pDArr);
        powBaseD = loadDoubleVectors(powBaseDArr);
        powExpD = loadDoubleVectors(powExpDArr);
    }

    @Benchmark
    public void floatTan(Blackhole bh) {
        unaryFloat(VectorOperators.TAN, smallF, bh);
    }

    @Benchmark
    public void doubleTan(Blackhole bh) {
        unaryDouble(VectorOperators.TAN, smallD, bh);
    }

    @Benchmark
    public void floatSin(Blackhole bh) {
        unaryFloat(VectorOperators.SIN, anyF, bh);
    }

    @Benchmark
    public void doubleSin(Blackhole bh) {
        unaryDouble(VectorOperators.SIN, anyD, bh);
    }

    @Benchmark
    public void floatSinh(Blackhole bh) {
        unaryFloat(VectorOperators.SINH, smallF, bh);
    }

    @Benchmark
    public void doubleSinh(Blackhole bh) {
        unaryDouble(VectorOperators.SINH, smallD, bh);
    }

    @Benchmark
    public void floatCos(Blackhole bh) {
        unaryFloat(VectorOperators.COS, anyF, bh);
    }

    @Benchmark
    public void doubleCos(Blackhole bh) {
        unaryDouble(VectorOperators.COS, anyD, bh);
    }

    @Benchmark
    public void floatCosh(Blackhole bh) {
        unaryFloat(VectorOperators.COSH, smallF, bh);
    }

    @Benchmark
    public void doubleCosh(Blackhole bh) {
        unaryDouble(VectorOperators.COSH, smallD, bh);
    }

    @Benchmark
    public void floatAsin(Blackhole bh) {
        unaryFloat(VectorOperators.ASIN, unitF, bh);
    }

    @Benchmark
    public void doubleAsin(Blackhole bh) {
        unaryDouble(VectorOperators.ASIN, unitD, bh);
    }

    @Benchmark
    public void floatAcos(Blackhole bh) {
        unaryFloat(VectorOperators.ACOS, unitF, bh);
    }

    @Benchmark
    public void doubleAcos(Blackhole bh) {
        unaryDouble(VectorOperators.ACOS, unitD, bh);
    }

    @Benchmark
    public void floatAtan(Blackhole bh) {
        unaryFloat(VectorOperators.ATAN, anyF, bh);
    }

    @Benchmark
    public void doubleAtan(Blackhole bh) {
        unaryDouble(VectorOperators.ATAN, anyD, bh);
    }

    @Benchmark
    public void floatCbrt(Blackhole bh) {
        unaryFloat(VectorOperators.CBRT, anyF, bh);
    }

    @Benchmark
    public void doubleCbrt(Blackhole bh) {
        unaryDouble(VectorOperators.CBRT, anyD, bh);
    }

    @Benchmark
    public void floatLog(Blackhole bh) {
        unaryFloat(VectorOperators.LOG, positiveF, bh);
    }

    @Benchmark
    public void doubleLog(Blackhole bh) {
        unaryDouble(VectorOperators.LOG, positiveD, bh);
    }

    @Benchmark
    public void floatLog10(Blackhole bh) {
        unaryFloat(VectorOperators.LOG10, positiveF, bh);
    }

    @Benchmark
    public void doubleLog10(Blackhole bh) {
        unaryDouble(VectorOperators.LOG10, positiveD, bh);
    }

    @Benchmark
    public void floatLog1p(Blackhole bh) {
        unaryFloat(VectorOperators.LOG1P, log1pF, bh);
    }

    @Benchmark
    public void doubleLog1p(Blackhole bh) {
        unaryDouble(VectorOperators.LOG1P, log1pD, bh);
    }

    @Benchmark
    public void floatExp(Blackhole bh) {
        unaryFloat(VectorOperators.EXP, smallF, bh);
    }

    @Benchmark
    public void doubleExp(Blackhole bh) {
        unaryDouble(VectorOperators.EXP, smallD, bh);
    }

    @Benchmark
    public void floatExpm1(Blackhole bh) {
        unaryFloat(VectorOperators.EXPM1, smallF, bh);
    }

    @Benchmark
    public void doubleExpm1(Blackhole bh) {
        unaryDouble(VectorOperators.EXPM1, smallD, bh);
    }

    @Benchmark
    public void floatAtan2(Blackhole bh) {
        binaryFloat(VectorOperators.ATAN2, anyF, smallF, bh);
    }

    @Benchmark
    public void doubleAtan2(Blackhole bh) {
        binaryDouble(VectorOperators.ATAN2, anyD, smallD, bh);
    }

    @Benchmark
    public void floatPow(Blackhole bh) {
        binaryFloat(VectorOperators.POW, powBaseF, powExpF, bh);
    }

    @Benchmark
    public void doublePow(Blackhole bh) {
        binaryDouble(VectorOperators.POW, powBaseD, powExpD, bh);
    }

    @Benchmark
    public void floatHypot(Blackhole bh) {
        binaryFloat(VectorOperators.HYPOT, anyF, smallF, bh);
    }

    @Benchmark
    public void doubleHypot(Blackhole bh) {
        binaryDouble(VectorOperators.HYPOT, anyD, smallD, bh);
    }

    private FloatVector[] loadFloatVectors(float[] input) {
        FloatVector[] vectors = new FloatVector[size / fsp.length()];
        for (int i = 0; i < vectors.length; i++) {
            vectors[i] = FloatVector.fromArray(fsp, input, i * fsp.length());
        }
        return vectors;
    }

    private DoubleVector[] loadDoubleVectors(double[] input) {
        DoubleVector[] vectors = new DoubleVector[size / dsp.length()];
        for (int i = 0; i < vectors.length; i++) {
            vectors[i] = DoubleVector.fromArray(dsp, input, i * dsp.length());
        }
        return vectors;
    }

    private static void unaryFloat(VectorOperators.Unary op, FloatVector[] input, Blackhole bh) {
        for (FloatVector v : input) {
            bh.consume(v.lanewise(op));
        }
    }

    private static void unaryDouble(VectorOperators.Unary op, DoubleVector[] input, Blackhole bh) {
        for (DoubleVector v : input) {
            bh.consume(v.lanewise(op));
        }
    }

    private static void binaryFloat(VectorOperators.Binary op, FloatVector[] input1,
            FloatVector[] input2, Blackhole bh) {
        for (int i = 0; i < input1.length; i++) {
            bh.consume(input1[i].lanewise(op, input2[i]));
        }
    }

    private static void binaryDouble(VectorOperators.Binary op, DoubleVector[] input1,
            DoubleVector[] input2, Blackhole bh) {
        for (int i = 0; i < input1.length; i++) {
            bh.consume(input1[i].lanewise(op, input2[i]));
        }
    }
}
