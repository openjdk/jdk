/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8295976
 * @summary GetThreadListStackTraces returns wrong state for blocked VirtualThread
 * @requires vm.continuations
 * @run main/othervm/native -agentlib:ThreadListStackTracesTest ThreadListStackTracesTest
 */

import java.util.concurrent.locks.ReentrantLock;

abstract class TestTask implements Runnable {
    volatile boolean threadReady = false;

    static void log(String msg) { System.out.println(msg); }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interruption in TestTask.sleep: \n\t" + e);
        }
    }

    public void ensureReady(Thread vt, Thread.State expState) {
        // wait while the thread is not ready or thread state is unexpected
        while (!threadReady || (vt.getState() != expState)) {
            sleep(1);
        }
    }

    public abstract void run();
}

class ReentrantLockTestTask extends TestTask {
    public void run() {
        log("grabbing reentrantLock");
        threadReady = true;
        ThreadListStackTracesTest.reentrantLock.lock();
        log("grabbed reentrantLock");
    }
}

class ObjectMonitorTestTask extends TestTask {
    public void run() {
        log("entering synchronized statement");
        threadReady = true;
        synchronized (ThreadListStackTracesTest.objectMonitor) {
            log("entered synchronized statement");
        }
    }
}

public class ThreadListStackTracesTest {
    static final int JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400;
    static final int JVMTI_THREAD_STATE_WAITING = 0x0080;

    static final ReentrantLock reentrantLock = new ReentrantLock();
    static final Object objectMonitor = new Object();

    private static native int getStateSingle(Thread thread);
    private static native int getStateMultiple(Thread thread, Thread other);

    static void log(String msg) { System.out.println(msg); }
    static void failed(String msg) { throw new RuntimeException(msg); }

    public static void main(String[] args) throws InterruptedException {
        checkReentrantLock();
        checkSynchronized();
    }

    private static void checkReentrantLock() throws InterruptedException {
        final Thread.State expState = Thread.State.WAITING;
        reentrantLock.lock();
        String name = "ReentrantLockTestTask";
        TestTask task = new ReentrantLockTestTask();
        Thread vt = Thread.ofVirtual().name(name).start(task);
        task.ensureReady(vt, expState);
        checkStates(vt, expState);
    }

    private static void checkSynchronized() throws InterruptedException {
        final Thread.State expState = Thread.State.BLOCKED;
        synchronized (objectMonitor) {
            String name = "ObjectMonitorTestTask";
            TestTask task = new ObjectMonitorTestTask();
            Thread vt = Thread.ofVirtual().name(name).start(task);
            task.ensureReady(vt, expState);
            checkStates(vt, expState);
        }
    }

    private static void checkStates(Thread vt, Thread.State expState) {
        int singleState = getStateSingle(vt);
        int multiState = getStateMultiple(vt, Thread.currentThread());
        int jvmtiExpState = (expState == Thread.State.WAITING) ?
                            JVMTI_THREAD_STATE_WAITING :
                            JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;

        System.out.printf("State: expected: %s single: %x multi: %x\n",
                          vt.getState(), singleState, multiState);

        if (vt.getState() != expState) {
            failed("Java thread state is wrong");
        }
        if ((singleState & jvmtiExpState) == 0) {
            failed("JVMTI single thread state is wrong");
        }
        if ((multiState & jvmtiExpState) == 0) {
            failed("JVMTI multi thread state is wrong");
        }
    }
}
