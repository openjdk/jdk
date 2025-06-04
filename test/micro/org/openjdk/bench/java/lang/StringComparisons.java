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
 * This benchmark naively explores String::startsWith and other String
 * comparison methods
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class StringComparisons {

    @Param({"6", "15", "1024"})
    public int size;

    @Param({"true", "false"})
    public boolean utf16;

    public String string;
    public String equalString;
    public String endsWithA;
    public String endsWithB;
    public String startsWithA;

    @Setup
    public void setup() {
        String c = utf16 ? "\uff11" : "c";
        string = c.repeat(size);
        equalString = c.repeat(size);
        endsWithA = c.repeat(size).concat("A");
        endsWithB = c.repeat(size).concat("B");
        startsWithA = "A" + (c.repeat(size));
    }

    @Benchmark
    public boolean startsWith() {
        return endsWithA.startsWith(string);
    }

    @Benchmark
    public boolean endsWith() {
        return startsWithA.endsWith(string);
    }

    @Benchmark
    public boolean regionMatches() {
        return endsWithA.regionMatches(0, endsWithB, 0, endsWithB.length());
    }

    @Benchmark
    public boolean regionMatchesRange() {
        return startsWithA.regionMatches(1, endsWithB, 0, endsWithB.length() - 1);
    }

    @Benchmark
    public boolean regionMatchesCI() {
        return endsWithA.regionMatches(true, 0, endsWithB, 0, endsWithB.length());
    }
}
