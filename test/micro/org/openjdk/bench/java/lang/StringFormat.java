/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/*
 * This benchmark naively explores String::format/formatted performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class StringFormat {

    public String s = "str";
    public int i = 17;
    public static final BigDecimal pi = new BigDecimal(Math.PI);

    @Benchmark
    public String decimalFormat() {
        return "%010.3f".formatted(pi);
    }

    @Benchmark
    public String stringFormat() {
        return "0123456789 %s".formatted(s);
    }

    @Benchmark
    public String stringFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %s".formatted(s);
    }

    @Benchmark
    public String widthStringFormat() {
        return "0123456789 %3s".formatted(s);
    }

    @Benchmark
    public String widthStringFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %3s".formatted(s);
    }

    @Benchmark
    public String widthStringIntFormat() {
        return "0123456789 %3s %d".formatted(s, i);
    }

    @Benchmark
    public String widthStringIntFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %3s %d".formatted(s, i);
    }

    @Benchmark
    public String lineFormat() {
        return "0123456789 %n".formatted(i);
    }

    @Benchmark
    public String lineFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %n".formatted(i);
    }

    @Benchmark
    public String intFormat() {
        return "0123456789 %d".formatted(i);
    }

    @Benchmark
    public String intFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %d".formatted(i);
    }

    @Benchmark
    public String intIntFormat() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %d %d".formatted(i, i);
    }

    @Benchmark
    public String intIntFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %d %d".formatted(i, i);
    }

    @Benchmark
    public String intHexFormat() {
        return "0123456789 is %d %x".formatted(i, i);
    }

    @Benchmark
    public String intHexFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %d : %x".formatted(i, i);
    }

    @Benchmark
    public String intHexUFormat() {
        return "0123456789 is %d %X".formatted(i, i);
    }

    @Benchmark
    public String intHexUFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %d : %X".formatted(i, i);
    }

    @Benchmark
    public String intOctalFormat() {
        return "0123456789 is %d %o".formatted(i, i);
    }

    @Benchmark
    public String intOctalFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %d : %o".formatted(i, i);
    }

    @Benchmark
    public String stringIntFormat() {
        return "0123456789 %s : %d".formatted(s, i);
    }

    @Benchmark
    public String stringIntFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %s : %d".formatted(s, i);
    }

    @Benchmark
    public String stringIntRFormat() {
        return "0123456789 %s : %d 0123456789".formatted(s, i);
    }

    @Benchmark
    public String stringIntRFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %s : %d \u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d".formatted(s, i);
    }

    @Benchmark
    public String stringWidthIntFormat() {
        return "0123456789 %s : %3d".formatted(s, i);
    }

    @Benchmark
    public String stringWidthIntFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %s : %3d".formatted(s, i);
    }

    @Benchmark
    public String stringIntHexFormat() {
        return "0123456789 %s : %x".formatted(s, i);
    }

    @Benchmark
    public String stringIntHexUFormat() {
        return "0123456789 %s : %x".formatted(s, i);
    }

    @Benchmark
    public String stringIntOctalFormat() {
        return "0123456789 %s : %o".formatted(s, i);
    }

    @Benchmark
    public String stringIntOctalFormatUtf16() {
        return "\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d %s : %o".formatted(s, i);
    }

    @Benchmark
    public String complexFormat() {
        return "%3s %10d %4S %04X %4S %04X %4S %04X".formatted(s, i, s, i, s, i, s, i);
    }
}

