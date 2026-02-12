/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Param;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark for StringBuilder char coalescing optimization.
 * Tests the performance of consecutive append(char) calls that should
 * be coalesced into append(char, char) and append(char, char, char, char).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class StringBuilderCharCoalescing {

    @Param({"65", "97", "48"})
    private int charValue;

    private char c1, c2, c3, c4;

    @Setup
    public void setup() {
        c1 = (char) charValue;
        c2 = (char) (charValue + 1);
        c3 = (char) (charValue + 2);
        c4 = (char) (charValue + 3);
    }

    // ====== Two consecutive char appends ======

    /**
     * Baseline: Two separate append(char) calls.
     * Without optimization, this makes two separate method calls.
     */
    @Benchmark
    public String twoCharsSeparate() {
        return new StringBuilder().append(c1).append(c2).toString();
    }

    /**
     * Optimized: Single append(char, char) call.
     * This should be faster as it's a single method call with two chars.
     */
    @Benchmark
    public String twoCharsCoalesced() {
        return new StringBuilder().append(c1, c2).toString();
    }

    // ====== Four consecutive char appends ======

    /**
     * Baseline: Four separate append(char) calls.
     * Without optimization, this makes four separate method calls.
     */
    @Benchmark
    public String fourCharsSeparate() {
        return new StringBuilder().append(c1).append(c2).append(c3).append(c4).toString();
    }

    /**
     * Optimized: Single append(char, char, char, char) call.
     * This should be faster as it's a single method call with four chars.
     */
    @Benchmark
    public String fourCharsCoalesced() {
        return new StringBuilder().append(c1, c2, c3, c4).toString();
    }

    // ====== Mixed scenarios ======

    /**
     * String followed by two chars (separate calls).
     */
    @Benchmark
    public String stringTwoCharsSeparate() {
        return new StringBuilder("AB").append(c1).append(c2).toString();
    }

    /**
     * String followed by two chars (coalesced).
     */
    @Benchmark
    public String stringTwoCharsCoalesced() {
        return new StringBuilder("AB").append(c1, c2).toString();
    }

    /**
     * Two chars followed by string (separate calls).
     */
    @Benchmark
    public String twoCharsStringSeparate() {
        return new StringBuilder().append(c1).append(c2).append("XY").toString();
    }

    /**
     * Two chars followed by string (coalesced).
     */
    @Benchmark
    public String twoCharsStringCoalesced() {
        return new StringBuilder().append(c1, c2).append("XY").toString();
    }

    // ====== Repeated appends ======

    /**
     * Eight chars with separate calls.
     */
    @Benchmark
    public String eightCharsSeparate() {
        return new StringBuilder()
            .append(c1).append(c2).append(c3).append(c4)
            .append(c1).append(c2).append(c3).append(c4)
            .toString();
    }

    /**
     * Eight chars with explicit coalescing.
     */
    @Benchmark
    public String eightCharsCoalesced() {
        return new StringBuilder()
            .append(c1, c2, c3, c4)
            .append(c1, c2, c3, c4)
            .toString();
    }

    // ====== Larger concatenations ======

    /**
     * Building a longer string with mixed content.
     */
    @Benchmark
    public String mixedLargeSeparate() {
        return new StringBuilder()
            .append("ID:")
            .append(c1).append(c2).append(c3).append(c4)
            .append("-")
            .append(c4).append(c3).append(c2).append(c1)
            .toString();
    }

    /**
     * Building a longer string with coalesced char appends.
     */
    @Benchmark
    public String mixedLargeCoalesced() {
        return new StringBuilder()
            .append("ID:")
            .append(c1, c2, c3, c4)
            .append("-")
            .append(c4, c3, c2, c1)
            .toString();
    }
}
