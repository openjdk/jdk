/*
 *  Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import jdk.internal.util.Architecture;
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

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(jvmArgsAppend = {
        // Assembler stuff
/*        "-Xbatch", "-XX:-TieredCompilation","-XX:CompileCommand=dontinline,org.openjdk.bench.java.lang.foreign.jmh_generated.HeapCopyTest_vector_jmhTest::vector_avgt_jmhStub*",
        "-XX:CompileCommand=PrintAssembly,org.openjdk.bench.java.lang.foreign.jmh_generated.HeapCopyTest_vector_jmhTest::vector_avgt_jmhStub*",*/

        "--enable-preview", "--add-modules=jdk.incubator.vector", "--add-exports=java.base/jdk.internal.util=ALL-UNNAMED"}, value = 3)
public class HeapCopyTest2 {

    private static final VectorSpecies<Byte> SPECIES256 = vectorSpeciesOrNull(VectorShape.S_256_BIT);

    @Param({"0", "1", "2", "3", "4", "5", "6", "7", "8",
            "9", "10", "11", "12", "13", "14", "15", "16",
            "17", "18", "19", "20", "21", "22", "23", "24",
            "25", "26", "27", "28", "29", "30", "31", "32",
            "33"})
    public int ELEM_SIZE;

    byte[] srcArray;
    byte[] dstArray;
    MemorySegment srcSegment;
    MemorySegment dstSegment;
    ByteBuffer srcBuffer;
    ByteBuffer dstBuffer;

    @Setup
    public void setup() {
        srcArray = new byte[ELEM_SIZE];
        dstArray = new byte[ELEM_SIZE];
        srcSegment = MemorySegment.ofArray(srcArray);
        dstSegment = MemorySegment.ofArray(dstArray);
        srcBuffer = ByteBuffer.wrap(srcArray);
        dstBuffer = ByteBuffer.wrap(dstArray);
    }

    @Benchmark
    public void segment_copy() {
        MemorySegment.copy(srcSegment, 0, dstSegment, 0, ELEM_SIZE);
    }

    @Benchmark
    public void segment_staged() {
        switch (ELEM_SIZE) {
            case 0: return;
            case 1: {
                dstSegment.set(JAVA_BYTE, 0, srcSegment.get(JAVA_BYTE, 0));
                return;
            }
            case 2: {
                dstSegment.set(JAVA_SHORT_UNALIGNED, 0, srcSegment.get(JAVA_SHORT_UNALIGNED, 0));
                return;
            }
            case 3: {
                dstSegment.set(JAVA_SHORT_UNALIGNED, 0, srcSegment.get(JAVA_SHORT_UNALIGNED, 0));
                dstSegment.set(JAVA_BYTE, 2, srcSegment.get(JAVA_BYTE, 2));
                return;
            }
            case 4: {
                dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                return;
            }
            case 5: {
                dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                dstSegment.set(JAVA_BYTE, 4, srcSegment.get(JAVA_BYTE, 4));
                return;
            }
            case 6: {
                dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 4, srcSegment.get(JAVA_SHORT_UNALIGNED, 4));
                return;
            }
            case 7: {
                dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 4, srcSegment.get(JAVA_SHORT_UNALIGNED, 4));
                dstSegment.set(JAVA_BYTE, 6, srcSegment.get(JAVA_BYTE, 6));
                return;
            }
            case 8: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                return;
            }
            case 9: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_BYTE, 8, srcSegment.get(JAVA_BYTE, 8));
                return;
            }
            case 10: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 8, srcSegment.get(JAVA_SHORT_UNALIGNED, 8));
                return;
            }
            case 11: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 8, srcSegment.get(JAVA_SHORT_UNALIGNED, 8));
                dstSegment.set(JAVA_BYTE, 10, srcSegment.get(JAVA_BYTE, 10));
                return;
            }
            case 12: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_INT_UNALIGNED, 8, srcSegment.get(JAVA_INT_UNALIGNED, 8));
                return;
            }
            case 13: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_INT_UNALIGNED, 8, srcSegment.get(JAVA_INT_UNALIGNED, 8));
                dstSegment.set(JAVA_BYTE, 12, srcSegment.get(JAVA_BYTE, 12));
                return;
            }
            case 14: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_INT_UNALIGNED, 8, srcSegment.get(JAVA_INT_UNALIGNED, 8));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 12, srcSegment.get(JAVA_SHORT_UNALIGNED, 12));
                return;
            }
            case 15: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_INT_UNALIGNED, 8, srcSegment.get(JAVA_INT_UNALIGNED, 8));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 12, srcSegment.get(JAVA_SHORT_UNALIGNED, 12));
                dstSegment.set(JAVA_BYTE, 14, srcSegment.get(JAVA_BYTE, 14));
                return;
            }
            case 16: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                return;
            }
            case 17: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_BYTE, 16, srcSegment.get(JAVA_BYTE, 16));
                return;
            }
            case 18: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 16, srcSegment.get(JAVA_SHORT_UNALIGNED, 16));
                return;
            }
            case 19: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 16, srcSegment.get(JAVA_SHORT_UNALIGNED, 16));
                dstSegment.set(JAVA_BYTE, 18, srcSegment.get(JAVA_BYTE, 18));
                return;
            }
            case 20: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_INT_UNALIGNED, 16, srcSegment.get(JAVA_INT_UNALIGNED, 16));
                return;
            }
            case 21: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_INT_UNALIGNED, 16, srcSegment.get(JAVA_INT_UNALIGNED, 16));
                dstSegment.set(JAVA_BYTE, 20, srcSegment.get(JAVA_BYTE, 20));
                return;
            }
            case 22: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_INT_UNALIGNED, 16, srcSegment.get(JAVA_INT_UNALIGNED, 16));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 20, srcSegment.get(JAVA_SHORT_UNALIGNED, 20));
                return;
            }
            case 23: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_INT_UNALIGNED, 16, srcSegment.get(JAVA_INT_UNALIGNED, 16));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 20, srcSegment.get(JAVA_SHORT_UNALIGNED, 20));
                dstSegment.set(JAVA_BYTE, 22, srcSegment.get(JAVA_BYTE, 22));
                return;
            }
            case 24: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                return;
            }
            case 25: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                dstSegment.set(JAVA_BYTE, 24, srcSegment.get(JAVA_BYTE, 24));
                return;
            }
            case 26: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 24, srcSegment.get(JAVA_SHORT_UNALIGNED, 24));
                return;
            }
            case 27: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 24, srcSegment.get(JAVA_SHORT_UNALIGNED, 24));
                dstSegment.set(JAVA_BYTE, 26, srcSegment.get(JAVA_BYTE, 26));
                return;
            }
            case 28: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                dstSegment.set(JAVA_INT_UNALIGNED, 24, srcSegment.get(JAVA_INT_UNALIGNED, 24));
                return;
            }
            case 29: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                dstSegment.set(JAVA_INT_UNALIGNED, 24, srcSegment.get(JAVA_INT_UNALIGNED, 24));
                dstSegment.set(JAVA_BYTE, 28, srcSegment.get(JAVA_BYTE, 28));
                return;
            }
            case 30: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                dstSegment.set(JAVA_INT_UNALIGNED, 24, srcSegment.get(JAVA_INT_UNALIGNED, 24));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 28, srcSegment.get(JAVA_SHORT_UNALIGNED, 28));
                return;
            }
            case 31: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                dstSegment.set(JAVA_INT_UNALIGNED, 24, srcSegment.get(JAVA_INT_UNALIGNED, 24));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 28, srcSegment.get(JAVA_SHORT_UNALIGNED, 28));
                dstSegment.set(JAVA_BYTE, 30, srcSegment.get(JAVA_BYTE, 30));
                return;
            }
            case 32: {
                dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                dstSegment.set(JAVA_LONG_UNALIGNED, 24, srcSegment.get(JAVA_LONG_UNALIGNED, 24));
                return;
            }
        }
        if (USE_VECTOR_API && ELEM_SIZE < 512) {
            vectorCopy();
        } else {
            MemorySegment.copy(srcSegment, 0, dstSegment, 0, ELEM_SIZE);
        }
    }

    private static final boolean USE_VECTOR_API = Architecture.isX64() && SPECIES256 != null; // And check a system property

    @SuppressWarnings("fallthrough")
    private void vectorCopy() {

        // Handle the remaining block that is less than 512 bytes
        // Fallthrough intended
        int i = ELEM_SIZE & 0b0000_0001_1110_0000;
        switch (i >> 5) {
            case 15: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  14 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  14 * 32, ByteOrder.nativeOrder());
            case 14: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  13 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  13 * 32, ByteOrder.nativeOrder());
            case 13: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  12 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  12 * 32, ByteOrder.nativeOrder());
            case 12: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  11 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  11 * 32, ByteOrder.nativeOrder());
            case 11: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  10 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  10 * 32, ByteOrder.nativeOrder());
            case 10: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  9 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  9 * 32, ByteOrder.nativeOrder());
            case 9: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  8 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  8 * 32, ByteOrder.nativeOrder());
            case 8: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 7 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 7 * 32, ByteOrder.nativeOrder());
            case 7: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  6 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  6 * 32, ByteOrder.nativeOrder());
            case 6: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  5 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  5 * 32, ByteOrder.nativeOrder());
            case 5: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  4 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  4 * 32, ByteOrder.nativeOrder());
            case 4: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  3 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  3 * 32, ByteOrder.nativeOrder());
            case 3: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  2 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  2 * 32, ByteOrder.nativeOrder());
            case 2: ByteVector.fromMemorySegment(SPECIES256, srcSegment,  32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment,  32, ByteOrder.nativeOrder());
            case 1: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 0, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 0, ByteOrder.nativeOrder());
            // case 0: do nothing
        }

        // Handle a block that is less than 32 bytes
        // Fallthrough intended
        final int delta = ELEM_SIZE & 0b0001_1000;
        switch (delta >> 3) {
            case 3: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 2, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 2));
            case 2: dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
            case 1: dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
            // case 0: do nothing
        }
        i += delta;

        // Handle the last byte block that is less than 8 bytes
        switch (ELEM_SIZE & 7) { // 0x0000 0000 0000 0111
            case 1 : dstSegment.set(JAVA_BYTE, i, srcSegment.get(JAVA_BYTE, i)); break;
            case 2 : dstSegment.set(JAVA_SHORT_UNALIGNED, i, srcSegment.get(JAVA_SHORT_UNALIGNED, i)); break;
            case 3 : {
                dstSegment.set(JAVA_SHORT_UNALIGNED, i, srcSegment.get(JAVA_SHORT_UNALIGNED, i));
                dstSegment.set(JAVA_BYTE, i + 2, srcSegment.get(JAVA_BYTE, i + 2));
                break;
            }
            case 4 : dstSegment.set(JAVA_INT_UNALIGNED, i, srcSegment.get(JAVA_INT_UNALIGNED, i)); break;
            case 5 : {
                dstSegment.set(JAVA_INT_UNALIGNED, i, srcSegment.get(JAVA_INT_UNALIGNED, i));
                dstSegment.set(JAVA_BYTE, i + 4, srcSegment.get(JAVA_BYTE, i + 4));
                break;
            }
            case 6 : {
                dstSegment.set(JAVA_INT_UNALIGNED, i, srcSegment.get(JAVA_INT_UNALIGNED, i));
                dstSegment.set(JAVA_SHORT_UNALIGNED, i + 4, srcSegment.get(JAVA_SHORT_UNALIGNED, i + 4));
                break;
            }
            case 7 : {
                dstSegment.set(JAVA_INT_UNALIGNED, i, srcSegment.get(JAVA_INT_UNALIGNED, i));
                dstSegment.set(JAVA_SHORT_UNALIGNED, i + 4, srcSegment.get(JAVA_SHORT_UNALIGNED, i + 4));
                dstSegment.set(JAVA_BYTE, i + 6, srcSegment.get(JAVA_BYTE, i + 6));
                break;
            }
        }
    }

    private static VectorSpecies<Byte> vectorSpeciesOrNull(VectorShape shape) {
        try {
            return VectorSpecies.of(byte.class, shape);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

}
