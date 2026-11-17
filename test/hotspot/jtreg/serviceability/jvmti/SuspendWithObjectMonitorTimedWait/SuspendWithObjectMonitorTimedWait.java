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

import jvmti.JVMTIUtils;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.Arrays;
import java.util.concurrent.Phaser;

import static jdk.test.lib.Asserts.assertTrue;

/**
 * @test
 * @bug 8382088
 * @summary Check that a thread suspended at a timed wait() call does not re-acquire the monitor before it is resumed.
 * @requires vm.jvmti
 * @library /test/lib /test/hotspot/jtreg/testlibrary
 * @run main/othervm/native SuspendWithObjectMonitorTimedWait
 */

public class SuspendWithObjectMonitorTimedWait {
    static final Object lock = new Object();
    static long timeout = 10; // milliseconds
    static int maxRetries = 200;
    static long waitForTimedWaitingMills = timeout * 2;

    private static boolean waitUntilTimedWaiting(Thread thread, long deadlineNs) {
        while (System.nanoTime() < deadlineNs) {
            if (thread.getState() == Thread.State.TIMED_WAITING) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    public static void main(String[] args) throws RuntimeException {
        long failureCounter = 0;
        System.out.println("Timeout = " + timeout + " msc.");

        Phaser phaser = new Phaser(2);
        waitTask task = new waitTask(phaser);
        Thread targetThread = Thread.ofPlatform().name("Target Thread").unstarted(task);

        targetThread.start();

        System.out.println("Target Thread started");

        int usefulRuns = 0;
        for (int n = 0; n < maxRetries; ++n) {

            phaser.arriveAndAwaitAdvance();

            long deadlineNanos = System.nanoTime() + waitForTimedWaitingMills * 1_000_000L;
            if (!waitUntilTimedWaiting(targetThread, deadlineNanos)) {
                continue;
            }

            boolean grabbedMonitor = false;

            JVMTIUtils.suspendThread(targetThread);
            try {
                grabbedMonitor = hasGrabbedMonitor(targetThread);

                if (grabbedMonitor) {
                    // Cannot assert anything here.
                    continue;
                }

                usefulRuns += 1;

                try {
                    Thread.sleep(2 * timeout);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // Check if the target still does not own monitors
                grabbedMonitor = hasGrabbedMonitor(targetThread);

                if (grabbedMonitor) {
                    System.out.println("Grabbed the monitor on iteration " + n);
                    failureCounter++;
                }
            } finally {
                JVMTIUtils.resumeThread(targetThread);
            }
        }

        phaser.arriveAndAwaitAdvance();

        // wait for targetThread finish
        try {
            targetThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Sync: targetThread finished");

        if (usefulRuns == 0) {
            // not representative
            System.out.println("Test succeeded, but there were 0 useful runs.");
        }

        if (failureCounter > 0) {
            throw new RuntimeException("Grabbed the monitor in total " + failureCounter + " times out of " + usefulRuns + " useful runs, which is more than 0.");
        }
    }

    private static boolean hasGrabbedMonitor(Thread targetThread) {
        ThreadInfo [] threadInfo = ManagementFactory.getThreadMXBean().getThreadInfo(new long [] { targetThread.threadId()}, true, false);
        assertTrue(threadInfo != null, "getThreadInfo() failed");
        assertTrue(threadInfo[0] != null, "getThreadInfo() failed");
        return Arrays.stream(threadInfo[0].getLockedMonitors()).anyMatch(m -> m.getIdentityHashCode() == System.identityHashCode(lock));
    }


    static class waitTask implements Runnable {

        private final Phaser phaser;

        waitTask(final Phaser phaser) {
            this.phaser = phaser;
        }

        public void run() {
            synchronized (lock) {
                for (int i = 0; i < maxRetries; ++i) {
                    phaser.arriveAndAwaitAdvance();
                    try {
                        lock.wait(timeout);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            phaser.arriveAndDeregister();
        }
    }
}

