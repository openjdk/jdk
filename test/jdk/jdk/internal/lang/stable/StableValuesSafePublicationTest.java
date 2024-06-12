/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for making sure StableValue publishes values safely
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} StableValuesSafePublicationTest.java
 * @run junit/othervm --enable-preview StableValuesSafePublicationTest
 */

import jdk.internal.lang.StableValue;
import jdk.internal.lang.StableValues;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

final class StableValuesSafePublicationTest {

    private static final int SIZE = 100_000;
    private static final int THREADS = Runtime.getRuntime().availableProcessors() / 2;

    static final class Holder {
        final int a;
        final int b;

        Holder() {
            a = 1;
            b = 1;
        }
    }

    static final class Consumer implements Runnable {

        final int[] observations = new int[SIZE];
        final StableValue<Holder>[] stables;

        Consumer(StableValue<Holder>[] stables) {
            this.stables = stables;
        }

        @Override
        public void run() {
            StableValue<Holder> s;
            Holder h;
            for (int i = 0; i < SIZE; i++) {
                // Wait until we see a new StableValue
                while ((s = stables[i]) == null) {}
                // Wait until the StableValue has a holder value
                while ((h = s.orElse(null)) == null) {}
                int a = h.a;
                int b = h.b;
                observations[i] = a + (b << 1);
            }
        }
    }

    static final class Producer implements Runnable {

        final StableValue<Holder>[] stables;

        Producer(StableValue<Holder>[] stables) {
            this.stables = stables;
        }

        @Override
        public void run() {
            int dummy = 0;
            StableValue<Holder> s;
            for (int i = 0; i < SIZE; i++) {
                s = StableValue.newInstance();
                s.trySet(new Holder());
                stables[i] = s;
                // Wait for a while
                for (int j = 0; j < 100; j++) {
                    dummy++;
                }
            }
            System.out.println(dummy);
        }
    }

    @Test
    void main() {
        @SuppressWarnings("unchecked")
        final StableValue<Holder>[] stables = (StableValue<Holder>[]) new StableValue[SIZE];

        List<Consumer> consumers = IntStream.range(0, THREADS)
                .mapToObj(_ -> new Consumer(stables))
                .toList();

        List<Thread> consumersThreads = IntStream.range(0, THREADS)
                .mapToObj(i -> Thread.ofPlatform()
                        .name("Consumer Thread " + i)
                        .start(consumers.get(i)))
                .toList();

        Thread producerThread = Thread.ofPlatform()
                .name("Producer Thread")
                .start(new Producer(stables));

        join(producerThread);
        join(consumersThreads.toArray(Thread[]::new));

        int[] histogram = new int[4];
        for (Consumer consumer : consumers) {
            for (int i = 0; i < SIZE; i++) {
                histogram[consumer.observations[i]]++;
            }
        }

        assertEquals(0, histogram[0]);
        assertEquals(0, histogram[1]);
        assertEquals(0, histogram[2]);
        // We should only observe a = b = 1 -> 3
        assertEquals(THREADS * SIZE, histogram[3]);
    }


    static void join(Thread... threads) {
        try {
            for (Thread t:threads) {
                t.join();
            }
        } catch (InterruptedException ie) {
            fail(ie);
        }
    }

}
