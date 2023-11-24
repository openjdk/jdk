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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static java.util.FormatProcessor.FMT;

/*
 * This benchmark measures StringTemplate.FMT FormatProcessor performance;
 * exactly mirroring {@link org.openjdk.bench.java.lang.StringFormat} benchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3, jvmArgsAppend = "--enable-preview")
public class StringTemplateFMT {

    public String s = "str";
    public int i = 17;

    @Benchmark
    public String stringFormat() {
        return FMT."%s\{s}";
    }

    @Benchmark
    public String stringIntFormat() {
        return FMT."%s\{s} %d\{i}";
    }

    @Benchmark
    public String widthStringFormat() {
        return FMT."%3s\{s}";
    }

    @Benchmark
    public String widthStringIntFormat() {
        return FMT."%3s\{s} %d\{i}";
    }

    @Benchmark
    public String complexFormat() {
        return FMT."%3s\{s} %10d\{i} %4S\{s} %04X\{i} %4S\{s} %04X\{i} %4S\{s} %04X\{i}";
    }
}
