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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 2) // 3
public class TestFill {

/*    @Param({"0", "1", "2", "3", "4", "5", "6", "7", "8",
            "9", "10", "11", "12", "13", "14", "15", "16",
            "17", "18", "19", "20", "21", "22", "23", "24",
            "25", "26", "27", "28", "29", "30", "31", "32",
            "33", "64", "128", "256", "512", "1023", "2048})*/
    //@Param({"0", "4", "8", "16", "32", "64", "128"})
    @Param({"16", "17", "18", "19", "20", "21", "22", "23"})
    public int ELEM_SIZE;

    byte[] dstArray;
    MemorySegment heapDstSegment;
    MemorySegment nativeDstSegment;
    ByteBuffer dstBuffer;

    @Setup
    public void setup() {
        dstArray = new byte[ELEM_SIZE];
        heapDstSegment = MemorySegment.ofArray(dstArray);
        nativeDstSegment = Arena.ofAuto().allocate(ELEM_SIZE);
        dstBuffer = ByteBuffer.wrap(dstArray);
    }

/*
    @Benchmark
    public void arrays_fill() {
        Arrays.fill(dstArray, (byte) 0);
    }
*/

    @Benchmark
    public void fill_patch() {
        heapDstSegment.fill((byte) 0);
    }

/*    @Benchmark
    public void fill_base() {
        heapDstSegment.fillBase((byte) 0);
    }

    @Benchmark
    public void buffer_fill() {
        dstBuffer.clear().put(new byte[ELEM_SIZE]);
    }*/

}
