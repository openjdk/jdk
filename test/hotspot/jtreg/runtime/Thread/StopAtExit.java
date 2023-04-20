/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8167108 8266130 8282704
 * @summary Stress test java.lang.Thread.stop() at thread exit.
 * @modules java.base/java.lang:open
 * @run main/othervm StopAtExit
 */

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StopAtExit extends Thread {
    private final static int DEF_TIME_MAX = 30;  // default max # secs to test
    private final static String PROG_NAME = "StopAtExit";

    public CountDownLatch exitSyncObj = new CountDownLatch(1);
    public CountDownLatch startSyncObj = new CountDownLatch(1);

    public StopAtExit(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    @Override
    public void run() {
        try {
            // Tell main thread we have started.
            startSyncObj.countDown();
            try {
                // Wait for main thread to tell us to race to the exit.
                exitSyncObj.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpected: " + e);
            }
        } catch (ThreadDeath td) {
            // ignore because we're testing Thread.stop() which throws it
        } catch (NoClassDefFoundError ncdfe) {
            // ignore because we're testing Thread.stop() which can cause it
        }
    }

    public static void main(String[] args) {
        int timeMax = 0;
        if (args.length == 0) {
            timeMax = DEF_TIME_MAX;
        } else {
            try {
                timeMax = Integer.parseUnsignedInt(args[0]);
            } catch (NumberFormatException nfe) {
                System.err.println("'" + args[0] + "': invalid timeMax value.");
                usage();
            }
        }

        System.out.println("About to execute for " + timeMax + " seconds.");

        long count = 0;
        long manualDestroyCnt = 0;
        long manualTerminateCnt = 0;
        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (timeMax * 1000)) {
            count++;

            // Use my own ThreadGroup so the thread count is known and make
            // it a daemon ThreadGroup so it is automatically destroyed when
            // the thread is terminated.
            ThreadGroup myTG = new ThreadGroup("myTG-" + count);
            myTG.setDaemon(true);
            StopAtExit thread = new StopAtExit(myTG, null);
            thread.start();
            try {
                // Wait for the worker thread to get going.
                thread.startSyncObj.await();
                // Tell the worker thread to race to the exit and the
                // Thread.stop() calls will come in during thread exit.
                thread.exitSyncObj.countDown();
                while (true) {
                    thread.stop();

                    if (!thread.isAlive()) {
                        // Done with Thread.stop() calls since
                        // thread is not alive.
                        break;
                    }
                }
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            } catch (NoClassDefFoundError ncdfe) {
                // Ignore because we're testing Thread.stop() which can
                // cause it. Yes, a NoClassDefFoundError that happens
                // in a worker thread can subsequently be seen in the
                // main thread.
            }

            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }
            // This stop() call happens after the join() so it should do
            // nothing, but let's make sure.
            thread.stop();

            if (myTG.activeCount() != 0) {
                // If the ThreadGroup still has a count, then the thread
                // received the async exception while in exit() so we need
                // to do a manual terminate.
                manualTerminateCnt++;
                try {
                    threadTerminated(myTG, thread);
                } catch (Exception e) {
                    throw new Error("threadTerminated() threw unexpected: " + e);
                }
                int activeCount = myTG.activeCount();
                if (activeCount != 0) {
                    throw new Error("threadTerminated() did not clean up " +
                                    "worker thread: count=" + activeCount);
                }
                if (!myTG.isDestroyed()) {
                    throw new Error("threadTerminated() did not destroy " +
                                    myTG.getName());
                }
            } else if (!myTG.isDestroyed()) {
                // If the ThreadGroup does not have a count, but is not
                // yet destroyed, then the thread received the async
                // exception while the thread was in the later stages of
                // its threadTerminated() call so we need to do a manual
                // destroy.
                manualDestroyCnt++;
                try {
                    myTG.destroy();
                } catch (Exception e) {
                    throw new Error("myTG.destroy() threw unexpected: " + e);
                }
            }
        }

        if (manualDestroyCnt != 0) {
            System.out.println("Manually destroyed ThreadGroup " +
                               manualDestroyCnt + " times.");
        }
        if (manualTerminateCnt != 0) {
            System.out.println("Manually terminated Thread " +
                               manualTerminateCnt + " times.");
        }
        System.out.println("Executed " + count + " loops in " + timeMax +
                           " seconds.");

        String cmd = System.getProperty("sun.java.command");
        if (cmd != null && !cmd.startsWith("com.sun.javatest.regtest.agent.MainWrapper")) {
            // Exit with success in a non-JavaTest environment:
            System.exit(0);
        }
    }

    static void threadTerminated(ThreadGroup group, Thread thread) throws Exception {
        // ThreadGroup.threadTerminated() is package private:
        Method method = ThreadGroup.class.getDeclaredMethod("threadTerminated", Thread.class);
        method.setAccessible(true);
        method.invoke(group, thread);
    }

    public static void usage() {
        System.err.println("Usage: " + PROG_NAME + " [time_max]");
        System.err.println("where:");
        System.err.println("    time_max  max looping time in seconds");
        System.err.println("              (default is " + DEF_TIME_MAX +
                           " seconds)");
        System.exit(1);
    }
}
