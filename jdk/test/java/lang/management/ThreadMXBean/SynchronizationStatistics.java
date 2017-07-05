/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4530538
 * @summary Basic unit test of the synchronization statistics support:
 *
 * @author  Mandy Chung
 * @author  Jaroslav Bachorik
 *
 * @run main/othervm SynchronizationStatistics
 */

import java.lang.management.*;
import java.util.concurrent.Phaser;

public class SynchronizationStatistics {
    private static class LockerThread extends Thread {
        public LockerThread(Runnable r) {
            super(r, "LockerThread");
        }
    }

    private static final ThreadMXBean mbean = ManagementFactory.getThreadMXBean();

    private static final boolean blockedTimeCheck =
        mbean.isThreadContentionMonitoringSupported();


    public static void main(String args[]) throws Exception {
        if (blockedTimeCheck) {
            mbean.setThreadContentionMonitoringEnabled(true);
        }

        if (!mbean.isThreadContentionMonitoringEnabled()) {
            throw new RuntimeException("TEST FAILED: " +
                "Thread Contention Monitoring is not enabled");
        }

        testBlockingOnSimpleMonitor();
        testBlockingOnNestedMonitor();
        testWaitingOnSimpleMonitor();
        testMultiWaitingOnSimpleMonitor();
        testWaitingOnNestedMonitor();

        System.out.println("Test passed.");
    }

    private static LockerThread newLockerThread(Runnable r) {
        LockerThread t = new LockerThread(r);
        t.setDaemon(true);
        return t;
    }

    private static void waitForThreadState(Thread t, Thread.State state) throws InterruptedException {
        while (!t.isInterrupted() && t.getState() != state) {
            Thread.sleep(3);
        }
    }

    /**
     * Tests that blocking on a single monitor properly increases the
     * blocked count at least by 1. Also asserts that the correct lock name is provided.
     */
    private static void testBlockingOnSimpleMonitor() throws Exception {
        System.out.println("testBlockingOnSimpleMonitor");
        final Object lock1 = new Object();
        final Phaser p = new Phaser(2);
        LockerThread lt = newLockerThread(new Runnable() {
            @Override
            public void run() {
                p.arriveAndAwaitAdvance(); // phase[1]
                synchronized(lock1) {
                    System.out.println("[LockerThread obtained Lock1]");
                    p.arriveAndAwaitAdvance(); // phase[2]
                }
                p.arriveAndAwaitAdvance(); // phase[3]
            }
        });

        lt.start();
        long tid = lt.getId();
        ThreadInfo ti = mbean.getThreadInfo(tid);
        String lockName = null;
        synchronized(lock1) {
            p.arriveAndAwaitAdvance(); // phase[1]
            waitForThreadState(lt, Thread.State.BLOCKED);
            lockName = mbean.getThreadInfo(tid).getLockName();
        }

        p.arriveAndAwaitAdvance(); // phase[2]
        testBlocked(ti, mbean.getThreadInfo(tid), lockName, lock1);
        p.arriveAndDeregister(); // phase[3]

        lt.join();

        System.out.println("OK");
    }

    /**
     * Tests that blocking on a nested monitor properly increases the
     * blocked count at least by 1 - it is not affected by the nesting depth.
     * Also asserts that the correct lock name is provided.
     */
    private static void testBlockingOnNestedMonitor() throws Exception {
        System.out.println("testBlockingOnNestedMonitor");
        final Object lock1 = new Object();
        final Object lock2 = new Object();

        final Phaser p = new Phaser(2);
        LockerThread lt = newLockerThread(new Runnable() {
            @Override
            public void run() {
                p.arriveAndAwaitAdvance(); // phase[1]
                synchronized(lock1) {
                    System.out.println("[LockerThread obtained Lock1]");
                    p.arriveAndAwaitAdvance(); // phase[2]
                    p.arriveAndAwaitAdvance(); // phase[3]
                    synchronized(lock2) {
                        System.out.println("[LockerThread obtained Lock2]");
                        p.arriveAndAwaitAdvance(); // phase[4]
                    }
                    p.arriveAndAwaitAdvance(); // phase[5]
                }
            }
        });

        lt.start();
        long tid = lt.getId();
        ThreadInfo ti = mbean.getThreadInfo(tid);
        ThreadInfo ti1 = null;
        String lockName = null;
        synchronized(lock1) {
            p.arriveAndAwaitAdvance(); // phase[1]
            waitForThreadState(lt, Thread.State.BLOCKED);
            lockName = mbean.getThreadInfo(tid).getLockName();
        }
        p.arriveAndAwaitAdvance(); // phase[2]

        ti1 = mbean.getThreadInfo(tid);
        testBlocked(ti, ti1, lockName, lock1);
        ti = ti1;

        synchronized(lock2) {
            p.arriveAndAwaitAdvance(); // phase [3]
            waitForThreadState(lt, Thread.State.BLOCKED);
            lockName = mbean.getThreadInfo(tid).getLockName();
        }
        p.arriveAndAwaitAdvance(); // phase [4]
        testBlocked(ti, mbean.getThreadInfo(tid), lockName, lock2);
        p.arriveAndDeregister();

        lt.join();

        System.out.println("OK");
    }

    /**
     * Tests that waiting on a single monitor properly increases the waited
     * count by 1 and the waited time by a positive number.
     */
    private static void testWaitingOnSimpleMonitor() throws Exception {
        System.out.println("testWaitingOnSimpleMonitor");
        final Object lock1 = new Object();
        final Phaser p = new Phaser(2);
        LockerThread lt = newLockerThread(new Runnable() {
            @Override
            public void run() {
                p.arriveAndAwaitAdvance(); // phase[1]
                synchronized(lock1) {
                    System.out.println("[LockerThread obtained Lock1]");
                    try {
                        lock1.wait(300);
                    } catch (InterruptedException ex) {
                        // ignore
                    }
                    p.arriveAndAwaitAdvance(); // phase[2]
                }
                p.arriveAndAwaitAdvance(); // phase[3]
            }
        });

        lt.start();
        ThreadInfo ti1 = mbean.getThreadInfo(lt.getId());
        synchronized(lock1) {
            p.arriveAndAwaitAdvance(); // phase[1]
            waitForThreadState(lt, Thread.State.BLOCKED);
        }
        p.arriveAndAwaitAdvance(); // phase[2]

        ThreadInfo ti2 = mbean.getThreadInfo(lt.getId());
        p.arriveAndDeregister(); // phase[3]

        lt.join();

        testWaited(ti1, ti2, 1);
        System.out.println("OK");
    }

    /**
     * Tests that waiting multiple times on the same monitor subsequently
     * increases the waited count by the number of subsequent calls and the
     * waited time by a positive number.
     */
    private static void testMultiWaitingOnSimpleMonitor() throws Exception {
        System.out.println("testWaitingOnMultipleMonitors");
        final Object lock1 = new Object();

        final Phaser p = new Phaser(2);
        LockerThread lt = newLockerThread(new Runnable() {
            @Override
            public void run() {
                p.arriveAndAwaitAdvance(); // phase[1]
                synchronized(lock1) {
                    System.out.println("[LockerThread obtained Lock1]");
                    for (int i = 0; i < 3; i++) {
                        try {
                            lock1.wait(300);
                        } catch (InterruptedException ex) {
                            // ignore
                        }
                        p.arriveAndAwaitAdvance(); // phase[2-4]
                    }
                }
                p.arriveAndAwaitAdvance(); // phase[5]
            }
        });

        lt.start();
        ThreadInfo ti1 = mbean.getThreadInfo(lt.getId());
        synchronized(lock1) {
            p.arriveAndAwaitAdvance(); //phase[1]
            waitForThreadState(lt, Thread.State.BLOCKED);
        }
        int phase = p.getPhase();
        while ((p.arriveAndAwaitAdvance() - phase) < 3); // phase[2-4]

        ThreadInfo ti2 = mbean.getThreadInfo(lt.getId());
        p.arriveAndDeregister(); // phase[5]

        lt.join();
        testWaited(ti1, ti2, 3);
        System.out.println("OK");
    }

    /**
     * Tests that waiting on monitors places in nested synchronized blocks
     * properly increases the waited count by the number of times the "lock.wait()"
     * was invoked and the waited time by a positive number.
     */
    private static void testWaitingOnNestedMonitor() throws Exception {
        System.out.println("testWaitingOnNestedMonitor");
        final Object lock1 = new Object();
        final Object lock2 = new Object();
        final Object lock3 = new Object();

        final Phaser p = new Phaser(2);
        LockerThread lt = newLockerThread(new Runnable() {
            @Override
            public void run() {
                p.arriveAndAwaitAdvance(); // phase[1]
                synchronized(lock1) {
                    System.out.println("[LockerThread obtained Lock1]");
                    try {
                        lock1.wait(300);
                    } catch (InterruptedException ex) {
                        // ignore
                    }

                    p.arriveAndAwaitAdvance(); // phase[2]
                    synchronized(lock2) {
                        System.out.println("[LockerThread obtained Lock2]");
                        try {
                            lock2.wait(300);
                        } catch (InterruptedException ex) {
                            // ignore
                        }

                        p.arriveAndAwaitAdvance(); // phase[3]
                        synchronized(lock3) {
                            System.out.println("[LockerThread obtained Lock3]");
                            try {
                                lock3.wait(300);
                            } catch (InterruptedException ex) {
                                // ignore
                            }
                            p.arriveAndAwaitAdvance(); // phase[4]
                        }
                    }
                }
                p.arriveAndAwaitAdvance(); // phase[5]
            }
        });

        lt.start();
        ThreadInfo ti1 = mbean.getThreadInfo(lt.getId());
        synchronized(lock1) {
            p.arriveAndAwaitAdvance(); // phase[1]
            waitForThreadState(lt, Thread.State.BLOCKED);
        }

        synchronized(lock2) {
            p.arriveAndAwaitAdvance(); // phase[2]
            waitForThreadState(lt, Thread.State.BLOCKED);
        }

        synchronized(lock3) {
            p.arriveAndAwaitAdvance(); // phase[3]
            waitForThreadState(lt, Thread.State.BLOCKED);
        }

        p.arriveAndAwaitAdvance(); // phase[4]
        ThreadInfo ti2 = mbean.getThreadInfo(lt.getId());
        p.arriveAndDeregister(); // phase[5]

        lt.join();
        testWaited(ti1, ti2, 3);
        System.out.println("OK");
    }

    private static void testWaited(ThreadInfo ti1, ThreadInfo ti2, int waited) throws Error {
        long waitCntDiff = ti2.getWaitedCount() - ti1.getWaitedCount();
        long waitTimeDiff = ti2.getWaitedTime() - ti1.getWaitedTime();
        if (waitCntDiff < waited) {
            throw new Error("Unexpected diff in waited count. Expecting at least "
                            + waited + " , got " + waitCntDiff);
        }
        if (waitTimeDiff <= 0) {
            throw new Error("Unexpected diff in waited time. Expecting increasing " +
                            "value, got " + waitTimeDiff + "ms");
        }
    }

    private static void testBlocked(ThreadInfo ti1, ThreadInfo ti2,
                                    String lockName, final Object lock)
    throws Error {
        long blkCntDiff = ti2.getBlockedCount() - ti1.getBlockedCount();
        long blkTimeDiff = ti2.getBlockedTime() - ti1.getBlockedTime();
        if (blkCntDiff < 1) {
            throw new Error("Unexpected diff in blocked count. Expecting at least 1, " +
                            "got " + blkCntDiff);
        }
        if (blkTimeDiff < 0) {
            throw new Error("Unexpected diff in blocked time. Expecting a positive " +
                            "number, got " + blkTimeDiff);
        }
        if (!lockName.equals(lock.toString())) {
            throw new Error("Unexpected blocked monitor name. Expecting " +
                    lock.toString() + ", got " +
                    lockName);
        }
    }
}
