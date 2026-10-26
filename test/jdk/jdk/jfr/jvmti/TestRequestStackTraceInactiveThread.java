/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

package jdk.jfr.jvmti;

import jdk.jfr.Recording;

import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Verify that RequestStackTrace returns THREAD_NOT_ALIVE for
 *          threads that have not been started or have already terminated.
 * @requires vm.hasJFR & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceInactiveThread
 */
public class TestRequestStackTraceInactiveThread {

    private static final String EVENT_NAME = "jdk.StackTraceRequest";

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("throttle", "100/s");
            r.start();

            testUnstartedThread();
            testTerminatedThread();

            r.stop();
        }
    }

    private static void testUnstartedThread() {
        Thread t = new Thread(() -> {}, "unstarted-thread");
        // No t.start().
        int rc = RequestStackTraceHelper.requestStackTraceWithThread(t, 0L);
        Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_THREAD_NOT_ALIVE, rc,
                "expected THREAD_NOT_ALIVE for unstarted thread, got " + rc);
    }

    private static void testTerminatedThread() throws InterruptedException {
        Thread t = new Thread(() -> {}, "terminated-thread");
        t.start();
        t.join();
        // t is now terminated; the JavaThread* back-pointer is null.
        int rc = RequestStackTraceHelper.requestStackTraceWithThread(t, 0L);
        Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_THREAD_NOT_ALIVE, rc,
                "expected THREAD_NOT_ALIVE for terminated thread, got " + rc);
    }
}
