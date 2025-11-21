/*
 * Copyright (c) 2025, Alibaba Group Holding Limited. All Rights Reserved.
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
package org.openjdk.bench.java.time.format;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
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

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class DateTimeFormatterParse {
    static final DateTimeFormatter formatterLocalTime = DateTimeFormatter.ofPattern("HH:mm:ss");
    static final DateTimeFormatter formatterLocalTimeWithNano = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    static final DateTimeFormatter formatterLocalDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final DateTimeFormatter formatterLocalDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    static final DateTimeFormatter formatterLocalDateTimeWithNano = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    static final DateTimeFormatter formatterOffsetDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ");
    static final DateTimeFormatter formatterZonedDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZZZZ'['VV']'");

    static final String STR_LOCALTIME = "21:11:48";
    static final String STR_LOCALTIME_WITH_NANO = "21:11:48.456";
    static final String STR_LOCALDATE = "2024-08-01";
    static final String STR_LOCALDATETIME = "2024-08-01T21:11:48";
    static final String STR_LOCALDATETIME_WITH_NANO = "2024-08-01T21:11:48.456";
    static final String STR_INSTANT = "2024-08-12T03:25:54.980339Z";
    static final String STR_OFFSETDATETIME = "2024-08-12T11:50:46.731509+08:00";
    static final String STR_ZONEDDATETIME = "2024-08-12T11:50:46.731509+08:00[Asia/Shanghai]";

    @Benchmark
    public LocalTime parseLocalTime() {
        return LocalTime.parse(STR_LOCALTIME, formatterLocalTime);
    }

    @Benchmark
    public LocalTime parseLocalTimeWithNano() {
        return LocalTime.parse(STR_LOCALTIME_WITH_NANO, formatterLocalTimeWithNano);
    }

    @Benchmark
    public LocalDate parseLocalDate() {
        return LocalDate.parse(STR_LOCALDATE, formatterLocalDate);
    }

    @Benchmark
    public LocalDateTime parseLocalDateTime() {
        return LocalDateTime.parse(STR_LOCALDATETIME, formatterLocalDateTime);
    }

    @Benchmark
    public LocalDateTime parseLocalDateTimeWithNano() {
        return LocalDateTime.parse(STR_LOCALDATETIME_WITH_NANO, formatterLocalDateTimeWithNano);
    }

    @Benchmark
    public OffsetDateTime parseOffsetDateTime() {
        return OffsetDateTime.parse(STR_OFFSETDATETIME, formatterOffsetDateTime);
    }

    @Benchmark
    public ZonedDateTime parseZonedDateTime() {
        return ZonedDateTime.parse(STR_ZONEDDATETIME, formatterZonedDateTime);
    }

    @Benchmark
    public Instant parseInstant() {
        return Instant.parse(STR_INSTANT);
    }
}