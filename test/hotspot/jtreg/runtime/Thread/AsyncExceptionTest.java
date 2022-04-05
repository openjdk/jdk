/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stress delivery of asynchronous exceptions.
 * @library /test/lib /test/hotspot/jtreg
 * @build AsyncExceptionTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI AsyncExceptionTest
 */

import compiler.testlibrary.CompilerUtils;
import compiler.whitebox.CompilerWhiteBoxTest;
import java.util.concurrent.CountDownLatch;
import sun.hotspot.WhiteBox;

public class AsyncExceptionTest extends Thread {
    private final static int DEF_TIME_MAX = 30;  // default max # secs to test
    private final static String PROG_NAME = "AsyncExceptionTest";

    public static final WhiteBox WB = WhiteBox.getWhiteBox();

    public CountDownLatch exitSyncObj = new CountDownLatch(1);
    public CountDownLatch startSyncObj = new CountDownLatch(1);

    private boolean realRun;
    private boolean firstEntry = true;
    private boolean receivedThreadDeathinInternal1 = false;
    private boolean receivedThreadDeathinInternal2 = false;

    public void setDontInline(String method) {
        java.lang.reflect.Method m;
        try {
            m = AsyncExceptionTest.class.getMethod(method);
        } catch(NoSuchMethodException e) {
            throw new RuntimeException("Unexpected: " + e);
        }
        WB.testSetDontInlineMethod(m, true);
    }

    public void checkCompLevel(String method) {
        int highestLevel = CompilerUtils.getMaxCompilationLevel();
        java.lang.reflect.Method m;
        try {
            m = AsyncExceptionTest.class.getMethod(method);
        } catch(NoSuchMethodException e) {
            throw new RuntimeException("Unexpected: " + e);
        }
        int compLevel = WB.getMethodCompilationLevel(m);
        while (compLevel < (highestLevel - 1)) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) { /* ignored */ }
            compLevel = WB.getMethodCompilationLevel(m);
        }
    }

    @Override
    public void run() {
        try {
            setDontInline("internalRun1");
            setDontInline("internalRun2");

            int callCount = CompilerWhiteBoxTest.THRESHOLD;
            while (callCount-- > 0) {
                receivedThreadDeathinInternal2 = false;
                realRun = false;
                internalRun1();
            }
            checkCompLevel("internalRun1");
            checkCompLevel("internalRun2");

            receivedThreadDeathinInternal2 = false;
            realRun = true;
            internalRun1();
        } catch (ThreadDeath td) {
            throw new RuntimeException("Catched ThreadDeath in run() instead of internalRun2() or internalRun1(). receivedThreadDeathinInternal1=" + receivedThreadDeathinInternal1 + "; receivedThreadDeathinInternal2=" + receivedThreadDeathinInternal2);
        } catch (NoClassDefFoundError ncdfe) {
            // ignore because we're testing Thread.stop() which can cause it
        }

        if (receivedThreadDeathinInternal2 == false && receivedThreadDeathinInternal1 == false) {
            throw new RuntimeException("Didn't catched ThreadDeath in internalRun2() nor in internalRun1(). receivedThreadDeathinInternal1=" + receivedThreadDeathinInternal1 + "; receivedThreadDeathinInternal2=" + receivedThreadDeathinInternal2);
        }
        exitSyncObj.countDown();
    }

    public void internalRun1() {
        long start_time = System.currentTimeMillis();
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

            if (realRun && firstEntry) {
                // Tell main thread we have started.
                startSyncObj.countDown();
                firstEntry = false;
            }

            while(myLocalCount > 0) {
                if (!realRun) {
                    receivedThreadDeathinInternal2 = true;
                    break;
                }
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
                while (true) {
                    // Send async exception and wait until it is thrown
                    thread.stop();
                    thread.exitSyncObj.await();
                    Thread.sleep(100);

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

