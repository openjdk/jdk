/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test verifies that selfsuspend doesn' block unmount by VTMTDisabler
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} SelfSuspendDisablerTest.java
 * @run main/othervm/native --enable-preview -agentlib:SelfSuspendDisablerTest SelfSuspendDisablerTest
 */

public class SelfSuspendDisablerTest {

    static {
        System.loadLibrary("SelfSuspendDisablerTest");
    }

    // Tested JVM TI thread states
    static final int NEW        = 0;        // No bits are set
    static final int TERMINATED = 2;        // JVMTI_THREAD_STATE_TERMINATED
    static final int RUNNABLE   = 5;        // JVMTI_THREAD_STATE_ALIVE & JVMTI_THREAD_STATE_RUNNABLE
    static final int SUSPENDED  = 0x100005; // RUNNABLE & JVMTI_THREAD_STATE_SUSPENDED

    native static boolean isSuspended(Thread thread);
    native static void selfSuspend();
    native static void resume(Thread thread);
    native static void suspendAllVirtualThreads();
    native static void resumeAllVirtualThreads();
    native static int getThreadState(Thread thread);

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            // ignore InterruptedException
        }
    }

    private static void testJvmtiThreadState(Thread thread, int expectedState) {
        String kindStr = thread.isVirtual()? "virtual " : "platform";
        int state = getThreadState(thread);

        System.out.printf("Expected %s thread state: %06X got: %06X\n",
                          kindStr, expectedState, state);
        if (state != expectedState) {
            throw new RuntimeException("Test FAILED: Unexpected thread state");
        }
    }

    public static void main(String argv[]) throws Exception {
        Thread t1 = Thread.ofPlatform().factory().newThread(() -> {
            testJvmtiThreadState(Thread.currentThread(), RUNNABLE);
            selfSuspend();
        });
        Thread t2 = Thread.ofVirtual().factory().newThread(() -> {
            testJvmtiThreadState(Thread.currentThread(), RUNNABLE);
            while(!isSuspended(t1)) {
              Thread.yield();
            }
            Thread.yield(); // provoke unmount

            testJvmtiThreadState(t1, SUSPENDED);

            resume(t1);

            suspendAllVirtualThreads();
        });

        testJvmtiThreadState(t1, NEW);
        testJvmtiThreadState(t2, NEW);

        t1.start();
        t2.start();

        while(!isSuspended(t2)) {
            sleep(100);
        }

        testJvmtiThreadState(t2, SUSPENDED);

        resumeAllVirtualThreads();

        t2.join();
        t1.join();

        testJvmtiThreadState(t1, TERMINATED);
        testJvmtiThreadState(t2, TERMINATED);
    }

}
