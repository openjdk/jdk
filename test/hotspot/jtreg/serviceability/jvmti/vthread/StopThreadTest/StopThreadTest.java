/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=default
 * @summary Verifies JVMTI StopThread support for virtual threads.
 * @requires vm.continuations
 * @library /test/lib
 * @run main/othervm/native -agentlib:StopThreadTest StopThreadTest
 */

/*
 * @test id=no-vmcontinuations
 * @summary Verifies JVMTI StopThread support for bound virtual threads.
 * @library /test/lib
 * @run main/othervm/native -agentlib:StopThreadTest -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations -DboundVThread=true StopThreadTest
 */

/*
 * @test id=platform
 * @summary Verifies JVMTI StopThread support for platform threads.
 * @library /test/lib
 * @run main/othervm/native -agentlib:StopThreadTest StopThreadTest platform
 */

import jdk.test.lib.Platform;
import java.lang.AssertionError;

/*
 *     The test exercises the JVMTI function: StopThread(jthread).
 *     The test creates a new virtual or platform thread.
 *     Its method run() invokes the following methods:
 *      - method A() that is blocked on a monitor
 *      - method B() that is stopped at a breakpoint
 *      - method C() that forces agent to send AssertionError exception to its own thread
 *     All cases are using JVMTI StopThread to send an AssertionError object.
 */
public class StopThreadTest {
    private static final String agentLib = "StopThreadTest";
    static final boolean isBoundVThread = Boolean.getBoolean("boundVThread");
    static final int JVMTI_ERROR_NONE = 0;
    static final int JVMTI_ERROR_OPAQUE_FRAME = 32;
    static final int THREAD_NOT_SUSPENDED = 13;
    static final int PASSED = 0;
    static final int FAILED = 2;

    static void log(String str) { System.out.println(str); }

    static native void prepareAgent(Class taskClass, Object exceptionObject);
    static native void suspendThread(Thread thread);
    static native void resumeThread(Thread thread);
    static native void ensureAtBreakpoint();
    static native void notifyAtBreakpoint();
    static native int  stopThread(Thread thread);

    static int status = PASSED;
    static boolean is_virtual = true;

    static void setFailed(String msg) {
        log("\nFAILED: " + msg);
        status = FAILED;
    }

    static void throwFailed(String msg) {
        log("\nFAILED: " + msg);
        throw new RuntimeException("StopThreadTest failed!");
    }

    public static void main(String args[]) {
        is_virtual = !(args.length > 0 && args[0].equals("platform"));
        run();
        if (status == FAILED) {
            throwFailed("StopThreadTest!");
        }
        log("\nStopThreadTest passed");
    }

    public static void run() {
        TestTask testTask = new TestTask();
        Thread testTaskThread = null;
        AssertionError excObject = new AssertionError();
        int retCode;

        prepareAgent(TestTask.class, excObject);

        log("\nMain #A: method A() must be blocked on entering a synchronized statement");
        synchronized (TestTask.lock) {
            if (is_virtual) {
                testTaskThread = Thread.ofVirtual().name("TestTaskThread").start(testTask);
            } else {
                testTaskThread = Thread.ofPlatform().name("TestTaskThread").start(testTask);
            }
            TestTask.ensureBlockedAfterPointA(testTaskThread);

            if (is_virtual) { // this check is for virtual target thread only
                log("\nMain #A.1: unsuspended");
                retCode = stopThread(testTaskThread);
                if (retCode != THREAD_NOT_SUSPENDED) {
                    throwFailed("Main #A.1: expected THREAD_NOT_SUSPENDED instead of: " + retCode);
                } else {
                    log("Main #A.1: got expected THREAD_NOT_SUSPENDED");
                }
            }

            log("\nMain #A.2: suspended");
            suspendThread(testTaskThread);
            retCode = stopThread(testTaskThread);
            int expectedRetCode = preemptableVirtualThread() ? JVMTI_ERROR_OPAQUE_FRAME : JVMTI_ERROR_NONE;
            String expectedRetCodeName = preemptableVirtualThread() ? "JVMTI_ERROR_OPAQUE_FRAME" : "JVMTI_ERROR_NONE";
            if (retCode != expectedRetCode) {
                throwFailed("Main #A.2: expected " + expectedRetCodeName + " instead of: " + retCode);
            } else {
                log("Main #A.2: got expected " + expectedRetCodeName);
            }
            resumeThread(testTaskThread);
        }
        log("\nMain #B: method B() must be blocked in a breakpoint event handler");
        {
            ensureAtBreakpoint();

            if (is_virtual) { // this check is for virtual target thread only
                log("\nMain #B.1: unsuspended");
                retCode = stopThread(testTaskThread);
                if (retCode != THREAD_NOT_SUSPENDED) {
                    throwFailed("Main #B.1: expected THREAD_NOT_SUSPENDED instead of: " + retCode);
                }
            }

            log("\nMain #B.2: suspended");
            suspendThread(testTaskThread);
            retCode = stopThread(testTaskThread);
            if (retCode != JVMTI_ERROR_NONE) {
                throwFailed("Main #B.2: expected JVMTI_ERROR_NONE");
            }
            resumeThread(testTaskThread);

            notifyAtBreakpoint();
        }

        log("\nMain #C: method C() sends AssertionError object to its own thread");
        {
            // StopThread is called from the test task (own thread) and expected to succeed.
            // No suspension of the test task thread is required or can be done in this case.
            TestTask.ensureFinished();
        }

        try {
            testTaskThread.join();
        } catch (InterruptedException ex) {
            throwFailed("Unexpected " + ex);
        }
    }


    static class TestTask implements Runnable {
        static Object lock = new Object();
        static void log(String str) { System.out.println(str); }

        static volatile boolean reachedPointA = false;
        static volatile boolean finished = false;

        static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interruption in TestTask.sleep: \n\t" + e);
            }
        }

        static void ensureBlockedAfterPointA(Thread vt) {
            // wait while the thread state is not the expected one
            while (!reachedPointA || vt.getState() != Thread.State.BLOCKED) {
                sleep(1);
            }
        }

        // Ensure thread is finished.
        static void ensureFinished() {
            while (!finished) {
                sleep(1);
            }
        }

        public void run() {
            log("TestTask.run: started");

            boolean seenExceptionFromA = false;
            try {
                A();
            } catch (AssertionError ex) {
                log("TestTask.run: caught expected AssertionError from method A()");
                seenExceptionFromA = true;
            }
            Thread.interrupted();
            if (!seenExceptionFromA && !preemptableVirtualThread()) {
                StopThreadTest.setFailed("TestTask.run: expected AssertionError from method A()");
            }
            sleep(1); // to cause yield

            boolean seenExceptionFromB = false;
            try {
              B();
            } catch (AssertionError ex) {
                log("TestTask.run: caught expected AssertionError from method B()");
                seenExceptionFromB = true;
            }
            Thread.interrupted();
            if (!seenExceptionFromB) {
                StopThreadTest.setFailed("TestTask.run: expected AssertionError from method B()");
            }
            sleep(1); // to cause yield

            boolean seenExceptionFromC = false;
            try {
                C();
            } catch (AssertionError ex) {
                log("TestTask.run: caught expected AssertionError from method C()");
                seenExceptionFromC = true;
            }
            Thread.interrupted();
            if (!seenExceptionFromC) {
                StopThreadTest.setFailed("TestTask.run: expected AssertionError from method C()");
            }
            finished = true;
        }

        // Method is blocked on entering a synchronized statement.
        // StopThread is used to send an AssertionError object two times:
        //  - when not suspended: THREAD_NOT_SUSPENDED is expected
        //  - when suspended: JVMTI_ERROR_NONE is expected
        static void A() {
            log("TestTask.A: started");
            reachedPointA = true;
            synchronized (lock) {
            }
            log("TestTask.A: finished");
        }

        // A breakpoint is set at start of this method.
        // StopThread is used to send an AssertionError object two times:
        //  - when not suspended: THREAD_NOT_SUSPENDED is expected
        //  - when suspended: expected to succeed
        static void B() {
            log("TestTask.B: started");
        }

        // This method uses StopThread to send an AssertionError object to
        // its own thread. It is expected to succeed.
        static void C() {
            log("TestTask.C: started");
            StopThreadTest.stopThread(Thread.currentThread());
            log("TestTask.C: finished");
        }
    }

    static boolean preemptableVirtualThread() {
        return is_virtual && !isBoundVThread;
    }
}
