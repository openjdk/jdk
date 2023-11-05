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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/*
 * This benchmark naively explores String::regionMatches, ignoring case
 */

public class RegionMatchesIC {

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Benchmark)
    @Warmup(iterations = 5, time = 1)
    @Measurement(iterations = 5, time = 1)
    @Fork(value = 3)
    public static class Latin1 {

        @Param({"1024"})
        public int size;

        @Param({"ascii-match",
                "ascii-mismatch",
                "number-match",
                "number-mismatch",
                "lat1-match",
                "lat1-mismatch"})
        String codePoints;
        private String leftString;
        private String rightString;

        @Setup
        public void setup() {

            switch (codePoints) {
                case "ascii-match" -> {
                    leftString  = "a".repeat(size);
                    rightString = "A".repeat(size);
                }
                case "ascii-mismatch" -> {
                    leftString  = "a".repeat(size);
                    rightString = "b".repeat(size);
                }
                case "number-match" -> {
                    leftString  = "7".repeat(size);
                    rightString = "7".repeat(size);
                }
                case "number-mismatch" -> {
                    leftString  = "7".repeat(size);
                    rightString = "9".repeat(size);
                }
                case "lat1-match" -> {
                    leftString  = "\u00e5".repeat(size);
                    rightString = "\u00c5".repeat(size);
                }
                case "lat1-mismatch" -> {
                    leftString  = "\u00e5".repeat(size);
                    rightString = "\u00c6".repeat(size);
                }
                default -> throw new IllegalArgumentException("Unsupported coding: " + codePoints);
            }
            // Make sure strings do not String.equals by adding a prefix
            leftString = "l" + leftString;
            rightString = "r" + rightString;
        }

        @Benchmark
        public boolean regionMatchesIC() {
            return leftString.regionMatches(true, 1, rightString, 1, size);
        }
    }
}
