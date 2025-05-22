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

/**
 * @test id=NormalDeflation
 * @summary A collection of small tests using synchronized, wait, notify to try
 *          and achieve good cheap coverage of UseObjectMonitorTable.
 * @library /test/lib
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+UseObjectMonitorTable
 *                   UseObjectMonitorTableTest
 */

/**
 * @test id=ExtremeDeflation
 * @summary Run the same tests but with deflation running constantly.
 * @library /test/lib
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -XX:GuaranteedAsyncDeflationInterval=1
 *                   -XX:+UseObjectMonitorTable
 *                   UseObjectMonitorTableTest
 */

import jdk.test.lib.Utils;

import java.lang.Runnable;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.Random;
import java.util.stream.Stream;

public class UseObjectMonitorTableTest {
    static final ThreadFactory TF = Executors.defaultThreadFactory();

    static class WaitNotifyTest implements Runnable {
        static final int ITERATIONS = 10_000;
        static final int THREADS = 10;
        final WaitNotifySyncChannel startLatchChannel = new WaitNotifySyncChannel();
        final WaitNotifySyncChannel endLatchChannel = new WaitNotifySyncChannel();
        int count = 0;

        static class WaitNotifyCountDownLatch {
            int latch;
            WaitNotifyCountDownLatch(int count) {
                latch = count;
            }
            synchronized void await() {
                while (latch != 0) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("WaitNotifyTest: Unexpected interrupt", e);
                    }
                }
            }
            synchronized void countDown() {
                if (latch != 0) {
                    latch--;
                    if (latch == 0) {
                        notifyAll();
                    }
                }
            }
        }
        static class WaitNotifySyncChannel extends WaitNotifyCountDownLatch {
            WaitNotifyCountDownLatch object;
            WaitNotifySyncChannel() { super(0); }
            synchronized void send(WaitNotifyCountDownLatch object, int count) {
                await();
                latch = count;
                this.object = object;
                notifyAll();
            }
            synchronized WaitNotifyCountDownLatch receive() {
                while (latch == 0) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("WaitNotifyTest: Unexpected interrupt", e);
                    }
                }
                countDown();
                return object;
            }
        }
        synchronized int getCount() {
            return count;
        }
        synchronized void increment() {
            count++;
        }
        public void run() {
            System.out.println("WaitNotifyTest started.");
            for (int t = 0; t < THREADS; t++) {
                TF.newThread(() -> {
                    for (int i = 0; i < ITERATIONS; i++) {
                        startLatchChannel.receive().await();
                        increment();
                        endLatchChannel.receive().countDown();
                    }
                }).start();
            }
            for (int i = 0; i < ITERATIONS; i++) {
                WaitNotifyCountDownLatch startLatch = new WaitNotifyCountDownLatch(1);
                WaitNotifyCountDownLatch endLatch = new WaitNotifyCountDownLatch(THREADS);
                int count = getCount();
                if (count != i * THREADS) {
                    throw new RuntimeException("WaitNotifyTest: Invalid Count " + count +
                                               " pre-iteration " + i);
                }
                startLatchChannel.send(startLatch, 10);
                startLatch.countDown();
                endLatchChannel.send(endLatch, 10);
                endLatch.await();
            }
            int count = getCount();
            if (count != ITERATIONS * THREADS) {
                throw new RuntimeException("WaitNotifyTest: Invalid Count " + count);
            }
            System.out.println("WaitNotifyTest passed.");
        }
    }

    static class RandomDepthTest implements Runnable {
        static final int THREADS = 10;
        static final int ITERATIONS = 10_000;
        static final int MAX_DEPTH = 20;
        static final int MAX_RECURSION_COUNT = 10;
        static final double RECURSION_CHANCE = .25;
        final Random random = Utils.getRandomInstance();
        final Locker lockers[] = new Locker[MAX_DEPTH];
        final CyclicBarrier syncBarrier = new CyclicBarrier(THREADS + 1);
        int count = 0;

        class Locker {
            final int depth;
            Locker(int depth) {
                this.depth = depth;
            }
            synchronized int getCount() {
                if (depth == MAX_DEPTH) {
                    return count;
                }
                return lockers[depth].getCount();
            }
            synchronized void increment(int recursion_count) {
                if (recursion_count != MAX_RECURSION_COUNT &&
                    random.nextDouble() < RECURSION_CHANCE) {
                    this.increment(recursion_count + 1);
                    return;
                }
                if (depth == MAX_DEPTH) {
                    count++;
                    return;
                }
                lockers[depth + random.nextInt(MAX_DEPTH - depth)].increment(recursion_count);
            }
            synchronized Locker create() {
                if (depth != MAX_DEPTH) {
                    lockers[depth] = (new Locker(depth + 1)).create();
                }
                return this;
            }
        }
        int getCount() {
            return lockers[0].getCount();
        }
        void increment() {
            lockers[random.nextInt(MAX_DEPTH)].increment(0);
        }
        void create() {
            lockers[0] = (new Locker(1)).create();
        }
        void syncPoint() {
            try {
                syncBarrier.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("RandomDepthTest: Unexpected interrupt", e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException("RandomDepthTest: Unexpected broken barrier", e);
            }
        }
        public void run() {
            System.out.println("RandomDepthTest started.");
            for (int t = 0; t < THREADS; t++) {
                TF.newThread(() -> {
                    syncPoint();
                    for (int i = 0; i < ITERATIONS; i++) {
                        increment();
                    }
                    syncPoint();
                }).start();
            }
            create();
            syncPoint();
            syncPoint();
            int count = getCount();
            if (count != THREADS * ITERATIONS) {
                throw new RuntimeException("RandomDepthTest: Invalid Count " + count);
            }
            System.out.println("RandomDepthTest passed.");
        }
    }

    public static void main(String[] args) {
        Stream.of(
            TF.newThread(new WaitNotifyTest()),
            TF.newThread(new RandomDepthTest())
        ).map(t -> {
            t.start();
            return t;
        }).forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException("UseObjectMonitorTableTest: Unexpected interrupt", e);
            }
        });

        System.out.println("UseObjectMonitorTableTest passed.");
    }
}
