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

/* @test
 * @summary Basic tests for making sure ComputedConstant publishes values safely
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.lang
 * @enablePreview
 * @run junit LazyConstantSafePublicationTest
 */

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.LazyConstant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class LazyConstantSafePublicationTest {

    private static final int SIZE = 100_000;
    private static final int THREADS = Runtime.getRuntime().availableProcessors();

    static final class Holder {
        // These are non-final fields but should be seen
        // fully initialized thanks to the HB properties of ComputedConstants.
        int a, b, c, d, e;

        Holder() {
            a = b = c = d = e = 1;
        }
    }

    static final class Consumer implements Runnable {

        final LazyConstant<Holder>[] constants;
        final int[] observations = new int[SIZE];
        int i = 0;

        public Consumer(LazyConstant<Holder>[] constants) {
            this.constants = constants;
        }

        @Override
        public void run() {
            for (; i < SIZE; i++) {
                LazyConstant<Holder> s = constants[i];
                Holder h;
                // Wait until the ComputedConstant has a holder value
                while ((h = s.orElse(null)) == null) { Thread.onSpinWait();}
                int a = h.a;
                int b = h.b;
                int c = h.c;
                int d = h.d;
                int e = h.e;
                observations[i] = a + (b << 1) + (c << 2) + (c << 3) + (d << 4) + (e << 5);
            }
        }
    }

    static final class Producer implements Runnable {

        final LazyConstant<Holder>[] constants;

        public Producer(LazyConstant<Holder>[] constants) {
            this.constants = constants;
        }

        @Override
        public void run() {
            LazyConstant<Holder> s;
            long deadlineNs = System.nanoTime();
            for (int i = 0; i < SIZE; i++) {
                s = constants[i];
                s.get();
                deadlineNs += 1000;
                while (System.nanoTime() < deadlineNs) {
                    Thread.onSpinWait();
                }
            }
        }
    }

    @Test
    void mainTest() {
        final LazyConstant<Holder>[] constants = constants();
        List<Consumer> consumers = IntStream.range(0, THREADS)
                .mapToObj(_ -> new Consumer(constants))
                .toList();

        List<Thread> consumersThreads = IntStream.range(0, THREADS)
                .mapToObj(i -> Thread.ofPlatform()
                        .name("Consumer Thread " + i)
                        .start(consumers.get(i)))
                .toList();

        Producer producer = new Producer(constants);

        Thread producerThread = Thread.ofPlatform()
                .name("Producer Thread")
                .start(producer);

        join(constants, consumers, producerThread);
        join(constants, consumers, consumersThreads.toArray(Thread[]::new));

        int[] histogram = new int[64];
        for (Consumer consumer : consumers) {
            for (int i = 0; i < SIZE; i++) {
                histogram[consumer.observations[i]]++;
            }
        }

        // unless a = 1, ..., e = 1, zero observations should be seen
        for (int i = 0; i < 63; i++) {
            assertEquals(0, histogram[i]);
        }
        // a = 1, ..., e = 1 : index 2^5-1 = 63
        // All observations should end up in this bucket
        assertEquals(THREADS * SIZE, histogram[63]);
    }

    static void join(final LazyConstant<Holder>[] constants, List<Consumer> consumers, Thread... threads) {
        try {
            for (Thread t:threads) {
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
                while (t.isAlive()) {
                    t.join(TimeUnit.SECONDS.toMillis(10));
                    if (t.isAlive()) {
                        String stack = Arrays.stream(t.getStackTrace())
                                .map(Objects::toString)
                                .collect(Collectors.joining(System.lineSeparator()));
                        System.err.println(t + ": " + stack);
                        for (int i = 0; i < consumers.size(); i++) {
                            System.err.println("Consumer " + i + ": " + consumers.get(i).i);
                        }
                    }
                    if (System.nanoTime() > deadline) {
                        long nonNulls = CompletableFuture.supplyAsync(() ->
                                Arrays.stream(constants)
                                        .map(s -> s.orElse(null))
                                        .filter(Objects::nonNull)
                                        .count(), Executors.newSingleThreadExecutor()).join();
                        fail("Giving up! Set lazy constants seen by a new thread: " + nonNulls);
                    }
                }
            }
        } catch (InterruptedException ie) {
            fail(ie);
        }
    }

    static LazyConstant<Holder>[] constants() {
        @SuppressWarnings("unchecked")
        LazyConstant<Holder>[] constants = (LazyConstant<Holder>[]) new LazyConstant[SIZE];
        for (int i = 0; i < SIZE; i++) {
            constants[i] = LazyConstant.of(Holder::new);
        }
        return constants;
    }

}
