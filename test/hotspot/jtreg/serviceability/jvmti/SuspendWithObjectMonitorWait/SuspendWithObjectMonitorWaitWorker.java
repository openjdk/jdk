/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

public class SuspendWithObjectMonitorWaitWorker extends Thread {
    private SuspendWithObjectMonitorWaitWorker target;  // target for resume operation
    private final long waitTimeout;

    public SuspendWithObjectMonitorWaitWorker(String name, long waitTimeout) {
        super(name);
        this.waitTimeout = waitTimeout;
    }

    public SuspendWithObjectMonitorWaitWorker(String name, SuspendWithObjectMonitorWaitWorker target) {
        super(name);
        this.target = target;
        this.waitTimeout = 0;
    }

    native static int resumeThread(SuspendWithObjectMonitorWaitWorker thr);

    public void run() {
        SuspendWithObjectMonitorWaitBase.logDebug("thread running");

        //
        // Launch the waiter thread:
        // - grab the threadLock
        // - threadLock.wait()
        // - releases threadLock
        //
        if (getName().equals("waiter")) {
            // grab threadLock before we tell main we are running
            SuspendWithObjectMonitorWaitBase.logDebug("before enter threadLock");
            synchronized(SuspendWithObjectMonitorWaitBase.threadLock) {
                SuspendWithObjectMonitorWaitBase.logDebug("enter threadLock");

                SuspendWithObjectMonitorWaitBase.checkTestState(SuspendWithObjectMonitorWaitBase.TS_INIT);

                synchronized(SuspendWithObjectMonitorWaitBase.barrierLaunch) {
                    // tell main we are running
                    SuspendWithObjectMonitorWaitBase.testState = SuspendWithObjectMonitorWaitBase.TS_WAITER_RUNNING;
                    SuspendWithObjectMonitorWaitBase.barrierLaunch.notify();
                }

                SuspendWithObjectMonitorWaitBase.logDebug("before wait");

                // TS_READY_TO_NOTIFY is set after the main thread has
                // entered threadLock so a spurious wakeup can't get the
                // waiter thread out of this threadLock.wait(0) call:
                while (SuspendWithObjectMonitorWaitBase.testState <= SuspendWithObjectMonitorWaitBase.TS_READY_TO_NOTIFY) {
                    try {
                        SuspendWithObjectMonitorWaitBase.threadLock.wait(waitTimeout);
                    } catch (InterruptedException ex) {
                    }
                }

                SuspendWithObjectMonitorWaitBase.logDebug("after wait");

                SuspendWithObjectMonitorWaitBase.checkTestState(SuspendWithObjectMonitorWaitBase.TS_CALL_RESUME);
                SuspendWithObjectMonitorWaitBase.testState = SuspendWithObjectMonitorWaitBase.TS_WAITER_DONE;

                SuspendWithObjectMonitorWaitBase.logDebug("exit threadLock");
            }
        }
        //
        // Launch the resumer thread:
        // - tries to grab the threadLock (should not block with doWork1!)
        // - grabs threadLock
        // - resumes the waiter thread
        // - releases threadLock
        //
        else if (getName().equals("resumer")) {
            synchronized(SuspendWithObjectMonitorWaitBase.barrierResumer) {
                synchronized(SuspendWithObjectMonitorWaitBase.barrierLaunch) {
                    // tell main we are running
                    SuspendWithObjectMonitorWaitBase.testState = SuspendWithObjectMonitorWaitBase.TS_RESUMER_RUNNING;
                    SuspendWithObjectMonitorWaitBase.barrierLaunch.notify();
                }
                SuspendWithObjectMonitorWaitBase.logDebug("thread waiting");
                while (SuspendWithObjectMonitorWaitBase.testState != SuspendWithObjectMonitorWaitBase.TS_READY_TO_RESUME) {
                    try {
                        // wait for main to tell us when to continue
                        SuspendWithObjectMonitorWaitBase.barrierResumer.wait(0);
                    } catch (InterruptedException ex) {
                    }
                }
            }

            SuspendWithObjectMonitorWaitBase.logDebug("before enter threadLock");
            synchronized(SuspendWithObjectMonitorWaitBase.threadLock) {
                SuspendWithObjectMonitorWaitBase.logDebug("enter threadLock");

                SuspendWithObjectMonitorWaitBase.checkTestState(SuspendWithObjectMonitorWaitBase.TS_READY_TO_RESUME);
                SuspendWithObjectMonitorWaitBase.testState = SuspendWithObjectMonitorWaitBase.TS_CALL_RESUME;

                // resume the waiter thread so waiter.join() can work
                SuspendWithObjectMonitorWaitBase.logDebug("before resume thread");
                int retCode = resumeThread(target);
                if (retCode != 0) {
                    throw new RuntimeException("error in JVMTI ResumeThread: " +
                            "retCode=" + retCode);
                }
                SuspendWithObjectMonitorWaitBase.logDebug("resumed thread");

                SuspendWithObjectMonitorWaitBase.logDebug("exit threadLock");
            }
        }
    }
}
