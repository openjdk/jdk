/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VirtualThreadParking {

    @Param({"100", "1000", "10000"})
    int threadCount;

    /**
     * Starts N threads that time-park, main thread unparks.
     */
    @Benchmark
    public void timedParkAndUnpark1() throws Exception {
        var threads = new Thread[threadCount];
        var unparked = new boolean[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = Thread.ofVirtual().start(() -> {
                LockSupport.parkNanos(Long.MAX_VALUE);
            });
        }
        int remaining = threadCount;
        while (remaining > 0) {
            for (int i = 0; i < threadCount; i++) {
                if (!unparked[i]) {
                    Thread t = threads[i];
                    if (t.getState() == Thread.State.TIMED_WAITING) {
                        LockSupport.unpark(t);
                        unparked[i] = true;
                        remaining--;
                    }
                }
            }
            if (remaining > 0) {
                Thread.yield();
            }
        }
        for (Thread t : threads) {
            t.join();
        }
    }

    /**
     * Starts N threads that time-park, start another N threads to unpark.
     */
    @Benchmark
    public void timedParkAndUnpark2() throws Exception {
        var threads = new Thread[threadCount * 2];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = Thread.ofVirtual().start(() -> {
                LockSupport.parkNanos(Long.MAX_VALUE);
            });
        }
        for (int i = 0; i < threadCount; i++) {
            Thread thread1 = threads[i];
            Thread thread2 = Thread.ofVirtual().start(() -> {
                while (thread1.getState() != Thread.State.TIMED_WAITING) {
                    Thread.yield();
                }
                LockSupport.unpark(thread1);
            });
            threads[threadCount + i] = thread2;
        }
        for (Thread t : threads) {
            t.join();
        }
    }
}