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

package org.openjdk.bench.loom;

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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

//@Fork(2)
@Fork(1)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@SuppressWarnings("preview")
public class Monitors2 {
    static Object[] globalLockArray;
    static ReentrantLock[] globalReentrantLockArray;
    static AtomicInteger workerCount = new AtomicInteger(0);
    static AtomicInteger dummyCounter = new AtomicInteger(0);
    static int workIterations;

    static int VT_COUNT;
    static int CARRIER_COUNT = 8;
    static ExecutorService scheduler = Executors.newFixedThreadPool(CARRIER_COUNT);

    //@Param({"8", "32", "256", "4096", "131072"})
    @Param({"8", "32", "256", "4096"})
    static int VT_MULTIPLIER;

    @Param({"1", "5", "12", "32"})
    static int MONITORS_CNT;

    @Param({"0", "50000", "100000", "200000"})
    static int WORKLOAD;

    @Param({"10"})
    static int STACK_DEPTH;

    void recursive4_1(int depth, int lockNumber) {
        if (depth > 0) {
            recursive4_1(depth - 1, lockNumber);
        } else {
            if (Math.random() < 0.5) {
                Thread.yield();
            }
            recursive4_2(lockNumber);
        }
    }

    void recursive4_2(int lockNumber) {
        if (lockNumber + 2 <= MONITORS_CNT - 1) {
            lockNumber += 2;
            synchronized(globalLockArray[lockNumber]) {
                Thread.yield();
                recursive4_2(lockNumber);
            }
        }
    }

    final Runnable FOO4 = () -> {
        int iterations = 10;
        while (iterations-- > 0) {
            int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITORS_CNT - 1);
            synchronized(globalLockArray[lockNumber]) {
                recursive4_1(lockNumber, lockNumber);
            }
        }
        workerCount.getAndIncrement();
    };

    /**
     *  Test contention on monitorenter with extra monitors on stack shared by all threads.
     */
    //@Benchmark
    public void testContentionMultipleMonitors(MyState state) throws Exception {
        workerCount.getAndSet(0);

        Thread batch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            batch[i] = virtualThreadBuilder(scheduler).name("BatchVT-" + i).start(FOO4);
        }

        for (int i = 0; i < VT_COUNT; i++) {
            batch[i].join();
        }

        if (workerCount.get() != VT_COUNT) {
            throw new RuntimeException("testContentionMultipleMonitors failed. Expected " + VT_COUNT + "but found " + workerCount.get());
        }
    }

    static void recursive5_1(int depth, int lockNumber, Object[] myLockArray) {
        if (depth > 0) {
            recursive5_1(depth - 1, lockNumber, myLockArray);
        } else {
            if (Math.random() < 0.5) {
                Thread.yield();
            }
            recursive5_2(lockNumber, myLockArray);
        }
    }

    static void recursive5_2(int lockNumber, Object[] myLockArray) {
        if (lockNumber + 2 <= MONITORS_CNT - 1) {
            lockNumber += 2;
            synchronized (myLockArray[lockNumber]) {
                if (Math.random() < 0.5) {
                    Thread.yield();
                }
                synchronized (globalLockArray[lockNumber]) {
                    Thread.yield();
                    recursive5_2(lockNumber, myLockArray);
                }
            }
        }
    }

    static final Runnable FOO5 = () -> {
        Object[] myLockArray = new Object[MONITORS_CNT];
        for (int i = 0; i < MONITORS_CNT; i++) {
            myLockArray[i] = new Object();
        }

        int iterations = 10;
        while (iterations-- > 0) {
            int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITORS_CNT - 1);
            synchronized (myLockArray[lockNumber]) {
                synchronized (globalLockArray[lockNumber]) {
                    recursive5_1(lockNumber, lockNumber, myLockArray);
                }
            }
        }
        workerCount.getAndIncrement();
    };

    /**
     *  Test contention on monitorenter with extra monitors on stack both local only and shared by all threads.
     */
    //@Benchmark
    public void testContentionMultipleMonitors2(MyState state) throws Exception {
        workerCount.getAndSet(0);

        // Create batch of VT threads.
        Thread batch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            //Thread.ofVirtual().name("FirstBatchVT-" + i).start(FOO);
            batch[i] = virtualThreadBuilder(scheduler).name("BatchVT-" + i).start(FOO5);
        }

        for (int i = 0; i < VT_COUNT; i++) {
            batch[i].join();
        }

        if (workerCount.get() != VT_COUNT) {
            throw new RuntimeException("testContentionMultipleMonitors2 failed. Expected " + VT_COUNT + "but found " + workerCount.get());
        }
    }

    static void recursiveSync(int depth) {
        if (depth > 0) {
            recursiveSync(depth - 1);
        } else {
            int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITORS_CNT);
            synchronized(globalLockArray[lockNumber]) {
                //Thread.yield();
                for (int i = 0; i < WORKLOAD; i++) {
                    dummyCounter.getAndIncrement();
                }
                workerCount.getAndIncrement();
            }
        }
    }

    static final Runnable SYNC = () -> {
        recursiveSync(STACK_DEPTH);
    };

    static void recursiveReentrant(int depth) {
        if (depth > 0) {
            recursiveReentrant(depth - 1);
        } else {
            int lockNumber = ThreadLocalRandom.current().nextInt(0, MONITORS_CNT);
            globalReentrantLockArray[lockNumber].lock();
            //Thread.yield();
            for (int i = 0; i < WORKLOAD; i++) {
                dummyCounter.getAndIncrement();
            }
            workerCount.getAndIncrement();
            globalReentrantLockArray[lockNumber].unlock();
        }
    }

    static final Runnable REENTRANTLOCK = () -> {
        recursiveReentrant(STACK_DEPTH);
    };

    public void runBenchmark(Runnable r) throws Exception {
        workerCount.getAndSet(0);

        // Create batch of VT threads.
        Thread batch[] = new Thread[VT_COUNT];
        for (int i = 0; i < VT_COUNT; i++) {
            batch[i] = virtualThreadBuilder(scheduler).name("BatchVT-" + i).start(r);
        }

        for (int i = 0; i < VT_COUNT; i++) {
            batch[i].join();
        }

        if (workerCount.get() != VT_COUNT) {
            throw new RuntimeException("testContentionMultipleMonitors2 failed. Expected " + VT_COUNT + "but found " + workerCount.get());
        }
    }

    @Benchmark
    public void testContentionReentrantLock(MyState state) throws Exception {
        runBenchmark(REENTRANTLOCK);
    }

    @Benchmark
    public void testContentionASync(MyState state) throws Exception {
        runBenchmark(SYNC);
    }

    //@Benchmark
    public void testExtraTime(MyState state) throws Exception {
        dummyCounter.getAndSet(0);
        // Takes ~120us
        for (int i = 0; i < 50000; i++) {
            dummyCounter.getAndIncrement();
        }
        if (dummyCounter.get() != 50000) {
            throw new RuntimeException("testContentionMultipleMonitors2 failed. Expected " + 50000 + "but found " + dummyCounter.get());
        }
    }

    @State(Scope.Thread)
    public static class MyState {

        @Setup(Level.Trial)
        public void doSetup() {
            // Setup up monitors/locks
            globalLockArray = new Object[MONITORS_CNT];
            globalReentrantLockArray = new ReentrantLock[MONITORS_CNT];
            for (int i = 0; i < MONITORS_CNT; i++) {
                globalLockArray[i] = new Object();
                globalReentrantLockArray[i] = new ReentrantLock();
            }
            // Setup VirtualThread count
            VT_COUNT = CARRIER_COUNT * VT_MULTIPLIER;

            System.out.println("Running test with MONITORS_CNT = " + MONITORS_CNT + " VT_COUNT = " + VT_COUNT + " and WORKLOAD = " + WORKLOAD);
            //System.out.println("Running test with VT_COUNT = " + VT_COUNT);
        }

        @TearDown(Level.Trial)
        public void doTearDown() {
            scheduler.shutdown();
            System.out.println("Do TearDown");
        }
    }

    private static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual();
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
            ctor.setAccessible(true);
            return (Thread.Builder.OfVirtual) ctor.newInstance(scheduler);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
