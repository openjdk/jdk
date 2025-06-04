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
package org.openjdk.bench.java.util.stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigInteger;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Benchmark for checking Pattern::splitAsStream performance
 * with CPU-bound downstream operation in sequential and parallel streams
 *
 * See JDK-8280915
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@State(Scope.Thread)
public class PatternSplit {
    @Param({"10", "100", "1000", "10000"})
    private int size;

    private String input;

    private static final Pattern PATTERN = Pattern.compile(",");

    @Setup
    public void setup() {
        input = new SplittableRandom(1).ints(size, 1000, 2000)
                .mapToObj(String::valueOf).collect(Collectors.joining(","));
    }

    @Benchmark
    public BigInteger sumOf1000thPowers() {
        return PATTERN.splitAsStream(input).map(BigInteger::new).map(v -> v.pow(1000))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    @Benchmark
    public BigInteger sumOf1000thPowersParallel() {
        return PATTERN.splitAsStream(input).parallel().map(BigInteger::new).map(v -> v.pow(1000))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
