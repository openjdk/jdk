/*
 * Copyright (c) 2025, Red Hat and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Tests speed of adding a series of additions of the same operand.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class SerialAdditions {
    private int a = 0xBADB0BA;
    private long b = 0x900dba51l;

    @Benchmark
    public int addIntsTo02() {
        return a + a; // baseline, still a + a
    }

    @Benchmark
    public int addIntsTo04() {
        return a + a + a + a; // a*4 => a<<2
    }

    @Benchmark
    public int addIntsTo05() {
        return a + a + a + a + a; // a*5 => (a<<2) + a
    }

    @Benchmark
    public int addIntsTo06() {
        return a + a + a + a + a + a; // a*6 => (a<<1) + (a<<2)
    }

    @Benchmark
    public int addIntsTo08() {
        return a + a + a + a + a + a + a + a; // a*8 => a<<3
    }

    @Benchmark
    public int addIntsTo16() {
        return a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a; // a*16 => a<<4
    }

    @Benchmark
    public int addIntsTo23() {
        return a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a + a; // a*23
    }

    @Benchmark
    public int addIntsTo32() {
        return a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a; // a*32 => a<<5
    }

    @Benchmark
    public int addIntsTo42() {
        return a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a; // a*42
    }

    @Benchmark
    public int addIntsTo64() {
        return a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a + a + a + a + a + a + a //
                + a + a + a + a; // 64 * a => a << 6
    }

    @Benchmark
    public void addIntsMixed(Blackhole blackhole) {
        blackhole.consume(addIntsTo02());
        blackhole.consume(addIntsTo04());
        blackhole.consume(addIntsTo05());
        blackhole.consume(addIntsTo06());
        blackhole.consume(addIntsTo08());
        blackhole.consume(addIntsTo16());
        blackhole.consume(addIntsTo23());
        blackhole.consume(addIntsTo32());
        blackhole.consume(addIntsTo42());
        blackhole.consume(addIntsTo64());
    }

    @Benchmark
    public long addLongsTo02() {
        return b + b; // baseline, still a + a
    }

    @Benchmark
    public long addLongsTo04() {
        return b + b + b + b; // a*4 => a<<2
    }

    @Benchmark
    public long addLongsTo05() {
        return b + b + b + b + b; // a*5 => (a<<2) + a
    }

    @Benchmark
    public long addLongsTo06() {
        return b + b + b + b + b + b; // a*6 => (a<<1) + (a<<2)
    }

    @Benchmark
    public long addLongsTo08() {
        return b + b + b + b + b + b + b + b; // a*8 => a<<3
    }

    @Benchmark
    public long addLongsTo16() {
        return b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b; // a*16 => a<<4
    }

    @Benchmark
    public long addLongsTo23() {
        return b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b + b; // a*23
    }

    @Benchmark
    public long addLongsTo32() {
        return b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b; // a*32 => a<<5
    }

    @Benchmark
    public long addLongsTo42() {
        return b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b; // a*42
    }

    @Benchmark
    public long addLongsTo64() {
        return b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b + b + b + b + b + b + b //
                + b + b + b + b; // 64 * a => a << 6
    }

    @Benchmark
    public void addLongsMixed(Blackhole blackhole) {
        blackhole.consume(addLongsTo02());
        blackhole.consume(addLongsTo04());
        blackhole.consume(addLongsTo05());
        blackhole.consume(addLongsTo06());
        blackhole.consume(addLongsTo08());
        blackhole.consume(addLongsTo16());
        blackhole.consume(addLongsTo23());
        blackhole.consume(addLongsTo32());
        blackhole.consume(addLongsTo42());
        blackhole.consume(addLongsTo64());
    }
}
