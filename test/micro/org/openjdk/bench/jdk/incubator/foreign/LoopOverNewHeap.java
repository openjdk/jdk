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
package org.openjdk.bench.jdk.incubator.foreign;

import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

import java.lang.invoke.VarHandle;
import java.nio.IntBuffer;
import java.util.concurrent.TimeUnit;

import static jdk.incubator.foreign.MemoryLayout.PathElement.sequenceElement;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = { "--add-modules=jdk.incubator.foreign" })
public class LoopOverNewHeap {

    static final Unsafe unsafe = Utils.unsafe;

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int)JAVA_INT.byteSize();

    static final VarHandle VH_int = MemoryLayout.sequenceLayout(JAVA_INT).varHandle(sequenceElement());

    @Param(value = {"false", "true"})
    boolean polluteProfile;

    @Setup
    public void setup() {
        if (polluteProfile) {
            for (int i = 0 ; i < 10000 ; i++) {
                MemorySegment intB = MemorySegment.ofArray(new byte[ELEM_SIZE]);
                MemorySegment intI = MemorySegment.ofArray(new int[ELEM_SIZE]);
                MemorySegment intD = MemorySegment.ofArray(new double[ELEM_SIZE]);
                MemorySegment intF = MemorySegment.ofArray(new float[ELEM_SIZE]);
            }
        }
    }

    @Benchmark
    public void unsafe_loop() {
        int[] elems = new int[ELEM_SIZE];
        for (int i = 0; i < ELEM_SIZE; i++) {
            unsafe.putInt(elems, Unsafe.ARRAY_INT_BASE_OFFSET + (i * CARRIER_SIZE) , i);
        }
    }


    @Benchmark
    public void segment_loop() {
        MemorySegment segment = MemorySegment.ofArray(new int[ELEM_SIZE]);
        for (int i = 0; i < ELEM_SIZE; i++) {
            VH_int.set(segment, (long) i, i);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public void segment_loop_dontinline() {
        MemorySegment segment = MemorySegment.ofArray(new int[ELEM_SIZE]);
        for (int i = 0; i < ELEM_SIZE; i++) {
            VH_int.set(segment, (long) i, i);
        }
    }

    @Benchmark
    public void buffer_loop() {
        IntBuffer buffer = IntBuffer.wrap(new int[ELEM_SIZE]);
        for (int i = 0; i < ELEM_SIZE; i++) {
            buffer.put(i , i);
        }
    }

}
