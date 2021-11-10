/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Datadog, Inc. All rights reserved.
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
package org.openjdk.bench.jdk.jfr;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import jdk.jfr.Recording;
import jdk.jfr.RecordingContext;
import jdk.jfr.RecordingContextFilter;
import jdk.jfr.RecordingContextKey;

public class RecordingContextBenchmark {

    static final RecordingContextKey contextKey1 =
        RecordingContextKey.forName("Key1");
    static final RecordingContextKey contextKey2 =
        RecordingContextKey.forName("Key2");
    static final RecordingContextKey contextKey3 =
        RecordingContextKey.forName("Key3");

    @State(Scope.Benchmark)
    static abstract class Base {

        protected Recording recording;
        protected RecordingContext context;

        final static int REPEAT = 1;

        @Setup
        public void setup() {
            recording = new Recording();
            recording.start();
            context = buildContext();
        }

        public abstract RecordingContext buildContext();

        @Benchmark
        public void withContext(Blackhole bh) {
            for (int i = 0; i < REPEAT; i++) {
                EmptyEventWithContext e = new EmptyEventWithContext();
                // bh.consume(e);
                e.commit();
            }
        }

        @Benchmark
        public void withoutContext(Blackhole bh) {
            for (int i = 0; i < REPEAT; i++) {
                EmptyEventWithoutContext e = new EmptyEventWithoutContext();
                // bh.consume(e);
                e.commit();
            }
        }

        @TearDown
        public void tearDown() {
            if (context != null) {
                context.close();
            }
            recording.close();
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 3)
    public static class Baseline extends Base {

        @Override
        public RecordingContext buildContext() {
            return null;
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 3)
    public static class One extends Base {

        @Override
        public RecordingContext buildContext() {
            return
                RecordingContext
                    .where(contextKey1, "Value1")
                    .build();
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 3)
    public static class Two extends Base {

        @Override
        public RecordingContext buildContext() {
            return
                RecordingContext
                    .where(contextKey1, "Value1")
                    .where(contextKey2, "Value2")
                    .build();
        }
    }

    @BenchmarkMode(Mode.AverageTime)
    @Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 3)
    public static class Three extends Base {

        @Override
        public RecordingContext buildContext() {
            return
                RecordingContext
                    .where(contextKey1, "Value1")
                    .where(contextKey2, "Value2")
                    .where(contextKey3, "Value3")
                    .build();
        }
    }
}
