/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
public abstract class VectorReduction2 {
    @Param({"2048"})
    public int SIZE;

    private int[] in1I;
    private int[] in2I;
    private int[] in3I;
    private long[] in1L;
    private long[] in2L;
    private long[] in3L;

    private float[] in1F;
    private float[] in2F;
    private float[] in3F;
    private double[] in1D;
    private double[] in2D;
    private double[] in3D;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    private static int globalResI;

    @Setup
    public void init() {
        in1I = new int[SIZE];
        in2I = new int[SIZE];
        in3I = new int[SIZE];
        in1L = new long[SIZE];
        in2L = new long[SIZE];
        in3L = new long[SIZE];

        in1F = new float[SIZE];
        in2F = new float[SIZE];
        in3F = new float[SIZE];
        in1D = new double[SIZE];
        in2D = new double[SIZE];
        in3D = new double[SIZE];

        for (int i = 0; i < SIZE; i++) {
            in1I[i] = r.nextInt();
            in2I[i] = r.nextInt();
            in3I[i] = r.nextInt();
            in1L[i] = r.nextLong();
            in2L[i] = r.nextLong();
            in3L[i] = r.nextLong();

            in1F[i] = r.nextFloat();
            in2F[i] = r.nextFloat();
            in3F[i] = r.nextFloat();
            in1D[i] = r.nextDouble();
            in2D[i] = r.nextDouble();
            in3D[i] = r.nextDouble();
        }
    }

    // Naming convention:
    //   How much work?
    //     - simple:   val = a[i]
    //     - dotprod:  val = a[i] * b[i]
    //     - big:      val = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i])
    //   Reduction operator:
    //     - and:     acc &= val
    //     - or:      acc |= val
    //     - xor:     acc ^= val
    //     - add:     acc += val
    //     - mul:     acc *= val
    //     - min:     acc = min(acc, val)
    //     - max:     acc = max(acc, val)

    // ---------int***Simple ------------------------------------------------------------
    @Benchmark
    public void intAndSimple(Blackhole bh) {
        int acc = 0xFFFFFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc &= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intOrSimple(Blackhole bh) {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc |= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intXorSimple(Blackhole bh) {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc ^= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intAddSimple(Blackhole bh) {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intMulSimple(Blackhole bh) {
        int acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intMinSimple(Blackhole bh) {
        int acc = Integer.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intMaxSimple(Blackhole bh) {
        int acc = Integer.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i];
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------int***DotProduct ------------------------------------------------------------
    @Benchmark
    public void intAndDotProduct(Blackhole bh) {
        int acc = 0xFFFFFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc &= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intOrDotProduct(Blackhole bh) {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc |= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intXorDotProduct(Blackhole bh) {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc ^= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intAddDotProduct(Blackhole bh) {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intMulDotProduct(Blackhole bh) {
        int acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intMinDotProduct(Blackhole bh) {
        int acc = Integer.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intMaxDotProduct(Blackhole bh) {
        int acc = Integer.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = in1I[i] * in2I[i];
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------int***Big ------------------------------------------------------------
    @Benchmark
    public void intAndBig(Blackhole bh) {
        int acc = 0xFFFFFFFF; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc &= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intOrBig(Blackhole bh) {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc |= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intXorBig(Blackhole bh) {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc ^= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intAddBig(Blackhole bh) {
        int acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intMulBig(Blackhole bh) {
        int acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intMinBig(Blackhole bh) {
        int acc = Integer.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void intMaxBig(Blackhole bh) {
        int acc = Integer.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            int val = (in1I[i] * in2I[i]) + (in1I[i] * in3I[i]) + (in2I[i] * in3I[i]);
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------long***Simple ------------------------------------------------------------
    @Benchmark
    public void longAndSimple(Blackhole bh) {
        long acc = 0xFFFFFFFFFFFFFFFFL; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc &= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longOrSimple(Blackhole bh) {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc |= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longXorSimple(Blackhole bh) {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc ^= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longAddSimple(Blackhole bh) {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longMulSimple(Blackhole bh) {
        long acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longMinSimple(Blackhole bh) {
        long acc = Long.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longMaxSimple(Blackhole bh) {
        long acc = Long.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i];
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------long***DotProduct ------------------------------------------------------------
    @Benchmark
    public void longAndDotProduct(Blackhole bh) {
        long acc = 0xFFFFFFFFFFFFFFFFL; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc &= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longOrDotProduct(Blackhole bh) {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc |= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longXorDotProduct(Blackhole bh) {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc ^= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longAddDotProduct(Blackhole bh) {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longMulDotProduct(Blackhole bh) {
        long acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longMinDotProduct(Blackhole bh) {
        long acc = Long.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longMaxDotProduct(Blackhole bh) {
        long acc = Long.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = in1L[i] * in2L[i];
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------long***Big ------------------------------------------------------------
    @Benchmark
    public void longAndBig(Blackhole bh) {
        long acc = 0xFFFFFFFFFFFFFFFFL; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc &= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longOrBig(Blackhole bh) {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc |= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longXorBig(Blackhole bh) {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc ^= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longAddBig(Blackhole bh) {
        long acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longMulBig(Blackhole bh) {
        long acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longMinBig(Blackhole bh) {
        long acc = Long.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void longMaxBig(Blackhole bh) {
        long acc = Long.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            long val = (in1L[i] * in2L[i]) + (in1L[i] * in3L[i]) + (in2L[i] * in3L[i]);
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------float***Simple ------------------------------------------------------------
    @Benchmark
    public void floatAddSimple(Blackhole bh) {
        float acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i];
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void floatMulSimple(Blackhole bh) {
        float acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i];
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void floatMinSimple(Blackhole bh) {
        float acc = Float.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i];
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void floatMaxSimple(Blackhole bh) {
        float acc = Float.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i];
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------float***DotProduct ------------------------------------------------------------
    @Benchmark
    public void floatAddDotProduct(Blackhole bh) {
        float acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i] * in2F[i];
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void floatMulDotProduct(Blackhole bh) {
        float acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i] * in2F[i];
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void floatMinDotProduct(Blackhole bh) {
        float acc = Float.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i] * in2F[i];
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void floatMaxDotProduct(Blackhole bh) {
        float acc = Float.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = in1F[i] * in2F[i];
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------float***Big ------------------------------------------------------------
    @Benchmark
    public void floatAddBig(Blackhole bh) {
        float acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = (in1F[i] * in2F[i]) + (in1F[i] * in3F[i]) + (in2F[i] * in3F[i]);
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void floatMulBig(Blackhole bh) {
        float acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = (in1F[i] * in2F[i]) + (in1F[i] * in3F[i]) + (in2F[i] * in3F[i]);
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void floatMinBig(Blackhole bh) {
        float acc = Float.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = (in1F[i] * in2F[i]) + (in1F[i] * in3F[i]) + (in2F[i] * in3F[i]);
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void floatMaxBig(Blackhole bh) {
        float acc = Float.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            float val = (in1F[i] * in2F[i]) + (in1F[i] * in3F[i]) + (in2F[i] * in3F[i]);
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------double***Simple ------------------------------------------------------------
    @Benchmark
    public void doubleAddSimple(Blackhole bh) {
        double acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i];
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void doubleMulSimple(Blackhole bh) {
        double acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i];
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void doubleMinSimple(Blackhole bh) {
        double acc = Double.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i];
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void doubleMaxSimple(Blackhole bh) {
        double acc = Double.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i];
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------double***DotProduct ------------------------------------------------------------
    @Benchmark
    public void doubleAddDotProduct(Blackhole bh) {
        double acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i] * in2D[i];
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void doubleMulDotProduct(Blackhole bh) {
        double acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i] * in2D[i];
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void doubleMinDotProduct(Blackhole bh) {
        double acc = Double.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i] * in2D[i];
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void doubleMaxDotProduct(Blackhole bh) {
        double acc = Double.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = in1D[i] * in2D[i];
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    // ---------double***Big ------------------------------------------------------------
    @Benchmark
    public void doubleAddBig(Blackhole bh) {
        double acc = 0; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = (in1D[i] * in2D[i]) + (in1D[i] * in3D[i]) + (in2D[i] * in3D[i]);
            acc += val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void doubleMulBig(Blackhole bh) {
        double acc = 1; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = (in1D[i] * in2D[i]) + (in1D[i] * in3D[i]) + (in2D[i] * in3D[i]);
            acc *= val;
        }
        bh.consume(acc);
    }

    @Benchmark
    public void doubleMinBig(Blackhole bh) {
        double acc = Double.MAX_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = (in1D[i] * in2D[i]) + (in1D[i] * in3D[i]) + (in2D[i] * in3D[i]);
            acc = Math.min(acc, val);
        }
        bh.consume(acc);
    }

    @Benchmark
    public void doubleMaxBig(Blackhole bh) {
        double acc = Double.MIN_VALUE; // neutral element
        for (int i = 0; i < SIZE; i++) {
            double val = (in1D[i] * in2D[i]) + (in1D[i] * in3D[i]) + (in2D[i] * in3D[i]);
            acc = Math.max(acc, val);
        }
        bh.consume(acc);
    }

    @Fork(value = 1, jvmArgsPrepend = {"-XX:+UseSuperWord"})
    public static class WithSuperword extends VectorReduction2 {}

    @Fork(value = 1, jvmArgsPrepend = {"-XX:-UseSuperWord"})
    public static class NoSuperword extends VectorReduction2 {}
}

