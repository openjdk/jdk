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
 * @requires vm.compiler1.enabled | vm.compiler2.enabled
 * @summary Stress delivery of asynchronous exceptions.
 * @library /test/hotspot/jtreg/testlibrary
 * @run main/othervm/native
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+StressCompiledExceptionHandlers
 *                   -Xcomp -XX:TieredStopAtLevel=3
 *                   -XX:CompileCommand=dontinline,AsyncExceptionTest::internalRun2
 *                   -XX:CompileCommand=compileonly,AsyncExceptionTest::internalRun1
 *                   -XX:CompileCommand=compileonly,AsyncExceptionTest::internalRun2
 *                   AsyncExceptionTest
 * @run main/othervm/native -Xcomp
 *                   -XX:CompileCommand=dontinline,AsyncExceptionTest::internalRun2
 *                   -XX:CompileCommand=compileonly,AsyncExceptionTest::internalRun1
 *                   -XX:CompileCommand=compileonly,AsyncExceptionTest::internalRun2
 *                   AsyncExceptionTest
 */

import jvmti.JVMTIUtils;

import java.util.concurrent.CountDownLatch;

public class AsyncExceptionTest extends Thread {
    private final static int DEF_TIME_MAX = 30;  // default max # secs to test
    private final static String PROG_NAME = "AsyncExceptionTest";

    public CountDownLatch startSyncObj = new CountDownLatch(1);

    private boolean firstEntry = true;
    private boolean receivedThreadDeathinInternal1 = false;
    private boolean receivedThreadDeathinInternal2 = false;
    private volatile RuntimeException error = null;

    @Override
    public void run() {
        try {
            internalRun1();
        } catch (ThreadDeath td) {
            error = new RuntimeException("Caught ThreadDeath in run() instead of internalRun2() or internalRun1().\n"
                    + "receivedThreadDeathinInternal1=" + receivedThreadDeathinInternal1
                    + "; receivedThreadDeathinInternal2=" + receivedThreadDeathinInternal2);
        } catch (NoClassDefFoundError ncdfe) {
            // ignore because we're testing StopThread() which can cause it
        }

        if (receivedThreadDeathinInternal2 == false && receivedThreadDeathinInternal1 == false) {
            error = new RuntimeException("Didn't catch ThreadDeath in internalRun2() nor in internalRun1().\n"
                    + "receivedThreadDeathinInternal1=" + receivedThreadDeathinInternal1
                    + "; receivedThreadDeathinInternal2=" + receivedThreadDeathinInternal2);
        }
    }

    public void internalRun1() {
        try {
            while (!receivedThreadDeathinInternal2) {
              internalRun2();
            }
        } catch (ThreadDeath e) {
            receivedThreadDeathinInternal1 = true;
        }
    }

    public void internalRun2() {
        try {
            Integer myLocalCount = 1;
            Integer myLocalCount2 = 1;

            if (firstEntry) {
                // Tell main thread we have started.
                startSyncObj.countDown();
                firstEntry = false;
            }

            while(myLocalCount > 0) {
                myLocalCount2 = (myLocalCount % 3) / 2;
                myLocalCount -= 1;
            }
        } catch (ThreadDeath e) {
            receivedThreadDeathinInternal2 = true;
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
        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (timeMax * 1000)) {
            count++;
            AsyncExceptionTest thread = new AsyncExceptionTest();
            thread.start();
            try {
                // Wait for the worker thread to get going.
                thread.startSyncObj.await();
                // Send async exception and wait until it is thrown
                JVMTIUtils.stopThread(thread);
                thread.join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            } catch (NoClassDefFoundError ncdfe) {
                // Ignore because we're testing StopThread which can
                // cause it. Yes, a NoClassDefFoundError that happens
                // in a worker thread can subsequently be seen in the
                // main thread.
            }
            if (thread.isAlive()) {
                // Really shouldn't be possible after join() above...
                throw new RuntimeException("Thread did not exit.\n"
                    + "receivedThreadDeathinInternal1=" + thread.receivedThreadDeathinInternal1
                    + "; receivedThreadDeathinInternal2=" + thread.receivedThreadDeathinInternal2);
            }
            if (thread.error != null) {
                throw thread.error;
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
        System.err.println("Usage: " + PROG_NAME + " [time_max]");
        System.err.println("where:");
        System.err.println("    time_max  max looping time in seconds");
        System.err.println("              (default is " + DEF_TIME_MAX +
                           " seconds)");
        System.exit(1);
    }
}
