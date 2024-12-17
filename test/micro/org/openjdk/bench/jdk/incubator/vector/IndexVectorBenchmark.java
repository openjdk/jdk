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
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class IndexVectorBenchmark {
    @Param({"1024"})
    private int size;

    private byte[] ba;
    private short[] sa;
    private int[] ia;
    private long[] la;
    private float[] fa;
    private double[] da;

    private static final VectorSpecies<Byte> bspecies = VectorSpecies.ofLargestShape(byte.class);
    private static final VectorSpecies<Short> sspecies = VectorSpecies.ofLargestShape(short.class);
    private static final VectorSpecies<Integer> ispecies = VectorSpecies.ofLargestShape(int.class);
    private static final VectorSpecies<Long> lspecies = VectorSpecies.ofLargestShape(long.class);
    private static final VectorSpecies<Float> fspecies = VectorSpecies.ofLargestShape(float.class);
    private static final VectorSpecies<Double> dspecies = VectorSpecies.ofLargestShape(double.class);

    @Setup(Level.Trial)
    public void Setup() {
        ba = new byte[size];
        sa = new short[size];
        ia = new int[size];
        la = new long[size];
        fa = new float[size];
        da = new double[size];
    }

    @Benchmark
    public void byteIndexVector() {
        for (int i = 0; i < size; i += bspecies.length()) {
            ((ByteVector) bspecies.broadcast(0).addIndex(i % 2)).intoArray(ba, i);
        }
    }

    @Benchmark
    public void shortIndexVector() {
        for (int i = 0; i < size; i += sspecies.length()) {
            ((ShortVector) sspecies.broadcast(0).addIndex(i % 5)).intoArray(sa, i);
        }
    }

    @Benchmark
    public void intIndexVector() {
        for (int i = 0; i < size; i += ispecies.length()) {
            ((IntVector) ispecies.broadcast(0).addIndex(i % 5)).intoArray(ia, i);
        }
    }

    @Benchmark
    public void longIndexVector() {
        for (int i = 0; i < size; i += lspecies.length()) {
            ((LongVector) lspecies.broadcast(0).addIndex(i % 5)).intoArray(la, i);
        }
    }

    @Benchmark
    public void floatIndexVector() {
        for (int i = 0; i < size; i += fspecies.length()) {
            ((FloatVector) fspecies.broadcast(0).addIndex(i % 5)).intoArray(fa, i);
        }
    }

    @Benchmark
    public void doubleIndexVector() {
        for (int i = 0; i < size; i += dspecies.length()) {
            ((DoubleVector) dspecies.broadcast(0).addIndex(i % 5)).intoArray(da, i);
        }
    }
}
