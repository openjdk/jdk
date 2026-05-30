/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.incubator.vector;

import java.util.stream.IntStream;
import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import static jdk.incubator.vector.Float16.*;
import static java.lang.Float.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class Float16ToIntegralConvBenchmark {
    @Param({"1024", "2048"})
    int size;

    short [] fp16inp;
    int [] iout;
    long [] lout;
    short [] sout;
    byte [] bout;

    @Setup(Level.Trial)
    public void BmSetup() {
        fp16inp = new short[size];
        iout = new int[size];
        lout = new long[size];
        sout = new short[size];
        bout = new byte[size];

        IntStream.range(0, size).forEach(i -> {fp16inp[i] = Float.floatToFloat16((float)i);});

        // Special Values
        Float16 [] specialValues = {Float16.NaN, Float16.NEGATIVE_INFINITY, Float16.valueOf(0.0), Float16.valueOf(-0.0), Float16.POSITIVE_INFINITY};
        IntStream.range(0, size).forEach(
            i -> {
                if ((i % 100) == 0) {
                    fp16inp[i] = float16ToRawShortBits(specialValues[i % specialValues.length]);
                }
            }
        );
    }

    @Benchmark
    public void convHF2L() {
        for (int i = 0; i < size; i++) {
            lout[i] = (long)float16ToFloat(fp16inp[i]);
        }
    }

    @Benchmark
    public void convHF2L_VAPI() {
        for (int i = 0; i < size; i++) {
            lout[i] = shortBitsToFloat16(fp16inp[i]).longValue();
        }
    }

    @Benchmark
    public void convHF2I() {
        for (int i = 0; i < size; i++) {
            iout[i] = (int)float16ToFloat(fp16inp[i]);
        }
    }

    @Benchmark
    public void convHF2I_VAPI() {
        for (int i = 0; i < size; i++) {
            iout[i] = shortBitsToFloat16(fp16inp[i]).intValue();
        }
    }

    @Benchmark
    public void convHF2S() {
        for (int i = 0; i < size; i++) {
            sout[i] = (short)float16ToFloat(fp16inp[i]);
        }
    }

    @Benchmark
    public void convHF2S_VAPI() {
        for (int i = 0; i < size; i++) {
            sout[i] = shortBitsToFloat16(fp16inp[i]).shortValue();
        }
    }

    @Benchmark
    public void convHF2B() {
        for (int i = 0; i < size; i++) {
            bout[i] = (byte)float16ToFloat(fp16inp[i]);
        }
    }

    @Benchmark
    public void convHF2B_VAPI() {
        for (int i = 0; i < size; i++) {
            bout[i] = shortBitsToFloat16(fp16inp[i]).byteValue();
        }
    }
}
