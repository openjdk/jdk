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
 * @summary Basic unit test of ThreadInfo.getLockName()
 *          and ThreadInfo.getLockOwnerName()
 * @author  Mandy Chung
 * @author  Jaroslav Bachorik
 *
 * @library /lib/testlibrary
 * @build jdk.testlibrary.*
 * @run main/othervm Locks
 */

import java.lang.management.*;
import java.util.concurrent.Phaser;
import jdk.testlibrary.LockFreeLogManager;

public class Locks {
    private static final Object objA = new Object();
    private static final Object objB = new Object();
    private static final Object objC = new Object();
    private static final ThreadMXBean tm = ManagementFactory.getThreadMXBean();
    private static final LockFreeLogManager logger = new LockFreeLogManager();

    private static boolean testFailed = false;

    private static String getLockName(Object lock) {
        if (lock == null) return null;

        return lock.getClass().getName() + '@' +
                Integer.toHexString(System.identityHashCode(lock));
    }

    private static void assertNoLock(Thread t) {
        long tid = t.getId();
        ThreadInfo info = tm.getThreadInfo(tid);
        String result = info.getLockName();

        if (result != null) {
            throw new RuntimeException("Thread " + t.getName() + " is not supposed to hold any lock. " +
                                       "Currently owning lock: " + result);
        }
    }

    private static void checkBlockedObject(Thread t, Object lock, Thread owner,
                                           Thread.State expectedState) {
        long tid = t.getId();
        ThreadInfo info = tm.getThreadInfo(tid);
        String result = info.getLockName();
        String expectedLock = (lock != null ? getLockName(lock) : null);
        String expectedOwner = (owner != null ? owner.getName() : null);

        if (lock != null) {
            if (expectedState == Thread.State.BLOCKED) {
                int retryCount=0;
                while(info.getThreadState() != Thread.State.BLOCKED) {
                    if (retryCount++ > 500) {
                        throw new RuntimeException("Thread " + t.getName() +
                                " is expected to block on " + expectedLock +
                                " but got " + result +
                                " Thread.State = " + info.getThreadState());
                    }
                    goSleep(100);
                    info = tm.getThreadInfo(tid);
                    result = info.getLockName();
                }
            }
            if (expectedState == Thread.State.WAITING &&
                    info.getThreadState() != Thread.State.WAITING) {
                throw new RuntimeException("Thread " + t.getName() +
                        " is expected to wait on " + expectedLock +
                        " but got " + result +
                        " Thread.State = " + info.getThreadState());
            }
        }

        if ((result != null && !result.equals(expectedLock)) ||
                (result == null && expectedLock != null)) {
            throw new RuntimeException("Thread " + t.getName() + " is blocked on " +
                    expectedLock + " but got " + result);
        }
        result = info.getLockOwnerName();
        if ((result != null && !result.equals(expectedOwner)) ||
                (result == null && expectedOwner != null)) {
            throw new RuntimeException("Owner of " + lock + " should be " +
                    expectedOwner + " but got " + result);
        }
    }

    private static void goSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
            testFailed = true;
        }
    }

    private static volatile int dummyCounter = 0;

    static class LockAThread extends Thread {
        private final Phaser p;
        public LockAThread(Phaser p) {
            super("LockAThread");
            this.p = p;
        }
        public void run() {
            synchronized(objA) {
                // stop here  for LockBThread to hold objB
                log("LockAThread about to block on objB");
                p.arriveAndAwaitAdvance(); // Phase 1 (blocking)
                synchronized(objB) {
                    dummyCounter++;
                };
            }
            p.arriveAndAwaitAdvance(); // Phase 2 (blocking)
            log("LockAThread about to exit");
            // Make sure the current thread is not holding any lock
            assertNoLock(this);
        }
    }

    static class LockBThread extends Thread {
        private final Phaser p;
        public LockBThread(Phaser p) {
            super("LockBThread");
            this.p = p;
        }
        public void run() {
            synchronized(objB) {
                log("LockBThread about to block on objC");
                p.arriveAndAwaitAdvance(); // Phase 1 (blocking)
                // Signal main thread about to block on objC
                synchronized(objC) {
                    dummyCounter++;
                };
            }
            p.arriveAndAwaitAdvance(); // Phase 2 (blocking)
            log("LockBThread about to exit");
            // Make sure the current thread is not holding any lock
            assertNoLock(this);
        }
    }

    private static WaitingThread waiter;
    private static final Object ready = new Object();
    private static CheckerThread checker;
    static class WaitingThread extends Thread {
        private final Phaser p;

        volatile boolean waiting = false;

        public WaitingThread(Phaser p) {
            super("WaitingThread");
            this.p = p;
        }
        @Override
        public void run() {
            synchronized(objC) {
                log("WaitingThread about to wait on objC");
                try {
                    // Signal checker thread, about to wait on objC.
                    waiting = false;
                    p.arriveAndAwaitAdvance(); // Phase 1 (waiting)
                    waiting = true;
                    objC.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    testFailed = true;
                }

                // block until CheckerThread finishes checking
                log("WaitingThread about to block on ready");
                // signal checker thread that it is about acquire
                // object ready.
                p.arriveAndAwaitAdvance(); // Phase 2 (waiting)
                synchronized(ready) {
                    dummyCounter++;
                }
            }
            synchronized(objC) {
                try {
                    // signal checker thread, about to wait on objC
                    waiting = false;
                    p.arriveAndAwaitAdvance(); // Phase 3 (waiting)
                    waiting = true;
                    objC.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    testFailed = true;
                }
            }
            log("WaitingThread about to exit waiting on objC 2");
        }

        public void waitForWaiting() {
            p.arriveAndAwaitAdvance();
            while (!waiting) {
                goSleep(10);
            }
            waitForState(State.WAITING);
        }

        public void waitForBlocked() {
            p.arriveAndAwaitAdvance();
            waitForState(State.BLOCKED);
        }

        private void waitForState(Thread.State state) {
            while (!waiter.isInterrupted() && waiter.getState() != state) {
                Thread.yield();
            }
        }
    }
    static class CheckerThread extends Thread {
        public CheckerThread() {
            super("CheckerThread");
        }

        public void run() {
            synchronized (ready) {
                // wait until WaitingThread about to wait for objC
                waiter.waitForWaiting(); // Phase 1 (waiting)
                checkBlockedObject(waiter, objC, null, Thread.State.WAITING);

                synchronized (objC) {
                    objC.notify();
                }

                // wait for waiter thread to about to enter
                // synchronized object ready.
                waiter.waitForBlocked(); // Phase 2 (waiting)
                checkBlockedObject(waiter, ready, this, Thread.State.BLOCKED);
            }

            // wait for signal from waiting thread that it is about
            // wait for objC.
            waiter.waitForWaiting(); // Phase 3 (waiting)
            synchronized(objC) {
                checkBlockedObject(waiter, objC, Thread.currentThread(), Thread.State.WAITING);
                objC.notify();
            }

        }
    }

    public static void main(String args[]) throws Exception {
        Thread mainThread = Thread.currentThread();

        // Test uncontested case
        LockAThread t1;
        LockBThread t2;

        Phaser p = new Phaser(3);
        synchronized(objC) {
            // Make sure the main thread is not holding any lock
            assertNoLock(mainThread);

            // Test deadlock case
            // t1 holds lockA and attempts to lock B
            // t2 holds lockB and attempts to lock C

            t1 = new LockAThread(p);
            t1.start();

            t2 = new LockBThread(p);
            t2.start();

            p.arriveAndAwaitAdvance(); // Phase 1 (blocking)
            checkBlockedObject(t2, objC, mainThread, Thread.State.BLOCKED);
            checkBlockedObject(t1, objB, t2, Thread.State.BLOCKED);

            long[] expectedThreads = new long[3];
            expectedThreads[0] = t1.getId(); // blocked on lockB
            expectedThreads[1] = t2.getId(); // owner of lockB blocking on lockC
            expectedThreads[2] = mainThread.getId(); // owner of lockC
            findThreadsBlockedOn(objB, expectedThreads);
        }
        p.arriveAndAwaitAdvance(); // Phase 2 (blocking)

        p = new Phaser(2);
        // Test Object.wait() case
        waiter = new WaitingThread(p);
        waiter.start();

        checker = new CheckerThread();
        checker.start();

        try {
            waiter.join();
            checker.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            testFailed = true;
        }

        if (testFailed) {
            throw new RuntimeException("TEST FAILED.");
        }
        System.out.println("Test passed.");
    }

    private static ThreadInfo findOwnerInfo(ThreadInfo[] infos, String lock)
            throws Exception {
        ThreadInfo ownerInfo = null;
        for (ThreadInfo info : infos) {
            String blockedLock = info.getLockName();
            if (lock.equals(blockedLock)) {
                long threadId = info.getLockOwnerId();
                if (threadId == -1) {
                    throw new RuntimeException("TEST FAILED: " +
                            lock + " expected to have owner");
                }
                for (int j = 0; j < infos.length; j++) {
                    if (infos[j].getThreadId() == threadId) {
                        ownerInfo = infos[j];
                        break;
                    }
                }
            }
        }
        return ownerInfo;
    }
    private static void findThreadsBlockedOn(Object o, long[] expectedThreads)
            throws Exception {
        String lock = getLockName(o);
        // Check with ThreadInfo with no stack trace (i.e. no safepoint)
        ThreadInfo[] infos = tm.getThreadInfo(tm.getAllThreadIds());
        doCheck(infos, lock, expectedThreads);

        // Check with ThreadInfo with stack trace
        infos = tm.getThreadInfo(tm.getAllThreadIds(), 1);
        doCheck(infos, lock, expectedThreads);
    }

    private static void doCheck(ThreadInfo[] infos, String lock, long[] expectedThreads)
            throws Exception {
        ThreadInfo ownerInfo = null;
        // Find the thread who is blocking on lock
        for (ThreadInfo info : infos) {
            String blockedLock = info.getLockName();
            if (lock.equals(blockedLock)) {
                log("%s blocked on %s", info.getThreadName(), blockedLock);
                ownerInfo = info;
            }
        }
        if (ownerInfo == null) {
            throw new RuntimeException("TEST FAILED: " +
                    "Can't retrieve ThreadInfo for the blocked thread");
        }

        long[] threads = new long[10];
        int count = 0;
        threads[count++] = ownerInfo.getThreadId();
        while (ownerInfo != null && ownerInfo.getThreadState() == Thread.State.BLOCKED) {
            ownerInfo = findOwnerInfo(infos, lock);
            threads[count++] = ownerInfo.getThreadId();
            log(" Owner = %s  id = %d",
                    ownerInfo.getThreadName(),
                    ownerInfo.getThreadId()
            );
            lock = ownerInfo.getLockName();
            log("%s Id = %d  blocked on %s",
                    ownerInfo.getThreadName(),
                    ownerInfo.getThreadId(),
                    lock
            );
        }
        log("");

        if (count != expectedThreads.length) {
            throw new RuntimeException("TEST FAILED: " +
                    "Expected chain of threads not matched; current count =" + count);
        }
        for (int i = 0; i < count; i++) {
            if (threads[i] != expectedThreads[i]) {
                log("TEST FAILED: Unexpected thread in the chain %s expected to be %s",
                    threads[i],
                    expectedThreads[i]
                );
            }
        }
    }

    private static void log(String format, Object ... args) {
        logger.log(format + "%n", args);
    }
}
