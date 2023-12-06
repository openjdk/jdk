/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8185164 8320515
 * @summary Checks that a contended monitor does not show up in the list of owned monitors.
 *          8320515 piggy-backs on this test and injects an owned monitor with a dead object,
            and checks that that monitor isn't exposed to GetOwnedMonitorInfo.
 * @requires vm.jvmti
 * @compile GetOwnedMonitorInfoTest.java
 * @run main/othervm/native -agentlib:GetOwnedMonitorInfoTest GetOwnedMonitorInfoTest
 */

import java.io.PrintStream;

public class GetOwnedMonitorInfoTest {

    static {
        try {
            System.loadLibrary("GetOwnedMonitorInfoTest");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load GetOwnedMonitorInfoTest library");
            System.err.println("java.library.path: "
                               + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    private static native void jniMonitorEnter(Object obj);
    private static native int check();
    private static native boolean hasEventPosted();

    private static void jniMonitorEnterAndLetObjectDie() {
        // The monitor iterator used by GetOwnedMonitorInfo used to
        // assert when an owned monitor with a dead object was found.
        // Inject this situation into this test that performs other
        // GetOwnedMonitorInfo testing.
        Object obj = new Object() {};
        jniMonitorEnter(obj);
        if (!Thread.holdsLock(obj)) {
            throw new RuntimeException("The object is not locked");
        }
        obj = null;
        System.gc();
    }

    public static void main(String[] args) throws Exception {
        runTest(true, true);
        runTest(true, false);
        runTest(false, true);
        runTest(false, false);
    }

    public static void runTest(boolean isVirtual, boolean jni) throws Exception {
        var threadFactory = isVirtual ? Thread.ofVirtual().factory() : Thread.ofPlatform().factory();
        final GetOwnedMonitorInfoTest lock = new GetOwnedMonitorInfoTest();

        Thread t1 = threadFactory.newThread(() -> {
            Thread.currentThread().setName("Worker-Thread");

            if (jni) {
                jniMonitorEnterAndLetObjectDie();
            }

            synchronized (lock) {
                System.out.println("Thread in sync section: "
                                   + Thread.currentThread().getName());
            }
        });

        // Make sure t1 contends on the monitor.
        synchronized (lock) {
            System.out.println("Main starting worker thread.");
            t1.start();

            // Wait for the MonitorContendedEnter event
            while (!hasEventPosted()) {
                System.out.println("Main waiting for event.");
                Thread.sleep(100);
            }
        }

        t1.join();

        if (check() != 0) {
            throw new RuntimeException("FAILED status returned from the agent");
        }
    }
}
