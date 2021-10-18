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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This microbenchmark models producer-consumer.
 * The consumer goes to 1ms sleep if data is not available.
 * To avoid this it uses Thread.onSpinWait() to wait for data from the producer.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class ThreadOnSpinWaitProducerConsumer {
    @Param({"100"})
    public int maxNum;

    @Param({"30"})
    public int spinNum;

    AtomicInteger counter;
    Thread threadProducer;
    Thread threadConsumer;
    volatile boolean consumed;

    void produce() {
        try {
            for (int i = 0; i < maxNum; ++i) {
                while (!consumed) {
                    Thread.sleep(0);
                }
                counter.incrementAndGet();
                consumed = false;
            }
        } catch (InterruptedException e) {}
    }

    void consume() {
        try {
            for (;;) {
                boolean goToSleep = true;
                for (int i = 0; i < spinNum; ++i) {
                    if (!consumed) {
                        goToSleep = false;
                        break;
                    }
                    Thread.onSpinWait();
                }

                if (goToSleep) {
                    while (consumed) {
                        Thread.sleep(1);
                    }
                }
                int v = counter.get();
                consumed = true;
                if (v >= maxNum) {
                    break;
                }
            }
        } catch (InterruptedException e) {}
    }

    @Setup(Level.Trial)
    public void foo() {
        counter = new AtomicInteger();
    }

    @Setup(Level.Invocation)
    public void setup() {
        counter.set(0);
        consumed = false;
        threadProducer = new Thread(this::produce);
        threadConsumer = new Thread(this::consume);
    }

    @Benchmark
    public void trial() throws Exception {
        threadProducer.start();
        threadConsumer.start();
        threadProducer.join();
        threadConsumer.join();
    }
}
