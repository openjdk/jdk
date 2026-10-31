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
 * @bug 8390031
 * @summary Suspend one or more vthreads while it's trying to acquire a monitor
 *          and check successor selection works as expected
 *
 * @requires vm.continuations
 * @requires vm.jvmti
 * @library /test/lib /test/hotspot/jtreg/testlibrary
 * @run main/othervm/native SuspendResume5
 */

import java.util.concurrent.CountDownLatch;

import jvmti.JVMTIUtils;

public class SuspendResume5 {
    static volatile Thread successor;
    static volatile Thread owner;
    static final Object lock = new Object();


    static void worker(CountDownLatch started) {
        started.countDown();
        synchronized (lock) {
            // Mark this thread as owner - this is only used to check
            // the owner is not the main thread.
            owner = Thread.currentThread();
            // Update successor only if it is null - that means we were
            // the selected successor when the main thread first released
            // the monitor.
            if (successor == null) {
                successor = owner;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        test1();
        test2();
        test3();
    }

    // Simple test that a suspended vthread doesn't get selected as
    // successor when the monitor is released.
    static void test1() throws Exception {
        Thread suspendee = null;
        boolean suspended = false;
        try {
            synchronized(lock) {
                final CountDownLatch started = new CountDownLatch(1);
                suspendee = Thread.ofVirtual().start(() -> worker(started));
                suspendee.setName("Suspendee");
                started.await();
                await(suspendee, Thread.State.BLOCKED);
                JVMTIUtils.suspendThread(suspendee);
                suspended = true;
            }

            // As thread is suspended whilst waiting on the lock it should not
            // be made the successor or acquire the lock. Grab the lock again and
            // check there has been no progress. Then insert a second thread.

            Thread thread2;

            synchronized(lock) {
                assertTrue(successor == null,
                           "Suspended vthread acquired the lock and executed");

                // Now let a second thread block on the lock
                final CountDownLatch started = new CountDownLatch(1);
                thread2 = Thread.ofVirtual().start(() -> worker(started));
                thread2.setName("Thread-2");
                started.await();
                await(thread2,Thread.State.BLOCKED);
            }
            thread2.join();
            assertTrue(successor == thread2,
                       "Successor not as expected: " + successor);
            successor = null;
        }
        finally {
            if (suspended) {
                JVMTIUtils.resumeThread(suspendee);
            }
            if (suspendee != null) {
                suspendee.join();
                assertTrue(successor == suspendee,
                           "Successor should be " + suspendee +
                           " but was actually " + successor);
            }
        }
    }

    // More complicated test that a suspended vthread doesn't get selected as
    // successor when the monitor is released. We use 3 queued threads and
    // cycle through which are suspended and which not, and so which should
    // get the lock next. Note that when all threads are suspended and then
    // resumed they will race to acquire the lock.
    static void test2() throws Exception {
        Thread me = Thread.currentThread();

        final int nThreads = 3;

        // The test matrix is as follows where S is suspended and R runnable.
        // Threads -> Expected successor index
        // S1S2S3  -> nil
        // S1S2R1  -> 2
        // S1R1S2  -> 1
        // S1R1R2  -> 1
        // R1S1S2  -> 0
        // R1S1R2  -> 0
        // R1R2S1  -> 0
        // R1R2R3  -> 0

        for (int mask = (1 << nThreads) - 1; mask >= 0; mask--) {
            System.out.println("Processing mask: " + mask);
            Thread[] threads = new Thread[nThreads];
            boolean[] suspended = new boolean[nThreads];
            try{
                successor = null;
                // Start all threads whilst holding the lock
                synchronized(lock) {
                    owner = me;
                    // Start the threads one at a time and allow them to block
                    // so we know the entry queue order.
                    for (int i = 0; i < nThreads; i++) {
                        final CountDownLatch started = new CountDownLatch(1);
                        threads[i] = Thread.ofVirtual().start(() -> worker(started));
                        threads[i].setName("Thread-" + i);
                        started.await();
                        await(threads[i], Thread.State.BLOCKED);
                        boolean suspend = (mask & (1 << (nThreads - 1 - i))) != 0;
                        if (suspend) {
                            JVMTIUtils.suspendThread(threads[i]);
                            suspended[i] = true;
                        }
                    }
                }

                boolean allSuspended = (mask == (1 << nThreads) - 1);
                // We don't want to grab the lock before the other eligible
                // threads have had a chance to claim it.
                if (!allSuspended) {
                    while (owner == me) {
                        Thread.yield();
                    }
                }

                // Grab the lock again and see whether the successor is as expected.
                synchronized(lock) {
                    // If all threads are suspended then we need a different check.
                    if (allSuspended) {
                        assertTrue(owner == me, "Unexpected owner: " + owner);
                        assertTrue(successor == null, "Unexpected successor: " + successor);
                    } else {
                        // Expected successor is the first non-suspended thread.
                        int expected = -1;
                        for (int i = 0; i < nThreads; i++) {
                            if (suspended[i]) {
                                assertTrue(successor != threads[i],
                                           "Suspended thread was successor: " + threads[i]);
                            } else {
                                expected = i;
                                break;
                            }
                        }
                        assertTrue(successor == threads[expected],
                                   "Successor should be " + threads[expected] +
                                   " but was actually " + successor);
                    }
                }
            }
            finally {
                // Now resume the threads
                for (int i = 0; i < nThreads; i++) {
                    if (suspended[i]) {
                        System.out.println("Resuming thread: " + threads[i]);
                        JVMTIUtils.resumeThread(threads[i]);
                    }
                }
                for (Thread t : threads) {
                    if (t != null) {
                        System.out.println("Joining thread: " + t);
                        t.join(10000);
                        if (t.isAlive()) {
                            System.out.println("Failed to join thread: " + t);
                            System.out.println("First successor was " + successor);
                            System.out.println("Last owner was " + owner);
                            t.join(); // let the test timeout
                        }
                    }
                }
            }
        }
    }

    // Test that resumed threads get the monitor as expected.
    static void test3() throws Exception {
        Thread me = Thread.currentThread();

        final int nThreads = 3;

        for (int firstResumer = 0; firstResumer < nThreads; firstResumer++) {
            Thread[] threads = new Thread[nThreads];
            boolean[] suspended = new boolean[nThreads];
            try{
                successor = null;
                // Start all threads whilst holding the lock
                synchronized(lock) {
                    owner = me;
                    // Start the threads one at a time and allow them to block
                    // so we know the entry queue order.
                    for (int i = 0; i < nThreads; i++) {
                        final CountDownLatch started = new CountDownLatch(1);
                        threads[i] = Thread.ofVirtual().start(() -> worker(started));
                        threads[i].setName("Thread-" + i);
                        started.await();
                        await(threads[i], Thread.State.BLOCKED);
                        JVMTIUtils.suspendThread(threads[i]);
                        suspended[i] = true;
                    }
                }

                // Grab the lock again then resume a thread
                synchronized(lock) {
                    assertTrue(owner == me, "Unexpected owner: " + owner);
                    assertTrue(successor == null, "Unexpected successor: " + successor);
                    JVMTIUtils.resumeThread(threads[firstResumer]);
                    suspended[firstResumer] = false;
                }
                // Ensure the resumed thread has completed
                threads[firstResumer].join();
                assertTrue(successor == threads[firstResumer],
                           "Successor should be " + threads[firstResumer] +
                           " but was actually " + successor);
            }
            finally {
                // Now resume the remaining threads
                for (int i = 0; i < nThreads; i++) {
                    if (suspended[i]) {
                        JVMTIUtils.resumeThread(threads[i]);
                    }
                }
                // Now join the remaining threads
                for (Thread t : threads) {
                    if (t != null) {
                        t.join();
                    }
                }
            }
        }
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    static void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            assertTrue(state != Thread.State.TERMINATED, "Thread has terminated");
            Thread.sleep(10);
            state = thread.getState();
        }
    }

    static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }
}
