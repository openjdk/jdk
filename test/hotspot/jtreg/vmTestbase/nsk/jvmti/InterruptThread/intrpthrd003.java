/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jvmti.InterruptThread;

import java.io.PrintStream;

public class intrpthrd003 {

    final static int THREADS_NUMBER = 32;
    final static int N_LATE_CALLS = 1000;

    static {
        try {
            System.loadLibrary("intrpthrd003");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load intrpthrd003 library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    native static int check(int ind, Thread thr);
    native static int getResult();
    native static boolean isThreadNotAliveError();

    public static void main(String[] args) {
        args = nsk.share.jvmti.JVMTITest.commonInit(args);

        System.exit(run(args, System.out) + 95/*STATUS_TEMP*/);
    }

    public static int run(String argv[], PrintStream ref) {
        intrpthrd003a runn[] = new intrpthrd003a[THREADS_NUMBER];

        System.out.println("Case 1: JVM/TI InterruptThread()");
        for (int i = 0; i < THREADS_NUMBER; i++ ) {
            runn[i] = new intrpthrd003a();
            int late_count = 1;
            synchronized (runn[i].syncObject) {
                runn[i].start();
                try {
                    runn[i].syncObject.wait();

                    for (; late_count <= N_LATE_CALLS; late_count++) {
                        if (check(i, runn[i]) == 2) break;

                        if (isThreadNotAliveError()) {
                            // Done with InterruptThread() calls since
                            // thread is not alive.
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    throw new Error("Unexpected: " + e);
                }
            }

            System.out.println("INFO: thread #" + i + ": made " + late_count +
                               " late calls to JVM/TI InterruptThread()");
            System.out.println("INFO: thread #" + i + ": N_LATE_CALLS==" +
                               N_LATE_CALLS + " value is " +
                               ((late_count >= N_LATE_CALLS) ? "NOT " : "") +
                               "large enough to cause an InterruptThread() " +
                               "call after thread exit.");

            try {
                runn[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }
            if (check(i, runn[i]) == 2) break;
            if (!isThreadNotAliveError()) {
                throw new Error("Expected JVMTI_ERROR_THREAD_NOT_ALIVE " +
                                "after thread #" + i + " has been join()'ed");
            }
        }

        int res = getResult();
        if (res != 0) {
            return res;
        }

        System.out.println("Case 2: java.lang.Thread.interrupt()");
        for (int i = 0; i < THREADS_NUMBER; i++ ) {
            runn[i] = new intrpthrd003a();
            int late_count = 1;
            synchronized (runn[i].syncObject) {
                runn[i].start();
                try {
                    runn[i].syncObject.wait();

                    for (; late_count <= N_LATE_CALLS; late_count++) {
                        runn[i].interrupt();

                        if (!runn[i].isAlive()) {
                            // Done with Thread.interrupt() calls since
                            // thread is not alive.
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    throw new Error("Unexpected: " + e);
                }
            }

            System.out.println("INFO: thread #" + i + ": made " + late_count +
                               " late calls to java.lang.Thread.interrupt()");
            System.out.println("INFO: thread #" + i + ": N_LATE_CALLS==" +
                               N_LATE_CALLS + " value is " +
                               ((late_count >= N_LATE_CALLS) ? "NOT " : "") +
                               "large enough to cause a Thread.interrupt() " +
                               "call after thread exit.");

            try {
                runn[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }
            runn[i].interrupt();
            if (runn[i].isAlive()) {
                throw new Error("Expected !Thread.isAlive() after thread #" +
                                i + " has been join()'ed");
            }
        }

        return res;
    }
}

class intrpthrd003a extends Thread {
    public Object syncObject = new Object();

    public void run() {
        synchronized (syncObject) {
            syncObject.notify();
        }
    }
}
