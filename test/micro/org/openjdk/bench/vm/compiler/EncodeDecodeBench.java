/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026 Alibaba Group Holding Limited. All Rights Reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * test encoding and decoding of heap oop
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class EncodeDecodeBench {

    static final class IntHolder {
        public int value;

        IntHolder(int value) {
            this.value = value;
        }
    }

    @Param("100000")
    private int arraySize;

    @Param("0.1")
    private double nullRatio;

    private IntHolder[] arrayA;
    private IntHolder[] arrayB;

    @Setup
    public void setup() {
        arrayA = new IntHolder[arraySize];
        arrayB = new IntHolder[arraySize];
        Random random = new Random(0);
        for (int i = 0; i < arraySize; i++) {
            arrayA[i] = (random.nextDouble() < nullRatio) ? null : new IntHolder(i);
        }
    }

    @Benchmark
    public IntHolder[] testEncode() {
        IntHolder[] a = arrayA;
        IntHolder[] b = arrayB;
        for (int i = 0; i < a.length; i++) {
            IntHolder holder = a[i];
            if (holder != null) {
                holder.value += 1;
            }
            b[i] = holder;
        }
        return b;
    }

    @Benchmark
    public int testDecode() {
        // Count the nulls so that the decode-and-compare branch is actually
        // taken for some elements; see count() below.
        return count(arrayA, null);
    }

    // DONT_INLINE keeps 'v' unknown to C2 compiler. So C2 can not do
    // null constatnt optimization.
    //
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private int count(IntHolder[] a, IntHolder v) {
        int cnt = 0;
        for (int i = 0; i < a.length; i++) {
            IntHolder holder = a[i];
            if (holder == v) {
                cnt++;
            }
        }
        return cnt;
    }
}
