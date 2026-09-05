/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.valhalla.sandbox.corelibs;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Measure performance of List of Integer operations.
 * - Set all of int from a set of random numbers (with a seed)
 * - Get all
 * - Shuffle the array
 * - Sort the array
 */


@Fork(value = 3, jvmArgsAppend = "--enable-preview")
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class ArrayListOfIntBench {

    @Param({
            "100",
            "1000000",
    })
    public int size;

    ArrayListInt arrayListInt;
    ArrayListPrimitiveInt arrayListPrimitiveInt;
    ArrayList<Integer> arrayListOfInteger;
    ArrayList<PrimitiveInt> arrayListOfPrimitiveInt;
    Random random;

    @Setup
    public void setup() {
        arrayListInt = new ArrayListInt(size);
        for (int i = 0; i < size; i++) {
            arrayListInt.add(i, i);
        }

        arrayListPrimitiveInt = new ArrayListPrimitiveInt(size);
        for (int i = 0; i < size; i++) {
            arrayListPrimitiveInt.add(i, new PrimitiveInt(i));
        }

        arrayListOfInteger = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            arrayListOfInteger.add(i, i);
        }

        arrayListOfPrimitiveInt = new ArrayList<PrimitiveInt>(size);
        for (int i = 0; i < size; i++) {
            arrayListOfPrimitiveInt.add(i, new PrimitiveInt(i));
        }

        random = new Random(42);
    }

    @Benchmark
    public Object appendListInt() {
        ArrayListInt list = new ArrayListInt(size);
        for (int i = 0; i < size; i++) {
            list.add(i);
        }
        return list;
    }

    @Benchmark
    public Object appendListPrimitiveInt() {
        ArrayListPrimitiveInt list = new ArrayListPrimitiveInt(size);
        for (int i = 0; i < size; i++) {
            list.add(new PrimitiveInt(i));
        }
        return list;
    }

    @Benchmark
    public Object appendListOfInteger() {
        ArrayList<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(i);
        }
        return list;
    }

    @Benchmark
    public Object appendListOfPrimitiveInt() {
        ArrayList<PrimitiveInt> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new PrimitiveInt(i));
        }
        return list;
    }


    @Benchmark
    public int sumListInt() {
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += arrayListInt.get(i);
        }
        return sum;
    }

    @Benchmark
    public int sumListOfInteger() {
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += arrayListOfInteger.get(i);
        }
        return sum;
    }


    @Benchmark
    public int sumListPrimitiveInt() {
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += arrayListPrimitiveInt.get(i).value();
        }
        return sum;
    }

    @Benchmark
    public int sumListOfPrimitiveInt() {
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += arrayListOfPrimitiveInt.get(i).value();
        }
        return sum;
    }

    @Benchmark
    public int thrashListInt() {
        final ArrayListInt list = arrayListInt;

        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            int ndx = (random.nextInt() & 0x7fffffff) % list.size();    // positive
            if (list.size() == size) {
                list.remove(ndx);
            } else {
                list.add(ndx);
            }
            sum += ndx;
        }
        return sum;
    }

    @Benchmark
    public int thrashListPrimitiveInt() {
        final ArrayListPrimitiveInt list = arrayListPrimitiveInt;
        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            int ndx = (random.nextInt() & 0x7fffffff) % list.size();    // positive
            if (list.size() == size) {
                list.remove(ndx);
            } else {
                list.add(ndx, new PrimitiveInt(ndx));
            }
            sum += ndx;
        }
        return sum;
    }

    @Benchmark
    public int thrashListOfInteger() {
        final ArrayList<Integer> list = arrayListOfInteger;
        int sum = 0;
        for (int i = 0; i < 1000; i++) {
            int ndx = (random.nextInt() & 0x7fffffff) % list.size();    // positive
            if (list.size() == size) {
                list.remove(ndx);
            } else {
                list.add(ndx);
            }
            sum += ndx;
        }
        return sum;
    }


    @Benchmark
    public int thrashListOfPrimitiveInt() {
        final ArrayList<PrimitiveInt> list = arrayListOfPrimitiveInt;
        int sum = 0;

        for (int i = 0; i < 1000; i++) {
            int ndx = (random.nextInt() & 0x7fffffff) % list.size();    // positive
            if (list.size() == size) {
                list.remove(ndx);
            } else {
                list.add(ndx, new PrimitiveInt(ndx));
            }
            sum += ndx;
        }
        return sum;
    }
}
