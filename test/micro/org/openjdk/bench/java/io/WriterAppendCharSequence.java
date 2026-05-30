/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package org.openjdk.bench.java.io;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring the cost calling Writer.append(CharSequence) for various
 * implementations. This targets the megamorphic cost of String.valueOf - see
 * JDK-8368292 for details.
 *
 * When poisoning is activated, it calls poisons heavily to pollute the
 * profiles.
 *
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = { "-XX:+UseParallelGC", "-Xmx3g" })
public class WriterAppendCharSequence {

    private static final int POISON_ITERATIONS = 1000000;

    @Param({ "true", "false" })
    private boolean poisonCallSites;

    @Param({ "buffer", "builder", "string" })
    private String inputType;

    private CharSequence input;
    private Writer writer;

    public static class BlackholeWriter extends Writer {
        protected final Blackhole blackhole;

        protected BlackholeWriter(Blackhole blackhole) {
            this.blackhole = blackhole;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(String str) throws IOException {
            blackhole.consume(str);
        }

    }

    private static final Object[] POISON_OBJECTS = new Object[] { "", 5, new HashMap<>(), new ArrayList<>(),
            new HashSet<>(), new Object[5], Collections.emptyList() };

    @Setup
    public void setup(Blackhole blackhole) throws IOException {
        input = switch (inputType) {
        case "buffer" -> new StringBuffer();
        case "builder" -> new StringBuilder();
        case "string" -> "";
        default -> null;
        };

        writer = new BlackholeWriter(blackhole);

        if (poisonCallSites) {
            for (int i = 0; i < POISON_ITERATIONS; i++) {
                for (Object obj : POISON_OBJECTS) {
                    blackhole.consume(String.valueOf(obj));
                }
            }
        }
    }

    @Benchmark
    public Writer appendInput() throws IOException {
        return writer.append(input);
    }

}
