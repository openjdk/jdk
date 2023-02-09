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
package org.openjdk.bench.java.time;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Examine ZoneOffset.ofTotalSeconds operations
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Benchmark)
public class ZoneOffsetBench {

    // An array with all the cached values [-18h, 18h]
    private static final int CACHED_SECONDS[] = IntStream.range(-18, 18)
            // Convert hour to seconds
            .map(h -> h * 3_600)
            // Include each even quarter of an hour
            .flatMap(s -> IntStream.iterate(s, l -> l + 3_600 / 4).limit(4 + 1))
            // There some overlaps so remove these
            .distinct()
            .sorted()
            .toArray();

    @Benchmark
    public void getFromCache(Blackhole bh) {
        for (int s : CACHED_SECONDS) {
            ZoneOffset zo = ZoneOffset.ofTotalSeconds(s);
            bh.consume(zo.getTotalSeconds());
        }
    }
}
