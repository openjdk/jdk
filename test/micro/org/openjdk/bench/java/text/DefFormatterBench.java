/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Benchmark)
public class DefFormatterBench {

    public static final int VALUES_SIZE = 13;
    public double[] values;
    public BigDecimal[] bdLargeValues;
    public BigDecimal[] bdSmallValues;

    @Setup(Level.Invocation)
    public void setup() {
        values = new double[] {
            1.23, 1.49, 1.80, 1.7, 0.0, -1.49, -1.50, 9999.9123, 1.494, 1.495, 1.03, 25.996, -25.996
        };

        bdLargeValues = DoubleStream.of(values)
                .mapToObj(BigDecimal::new)
                .toArray(BigDecimal[]::new);

        bdSmallValues = DoubleStream.of(values)
                .mapToObj(BigDecimal::valueOf)
                .toArray(BigDecimal[]::new);
    }

    private DefNumberFormat dnf = new DefNumberFormat();

    @Benchmark
    @OperationsPerInvocation(VALUES_SIZE)
    public void testDefNumberFormatter(final Blackhole blackhole) {
        for (double value : values) {
            blackhole.consume(this.dnf.format(value));
        }
    }

    @Benchmark
    @OperationsPerInvocation(VALUES_SIZE)
    public void testSmallBigDecDefNumberFormatter(final Blackhole blackhole) {
        for (BigDecimal value : bdSmallValues) {
            blackhole.consume(this.dnf.format(value));
        }
    }

    @Benchmark
    @OperationsPerInvocation(VALUES_SIZE)
    public void testLargeBigDecDefNumberFormatter(final Blackhole blackhole) {
        for (BigDecimal value : bdLargeValues) {
            blackhole.consume(this.dnf.format(value));
        }
    }

    public static void main(String... args) throws Exception {
        Options opts = new OptionsBuilder().include(DefFormatterBench.class.getSimpleName()).shouldDoGC(true).build();
        new Runner(opts).run();
    }

    private static class DefNumberFormat {

        private final NumberFormat n;

        public DefNumberFormat() {
            this.n = NumberFormat.getInstance(Locale.ENGLISH);
            this.n.setMaximumFractionDigits(2);
            this.n.setMinimumFractionDigits(2);
            this.n.setGroupingUsed(false);
        }

        public String format(final double d) {
            return this.n.format(d);
        }

        public String format(final BigDecimal bd) {
            return this.n.format(bd);
        }
    }
}
