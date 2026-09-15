/*
 *  Copyright (c) 2026, Google LLC. All rights reserved.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.jdk.incubator.vector;

import static jdk.incubator.vector.VectorOperators.*;
import static jdk.incubator.vector.VectorOperators.EQ;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
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
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Compares vector and scalar loops over a Latin-1 String. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
        value = 3,
        jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class StringLoadBenchmark {
    static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;

    static final byte NEEDLE = (byte) 'a';

    @Param({"16", "32", "64", "128", "1024"})
    private int size;

    private String text;
    private byte[] textBytes;
    private MemorySegment heapSegment;
    private MemorySegment nativeSegment;

    @Setup
    public void setup() {
        System.out.println("# SPECIES: " + SPECIES + " (" + SPECIES.length() + " lanes)");
        Random random = new Random(42);
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) random.nextInt(256));
        }
        text = sb.toString();
        textBytes = text.getBytes(StandardCharsets.ISO_8859_1);
        heapSegment = MemorySegment.ofArray(textBytes).asReadOnly();
        nativeSegment = Arena.ofAuto().allocate(size, 1);
        MemorySegment.copy(textBytes, 0, nativeSegment, ValueLayout.JAVA_BYTE, 0, size);
    }

    @Benchmark
    public int scalar() {
        return countScalar(text, (char) (NEEDLE & 0xFF), size);
    }

    @Benchmark
    public int getBytesFromArray() {
        return countArray(text.getBytes(StandardCharsets.ISO_8859_1), NEEDLE, size);
    }

    @Benchmark
    public int fromString() {
        return countString(text, NEEDLE, size);
    }

    @Benchmark
    public int segment() {
        return countSegment(heapSegment, NEEDLE, size);
    }

    @Benchmark
    public int fromArray() {
        return countArray(textBytes, NEEDLE, size);
    }

    @Benchmark
    public int monomorphic() {
        return countSegmentNotInlined(heapSegment, NEEDLE, size)
                + countSegmentNotInlined(heapSegment, NEEDLE, size);
    }

    @Benchmark
    public int polluted() {
        return countSegmentNotInlined(heapSegment, NEEDLE, size)
                + countSegmentNotInlined(nativeSegment, NEEDLE, size);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private int countSegmentNotInlined(MemorySegment segment, byte needle, int len) {
        return countSegment(segment, needle, len);
    }

    private static int countScalar(String s, char needle, int len) {
        int count = 0;
        for (int i = 0; i < len; i++) {
            if (s.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    private static int countArray(byte[] bytes, byte needle, int len) {
        int count = 0;
        int i = 0;
        for (; i < SPECIES.loopBound(len); i += SPECIES.length()) {
            count += ByteVector.fromArray(SPECIES, bytes, i).compare(EQ, needle).trueCount();
        }
        for (; i < len; i++) {
            if (bytes[i] == needle) {
                count++;
            }
        }
        return count;
    }

    private static int countString(String s, byte needle, int len) {
        int count = 0;
        int i = 0;
        for (; i < SPECIES.loopBound(len); i += SPECIES.length()) {
            count +=
                    ByteVector.fromString(SPECIES, s, StandardCharsets.ISO_8859_1, i)
                            .compare(EQ, needle)
                            .trueCount();
        }
        for (; i < len; i++) {
            if ((byte) s.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    private static int countSegment(MemorySegment segment, byte needle, int len) {
        int count = 0;
        int i = 0;
        for (; i < SPECIES.loopBound(len); i += SPECIES.length()) {
            count +=
                    ByteVector.fromMemorySegment(SPECIES, segment, i, ByteOrder.nativeOrder())
                            .compare(EQ, needle)
                            .trueCount();
        }
        for (; i < len; i++) {
            if (segment.get(ValueLayout.JAVA_BYTE, i) == needle) {
                count++;
            }
        }
        return count;
    }
}
