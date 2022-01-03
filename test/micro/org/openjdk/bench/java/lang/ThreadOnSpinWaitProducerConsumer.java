/*
 * Copyright (c) 2021, Amazon.com Inc. or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import org.openjdk.jmh.infra.Blackhole;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * This microbenchmark models producer-consumer.
 *
 * The microbenchmark uses two thread: 1 for a producer, 1 for a consumer.
 * The microbenchmark uses BigInteger to have latencies of producing/consuming
 * data comparable with synchronization operations.
 *
 * Thread.onSpinWait is used in a spin loop which is used to avoid heavy locks.
 * In the spin loop volatile fields are checked. To reduce overhead accessing them
 * they are only checked after a number of iterations.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Threads(1)
public class ThreadOnSpinWaitProducerConsumer {
    @Param({"100"})
    public int maxNum;

    @Param({"125"})
    public int spinNum;

    @Param({"10"})
    public int checkSpinCondAfterIters;

    @Param({"256"})
    public int dataBitLength;

    private Thread threadProducer;
    private Thread threadConsumer;
    private Object monitor;

    private BigInteger a;
    private BigInteger b;
    private Blackhole bh;

    private volatile int dataId;
    private volatile int seenDataId;

    private int producedDataCount;
    private int consumedDataCount;

    private void produceData() {
        if (!isDataSeen()) {
            return;
        }

        b = a.not();
        ++dataId;
        ++producedDataCount;
    }

    private void consumeData() {
        if (isDataSeen()) {
            return;
        }
        bh.consume(a.equals(b.not()));
        seenDataId = dataId;
        ++consumedDataCount;
    }

    private boolean isDataSeen() {
        return seenDataId == dataId;
    }

    private boolean isNewData() {
        return seenDataId != dataId;
    }

    private boolean spinWaitForCondition(int spinNum, BooleanSupplier cond) {
        for (int i = 0; i < spinNum; ++i) {
            if ((i % checkSpinCondAfterIters) == 0 && cond.getAsBoolean()) {
                return true;
            }
            Thread.onSpinWait();
        }
        return cond.getAsBoolean();
    }

    void produce() {
        try {
            while (dataId < maxNum) {
                if (spinWaitForCondition(this.spinNum, this::isDataSeen)) {
                    synchronized (monitor) {
                        produceData();
                        monitor.notify();
                    }
                } else {
                    synchronized (monitor) {
                        while (!isDataSeen()) {
                            monitor.wait();
                        }

                        produceData();
                        monitor.notify();
                    }
                }
            }
        } catch (InterruptedException e) {}
    }

    void consume() {
        try {
            for (;;) {
                if (spinWaitForCondition(this.spinNum, this::isNewData)) {
                    synchronized (monitor) {
                         consumeData();
                         monitor.notify();
                    }
                } else {
                    synchronized (monitor) {
                        while (isDataSeen()) {
                            monitor.wait();
                        }

                        consumeData();
                        monitor.notify();
                    }
                }
            }
        } catch (InterruptedException e) {}
    }

    @Setup(Level.Trial)
    public void setup01() {
        Random rnd = new Random(111);
        a = BigInteger.probablePrime(dataBitLength, rnd);
        monitor = new Object();
    }

    @Setup(Level.Invocation)
    public void setup02() {
        threadProducer = new Thread(this::produce);
        threadConsumer = new Thread(this::consume);
    }

    @Benchmark
    public void trial(Blackhole bh) throws Exception {
        this.bh = bh;
        producedDataCount = 0;
        consumedDataCount = 0;
        dataId = 0;
        seenDataId = 0;
        threadProducer.start();
        threadConsumer.start();
        threadProducer.join();

        synchronized (monitor) {
            while (!isDataSeen()) {
                monitor.wait();
            }
        }
        threadConsumer.interrupt();

        if (producedDataCount != maxNum) {
            throw new RuntimeException("Produced: " + producedDataCount + ". Expected: " + maxNum);
        }
        if (producedDataCount != consumedDataCount) {
            throw new RuntimeException("produced != consumed: " + producedDataCount + " != " + consumedDataCount);
        }
    }
}
