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

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Smoke test for the RequestStackTrace JVMTI extension - drives
 *          many requests from a known method and verifies that emitted
 *          StackTraceRequest events carry the expected userData, thread
 *          and stack frame.
 * @requires vm.hasJFR & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceBasic
 */
public class TestRequestStackTraceBasic {

    private static final long  USER_DATA   = 0xCAFEL;
    private static final int   CALLS       = 200;
    private static final int   MIN_EVENTS  = 60;
    private static final String EVENT_NAME = "jdk.StackTraceRequest";

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("throttle", "100000/s");
            r.start();

            issueRequests();

            r.stop();

            List<RecordedEvent> events = Events.fromRecording(r).stream()
                    .filter(e -> e.getEventType().getName().equals(EVENT_NAME))
                    .toList();

            String selfName = Thread.currentThread().getName();
            int succeeded = 0;
            int failed = 0;
            for (RecordedEvent e : events) {
                // userData and eventThread are populated on both the success
                // and failure paths.
                Asserts.assertEquals(USER_DATA, e.getLong("userData"),
                        "userData mismatch in event " + e);
                Asserts.assertEquals(selfName, e.getThread().getJavaName(),
                        "eventThread mismatch in event " + e);
                if (e.getBoolean("failed")) {
                    failed++;
                } else {
                    succeeded++;
                    // Stack trace is only populated on the success path.
                    Asserts.assertTrue(stackContainsIssueRequests(e),
                            "expected stack trace to contain issueRequests, got " + e);
                }
            }

            System.out.println("Issued " + CALLS + " requests, observed "
                    + events.size() + " events (" + succeeded + " ok, "
                    + failed + " failed)");

            // Failures can happen for reasons outside the test's control
            // (thread state at sample time, signal-handler timing on certain
            // paths, etc.). Threshold on the count of successful events;
            // failed events are allowed but do not count toward it.
            Asserts.assertGreaterThanOrEqual(succeeded, MIN_EVENTS,
                    "expected at least " + MIN_EVENTS
                            + " successful StackTraceRequest events");
        }
    }

    // Kept on a separate frame so the assertion can verify that the
    // emitted stack trace covers the call site.
    private static void issueRequests() {
        for (int i = 0; i < CALLS; i++) {
            int rc = RequestStackTraceHelper.requestStackTrace(USER_DATA);
            Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NONE, rc,
                    "unexpected JVMTI error from requestStackTrace: " + rc);
        }
    }

    private static boolean stackContainsIssueRequests(RecordedEvent e) {
        if (e.getStackTrace() == null) {
            return false;
        }
        for (RecordedFrame f : e.getStackTrace().getFrames()) {
            if (f.getMethod() == null) {
                continue;
            }
            String type = f.getMethod().getType().getName();
            String method = f.getMethod().getName();
            if ("jdk.jfr.jvmti.TestRequestStackTraceBasic".equals(type)
                    && "issueRequests".equals(method)) {
                return true;
            }
        }
        return false;
    }
}
