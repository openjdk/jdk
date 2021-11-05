/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Fork;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.concurrent.TimeUnit;

/**
 * Tests java.util.random.RandomGenerator's different random number generators 
 * which use Math.unsignedMultiplyHigh().
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class RandomGeneratorNext {

    public RandomGenerator rngL128X128MixRandom;
    public RandomGenerator rngL128X256MixRandom;
    public RandomGenerator rngL128X1024MixRandom;
    public static long[] buffer;
    public static final int SIZE = 1024;

    @Setup
    public void setup() {
        buffer = new long[SIZE];
        rngL128X128MixRandom = RandomGeneratorFactory.of("L128X128MixRandom").create();
        rngL128X256MixRandom = RandomGeneratorFactory.of("L128X256MixRandom").create();
        rngL128X1024MixRandom = RandomGeneratorFactory.of("L128X1024MixRandom").create();
    }

    @Benchmark
    public long testL128X128MixRandomNextLong() {
        return rngL128X128MixRandom.nextLong();
    }

    @Benchmark
    public long testL128X256MixRandomNextLong() {
        return rngL128X256MixRandom.nextLong();
    }

    @Benchmark
    public long testL128X1024MixRandomNextLong() {
        return rngL128X1024MixRandom.nextLong();
    }

    @Benchmark
    @Fork(1)
    public void testL128X128MixRandomNextLongLoop() {
        for (int i = 0; i < SIZE; i++) buffer[i] = rngL128X128MixRandom.nextLong();
    }

    @Benchmark
    @Fork(1)
    public void testL128X256MixRandomNextLongLoop() {
        for (int i = 0; i < SIZE; i++) buffer[i] = rngL128X256MixRandom.nextLong();
    }

    @Benchmark
    @Fork(1)
    public void testL128X1024MixRandomNextLongLoop() {
        for (int i = 0; i < SIZE; i++) buffer[i] = rngL128X1024MixRandom.nextLong();
    }

}
