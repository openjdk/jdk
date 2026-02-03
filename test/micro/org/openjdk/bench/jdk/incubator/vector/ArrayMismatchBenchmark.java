/*
 *  Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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


import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class ArrayMismatchBenchmark {

    @Param({"9", "257", "100000"})
    int size;

    @Param({"0.5", "1.0"})
    double prefix;

    byte[] byteData1;
    byte[] byteData2;

    short[] shortData1;
    short[] shortData2;

    int[] intData1;
    int[] intData2;

    long[] longData1;
    long[] longData2;

    double[] doubleData1;
    double[] doubleData2;

    static final VectorSpecies<Byte> BYTE_SPECIES_PREFERRED = ByteVector.SPECIES_PREFERRED;
    static final VectorSpecies<Short> SHORT_SPECIES_PREFERRED = ShortVector.SPECIES_PREFERRED;
    static final VectorSpecies<Integer> INT_SPECIES_PREFERRED = IntVector.SPECIES_PREFERRED;
    static final VectorSpecies<Double> FLOAT_SPECIES_PREFERRED = DoubleVector.SPECIES_PREFERRED;
    static final VectorSpecies<Long> LONG_SPECIES_PREFERRED = LongVector.SPECIES_PREFERRED;

    @Setup
    public void setup(BenchmarkParams params) {
        RandomGenerator random = RandomGenerator.getDefault();
        int common = (int) (prefix * size);

        if (params.getBenchmark().endsWith("Byte")) {
            byteData1 = new byte[size];
            byteData2 = new byte[size];
            random.nextBytes(byteData1);
            random.nextBytes(byteData2);

            byte[] commonBytes = new byte[common];
            random.nextBytes(commonBytes);

            System.arraycopy(commonBytes, 0, byteData1, 0, common);
            System.arraycopy(commonBytes, 0, byteData2, 0, common);
        } else if (params.getBenchmark().endsWith("Short")) {
            shortData1 = new short[size];
            shortData2 = new short[size];
            Arrays.fill(shortData1, (short)random.nextInt());
            Arrays.fill(shortData2, (short)random.nextInt());

            short[] commonShorts = new short[common];
            Arrays.fill(commonShorts, (short)random.nextInt());
            System.arraycopy(commonShorts, 0, shortData1, 0, common);
            System.arraycopy(commonShorts, 0, shortData2, 0, common);
        } else if (params.getBenchmark().endsWith("Int")) {
            intData1 = random.ints(size).toArray();
            intData2 = random.ints(size).toArray();

            int[] commonInts = random.ints(common).toArray();
            System.arraycopy(commonInts, 0, intData1, 0, common);
            System.arraycopy(commonInts, 0, intData2, 0, common);
        } else if (params.getBenchmark().endsWith("Double")) {
            doubleData1 = random.doubles(size).toArray();
            doubleData2 = random.doubles(size).toArray();

            double[] commonDoubles = random.doubles(common).toArray();
            System.arraycopy(commonDoubles, 0, doubleData1, 0, common);
            System.arraycopy(commonDoubles, 0, doubleData2, 0, common);
        } else if (params.getBenchmark().endsWith("Long")) {
            longData1 = random.longs(size).toArray();
            longData2 = random.longs(size).toArray();

            long[] commonLongs = random.longs(common).toArray();
            System.arraycopy(commonLongs, 0, longData1, 0, common);
            System.arraycopy(commonLongs, 0, longData2, 0, common);
        }
    }

    @Benchmark
    public int mismatchIntrinsicByte() {
        return Arrays.mismatch(byteData1, byteData2);
    }

    @Benchmark
    public int mismatchVectorByte() {
        int length = Math.min(byteData1.length, byteData2.length);
        int index = 0;
        for (; index < BYTE_SPECIES_PREFERRED.loopBound(length); index += BYTE_SPECIES_PREFERRED.length()) {
            ByteVector vector1 = ByteVector.fromArray(BYTE_SPECIES_PREFERRED, byteData1, index);
            ByteVector vector2 = ByteVector.fromArray(BYTE_SPECIES_PREFERRED, byteData2, index);
            VectorMask<Byte> mask = vector1.compare(VectorOperators.NE, vector2);
            if (mask.anyTrue()) {
                return index + mask.firstTrue();
            }
        }
        // process the tail
        int mismatch = -1;
        for (int i = index; i < length; ++i) {
            if (byteData1[i] != byteData2[i]) {
                mismatch = i;
                break;
            }
        }
        return mismatch;
    }

    @Benchmark
    public int mismatchIntrinsicShort() {
        return Arrays.mismatch(shortData1, shortData2);
    }

    @Benchmark
    public int mismatchVectorShort() {
        int length = Math.min(shortData1.length, shortData2.length);
        int index = 0;
        for (; index < SHORT_SPECIES_PREFERRED.loopBound(length); index += SHORT_SPECIES_PREFERRED.length()) {
            ShortVector vector1 = ShortVector.fromArray(SHORT_SPECIES_PREFERRED, shortData1, index);
            ShortVector vector2 = ShortVector.fromArray(SHORT_SPECIES_PREFERRED, shortData2, index);
            VectorMask<Short> mask = vector1.compare(VectorOperators.NE, vector2);
            if (mask.anyTrue()) {
                return index + mask.firstTrue();
            }
        }
        // process the tail
        int mismatch = -1;
        for (int i = index; i < length; ++i) {
            if (shortData1[i] != shortData2[i]) {
                mismatch = i;
                break;
            }
        }
        return mismatch;
    }

    @Benchmark
    public int mismatchIntrinsicInt() {
        return Arrays.mismatch(intData1, intData2);
    }

    @Benchmark
    public int mismatchVectorInt() {
        int length = Math.min(intData1.length, intData2.length);
        int index = 0;
        for (; index < INT_SPECIES_PREFERRED.loopBound(length); index += INT_SPECIES_PREFERRED.length()) {
            IntVector vector1 = IntVector.fromArray(INT_SPECIES_PREFERRED, intData1, index);
            IntVector vector2 = IntVector.fromArray(INT_SPECIES_PREFERRED, intData2, index);
            VectorMask<Integer> mask = vector1.compare(VectorOperators.NE, vector2);
            if (mask.anyTrue()) {
                return index + mask.firstTrue();
            }
        }
        // process the tail
        int mismatch = -1;
        for (int i = index; i < length; ++i) {
            if (intData1[i] != intData2[i]) {
                mismatch = i;
                break;
            }
        }
        return mismatch;
    }

    @Benchmark
    public int mismatchIntrinsicDouble() {
        return Arrays.mismatch(doubleData1, doubleData2);
    }

    @Benchmark
    public int mismatchVectorDouble() {
        int length = Math.min(doubleData1.length, doubleData2.length);
        int index = 0;
        for (; index < FLOAT_SPECIES_PREFERRED.loopBound(length); index += FLOAT_SPECIES_PREFERRED.length()) {
            DoubleVector vector1 = DoubleVector.fromArray(FLOAT_SPECIES_PREFERRED, doubleData1, index);
            DoubleVector vector2 = DoubleVector.fromArray(FLOAT_SPECIES_PREFERRED, doubleData2, index);
            VectorMask<Double> mask = vector1.compare(VectorOperators.NE, vector2);
            if (mask.anyTrue()) {
                return index + mask.firstTrue();
            }
        }
        // process the tail
        int mismatch = -1;
        for (int i = index; i < length; ++i) {
            if (doubleData1[i] != doubleData2[i]) {
                mismatch = i;
                break;
            }
        }
        return mismatch;
    }

    @Benchmark
    public int mismatchIntrinsicLong() {
        return Arrays.mismatch(longData1, longData2);
    }

    @Benchmark
    public int mismatchVectorLong() {
        int length = Math.min(longData1.length, longData2.length);
        int index = 0;
        for (; index < LONG_SPECIES_PREFERRED.loopBound(length); index += LONG_SPECIES_PREFERRED.length()) {
            LongVector vector1 = LongVector.fromArray(LONG_SPECIES_PREFERRED, longData1, index);
            LongVector vector2 = LongVector.fromArray(LONG_SPECIES_PREFERRED, longData2, index);
            VectorMask<Long> mask = vector1.compare(VectorOperators.NE, vector2);
            if (mask.anyTrue()) {
                return index + mask.firstTrue();
            }
        }
        // process the tail
        int mismatch = -1;
        for (int i = index; i < length; ++i) {
            if (longData1[i] != longData2[i]) {
                mismatch = i;
                break;
            }
        }
        return mismatch;
    }

}

