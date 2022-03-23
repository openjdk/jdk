/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public abstract class VectorBitCount {
    @Param({"1024"})
    public int SIZE;

    @Param("0")
    private int seed;
    private RandomGenerator rng = RandomGeneratorFactory.getDefault().create(seed);
    private int[] bufferRandInts;
    private long[] bufferRandLongs;
    private int[] bitCounts;
    @Setup
    public void init() {
       bufferRandInts = new int[SIZE];
       bufferRandLongs = new long[SIZE];
       bitCounts = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            bufferRandInts[i] = rng.nextInt();
            bufferRandLongs[i] = rng.nextLong();
        }
    }

    @Benchmark
    public int[] intBitCount() {
        for (int i = 0; i < SIZE; i++) {
            bitCounts[i] = Integer.bitCount(bufferRandInts[i]);
        }
        return bitCounts;
    }

    @Benchmark
    public int[] longBitCount() {
        for (int i = 0; i < SIZE; i++) {
            bitCounts[i] = Long.bitCount(bufferRandLongs[i]);
        }
        return bitCounts;
    }


    @Fork(value = 1, jvmArgsPrepend = {
        "-XX:+UseSuperWord"
    })
    public static class WithSuperword extends VectorBitCount {

    }

    @Fork(value = 1, jvmArgsPrepend = {
        "-XX:-UseSuperWord"
    })
    public static class NoSuperword extends VectorBitCount {
    }

}

