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
 * @summary Passing Thread.currentThread() as the thread argument should
 *          behave identically to passing null - per the JEP, both
 *          designate the calling thread.
 * @requires vm.hasJFR & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceCurrentThreadAsArg
 */
public class TestRequestStackTraceCurrentThreadAsArg {

    private static final String EVENT_NAME = "jdk.StackTraceRequest";
    private static final long   USER_DATA  = 0xFEEDL;
    private static final int    CALLS      = 200;
    private static final int    MIN_EVENTS = 60;

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("throttle", "100000/s");
            r.start();

            issueRequests();

            r.stop();

            String selfName = Thread.currentThread().getName();
            List<RecordedEvent> events = Events.fromRecording(r).stream()
                    .filter(e -> e.getEventType().getName().equals(EVENT_NAME))
                    .toList();

            int succeeded = 0;
            int failed = 0;
            for (RecordedEvent e : events) {
                Asserts.assertEquals(USER_DATA, e.getLong("userData"),
                        "userData mismatch in event " + e);
                Asserts.assertEquals(selfName, e.getThread().getJavaName(),
                        "eventThread mismatch in event " + e);
                if (e.getBoolean("failed")) {
                    failed++;
                } else {
                    succeeded++;
                    Asserts.assertTrue(stackContainsIssueRequests(e),
                            "expected stack trace to contain issueRequests, got " + e);
                }
            }

            System.out.println("Issued " + CALLS + " requests, observed "
                    + events.size() + " events (" + succeeded + " ok, "
                    + failed + " failed)");

            Asserts.assertGreaterThanOrEqual(succeeded, MIN_EVENTS,
                    "expected at least " + MIN_EVENTS
                            + " successful StackTraceRequest events");
        }
    }

    private static void issueRequests() {
        Thread self = Thread.currentThread();
        for (int i = 0; i < CALLS; i++) {
            int rc = RequestStackTraceHelper.requestStackTraceWithThread(self, USER_DATA);
            Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NONE, rc,
                    "unexpected JVMTI error: " + rc);
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
            if ("jdk.jfr.jvmti.TestRequestStackTraceCurrentThreadAsArg".equals(type)
                    && "issueRequests".equals(method)) {
                return true;
            }
        }
        return false;
    }
}
