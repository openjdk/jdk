/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Datadog, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.openjdk.bench.jdk.jfr.internal;

import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Registered;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.internal.Contextual;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;


@Warmup(iterations = 2)
@Measurement(iterations = 4)
@Fork(value = 1, jvmArgsAppend = {
        "--add-exports", "jdk.jfr/jdk.jfr.internal=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class ContextualEvents {
    @Label("Span Context Event")
    @Name("test.SpanContext")
    @Registered
    @Contextual
    public static class ContextualEvent extends Event {
        @Label("spanId")
        private String spanId;

        public ContextualEvent(String spanId) {
            this.spanId = spanId;
        }
    }

    @Label("Work Event")
    @Name("test.WorkContext")
    @Enabled
    @Registered
    public static class WorkEvent extends Event {
    }

    public static enum Experiment {
        NO_CONTEXT,
        CONTEXT
    }

    @Param({"NO_CONTEXT", "CONTEXT"})
    private Experiment experiment;

    private Recording recording;
    @Setup(Level.Iteration)
    public void setUp() {
        recording = new Recording();
        switch (experiment) {
            case NO_CONTEXT:
                recording.enable("test.WorkContext");
                break;
            case CONTEXT:
                recording.enable("test.SpanContext");
                recording.enable("test.WorkContext");
                break;
        }
        recording.start();
    }

    @TearDown(Level.Iteration)
    public void tearDown() {
        recording.close();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void test(Blackhole bh) {
        WorkEvent event = new WorkEvent();
        ContextualEvent ctx = new ContextualEvent("1234");
        ctx.begin();
        event.commit();
        ctx.end();
        bh.consume(event);
        bh.consume(ctx);
    }

}
