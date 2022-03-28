/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Param;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.concurrent.TimeUnit;

/**
 * Tests for compare() and compareUnsigned() methods in java.lang.Long
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class CompareLong {

    RandomGenerator rng;

    @Param({"mixed", "lessThanEqual", "greaterThanEqual", "equal"})
    String mode;

    @Param({"1024"})
    int BUFFER_SIZE;

    long[] input1, input2, outputs;

    @Setup
    public void setup() {
        input1 = new long[BUFFER_SIZE];
        input2 = new long[BUFFER_SIZE];
        outputs =  new long[BUFFER_SIZE];
        RandomGenerator rng = RandomGeneratorFactory.getDefault().create(0);

        for (int i = 0; i < BUFFER_SIZE; i++) {
            input1[i] = rng.nextLong();
            if (mode.equals("equal")) {
                input2[i] = input1[i];
                continue;
            }
            else input2[i] = rng.nextLong();

            if (!mode.equals("mixed")) {
                boolean doSwap = (mode.equals("lessThanEqual") && input1[i] > input2[i]) ||
                                (mode.equals("greaterThanEqual") && input1[i] < input2[i]);
                if (doSwap) {
                    long tmp = input1[i];
                    input1[i] = input2[i];
                    input2[i] = tmp;
                }
            }
        }
    }

    @Benchmark
    public void testCompare() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            outputs[i] = Long.compare(input1[i], input2[i]);
        }
    }

    @Benchmark
    public void testCompareUnsigned() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            outputs[i] = Long.compareUnsigned(input1[i], input2[i]);
        }
    }

}


