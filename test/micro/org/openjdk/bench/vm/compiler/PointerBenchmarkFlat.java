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

package org.openjdk.bench.vm.compiler;

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

import java.util.concurrent.TimeUnit;

@Fork(value = 3)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class PointerBenchmarkFlat {

    static final int ELEM_SIZE = 1_000_000;

    PointerImpl ptr_ptr;
    PointerImplFlat ptr_ptr_flat;

    @Setup
    public void setup() {
        ptr_ptr = new PointerImpl(new FakeSegment(MemoryAddress.NULL, Long.MAX_VALUE));
        ptr_ptr_flat = new PointerImplFlat(new FakeSegmentFlat(MemoryAddress.NULL, Long.MAX_VALUE));
    }

    static class MemoryAddress {
        private long addr;

        public MemoryAddress(long addr) {
            this.addr = addr;
        }

        long toRawLongValue() {
            return addr;
        }

        private static final MemoryAddress NULL = new MemoryAddress(0);

        static MemoryAddress ofLong(long val) {
            return new MemoryAddress(val);
        }
    }

    static class PointerImpl {
        final FakeSegment segment;

        public PointerImpl(FakeSegment segment) {
            this.segment = segment;
        }

        MemoryAddress address() {
            return segment.address();
        }

        PointerImpl get(long index) {
            MemoryAddress address = MemoryAddress.ofLong(index);
            FakeSegment holder = new FakeSegment(address, Long.MAX_VALUE);
            return new PointerImpl(holder);
        }
    }

    static class PointerImplFlat {
        final FakeSegmentFlat segment;

        public PointerImplFlat(FakeSegmentFlat segment) {
            this.segment = segment;
        }

        MemoryAddress address() {
            return segment.address();
        }

        PointerImplFlat get(long index) {
            MemoryAddress address = MemoryAddress.ofLong(index);
            FakeSegmentFlat holder = new FakeSegmentFlat(address, Long.MAX_VALUE);
            return new PointerImplFlat(holder);
        }
    }

    static class AbstractFakeSegment {
        final long size;

        public AbstractFakeSegment(long size) {
            this.size = size;
        }
    }

    static class FakeSegment extends AbstractFakeSegment {
        final MemoryAddress address;

        public FakeSegment(MemoryAddress address, long size) {
            super(size);
            this.address = address;
        }

        MemoryAddress address() {
            return address;
        }
    }

    static class FakeSegmentFlat {
        final MemoryAddress address;
        final long size;

        public FakeSegmentFlat(MemoryAddress address, long size) {
            this.size = size;
            this.address = address;
        }

        MemoryAddress address() {
            return address;
        }
    }

    @Benchmark
    public int test() {
        int sum = 0;
        for (int i = 0 ; i < ELEM_SIZE ; i++) {
            sum += ptr_ptr.get(i).address().toRawLongValue();
        }
        return sum;
    }

    @Benchmark
    public int testFlat() {
        int sum = 0;
        for (int i = 0 ; i < ELEM_SIZE ; i++) {
            sum += ptr_ptr_flat.get(i).address().toRawLongValue();
        }
        return sum;
    }
}
