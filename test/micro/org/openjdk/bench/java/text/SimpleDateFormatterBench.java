/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

package org.openjdk.bench.java.text;

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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Benchmark)
public class SimpleDateFormatterBench {

    private Date date;
    private Object objDate;
    private String dateStr;
    private String timeStr;

    private static final String DATE_PATTERN = "EEEE, MMMM d, y";
    private static final String TIME_PATTERN = "h:mm:ss a zzzz";

    // Use non-factory methods w/ pattern to ensure test data can be round
    // tripped and guarantee no re-use of the same instance
    private DateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN);
    private DateFormat timeFormat = new SimpleDateFormat(TIME_PATTERN);

    @Setup
    public void setup() {
        date = new Date();
        objDate = new Date();
        // Generate the strings for parsing using dedicated separate instances
        dateStr = new SimpleDateFormat(DATE_PATTERN).format(date);
        timeStr = new SimpleDateFormat(TIME_PATTERN).format(date);
    }

    @Benchmark
    public String testTimeFormat() {
        return timeFormat.format(date);
    }

    @Benchmark
    public String testTimeFormatObject() {
        return timeFormat.format(objDate);
    }

    @Benchmark
    public String testDateFormat() {
        return dateFormat.format(date);
    }

    @Benchmark
    public String testDateFormatObject() {
        return dateFormat.format(objDate);
    }

    @Benchmark
    public Date testDateParse() throws ParseException {
        return dateFormat.parse(dateStr);
    }

    @Benchmark
    public Date testTimeParse() throws ParseException {
        return timeFormat.parse(timeStr);
    }

    public static void main(String... args) throws Exception {
        Options opts = new OptionsBuilder().include(org.openjdk.bench.java.text.SimpleDateFormatterBench.class.getSimpleName()).shouldDoGC(true).build();
        new Runner(opts).run();
    }
}
