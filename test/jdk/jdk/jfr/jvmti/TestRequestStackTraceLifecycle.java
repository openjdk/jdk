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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Toggle the StackTraceRequest event on/off mid-recording several
 *          times. During each enabled window events should flow; during
 *          each disabled window calls return NOT_AVAILABLE and zero events
 *          are emitted with that window's userData prefix.
 * @requires vm.hasJFR & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceLifecycle
 */
public class TestRequestStackTraceLifecycle {

    private static final String EVENT_NAME = "jdk.StackTraceRequest";

    private static final int CYCLES               = 4;
    private static final int CALLS_PER_WINDOW     = 100;
    private static final int MIN_EVENTS_PER_ENABLED_WINDOW = 30;

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            // Start with event disabled.
            r.disable(EVENT_NAME);
            r.start();

            // Each window's userData carries (cycle << 32) | (1 if enabled else 0).
            // This lets us bucket events by the window they came from.
            for (int cycle = 1; cycle <= CYCLES; cycle++) {
                long disabledTag = ((long) cycle << 32) | 0L;
                long enabledTag  = ((long) cycle << 32) | 1L;

                runDisabledWindow(disabledTag);
                r.enable(EVENT_NAME).with("throttle", "100000/s");
                runEnabledWindow(enabledTag);
                r.disable(EVENT_NAME);
            }

            r.stop();

            List<RecordedEvent> events = Events.fromRecording(r).stream()
                    .filter(e -> e.getEventType().getName().equals(EVENT_NAME))
                    .toList();

            // Bucket events by userData.
            Map<Long, Integer> counts = new HashMap<>();
            for (RecordedEvent e : events) {
                counts.merge(e.getLong("userData"), 1, Integer::sum);
            }

            for (int cycle = 1; cycle <= CYCLES; cycle++) {
                long disabledTag = ((long) cycle << 32) | 0L;
                long enabledTag  = ((long) cycle << 32) | 1L;

                int disabledCount = counts.getOrDefault(disabledTag, 0);
                int enabledCount  = counts.getOrDefault(enabledTag, 0);

                System.out.println("cycle " + cycle + ": disabled=" + disabledCount
                        + " enabled=" + enabledCount);

                Asserts.assertEquals(0, disabledCount,
                        "expected zero events for disabled window in cycle " + cycle);
                Asserts.assertGreaterThanOrEqual(enabledCount,
                        MIN_EVENTS_PER_ENABLED_WINDOW,
                        "expected >= " + MIN_EVENTS_PER_ENABLED_WINDOW
                                + " events for enabled window in cycle " + cycle);
            }
        }
    }

    private static void runDisabledWindow(long tag) {
        for (int i = 0; i < CALLS_PER_WINDOW; i++) {
            int rc = RequestStackTraceHelper.requestStackTrace(tag);
            Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NOT_AVAILABLE, rc,
                    "expected NOT_AVAILABLE while event is disabled, got " + rc);
        }
    }

    private static void runEnabledWindow(long tag) {
        for (int i = 0; i < CALLS_PER_WINDOW; i++) {
            int rc = RequestStackTraceHelper.requestStackTrace(tag);
            Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NONE, rc,
                    "expected NONE while event is enabled, got " + rc);
        }
    }
}
