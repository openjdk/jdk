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

/*
 * @test
 *
 * @summary Verifies JVMTI StopThread support for virtual threads.
 * DESCRIPTION
 *     The test exercises the JVMTI function: StopThread(jthread).
 *     The test creates a new virtual thread.
 *     Its method run() invokes the following methods:
 *      - method A() that is blocked on a monitor
 *      - method B() that is stopped at a brakepoint
 *      - method C() that forces agent to send AssertionError exception to its own thread
 *     All cases are using JVMTI StopThread to send an AssertionError object.
 *
 * @requires vm.continuations
 * @compile --enable-preview -source ${jdk.version} StopThreadTest.java
 * @run main/othervm/native --enable-preview -agentlib:StopThreadTest StopThreadTest
 */

import java.lang.AssertionError;

public class StopThreadTest {
    private static final String agentLib = "StopThreadTest";
    static final int JVMTI_ERROR_NONE = 0;
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

    static void setFailed() { status = FAILED; }

    public static void main(String args[]) throws Exception {
        if (run(args) == FAILED) {
            throw new RuntimeException("StopThreadTest failed!");
        }
        log("\nStopThreadTest passed");
    }

    public static int run(String args[]) {
        TestTask testTask = new TestTask();
        Thread testTaskThread = null;
        AssertionError excObject = new AssertionError();
        int retCode;

        prepareAgent(TestTask.class, excObject);

        log("\nMain #A: method A() must be blocked on entering a synchronized statement");
        synchronized (TestTask.lock) {
            testTaskThread = Thread.ofVirtual().name("TestTaskThread").start(testTask);
            testTask.ensureStarted();

            log("\nMain #A.1: unsuspended");
            retCode = stopThread(testTaskThread);
            if (retCode != THREAD_NOT_SUSPENDED) {
                log("Failed: Main #A.1: expected THREAD_NOT_SUSPENDED instead of: " + retCode);
                setFailed();
            } else {
                log("Main #A.1: got expected THREAD_NOT_SUSPENDED");
            }

            log("\nMain #A.2: suspended");
            suspendThread(testTaskThread);
            retCode = stopThread(testTaskThread);
            if (retCode != JVMTI_ERROR_NONE) {
                log("Failed: Main #A.2: expected JVMTI_ERROR_NONE instead of: " + retCode);
                setFailed();
            } else {
                log("Main #A.2: got expected JVMTI_ERROR_NONE");
            }
            resumeThread(testTaskThread);
        }
        log("\nMain #B: method B() must be blocked in a breakpoint event handler");
        {
            ensureAtBreakpoint();

            log("\nMain #B.1: unsuspended");
            retCode = stopThread(testTaskThread);
            if (retCode != THREAD_NOT_SUSPENDED) {
                log("Failed: Main #B.1: expected THREAD_NOT_SUSPENDED instead of: " + retCode);
                setFailed();
            }

            log("\nMain #B.2: suspended");
            suspendThread(testTaskThread);
            retCode = stopThread(testTaskThread);
            if (retCode != JVMTI_ERROR_NONE) {
                log("Failed: Main #B.2: expected JVMTI_ERROR_NONE");
                setFailed();
            }
            resumeThread(testTaskThread);

            notifyAtBreakpoint();
        }

        log("\nMain #C: method C() sends AssertionError object to its own thread");
        {
            // StopThread is expected to succeed.
            testTask.ensureFinished();
        }

        try {
            testTaskThread.join();
        } catch (InterruptedException ex) {
            System.out.println("# Unexpected " + ex);
            setFailed();
        }
        return status;
    }


    static class TestTask implements Runnable {
        static Object lock = new Object();
        static void log(String str) { System.out.println(str); }

        private volatile boolean started = false;
        private volatile boolean finished = false;

        static public void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interruption in TestTask.sleep: \n\t" + e);
            }
        }

        // Ensure thread is ready.
        public void ensureStarted() {
            while (!started) {
                sleep(1);
            }
        }

        // Ensure thread is finished.
        public void ensureFinished() {
            while (!finished) {
                sleep(1);
            }
        }

        public void run() {
            log("TestTask.run: started");
            started = true;

            boolean seenExceptionFromA = false;
            try {
                A();
            } catch (AssertionError ex) {
                log("TestTask.run: caught expected AssertionError from method A()");
                seenExceptionFromA = true;
            }
            if (!seenExceptionFromA) {
                log("Failed: TestTask.run: expected AssertionError from method A()");
                StopThreadTest.setFailed();
            }
            sleep(1); // to cause yield

            boolean seenExceptionFromB = false;
            try {
              B();
            } catch (AssertionError ex) {
                log("TestTask.run: caught expected AssertionError from method B()");
                seenExceptionFromB = true;
            }
            if (!seenExceptionFromB) {
                log("Failed: TestTask.run: expected AssertionError from method B()");
                StopThreadTest.setFailed();
            }
            sleep(1); // to cause yield

            boolean seenExceptionFromC = false;
            try {
                C();
            } catch (AssertionError ex) {
                log("TestTask.run: caught expected AssertionError from method C()");
                seenExceptionFromC = true;
            }
            if (!seenExceptionFromC) {
                log("Failed: TestTask.run: expected AssertionError from method C()");
                StopThreadTest.setFailed();
            }
            finished = true;
        }

        // Method is blocked on entering a synchronized statement.
        // StopThread is used to send an AssertionError object two times:
        //  - when not suspended: THREAD_NOT_SUSPENDED is expected
        //  - when suspended: JVMTI_ERROR_NONE is expected
        static void A() {
            log("TestTask.A: started");
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
}
