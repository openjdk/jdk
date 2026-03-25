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

/**
 * @test
 * @bug 8373944
 * @summary Suspend thread while it's trying to acquire a monitor when unmounted vthreads are in the queue
 * @requires vm.continuations
 * @requires vm.jvmti
 * @library /test/lib /test/hotspot/jtreg/testlibrary
 * @run main/othervm/native SuspendResume3
 */

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;

import jvmti.JVMTIUtils;

public class SuspendResume3 {
    int iterations;
    int dummyCounter;
    Object lock = new Object();
    Phaser allSync = new Phaser(3);

    SuspendResume3 (int iterations) {
        this.iterations = iterations;
    }

    void worker1(Phaser sync1, Phaser sync2) {
        for (int i = 0; i < iterations; i++) {
            synchronized (lock) {
                sync1.arriveAndAwaitAdvance();
                sync2.arriveAndAwaitAdvance();
            }
            allSync.arriveAndAwaitAdvance();
        }
    };

    void worker2(Phaser sync1, Phaser sync2) {
        for (int i = 0; i < iterations; i++) {
            sync1.arriveAndAwaitAdvance();
            synchronized (lock) {
                sync2.arriveAndAwaitAdvance();
            }
            allSync.arriveAndAwaitAdvance();
        }
    };

    void vthreadWorker(CountDownLatch started) {
        started.countDown();
        synchronized (lock) {
            dummyCounter++;
        }
    }

    private void runTest() throws Exception {
        final Phaser w1Sync1 = new Phaser(2);
        final Phaser w1Sync2 = new Phaser(2);
        Thread worker1 = Thread.ofPlatform().start(() -> worker1(w1Sync1, w1Sync2));

        final Phaser w2Sync1 = new Phaser(2);
        final Phaser w2Sync2 = new Phaser(2);
        Thread worker2 = Thread.ofPlatform().start(() -> worker2(w2Sync1, w2Sync2));

        for (int i = 0; i < iterations; i++) {
            // Wait until worker1 acquires monitor
            w1Sync1.arriveAndAwaitAdvance();
            // Let worker2 block on monitor
            w2Sync1.arriveAndAwaitAdvance();
            await(worker2, Thread.State.BLOCKED);

            // Suspend worker2
            JVMTIUtils.suspendThread(worker2);

            // Add umounted vthread to _entry_list
            var started = new CountDownLatch(1);
            Thread vthread = Thread.ofVirtual().start(() -> vthreadWorker(started));
            started.await();
            await(vthread, Thread.State.BLOCKED);

            // Now let worker1 release the monitor picking worker2
            // as successor. Since worker2 is suspended, it will wake
            // up, acquire the monitor and release it, unparking the
            // unmounted thread as next successor.
            w1Sync2.arriveAndAwaitAdvance();

            // Force safepoint
            System.gc();

            // Let vthread terminate
            vthread.join();

            // Resume worker2
            JVMTIUtils.resumeThread(worker2);
            w2Sync2.arriveAndAwaitAdvance();

            if ((i % 10) == 0) {
                System.out.println(Instant.now() + " => " + i + " of " + iterations);
            }
            allSync.arriveAndAwaitAdvance();
        }

        worker1.join();
        worker2.join();
    }

    public static void main(String[] args) throws Exception {
        int iterations = (args.length > 0) ? Integer.parseInt(args[0]) : 100;

        SuspendResume3 obj = new SuspendResume3(iterations);
        obj.runTest();
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    private void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            assertTrue(state != Thread.State.TERMINATED, "Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }
}
