/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class CalculateHashMapCapacityTestJMH {

    /**
     * Calculate initial capacity for HashMap based classes, from expected size.
     *
     * @param expectedSize expected size
     * @return initial capacity for HashMap based classes.
     * @since 19
     */
    private static int calculateHashMapCapacity1(int expectedSize) {
        return (int) Math.ceil(expectedSize / 0.75);
    }

    /**
     * Calculate initial capacity for HashMap based classes, from expected size.
     *
     * @param expectedSize expected size
     * @return initial capacity for HashMap based classes.
     * @since 19
     */
    private static int calculateHashMapCapacity2(int expectedSize) {
        if (expectedSize >= Integer.MAX_VALUE / 4 * 3 + 3) {
            return Integer.MAX_VALUE;
        }
        if (expectedSize > 0) {
            return (expectedSize + (expectedSize + 2) / 3);
        }
        return expectedSize;
    }

    /**
     * Calculate initial capacity for HashMap based classes, from expected size.
     *
     * @param expectedSize expected size
     * @return initial capacity for HashMap based classes.
     * @since 19
     */
    private static int calculateHashMapCapacity3(int expectedSize) {
        if (expectedSize >= 805306368) {
            return (1 << 30);
        }
        if (expectedSize > 0) {
            return (expectedSize + (expectedSize + 2) / 3);
        }
        return expectedSize;
    }

    @Warmup(iterations = 20)
    @Measurement(iterations = 10)
    @Benchmark
    public void testCalculateHashMapCapacity1(Blackhole blackhole) {
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            blackhole.consume(calculateHashMapCapacity1(i));
        }
    }

    @Warmup(iterations = 20)
    @Measurement(iterations = 10)
    @Benchmark
    public void testCalculateHashMapCapacity2(Blackhole blackhole) {
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            blackhole.consume(calculateHashMapCapacity2(i));
        }
    }

    @Warmup(iterations = 20)
    @Measurement(iterations = 10)
    @Benchmark
    public void testCalculateHashMapCapacity3(Blackhole blackhole) {
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            blackhole.consume(calculateHashMapCapacity3(i));
        }
    }

}
