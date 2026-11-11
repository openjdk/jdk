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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

// This benchmark is used as an easy reproducer of JDK-8307970, aka
// "Thread.start is slow with large number of threads". Use the
// startOnly parameter to measure only the thread start, or to measure
// the full create, start and join cycle.

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Threads(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class ThreadStartWithIdleThreads {

    @Param({"0", "1000", "10000", "30000"})
    private int idleThreads;

    @Param({"true", "false"})
    private boolean startOnly;

    private volatile boolean done;
    private Thread[] ts;
    private Thread startOnlyThread;

    @Setup
    public void setup() throws InterruptedException {
        ts = new Thread[idleThreads];
        var started = new CountDownLatch(idleThreads);
        done = false;
        for (int i = 0; i < idleThreads; i++) {
            ts[i] = new Thread(() -> {
                    started.countDown();
                    while (!done) {
                        LockSupport.park();
                    }
            });
            ts[i].start();
        }
        started.await();
    }

    @Setup(Level.Invocation)
    public void setupLevel() throws InterruptedException {
        if (startOnly) {
            startOnlyThread = new Thread();
        }
    }

    @TearDown
    public final void cleanup() throws InterruptedException {
        done = true;
        if (ts != null) {
            for (Thread t : ts) {
                if (t != null) {
                    LockSupport.unpark(t);
                }
            }
            for (Thread t : ts) {
                if (t != null) {
                    t.join();
                }
            }
            ts = null;
        }
    }

    @TearDown(Level.Invocation)
    public final void cleanupLevel() throws InterruptedException {
        if (startOnly) {
            startOnlyThread.join();
        }
    }

    @Benchmark
    public void threadStartWithIdleThreads() throws Exception {
        if (startOnly) {
            startOnlyThread.start();
        } else {
            Thread t = new Thread();
            t.start();
            t.join();
        }
    }
}
