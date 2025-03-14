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
package org.openjdk.bench.java.lang;

import java.util.stream.IntStream;
import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import static jdk.incubator.vector.Float16.*;
import static java.lang.Float.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector", "-Xbatch", "-XX:-TieredCompilation"})
public class Float16OperationsBenchmark {
    @Param({"256", "512", "1024", "2048"})
    int vectorDim;

    int   [] rexp;
    short [] vectorRes;
    short [] vector1;
    short [] vector2;
    short [] vector3;
    short [] vector4;
    short [] vector5;
    boolean [] vectorPredicate;

    static final short f16_one = Float.floatToFloat16(1.0f);
    static final short f16_two = Float.floatToFloat16(2.0f);

    @Setup(Level.Trial)
    public void BmSetup() {
        rexp      = new int[vectorDim];
        vectorRes = new short[vectorDim];
        vector1   = new short[vectorDim];
        vector2   = new short[vectorDim];
        vector3   = new short[vectorDim];
        vector4   = new short[vectorDim];
        vector5   = new short[vectorDim];
        vectorPredicate = new boolean[vectorDim];

        IntStream.range(0, vectorDim).forEach(i -> {vector1[i] = Float.floatToFloat16((float)i);});
        IntStream.range(0, vectorDim).forEach(i -> {vector2[i] = Float.floatToFloat16((float)i);});
        IntStream.range(0, vectorDim).forEach(i -> {vector3[i] = Float.floatToFloat16((float)i);});
        IntStream.range(0, vectorDim).forEach(i -> {vector4[i] = ((i & 0x1) == 0) ?
                                                                  float16ToRawShortBits(Float16.POSITIVE_INFINITY) :
                                                                  Float.floatToFloat16((float)i);});
        IntStream.range(0, vectorDim).forEach(i -> {vector5[i] = ((i & 0x1) == 0) ?
                                                                  float16ToRawShortBits(Float16.NaN) :
                                                                  Float.floatToFloat16((float)i);});
        // Special Values
        Float16 [] specialValues = {Float16.NaN, Float16.NEGATIVE_INFINITY, Float16.valueOf(0.0), Float16.valueOf(-0.0), Float16.POSITIVE_INFINITY};
        IntStream.range(0, vectorDim).forEach(
            i -> {
                if ((i % 64) == 0) {
                    int idx1 = i % specialValues.length;
                    int idx2 = (i + 1) % specialValues.length;
                    int idx3 = (i + 2) % specialValues.length;
                    vector1[i] = float16ToRawShortBits(specialValues[idx1]);
                    vector2[i] = float16ToRawShortBits(specialValues[idx2]);
                    vector3[i] = float16ToRawShortBits(specialValues[idx3]);
                }
            }
        );
    }

    @Benchmark
    public void addBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(add(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void subBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(subtract(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void mulBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(multiply(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void divBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(divide(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void fmaBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(fma(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i]), shortBitsToFloat16(vector3[i])));
        }
    }

    @Benchmark
    public boolean isInfiniteBenchmark() {
        boolean res = true;
        for (int i = 0; i < vectorDim; i++) {
            res &= isInfinite(shortBitsToFloat16(vector1[i]));
        }
        return res;
    }

    @Benchmark
    public boolean isFiniteBenchmark() {
        boolean res = true;
        for (int i = 0; i < vectorDim; i++) {
            res &= isFinite(shortBitsToFloat16(vector1[i]));
        }
        return res;
    }

    @Benchmark
    public boolean isNaNBenchmark() {
        boolean res = true;
        for (int i = 0; i < vectorDim; i++) {
            res &= isNaN(shortBitsToFloat16(vector1[i]));
        }
        return res;
    }

    @Benchmark
    public void isNaNStoreBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorPredicate[i] = isNaN(shortBitsToFloat16(vector1[i]));
        }
    }


    @Benchmark
    public void isNaNCMovBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = isNaN(shortBitsToFloat16(vector5[i])) ? vector1[i] : vector2[i];
        }
    }


    @Benchmark
    public void isInfiniteStoreBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorPredicate[i] = isInfinite(shortBitsToFloat16(vector1[i]));
        }
    }


    @Benchmark
    public void isInfiniteCMovBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = isInfinite(shortBitsToFloat16(vector4[i])) ? vector1[i] : vector2[i];
        }
    }


    @Benchmark
    public void isFiniteStoreBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorPredicate[i] = isFinite(shortBitsToFloat16(vector1[i]));
        }
    }


    @Benchmark
    public void isFiniteCMovBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = isFinite(shortBitsToFloat16(vector4[i])) ? vector1[i] : vector2[i];
        }
    }

    @Benchmark
    public void maxBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(max(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void minBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(min(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void sqrtBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(sqrt(shortBitsToFloat16(vector1[i])));
        }
    }

    @Benchmark
    public void negateBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(negate(shortBitsToFloat16(vector1[i])));
        }
    }

    @Benchmark
    public void absBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(abs(shortBitsToFloat16(vector1[i])));
        }
    }

    @Benchmark
    public void getExponentBenchmark() {
        for (int i = 0; i < vectorDim; i++) {
            rexp[i] = getExponent(shortBitsToFloat16(vector1[i]));
        }
    }

    @Benchmark
    public short cosineSimilarityDoubleRoundingFP16() {
        short macRes = floatToFloat16(0.0f);
        short vector1Square = floatToFloat16(0.0f);
        short vector2Square = floatToFloat16(0.0f);
        for (int i = 0; i < vectorDim; i++) {
            // Explicit add and multiply operation ensures double rounding.
            Float16 vec1 = shortBitsToFloat16(vector1[i]);
            Float16 vec2 = shortBitsToFloat16(vector2[i]);
            macRes = float16ToRawShortBits(add(multiply(vec1, vec2), shortBitsToFloat16(macRes)));
            vector1Square = float16ToRawShortBits(add(multiply(vec1, vec1), shortBitsToFloat16(vector1Square)));
            vector2Square = float16ToRawShortBits(add(multiply(vec2, vec2), shortBitsToFloat16(vector2Square)));
        }
        return float16ToRawShortBits(divide(shortBitsToFloat16(macRes), add(shortBitsToFloat16(vector1Square), shortBitsToFloat16(vector2Square))));
    }

    @Benchmark
    public short cosineSimilaritySingleRoundingFP16() {
        short macRes = floatToFloat16(0.0f);
        short vector1Square = floatToFloat16(0.0f);
        short vector2Square = floatToFloat16(0.0f);
        for (int i = 0; i < vectorDim; i++) {
            Float16 vec1 = shortBitsToFloat16(vector1[i]);
            Float16 vec2 = shortBitsToFloat16(vector2[i]);
            macRes = float16ToRawShortBits(fma(vec1, vec2, shortBitsToFloat16(macRes)));
            vector1Square = float16ToRawShortBits(fma(vec1, vec1, shortBitsToFloat16(vector1Square)));
            vector2Square = float16ToRawShortBits(fma(vec2, vec2, shortBitsToFloat16(vector2Square)));
        }
        return float16ToRawShortBits(divide(shortBitsToFloat16(macRes), add(shortBitsToFloat16(vector1Square), shortBitsToFloat16(vector2Square))));
    }

    @Benchmark
    public short cosineSimilarityDequantizedFP16() {
        float macRes = 0.0f;
        float vector1Square = 0.0f;
        float vector2Square = 0.0f;
        for (int i = 0; i < vectorDim; i++) {
            float vec1 = float16ToFloat(vector1[i]);
            float vec2 = float16ToFloat(vector2[i]);
            macRes = Math.fma(vec1, vec2, macRes);
            vector1Square = Math.fma(vec1, vec1, vector1Square);
            vector2Square = Math.fma(vec2, vec2, vector2Square);
        }
        return floatToFloat16(macRes / (vector1Square + vector2Square));
    }

    @Benchmark
    public short euclideanDistanceFP16() {
        short distRes = floatToFloat16(0.0f);
        short squareRes = floatToFloat16(0.0f);
        for (int i = 0; i < vectorDim; i++) {
            squareRes = float16ToRawShortBits(subtract(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
            distRes = float16ToRawShortBits(fma(shortBitsToFloat16(squareRes), shortBitsToFloat16(squareRes), shortBitsToFloat16(distRes)));
        }
        return float16ToRawShortBits(sqrt(shortBitsToFloat16(distRes)));
    }

    @Benchmark
    public short euclideanDistanceDequantizedFP16() {
        float distRes = 0.0f;
        float squareRes = 0.0f;
        for (int i = 0; i < vectorDim; i++) {
            squareRes = float16ToFloat(vector1[i]) - float16ToFloat(vector2[i]);
            distRes = distRes + squareRes * squareRes;
        }
        return float16ToRawShortBits(sqrt(shortBitsToFloat16(floatToFloat16(distRes))));
    }

    @Benchmark
    public short dotProductFP16() {
        short distRes = floatToFloat16(0.0f);
        for (int i = 0; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(multiply(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
        }
        for (int i = 0; i < vectorDim; i++) {
            distRes = float16ToRawShortBits(add(shortBitsToFloat16(vectorRes[i]), shortBitsToFloat16(distRes)));
        }
        return distRes;
    }
}
