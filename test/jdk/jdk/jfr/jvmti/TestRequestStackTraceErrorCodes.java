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
import jdk.jfr.internal.JVM;

import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Verify that RequestStackTrace returns the documented JVMTI error
 *          codes for not-available conditions.
 * @requires vm.hasJFR & os.family != "windows"
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceErrorCodes
 */
public class TestRequestStackTraceErrorCodes {

    private static final String EVENT_NAME = "jdk.StackTraceRequest";

    public static void main(String[] args) throws Exception {
        // Order matters: "no recorder yet" is only observable before any
        // recording has been started in this VM.
        testNotAvailableNoRecording();
        testNotAvailableWhenEventDisabled();
        testNotAvailableForExcludedThread();
    }

    private static void testNotAvailableNoRecording() {
        int rc = RequestStackTraceHelper.requestStackTrace(0L);
        Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NOT_AVAILABLE, rc,
                "expected NOT_AVAILABLE before any recording is started, got " + rc);
    }

    private static void testNotAvailableWhenEventDisabled() throws Exception {
        try (Recording r = new Recording()) {
            // Do not enable jdk.StackTraceRequest.
            r.start();

            int rc = RequestStackTraceHelper.requestStackTrace(0L);
            Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NOT_AVAILABLE, rc,
                    "expected NOT_AVAILABLE when event is disabled, got " + rc);

            r.stop();
        }
    }

    private static void testNotAvailableForExcludedThread() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("throttle", "100/s");
            r.start();

            JVM.exclude(Thread.currentThread());
            try {
                int rc = RequestStackTraceHelper.requestStackTrace(0L);
                Asserts.assertEquals(
                        RequestStackTraceHelper.JVMTI_ERROR_NOT_AVAILABLE, rc,
                        "expected NOT_AVAILABLE for excluded thread, got " + rc);
            } finally {
                JVM.include(Thread.currentThread());
            }

            r.stop();
        }
    }
}
