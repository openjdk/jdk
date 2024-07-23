/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
package org.openjdk.bench.java.lang;

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

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 4, time = 5)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
public class MathVectorizedBench {

    @Param({"1024", "4096", "8192", "16384"})
    int size;

    public int[] aInt;
    public int[] bInt;
    public int[] cInt;
    public long[] aLong;
    public long[] bLong;
    public long[] cLong;

    @Setup
    public void setup() {
        aInt = new int[size];
        bInt = new int[size];
        cInt = new int[size];
        aLong = new long[size];
        bLong = new long[size];
        cLong = new long[size];
        for (int i = 0; i < size; i++) {
            aInt[i] = ThreadLocalRandom.current().nextInt();
            bInt[i] = ThreadLocalRandom.current().nextInt();
            cInt[i] = ThreadLocalRandom.current().nextInt();
            aLong[i] = ThreadLocalRandom.current().nextLong();
            bLong[i] = ThreadLocalRandom.current().nextLong();
            cLong[i] = ThreadLocalRandom.current().nextLong();
        }
    }

    @Benchmark
    public long reductionSingleIntMin() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = 11 * aInt[i];
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long reductionMultiIntMin() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = (aInt[i] * bInt[i]) + (aInt[i] * cInt[i]) + (bInt[i] * cInt[i]);
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long reductionSingleIntMax() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = 11 * aInt[i];
            result = Math.max(result, v);
        }
        return result;
    }

    @Benchmark
    public long reductionMultiIntMax() {
        int result = 0;
        for (int i = 0; i < size; i++) {
            final int v = (aInt[i] * bInt[i]) + (aInt[i] * cInt[i]) + (bInt[i] * cInt[i]);
            result = Math.max(result, v);
        }
        return result;
    }

    @Benchmark
    public long reductionSingleLongMin() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = 11 * aLong[i];
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long reductionMultiLongMin() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final int v = (aInt[i] * bInt[i]) + (aInt[i] * cInt[i]) + (bInt[i] * cInt[i]);
            result = Math.min(result, v);
        }
        return result;
    }

    @Benchmark
    public long reductionSingleLongMax() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final long v = 11 * aLong[i];
            result = Math.max(result, v);
        }
        return result;
    }

    @Benchmark
    public long reductionMultiLongMax() {
        long result = 0;
        for (int i = 0; i < size; i++) {
            final int v = (aInt[i] * bInt[i]) + (aInt[i] * cInt[i]) + (bInt[i] * cInt[i]);
            result = Math.max(result, v);
        }
        return result;
    }
}
