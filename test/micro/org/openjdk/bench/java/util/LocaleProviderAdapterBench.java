/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.util;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.BreakIterator;
import java.text.Collator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.DecimalFormatSymbols;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Benchmarks for LocaleProviderAdapter to measure the performance of various
 * locale-sensitive operations such as number formatting, date formatting,
 * and localized date/time pattern retrieval.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3)
public class LocaleProviderAdapterBench {
    /**
     * Benchmark for testing NumberFormat provider adapter.
     * Measures the performance of getting a NumberFormat instance for Locale.ENGLISH.
     *
     * @return A NumberFormat instance for Locale.ENGLISH.
     */
    @Benchmark
    public NumberFormat getNumberFormat() {
        return NumberFormat.getInstance(Locale.ENGLISH);
    }

    /**
     * Benchmark for testing DateFormat provider adapter.
     * Measures the performance of creating a SimpleDateFormat instance with a specific pattern.
     *
     * @return A DateFormat instance (SimpleDateFormat) with pattern "yyyy-MM-dd".
     */
    @Benchmark
    public DateFormat getDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd");
    }

    /**
     * Benchmark for testing DecimalFormatSymbols provider adapter.
     * Measures the performance of getting DecimalFormatSymbols instance for Locale.ENGLISH.
     *
     * @return A DecimalFormatSymbols instance for Locale.ENGLISH.
     */
    @Benchmark
    public DecimalFormatSymbols getDecimalFormatSymbols() {
        return DecimalFormatSymbols.getInstance(Locale.ENGLISH);
    }

    /**
     * Benchmark for testing JavaTimeDateTimePattern provider adapter.
     * Measures the performance of retrieving a localized date/time pattern.
     *
     * @return A localized date/time pattern string for Locale.CHINA.
     */
    @Benchmark
    public String getLocalizedDateTimePattern() {
        return DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.FULL,
                FormatStyle.FULL,
                IsoChronology.INSTANCE,
                Locale.CHINA);
    }
}
