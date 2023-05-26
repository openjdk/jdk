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
 * @test id=default
 * @summary Verifies JVMTI ForceEarlyReturn support for virtual threads.
 * @requires vm.continuations
 * @run main/othervm/native -agentlib:ForceEarlyReturnTest ForceEarlyReturnTest
 */

/*
 * @test id=no-vmcontinuations
 * @summary Verifies JVMTI ForceEarlyReturn support for bound virtual threads.
 * @run main/othervm/native -agentlib:ForceEarlyReturnTest -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations ForceEarlyReturnTest
 */

/*
 * @test id=platform
 * @summary Verifies JVMTI ForceEarlyReturn support for platform threads.
 * @run main/othervm/native -agentlib:ForceEarlyReturnTest ForceEarlyReturnTest platform
 */

import java.lang.AssertionError;

/*
 *     The test exercises the JVMTI function ForceEarlyReturn.
 *     The test creates a new virtual or platform thread.
 *     Its method run() invokes the following methods:
 *      - method A() that is blocked on a monitor
 *      - method B() that is stopped at a breakpoint
 *      - method C() that forces agent to call ForceEarlyReturn on its own thread
 *     JVMTI ForceEarlyReturn is called in all cases.
 */
public class ForceEarlyReturnTest {
    private static final String agentLib = "ForceEarlyReturnTest";
    static final int JVMTI_ERROR_NONE = 0;
    static final int THREAD_NOT_SUSPENDED = 13;
    static final int OPAQUE_FRAME = 32;
    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int expValA1 = 111;
    static final int expValA2 = 222;
    static final String expValB1 = "B1";
    static final String expValB2 = "B2";
    static final String expValB3 = "B3";

    static void log(String str) { System.out.println(str); }

    static native void prepareAgent(Class taskClass);
    static native void suspendThread(Thread thread);
    static native void resumeThread(Thread thread);
    static native void ensureAtBreakpoint();
    static native void notifyAtBreakpoint();
    static native int  forceEarlyReturnV(Thread thread);
    static native int  forceEarlyReturnI(Thread thread, int val);
    static native int  forceEarlyReturnO(Thread thread, Object obj);

    static int status = PASSED;
    static boolean is_virtual = true;

    static void setFailed(String msg) {
        log("\nFAILED: " + msg);
        status = FAILED;
    }

    static void throwFailed(String msg) {
        log("\nFAILED: " + msg);
        throw new RuntimeException("ForceEarlyReturnTest failed!");
    }

    public static void main(String args[]) {
        is_virtual = !(args.length > 0 && args[0].equals("platform"));
        run();
        if (status == FAILED) {
            throwFailed("ForceEarlyReturnTest!");
        }
        log("\nForceEarlyReturnTest passed");
    }

    public static void run() {
        TestTask testTask = new TestTask();
        Thread testTaskThread = null;
        int errCode;

        prepareAgent(TestTask.class);

        log("\nMain #A: method A() must be blocked on entering a synchronized statement");
        if (is_virtual) {
            testTaskThread = Thread.ofVirtual().name("TestTaskThread").start(testTask);
        } else {
            testTaskThread = Thread.ofPlatform().name("TestTaskThread").start(testTask);
        }

        {
            TestTask.ensureAtPointA();

            log("\nMain #A.1: unsuspended");
            errCode = forceEarlyReturnI(testTaskThread, expValA1);
            if (errCode != THREAD_NOT_SUSPENDED) {
                throwFailed("Main #A.1: expected THREAD_NOT_SUSPENDED instead of: " + errCode);
            } else {
                log("Main #A.1: got expected THREAD_NOT_SUSPENDED");
            }

            log("\nMain #A.2: suspended");
            suspendThread(testTaskThread);
            errCode = forceEarlyReturnI(testTaskThread, expValA2);
            if (errCode != JVMTI_ERROR_NONE) {
                throwFailed("Main #A.2: expected JVMTI_ERROR_NONE instead of: " + errCode);
            } else {
                log("Main #A.2: got expected JVMTI_ERROR_NONE");
            }
            resumeThread(testTaskThread);
            TestTask.clearDoLoop();
            TestTask.sleep(5);
        }

        log("\nMain #B: method B() must be blocked in a breakpoint event handler");
        {
            ensureAtBreakpoint();

            log("\nMain #B.1: unsuspended");
            errCode = forceEarlyReturnO(testTaskThread, expValB1);
            if (errCode != THREAD_NOT_SUSPENDED) {
                throwFailed("Main #B.1: expected THREAD_NOT_SUSPENDED instead of: " + errCode);
            }
            log("Main #B.1: got expected THREAD_NOT_SUSPENDED");

            log("\nMain #B.2: suspended");
            suspendThread(testTaskThread);
            errCode = forceEarlyReturnO(testTaskThread, expValB2);
            if (errCode != JVMTI_ERROR_NONE) {
                throwFailed("Main #B.2: expected JVMTI_ERROR_NONE");
            }
            log("Main #B.2: got expected JVMTI_ERROR_NONE");
            resumeThread(testTaskThread);
            notifyAtBreakpoint();
            TestTask.sleep(5);

            log("\nMain #B.3: unsuspended, call ForceEarlyReturn on own thread");
            ensureAtBreakpoint();
            notifyAtBreakpoint();
            TestTask.sleep(5);
        }

        log("\nMain #C: method C() calls ForceEarlyReturn on its own thread");
        {
            // ForceEarlyReturn is called from the test task (own thread) and expected to succeed.
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
        static void log(String str) { System.out.println(str); }

        static volatile boolean doLoop = true;
        static volatile boolean atPointA = false;
        static volatile boolean finished = false;

        static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interruption in TestTask.sleep: \n\t" + e);
            }
        }

        // Ensure thread is ready.
        static void ensureAtPointA() {
            while (!atPointA) {
                sleep(1);
            }
        }

        // Ensure thread is finished.
        static void ensureFinished() {
            while (!finished) {
                sleep(1);
            }
        }

        static void clearDoLoop() {
            doLoop = false;
        }

        public void run() {
            log("TestTask.run: started");

            int valA2 = A();
            if (valA2 != expValA2) {
                setFailed("TestTask.A: expValA2: " + expValA2 + "got: " + valA2);
            }
            sleep(1); // to cause yield

            String valB2 = B(false, expValB2); // false: do not force early return in breakpoint
            if (!valB2.equals(expValB2)) {
                setFailed("TestTask.B.2: expValB2: " + expValB2 + "got: " + valB2);
            }
            sleep(1); // to cause yield

            String valB3 = B(true, expValB3); // true: force early return in breakpoint
            if (!valB3.equals(expValB3)) {
                setFailed("TestTask.B.3: expected valB3: " + expValB3 + "got: " + valB3);
            }
            sleep(1); // to cause yield

            C();
            finished = true;
        }

        // Method is busy in a while loop.
        // ForceEarlyReturn is used two times:
        //  - when not suspended: THREAD_NOT_SUSPENDED is expected
        //  - when suspended: JVMTI_ERROR_NONE is expected
        static int A() {
            log("TestTask.A: started");
            atPointA = true;
            while (doLoop) {
            }
            log("TestTask.A: finished");
            return 0;
        }

        // A breakpoint is set at start of this method.
        // ForceEarlyReturn is used two times:
        //  - when not suspended: THREAD_NOT_SUSPENDED is expected
        //  - when suspended: expected to succeed
        static String B(boolean forceRet, String retObj) {
            log("TestTask.B: started");
            return "00";
        }

        // This method uses ForceEarlyReturn on its own thread. It is expected to return OPAQUE_FRAME.
        static void C() {
            log("TestTask.C: started");
            int errCode = ForceEarlyReturnTest.forceEarlyReturnV(Thread.currentThread());
            if (errCode == OPAQUE_FRAME) {
                log("TestTask.C: got expected OPAQUE_FRAME");
            } else {
                setFailed("TestTask.C: expected OPAQUE_FRAME from ForceEarlyReturn instead of: " + errCode);
            }
        }
    }
}
