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
 * @bug 8373839
 * @requires vm.jvmti
 * @library /test/lib
 * @run main/othervm/native -agentlib:SuspendInCritical SuspendInCritical 1
 * @run main/othervm/native -agentlib:SuspendInCritical SuspendInCritical 2
 * @run main/othervm/native -agentlib:SuspendInCritical SuspendInCritical 3
 */
import jdk.test.lib.Asserts;

import java.util.concurrent.CountDownLatch;

public class SuspendInCritical {

    static {
        System.loadLibrary("SuspendInCritical");
    }

    static native void suspendThread(Thread t);
    static native void resumeThread(Thread t);
    static native boolean isSuspended(Thread t);

    static native void doCritical(byte[] b, String str);
    static native void leaveCriticalNative();
    static native long getNativeCounter();

    static volatile boolean suspendStarted = false;
    static volatile boolean suspendCompleted = false;
    static volatile boolean canTerminate = false;

    /*
     * Incoming arg sets the mode:
     * - 1: GetPrimitiveArrayCritical only
     * - 2: GetStringCritical only
     * - 3: Do both so we have a nested critical region
     */
    public static void main(String[] args) throws Throwable {
        int mode = Integer.parseInt(args[0]);
        final byte[] bytes = (mode == 1 || mode == 3) ? new byte[16] : null;
        final String str = (mode == 2 || mode == 3) ? "A String" : null;

        final Thread t = new Thread() {
                public void run() {
                    System.out.println("CriticalThread calling native ...");
                    doCritical(bytes, str);
                    System.out.println("CriticalThread return from native ...");
                    // delay termination so we can check the suspend state
                    // from another thread
                    while (!canTerminate) {
                        delay(10);
                    }
                    System.out.println("CriticalThread terminating");
                }
            };
        t.setName("CriticalThread");

        System.out.println("main thread starting CriticalThread");

        // Start the target thread. It will enter a JNI critical region
        // and stay executing native code until released.
        t.start();

        // Check that the counter is progressing
        checkNativeCounter();

        System.out.println("main thread saw CriticalThread executing and is starting SuspenderThread");

        // Now start the suspender thread.
        Thread s = new Thread() {
                public void run() {
                    suspendStarted = true;
                    System.out.println("SuspenderThread calling suspend ...");
                    // This will block until t is out of the critical region
                    suspendThread(t);
                    suspendCompleted = true;

                    System.out.println("SuspenderThread checking suspend ...");
                    // Verify t is suspended
                    Asserts.assertTrue(isSuspended(t), "not suspended");

                    System.out.println("SuspenderThread calling resume   ...");
                    // Resume t
                    resumeThread(t);

                    System.out.println("SuspenderThread checking not suspended ...");
                    Asserts.assertFalse(isSuspended(t), "suspended");

                    System.out.println("SuspenderThread allowing target to terminate ...");
                    // Allow t to terminate
                    canTerminate = true;
                    System.out.println("SuspenderThread terminatng");
                }
            };
        s.setName("SuspenderThread");
        s.start();

        while (!suspendStarted) {
            delay(2);
        }

        // Check suspender is blocked
        checkSuspenderIsBlocked();

        System.out.println("main thread confirms SuspenderThread is blocked in suspend()");

        // Check target is still progressing
        checkNativeCounter();

        System.out.println("main thread saw CriticalThread still executing and has enabled the upcall");

        // Allow target to proceed to Java upcall
        leaveCriticalNative();

        // Wait till target is in Java upcall
        waitForUpcall();

        System.out.println("main thread saw CriticalThread in upcall");

        // Check suspender is blocked
        checkSuspenderIsBlocked();

        System.out.println("main thread confirms SuspenderThread is still blocked in suspend()");

        // Check target is still executing
        checkUpcallCount();

        System.out.println("main thread confirms CriticalThread is still executing and will let it return and be suspended");

        // Check suspender is still blocked
        checkSuspenderIsBlocked();

        // Allow target to return from Java and exit critical
        upcallDone = true;

        // The suspension can now proceed, then both threads will terminate
        t.join();
        s.join();
        System.out.println("main thread terminating");
    }

    static void checkNativeCounter() {
        long counter = getNativeCounter();
        // If the counter never progresses then the test will timeout
        while (counter == getNativeCounter()) {
            delay(5);
        }
    }

    static void checkSuspenderIsBlocked() {
        delay(200);
        Asserts.assertTrue(!suspendCompleted,"Unexpected suspend completion");
    }

    static volatile boolean upcallDone = false;
    static volatile long upcallCounter = 0;
    static CountDownLatch inUpcall = new CountDownLatch(1);

    static void waitForUpcall() throws InterruptedException {
        inUpcall.await();
    }

    static void upcall() {
        inUpcall.countDown();
        System.out.println("CriticalThread is executing upcall");
        while (!upcallDone) {
            upcallCounter++;
            delay(1);
        }
    }

    static void checkUpcallCount() {
        long count = upcallCounter;
        // If the counter never progresses then the test will timeout
        while (count == upcallCounter) {
            delay(5);
        }
    }

    static void delay(long ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException ex) {
            throw new Error("interrupted!");
        }
    }

}
