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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Verify that userData round-trips bit-exactly into the
 *          StackTraceRequest event, including edge values.
 * @requires vm.hasJFR & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceUserData
 */
public class TestRequestStackTraceUserData {

    private static final String EVENT_NAME = "jdk.StackTraceRequest";

    private static final long[] VALUES = {
            0L,
            1L,
            -1L,
            42L,
            Long.MAX_VALUE,
            Long.MIN_VALUE,
            0x0123456789ABCDEFL,
            0xFEDCBA9876543210L
    };

    // 1000 calls, expect at least 30% to land.
    private static final int CALLS_PER_VALUE = 125;
    private static final int MIN_EVENTS      = 300;

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("throttle", "100000/s");
            r.start();

            for (int i = 0; i < CALLS_PER_VALUE; i++) {
                for (long value : VALUES) {
                    int rc = RequestStackTraceHelper.requestStackTrace(value);
                    Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NONE, rc,
                            "unexpected JVMTI error: " + rc);
                }
            }

            r.stop();

            List<RecordedEvent> events = Events.fromRecording(r).stream()
                    .filter(e -> e.getEventType().getName().equals(EVENT_NAME))
                    .toList();

            System.out.println("Issued " + (CALLS_PER_VALUE * VALUES.length)
                    + " requests, observed " + events.size() + " events");

            Asserts.assertGreaterThanOrEqual(events.size(), MIN_EVENTS,
                    "expected at least " + MIN_EVENTS + " events");

            Set<Long> issued = new HashSet<>();
            for (long v : VALUES) issued.add(v);

            Set<Long> observed = new HashSet<>();
            for (RecordedEvent e : events) {
                long u = e.getLong("userData");
                Asserts.assertTrue(issued.contains(u),
                        "unexpected userData in event: " + Long.toHexString(u));
                observed.add(u);
            }

            // Ensure every value we issued shows up at least once - protects
            // against a bug that drops only specific values.
            for (long v : VALUES) {
                Asserts.assertTrue(observed.contains(v),
                        "missing userData in observed events: 0x"
                                + Long.toHexString(v));
            }
        }
    }
}
