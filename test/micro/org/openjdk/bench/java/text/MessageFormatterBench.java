/*
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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Benchmark)
public class MessageFormatterBench {

    private Object[][] values;

    @Setup
    public void setup() {
        values = new Object[][]{
                new Object[]{Integer.valueOf(13), "MyDisk1"},
                new Object[]{Float.valueOf(25.6f), "MyDisk2"},
                new Object[]{Double.valueOf(123.89), "MyDisk3"},
                new Object[]{Long.valueOf(1234567), "MyDisk4"},
        };
    }

    private MessageFormat messageFormat = new MessageFormat("There is {0} GB of free space on the {1}.", Locale.ENGLISH);

    @Benchmark
    @OperationsPerInvocation(4)
    public void testMessageFormat(final Blackhole bh) {
        for (Object[] value : values) {
            bh.consume(messageFormat.format(value));
        }
    }

    public static void main(String... args) throws Exception {
        Options opts = new OptionsBuilder().include(MessageFormatterBench.class.getSimpleName()).shouldDoGC(true).build();
        new Runner(opts).run();
    }
}
