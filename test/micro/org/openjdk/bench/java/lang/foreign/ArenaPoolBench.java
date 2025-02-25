/*
 *  Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.java.lang.foreign;

import jdk.internal.foreign.CarrierLocalArenaPools;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {
        "--add-exports=java.base/jdk.internal.foreign=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED"})
public class ArenaPoolBench {

    private static final CarrierLocalArenaPools POOLS = CarrierLocalArenaPools.create(32, 8);

    @Param({"4", "64"})
    public int ELEM_SIZE;

    @Benchmark
    public long confined() {
        try (var arena = Arena.ofConfined()) {
            return arena.allocate(ELEM_SIZE).address();
        }
    }

    @Benchmark
    public long pooled() {
        try (var arena = POOLS.take()) {
            return arena.allocate(ELEM_SIZE).address();
        }

    }

    @Benchmark
    public long confined2() {
        long x;
        try (var arena = Arena.ofConfined()) {
            x = arena.allocate(ELEM_SIZE).address();
            try (var arena2 = Arena.ofConfined()) {
                x += arena2.allocate(ELEM_SIZE).address();
            }
        }
        return x;
    }

    @Benchmark
    public long pooled2() {
        long x;
        try (var arena = POOLS.take()) {
            x = arena.allocate(ELEM_SIZE).address();
            try (var arena2 = POOLS.take()) {
                x += arena2.allocate(ELEM_SIZE).address();
            }
        }
        return x;
    }

    @Fork(value = 3, jvmArgsAppend = "-Djmh.executor=VIRTUAL")
    public static class OfVirtual extends ArenaPoolBench {
    }

}
