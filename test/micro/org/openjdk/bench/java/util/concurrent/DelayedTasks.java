/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Supplier;
import org.openjdk.jmh.annotations.*;

/**
 * Benchmark to compare delayed task scheduling with ScheduledThreadPoolExcutor and ForkJoinPool.
 */

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 3)
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class DelayedTasks {

    private Supplier<ScheduledExecutorService> stpeSupplier;
    private Supplier<ScheduledExecutorService> fjpSupplier;

    @Setup
    public void setup() {
        stpeSupplier = () -> {
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            ((ScheduledThreadPoolExecutor) executor).setRemoveOnCancelPolicy(true);
            return executor;
        };
        int nprocs = Runtime.getRuntime().availableProcessors();
        fjpSupplier = () -> new ForkJoinPool(nprocs);
    }

    @Param({"100", "1000", "10000"})
    int delayedTasks;

    // delayed tasks cancelled by main thread
    private void mainThreadCancels(Supplier<ScheduledExecutorService> supplier) {
        try (ScheduledExecutorService ses = supplier.get()) {
            var futures = new ScheduledFuture[delayedTasks];
            for (int i = 0; i < delayedTasks; i++) {
                futures[i] = ses.schedule(() -> { }, 30L, TimeUnit.MINUTES);
            }
            for (ScheduledFuture<?> f : futures) {
                f.cancel(false);
            }
        }
    }

    // delayed tasks cancelled by virtual threads
    private void virtualThreadCancels(Supplier<ScheduledExecutorService> supplier) throws Exception {
        try (ScheduledExecutorService ses = supplier.get()) {
            var futures = new ScheduledFuture[delayedTasks];
            var threads = new Thread[delayedTasks];
            for (int i = 0; i < delayedTasks; i++) {
                ScheduledFuture<?> future = ses.schedule(() -> { }, 30L, TimeUnit.MINUTES);
                futures[i] = future;
                threads[i] = Thread.ofVirtual().start(() -> future.cancel(false));
            }
            for (Thread t : threads) {
                t.join();
            }
        }
    }

    // delayed task executes
    private void delayedTaskExecutes(Supplier<ScheduledExecutorService> supplier) throws Exception {
        try (ScheduledExecutorService ses = supplier.get()) {
            var futures = new ScheduledFuture[delayedTasks];
            for (int i = 0; i < delayedTasks; i++) {
                futures[i] = ses.schedule(() -> { }, 10L, TimeUnit.MILLISECONDS);
            }
            for (ScheduledFuture<?> f : futures) {
                f.get();
            }
        }
    }

    @Benchmark
    public void spteMainThreadCancels() {
        mainThreadCancels(stpeSupplier);
    }

    @Benchmark
    public void spteVirtualThreadCancels() throws Exception {
        virtualThreadCancels(stpeSupplier);
    }

    @Benchmark
    public void spteDelayedTaskExecutes() throws Exception {
        delayedTaskExecutes(stpeSupplier);
    }

    @Benchmark
    public void fjpMainThreadCancels() {
        mainThreadCancels(fjpSupplier);
    }

    @Benchmark
    public void fjpVirtualThreadCancels() throws Exception {
        virtualThreadCancels(fjpSupplier);
    }

    @Benchmark
    public void fjpDelayedTaskExecutes() throws Exception {
        delayedTaskExecutes(fjpSupplier);
    }
}