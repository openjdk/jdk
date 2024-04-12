/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class Characters {

    @Param({"9", "65", "97", "223", "430"})
    private int codePoint;

    @Benchmark
    public boolean isDigit() {
        return Character.isDigit(codePoint);
    }

    @Benchmark
    public boolean isLowerCase() {
        return Character.isLowerCase(codePoint);
    }

    @Benchmark
    public boolean isUpperCase() {
        return Character.isUpperCase(codePoint);
    }

    @Benchmark
    public boolean isWhitespace() {
        return Character.isWhitespace(codePoint);
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Warmup(iterations = 5, time = 1)
    @Measurement(iterations = 5, time = 1)
    @Fork(3)
    public static class CodePoints {
        @Benchmark
        public void codePointOf() {
            Character.codePointOf("Latin Capital Letter B with hook");
        }

    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Thread)
    @Warmup(iterations = 5, time = 1)
    @Measurement(iterations = 5, time = 1)
    @Fork(3)
    public static class CaseConversions {
        @Param({
                "low",     // 0x09 pre A
                "A",       // 0x41 uppercase A
                "a",       // 0x61 lowercase a
                "A-grave", // 0xC0 uppercase A-grave
                "a-grave", // 0xE0 lowercase a-grave
                "micro",   // 0xB5 lowercase 'Micro Sign'
                "yD"       // 0xFF lowercase 'y with Diaeresis'
        })
        private String codePoint;
        private int cp;

        @Setup(Level.Trial)
        public void setup() {
            cp = switch (codePoint) {
                case "low"     -> 0x09;
                case "A"       -> 0x41;
                case "a"       -> 0x61;
                case "A-grave" -> 0xC0;
                case "a-grave" -> 0xE0;
                case "yD"      -> 0xE0;
                case "micro"   -> 0xFF;
                default -> Integer.parseInt(codePoint);
            };
        }
        @Benchmark
        public int toUpperCase() {
            return Character.toUpperCase(cp);
        }

        @Benchmark
        public int toLowerCase() {
            return Character.toLowerCase(cp);
        }
    }
}
