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

import jdk.test.lib.jvmti.DebugeeClass;
import jvmti.JVMTIUtils;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Arrays;

import static jdk.test.lib.Asserts.assertTrue;

/**
 * @test
 * @bug 8382088
 * @summary Suspend thread  right after it timed out in wait()
 * @requires vm.continuations
 * @requires vm.jvmti
 * @library /test/lib /test/hotspot/jtreg/testlibrary
 * @run main/othervm/native SuspendWithObjectMonitorTimedWait
 */

public class SuspendWithObjectMonitorTimedWait extends DebugeeClass {

    static final Object startBarrier = new Object();
    static volatile boolean targetStarted = false;
    static final Object lock = new Object();

    private static boolean waitUntilTimedWaiting(Thread thread, long deadlineNs) {
        while(System.nanoTime() < deadlineNs) {
            if (!thread.isAlive()) {
                return false;
            }
            if (thread.getState() == Thread.State.TIMED_WAITING) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    public static void main(String args[]) {
        int result = new SuspendWithObjectMonitorTimedWait().runIt();
        if (result != 0) {
            throw new RuntimeException("Unexpected status: " + result);
        }
    }

    static long timeout = 10; // milliseconds
    static long sleepInterval = 20;
    static int maxRetries = 2000;
    static long waitForTimedWaitingMills = 5000;
    static double acceptableFailureRate = 0.01;

    // run debuggee
    public int runIt() {
        int status = DebugeeClass.TEST_PASSED;
        long failureCounter = 0;
        System.out.println("Timeout = " + timeout + " msc.");

        waitTask task = new waitTask();
        Thread.Builder builder;
        builder = Thread.ofPlatform();
        Thread targetThread = builder.name("Target Thread").unstarted(task);

        targetStarted = false;

        // run targetThread
        synchronized (startBarrier) {
            targetThread.start();
            while (!targetStarted) {
                try {
                    startBarrier.wait();
                } catch (InterruptedException ex) {
                    throw new Failure(ex);
                }
            }
        }

        Thread.yield();
        System.out.println("Target Thread started");

        int usefulRun = 0;
        for (int n = 0; n < maxRetries; ++n) {

            long deadlineNanos = System.nanoTime() + waitForTimedWaitingMills * 1000_000L;
            if (!waitUntilTimedWaiting(targetThread, deadlineNanos)) {
                if (!targetThread.isAlive()) {
                    System.out.println("Target thread finished before retry " + n);
                    break;
                }
                throw new RuntimeException("Timed out waiting to reach TIMED_WAITING state at retry " + n);
            }

            boolean is_suspended = false;
            boolean grabbedMonitor = false;

            try {
                JVMTIUtils.suspendThread(targetThread);
                is_suspended = true;

                ThreadInfo [] threadInfo = ManagementFactory.getThreadMXBean().getThreadInfo(new long [] { targetThread.threadId()}, true, false);

                assertTrue(threadInfo != null, "getThreadInfo() failed");
                assertTrue(threadInfo[0] != null, "getThreadInfo() failed");

                grabbedMonitor =
                        Arrays.stream(threadInfo[0].getLockedMonitors()).anyMatch(m -> m.getIdentityHashCode() == System.identityHashCode(lock));

                if (grabbedMonitor) {
                    // Cannot assert anything here.
                    continue;
                }

                usefulRun += 1;

                if (sleepInterval > 0) {
                    try {
                        Thread.sleep(sleepInterval);
                    } catch (InterruptedException ex) {
                        throw new Failure(ex);
                    }
                }

                // Check if the target still does not own monitors
                threadInfo = ManagementFactory.getThreadMXBean().getThreadInfo(new long [] { targetThread.threadId()}, true, false);
                grabbedMonitor =
                        Arrays.stream(threadInfo[0].getLockedMonitors()).anyMatch(m -> m.getIdentityHashCode() == System.identityHashCode(lock));

                if (grabbedMonitor) {
                    System.out.println("Grabbed the monitor on iteration " + n);
                    failureCounter++;
                }

            } finally {
                if (is_suspended) {
                    JVMTIUtils.resumeThread(targetThread);
                }
            }

            if (!targetThread.isAlive()) {
                System.out.println("Target thread finished before retry " + n);
                break;
            }
        }


        // wait for targetThread finish
        try {
            targetThread.join(timeout*1000*5);
        } catch (InterruptedException e) {
            throw new Failure(e);
        }

        assertTrue(!targetThread.isAlive(), "target thread is still alive");
        System.out.println("Sync: targetThread finished");

        if (usefulRun == 0) {
            // not representative
            status = DebugeeClass.TEST_FAILED;
            return status;
        }

        // Determined this purely experimentally
        if ((double)failureCounter / (double)usefulRun > acceptableFailureRate) {
            System.out.println("Grabbed the monitor in total " + failureCounter + " times out of " + usefulRun + " useful runs, which exceed the failure rate of " + acceptableFailureRate * 100 +"%");
            status = DebugeeClass.TEST_FAILED;
        }
        return status;
    }


    static class waitTask implements Runnable {

        static int maxNRetries = (int) (1.2 * maxRetries);

        public void run() {
            synchronized (lock) {

                // notify about starting
                synchronized (startBarrier) {
                    targetStarted = true;
                    startBarrier.notifyAll();
                }

                boolean done = false;
                int retryNumber = 0;
                while (!done) {
                    try {
                        lock.wait(timeout);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    retryNumber+=1;
                    if (retryNumber == maxNRetries){
                        done = true;
                    }
                }
            }
        }
    }

}

