/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package org.openjdk.bench.vm.lang;

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

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class ImplicitExceptions {
    static final int ASCII_SIZE = 128;
    static final boolean[] ASCII_ALPHA = new boolean[ASCII_SIZE];
    static final int testStringLengthLog = 7;
    static final int TestStringLenghtMask = (1 << testStringLengthLog) - 1;
    static final char[] testString = new char[1 << testStringLengthLog];
    int iterator = 0;

    @Param({"0.0", "0.33", "0.66", "1.00"})
    public double exceptionProbability;

    @Setup
    public void init() {
        for (int i = 0; i < ASCII_SIZE; i++) {
            if (i >= 'a' && i <= 'z' || i >= 'A' && i <= 'Z') {
              ASCII_ALPHA[i] = true;
            }
        }
        Random rnd = new Random();
        rnd.setSeed(Long.getLong("random.seed", 42L));
        for (int i = 0; i < testString.length; i++) {
            if (rnd.nextDouble() < exceptionProbability) {
              testString[i] = 0xbad; // Something bigger than ASCII_SIZE
            }
            else {
              testString[i] = (char)rnd.nextInt(ASCII_SIZE);
            }
        }
    }

    public boolean isAlphaWithExceptions(int c) {
        try {
            return ASCII_ALPHA[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }

    @Benchmark
    public void bench() {
        isAlphaWithExceptions(testString[iterator++ & TestStringLenghtMask]);
        //if (++iterator == Long.MAX_VALUE) {
        //    iterator = 0;
        //}
    }
}
