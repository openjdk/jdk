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
public class Float16VectorOperationsBenchmark {
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

    static final VectorSpecies<Float16> HSPECIES = Float16Vector.SPECIES_PREFERRED;
    static final VectorSpecies<Float> FSPECIES = FloatVector.SPECIES_PREFERRED;

    @Benchmark
    public void addBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i +=  HSPECIES.length()) {
            Float16Vector.fromArray(HSPECIES, vector1, i)
                           .lanewise(VectorOperators.ADD,
                                     Float16Vector.fromArray(HSPECIES, vector2, i))
                           .intoArray(vectorRes, i);
        }
        for (; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(add(shortBitsToFloat16(vector1[i]),
                                                     shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void subBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i +=  HSPECIES.length()) {
            Float16Vector.fromArray(HSPECIES, vector1, i)
                           .lanewise(VectorOperators.SUB,
                                     Float16Vector.fromArray(HSPECIES, vector2, i))
                           .intoArray(vectorRes, i);
        }
        for (; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(subtract(shortBitsToFloat16(vector1[i]),
                                                          shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void mulBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i +=  HSPECIES.length()) {
            Float16Vector.fromArray(HSPECIES, vector1, i)
                           .lanewise(VectorOperators.MUL,
                                     Float16Vector.fromArray(HSPECIES, vector2, i))
                           .intoArray(vectorRes, i);
        }
        for (; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(multiply(shortBitsToFloat16(vector1[i]),
                                                          shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void divBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i +=  HSPECIES.length()) {
            Float16Vector.fromArray(HSPECIES, vector1, i)
                           .lanewise(VectorOperators.DIV,
                                     Float16Vector.fromArray(HSPECIES, vector2, i))
                           .intoArray(vectorRes, i);
        }
        for (; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(divide(shortBitsToFloat16(vector1[i]),
                                                        shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void fmaBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i +=  HSPECIES.length()) {
            Float16Vector.fromArray(HSPECIES, vector1, i)
                           .lanewise(VectorOperators.FMA,
                                     Float16Vector.fromArray(HSPECIES, vector2, i),
                                     Float16Vector.fromArray(HSPECIES, vector3, i))
                           .intoArray(vectorRes, i);
        }
        for (; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(fma(shortBitsToFloat16(vector1[i]),
                                                     shortBitsToFloat16(vector2[i]),
                                                     shortBitsToFloat16(vector3[i])));
        }
    }

    @Benchmark
    public void maxBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i +=  HSPECIES.length()) {
            Float16Vector.fromArray(HSPECIES, vector1, i)
                           .lanewise(VectorOperators.MAX,
                                     Float16Vector.fromArray(HSPECIES, vector2, i))
                           .intoArray(vectorRes, i);
        }
        for (; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(max(shortBitsToFloat16(vector1[i]),
                                                     shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void minBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i +=  HSPECIES.length()) {
            Float16Vector.fromArray(HSPECIES, vector1, i)
                           .lanewise(VectorOperators.MIN,
                                     Float16Vector.fromArray(HSPECIES, vector2, i))
                           .intoArray(vectorRes, i);
        }
        for (; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(min(shortBitsToFloat16(vector1[i]),
                                                     shortBitsToFloat16(vector2[i])));
        }
    }

    @Benchmark
    public void sqrtBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i +=  HSPECIES.length()) {
            Float16Vector.fromArray(HSPECIES, vector1, i)
                           .lanewise(VectorOperators.SQRT)
                           .intoArray(vectorRes, i);
        }
        for (; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(sqrt(shortBitsToFloat16(vector1[i])));
        }
    }

    @Benchmark
    public short cosineSimilarityDoubleRoundingFP16() {
        int i = 0;
        Float16Vector macResVec = Float16Vector.broadcast(HSPECIES, (short)0);
        Float16Vector vector1SquareVec = Float16Vector.broadcast(HSPECIES, (short)0);
        Float16Vector vector2SquareVec = Float16Vector.broadcast(HSPECIES, (short)0);
        // cosine distance = (VEC1 . VEC2) / ||VEC1||.||VEC2||
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            // Explicit add and multiply operation ensures double rounding.
            Float16Vector vec1 = Float16Vector.fromArray(HSPECIES, vector1, i);
            Float16Vector vec2 = Float16Vector.fromArray(HSPECIES, vector2, i);
            macResVec = vec1.lanewise(VectorOperators.MUL, vec2)
                            .lanewise(VectorOperators.ADD, macResVec);
            vector1SquareVec = vec1.lanewise(VectorOperators.MUL, vec1)
                                .lanewise(VectorOperators.ADD, vector1SquareVec);
            vector2SquareVec = vec2.lanewise(VectorOperators.MUL, vec2)
                                .lanewise(VectorOperators.ADD, vector2SquareVec);
        }
        short macRes =  macResVec.lanewise(VectorOperators.DIV,
                                           vector1SquareVec.lanewise(VectorOperators.MUL,
                                                                  vector2SquareVec))
                                 .reduceLanes(VectorOperators.ADD);

        short vector1Square = floatToFloat16(0.0f);
        short vector2Square = floatToFloat16(0.0f);
        for (; i < vectorDim; i++) {
            // Explicit add and multiply operation ensures double rounding.
            Float16 vec1 = shortBitsToFloat16(vector1[i]);
            Float16 vec2 = shortBitsToFloat16(vector2[i]);
            macRes = float16ToRawShortBits(add(multiply(vec1, vec2),
                                               shortBitsToFloat16(macRes)));
            vector1Square = float16ToRawShortBits(add(multiply(vec1, vec1),
                                                      shortBitsToFloat16(vector1Square)));
            vector2Square = float16ToRawShortBits(add(multiply(vec2, vec2),
                                                      shortBitsToFloat16(vector2Square)));
        }
        return float16ToRawShortBits(divide(shortBitsToFloat16(macRes),
                                            multiply(shortBitsToFloat16(vector1Square),
                                                     shortBitsToFloat16(vector2Square))));
    }

    @Benchmark
    public short cosineSimilaritySingleRoundingFP16() {
        int i = 0;
        Float16Vector macResVec = Float16Vector.broadcast(HSPECIES, (short)0);
        Float16Vector vector1SquareVec = Float16Vector.broadcast(HSPECIES, (short)0);
        Float16Vector vector2SquareVec = Float16Vector.broadcast(HSPECIES, (short)0);
        // cosine distance = (VEC1 . VEC2) / ||VEC1||.||VEC2||
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            // Explicit add and multiply operation ensures double rounding.
            Float16Vector vec1 = Float16Vector.fromArray(HSPECIES, vector1, i);
            Float16Vector vec2 = Float16Vector.fromArray(HSPECIES, vector2, i);
            macResVec = vec1.lanewise(VectorOperators.FMA, vec2, macResVec);
            vector1SquareVec = vec1.lanewise(VectorOperators.FMA, vec1, vector1SquareVec);
            vector2SquareVec = vec2.lanewise(VectorOperators.FMA, vec2, vector2SquareVec);
        }
        short macRes =  macResVec.lanewise(VectorOperators.DIV,
                                           vector1SquareVec.lanewise(VectorOperators.MUL,
                                                                     vector2SquareVec))
                                 .reduceLanes(VectorOperators.ADD);

        short vector1Square = floatToFloat16(0.0f);
        short vector2Square = floatToFloat16(0.0f);
        for (; i < vectorDim; i++) {
            // Explicit add and multiply operation ensures double rounding.
            Float16 vec1 = shortBitsToFloat16(vector1[i]);
            Float16 vec2 = shortBitsToFloat16(vector2[i]);
            macRes = float16ToRawShortBits(fma(vec1, vec2,
                                               shortBitsToFloat16(macRes)));
            vector1Square = float16ToRawShortBits(fma(vec1, vec1,
                                                      shortBitsToFloat16(vector1Square)));
            vector2Square = float16ToRawShortBits(fma(vec2, vec2,
                                                      shortBitsToFloat16(vector2Square)));
        }
        return float16ToRawShortBits(divide(shortBitsToFloat16(macRes),
                                            multiply(shortBitsToFloat16(vector1Square),
                                                     shortBitsToFloat16(vector2Square))));
    }

    @Benchmark
    public short euclideanDistanceFP16() {
        Float16Vector resVec = Float16Vector.broadcast(HSPECIES, (short)0);
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            Float16Vector diffVec = Float16Vector.fromArray(HSPECIES, vector1, i)
                                                     .lanewise(VectorOperators.SUB,
                                                               Float16Vector.fromArray(HSPECIES, vector2, i));
            resVec = diffVec.lanewise(VectorOperators.FMA, diffVec, resVec);
        }
        short distRes = resVec.lanewise(VectorOperators.SQRT)
                              .reduceLanes(VectorOperators.ADD);
        short squareRes = floatToFloat16(0.0f);
        for (; i < vectorDim; i++) {
            squareRes = float16ToRawShortBits(subtract(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
            distRes = float16ToRawShortBits(fma(shortBitsToFloat16(squareRes), shortBitsToFloat16(squareRes), shortBitsToFloat16(distRes)));
        }
        return float16ToRawShortBits(sqrt(shortBitsToFloat16(distRes)));
    }

    @Benchmark
    public short dotProductFP16() {
        Float16Vector distResVec = Float16Vector.broadcast(HSPECIES, (short)0);
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            distResVec = Float16Vector.fromArray(HSPECIES, vector1, i)
                                        .lanewise(VectorOperators.FMA,
                                                  Float16Vector.fromArray(HSPECIES, vector2, i),
                                                  distResVec);
        }
        short distRes = distResVec.reduceLanes(VectorOperators.ADD);
        for (; i < vectorDim; i++) {
            vectorRes[i] = float16ToRawShortBits(multiply(shortBitsToFloat16(vector1[i]), shortBitsToFloat16(vector2[i])));
        }
        for (; i < vectorDim; i++) {
            distRes = float16ToRawShortBits(add(shortBitsToFloat16(vectorRes[i]), shortBitsToFloat16(distRes)));
        }
        return distRes;
    }
}
