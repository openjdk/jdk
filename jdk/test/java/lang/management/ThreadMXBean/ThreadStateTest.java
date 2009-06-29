/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug     4967283 5080203
 * @summary Basic unit test of thread states returned by
 *          ThreadMXBean.getThreadInfo.getThreadState().
 *          It also tests lock information returned by ThreadInfo.
 *
 * @author  Mandy Chung
 *
 * @build ThreadExecutionSynchronizer Utils
 * @run main ThreadStateTest
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.management.ThreadInfo;

import java.util.concurrent.locks.LockSupport;

public class ThreadStateTest {
    private static final ThreadMXBean tm = ManagementFactory.getThreadMXBean();

    static class Lock {
        private String name;
        Lock(String name) {
            this.name = name;
        }
        public String toString() {
            return name;
        }
    }
    private static Lock globalLock = new Lock("my lock");

    public static void main(String[] argv) {
        // Force thread state initialization now before the test
        // verification begins.
        Thread.currentThread().getState();

        MyThread myThread = new MyThread("MyThread");

        // before myThread starts
        // Utils.checkThreadState(myThread, Thread.State.NEW);

        myThread.start();
        myThread.waitUntilStarted();
        Utils.checkThreadState(myThread, Thread.State.RUNNABLE);
        checkLockInfo(myThread, Thread.State.RUNNABLE, null, null);

        myThread.suspend();
        Utils.goSleep(10);
        checkSuspendedThreadState(myThread, Thread.State.RUNNABLE);
        myThread.resume();

        synchronized (globalLock) {
            myThread.goBlocked();
            Utils.checkThreadState(myThread, Thread.State.BLOCKED);
            checkLockInfo(myThread, Thread.State.BLOCKED,
                          globalLock, Thread.currentThread());
        }

        myThread.goWaiting();
        Utils.checkThreadState(myThread, Thread.State.WAITING);
        checkLockInfo(myThread, Thread.State.WAITING,
                      globalLock, null);

        myThread.goTimedWaiting();
        Utils.checkThreadState(myThread, Thread.State.TIMED_WAITING);
        checkLockInfo(myThread, Thread.State.TIMED_WAITING,
                      globalLock, null);



      /*
       *********** parkUntil seems not working
       * ignore this park case for now.

         Bug ID : 5062095
       ***********************************************
        myThread.goParked();
        Utils.checkThreadState(myThread, Thread.State.WAITING);
        checkLockInfo(myThread, Thread.State.WAITING, null, null);

        myThread.goTimedParked();
        Utils.checkThreadState(myThread, Thread.State.TIMED_WAITING);
        checkLockInfo(myThread, Thread.State.TIMED_WAITING, null, null);

       */

        myThread.goSleeping();
        Utils.checkThreadState(myThread, Thread.State.TIMED_WAITING);
        checkLockInfo(myThread, Thread.State.TIMED_WAITING, null, null);


        myThread.terminate();
        // Utils.checkThreadState(myThread, ThreadState.TERMINATED);

        try {
            myThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("TEST FAILED: Unexpected exception.");
            throw new RuntimeException(e);
        }
        System.out.println("Test passed.");
    }

    private static void checkSuspendedThreadState(Thread t, Thread.State state) {
        ThreadInfo info = tm.getThreadInfo(t.getId());
        if (info == null) {
            throw new RuntimeException(t.getName() +
               " expected to have ThreadInfo " +
               " but got null.");
        }

        if (info.getThreadState() != state) {
            throw new RuntimeException(t.getName() + " expected to be in " +
                state + " state but got " + info.getThreadState());
        }

        if (!info.isSuspended()) {
            throw new RuntimeException(t.getName() + " expected to be suspended " +
                " but isSuspended() returns " + info.isSuspended());
        }
        Utils.checkThreadState(t, state);
    }

    private static String getLockName(Object lock) {
        if (lock == null) return null;

        return lock.getClass().getName() + '@' +
            Integer.toHexString(System.identityHashCode(lock));
    }

    private static void checkLockInfo(Thread t, Thread.State state, Object lock, Thread owner) {
        ThreadInfo info = tm.getThreadInfo(t.getId());
        if (info == null) {
            throw new RuntimeException(t.getName() +
               " expected to have ThreadInfo " +
               " but got null.");
        }

        if (info.getThreadState() != state) {
            throw new RuntimeException(t.getName() + " expected to be in " +
                state + " state but got " + info.getThreadState());
        }

        if (lock == null && info.getLockName() != null) {
            throw new RuntimeException(t.getName() +
                " expected not to be blocked on any lock" +
                " but got " + info.getLockName());
        }
        String expectedLockName = getLockName(lock);
        if (lock != null && info.getLockName() == null) {
            throw new RuntimeException(t.getName() +
                " expected to be blocked on lock [" + expectedLockName +
                "] but got null.");
        }

        if (lock != null && !expectedLockName.equals(info.getLockName())) {
            throw new RuntimeException(t.getName() +
                " expected to be blocked on lock [" + expectedLockName +
                "] but got [" + info.getLockName() + "].");
        }

        if (owner == null && info.getLockOwnerName() != null) {
            throw new RuntimeException("Lock owner is expected " +
                " to be null but got " + info.getLockOwnerName());
        }

        if (owner != null && info.getLockOwnerName() == null) {
            throw new RuntimeException("Lock owner is expected to be " +
                owner.getName() +
                " but got null.");
        }
        if (owner != null && !info.getLockOwnerName().equals(owner.getName())) {
            throw new RuntimeException("Lock owner is expected to be " +
                owner.getName() +
                " but got " + owner.getName());
        }
        if (owner == null && info.getLockOwnerId() != -1) {
            throw new RuntimeException("Lock owner is expected " +
                " to be -1 but got " + info.getLockOwnerId());
        }

        if (owner != null && info.getLockOwnerId() <= 0) {
            throw new RuntimeException("Lock owner is expected to be " +
                owner.getName() + "(id = " + owner.getId() +
                ") but got " + info.getLockOwnerId());
        }
        if (owner != null && info.getLockOwnerId() != owner.getId()) {
            throw new RuntimeException("Lock owner is expected to be " +
                owner.getName() + "(id = " + owner.getId() +
                ") but got " + info.getLockOwnerId());
        }
        if (info.isSuspended()) {
            throw new RuntimeException(t.getName() +
                " isSuspended() returns " + info.isSuspended());
        }
    }

    static class MyThread extends Thread {
        private ThreadExecutionSynchronizer thrsync = new ThreadExecutionSynchronizer();

        MyThread(String name) {
            super(name);
        }

        private final int RUNNABLE = 0;
        private final int BLOCKED = 1;
        private final int WAITING = 2;
        private final int TIMED_WAITING = 3;
        private final int PARKED = 4;
        private final int TIMED_PARKED = 5;
        private final int SLEEPING = 6;
        private final int TERMINATE = 7;
        private int state = RUNNABLE;

        private boolean done = false;
        public void run() {
            // Signal main thread to continue.
            thrsync.signal();
            while (!done) {
                switch (state) {
                    case RUNNABLE: {
                        double sum = 0;
                        for (int i = 0; i < 1000; i++) {
                           double r = Math.random();
                           double x = Math.pow(3, r);
                           sum += x - r;
                        }
                        break;
                    }
                    case BLOCKED: {
                        // signal main thread.
                        thrsync.signal();
                        System.out.println("  myThread is going to block.");
                        synchronized (globalLock) {
                            // finish blocking
                            state = RUNNABLE;
                        }
                        break;
                    }
                    case WAITING: {
                        synchronized (globalLock) {
                            // signal main thread.
                            thrsync.signal();
                            System.out.println("  myThread is going to wait.");
                            try {
                                globalLock.wait();
                            } catch (InterruptedException e) {
                                // ignore
                            }
                        }
                        break;
                    }
                    case TIMED_WAITING: {
                        synchronized (globalLock) {
                            // signal main thread.
                            thrsync.signal();
                            System.out.println("  myThread is going to timed wait.");
                            try {
                                globalLock.wait(10000);
                            } catch (InterruptedException e) {
                                // ignore
                            }
                        }
                        break;
                    }
                    case PARKED: {
                        // signal main thread.
                        thrsync.signal();
                        System.out.println("  myThread is going to park.");
                        LockSupport.park();
                        // give a chance for the main thread to block
                        System.out.println("  myThread is going to park.");
                        Utils.goSleep(10);
                        break;
                    }
                    case TIMED_PARKED: {
                        // signal main thread.
                        thrsync.signal();
                        System.out.println("  myThread is going to timed park.");
                        long deadline = System.currentTimeMillis() + 10000*1000;
                        LockSupport.parkUntil(deadline);

                        // give a chance for the main thread to block
                        Utils.goSleep(10);
                        break;
                    }
                    case SLEEPING: {
                        // signal main thread.
                        thrsync.signal();
                        System.out.println("  myThread is going to sleep.");
                        try {
                            Thread.sleep(1000000);
                        } catch (InterruptedException e) {
                            // finish sleeping
                            interrupted();
                        }
                        break;
                    }
                    case TERMINATE: {
                        done = true;
                        // signal main thread.
                        thrsync.signal();
                        break;
                    }
                    default:
                        break;
                }
            }
        }
        public void waitUntilStarted() {
            // wait for MyThread.
            thrsync.waitForSignal();
            Utils.goSleep(10);
        }

        public void goBlocked() {
            System.out.println("Waiting myThread to go blocked.");
            setState(BLOCKED);
            // wait for MyThread to get blocked
            thrsync.waitForSignal();
            Utils.goSleep(20);
        }

        public void goWaiting() {
            System.out.println("Waiting myThread to go waiting.");
            setState(WAITING);
            // wait for  MyThread to wait on object.
            thrsync.waitForSignal();
            Utils.goSleep(20);
        }
        public void goTimedWaiting() {
            System.out.println("Waiting myThread to go timed waiting.");
            setState(TIMED_WAITING);
            // wait for MyThread timed wait call.
            thrsync.waitForSignal();
            Utils.goSleep(20);
        }
        public void goParked() {
            System.out.println("Waiting myThread to go parked.");
            setState(PARKED);
            // wait for  MyThread state change to PARKED.
            thrsync.waitForSignal();
            Utils.goSleep(20);
        }
        public void goTimedParked() {
            System.out.println("Waiting myThread to go timed parked.");
            setState(TIMED_PARKED);
            // wait for  MyThread.
            thrsync.waitForSignal();
            Utils.goSleep(20);
        }

        public void goSleeping() {
            System.out.println("Waiting myThread to go sleeping.");
            setState(SLEEPING);
            // wait for  MyThread.
            thrsync.waitForSignal();
            Utils.goSleep(20);
        }
        public void terminate() {
            System.out.println("Waiting myThread to terminate.");
            setState(TERMINATE);
            // wait for  MyThread.
            thrsync.waitForSignal();
            Utils.goSleep(20);
        }

        private void setState(int newState) {
            switch (state) {
                case BLOCKED:
                    while (state == BLOCKED) {
                        Utils.goSleep(20);
                    }
                    state = newState;
                    break;
                case WAITING:
                case TIMED_WAITING:
                    state = newState;
                    synchronized (globalLock) {
                        globalLock.notify();
                    }
                    break;
                case PARKED:
                case TIMED_PARKED:
                    state = newState;
                    LockSupport.unpark(this);
                    break;
                case SLEEPING:
                    state = newState;
                    this.interrupt();
                    break;
                default:
                    state = newState;
                    break;
            }
        }
    }
}
