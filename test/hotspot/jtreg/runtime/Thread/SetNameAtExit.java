/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8167108
 * @summary Stress test java.lang.Thread.setName() at thread exit.
 * @run main/othervm -Xlog:thread+smr=debug SetNameAtExit
 */

import java.util.concurrent.CountDownLatch;

public class SetNameAtExit extends Thread {
    final static int N_THREADS = 32;
    final static int N_LATE_CALLS = 1000;

    public CountDownLatch exitSyncObj = new CountDownLatch(1);
    public CountDownLatch startSyncObj = new CountDownLatch(1);

    @Override
    public void run() {
        // Tell main thread we have started.
        startSyncObj.countDown();
        try {
            // Wait for main thread to interrupt us so we
            // can race to exit.
            exitSyncObj.await();
        } catch (InterruptedException e) {
            // ignore because we expect one
        }
    }

    public static void main(String[] args) {
        SetNameAtExit threads[] = new SetNameAtExit[N_THREADS];

        for (int i = 0; i < N_THREADS; i++ ) {
            threads[i] = new SetNameAtExit();
            int late_count = 1;
            threads[i].start();
            try {
                // Wait for the worker thread to get going.
                threads[i].startSyncObj.await();

                // This interrupt() call will break the worker out
                // of the exitSyncObj.await() call and the setName()
                // calls will come in during thread exit.
                threads[i].interrupt();
                for (; late_count <= N_LATE_CALLS; late_count++) {
                    threads[i].setName("T" + i + "-" + late_count);

                    if (!threads[i].isAlive()) {
                        // Done with Thread.setName() calls since
                        // thread is not alive.
                        break;
                    }
                }
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }

            System.out.println("INFO: thread #" + i + ": made " + late_count +
                               " late calls to java.lang.Thread.setName()");
            System.out.println("INFO: thread #" + i + ": N_LATE_CALLS==" +
                               N_LATE_CALLS + " value is " +
                               ((late_count >= N_LATE_CALLS) ? "NOT " : "") +
                               "large enough to cause a Thread.setName() " +
                               "call after thread exit.");

            try {
                threads[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }
            threads[i].setName("T" + i + "-done");
            if (threads[i].isAlive()) {
                throw new Error("Expected !Thread.isAlive() after thread #" +
                                i + " has been join()'ed");
            }
        }

        String cmd = System.getProperty("sun.java.command");
        if (cmd != null && !cmd.startsWith("com.sun.javatest.regtest.agent.MainWrapper")) {
            // Exit with success in a non-JavaTest environment:
            System.exit(0);
        }
    }
}
