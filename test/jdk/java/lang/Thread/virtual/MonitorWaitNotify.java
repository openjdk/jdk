/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test virtual threads using Object.wait/notifyAll
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run junit MonitorWaitNotify
 */

import java.util.concurrent.Semaphore;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonitorWaitNotify {

    /**
     * Test virtual thread waits, notified by platform thread.
     */
    @Test
    void testWaitNotify1() throws Exception {
        var lock = new Object();
        var ready = new Semaphore(0);
        var thread = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                ready.release();
                try {
                    lock.wait();
                } catch (InterruptedException e) { }
            }
        });
        // thread invokes notify
        ready.acquire();
        synchronized (lock) {
            lock.notifyAll();
        }
        thread.join();
    }

    /**
     * Test platform thread waits, notified by virtual thread.
     */
    @Test
    void testWaitNotify2() throws Exception {
        var lock = new Object();
        var ready = new Semaphore(0);
        var thread = Thread.ofVirtual().start(() -> {
            ready.acquireUninterruptibly();
            synchronized (lock) {
                lock.notifyAll();
            }
        });
        synchronized (lock) {
            ready.release();
            lock.wait();
        }
        thread.join();
    }

    /**
     * Test virtual thread waits, notified by another virtual thread.
     */
    @Test
    void testWaitNotify3() throws Exception {
        // need at least two carrier threads due to pinning
        int previousParallelism = VThreadRunner.ensureParallelism(2);
        try {
            var lock = new Object();
            var ready = new Semaphore(0);
            var thread1 = Thread.ofVirtual().start(() -> {
                synchronized (lock) {
                    ready.release();
                    try {
                        lock.wait();
                    } catch (InterruptedException e) { }
                }
            });
            var thread2 = Thread.ofVirtual().start(() -> {
                ready.acquireUninterruptibly();
                synchronized (lock) {
                    lock.notifyAll();
                }
            });
            thread1.join();
            thread2.join();
        } finally {
            // restore
            VThreadRunner.setParallelism(previousParallelism);
        }
    }

    /**
     * Test interrupt status set when calling Object.wait.
     */
    @Test
    void testWaitNotify4() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            t.interrupt();
            Object lock = new Object();
            synchronized (lock) {
                try {
                    lock.wait();
                    fail();
                } catch (InterruptedException e) {
                    // interrupt status should be cleared
                    assertFalse(t.isInterrupted());
                }
            }
        });
    }

    /**
     * Test interrupt when blocked in Object.wait.
     */
    @Test
    void testWaitNotify5() throws Exception {
        VThreadRunner.run(() -> {
            Thread t = Thread.currentThread();
            scheduleInterrupt(t, 1000);
            Object lock = new Object();
            synchronized (lock) {
                try {
                    lock.wait();
                    fail();
                } catch (InterruptedException e) {
                    // interrupt status should be cleared
                    assertFalse(t.isInterrupted());
                }
            }
        });
    }

    /**
     * Schedule a thread to be interrupted after a delay.
     */
    private static void scheduleInterrupt(Thread thread, long delay) {
        Runnable interruptTask = () -> {
            try {
                Thread.sleep(delay);
                thread.interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        new Thread(interruptTask).start();
    }
}
