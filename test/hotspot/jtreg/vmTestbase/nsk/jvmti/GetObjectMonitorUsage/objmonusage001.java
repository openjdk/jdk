/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jvmti.GetObjectMonitorUsage;

import java.io.PrintStream;

public class objmonusage001 {
    final static int JCK_STATUS_BASE = 95;
    final static int NUMBER_OF_THREADS = 32;
    final static boolean ADD_DELAYS_FOR_RACES = false;

    static {
        try {
            System.loadLibrary("objmonusage001");
        } catch (UnsatisfiedLinkError err) {
            System.err.println("Could not load objmonusage001 library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw err;
        }
    }

    native static int getResult();
    native static void check(int index, Object syncObject, Thread owner, int entryCount,
                             Thread waiterThread, int waiterCount,
                             Thread notifyWaiterThread, int notifyWaiterCount);

    public static void main(String argv[]) {
        argv = nsk.share.jvmti.JVMTITest.commonInit(argv);

        System.exit(run(argv, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        Thread mainThread = Thread.currentThread();
        Object syncObject[] = new Object[NUMBER_OF_THREADS];
        objmonusage001a runn[] = new objmonusage001a[NUMBER_OF_THREADS];

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            syncObject[i] = new Object();
            runn[i] = new objmonusage001a(mainThread, i, syncObject[i]);
        }
        // Virtual threads are not supported by GetObjectMonitorUsage.
        // Correct the expected values if the test is executed with
        // JTREG_TEST_THREAD_FACTORY=Virtual.
        Thread expOwner = mainThread.isVirtual() ? null : mainThread;
        int expEntryCount = mainThread.isVirtual() ? 0 : 1;

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            Thread expNotifyWaiter = runn[i].isVirtual() ? null : runn[i];
            int expNotifyWaitingCount = runn[i].isVirtual() ? 0 : 1;

            synchronized (syncObject[i]) {
                runn[i].start();
                try {
                    syncObject[i].wait();
                } catch (Throwable e) {
                    out.println(e);
                    return 2;
                }

                // Check #2:
                // - owner == main:
                //       main thread owns the monitor and worker thread
                //       is in wait() and is not notified
                // - entry_count == 1:
                //       main thread reentered 1 time
                // - waiter_count == 0:
                //       main thread has already reentered the monitor and worker thread
                //       is in wait() and is not notified so it is not waiting to reenter
                //       the monitor
                // - waiter_thread == null:
                //       no thread is waiting to reenter the monitor
                // - notify_waiter_count == 1:
                //       worker thread is in wait() and is not notified
                // - notify_waiter_thread == runn[i]:
                //       worker thread is in wait() and is not notified
                //
                // This is a stable verification point because the worker thread is in wait()
                // and is not notified and the main thread is doing the verification.
                //
                check(NUMBER_OF_THREADS + i, syncObject[i], expOwner, expEntryCount,
                      null, 0, expNotifyWaiter, expNotifyWaitingCount);
            }

            // Check #3:
            // - owner == null:
            //       main thread does not own the monitor and worker thread is in
            //       wait() and is not notified so there is no owner
            // - entry_count == 0:
            //       no owner so entry_count is 0
            // - waiter_count == 0:
            //       main thread is not trying to enter the monitor and worker thread
            //       is in wait() and is not notified so it is not waiting to reenter
            //       the monitor
            // - waiter_thread == null:
            //       no thread is waiting to reenter the monitor
            // - notify_waiter_count == 1:
            //       worker thread is in wait() and is not notified
            // - notify_waiter_thread == runn[i]:
            //       worker thread is in wait() and is not notified
            //
            // This is a stable verification point because the worker thread is in wait()
            // and is not notified and the main thread is doing the verification.
            //
            check((NUMBER_OF_THREADS * 2) + i, syncObject[i], null, 0,
                  null, 0, expNotifyWaiter, expNotifyWaitingCount);
        }

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            synchronized (syncObject[i]) {
                syncObject[i].notify();
            }
            try {
                runn[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }

        return getResult();
    }
}

class objmonusage001a extends Thread {
    Thread mainThread;
    Object syncObject;
    int index;

    public objmonusage001a(Thread mt, int i, Object s) {
        mainThread = mt;
        index = i;
        syncObject = s;
    }

    public void run() {
        // Virtual threads are not supported by GetObjectMonitorUsage.
        // Correct the expected values if the test is executed with
        // JTREG_TEST_THREAD_FACTORY=Virtual.
        Thread expOwner = this.isVirtual() ? null : this;
        Thread expNotifyWaiter = mainThread.isVirtual() ? null : mainThread;
        int expEntryCount = this.isVirtual() ? 0 : 1;
        int expNotifyWaitingCount = mainThread.isVirtual() ? 0 : 1;

        synchronized (syncObject) {
            // Check #1:
            // - owner == this_thread:
            //       this worker thread is owner
            // - entry_count == 1:
            //       worker thread entered 1 time
            // - waiter_count == 0:
            //       main thread is in wait() and is not notified so it is not
            //       waiting to reenter the monitor
            // - waiter_thread == null:
            //       no thread is waiting to reenter the monitor
            // - notify_waiter_count == 1:
            //        main thread is in wait() and is not notified
            // - notify_waiter_thread == mainThread:
            //       main thread is in wait() and is not notified
            //
            // This is a stable verification point because the main thread is in wait()
            // and is not notified and this worker thread is doing the verification.
            //
            objmonusage001.check(index, syncObject, expOwner, expEntryCount,
                                 null, 0, expNotifyWaiter, expNotifyWaitingCount);
            syncObject.notify();

            try {
                syncObject.wait();

                if (objmonusage001.ADD_DELAYS_FOR_RACES) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }
    }
}
