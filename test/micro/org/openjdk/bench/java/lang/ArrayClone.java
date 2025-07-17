/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Compare array clone with equivalent System.arraycopy-based routines
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 15, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class ArrayClone {

    @Param({"0", "10", "100", "1000"})
    int size;

    private byte[] bytes;
    private int[] ints;

    @Setup
    public void setup() {
        bytes = new byte[size];
        ints = new int[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        for (int i = 0; i < ints.length; i++) {
            ints[i] = ThreadLocalRandom.current().nextInt();
        }
    }

    @Benchmark
    public byte[] byteClone() {
        return bytes.clone();
    }

    @Benchmark
    public byte[] byteArraycopy() {
        byte[] copy = new byte[bytes.length];
        System.arraycopy(bytes, 0, copy, 0, bytes.length);
        return copy;
    }

    @Benchmark
    public int[] intClone() {
        return ints.clone();
    }

    @Benchmark
    public int[] intArraycopy() {
        int[] copy = new int[ints.length];
        System.arraycopy(ints, 0, copy, 0, ints.length);
        return copy;
    }

}