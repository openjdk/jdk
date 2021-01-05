/*
 * Copyright 2020 Google Inc.  All Rights Reserved.
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
package org.openjdk.bench.java.util.regex;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Detecting trailing whitespace is a very common task that many programmers
 * have solved, but it's surprisingly difficult to avoid O(N^2) performance
 * when the input contains a long run of consecutive whitespace.  For
 * example, attempts to trim such whitespace caused a Stack Exchange outage.
 * https://stackstatus.net/post/147710624694/outage-postmortem-july-20-2016
 *
 * We use "[ \t]" as our definition of whitespace (easy, but not too easy!).
 *
 * The use of Matcher#find (instead of Matcher#matches) is convenient, but
 * introduces an implicit O(N) loop over the input.  In order for the
 * entire search operation to not be O(N^2), most of the regex match
 * operations while scanning the input need to be O(1), which may require
 * the use of less-obvious constructs like lookbehind.  The use of
 * possessive quantifiers in the regex itself is sadly **insufficient**.
 *
 * Here's a way to compare the per-char cost:
 *
 * (cd $(git rev-parse --show-toplevel) && for size in 16 64 256 1024; do make test TEST='micro:java.util.regex.Trim' MICRO="FORK=2;WARMUP_ITER=1;ITER=4;OPTIONS=-opi $size -p size=$size" |& perl -ne 'print if /^Benchmark/ .. /^Finished running test/'; done)
 *
 * TODO: why is simple_find faster than possessive_find, for size below 512 ?
 *
 * (cd $(git rev-parse --show-toplevel) && for size in 128 256 512 1024 2048; do make test TEST='micro:java.util.regex.Trim.\\\(simple_find\\\|possessive_find\\\)' MICRO="FORK=2;WARMUP_ITER=1;ITER=4;OPTIONS=-opi $size -p size=$size" |& perl -ne 'print if /^Benchmark/ .. /^Finished running test/'; done)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class Trim {
    /** Run length of non-matching consecutive whitespace chars. */
    @Param({"16", "64", "256", "1024"})
    int size;

    /** String containing long run of whitespace */
    public String noMatch;

    public Pattern simplePattern;
    public Pattern possessivePattern;
    public Pattern possessivePattern2;
    public Pattern possessivePattern3;
    public Pattern lookBehindPattern;

    @Setup
    public void setup() {
        noMatch = "xx" + " \t".repeat(size) + "yy";

        simplePattern = Pattern.compile("[ \t]+$");
        possessivePattern = Pattern.compile("[ \t]++$");
        possessivePattern2 = Pattern.compile("(.*+[^ \t]|^)([ \t]++)$");
        possessivePattern3 = Pattern.compile("(?:[^ \t]|^)([ \t]++)$");
        lookBehindPattern = Pattern.compile("(?<![ \t])[ \t]++$");
    }

    @Benchmark
    public boolean simple_find() {
        return simplePattern.matcher(noMatch).find();
    }

    @Benchmark
    public boolean possessive_find() {
        return possessivePattern.matcher(noMatch).find();
    }

    @Benchmark
    public boolean possessive2_find() {
        return possessivePattern2.matcher(noMatch).find();
    }

    @Benchmark
    public boolean possessive2_matches() {
        return possessivePattern2.matcher(noMatch).matches();
    }

    @Benchmark
    public boolean possessive3_find() {
        return possessivePattern3.matcher(noMatch).find();
    }

    @Benchmark
    public boolean lookBehind_find() {
        return lookBehindPattern.matcher(noMatch).find();
    }
}
