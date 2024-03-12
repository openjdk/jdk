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
package org.openjdk.bench.java.util.concurrent;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations =  5, time = 5)
@Measurement(iterations =  5, time = 5)
public class UnparkBenchSleepersAfter {

    /*
        This micro creates thousands of sleeper threads after the threads doing the barrier await
        to see if that has any effect on the barrier performance.
     */

    @Param({"4000"})
    int idles;

    @Param({"2"})
    int workers;

    CyclicBarrier barrier;

    @Benchmark
    public void barrier() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(workers);
        for (int i = 0; i < workers; i++) {
            exec.submit(() ->
            {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    barrier.reset();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
    }

    IdleRunnable[] idleRunnables;

    ExecutorService exec;

    @Setup
    public void setup() throws InterruptedException {
        barrier = new CyclicBarrier(workers);
        exec = Executors.newFixedThreadPool(workers);
        CountDownLatch latch = new CountDownLatch(workers);
        for (int i = 0; i < workers; i++) { // warmup exec
            exec.submit(() -> {
                try {
                    Thread.sleep(0);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        idleRunnables = new IdleRunnable[idles];
        for(int i = 0; i < idles; i++) {
            IdleRunnable r = new IdleRunnable();
            idleRunnables[i] = r;
            new Thread(r).start();
        }
    }

    @TearDown
    public void tearDown() {
        for (IdleRunnable r : idleRunnables) {
            r.stop();
        }
        exec.shutdown();
    }

    public static class IdleRunnable implements Runnable {
        volatile boolean done;
        Thread myThread;

        @Override
        public void run() {
            myThread = Thread.currentThread();
            while (!done) {
                LockSupport.park();
            }
        }

        public void stop() {
            done = true;
            LockSupport.unpark(myThread);
        }
    }

}
