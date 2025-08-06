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

/*
 * @test id=default
 * @summary Test virtual threads entering a lot of monitors with contention
 * @library /test/lib
 * @run main LotsOfContendedMonitorEnter
 */

import java.util.concurrent.CountDownLatch;
import jdk.test.lib.thread.VThreadRunner;

public class LotsOfContendedMonitorEnter {

    public static void main(String[] args) throws Exception {
        int depth;
        if (args.length > 0) {
            depth = Integer.parseInt(args[0]);
        } else {
            depth = 1024;
        }
        VThreadRunner.run(() -> testContendedEnter(depth));
    }

    /**
     * Enter the monitor for a new object, racing with another virtual thread that
     * attempts to enter around the same time, then repeat to the given depth.
     */
    private static void testContendedEnter(int depthRemaining) throws Exception {
        if (depthRemaining > 0) {
            var lock = new Object();

            // start thread to enter monitor for brief period, then enters again when signalled
            var started = new CountDownLatch(1);
            var signal = new CountDownLatch(1);
            var thread = Thread.ofVirtual().start(() -> {
                started.countDown();

                // enter, may be contended
                synchronized (lock) {
                    Thread.onSpinWait();
                }

                // wait to be signalled
                try {
                    signal.await();
                } catch (InterruptedException e) { }

                // enter again, this will block until the main thread releases
                synchronized (lock) {
                    // do nothing
                }
            });
            try {
                // wait for thread to start
                started.await();

                // enter, may be contended
                synchronized (lock) {
                    // signal thread to enter monitor again, it should block
                    signal.countDown();
                    await(thread, Thread.State.BLOCKED);
                    testContendedEnter(depthRemaining - 1);
                }
            } finally {
                thread.join();
            }
        }
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    private static void await(Thread thread, Thread.State expectedState) {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            assert state != Thread.State.TERMINATED : "Thread has terminated";
            Thread.yield();
            state = thread.getState();
        }
    }
}
