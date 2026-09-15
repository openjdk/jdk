/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util.concurrent;

import java.util.*;
import java.util.concurrent.*;
import org.openjdk.jmh.annotations.*;

/**
 * Benchmark to ascertain the latency in exchanges
 */

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1)
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class Exchangers {
    @Param({"10", "100", "1000"})
    private int exchanges;

    @Param({"false", "true"})
    private boolean virtual;

    private final Exchanger<Object> exchanger = new Exchanger<Object>();

    private static final Object ping = new Object();
    private static final Object pong = new Object();

    // Only accessed by bench runner
    private CountDownLatch start;
    private Future<?> participant;
    private ExecutorService exec;

    @Setup(Level.Trial)
    public void init() {
        if (exchanges < 0)
            throw new IllegalArgumentException("Number of exchanges most be a non-negative number");

        if (participant != null)
            throw new IllegalStateException("Tasks must not exist prior to setup");

        if (exec != null)
            throw new IllegalStateException("ExecutorService must not exist prior to setup");

        exec = Executors.newFixedThreadPool(
                1,
                (virtual ? Thread.ofVirtual() : Thread.ofPlatform()).factory()
        );
    }

    @TearDown(Level.Trial)
    public void uninit() throws InterruptedException {
        exec.close();
        exec = null;
        participant = null;
    }

    @Setup(Level.Invocation)
    public void setup() throws InterruptedException {
        final var noOfExchanges = exchanges;
        final var xchg = exchanger;

        final var ready = new CountDownLatch(1);
        final var begin = (start = new CountDownLatch(1));

        participant = exec.submit(() -> {
            ready.countDown();
            try {
                begin.await();
                for (int n = 0; n < noOfExchanges; ++n)
                    xchg.exchange(pong);
            } catch (InterruptedException ie) {
                throw new IllegalStateException("Was interrupted", ie);
            }
            return null;
        });

        ready.await(); // wait for participant to become ready
    }

    @TearDown(Level.Invocation)
    public void tearDown() throws InterruptedException, ExecutionException {
        participant.get(); // Ensure that the task is done before progressing
    }

    @Benchmark
    public void exchange() throws InterruptedException, ExecutionException {
        start.countDown(); // Signal exchangers to start
        final int noOfExchanges = exchanges;
        final Exchanger<Object> xchg = exchanger;
        for (int n = 0; n < noOfExchanges; ++n)
            xchg.exchange(ping);
    }
}