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
package org.openjdk.bench.java.io;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)

public class PrimitiveFieldSerializationBenchmark {

    public static void main(String[] args) throws Exception {

        Options options = new OptionsBuilder()
                .include(PrimitiveFieldSerializationBenchmark.class.getSimpleName())
                .build();
        new Runner(options).run();
    }

    @State(Scope.Benchmark)
    public static class Log {

        MyData myData = new MyData((byte) 1, 'a', (short) 47, 1234, 0.01f, 1234L, 0.01d);
        MyRecord myRecord = new MyRecord((byte) 1, 'a', (short) 47, 1234, 0.01f, 1234L, 0.01d);
    }

    private OutputStream bos;
    private ObjectOutputStream os;

    @Setup
    public void setupStreams(Blackhole bh) throws IOException {
        bos = new BlackholeOutputStream(bh);
        os = new ObjectOutputStream(bos);
    }

    @TearDown
    public void tearDownStreams() throws IOException {
        os.close();
        bos.close();
    }

    private static final class MyData implements Serializable {
        byte b;
        char c;
        short s;
        int i;
        float f;
        long l;
        double d;

        public MyData(byte b, char c, short s, int i, float f, long l, double d) {
            this.b = b;
            this.c = c;
            this.s = s;
            this.i = i;
            this.f = f;
            this.l = l;
            this.d = d;
        }
    }

    private record MyRecord(byte b,
                            char c,
                            short s,
                            int i,
                            float f,
                            long l,
                            double d) implements Serializable {
    }

    @Benchmark
    public void serializeData(Log input) throws IOException {
        os.writeObject(input.myData);
    }

    @Benchmark
    public void serializeRecord(Log input) throws IOException {
        os.writeObject(input.myRecord);
    }

    public static final class BlackholeOutputStream extends OutputStream {

        private final Blackhole bh;

        public BlackholeOutputStream(Blackhole bh) {
            this.bh = bh;
        }

        @Override
        public void write(int b) {
            bh.consume(b);
        }

        @Override
        public void write(byte[] b) {
            bh.consume(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            bh.consume(b);
        }
    }


}
