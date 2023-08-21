//
// Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// or visit www.oracle.com if you need additional information or have any
// questions.
//
//
package org.openjdk.bench.jdk.incubator.vector;

import java.util.random.RandomGenerator;

import jdk.incubator.vector.*;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgsPrepend = {"--add-modules=jdk.incubator.vector", "-XX:-TieredCompilation"})
public class MatrixMultiplicationBenchmark {

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private static final int BLOCK_SIZE = 16;
    @Param({"32", "1024"})
    private int size;
    private float[] left;
    private float[] right;

    private float[] result;

    @Setup
    public void setup() {
        this.left = MatrixMultiplicationBenchmark.newFloatRowMatrix(size * size);
        this.right = MatrixMultiplicationBenchmark.newFloatRowMatrix(size * size);
        this.result = new float[size * size];
    }

    @Benchmark
    public float[] mmulBaseline() {
        return baseline(left, right, result, size);
    }

    @Benchmark
    public float[] mmulBlocked() {
        return blocked(left, right, result, size, BLOCK_SIZE);
    }

    @Benchmark
    public float[] mmulSimpleFMA() {
        return simpleFMA(left, right, result, size);
    }

    @Benchmark
    public float[] mmulSimpleVector() {
        return simpleVector(left, right, result, size);
    }

    @Benchmark
    public float[] mmulBlockedVectorAuto() {
        return blockedVector(left, right, result, size);
    }


    private float[] baseline(float[] a, float[] b, float[] result, int n) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float sum = 0.0f;
                for (int k = 0; k < n; k++) {
                    sum += a[i * n + k] * b[k * n + j];
                }
                result[i * n + j] = sum;
            }
        }
        return result;
    }

    private float[] blocked(float[] a, float[] b, float[] result, int n, int blocksize) {
        for (int kk = 0; kk < n; kk += blocksize) {
            for (int jj = 0; jj < n; jj += blocksize) {
                for (int i = 0; i < n; i++) {
                    for (int j = jj; j < jj + blocksize; ++j) {
                        float sum = result[i * n + j];
                        for (int k = kk; k < kk + blocksize; ++k) {
                            sum += a[i * n + k] * b[k * n + j];
                        }
                        result[i * n + j] = sum;
                    }
                }
            }
        }
        return result;
    }

    private float[] simpleFMA(float[] a, float[] b, float[] result, int n) {
        int in = 0;
        for (int i = 0; i < n; ++i) {
            int kn = 0;
            for (int k = 0; k < n; ++k) {
                float aik = a[in + k];
                for (int j = 0; j < n; ++j) {
                    result[in + j] = Math.fma(aik, b[kn + j], result[in + j]);
                }
                kn += n;
            }
            in += n;
        }
        return result;
    }

    private float[] simpleVector(float[] a, float[] b, float[] result, int n) {
        final int upperBound = SPECIES.loopBound(n);

        int in = 0;
        for (int i = 0; i < n; i++) {
            int kn = 0;
            for (int k = 0; k < n; k++) {
                float aik = a[in + k];
                FloatVector vaik = FloatVector.broadcast(SPECIES, aik);

                for (int j = 0; j < upperBound; j += SPECIES.length()) {
                    // FloatVector va, vb, vc
                    var vb = FloatVector.fromArray(SPECIES, b, kn + j);
                    var vResult = FloatVector.fromArray(SPECIES, result, in + j);
                    vResult = vaik.fma(vb, vResult);
                    vResult.intoArray(result, in + j);
                }
                kn += n;
            }
            in += n;
        }
        return result;
    }

    private float[] blockedVector(float[] a, float[] b, float[] result, int n) {
        int blockWidth = n >= 256 ? 512 : 256;
        int blockHeight = n >= 512 ? 8 : n >= 256 ? 16 : 32;

        for (int rowOffset = 0; rowOffset < n; rowOffset += blockHeight) {
            for (int columnOffset = 0; columnOffset < n; columnOffset += blockWidth) {
                for (int i = 0; i < n; i++) {
                    for (int j = columnOffset; j < columnOffset + blockWidth && j < n; j += SPECIES.length()) {
                        var sum = FloatVector.fromArray(SPECIES, result, i * n + j);
                        for (int k = rowOffset; k < rowOffset + blockHeight && k < n; k++) {
                            var multiplier = FloatVector.broadcast(SPECIES, a[i * n + k]);
                            sum = multiplier.fma(FloatVector.fromArray(SPECIES, b, k * n + j), sum);
                        }
                        sum.intoArray(result, i * n + j);
                    }
                }
            }
        }
        return result;
    }

    private static float[] newFloatRowMatrix(int size) {
        float[] matrix = new float[size];
        var randomGenerator = RandomGenerator.getDefault();
        for (int i = 0; i < matrix.length; ++i) {
            matrix[i] = randomGenerator.nextFloat();
        }
        return matrix;
    }

}

