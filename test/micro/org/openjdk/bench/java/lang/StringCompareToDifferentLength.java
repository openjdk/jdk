/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, BELLSOFT. All rights reserved.
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

package com.arm.benchmarks.intrinsics;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import org.openjdk.jmh.infra.Blackhole;

/**
 * This benchmark modified from test/hotspot/jtreg/compiler/intrinsics/string/TestStringCompareToDifferentLength.java
 * This benchmark can be used to measure performance of compareTo() in
 * (Latin1, Latin1), (Latin1, UTF16), (UTF16, Latin1), and (UTF16, UTF16)
 * comparisons.
 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@CompilerControl(CompilerControl.Mode.DONT_INLINE)
public class StringCompareToDifferentLength {

    @State(Scope.Benchmark)
    public static class Input {
        @Param({"24", "36", "72", "128", "256", "512"})
        public int size;

        @Param({"2"})
        public int delta;

        int count = 100000;
        String longLatin1;
        String shortLatin1;
        String longUTF16FirstChar;
        String shortUTF16FirstChar;
        String longUTF16LastChar;
        String shortUTF16LastChar;

        /**
         * Initialize. New array objects and set initial values.
         */
        @Setup(Level.Trial)
        public void setup() throws Exception {
            char[] strsrc = new char[size + delta];
            // generate ASCII string
            for (int i = 0; i < size + delta; i++) {
                strsrc[i] = (char) ('a' + (i % 26));
            }

            longLatin1 = new String(strsrc);
            shortLatin1 = longLatin1.substring(0, size);
            longUTF16LastChar = longLatin1.substring(0, longLatin1.length() - 1) + '\ubeef';
            longUTF16FirstChar = '\ubeef' + longLatin1.substring(1, longLatin1.length());
            shortUTF16LastChar = shortLatin1.substring(0, shortLatin1.length() - 1) + '\ubeef';
            shortUTF16FirstChar = longUTF16FirstChar.substring(0, size);
        }
    }

    private int runCompareTo(String str2, String str1) {
        return str1.compareTo(str2);
    }

    /**
     * latin1-latin1
     */
    @Benchmark
    public void compareToLL(Input in, Blackhole blackhole) {
        int res = 0;
        for (int i = 0; i < in.count; ++i) {
            res += runCompareTo(in.longLatin1, in.shortLatin1);
        }
        blackhole.consume(res);
    }

    /**
     * UTF16-UTF16
     */
    @Benchmark
    public void compareToUU(Input in, Blackhole blackhole) {
        int res = 0;
        for (int i = 0; i < in.count; ++i) {
            res += runCompareTo(in.longUTF16FirstChar, in.shortUTF16FirstChar);
        }
        blackhole.consume(res);
    }

    /**
     * latin1-UTF16
     */
    @Benchmark
    public void compareToLU(Input in, Blackhole blackhole) {
        int res = 0;
        for (int i = 0; i < in.count; ++i) {
            res += runCompareTo(in.longUTF16LastChar, in.shortLatin1);
        }
        blackhole.consume(res);
    }

    /**
     * UTF16-latin1
     */
    @Benchmark
    public void compareToUL(Input in, Blackhole blackhole) {
        int res = 0;
        for (int i = 0; i < in.count; ++i) {
            res += runCompareTo(in.longLatin1, in.shortUTF16LastChar);
        }
        blackhole.consume(res);
    }
}

