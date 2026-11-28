/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring large byte array copies, where the arraycopy stub may
 * use non-temporal stores. The destination is rotated through a small pool so
 * each copy writes a buffer that has been evicted from cache.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1)
public class ArrayCopyNonTemporal {

    private static final int DST_COUNT = 2;

    @Param({"8388608", "16777216", "33554432", "67108864"})
    int length;

    byte[] src;
    byte[][] dsts;
    int index;

    @Setup
    public void setup() {
        src = new byte[length];
        dsts = new byte[DST_COUNT][];
        for (int i = 0; i < DST_COUNT; i++) {
            dsts[i] = new byte[length];
        }
        // Large copies run too few times per second for the benchmark methods
        // to be compiled, leaving System.arraycopy interpreted instead of
        // intrinsified to the stub. Compile them with small copies first.
        byte[] smallSrc = src;
        byte[][] smallDsts = dsts;
        int smallLength = length;
        src = new byte[1024];
        dsts = new byte[][] { new byte[1024], new byte[1024] };
        length = 1024;
        for (int i = 0; i < 100_000; i++) {
            arraycopy();
            arraycopyThenRead();
        }
        src = smallSrc;
        dsts = smallDsts;
        length = smallLength;
        index = 0;
    }

    @Benchmark
    public void arraycopy() {
        System.arraycopy(src, 0, dsts[index], 0, length);
        if (++index == DST_COUNT) {
            index = 0;
        }
    }

    /**
     * Copies and then reads the whole destination: non-temporal stores leave
     * it out of cache, so this measures the cost when the copy result is used
     * immediately.
     */
    @Benchmark
    public long arraycopyThenRead() {
        byte[] dst = dsts[index];
        System.arraycopy(src, 0, dst, 0, length);
        if (++index == DST_COUNT) {
            index = 0;
        }
        long sum = 0;
        for (int i = 0; i < length; i++) {
            sum += dst[i];
        }
        return sum;
    }
}
