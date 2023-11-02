//
// Copyright (c) 2022, Arm Limited. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class LoadMaskedIOOBEBenchmark {
    @Param({"1026"})
    private int inSize;

    @Param({"1152"})
    private int outSize;

    private byte[] byteIn;
    private byte[] byteOut;
    private short[] shortIn;
    private short[] shortOut;
    private int[] intIn;
    private int[] intOut;
    private long[] longIn;
    private long[] longOut;
    private float[] floatIn;
    private float[] floatOut;
    private double[] doubleIn;
    private double[] doubleOut;

    private boolean[] m;

    private static final VectorSpecies<Byte> bspecies = VectorSpecies.ofLargestShape(byte.class);
    private static final VectorSpecies<Short> sspecies = VectorSpecies.ofLargestShape(short.class);
    private static final VectorSpecies<Integer> ispecies = VectorSpecies.ofLargestShape(int.class);
    private static final VectorSpecies<Long> lspecies = VectorSpecies.ofLargestShape(long.class);
    private static final VectorSpecies<Float> fspecies = VectorSpecies.ofLargestShape(float.class);
    private static final VectorSpecies<Double> dspecies = VectorSpecies.ofLargestShape(double.class);

    @Setup(Level.Trial)
    public void Setup() {
        byteIn = new byte[inSize];
        byteOut = new byte[outSize];
        shortIn = new short[inSize];
        shortOut = new short[outSize];
        intIn = new int[inSize];
        intOut = new int[outSize];
        longIn = new long[inSize];
        longOut = new long[outSize];
        floatIn = new float[inSize];
        floatOut = new float[outSize];
        doubleIn = new double[inSize];
        doubleOut = new double[outSize];

        for (int i = 0; i < inSize; i++) {
            byteIn[i] = (byte) i;
            shortIn[i] = (short) i;
            intIn[i] = i;
            longIn[i] = i;
            floatIn[i] = (float) i;
            doubleIn[i] = (double) i;
        }
        m = new boolean[outSize];
        for (int i = 0; i < inSize; i++) {
            m[i] = i % 2 == 0;
        }
    }

    @Benchmark
    public void byteLoadArrayMaskIOOBE() {
        for (int i = 0; i < inSize; i += bspecies.length()) {
            VectorMask<Byte> mask = VectorMask.fromArray(bspecies, m, i);
            ByteVector.fromArray(bspecies, byteIn, i, mask).intoArray(byteOut, i);
        }
    }

    @Benchmark
    public void shortLoadArrayMaskIOOBE() {
        for (int i = 0; i < inSize; i += sspecies.length()) {
            VectorMask<Short> mask = VectorMask.fromArray(sspecies, m, i);
            ShortVector.fromArray(sspecies, shortIn, i, mask).intoArray(shortOut, i);
        }
    }

    @Benchmark
    public void intLoadArrayMaskIOOBE() {
        for (int i = 0; i < inSize; i += ispecies.length()) {
            VectorMask<Integer> mask = VectorMask.fromArray(ispecies, m, i);
            IntVector.fromArray(ispecies, intIn, i, mask).intoArray(intOut, i);
        }
    }

    @Benchmark
    public void longLoadArrayMaskIOOBE() {
        for (int i = 0; i < inSize; i += lspecies.length()) {
            VectorMask<Long> mask = VectorMask.fromArray(lspecies, m, i);
            LongVector.fromArray(lspecies, longIn, i, mask).intoArray(longOut, i);
        }
    }

    @Benchmark
    public void floatLoadArrayMaskIOOBE() {
        for (int i = 0; i < inSize; i += fspecies.length()) {
            VectorMask<Float> mask = VectorMask.fromArray(fspecies, m, i);
            FloatVector.fromArray(fspecies, floatIn, i, mask).intoArray(floatOut, i);
        }
    }

    @Benchmark
    public void doubleLoadArrayMaskIOOBE() {
        for (int i = 0; i < inSize; i += dspecies.length()) {
            VectorMask<Double> mask = VectorMask.fromArray(dspecies, m, i);
            DoubleVector.fromArray(dspecies, doubleIn, i, mask).intoArray(doubleOut, i);
        }
    }
}
