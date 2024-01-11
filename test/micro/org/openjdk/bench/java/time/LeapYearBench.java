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

package org.openjdk.bench.java.time;

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
import org.openjdk.jmh.infra.Blackhole;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZonedDateTime;
import java.time.chrono.IsoChronology;
import java.time.temporal.ChronoUnit;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Examine Year.leapYear-related operations
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class LeapYearBench {

    private long[] years;

    private GregorianCalendar calendar;

    @Setup
    public void createInstants() {
        // Large enough number of years to guarantee that the distribution of
        // leap years is reasonably realistic
        years = new long[2048];
        final Random random = new Random(0);
        for (int i = 0; i < years.length; i++) {
            years[i] = random.nextLong(2000) + 2000;
        }
        calendar = GregorianCalendar.from(ZonedDateTime.now());
    }

    @Benchmark
    public void isLeapYear(Blackhole bh) {
        for (long year : years) {
            bh.consume(Year.isLeap(year));
        }
    }

    @Benchmark
    public void isLeapYearChrono(Blackhole bh) {
        for (long year : years) {
            bh.consume(IsoChronology.INSTANCE.isLeapYear(year));
        }
    }

    @Benchmark
    public void isLeapYearGregorian(Blackhole bh) {
        for (long year : years) {
            bh.consume(calendar.isLeapYear((int)year));
        }
    }

    public static boolean isLeapNeriSchneider(long year) {
        int d = year % 100 != 0 ? 4 : 16;
        return (year & (d - 1)) == 0;
    }

    @Benchmark
    public void isLeapYearNS(Blackhole bh) {
        for (long year : years) {
            bh.consume(isLeapNeriSchneider(year));
        }
    }
}
