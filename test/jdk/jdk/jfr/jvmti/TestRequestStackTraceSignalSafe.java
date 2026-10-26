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

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Drive RequestStackTrace from a real POSIX signal handler with
 *          a captured ucontext. Verifies the API is callable from a
 *          signal-handler context (no crash, no asserts) and that events
 *          flow with the right userData.
 *
 *          Note: this test self-targets SIGUSR1 via pthread_kill, so the
 *          captured PC is in C/libc code; samples are expected to be
 *          biased. Verifying biased=false would require a separate
 *          sender thread targeting a worker doing Java work; left as a
 *          follow-up.
 * @requires vm.hasJFR & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceSignalSafe
 */
public class TestRequestStackTraceSignalSafe {

    private static final String EVENT_NAME = "jdk.StackTraceRequest";

    private static final long USER_DATA  = 0xBEEFL;
    private static final int  CALLS      = 500;
    private static final int  MIN_EVENTS = 100;

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("throttle", "100000/s");
            r.start();

            for (int i = 0; i < CALLS; i++) {
                int rc = RequestStackTraceHelper
                        .requestStackTraceFromSignalHandler(USER_DATA);
                Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NONE, rc,
                        "unexpected JVMTI error from signal handler: " + rc);
            }

            r.stop();

            List<RecordedEvent> events = Events.fromRecording(r).stream()
                    .filter(e -> e.getEventType().getName().equals(EVENT_NAME))
                    .filter(e -> e.getLong("userData") == USER_DATA)
                    .toList();

            System.out.println("Issued " + CALLS
                    + " signal-handler requests, observed "
                    + events.size() + " events");

            Asserts.assertGreaterThanOrEqual(events.size(), MIN_EVENTS,
                    "expected at least " + MIN_EVENTS
                            + " events from signal-handler path");
        }
    }
}
