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
package org.openjdk.bench.java.time.format;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class DateTimeFormatterWithPaddingBench {

    private static final DateTimeFormatter FORMATTER_WITH_PADDING = new DateTimeFormatterBuilder()
            .appendLiteral("Date:")
            .padNext(20, ' ')
            .append(DateTimeFormatter.ISO_DATE)
            .toFormatter();

    private static final DateTimeFormatter FORMATTER_WITH_PADDING_ZERO = new DateTimeFormatterBuilder()
            .appendLiteral("Year:")
            .padNext(4)
            .appendValue(ChronoField.YEAR)
            .toFormatter();

    private static final DateTimeFormatter FORMATTER_WITH_PADDING_ONE = new DateTimeFormatterBuilder()
            .appendLiteral("Year:")
            .padNext(5)
            .appendValue(ChronoField.YEAR)
            .toFormatter();

    private final LocalDateTime now = LocalDateTime.now();

    @Benchmark
    public String formatWithPadding() {
        return FORMATTER_WITH_PADDING.format(now);
    }

    @Benchmark
    public String formatWithPaddingLengthZero() {
        return FORMATTER_WITH_PADDING_ZERO.format(now);
    }

    @Benchmark
    public String formatWithPaddingLengthOne() {
        return FORMATTER_WITH_PADDING_ONE.format(now);
    }

}
