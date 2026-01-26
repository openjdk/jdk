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

/*
 * @test
 * @bug 8373120
 * @summary Stress test two consecutive timed Object.wait calls where only the first one is notified.
 * @run main/othervm -XX:CompileCommand=exclude,java.lang.VirtualThread::afterYield NotifiedThenTimedOutWait 1 100 100
 */

/*
 * @test
 * @run main/othervm -XX:CompileCommand=exclude,java.lang.VirtualThread::afterYield NotifiedThenTimedOutWait 2 100 100
 */

import java.time.Instant;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;

public class NotifiedThenTimedOutWait {
    public static void main(String[] args) throws Exception {
        int race = (args.length > 0) ? Integer.parseInt(args[0]) : 1;
        int nruns = (args.length > 1) ? Integer.parseInt(args[1]) : 100;
        int iterations = (args.length > 2) ? Integer.parseInt(args[2]) : 100;

        for (int i = 1; i <= nruns; i++) {
            System.out.println(Instant.now() + " => " + i + " of " + nruns);
            switch (race) {
                case 1 -> race1(iterations);
                case 2 -> race2(iterations);
            }
        }
    }

    /**
     * Barrier in synchronized block.
     */
    private static void race1(int iterations) throws InterruptedException {
        final int timeout = 1;
        var lock = new Object();
        var start = new Phaser(2);
        var end = new Phaser(2);

        var vthread = Thread.ofVirtual().start(() -> {
            try {
                for (int j = 0; j < iterations; j++) {
                    synchronized (lock) {
                        start.arriveAndAwaitAdvance();
                        lock.wait(timeout);
                        lock.wait(timeout);
                    }
                    end.arriveAndAwaitAdvance();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        ThreadFactory factory = ThreadLocalRandom.current().nextBoolean()
                    ? Thread.ofPlatform().factory() : Thread.ofVirtual().factory();
        var notifier = factory.newThread(() -> {
            for (int j = 0; j < iterations; j++) {
                start.arriveAndAwaitAdvance();
                synchronized (lock) {
                    lock.notify();
                }
                end.arriveAndAwaitAdvance();
            }
        });
        notifier.start();

        vthread.join();
        notifier.join();
    }

    /**
     * Barrier before synchronized block.
     */
    private static void race2(int iterations) throws InterruptedException {
        final int timeout = 1;
        var lock = new Object();
        var start = new Phaser(2);

        var vthread = Thread.startVirtualThread(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    start.arriveAndAwaitAdvance();
                    synchronized (lock) {
                        lock.wait(timeout);
                        lock.wait(timeout);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        ThreadFactory factory = ThreadLocalRandom.current().nextBoolean()
                    ? Thread.ofPlatform().factory() : Thread.ofVirtual().factory();
        var notifier = factory.newThread(() -> {
            for (int i = 0; i < iterations; i++) {
                start.arriveAndAwaitAdvance();
                synchronized (lock) {
                    lock.notify();
                }
            }
        });
        notifier.start();

        vthread.join();
        notifier.join();
    }
}
