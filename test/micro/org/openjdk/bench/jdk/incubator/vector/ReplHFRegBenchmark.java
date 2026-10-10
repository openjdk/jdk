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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class ReplHFRegBenchmark {
    static final int LEN = 32;

    static final int N = 16;

    static final int K = 512;

    short[] src = new short[LEN];
    short[] dst = new short[LEN];

    float[] vals = new float[K + N];

    @Setup
    public void setup() {
        Arrays.fill(src, Float.floatToFloat16(1.0f));
        for (int i = 0; i < vals.length; i++) {
            // Small magnitudes so the adds stay in FP16 range.
            vals[i] = 0.125f + (i % 7) * 0.0625f;
        }
    }

    private static short hfAdd(short a, float b) {
        return Float.floatToFloat16(Float.float16ToFloat(a)
                                  + Float.float16ToFloat(Float.floatToFloat16(b)));
    }

    @Benchmark
    public int manyBroadcasts() {
        short[] d = dst;
        short[] s = src;
        float[] v = vals;
        for (int k = 0; k < K; k++) {
            float v0  = v[k],      v1  = v[k + 1],  v2  = v[k + 2],  v3  = v[k + 3];
            float v4  = v[k + 4],  v5  = v[k + 5],  v6  = v[k + 6],  v7  = v[k + 7];
            float v8  = v[k + 8],  v9  = v[k + 9],  v10 = v[k + 10], v11 = v[k + 11];
            float v12 = v[k + 12], v13 = v[k + 13], v14 = v[k + 14], v15 = v[k + 15];
            for (int i = 0; i < LEN; i++) {
                short a = s[i];
                int t = hfAdd(a, v0)  ^ hfAdd(a, v1)  ^ hfAdd(a, v2)  ^ hfAdd(a, v3)
                      ^ hfAdd(a, v4)  ^ hfAdd(a, v5)  ^ hfAdd(a, v6)  ^ hfAdd(a, v7)
                      ^ hfAdd(a, v8)  ^ hfAdd(a, v9)  ^ hfAdd(a, v10) ^ hfAdd(a, v11)
                      ^ hfAdd(a, v12) ^ hfAdd(a, v13) ^ hfAdd(a, v14) ^ hfAdd(a, v15);
                d[i] = (short) t;
            }
        }
        return d[0] & 0xffff;
    }
}
