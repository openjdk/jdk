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

package org.openjdk.bench.java.math;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(value = 3)
public class BigIntegerEquals {

    public enum Group {S, M, L}

    @Param({"S", "M", "L"})
    private Group group;

    private static final int MAX_LENGTH = Arrays.stream(Group.values())
            .mapToInt(p -> getNumbersOfBits(p).length)
            .max()
            .getAsInt();

    private BigInteger[] numbers;

    @Setup
    public void setup() {
        int[] nBits = getNumbersOfBits(group);
        numbers = new BigInteger[2 * MAX_LENGTH];
        for (int i = 0; i < MAX_LENGTH; i++) {
            var p = Shared.createPair(nBits[i % nBits.length]);
            numbers[2 * i] = p.x();
            numbers[2 * i + 1] = p.y();
        }
    }

    private static int[] getNumbersOfBits(Group p) {
        // the below arrays were derived from stats gathered from running tests in
        // the security area, which is the biggest client of BigInteger in JDK
        return switch (p) {
            case S -> new int[]{1, 46};
            case M -> new int[]{129, 130, 251, 252, 253, 254, 255, 256};
            case L -> new int[]{382, 383, 384, 445, 446, 447, 448, 519, 520, 521};
        };
    }

    @Benchmark
    public void testEquals(Blackhole bh) {
        for (int i = 0; i < numbers.length; i += 2)
            bh.consume(numbers[i].equals(numbers[i + 1]));
    }
}
