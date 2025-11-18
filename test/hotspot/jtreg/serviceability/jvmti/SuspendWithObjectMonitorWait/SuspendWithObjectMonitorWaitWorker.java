/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

class SuspendWithObjectMonitorWaitWorker extends Thread {
    private SuspendWithObjectMonitorWaitWorker target;  // target for resume operation
    private final long waitTimeout;

    public SuspendWithObjectMonitorWaitWorker(String name) {
        super(name);
        this.waitTimeout = 0;
    }

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
        SuspendWithObjectMonitorWait1.logDebug("thread running");

        //
        // Launch the waiter thread:
        // - grab the threadLock
        // - threadLock.wait()
        // - releases threadLock
        //
        if (getName().equals("waiter")) {
            // grab threadLock before we tell main we are running
            SuspendWithObjectMonitorWait1.logDebug("before enter threadLock");
            synchronized(SuspendWithObjectMonitorWait1.threadLock) {
                SuspendWithObjectMonitorWait1.logDebug("enter threadLock");

                SuspendWithObjectMonitorWait1.checkTestState(SuspendWithObjectMonitorWait1.TS_INIT);

                synchronized(SuspendWithObjectMonitorWait1.barrierLaunch) {
                    // tell main we are running
                    SuspendWithObjectMonitorWait1.testState = SuspendWithObjectMonitorWait1.TS_WAITER_RUNNING;
                    SuspendWithObjectMonitorWait1.barrierLaunch.notify();
                }

                SuspendWithObjectMonitorWait1.logDebug("before wait");

                // TS_READY_TO_NOTIFY is set after the main thread has
                // entered threadLock so a spurious wakeup can't get the
                // waiter thread out of this threadLock.wait(0) call:
                while (SuspendWithObjectMonitorWait1.testState <= SuspendWithObjectMonitorWait1.TS_READY_TO_NOTIFY) {
                    try {
                        SuspendWithObjectMonitorWait1.threadLock.wait(waitTimeout);
                    } catch (InterruptedException ex) {
                    }
                }

                SuspendWithObjectMonitorWait1.logDebug("after wait");

                SuspendWithObjectMonitorWait1.checkTestState(SuspendWithObjectMonitorWait1.TS_CALL_RESUME);
                SuspendWithObjectMonitorWait1.testState = SuspendWithObjectMonitorWait1.TS_WAITER_DONE;

                SuspendWithObjectMonitorWait1.logDebug("exit threadLock");
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
            synchronized(SuspendWithObjectMonitorWait1.barrierResumer) {
                synchronized(SuspendWithObjectMonitorWait1.barrierLaunch) {
                    // tell main we are running
                    SuspendWithObjectMonitorWait1.testState = SuspendWithObjectMonitorWait1.TS_RESUMER_RUNNING;
                    SuspendWithObjectMonitorWait1.barrierLaunch.notify();
                }
                SuspendWithObjectMonitorWait1.logDebug("thread waiting");
                while (SuspendWithObjectMonitorWait1.testState != SuspendWithObjectMonitorWait1.TS_READY_TO_RESUME) {
                    try {
                        // wait for main to tell us when to continue
                        SuspendWithObjectMonitorWait1.barrierResumer.wait(0);
                    } catch (InterruptedException ex) {
                    }
                }
            }

            SuspendWithObjectMonitorWait1.logDebug("before enter threadLock");
            synchronized(SuspendWithObjectMonitorWait1.threadLock) {
                SuspendWithObjectMonitorWait1.logDebug("enter threadLock");

                SuspendWithObjectMonitorWait1.checkTestState(SuspendWithObjectMonitorWait1.TS_READY_TO_RESUME);
                SuspendWithObjectMonitorWait1.testState = SuspendWithObjectMonitorWait1.TS_CALL_RESUME;

                // resume the waiter thread so waiter.join() can work
                SuspendWithObjectMonitorWait1.logDebug("before resume thread");
                int retCode = resumeThread(target);
                if (retCode != 0) {
                    throw new RuntimeException("error in JVMTI ResumeThread: " +
                            "retCode=" + retCode);
                }
                SuspendWithObjectMonitorWait1.logDebug("resumed thread");

                SuspendWithObjectMonitorWait1.logDebug("exit threadLock");
            }
        }
    }
}
