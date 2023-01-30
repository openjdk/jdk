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
 * @summary Verifies JVMTI GetLocalXXX/SetLocalXXX return errors for unsuspended vthreads
 * @requires vm.continuations
 * @enablePreview
 * @library /test/lib
 * @run main/othervm/native -agentlib:GetSetLocalUnsuspended GetSetLocalUnsuspended
 */


public class GetSetLocalUnsuspended {
    private static final String agentLib = "GetSetLocalUnsuspended";

    private static native void testUnsuspendedThread(Thread thread);

    private static volatile boolean doStop;

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interruption in Thread.sleep: \n\t" + e);
        }
    }

    static final Runnable SLEEPING_THREAD = () -> {
        while (!doStop) {
            sleep(1);
        }
    };

    private static void testPlatformThread() throws Exception {
        doStop = false;
        Thread thread = Thread.ofPlatform().name("SleepingPlatformThread").start(SLEEPING_THREAD);
        testUnsuspendedThread(thread);
        doStop = true;
        thread.join();
    }

    private static void testVirtualThread() throws Exception {
        doStop = false;
        Thread thread = Thread.ofVirtual().name("SleepingVirtualThread").start(SLEEPING_THREAD);
        testUnsuspendedThread(thread);
        doStop = true;
        thread.join();
    }

    private void runTest() throws Exception {
        testPlatformThread();
        testVirtualThread();
    }

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(agentLib);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + agentLib + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }

        GetSetLocalUnsuspended obj = new GetSetLocalUnsuspended();
        obj.runTest();

    }
}
