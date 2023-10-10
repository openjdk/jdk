/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.*;
import java.lang.foreign.Arena;
import java.lang.invoke.VarHandle;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;
import java.util.concurrent.TimeUnit;

import static java.lang.foreign.ValueLayout.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--enable-native-access=ALL-UNNAMED" })
public class UnrolledAccess extends JavaLayouts {

    static final Unsafe U = Utils.unsafe;

    final static int SIZE = 1024;

    @State(Scope.Benchmark)
    public static class Data {

        final double[] inputArray;
        final double[] outputArray;
        final long inputAddress;
        final long outputAddress;
        final MemorySegment inputSegment;
        final MemorySegment outputSegment;


        public Data() {
            this.inputArray = new double[SIZE];
            this.outputArray = new double[SIZE];
            this.inputAddress = U.allocateMemory(8 * SIZE);
            this.outputAddress = U.allocateMemory(8 * SIZE);
            this.inputSegment = MemorySegment.ofAddress(inputAddress)
                    .reinterpret(8*SIZE);
            this.outputSegment = MemorySegment.ofAddress(outputAddress)
                    .reinterpret(8*SIZE);
        }
    }

    @Benchmark
    public void unsafe_loop(Data state) {
        final long ia = state.inputAddress;
        final long oa = state.outputAddress;
        for(int i = 0; i < SIZE; i+=4) {
            U.putLong(oa + 8*i, U.getLong(ia + 8*i) + U.getLong(oa + 8*i));
            U.putLong(oa + 8*(i+1), U.getLong(ia + 8*(i+1)) + U.getLong(oa + 8*(i+1)));
            U.putLong(oa + 8*(i+2), U.getLong(ia + 8*(i+2)) + U.getLong(oa + 8*(i+2)));
            U.putLong(oa + 8*(i+3), U.getLong(ia + 8*(i+3)) + U.getLong(oa + 8*(i+3)));
        }
    }

    @Benchmark
    public void handle_loop(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            VH_LONG.set(os, (long) (i),   (long) VH_LONG.get(is, (long) (i))   + (long) VH_LONG.get(os, (long) (i)));
            VH_LONG.set(os, (long) (i+1), (long) VH_LONG.get(is, (long) (i+1)) + (long) VH_LONG.get(os, (long) (i+1)));
            VH_LONG.set(os, (long) (i+2), (long) VH_LONG.get(is, (long) (i+2)) + (long) VH_LONG.get(os, (long) (i+2)));
            VH_LONG.set(os, (long) (i+3), (long) VH_LONG.get(is, (long) (i+3)) + (long) VH_LONG.get(os, (long) (i+3)));
        }
    }

    @Benchmark
    public void handle_loop_instance(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            os.setAtIndex(JAVA_LONG, i, is.getAtIndex(JAVA_LONG, i) + os.getAtIndex(JAVA_LONG, i));
            os.setAtIndex(JAVA_LONG, i+1, is.getAtIndex(JAVA_LONG, i+1) + os.getAtIndex(JAVA_LONG, i+1));
            os.setAtIndex(JAVA_LONG, i+2, is.getAtIndex(JAVA_LONG, i+2) + os.getAtIndex(JAVA_LONG, i+2));
            os.setAtIndex(JAVA_LONG, i+3, is.getAtIndex(JAVA_LONG, i+3) + os.getAtIndex(JAVA_LONG, i+3));
        }
    }

    @Benchmark
    public void handle_loop_unaligned(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            VH_LONG_UNALIGNED.set(os, (long) (i),   (long) VH_LONG_UNALIGNED.get(is, (long) (i))   + (long) VH_LONG_UNALIGNED.get(os, (long) (i)));
            VH_LONG_UNALIGNED.set(os, (long) (i+1), (long) VH_LONG_UNALIGNED.get(is, (long) (i+1)) + (long) VH_LONG_UNALIGNED.get(os, (long) (i+1)));
            VH_LONG_UNALIGNED.set(os, (long) (i+2), (long) VH_LONG_UNALIGNED.get(is, (long) (i+2)) + (long) VH_LONG_UNALIGNED.get(os, (long) (i+2)));
            VH_LONG_UNALIGNED.set(os, (long) (i+3), (long) VH_LONG_UNALIGNED.get(is, (long) (i+3)) + (long) VH_LONG_UNALIGNED.get(os, (long) (i+3)));
        }
    }

    @Benchmark
    public void handle_loop_instance_unaligned(Data state) {
        final MemorySegment is = state.inputSegment;
        final MemorySegment os = state.outputSegment;

        for(int i = 0; i < SIZE; i+=4) {
            os.setAtIndex(JAVA_LONG_UNALIGNED, i, is.getAtIndex(JAVA_LONG_UNALIGNED, i) + os.getAtIndex(JAVA_LONG_UNALIGNED, i));
            os.setAtIndex(JAVA_LONG_UNALIGNED, i+1, is.getAtIndex(JAVA_LONG_UNALIGNED, i+1) + os.getAtIndex(JAVA_LONG_UNALIGNED, i+1));
            os.setAtIndex(JAVA_LONG_UNALIGNED, i+2, is.getAtIndex(JAVA_LONG_UNALIGNED, i+2) + os.getAtIndex(JAVA_LONG_UNALIGNED, i+2));
            os.setAtIndex(JAVA_LONG_UNALIGNED, i+3, is.getAtIndex(JAVA_LONG_UNALIGNED, i+3) + os.getAtIndex(JAVA_LONG_UNALIGNED, i+3));
        }
    }
}
