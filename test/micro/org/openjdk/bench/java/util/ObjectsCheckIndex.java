/*
 * Copyright (c) 2026, IBM and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 4, time = 2)
@Fork(value = 3)
public class ObjectsCheckIndex {

    @Param({"256", "1024", "4096"})
    int size;

    int[] array;
    int from;
    int to;
    int subSize;

    @Setup
    public void setup() {
        array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = i;
        }
        from = size / 4; // rather arbitrary indices
        to = 3 * size / 4;
        subSize = to - from;
    }

    // No-check baseline: bare loop with no bounds validation before it. If RCE from the intrinsic works, the
    // checkFromToIndex variants should match this speed.
    @Benchmark
    public void noCheck_sum(Blackhole bh) {
        for (int i = from; i < to; i++) {
            bh.consume(array[i]);
        }
    }

    // ===== checkFromToIndex =====
    @Benchmark
    public void checkFromToIndex_sum(Blackhole bh) {
        Objects.checkFromToIndex(from, to, array.length);
        for (int i = from; i < to; i++) {
            bh.consume(array[i]);
        }
    }

    @Benchmark
    public void manual_checkFromToIndex_sum(Blackhole bh) {
        if (from < 0 || from > to || to > array.length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = from; i < to; i++) {
            bh.consume(array[i]);
        }
    }

    // ===== checkFromIndexSize =====

    @Benchmark
    public void checkFromIndexSize_sum(Blackhole bh) {
        Objects.checkFromIndexSize(from, subSize, array.length);
        for (int i = from; i < from + subSize; i++) {
            bh.consume(array[i]);
        }
    }

    @Benchmark
    public void manual_checkFromIndexSize_sum(Blackhole bh) {
        if (from < 0 || subSize < 0 || from + subSize > array.length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = from; i < from + subSize; i++) {
            bh.consume(array[i]);
        }
    }

    // ===== Per-iteration checks =====

    @Benchmark
    public void checkIndex_loop(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            Objects.checkIndex(i, len);
            bh.consume(array[i]);
        }
    }

    @Benchmark
    public void checkFromToIndex_perIteration(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            Objects.checkFromToIndex(i, i + 1, len);
            bh.consume(array[i]);
        }
    }

    @Benchmark
    public void checkFromIndexSize_perIteration(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            Objects.checkFromIndexSize(i, 1, len);
            bh.consume(array[i]);
        }
    }

    // ===== Array copy =====

    int[] dst;

    @Setup
    public void setupDst() {
        dst = new int[size];
    }

    @Benchmark
    public void checkFromToIndex_arrayCopy(Blackhole bh) {
        Objects.checkFromToIndex(from, to, array.length);
        Objects.checkFromToIndex(from, to, dst.length);
        for (int i = from; i < to; i++) {
            dst[i] = array[i];
        }
        bh.consume(dst); // technically not needed, but doesn't hurt either
    }

    @Benchmark
    public void manual_arrayCopy(Blackhole bh) {
        if (from < 0 || from > to || to > array.length) {
            throw new IndexOutOfBoundsException();
        }
        if (from < 0 || from > to || to > dst.length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = from; i < to; i++) {
            dst[i] = array[i];
        }
        bh.consume(dst); // same
    }
}
