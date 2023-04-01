/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary converted from VM Testbase nsk/jvmti/GetThreadState/thrstat005.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test verifies that the new hierarchical flags returned by GetThreadState()
 *     are properly set in various thread states as requested in bug #5041847.
 *     Flags being tested are:
 *     JVMTI_THREAD_STATE_ALIVE
 *     JVMTI_THREAD_STATE_TERMINATED
 *     JVMTI_THREAD_STATE_RUNNABLE
 *     JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER
 *     JVMTI_THREAD_STATE_WAITING
 *     JVMTI_THREAD_STATE_WAITING_INDEFINITELY
 *     JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT
 *     JVMTI_THREAD_STATE_SLEEPING
 *     JVMTI_THREAD_STATE_IN_OBJECT_WAIT
 *     JVMTI_THREAD_STATE_PARKED
 *     The state is checked in the following test cases:
 *     - A new thread is created
 *     - Thread is running (doing some computations)
 *     - Thread is blocked on a monitor (synchronized (...) { ... })
 *     - Thread is waiting in wait(timeout)
 *     - Thread is waiting in wait() w/o a timeout
 *     - Thread is parked using LockSupport.park()
 *     - Thread is parked using LockSupport.parkUntil()
 *     - Thread is in Thread.sleep()
 *     - Thread has terminated
 *     For more information see bugs #5041847, #4980307 and J2SE 5.0+ JVMTI spec.
 * COMMENTS
 *
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} thrstat05.java
 * @run main/othervm/native --enable-preview -agentlib:thrstat05 thrstat05
 */


import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class thrstat05 {

    public static final int TS_NEW = 0;
    public static final int TS_TERMINATED = 1;

    public static final int TS_RUN_RUNNING = 2;
    public static final int TS_RUN_BLOCKED = 3;
    public static final int TS_RUN_WAIT_TIMED = 4;
    public static final int TS_RUN_WAIT_INDEF = 5;
    public static final int TS_RUN_WAIT_PARKED_TIMED = 6;
    public static final int TS_RUN_WAIT_PARKED_INDEF = 7;
    public static final int TS_RUN_WAIT_SLEEP = 8; /* assumes _TIMED */

    public static final int WAIT_TIME = 250;

    public TestThread testThread;
    public int passedCnt, failedCnt;

    /**
     * Set waiting time for checkThreadState
     */
    native static void setWaitTime(int sec);

    /**
     * Check that thread state (TS_xxx) is what we expect
     * (for TS_xxx -> JVMTI_THREAD_STATE_xxx mapping see table in thrstat05.c)
     */
    native static boolean checkThreadState(Thread t, int stateIdx);

    public static void main(String args[]) {
        new thrstat05().run();
    }

    thrstat05() {
        setWaitTime(WAIT_TIME * 23 / 11);
    }

    public void run() {
        failedCnt = 0;
        passedCnt = 0;

        testAndPrint("New", TS_NEW);
        testAndPrint("Running", TS_RUN_RUNNING);
        testAndPrint("Blocked on monitor", TS_RUN_BLOCKED);
        testAndPrint("Waiting with timeout", TS_RUN_WAIT_TIMED);
        testAndPrint("Waiting forever", TS_RUN_WAIT_INDEF);
        testAndPrint("Parking forever", TS_RUN_WAIT_PARKED_TIMED);
        testAndPrint("Parking with timeout", TS_RUN_WAIT_PARKED_INDEF);
        testAndPrint("Sleeping", TS_RUN_WAIT_SLEEP);
        testAndPrint("Terminating", TS_TERMINATED);

        System.out.println(">>> PASS/FAIL: " + passedCnt + "/" + failedCnt);

        if (failedCnt > 0) {
            throw new RuntimeException("Failed cnt: " + failedCnt);
        }
    }

    public void testAndPrint(String name, int state) {
        boolean fPassed;
        try {
            System.out.println(">>> Testing state: " + name);
            fPassed = test(state);
        } catch (BrokenBarrierException e) {
            System.out.println("Main: broken barrier exception");
            fPassed = false;
        } catch (InterruptedException e) {
            System.out.println("Main: interrupted exception");
            fPassed = false;
        }

        System.out.println(">>> " + (fPassed ? "PASSED" : "FAILED") + " testing state: " + name);
        if (fPassed) {
            passedCnt++;
        } else {
            failedCnt++;
        }
    }

    public boolean test(int state) throws BrokenBarrierException, InterruptedException {
        boolean fRes;

        switch (state) {
            case TS_NEW:
                System.out.println("Main: Creating new thread");
                testThread = new TestThread();
                fRes = checkThreadState(testThread.thread, state);
                testThread.start();
                return fRes;

            case TS_RUN_RUNNING:
                System.out.println("Main: Running thread");
                testThread.fRun = true;
                fRes = sendStateAndCheckIt(state);
                testThread.fRun = false;
                return fRes;

            case TS_RUN_BLOCKED:
                System.out.println("Main: Blocking thread");
                synchronized (testThread.monitor) {
                    return sendStateAndCheckIt(state);
                }

            case TS_RUN_WAIT_TIMED:
            case TS_RUN_WAIT_INDEF:
                System.out.println("Main: Thread will wait");
                testThread.fRun = true;
                fRes = sendStateAndCheckIt(state);

                testThread.fRun = false;
                do {
                    System.out.println("Main: Notifying the thread");
                    synchronized (testThread.monitor) {
                        testThread.monitor.notify();
                    }

                    if (!testThread.fInTest) {
                        break;
                    }

                    Thread.sleep(WAIT_TIME / 4);
                } while (true);

                return fRes;

            case TS_RUN_WAIT_PARKED_TIMED:
            case TS_RUN_WAIT_PARKED_INDEF:
                System.out.println("Main: Thread will park");
                testThread.fRun = true;
                fRes = sendStateAndCheckIt(state);

                testThread.fRun = false;
                do {
                    System.out.println("Main: Unparking the thread");
                    LockSupport.unpark(testThread.thread);

                    if (!testThread.fInTest) {
                        break;
                    }

                    Thread.sleep(WAIT_TIME);
                } while (true);

                return fRes;

            case TS_RUN_WAIT_SLEEP:
                System.out.println("Main: Thread will sleep");
                testThread.fRun = true;
                fRes = sendStateAndCheckIt(state);
                testThread.fRun = false;
                return fRes;

            case TS_TERMINATED:
                System.out.println("Main: Terminating thread");
                testThread.sendTestState(state);

                System.out.println("Main: Waiting for join");
                testThread.join();
                return checkThreadState(testThread.thread, state);
        }

        return false;
    }

    public boolean sendStateAndCheckIt(int state) throws BrokenBarrierException, InterruptedException {
        testThread.sendTestState(state);
        while (!testThread.fInTest) {
            System.out.println("Main: Waiting for the thread to start the test");
            Thread.sleep(WAIT_TIME * 29 / 7); // Wait time should not be a multiple of WAIT_TIME
        }
        return checkThreadState(testThread.thread, state);
    }

    class TestThread implements Runnable {

        Thread thread;

        SynchronousQueue<Integer> taskQueue = new SynchronousQueue<>();

        public volatile boolean fRun = true;
        public volatile boolean fInTest = false;
        public Object monitor = new Object();

        TestThread() {
            thread = Thread.ofPlatform().unstarted(this);
        }

        public void sendTestState(int state) throws InterruptedException {
            taskQueue.put(state);
        }

        public void start() {
           thread.start();
        }

        public void join() throws InterruptedException{
            thread.join();
        }

        public int recvTestState() {
            int state = TS_NEW;
            try {
                state = taskQueue.take();
            } catch (InterruptedException e) {
                System.out.println("Thread: interrupted exception " + e);
            }
            return state;
        }

        public void run() {
            System.out.println("Thread: started");

            while (true) {
                int state = recvTestState();
                switch (state) {
                    case TS_NEW:
                        System.out.println("Thread: ERROR IN TEST: TS_NEW");
                        break;

                    case TS_RUN_RUNNING:
                        int i = 0;
                        System.out.println("Thread: Running...");
                        fInTest = true;
                        while (fRun) { i++; }
                        System.out.println("Thread: Running: done");
                        fInTest = false;
                        break;

                    case TS_RUN_BLOCKED:
                        System.out.println("Thread: Blocking...");
                        fInTest = true;
                        synchronized (monitor) {
                        }
                        System.out.println("Thread: Blocking: done");
                        fInTest = false;
                        break;

                    case TS_RUN_WAIT_TIMED:
                        System.out.println("Thread: Waiting with timeout...");
                        while (fRun) {
                            synchronized (monitor) {
                                fInTest = true;
                                try {
                                    monitor.wait(WAIT_TIME);
                                } catch (InterruptedException e) {
                                    System.out.println("Thread: Interrupted exception");
                                }
                            }
                        }
                        System.out.println("Thread: Waiting: done");
                        fInTest = false;
                        break;

                    case TS_RUN_WAIT_INDEF:
                        System.out.println("Thread: Waiting indefinitely...");
                        fInTest = true;
                        synchronized (monitor) {
                            try {
                                monitor.wait();
                            } catch (InterruptedException e) {
                                System.out.println("Thread: Interrupted exception");
                            }
                            System.out.println("Thread: Waiting: done");
                            fInTest = false;
                        }
                        break;

                    case TS_RUN_WAIT_SLEEP:
                        System.out.println("Thread: Sleeping...");
                        while (fRun) {
                            try {
                                fInTest = true;
                                Thread.sleep(WAIT_TIME);
                            } catch (InterruptedException e) {
                                System.out.println("Thread: Interrupted exception");
                            }
                        }
                        System.out.println("Thread: Sleeping: done");
                        fInTest = false;
                        break;

                    case TS_RUN_WAIT_PARKED_TIMED:
                        System.out.println("Thread: Parking indefinitely...");
                        fInTest = true;
                        while (fRun) {
                            LockSupport.park();
                        }
                        System.out.println("Thread: Parking: done");
                        fInTest = false;
                        break;

                    case TS_RUN_WAIT_PARKED_INDEF:
                        System.out.println("Thread: Parking with timeout...");
                        fInTest = true;
                        while (fRun) {
                            LockSupport.parkUntil(System.currentTimeMillis() + WAIT_TIME);
                        }
                        System.out.println("Thread: Parking: done");
                        fInTest = false;
                        break;

                    case TS_TERMINATED:
                        System.out.println("Thread: terminating");
                        return;
                }
            }
        }
    }
}
