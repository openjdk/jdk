/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HexFormat;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Tests java.net.URLEncoder.encode and Decoder.decode.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class HexFormatBench {

    @Param({"512"})
    public int size;

    public byte[] bytes;

    public StringBuilder builder = new StringBuilder(size * 2);

    HexFormat LOWER_FORMATTER = HexFormat.of();
    HexFormat UPPER_FORMATTER = HexFormat.of().withUpperCase();

    @Setup
    public void setupStrings() {
        Random random = new Random(3);
        bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte)random.nextInt(16);
        }
    }

    @Benchmark
    public StringBuilder appenderLower() {
        builder.setLength(0);
        return HexFormat.of().formatHex(builder, bytes);
    }

    @Benchmark
    public StringBuilder appenderUpper() {
        builder.setLength(0);
        return HexFormat.of().withUpperCase().formatHex(builder, bytes);
    }

    @Benchmark
    public StringBuilder appenderLowerCached() {
        builder.setLength(0);
        return LOWER_FORMATTER.formatHex(builder, bytes);
    }

    @Benchmark
    public StringBuilder appenderUpperCached() {
        builder.setLength(0);
        return UPPER_FORMATTER.formatHex(builder, bytes);
    }

    @Benchmark
    public String formatLower() {
        return HexFormat.of().formatHex(bytes);
    }

    @Benchmark
    public String formatUpper() {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    @Benchmark
    public String formatLowerCached() {
        return LOWER_FORMATTER.formatHex(bytes);
    }

    @Benchmark
    public String formatUpperCached() {
        return UPPER_FORMATTER.formatHex(bytes);
    }

    @Benchmark
    public void toHexLower(Blackhole bh) {
        for (byte b : bytes) {
            bh.consume(HexFormat.of().toHighHexDigit(b));
            bh.consume(HexFormat.of().toLowHexDigit(b));
        }
    }

    @Benchmark
    public void toHexUpper(Blackhole bh) {
        for (byte b : bytes) {
            bh.consume(HexFormat.of().withUpperCase().toHighHexDigit(b));
            bh.consume(HexFormat.of().withUpperCase().toLowHexDigit(b));
        }
    }

    @Benchmark
    public void toHexLowerCached(Blackhole bh) {
        for (byte b : bytes) {
            bh.consume(LOWER_FORMATTER.toHighHexDigit(b));
            bh.consume(LOWER_FORMATTER.toLowHexDigit(b));
        }
    }

    @Benchmark
    public void toHexUpperCached(Blackhole bh) {
        for (byte b : bytes) {
            bh.consume(UPPER_FORMATTER.toHighHexDigit(b));
            bh.consume(UPPER_FORMATTER.toLowHexDigit(b));
        }
    }

    @Benchmark
    public void toHexDigitsByte(Blackhole bh) {
        for (byte b : bytes) {
            bh.consume(LOWER_FORMATTER.toHexDigits(b));
        }
    }

    @Benchmark
    public void toHexDigitsShort(Blackhole bh) {
        for (short b : bytes) {
            bh.consume(LOWER_FORMATTER.toHexDigits(b));
        }
    }

    @Benchmark
    public void toHexDigitsInt(Blackhole bh) {
        for (int b : bytes) {
            bh.consume(LOWER_FORMATTER.toHexDigits(b));
        }
    }

    @Benchmark
    public void toHexDigitsLong(Blackhole bh) {
        for (long b : bytes) {
            bh.consume(LOWER_FORMATTER.toHexDigits(b));
        }
    }
}
