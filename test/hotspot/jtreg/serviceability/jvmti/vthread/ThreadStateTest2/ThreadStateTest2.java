/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8373366
 * @summary HandshakeState should disallow suspend ops for disabler threads
 * @requires vm.continuations
 * @requires vm.jvmti
 * @requires vm.compMode != "Xcomp"
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run main/othervm/native -agentlib:ThreadStateTest2 ThreadStateTest2
 */

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import jdk.test.lib.thread.VThreadScheduler;

/* Testing scenario:
 * Several threads are involved:
 *  - VT-0: a virtual thread which is interrupt-friendly and constantly interrupted with JVMTI InterruptThread
 *  - VT-1: a virtual thread which state is constantly checked with JVMTI GetThreadState
 *  - VT-2: a virtual thread: in a loop calls JVMTI InterruptThread(VT-0) and GetThreadState(VT-1)
 *  - main: a platform thread: in a loop invokes native method testSuspendResume which suspends and resumes VT-2
 * The JVMTI functions above install a MountUnmountDisabler for target virtual thread (VT-0 or VT-1).
 * The goal is to catch VT-2 in an attempt to self-suspend while in a context of MountUnmountDisabler.
 * This would mean there is a suspend point while VT-2 is in a context of MountUnmountDisabler.
 * The InterruptThread implementation does a Java upcall to j.l.Thread::interrupt().
 * The JavaCallWrapper constructor has such a suspend point.
 */
public class ThreadStateTest2 {
    private static native void setMonitorContendedMode(boolean enable);
    private static native void testSuspendResume(Thread vthread);
    private static native void testInterruptThread(Thread vthread);
    private static native int testGetThreadState(Thread vthread);

    static Thread vthread0;
    static Thread vthread1;
    static Thread vthread2;
    static AtomicBoolean vt2Started = new AtomicBoolean();
    static AtomicBoolean vt2Finished = new AtomicBoolean();

    static void log(String msg) { System.out.println(msg); }

    // Should handle interruptions from vthread2.
    final Runnable FOO_0 = () -> {
        log("VT-0 started");
        while (!vt2Finished.get()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ie) {
                // ignore
            }
        }
        log("VT-0 finished");
    };

    // A target for vthread2 to check state with JVMTI GetThreadState.
    final Runnable FOO_1 = () -> {
        log("VT-1 started");
        while (!vt2Finished.get()) {
            Thread.yield();
        }
        log("VT-1 finished");
    };

    // In a loop execute JVMTI functions on threads vthread0 and vthread1:
    // InterruptThread(vthread0) and GetThreadState(vthread1).
    final Runnable FOO_2 = () -> {
        log("VT-2 started");
        vt2Started.set(true);
        for (int i = 0; i < 40; i++) {
            testInterruptThread(vthread0);
            int state = testGetThreadState(vthread1);
            if (state == 2) {
                break;
            }
            Thread.yield();
        }
        vt2Finished.set(true);
        log("VT-2 finished");
    };

    private void runTest() throws Exception {
        // Force creation of JvmtiThreadState on vthread start.
        setMonitorContendedMode(true);

        ExecutorService scheduler = Executors.newFixedThreadPool(2);
        ThreadFactory factory = VThreadScheduler.virtualThreadBuilder(scheduler).factory();

        vthread0 = factory.newThread(FOO_0);
        vthread1 = factory.newThread(FOO_1);
        vthread2 = factory.newThread(FOO_2);
        vthread0.setName("VT-0");
        vthread1.setName("VT-1");
        vthread2.setName("VT-2");
        vthread0.start();
        vthread1.start();
        vthread2.start();

        // Give some time for vthreads to start.
        while (!vt2Started.get()) {
            Thread.sleep(1);
        }
        while (!vt2Finished.get() /* && tryCount-- > 0 */) {
            testSuspendResume(vthread2);
        }
        vthread0.join();
        vthread1.join();
        vthread2.join();

        // Let all carriers go away.
        scheduler.shutdown();
        Thread.sleep(20);
    }

    public static void main(String[] args) throws Exception {
        ThreadStateTest2 obj = new ThreadStateTest2();
        obj.runTest();
    }
}
