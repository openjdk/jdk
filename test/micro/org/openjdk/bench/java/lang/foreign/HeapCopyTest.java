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
import org.openjdk.jmh.annotations.*;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(jvmArgsAppend = {
        // Assembler stuff
/*        "-Xbatch", "-XX:-TieredCompilation","-XX:CompileCommand=dontinline,org.openjdk.bench.java.lang.foreign.jmh_generated.HeapCopyTest_vector_jmhTest::vector_avgt_jmhStub*",
        "-XX:CompileCommand=PrintAssembly,org.openjdk.bench.java.lang.foreign.jmh_generated.HeapCopyTest_vector_jmhTest::vector_avgt_jmhStub*",*/

        "--enable-preview", "--add-modules=jdk.incubator.vector", "--add-exports=java.base/jdk.internal.util=ALL-UNNAMED"}, value = 3)
public class HeapCopyTest {

    private static final VectorSpecies<Byte> SPECIES256 = vectorSpeciesOrNull(VectorShape.S_256_BIT);

    @Param({"4", "8", "16", "32", "64", "128", "256", "384"})
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
        System.out.println();
        System.out.println("ELEM_SIZE = " + ELEM_SIZE);
        System.out.println("SPECIES256 = " + SPECIES256);
        System.out.println("dstSegment.address() = " + dstSegment.address());
        System.out.println("USE_VECTOR_API = " + USE_VECTOR_API);
    }
/*
    @Benchmark
    public void array_copy() {
        System.arraycopy(srcArray, 0, dstArray, 0, ELEM_SIZE);
    } */

    @Benchmark
    public void segment_copy() {
        MemorySegment.copy(srcSegment, 0, dstSegment, 0, ELEM_SIZE);
    }
/*
    @Benchmark
    public void buffer_copy() {
        dstBuffer.put(srcBuffer);
    }

    private static final VarHandle HANDLE = JAVA_BYTE.varHandle();

    @Benchmark
    public void segment_auto_vector() {
        switch (ELEM_SIZE) {
            case 1 -> dstSegment.set(JAVA_BYTE, 0, srcSegment.get(JAVA_BYTE, 0));
            case 2 -> dstSegment.set(JAVA_SHORT_UNALIGNED, 0, srcSegment.get(JAVA_SHORT_UNALIGNED, 0));
            case 3 -> {
                dstSegment.set(JAVA_SHORT_UNALIGNED, 0, srcSegment.get(JAVA_SHORT_UNALIGNED, 0));
                dstSegment.set(JAVA_BYTE, 2, srcSegment.get(JAVA_BYTE, 2));
            }
            case 4 -> dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
            case 5 -> {
                dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                dstSegment.set(JAVA_BYTE, 4, srcSegment.get(JAVA_BYTE, 4));
            }
            case 6 -> {
                dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 4, srcSegment.get(JAVA_SHORT_UNALIGNED, 4));
            }
            case 7 -> {
                dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                dstSegment.set(JAVA_SHORT_UNALIGNED, 4, srcSegment.get(JAVA_SHORT_UNALIGNED, 4));
                dstSegment.set(JAVA_BYTE, 6, srcSegment.get(JAVA_BYTE, 6));
            }
            default -> {
                final int chunkEnd = ELEM_SIZE - 7;
                int i = 0;
                for (; i < chunkEnd; i += 8) {
                    dstSegment.set(JAVA_LONG_UNALIGNED, i, srcSegment.get(JAVA_LONG_UNALIGNED, i));
                }
                for (; i < ELEM_SIZE; i++) {
                    dstSegment.set(JAVA_BYTE, i, srcSegment.get(JAVA_BYTE, i));
                }
            }
        }
    } */

    @Benchmark
    public void segment_staged() {
        switch (ELEM_SIZE) {
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
            case 23: { // Slow
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
            case 31: { // Slow
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
                    /*final int chunkEnd = ELEM_SIZE - STEP + 1;
                    int i = 0;
                    for (; i < chunkEnd; i += STEP) {
                        var vs = ByteVector.fromMemorySegment(SPECIES, srcSegment, i, ByteOrder.nativeOrder());
                        vs.intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
                    }
                    // Tail
                    for (; i < ELEM_SIZE; i++) {
                        dstSegment.set(JAVA_BYTE, i, srcSegment.get(JAVA_BYTE, i));
                    }*/
        } else {
            MemorySegment.copy(srcSegment, 0, dstSegment, 0, ELEM_SIZE);
        }
    }
/*
    @SuppressWarnings("fallthrough")
    @Benchmark
    public void segment_staged2() {
        if (ELEM_SIZE <= 64) {
            switch (ELEM_SIZE >> 3) {
                case 8 : dstSegment.set(JAVA_LONG_UNALIGNED, 56, srcSegment.get(JAVA_LONG_UNALIGNED, 56));
                case 7 : dstSegment.set(JAVA_LONG_UNALIGNED, 48, srcSegment.get(JAVA_LONG_UNALIGNED, 48));
                case 6 : dstSegment.set(JAVA_LONG_UNALIGNED, 40, srcSegment.get(JAVA_LONG_UNALIGNED, 40));
                case 5 : dstSegment.set(JAVA_LONG_UNALIGNED, 32, srcSegment.get(JAVA_LONG_UNALIGNED, 32));
                case 4 : dstSegment.set(JAVA_LONG_UNALIGNED, 24, srcSegment.get(JAVA_LONG_UNALIGNED, 24));
                case 3 : dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                case 2 : dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                case 1 : dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                // case 0: do nothing
            }
*//*            final int i = (ELEM_SIZE & 63);
            switch (i >> 3) {
                case 1 : dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0)); break;
                case 2 : {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    break;
                }
                case 3 : {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    break;
                }
                case 4 : {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 24, srcSegment.get(JAVA_LONG_UNALIGNED, 24));
                    break;
                }
                case 5 : {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 24, srcSegment.get(JAVA_LONG_UNALIGNED, 24));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 32, srcSegment.get(JAVA_LONG_UNALIGNED, 32));
                    break;
                }
            }*//*
            final int i = ELEM_SIZE & ~7;
            switch (ELEM_SIZE & 7) {
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
*//*            if ((ELEM_SIZE & 4) != 0) {
                dstSegment.set(JAVA_INT_UNALIGNED, i, srcSegment.get(JAVA_INT_UNALIGNED, i));
                i += 4;
            }
            if ((ELEM_SIZE & 2) != 0) {
                dstSegment.set(JAVA_SHORT_UNALIGNED, i, srcSegment.get(JAVA_SHORT_UNALIGNED, i));
                i += 2;
            }
            if ((ELEM_SIZE & 1) != 0) {
                dstSegment.set(JAVA_BYTE, i, srcSegment.get(JAVA_BYTE, i));
            }*//*

        } else {
            if (IS_X64 && ELEM_SIZE <= 512) {
                final int chunkEnd = ELEM_SIZE - STEP + 1;
                int i = 0;
                // Todo: unroll (hard)
                for (; i < chunkEnd; i += STEP) {
                    var vs = ByteVector.fromMemorySegment(SPECIES, srcSegment, i, ByteOrder.nativeOrder());
                    vs.intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
                }
                // Tail
                for (; i < ELEM_SIZE; i++) {
                    dstSegment.set(JAVA_BYTE, i, srcSegment.get(JAVA_BYTE, i));
                }
            } else {
                MemorySegment.copy(srcSegment, 0, dstSegment, 0, ELEM_SIZE);
            }
        }
    }

    @Benchmark
    public void vector() {
        final int len = ELEM_SIZE;
        for (int i = 0; i < len; i += STEP) {
            var vs = ByteVector.fromMemorySegment(SPECIES, srcSegment, i, ByteOrder.nativeOrder());
            vs.intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
        }
        // Todo: handle tail
    }

    @Benchmark
    public void vector256() {
        final int len = ELEM_SIZE;
        for (long i = 0; i < len; i += 32) {
            var vs = ByteVector.fromMemorySegment(SPECIES128, srcSegment, i, ByteOrder.nativeOrder());
            vs.intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
        }
        // Todo: handle tail
    }


    @SuppressWarnings("fallthrough")
    @Benchmark
    public void vectorUnroll256() {
        // Precondition ELEM_SIZE < 512 and SPECIES256 exists

        int i = ELEM_SIZE & ~31;
        switch (i >> 5) {
            case 15: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 14 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 14 * 32, ByteOrder.nativeOrder());
            case 14: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 13 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 13 * 32, ByteOrder.nativeOrder());
            case 13: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 12 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 12 * 32, ByteOrder.nativeOrder());
            case 12: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 11 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 11 * 32, ByteOrder.nativeOrder());
            case 11: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 10 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 10 * 32, ByteOrder.nativeOrder());
            case 10: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 9 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 9 * 32, ByteOrder.nativeOrder());
            case 9: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 8 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 8 * 32, ByteOrder.nativeOrder());
            case 8: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 7 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 7 * 32, ByteOrder.nativeOrder());
            case 7: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 6 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 6 * 32, ByteOrder.nativeOrder());
            case 6: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 5 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 5 * 32, ByteOrder.nativeOrder());
            case 5: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 4 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 4 * 32, ByteOrder.nativeOrder());
            case 4: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 3 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 3 * 32, ByteOrder.nativeOrder());
            case 3: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 2 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 2 * 32, ByteOrder.nativeOrder());
            case 2: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 32, ByteOrder.nativeOrder());
            case 1: ByteVector.fromMemorySegment(SPECIES256, srcSegment, 0, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 0, ByteOrder.nativeOrder());
                // case 0: do nothing
        }
        final int i2 = (ELEM_SIZE & (31 - 7));
        switch (i2 >> 3) {
            case 3: dstSegment.set(JAVA_LONG_UNALIGNED, i + 16, srcSegment.get(JAVA_LONG_UNALIGNED, i + 16));
            case 2: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8));
            case 1: dstSegment.set(JAVA_LONG_UNALIGNED, i, srcSegment.get(JAVA_LONG_UNALIGNED, i));
                // case 0: do nothing
        }
        final int i3 = i + i2;
        switch (ELEM_SIZE & 7) {
            case 1 : dstSegment.set(JAVA_BYTE, i3, srcSegment.get(JAVA_BYTE, i3)); break;
            case 2 : dstSegment.set(JAVA_SHORT_UNALIGNED, i3, srcSegment.get(JAVA_SHORT_UNALIGNED, i3)); break;
            case 3 : {
                dstSegment.set(JAVA_SHORT_UNALIGNED, i3, srcSegment.get(JAVA_SHORT_UNALIGNED, i3));
                dstSegment.set(JAVA_BYTE, i3 + 2, srcSegment.get(JAVA_BYTE, i3 + 2));
                break;
            }
            case 4 : dstSegment.set(JAVA_INT_UNALIGNED, i3, srcSegment.get(JAVA_INT_UNALIGNED, i3)); break;
            case 5 : {
                dstSegment.set(JAVA_INT_UNALIGNED, i3, srcSegment.get(JAVA_INT_UNALIGNED, i3));
                dstSegment.set(JAVA_BYTE, i3 + 4, srcSegment.get(JAVA_BYTE, i3 + 4));
                break;
            }
            case 6 : {
                dstSegment.set(JAVA_INT_UNALIGNED, i3, srcSegment.get(JAVA_INT_UNALIGNED, i3));
                dstSegment.set(JAVA_SHORT_UNALIGNED, i3 + 4, srcSegment.get(JAVA_SHORT_UNALIGNED, i3 + 4));
                break;
            }
            case 7 : {
                dstSegment.set(JAVA_INT_UNALIGNED, i3, srcSegment.get(JAVA_INT_UNALIGNED, i3));
                dstSegment.set(JAVA_SHORT_UNALIGNED, i3 + 4, srcSegment.get(JAVA_SHORT_UNALIGNED, i3 + 4));
                dstSegment.set(JAVA_BYTE, i3 + 6, srcSegment.get(JAVA_BYTE, i3 + 6));
                break;
            }
        }
    }


    @SuppressWarnings("fallthrough")
    @Benchmark
    public void vectorUnroll() {
        //256 / 64 = 2^(8 - 6) = 2^2 = 4
        // 64 is hardcoded....
        // Precondition ELEM_SIZE < 512

        int i = ELEM_SIZE & ~31;
        switch (i >> 6) {
            case 7: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 6 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 6 * 64, ByteOrder.nativeOrder());
            case 6: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 5 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 5 * 64, ByteOrder.nativeOrder());
            case 5: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 4 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 4 * 64, ByteOrder.nativeOrder());
            case 4: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 3 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 3 * 64, ByteOrder.nativeOrder());
            case 3: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 2 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 2 * 64, ByteOrder.nativeOrder());
            case 2: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 64, ByteOrder.nativeOrder());
            case 1: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 0, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 0, ByteOrder.nativeOrder());
            // case 0: do nothing
        }
        if ((ELEM_SIZE & 32) != 0) {
            ByteVector.fromMemorySegment(SPECIES256, srcSegment, 7 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 7 * 64, ByteOrder.nativeOrder());
            i += 16;
        }
        final int i2 = (ELEM_SIZE & (31 - 7));
        switch (i2 >> 3) {
            case 3: dstSegment.set(JAVA_LONG_UNALIGNED, i + 16, srcSegment.get(JAVA_LONG_UNALIGNED, i + 16));
            case 2: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8));
            case 1: dstSegment.set(JAVA_LONG_UNALIGNED, i, srcSegment.get(JAVA_LONG_UNALIGNED, i));
            // case 0: do nothing
        }
        final int i3 = i + i2;
        switch (ELEM_SIZE & 7) {
            case 1 : dstSegment.set(JAVA_BYTE, i3, srcSegment.get(JAVA_BYTE, i3)); break;
            case 2 : dstSegment.set(JAVA_SHORT_UNALIGNED, i3, srcSegment.get(JAVA_SHORT_UNALIGNED, i3)); break;
            case 3 : {
                dstSegment.set(JAVA_SHORT_UNALIGNED, i3, srcSegment.get(JAVA_SHORT_UNALIGNED, i3));
                dstSegment.set(JAVA_BYTE, i3 + 2, srcSegment.get(JAVA_BYTE, i3 + 2));
                break;
            }
            case 4 : dstSegment.set(JAVA_INT_UNALIGNED, i3, srcSegment.get(JAVA_INT_UNALIGNED, i3)); break;
            case 5 : {
                dstSegment.set(JAVA_INT_UNALIGNED, i3, srcSegment.get(JAVA_INT_UNALIGNED, i3));
                dstSegment.set(JAVA_BYTE, i3 + 4, srcSegment.get(JAVA_BYTE, i3 + 4));
                break;
            }
            case 6 : {
                dstSegment.set(JAVA_INT_UNALIGNED, i3, srcSegment.get(JAVA_INT_UNALIGNED, i3));
                dstSegment.set(JAVA_SHORT_UNALIGNED, i3 + 4, srcSegment.get(JAVA_SHORT_UNALIGNED, i3 + 4));
                break;
            }
            case 7 : {
                dstSegment.set(JAVA_INT_UNALIGNED, i3, srcSegment.get(JAVA_INT_UNALIGNED, i3));
                dstSegment.set(JAVA_SHORT_UNALIGNED, i3 + 4, srcSegment.get(JAVA_SHORT_UNALIGNED, i3 + 4));
                dstSegment.set(JAVA_BYTE, i3 + 6, srcSegment.get(JAVA_BYTE, i3 + 6));
                break;
            }
        }
    }


    @SuppressWarnings("fallthrough")
    @Benchmark
    public void vectorUnroll2() {
        // Precondition: ELEM_SIZE < 512

        final int i512Steps = SPECIES512 == null ? 0 : ELEM_SIZE >> 6;
        switch (i512Steps) {
            case 7: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 6 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 6 * 64, ByteOrder.nativeOrder());
            case 6: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 5 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 5 * 64, ByteOrder.nativeOrder());
            case 5: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 4 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 4 * 64, ByteOrder.nativeOrder());
            case 4: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 3 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 3 * 64, ByteOrder.nativeOrder());
            case 3: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 2 * 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 2 * 64, ByteOrder.nativeOrder());
            case 2: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 64, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 64, ByteOrder.nativeOrder());
            case 1: ByteVector.fromMemorySegment(SPECIES512, srcSegment, 0, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, 0, ByteOrder.nativeOrder());
            // case 0: do nothing
        }
        int i = i512Steps << 6;
        final int i256Steps = SPECIES256 == null ? 0 : (ELEM_SIZE - i) >> 5;
        switch (i256Steps) {
            case 15: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 14 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 14 * 32, ByteOrder.nativeOrder());
            case 14: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 13 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 13 * 32, ByteOrder.nativeOrder());
            case 13: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 12 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 12 * 32, ByteOrder.nativeOrder());
            case 12: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 11 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 11 * 32, ByteOrder.nativeOrder());
            case 11: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 10 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 10 * 32, ByteOrder.nativeOrder());
            case 10: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 9 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 9 * 32, ByteOrder.nativeOrder());
            case 9: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 8 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 8 * 32, ByteOrder.nativeOrder());
            case 8: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 7 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 7 * 32, ByteOrder.nativeOrder());
            case 7: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 6 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 6 * 32, ByteOrder.nativeOrder());
            case 6: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 5 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 5 * 32, ByteOrder.nativeOrder());
            case 5: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 4 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 4 * 32, ByteOrder.nativeOrder());
            case 4: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 3 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 3 * 32, ByteOrder.nativeOrder());
            case 3: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 2 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 2 * 32, ByteOrder.nativeOrder());
            case 2: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 32, ByteOrder.nativeOrder());
            case 1: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
            // case 0: do nothing
        }
        i += i256Steps << 5;
        final int i128Steps = SPECIES128 == null ? 0 : (ELEM_SIZE - i) >> 4;
        switch (i128Steps) {
            case 29: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 28 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 28 * 32, ByteOrder.nativeOrder());
            case 28: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 27 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 27 * 32, ByteOrder.nativeOrder());
            case 27: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 26 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 26 * 32, ByteOrder.nativeOrder());
            case 26: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 25 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 25 * 32, ByteOrder.nativeOrder());
            case 25: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 24 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 24 * 32, ByteOrder.nativeOrder());
            case 24: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 23 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 23 * 32, ByteOrder.nativeOrder());
            case 23: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 22 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 22 * 32, ByteOrder.nativeOrder());
            case 22: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 21 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 21 * 32, ByteOrder.nativeOrder());
            case 21: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 20 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 20 * 32, ByteOrder.nativeOrder());
            case 20: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 19 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 19 * 32, ByteOrder.nativeOrder());
            case 19: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 18 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 18 * 32, ByteOrder.nativeOrder());
            case 18: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 17 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 17 * 32, ByteOrder.nativeOrder());
            case 17: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 16 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 16 * 32, ByteOrder.nativeOrder());
            case 16: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 15 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 15 * 32, ByteOrder.nativeOrder());
            case 15: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 14 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 14 * 32, ByteOrder.nativeOrder());
            case 14: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 13 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 13 * 32, ByteOrder.nativeOrder());
            case 13: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 12 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 12 * 32, ByteOrder.nativeOrder());
            case 12: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 11 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 11 * 32, ByteOrder.nativeOrder());
            case 11: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 10 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 10 * 32, ByteOrder.nativeOrder());
            case 10: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 9 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 9 * 32, ByteOrder.nativeOrder());
            case 9: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 8 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 8 * 32, ByteOrder.nativeOrder());
            case 8: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 7 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 7 * 32, ByteOrder.nativeOrder());
            case 7: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 6 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 6 * 32, ByteOrder.nativeOrder());
            case 6: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 5 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 5 * 32, ByteOrder.nativeOrder());
            case 5: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 4 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 4 * 32, ByteOrder.nativeOrder());
            case 4: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 3 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 3 * 32, ByteOrder.nativeOrder());
            case 3: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 2 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 2 * 32, ByteOrder.nativeOrder());
            case 2: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i + 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 32, ByteOrder.nativeOrder());
            case 1: ByteVector.fromMemorySegment(SPECIES128, srcSegment, i, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
                // case 0: do nothing
        }
        i += i128Steps << 4;

        final int i64Steps = (ELEM_SIZE - i) >> 3;
        switch (i64Steps) {
            case 5: dstSegment.set(JAVA_LONG_UNALIGNED, i512Steps + 32, srcSegment.get(JAVA_LONG_UNALIGNED, i512Steps + 32));
            case 4: dstSegment.set(JAVA_LONG_UNALIGNED, i512Steps + 24, srcSegment.get(JAVA_LONG_UNALIGNED, i512Steps + 24));
            case 3: dstSegment.set(JAVA_LONG_UNALIGNED, i512Steps + 16, srcSegment.get(JAVA_LONG_UNALIGNED, i512Steps + 16));
            case 2: dstSegment.set(JAVA_LONG_UNALIGNED, i512Steps + 8, srcSegment.get(JAVA_LONG_UNALIGNED, i512Steps + 8));
            case 1: dstSegment.set(JAVA_LONG_UNALIGNED, i512Steps, srcSegment.get(JAVA_LONG_UNALIGNED, i512Steps));
            // case 0: do nothing
        }
        i += i64Steps << 3;
        switch (ELEM_SIZE & 7) {
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
    }*/

    private static final boolean USE_VECTOR_API = Architecture.isX64() && SPECIES256 != null; // And check a system property

/*    @SuppressWarnings("fallthrough")
    @Benchmark
    public void candidate() {
        long i;
        if (USE_VECTOR_API) {
            i = vectorCopyHeader(ELEM_SIZE);
        } else {
            i = scalarCopyHeader(ELEM_SIZE);
*//*            if (ELEM_SIZE >= 128) {
                MemorySegment.copy(srcSegment, 0, dstSegment, 0, ELEM_SIZE);
                return;
            }
            i = 0;*//*
        }
        int remaining = (int) (ELEM_SIZE - i);
        // Handle the remaining block that is less than 128 bytes (<32 for vector)
        // Fallthrough intended
        switch ((remaining & 127) >> 3) { // 0000 0000 0011 1000
            case 15: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 14, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 14));
            case 14: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 13, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 13));
            case 13: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 12, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 12));
            case 12: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 11, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 11));
            case 11: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 10, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 10));
            case 10: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 9, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 9));
            case 9: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 8, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 8));
            case 8: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 7, srcSegment.get(JAVA_LONG_UNALIGNED, 8 * 7));
            case 7: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 6, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 6));
            case 6: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 5, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 5));
            case 5: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 4, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 4));
            case 4: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 3, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 3));
            case 3: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8 * 2, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8 * 2));
            case 2: dstSegment.set(JAVA_LONG_UNALIGNED, i + 8, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8));
            case 1: dstSegment.set(JAVA_LONG_UNALIGNED, i, srcSegment.get(JAVA_LONG_UNALIGNED, i));
            // case 0: do nothing
        }
        i += (remaining & 0b0111_1000);

        // Handle the last byte block that is less than 8 bytes
        switch (remaining & 7) { // 0x0000 0000 0000 0111
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

    @SuppressWarnings("fallthrough")
    private long vectorCopyHeader(long size) {
        // Deal with the initial N * 256 byte blocks
        long headEnd = size & ~255; // 1111 1111 0000 0000 (32 bytes in the vector * 8 operations = 256 bytes per iteration)
        long i = 0;
        for (; i  < headEnd ; i += 256) {
            ByteVector.fromMemorySegment(SPECIES256, srcSegment, i, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
            ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 32, ByteOrder.nativeOrder());
            ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 2 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 2 * 32, ByteOrder.nativeOrder());
            ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 3 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 3 * 32, ByteOrder.nativeOrder());
            ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 4 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 4 * 32, ByteOrder.nativeOrder());
            ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 5 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 5 * 32, ByteOrder.nativeOrder());
            ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 6 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 6 * 32, ByteOrder.nativeOrder());
            ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 7 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 7 * 32, ByteOrder.nativeOrder());
        }

        // Handle the remaining block that is less than 256 bytes
        // Fallthrough intended
        switch (((int) (size & 255)) >> 5) { // 0000 0000 1110 0000
            case 7: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 6 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 6 * 32, ByteOrder.nativeOrder());
            case 6: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 5 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 5 * 32, ByteOrder.nativeOrder());
            case 5: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 4 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 4 * 32, ByteOrder.nativeOrder());
            case 4: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 3 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 3 * 32, ByteOrder.nativeOrder());
            case 3: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 2 * 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 2 * 32, ByteOrder.nativeOrder());
            case 2: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i + 32, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i + 32, ByteOrder.nativeOrder());
            case 1: ByteVector.fromMemorySegment(SPECIES256, srcSegment, i, ByteOrder.nativeOrder()).intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
            // case 0: do nothing
        }
        i += size & 0b1110_0000;
        // Max 32 bytes remaining
        return i;
    }

    private long scalarCopyHeader(long size) {
        long headEnd = size & ~63; // 1111 1111 1100 0000 (8 bytes per operation * 8 operations = 64 bytes per iteration)
        long i = 0;
        for (; i  < headEnd ; i += 64) {
            dstSegment.set(JAVA_LONG_UNALIGNED, i, srcSegment.get(JAVA_LONG_UNALIGNED, i));
            dstSegment.set(JAVA_LONG_UNALIGNED, i + 8, srcSegment.get(JAVA_LONG_UNALIGNED, i + 8));
            dstSegment.set(JAVA_LONG_UNALIGNED, i + 16, srcSegment.get(JAVA_LONG_UNALIGNED, i + 16));
            dstSegment.set(JAVA_LONG_UNALIGNED, i + 24, srcSegment.get(JAVA_LONG_UNALIGNED, i + 24));
            dstSegment.set(JAVA_LONG_UNALIGNED, i + 32, srcSegment.get(JAVA_LONG_UNALIGNED, i + 32));
            dstSegment.set(JAVA_LONG_UNALIGNED, i + 40, srcSegment.get(JAVA_LONG_UNALIGNED, i + 40));
            dstSegment.set(JAVA_LONG_UNALIGNED, i + 48, srcSegment.get(JAVA_LONG_UNALIGNED, i + 48));
            dstSegment.set(JAVA_LONG_UNALIGNED, i + 56, srcSegment.get(JAVA_LONG_UNALIGNED, i + 56));
        }
        return i;
    }*/

    @SuppressWarnings("fallthrough")
    @Benchmark
    public void candidate_small() {
        if (USE_VECTOR_API) {
            if (ELEM_SIZE < 512) {
                vectorCopy();
                return;
            }
        } else {
            if (ELEM_SIZE < 128) {
                scalarCopy();
                return;
            }
        }
        // Fall back to the good ol' copy
        MemorySegment.copy(srcSegment, 0, dstSegment, 0, ELEM_SIZE);
    }

    @SuppressWarnings("fallthrough")
    @Benchmark
    public void candidate_small_scalar() {
         if (ELEM_SIZE < 128) {
             // Handle a block that is less than 128 bytes
             // Fallthrough intended
             final int i = (ELEM_SIZE & 0b0111_1000);
             switch (i >> 3) {
                 case 15: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 14, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 14));
                 case 14: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 13, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 13));
                 case 13: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 12, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 12));
                 case 12: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 11, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 11));
                 case 11: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 10, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 10));
                 case 10: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 9, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 9));
                 case 9: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 8, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 8));
                 case 8: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 7, srcSegment.get(JAVA_LONG_UNALIGNED, 8 * 7));
                 case 7: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 6, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 6));
                 case 6: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 5, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 5));
                 case 5: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 4, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 4));
                 case 4: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 3, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 3));
                 case 3: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 2, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 2));
                 case 2: dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                 case 1: dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                     // case 0: do nothing
             }

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
         } else {
             // Fall back to the good ol' copy
             MemorySegment.copy(srcSegment, 0, dstSegment, 0, ELEM_SIZE);
         }
    }

    @SuppressWarnings("fallthrough")
    @Benchmark
    public void candidate_smaller_scalar() {
        if (ELEM_SIZE < 64) {
            // Handle a block that is less than 128 bytes
            // Fallthrough intended
            int i = (ELEM_SIZE & 0b0011_1000);
            switch (i >> 3) {
                case 7: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 6, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 6));
                case 6: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 5, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 5));
                case 5: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 4, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 4));
                case 4: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 3, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 3));
                case 3: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 2, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 2));
                case 2: dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                case 1: dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    // case 0: do nothing
            }

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
        } else {
            // Fall back to the good ol' copy
            MemorySegment.copy(srcSegment, 0, dstSegment, 0, ELEM_SIZE);
        }
    }


    @SuppressWarnings("fallthrough")
    void scalarCopy() {
        // Handle a block that is less than 128 bytes
        // Fallthrough intended
        final int i = (ELEM_SIZE & 0b0111_1000);
        switch ((i) >> 3) {
            case 15: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 14, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 14));
            case 14: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 13, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 13));
            case 13: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 12, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 12));
            case 12: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 11, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 11));
            case 11: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 10, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 10));
            case 10: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 9, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 9));
            case 9: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 8, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 8));
            case 8: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 7, srcSegment.get(JAVA_LONG_UNALIGNED, 8 * 7));
            case 7: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 6, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 6));
            case 6: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 5, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 5));
            case 5: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 4, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 4));
            case 4: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 3, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 3));
            case 3: dstSegment.set(JAVA_LONG_UNALIGNED,  8 * 2, srcSegment.get(JAVA_LONG_UNALIGNED,  8 * 2));
            case 2: dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
            case 1: dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
            // case 0: do nothing
        }

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

/*

    static void copyMemoryUnroll(MemorySegment srcSegment, long srcOffset,
                                 MemorySegment dstSegment, long dstOffset, long bytes) {
        if (bytes <= 512) {
            final int bytesAsInt = (int) bytes;
            switch (bytesAsInt) {
                case 1 ->
                        dstSegment.set(JAVA_BYTE, 0, srcSegment.get(JAVA_BYTE, 0));
                case 2 ->
                        dstSegment.set(JAVA_SHORT_UNALIGNED, 0, srcSegment.get(JAVA_SHORT_UNALIGNED, 0));
                case 3 -> {
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 0, srcSegment.get(JAVA_SHORT_UNALIGNED, 0));
                    dstSegment.set(JAVA_BYTE, 2, srcSegment.get(JAVA_BYTE, 2));
                }
                case 4 ->
                        dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                case 5 -> {
                    dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                    dstSegment.set(JAVA_BYTE, 4, srcSegment.get(JAVA_BYTE, 4));
                }
                case 6 -> {
                    dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 4, srcSegment.get(JAVA_SHORT_UNALIGNED, 4));
                }
                case 7 -> {
                    dstSegment.set(JAVA_INT_UNALIGNED, 0, srcSegment.get(JAVA_INT_UNALIGNED, 0));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 4, srcSegment.get(JAVA_SHORT_UNALIGNED, 4));
                    dstSegment.set(JAVA_BYTE, 6, srcSegment.get(JAVA_BYTE, 6));
                }
                case 8 ->
                        dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                case 9 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_BYTE, 8, srcSegment.get(JAVA_BYTE, 8));
                }
                case 10 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 8, srcSegment.get(JAVA_SHORT_UNALIGNED, 8));
                }
                case 11 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 8, srcSegment.get(JAVA_SHORT_UNALIGNED, 8));
                    dstSegment.set(JAVA_BYTE, 10, srcSegment.get(JAVA_BYTE, 10));
                }
                case 12 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_INT_UNALIGNED, 8, srcSegment.get(JAVA_INT_UNALIGNED, 8));
                }
                case 13 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_INT_UNALIGNED, 8, srcSegment.get(JAVA_INT_UNALIGNED, 8));
                    dstSegment.set(JAVA_BYTE, 12, srcSegment.get(JAVA_BYTE, 12));
                }
                case 14 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_INT_UNALIGNED, 8, srcSegment.get(JAVA_INT_UNALIGNED, 8));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 12, srcSegment.get(JAVA_SHORT_UNALIGNED, 12));
                }
                case 15 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_INT_UNALIGNED, 8, srcSegment.get(JAVA_INT_UNALIGNED, 8));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 12, srcSegment.get(JAVA_SHORT_UNALIGNED, 12));
                    dstSegment.set(JAVA_BYTE, 14, srcSegment.get(JAVA_BYTE, 14));
                }
                case 16 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                }
                case 17 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_BYTE, 16, srcSegment.get(JAVA_BYTE, 16));
                }
                case 18 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 16, srcSegment.get(JAVA_SHORT_UNALIGNED, 16));
                }
                case 19 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 16, srcSegment.get(JAVA_SHORT_UNALIGNED, 16));
                    dstSegment.set(JAVA_BYTE, 18, srcSegment.get(JAVA_BYTE, 18));
                }
                case 20 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_INT_UNALIGNED, 16, srcSegment.get(JAVA_INT_UNALIGNED, 16));
                }
                case 21 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_INT_UNALIGNED, 16, srcSegment.get(JAVA_INT_UNALIGNED, 16));
                    dstSegment.set(JAVA_BYTE, 20, srcSegment.get(JAVA_BYTE, 20));
                }
                case 22 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_INT_UNALIGNED, 16, srcSegment.get(JAVA_INT_UNALIGNED, 16));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 20, srcSegment.get(JAVA_SHORT_UNALIGNED, 20));
                }
                case 23 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_INT_UNALIGNED, 16, srcSegment.get(JAVA_INT_UNALIGNED, 16));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 20, srcSegment.get(JAVA_SHORT_UNALIGNED, 20));
                    dstSegment.set(JAVA_BYTE, 22, srcSegment.get(JAVA_BYTE, 22));
                }
                case 24 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                }
                case 25 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_BYTE, 24, srcSegment.get(JAVA_BYTE, 24));
                }
                case 26 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 24, srcSegment.get(JAVA_SHORT_UNALIGNED, 24));
                }
                case 27 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 24, srcSegment.get(JAVA_SHORT_UNALIGNED, 24));
                    dstSegment.set(JAVA_BYTE, 26, srcSegment.get(JAVA_BYTE, 26));
                }
                case 28 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_INT_UNALIGNED, 24, srcSegment.get(JAVA_INT_UNALIGNED, 24));
                }
                case 29 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_INT_UNALIGNED, 24, srcSegment.get(JAVA_INT_UNALIGNED, 24));
                    dstSegment.set(JAVA_BYTE, 28, srcSegment.get(JAVA_BYTE, 28));
                }
                case 30 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_INT_UNALIGNED, 24, srcSegment.get(JAVA_INT_UNALIGNED, 24));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 28, srcSegment.get(JAVA_SHORT_UNALIGNED, 28));
                }
                case 31 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_INT_UNALIGNED, 24, srcSegment.get(JAVA_INT_UNALIGNED, 24));
                    dstSegment.set(JAVA_SHORT_UNALIGNED, 28, srcSegment.get(JAVA_SHORT_UNALIGNED, 28));
                    dstSegment.set(JAVA_BYTE, 30, srcSegment.get(JAVA_BYTE, 30));
                }
                case 32 -> {
                    dstSegment.set(JAVA_LONG_UNALIGNED, 0, srcSegment.get(JAVA_LONG_UNALIGNED, 0));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 8, srcSegment.get(JAVA_LONG_UNALIGNED, 8));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 16, srcSegment.get(JAVA_LONG_UNALIGNED, 16));
                    dstSegment.set(JAVA_LONG_UNALIGNED, 24, srcSegment.get(JAVA_LONG_UNALIGNED, 24));
                }
                // case 64 and bigger does not pay of to unroll
                default -> {
                    if (IS_X64) {
                        final int chunkEnd = bytesAsInt - STEP + 1;
                        int i = 0;
                        for (; i < chunkEnd; i += STEP) {
                            var vs = ByteVector.fromMemorySegment(SPECIES, srcSegment, i, ByteOrder.nativeOrder());
                            vs.intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
                        }
                        // Tail
                        for (; i < bytes; i++) {
                            dstSegment.set(JAVA_BYTE, i, srcSegment.get(JAVA_BYTE, i));
                        }
                    } else {
                        MemorySegment.copy(srcSegment, 0, dstSegment, 0, bytes);
                    }
                }
            }
        } else {
            MemorySegment.copy(srcSegment, 0, dstSegment, 0, bytes);
        }

    }

    static void copyMemoryLoop(MemorySegment srcSegment, long srcOffset,
                               MemorySegment dstSegment, long dstOffset, long bytes) {

        if (bytes <= 32) {
            final int bytesAsInt = (int) bytes;
            final int longEnd = bytesAsInt - 7;
            int i = 0;
            for (; i < longEnd; i += 8) {
                dstSegment.set(JAVA_LONG_UNALIGNED, i, srcSegment.get(JAVA_LONG_UNALIGNED, i));
            }
            if ((bytesAsInt & 4) != 0) {
                dstSegment.set(JAVA_INT_UNALIGNED, i, srcSegment.get(JAVA_INT_UNALIGNED, i));
                i += 4;
            }
            if ((bytesAsInt & 2) != 0) {
                dstSegment.set(JAVA_SHORT_UNALIGNED, i, srcSegment.get(JAVA_SHORT_UNALIGNED, i));
                i += 2;
            }
            if ((bytesAsInt & 1) != 0) {
                dstSegment.set(JAVA_BYTE, i, srcSegment.get(JAVA_BYTE, i));
            }
        }
        if (IS_X64 && bytes <= 512) {
            final int bytesAsInt = (int) bytes;
            final int chunkEnd = bytesAsInt - STEP + 1;
            int i = 0;
            for (; i < chunkEnd; i += STEP) {
                var vs = ByteVector.fromMemorySegment(SPECIES, srcSegment, i, ByteOrder.nativeOrder());
                vs.intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
            }
            // Tail
            for (; i < bytes; i++) {
                dstSegment.set(JAVA_BYTE, i, srcSegment.get(JAVA_BYTE, i));
            }
        } else {
            MemorySegment.copy(srcSegment, 0, dstSegment, 0, bytes);
        }
    }
*/

    private static VectorSpecies<Byte> vectorSpeciesOrNull(VectorShape shape) {
        try {
            return VectorSpecies.of(byte.class, shape);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

}
