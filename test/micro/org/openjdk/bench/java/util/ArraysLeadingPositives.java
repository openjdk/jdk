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
package org.openjdk.bench.java.util;

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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class ArraysLeadingPositives {

    @Param({"1", "15", "64", "8195"})
    public int size;

    public byte[] positiveByteArray;
    public byte[] mixedByteArray;
    @Setup
    public void setup() {
        positiveByteArray = new byte[size];
        Arrays.fill(positiveByteArray, (byte)64);
        mixedByteArray = positiveByteArray.clone();
        mixedByteArray[size - 1] = (byte)-17;
    }

    @Benchmark
    public int positiveBytes() {
        return Arrays.numberOfLeadingPositives(positiveByteArray, 0, positiveByteArray.length);
    }

    @Benchmark
    public int mixedBytes() {
        return Arrays.numberOfLeadingPositives(mixedByteArray, 0, mixedByteArray.length);
    }

    private static int numberOfLeadingPositives(byte[] ba, int off, int len) {
        int limit = off + len;
        for (int i = off; i < limit; i++) {
            if (ba[i] < 0) {
                return i - off;
            }
        }
        return len;
    }

    @Benchmark
    public int positiveBytesBaseline() {
        return numberOfLeadingPositives(positiveByteArray, 0, positiveByteArray.length);
    }

    @Benchmark
    public int mixedBytesBaseline() {
        return numberOfLeadingPositives(mixedByteArray, 0, mixedByteArray.length);
    }

}
