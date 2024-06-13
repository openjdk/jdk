/*
 * Copyright (c) 2024 IBM Corporation. All rights reserved.
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 5)
public class PopCount {
    int numTests = 100_000;

    @Benchmark
    public long test() {
        long l1 = 1, l2 = 2, l3 = 3, l4 = 4, l5 = 5, l6 = 6, l7 = 7, l8 = 9, l9 = 9, l10 = 10;
        for (long i = 0; i < numTests; i++) {
            l1 ^= Long.bitCount(l1) + i;
            l2 ^= Long.bitCount(l2) + i;
            l3 ^= Long.bitCount(l3) + i;
            l4 ^= Long.bitCount(l4) + i;
            l5 ^= Long.bitCount(l5) + i;
            l6 ^= Long.bitCount(l6) + i;
            l7 ^= Long.bitCount(l7) + i;
            l8 ^= Long.bitCount(l8) + i;
            l9 ^= Long.bitCount(l9) + i;
            l10 ^= Long.bitCount(l10) + i;
        }
        return l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8 + l9 + l10;
    }

}
