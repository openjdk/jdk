//
// Copyright (c) 2023, Arm Limited. All rights reserved.
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
public class IndexInRangeBenchmark {
    @Param({"7", "256", "259", "512"})
    private int size;

    private boolean[] mask;

    private static final VectorSpecies<Byte> bspecies = VectorSpecies.ofLargestShape(byte.class);
    private static final VectorSpecies<Short> sspecies = VectorSpecies.ofLargestShape(short.class);
    private static final VectorSpecies<Integer> ispecies = VectorSpecies.ofLargestShape(int.class);
    private static final VectorSpecies<Long> lspecies = VectorSpecies.ofLargestShape(long.class);
    private static final VectorSpecies<Float> fspecies = VectorSpecies.ofLargestShape(float.class);
    private static final VectorSpecies<Double> dspecies = VectorSpecies.ofLargestShape(double.class);

    @Setup(Level.Trial)
    public void Setup() {
        mask = new boolean[512];
    }

    @Benchmark
    public void byteIndexInRange() {
        for (int i = 0; i < size; i += bspecies.length()) {
            var m = bspecies.indexInRange(i, size);
            m.intoArray(mask, i);
        }
    }

    @Benchmark
    public void shortIndexInRange() {
        for (int i = 0; i < size; i += sspecies.length()) {
            var m = sspecies.indexInRange(i, size);
            m.intoArray(mask, i);
        }
    }

    @Benchmark
    public void intIndexInRange() {
        for (int i = 0; i < size; i += ispecies.length()) {
            var m = ispecies.indexInRange(i, size);
            m.intoArray(mask, i);
        }
    }

    @Benchmark
    public void longIndexInRange() {
        for (int i = 0; i < size; i += lspecies.length()) {
            var m = lspecies.indexInRange(i, size);
            m.intoArray(mask, i);
        }
    }

    @Benchmark
    public void floatIndexInRange() {
        for (int i = 0; i < size; i += fspecies.length()) {
            var m = fspecies.indexInRange(i, size);
            m.intoArray(mask, i);
        }
    }

    @Benchmark
    public void doubleIndexInRange() {
        for (int i = 0; i < size; i += dspecies.length()) {
            var m = dspecies.indexInRange(i, size);
            m.intoArray(mask, i);
        }
    }
}
