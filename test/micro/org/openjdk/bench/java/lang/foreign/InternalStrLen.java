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
package org.openjdk.bench.java.lang.foreign;

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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.*;
import static jdk.internal.foreign.StringSupport.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = {"--add-exports=java.base/jdk.internal.foreign=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED", "--enable-preview"})
public class InternalStrLen {

    private MemorySegment singleByteSegment;
    private MemorySegment singleByteSegmentMisaligned;
    private MemorySegment doubleByteSegment;
    private MemorySegment quadByteSegment;

    @Param({"1", "4", "16", "251", "1024"})
    int size;

    @Setup
    public void setup() {
        var arena = Arena.ofAuto();
        singleByteSegment = arena.allocate((size + 1L) * Byte.BYTES);
        singleByteSegmentMisaligned = arena.allocate((size + 1L) * Byte.BYTES);
        doubleByteSegment = arena.allocate((size + 1L) * Short.BYTES);
        quadByteSegment = arena.allocate((size + 1L) * Integer.BYTES);
        Stream.of(singleByteSegment, doubleByteSegment, quadByteSegment)
                .forEach(s -> IntStream.range(0, (int) s.byteSize() - 1)
                        .forEach(i -> s.set(
                                ValueLayout.JAVA_BYTE,
                                i,
                                (byte) ThreadLocalRandom.current().nextInt(1, 254)
                        )));
        singleByteSegment.set(ValueLayout.JAVA_BYTE, singleByteSegment.byteSize() - Byte.BYTES, (byte) 0);
        doubleByteSegment.set(ValueLayout.JAVA_SHORT, doubleByteSegment.byteSize() - Short.BYTES, (short) 0);
        quadByteSegment.set(ValueLayout.JAVA_INT, quadByteSegment.byteSize() - Integer.BYTES, 0);
        singleByteSegmentMisaligned = arena.allocate(singleByteSegment.byteSize() + 1).
                asSlice(1);
        MemorySegment.copy(singleByteSegment, 0, singleByteSegmentMisaligned, 0, singleByteSegment.byteSize());
    }

    @Benchmark
    public int elementSingle() {
        return legacy_strlen_byte(singleByteSegment, 0);
    }

    @Benchmark
    public int elementByteMisaligned() {
        return legacy_strlen_byte(singleByteSegmentMisaligned, 0);
    }

    @Benchmark
    public int elementDouble() {
        return legacy_strlen_short(doubleByteSegment, 0);
    }

    @Benchmark
    public int elementQuad() {
        return legacy_strlen_int(quadByteSegment, 0);
    }

    @Benchmark
    public int chunkedSingle() {
        return chunkedStrlenByte(singleByteSegment, 0);
    }

    @Benchmark
    public int chunkedSingleMisaligned() {
        return chunkedStrlenByte(singleByteSegmentMisaligned, 0);
    }

    @Benchmark
    public int chunkedDouble() {
        return chunkedStrlenShort(doubleByteSegment, 0);
    }

    @Benchmark
    public int changedElementQuad() {
        return strlenInt(quadByteSegment, 0);
    }

    // These are the legacy methods

    private static int legacy_strlen_byte(MemorySegment segment, long start) {
        // iterate until overflow (String can only hold a byte[], whose length can be expressed as an int)
        for (int offset = 0; offset >= 0; offset++) {
            byte curr = segment.get(JAVA_BYTE, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw new IllegalArgumentException("String too large");
    }

    private static int legacy_strlen_short(MemorySegment segment, long start) {
        // iterate until overflow (String can only hold a byte[], whose length can be expressed as an int)
        for (int offset = 0; offset >= 0; offset += 2) {
            short curr = segment.get(JAVA_SHORT, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw new IllegalArgumentException("String too large");
    }

    private static int legacy_strlen_int(MemorySegment segment, long start) {
        // iterate until overflow (String can only hold a byte[], whose length can be expressed as an int)
        for (int offset = 0; offset >= 0; offset += 4) {
            int curr = segment.get(JAVA_INT, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw new IllegalArgumentException("String too large");
    }

}
