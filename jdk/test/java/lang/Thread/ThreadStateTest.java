/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     5014783
 * @summary Basic unit test of thread states returned by
 *          Thread.getState().
 *
 * @author  Mandy Chung
 *
 * @build ThreadStateTest
 * @run main ThreadStateTest
 */

import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.Phaser;

public class ThreadStateTest {
    // maximum number of retries when checking for thread state.
    static final int MAX_RETRY = 500;

    private static boolean testFailed = false;

    // used to achieve waiting states
    static final Object globalLock = new Object();

    public static void main(String[] argv) {
        // Call Thread.getState to force all initialization done
        // before test verification begins.
        Thread.currentThread().getState();
        MyThread myThread = new MyThread("MyThread");

        // before myThread starts
        checkThreadState(myThread, Thread.State.NEW);

        myThread.start();
        myThread.waitUntilStarted();
        checkThreadState(myThread, Thread.State.RUNNABLE);

        synchronized (globalLock) {
            myThread.goBlocked();
            checkThreadState(myThread, Thread.State.BLOCKED);
        }

        myThread.goWaiting();
        checkThreadState(myThread, Thread.State.WAITING);

        myThread.goTimedWaiting();
        checkThreadState(myThread, Thread.State.TIMED_WAITING);


      /*
       *********** park and parkUntil seems not working
       * ignore this case for now.
       * Bug ID 5062095
       ***********************************************

        myThread.goParked();
        checkThreadState(myThread, Thread.State.WAITING);

        myThread.goTimedParked();
        checkThreadState(myThread, Thread.State.TIMED_WAITING);
       */


        myThread.goSleeping();
        checkThreadState(myThread, Thread.State.TIMED_WAITING);

        myThread.terminate();
        checkThreadState(myThread, Thread.State.TERMINATED);

        try {
            myThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Unexpected exception.");
            testFailed = true;
        }

        if (testFailed)
            throw new RuntimeException("TEST FAILED.");
        System.out.println("Test passed.");
    }

    private static void checkThreadState(Thread t, Thread.State expected) {
        // wait for the thread to transition to the expected state.
        // There is a small window between the thread checking the state
        // and the thread actual entering that state.
        Thread.State state;
        int retryCount=0;
        while ((state = t.getState()) != expected && retryCount < MAX_RETRY) {
            if (state != Thread.State.RUNNABLE) {
                throw new RuntimeException("Thread not in expected state yet," +
                        " but it should at least be RUNNABLE");
            }
            goSleep(10);
            retryCount++;
        }

        System.out.println("Checking thread state " + state);
        if (state == null) {
            throw new RuntimeException(t.getName() + " expected to have " +
                expected + " but got null.");
        }

        if (state != expected) {
            throw new RuntimeException(t.getName() + " expected to have " +
                expected + " but got " + state);
        }
    }

    private static void goSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Unexpected exception.");
            testFailed = true;
        }
    }

    static class MyThread extends Thread {
        // Phaser to sync between the main thread putting
        // this thread into various states
        private Phaser phaser =  new Phaser(2);

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

        private volatile int state = RUNNABLE;

        private boolean done = false;
        public void run() {
            // Signal main thread to continue.
            phaser.arriveAndAwaitAdvance();

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
                        phaser.arrive();
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
                            phaser.arrive();
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
                            phaser.arrive();
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
                        phaser.arrive();
                        System.out.println("  myThread is going to park.");
                        LockSupport.park();
                        // give a chance for the main thread to block
                        goSleep(10);
                        break;
                    }
                    case TIMED_PARKED: {
                        // signal main thread.
                        phaser.arrive();
                        System.out.println("  myThread is going to timed park.");
                        long deadline = System.currentTimeMillis() + 10000*1000;
                        LockSupport.parkUntil(deadline);

                        // give a chance for the main thread to block
                        goSleep(10);
                        break;
                    }
                    case SLEEPING: {
                        // signal main thread.
                        phaser.arrive();
                        System.out.println("  myThread is going to sleep.");
                        try {
                            Thread.sleep(1000000);
                        } catch (InterruptedException e) {
                            // finish sleeping
                        }
                        break;
                    }
                    case TERMINATE: {
                        done = true;
                        // signal main thread.
                        phaser.arrive();
                        break;
                    }
                    default:
                        break;
                }
            }
        }

        public void waitUntilStarted() {
            // wait for MyThread.
            phaser.arriveAndAwaitAdvance();
        }

        public void goBlocked() {
            System.out.println("Waiting myThread to go blocked.");
            setState(BLOCKED);
            // wait for MyThread to get to a point just before being blocked
            phaser.arriveAndAwaitAdvance();
        }

        public void goWaiting() {
            System.out.println("Waiting myThread to go waiting.");
            setState(WAITING);
            // wait for MyThread to get to just before wait on object.
            phaser.arriveAndAwaitAdvance();
        }

        public void goTimedWaiting() {
            System.out.println("Waiting myThread to go timed waiting.");
            setState(TIMED_WAITING);
            // wait for MyThread to get to just before timed wait call.
            phaser.arriveAndAwaitAdvance();
        }

        public void goParked() {
            System.out.println("Waiting myThread to go parked.");
            setState(PARKED);
            // wait for MyThread to get to just before parked.
            phaser.arriveAndAwaitAdvance();
        }

        public void goTimedParked() {
            System.out.println("Waiting myThread to go timed parked.");
            setState(TIMED_PARKED);
            // wait for MyThread to get to just before timed park.
            phaser.arriveAndAwaitAdvance();
        }

        public void goSleeping() {
            System.out.println("Waiting myThread to go sleeping.");
            setState(SLEEPING);
            // wait for MyThread to get to just before sleeping
            phaser.arriveAndAwaitAdvance();
        }

        public void terminate() {
            System.out.println("Waiting myThread to terminate.");
            setState(TERMINATE);
            // wait for MyThread to get to just before terminate
            phaser.arriveAndAwaitAdvance();
        }

        private void setState(int newState) {
            switch (state) {
                case BLOCKED:
                    while (state == BLOCKED) {
                        goSleep(10);
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
