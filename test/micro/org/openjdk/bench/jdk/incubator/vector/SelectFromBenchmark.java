/*
 *  Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class SelectFromBenchmark {
    @Param({"1024","2048"})
    int size;

    byte[] byteindex;
    byte[] bytesrc1;
    byte[] bytesrc2;
    byte[] byteres;

    short[] shortindex;
    short[] shortsrc1;
    short[] shortsrc2;
    short[] shortres;

    int[] intindex;
    int[] intsrc1;
    int[] intsrc2;
    int[] intres;

    long[] longindex;
    long[] longsrc1;
    long[] longsrc2;
    long[] longres;

    float[] floatindex;
    float[] floatsrc1;
    float[] floatsrc2;
    float[] floatres;

    double[] doubleindex;
    double[] doublesrc1;
    double[] doublesrc2;
    double[] doubleres;

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(1024);
        byteindex = new byte[size];
        bytesrc1 = new byte[size];
        bytesrc2 = new byte[size];
        byteres = new byte[size];

        shortindex = new short[size];
        shortsrc1 = new short[size];
        shortsrc2 = new short[size];
        shortres = new short[size];

        intindex = new int[size];
        intsrc1 = new int[size];
        intsrc2 = new int[size];
        intres = new int[size];

        longindex = new long[size];
        longsrc1 = new long[size];
        longsrc2 = new long[size];
        longres = new long[size];

        floatindex = new float[size];
        floatsrc1 = new float[size];
        floatsrc2 = new float[size];
        floatres = new float[size];

        doubleindex = new double[size];
        doublesrc1 = new double[size];
        doublesrc2 = new double[size];
        doubleres = new double[size];

        Arrays.fill(bytesrc1, (byte)1);
        Arrays.fill(bytesrc2, (byte)2);

        Arrays.fill(shortsrc1, (short)1);
        Arrays.fill(shortsrc2, (short)2);

        Arrays.fill(intsrc1, 1);
        Arrays.fill(intsrc2, 2);

        Arrays.fill(longsrc1, 1);
        Arrays.fill(longsrc2, 2);

        Arrays.fill(floatsrc1, 1.0f);
        Arrays.fill(floatsrc2, 2.0f);

        Arrays.fill(doublesrc1, 1.0);
        Arrays.fill(doublesrc2, 2.0);

        for (int i = 0; i < size; i++) {
            byteindex[i] = (byte)((ByteVector.SPECIES_PREFERRED.length() - 1) & i);
            shortindex[i] = (short)((ShortVector.SPECIES_PREFERRED.length() - 1) & i);
            intindex[i] = (int)((IntVector.SPECIES_PREFERRED.length() - 1) & i);
            longindex[i] = (long)((LongVector.SPECIES_PREFERRED.length() - 1) & i);
            floatindex[i] = (float)((FloatVector.SPECIES_PREFERRED.length() - 1) & i);
            doubleindex[i] = (double)((DoubleVector.SPECIES_PREFERRED.length() - 1) & i);
        }
    }

    @Benchmark
    public void selectFromByteVector() {
        for (int j = 0; j < size; j += ByteVector.SPECIES_PREFERRED.length()) {
            ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, byteindex, j)
                .selectFrom(ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, bytesrc1, j),
                            ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, bytesrc2, j))
                .intoArray(byteres, j);
        }
    }

    @Benchmark
    public void rearrangeFromByteVector() {
        for (int j = 0; j < size; j += ByteVector.SPECIES_PREFERRED.length()) {
            ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, bytesrc1, j)
                .rearrange(ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, byteindex, j).toShuffle(),
                           ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, bytesrc2, j))
                .intoArray(byteres, j);
        }
    }

    @Benchmark
    public void selectFromShortVector() {
        for (int j = 0; j < size; j += ShortVector.SPECIES_PREFERRED.length()) {
            ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, shortindex, j)
                .selectFrom(ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, shortsrc1, j),
                            ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, shortsrc2, j))
                .intoArray(shortres, j);
        }
    }

    @Benchmark
    public void rearrangeFromShortVector() {
        for (int j = 0; j < size; j += ShortVector.SPECIES_PREFERRED.length()) {
            ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, shortsrc1, j)
                .rearrange(ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, shortindex, j).toShuffle(),
                           ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, shortsrc2, j))
                .intoArray(shortres, j);
        }
    }

    @Benchmark
    public void selectFromIntVector() {
        for (int j = 0; j < size; j += IntVector.SPECIES_PREFERRED.length()) {
            IntVector.fromArray(IntVector.SPECIES_PREFERRED, intindex, j)
                .selectFrom(IntVector.fromArray(IntVector.SPECIES_PREFERRED, intsrc1, j),
                            IntVector.fromArray(IntVector.SPECIES_PREFERRED, intsrc2, j))
                .intoArray(intres, j);
        }
    }

    @Benchmark
    public void rearrangeFromIntVector() {
        for (int j = 0; j < size; j += IntVector.SPECIES_PREFERRED.length()) {
            IntVector.fromArray(IntVector.SPECIES_PREFERRED, intsrc1, j)
                .rearrange(IntVector.fromArray(IntVector.SPECIES_PREFERRED, intindex, j).toShuffle(),
                           IntVector.fromArray(IntVector.SPECIES_PREFERRED, intsrc2, j))
                .intoArray(intres, j);
        }
    }

    @Benchmark
    public void selectFromLongVector() {
        for (int j = 0; j < size; j += LongVector.SPECIES_PREFERRED.length()) {
            LongVector.fromArray(LongVector.SPECIES_PREFERRED, longindex, j)
                .selectFrom(LongVector.fromArray(LongVector.SPECIES_PREFERRED, longsrc1, j),
                            LongVector.fromArray(LongVector.SPECIES_PREFERRED, longsrc2, j))
                .intoArray(longres, j);
        }
    }

    @Benchmark
    public void rearrangeFromLongVector() {
        for (int j = 0; j < size; j += LongVector.SPECIES_PREFERRED.length()) {
            LongVector.fromArray(LongVector.SPECIES_PREFERRED, longsrc1, j)
                .rearrange(LongVector.fromArray(LongVector.SPECIES_PREFERRED, longindex, j).toShuffle(),
                           LongVector.fromArray(LongVector.SPECIES_PREFERRED, longsrc2, j))
                .intoArray(longres, j);
        }
    }

    @Benchmark
    public void selectFromFloatVector() {
        for (int j = 0; j < size; j += FloatVector.SPECIES_PREFERRED.length()) {
            FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, floatindex, j)
                .selectFrom(FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, floatsrc1, j),
                            FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, floatsrc2, j))
                .intoArray(floatres, j);
        }
    }

    @Benchmark
    public void rearrangeFromFloatVector() {
        for (int j = 0; j < size; j += FloatVector.SPECIES_PREFERRED.length()) {
            FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, floatsrc1, j)
                .rearrange(FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, floatindex, j).toShuffle(),
                           FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, floatsrc2, j))
                .intoArray(floatres, j);
        }
    }

    @Benchmark
    public void selectFromDoubleVector() {
        for (int j = 0; j < size; j += DoubleVector.SPECIES_PREFERRED.length()) {
            DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, doubleindex, j)
                .selectFrom(DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, doublesrc1, j),
                            DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, doublesrc2, j))
                .intoArray(doubleres, j);
        }
    }

    @Benchmark
    public void rearrangeFromDoubleVector() {
        for (int j = 0; j < size; j += DoubleVector.SPECIES_PREFERRED.length()) {
            DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, doublesrc1, j)
                .rearrange(DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, doubleindex, j).toShuffle(),
                           DoubleVector.fromArray(DoubleVector.SPECIES_PREFERRED, doublesrc2, j))
                .intoArray(doubleres, j);
        }
    }
}
