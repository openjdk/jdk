/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

/**
 * Examine ability to perform escape analysis on expressions
 * such as {@code Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).getYear()}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Benchmark)
public class GetYearBench {

    private TimeZone UTC = TimeZone.getTimeZone("UTC");

    private TimeZone LONDON = TimeZone.getTimeZone("Europe/London");

    private long[] INSTANT_MILLIS;

    private int[] YEARS;

    @Setup
    public void createInstants() {
        // Various instants during the same day
        final Instant loInstant = Instant.EPOCH.plus(Duration.ofDays(365*50)); // 2020-01-01
        final Instant hiInstant = loInstant.plus(Duration.ofDays(1));
        final long maxOffsetNanos = Duration.between(loInstant, hiInstant).toNanos();
        final Random random = new Random(0);
        INSTANT_MILLIS = IntStream
                .range(0, 1_000)
                .mapToObj(ignored -> {
                    final long offsetNanos = (long) Math.floor(random.nextDouble() * maxOffsetNanos);
                    return loInstant.plus(offsetNanos, ChronoUnit.NANOS);
                })
                .mapToLong(instant -> instant.toEpochMilli())
                .toArray();
        YEARS = new int[INSTANT_MILLIS.length];
    }

    @Benchmark
    public int[] getYearFromMillisZoneOffset() {
        for (int i = 0; i < YEARS.length; i++) {
            YEARS[i] = Instant.ofEpochMilli(INSTANT_MILLIS[i]).atZone(ZoneOffset.UTC).getYear();
        }
        return YEARS;
    }

    @Benchmark
    public int[] getYearFromMillisZoneRegionUTC() {
        for (int i = 0; i < YEARS.length; i++) {
            YEARS[i] = Instant.ofEpochMilli(INSTANT_MILLIS[i]).atZone(UTC.toZoneId()).getYear();
        }
        return YEARS;
    }

    @Benchmark
    public int[] getYearFromMillisZoneRegion() {
        for (int i = 0; i < YEARS.length; i++) {
            YEARS[i] = Instant.ofEpochMilli(INSTANT_MILLIS[i]).atZone(LONDON.toZoneId()).getYear();
        }
        return YEARS;
    }

    @Benchmark
    public int[] getYearFromMillisZoneRegionNormalized() {
        for (int i = 0; i < YEARS.length; i++) {
            YEARS[i] = Instant.ofEpochMilli(INSTANT_MILLIS[i]).atZone(UTC.toZoneId().normalized()).getYear();
        }
        return YEARS;
    }
}
