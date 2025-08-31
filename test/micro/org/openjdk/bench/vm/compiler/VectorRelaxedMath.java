/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.math.RelaxedMath;

/*
 * Measure the performance difference of using RelaxedMath.add and mul, with
 * reduction reordering mode, compared to the regular "strict" reduction
 * order.
 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"--add-exports", "java.base/jdk.internal.math=ALL-UNNAMED"})
public class VectorRelaxedMath {
    @Param({"10000"})
    public int SIZE;

    private float[] aF;
    private float[] bF;

    private double[] aD;
    private double[] bD;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        aF = new float[SIZE];
        bF = new float[SIZE];

        aD = new double[SIZE];
        bD = new double[SIZE];

        for (int i = 0; i < SIZE; i++) {
            aF[i] = r.nextFloat();
            bF[i] = r.nextFloat();

            aD[i] = r.nextDouble();
            bD[i] = r.nextDouble();
        }
    }

    @Benchmark
    public float floatAddReductionStrict() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum += aF[i];
        }
        return sum;
    }

    @Benchmark
    public float floatAddReductionReorder() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum = RelaxedMath.add(sum, aF[i], /* allow reduction reordering */ 1);
        }
        return sum;
    }

    @Benchmark
    public float floatMulReductionStrict() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum *= aF[i];
        }
        return sum;
    }

    @Benchmark
    public float floatMulReductionReorder() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum = RelaxedMath.mul(sum, aF[i], /* allow reduction reordering */ 1);
        }
        return sum;
    }

    @Benchmark
    public float floatAddReductionDotProductStrict() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum += aF[i] * bF[i];
        }
        return sum;
    }

    @Benchmark
    public float floatAddReductionDotProductReorder() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum = RelaxedMath.add(sum, aF[i] * bF[i], /* allow reduction reordering */ 1);
        }
        return sum;
    }

    @Benchmark
    public float floatMulReductionOfAdditionStrict() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum *= aF[i] + bF[i];
        }
        return sum;
    }

    @Benchmark
    public float floatMulReductionOfAdditionReorder() {
        float sum = 0;
        for (int i = 0; i < aF.length; i++) {
            sum = RelaxedMath.mul(sum, aF[i] + bF[i], /* allow reduction reordering */ 1);
        }
        return sum;
    }

    @Benchmark
    public double doubleAddReductionStrict() {
        double sum = 0;
        for (int i = 0; i < aD.length; i++) {
            sum += aD[i];
        }
        return sum;
    }

    @Benchmark
    public double doubleAddReductionReorder() {
        double sum = 0;
        for (int i = 0; i < aD.length; i++) {
            sum = RelaxedMath.add(sum, aD[i], /* allow reduction reordering */ 1);
        }
        return sum;
    }

    @Benchmark
    public double doubleMulReductionStrict() {
        double sum = 0;
        for (int i = 0; i < aD.length; i++) {
            sum *= aD[i];
        }
        return sum;
    }

    @Benchmark
    public double doubleMulReductionReorder() {
        double sum = 0;
        for (int i = 0; i < aD.length; i++) {
            sum = RelaxedMath.mul(sum, aD[i], /* allow reduction reordering */ 1);
        }
        return sum;
    }

    @Benchmark
    public double doubleAddReductionDotProductStrict() {
        double sum = 0;
        for (int i = 0; i < aD.length; i++) {
            sum += aD[i] * bD[i];
        }
        return sum;
    }

    @Benchmark
    public double doubleAddReductionDotProductReorder() {
        double sum = 0;
        for (int i = 0; i < aD.length; i++) {
            sum = RelaxedMath.add(sum, aD[i] * bD[i], /* allow reduction reordering */ 1);
        }
        return sum;
    }

    @Benchmark
    public double doubleMulReductionOfAdditionStrict() {
        double sum = 0;
        for (int i = 0; i < aD.length; i++) {
            sum *= aD[i] + bD[i];
        }
        return sum;
    }

    @Benchmark
    public double doubleMulReductionOfAdditionReorder() {
        double sum = 0;
        for (int i = 0; i < aD.length; i++) {
            sum = RelaxedMath.mul(sum, aD[i] + bD[i], /* allow reduction reordering */ 1);
        }
        return sum;
    }
}
