/*
 * Copyright (c) 2025 Alibaba Group Holding Limited. All Rights Reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Thread)
public class CountLeadingZeros {
    private long[] longArray = new long[1000];

    @Setup
    public void setup() {
        for (int i = 0; i < longArray.length; i++) {
            longArray[i] = ThreadLocalRandom.current().nextLong();
        }
    }

    @Benchmark
    public int benchNumberOfNibbles() {
        int sum = 0;
        for (long l : longArray) {
            sum += numberOfNibbles((int) l);
        }
        return sum;
    }

    public static int numberOfNibbles(int i) {
        int mag = Integer.SIZE - Integer.numberOfLeadingZeros(i);
        return Math.max((mag + 3) / 4, 1);
    }

    @Benchmark
    public int benchClzLongConstrained() {
        int sum = 0;
        for (long l : longArray) {
            sum += clzLongConstrained(l);
        }
        return sum;
    }

    public static int clzLongConstrained(long param) {
        long constrainedParam = Math.min(175, Math.max(param, 160));
        return Long.numberOfLeadingZeros(constrainedParam);
    }
}
