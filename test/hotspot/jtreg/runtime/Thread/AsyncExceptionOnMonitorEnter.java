/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8283044
 * @summary Stress delivery of asynchronous exceptions while target is at monitorenter
 * @library /test/hotspot/jtreg/testlibrary
 * @run main/othervm/native AsyncExceptionOnMonitorEnter 0
 * @run main/othervm/native -agentlib:AsyncExceptionOnMonitorEnter AsyncExceptionOnMonitorEnter 1
 */

import jvmti.JVMTIUtils;

import java.util.concurrent.Semaphore;

public class AsyncExceptionOnMonitorEnter extends Thread {
    private final static int DEF_TIME_MAX = 30;  // default max # secs to test
    private final static String PROG_NAME = "AsyncExceptionOnMonitorEnter";
    private static int TEST_MODE = 0;

    public static native int createRawMonitor();
    public static native int enterRawMonitor();
    public static native int exitRawMonitor();
    public static native void destroyRawMonitor();

    private static Object o1 = new Object();
    private static boolean firstWorker = true;
    private static Semaphore sem = new Semaphore(0);

    @Override
    public void run() {
        if (TEST_MODE == 0) {
            testWithJavaMonitor();
        } else {
            testWithJVMTIRawMonitor();
        }
    }

    public void testWithJavaMonitor() {
        try {
            synchronized (o1) {
                if (firstWorker) {
                    firstWorker = false;
                    sem.release();
                }
                Thread.sleep(1000);
            }
        } catch (ThreadDeath td) {
        } catch (InterruptedException e) {
            throw new Error("Unexpected: " + e);
        }
    }


    public void testWithJVMTIRawMonitor() {
        boolean savedFirst = false;
        try {
            int retCode = enterRawMonitor();
            if (retCode != 0 && firstWorker) {
                throw new RuntimeException("error in JVMTI RawMonitorEnter: retCode=" + retCode);
            }
            if (firstWorker) {
                firstWorker = false;
                savedFirst = true;
                sem.release();
            }
            Thread.sleep(1000);
            retCode = exitRawMonitor();
            if (retCode != 0 && savedFirst) {
                throw new RuntimeException("error in JVMTI RawMonitorExit: retCode=" + retCode);
            }
        } catch (ThreadDeath td) {
        } catch (InterruptedException e) {
            throw new Error("Unexpected: " + e);
        }
    }

    public static void main(String[] args) {
        int timeMax = DEF_TIME_MAX;
        try {
            if (args.length == 1) {
                TEST_MODE = Integer.parseUnsignedInt(args[0]);
            } else if (args.length == 2) {
                TEST_MODE = Integer.parseUnsignedInt(args[0]);
                timeMax = Integer.parseUnsignedInt(args[1]);
            }
            if (TEST_MODE != 0 && TEST_MODE != 1) {
                System.err.println("'" + TEST_MODE + "': invalid mode");
                usage();
            }
        } catch (NumberFormatException nfe) {
            System.err.println("'" + args[0] + "': invalid value.");
            usage();
        }

        System.out.println("About to execute for " + timeMax + " seconds.");

        long count = 0;
        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (timeMax * 1000)) {
            count++;

            if (TEST_MODE == 1) {
                // Create JVMTI raw monitor that will be used
                int retCode = createRawMonitor();
                if (retCode != 0) {
                    throw new RuntimeException("error in JVMTI CreateRawMonitor: retCode=" + retCode);
                }
            }

            AsyncExceptionOnMonitorEnter worker1 = new AsyncExceptionOnMonitorEnter();
            AsyncExceptionOnMonitorEnter worker2 = new AsyncExceptionOnMonitorEnter();

            try {
                // Start firstWorker worker and wait until monitor is acquired
                firstWorker = true;
                worker1.start();
                sem.acquire();

                // Start second worker and allow some time for target to block on monitorenter
                // before executing Thread.stop()
                worker2.start();
                Thread.sleep(300);

                while (true) {
                    JVMTIUtils.stopThread(worker2);
                    if (TEST_MODE != 1) {
                        // Don't stop() worker1 with JVMTI raw monitors since if the monitor is
                        // not released worker2 will deadlock on enter
                        JVMTIUtils.stopThread(worker1);
                    }

                    if (!worker1.isAlive() && !worker2.isAlive()) {
                        // Done with Thread.stop() calls since
                        // threads are not alive.
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
                worker1.join();
                worker2.join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }

            if (TEST_MODE == 1) {
                // Destroy JVMTI raw monitor used
                destroyRawMonitor();
            }
        }

        System.out.println("Executed " + count + " loops in " + timeMax +
                           " seconds.");

        String cmd = System.getProperty("sun.java.command");
        if (cmd != null && !cmd.startsWith("com.sun.javatest.regtest.agent.MainWrapper")) {
            // Exit with success in a non-JavaTest environment:
            System.exit(0);
        }
    }

    public static void usage() {
        System.err.println("Usage: " + PROG_NAME + " [mode [time_max]]");
        System.err.println("where:");
        System.err.println("    mode      0: Test with Java monitors (default); 1: Test with JVMTI raw monitors");
        System.err.println("    time_max  max looping time in seconds");
        System.err.println("              (default is " + DEF_TIME_MAX +
                           " seconds)");
        System.exit(1);
    }
}
