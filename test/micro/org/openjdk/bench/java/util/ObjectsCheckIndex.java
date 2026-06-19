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
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(value = 5)
public class ObjectsCheckIndex {

    @Param({"256", "1024", "4096"})
    int size;

    int[] array;
    int from;
    int to;
    int subSize;

    public static int unintrinsifiedCheckIndex(int index, int length) {
        if (index < 0 || index >= length)
            throw new IndexOutOfBoundsException("oob");
        return index;
    }

    public static int unintrinsifiedCheckFromToIndex(int fromIndex, int toIndex, int length) {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length)
            throw new IndexOutOfBoundsException("oob");
        return fromIndex;
    }

    public static int unintrinsifiedCheckFromIndexSize(int fromIndex, int size, int length) {
        if ((length | fromIndex | size) < 0 || size > length - fromIndex)
            throw new IndexOutOfBoundsException("oob");
        return fromIndex;
    }

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

    // ==== sum_*(): one check outside the loop ====

    // No-check baseline: bare loop with no bounds validation before it.
    @Benchmark
    public void sum_noCheck(Blackhole bh) {
        for (int i = from; i < to; i++) {
            bh.consume(array[i]);
        }
    }

    // ---- checkFromToIndex ----
    @Benchmark
    public void sum_checkFromToIndex(Blackhole bh) {
        Objects.checkFromToIndex(from, to, array.length);
        for (int i = from; i < to; i++) {
            bh.consume(array[i]);
        }
    }

    @Benchmark
    public void sum_checkFromToIndex_unintrinsified(Blackhole bh) {
        unintrinsifiedCheckFromToIndex(from, to, array.length);
        for (int i = from; i < to; i++) {
            bh.consume(array[i]);
        }
    }

    // ---- checkFromIndexSize ----
    @Benchmark
    public void sum_checkFromIndexSize(Blackhole bh) {
        Objects.checkFromIndexSize(from, subSize, array.length);
        for (int i = from; i < from + subSize; i++) {
            bh.consume(array[i]);
        }
    }

    @Benchmark
    public void sum_checkFromIndexSize_unintrinsified(Blackhole bh) {
        unintrinsifiedCheckFromIndexSize(from, subSize, array.length);
        for (int i = from; i < from + subSize; i++) {
            bh.consume(array[i]);
        }
    }

    // ===== perIteration_*(): check on every iteration inside a loop =====

    // No-check baseline
    @Benchmark
    public void perIteration_noCheck(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            bh.consume(array[i]);
        }
    }

    // ---- checkIndex ----
    @Benchmark
    public void perIteration_checkIndex(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            Objects.checkIndex(i, len);
            bh.consume(array[i]);
        }
    }

    @Benchmark
    public void perIteration_checkIndex_unintrinsified(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            unintrinsifiedCheckIndex(i, len);
            bh.consume(array[i]);
        }
    }

    // ---- checkFromToIndex ----
    @Benchmark
    public void perIteration_checkFromToIndex(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            Objects.checkFromToIndex(i, i + 1, len);
            bh.consume(array[i]);
        }
    }

    @Benchmark
    public void perIteration_checkFromToIndex_unintrinsified(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            unintrinsifiedCheckFromToIndex(i, i + 1, len);
            bh.consume(array[i]);
        }
    }

    // ---- checkFromIndexSize ----
    @Benchmark
    public void perIteration_checkFromIndexSize(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            Objects.checkFromIndexSize(i, 1, len);
            bh.consume(array[i]);
        }
    }

    @Benchmark
    public void perIteration_checkFromIndexSize_unintrinsified(Blackhole bh) {
        int len = array.length;
        for (int i = from; i < to; i++) {
            unintrinsifiedCheckFromIndexSize(i, 1, len);
            bh.consume(array[i]);
        }
    }

    // ===== Array copy =====

    int[] dst;

    @Setup
    public void setupDst() {
        dst = new int[size];
    }


    // No-check baseline
    @Benchmark
    public void arrayCopy_noCheck(Blackhole bh) {
        for (int i = from; i < to; i++) {
            dst[i] = array[i];
        }
        bh.consume(dst); // technically not needed, but doesn't hurt either
    }

    @Benchmark
    public void arrayCopy_checkFromToIndex(Blackhole bh) {
        Objects.checkFromToIndex(from, to, array.length);
        Objects.checkFromToIndex(from, to, dst.length);
        for (int i = from; i < to; i++) {
            dst[i] = array[i];
        }
        bh.consume(dst);
    }

    @Benchmark
    public void arrayCopy_checkFromToIndex_unintrinsified(Blackhole bh) {
        unintrinsifiedCheckFromToIndex(from, to, array.length);
        unintrinsifiedCheckFromToIndex(from, to, dst.length);
        for (int i = from; i < to; i++) {
            dst[i] = array[i];
        }
        bh.consume(dst);
    }

    @Benchmark
    public void arrayCopy_checkFromIndexSize(Blackhole bh) {
        Objects.checkFromIndexSize(from, subSize, array.length);
        Objects.checkFromIndexSize(from, subSize, dst.length);
        for (int i = from; i < to; i++) {
            dst[i] = array[i];
        }
        bh.consume(dst);
    }

    @Benchmark
    public void arrayCopy_checkFromIndexSize_unintrinsified(Blackhole bh) {
        unintrinsifiedCheckFromIndexSize(from, subSize, array.length);
        unintrinsifiedCheckFromIndexSize(from, subSize, dst.length);
        for (int i = from; i < to; i++) {
            dst[i] = array[i];
        }
        bh.consume(dst);
    }
}
