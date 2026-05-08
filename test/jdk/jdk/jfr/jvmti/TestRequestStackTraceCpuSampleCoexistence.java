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
 * @summary Regression test that StackTraceRequest and CPUTimeSample can be
 *          enabled together, and that disabling StackTraceRequest does not
 *          dis-enroll the native stack-walker thread that CPUTimeSample
 *          also depends on.
 * @requires vm.hasJFR & os.family == "linux"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceCpuSampleCoexistence
 */
public class TestRequestStackTraceCpuSampleCoexistence {

    private static final String STR_EVENT = "jdk.StackTraceRequest";
    private static final String CPU_EVENT = "jdk.CPUTimeSample";

    private static final int    REQUEST_COUNT       = 200;
    private static final int    MIN_STR_EVENTS      = 50;
    private static final int    MIN_CPU_EVENTS      = 5;
    private static final long   BUSY_DURATION_NANOS = 2_000_000_000L; // 2 s
    private static final long   USER_DATA           = 0xC0FFEEL;

    public static void main(String[] args) throws Exception {
        // Phase 1: both events enabled together.
        try (Recording r = new Recording()) {
            r.enable(STR_EVENT).with("throttle", "100000/s");
            r.enable(CPU_EVENT).with("throttle", "1000/s");
            r.start();

            burnAndRequest();

            r.stop();

            List<RecordedEvent> all = Events.fromRecording(r);
            long strCount = all.stream()
                    .filter(e -> e.getEventType().getName().equals(STR_EVENT))
                    .count();
            long cpuCount = all.stream()
                    .filter(e -> e.getEventType().getName().equals(CPU_EVENT))
                    .count();

            System.out.println("Phase 1: StackTraceRequest=" + strCount
                    + " CPUTimeSample=" + cpuCount);

            Asserts.assertGreaterThanOrEqual((int) strCount, MIN_STR_EVENTS,
                    "phase 1: expected >= " + MIN_STR_EVENTS
                            + " StackTraceRequest events");
            Asserts.assertGreaterThanOrEqual((int) cpuCount, MIN_CPU_EVENTS,
                    "phase 1: expected >= " + MIN_CPU_EVENTS
                            + " CPUTimeSample events");
        }

        // Phase 2: only CPUTimeSample. The walker thread the CPU sampler
        // shares with StackTraceRequest must still be enrolled here. The
        // bug this regression-tests dis-enrolled the walker on phase 1's
        // stop, causing this phase to produce no CPU samples for in-native
        // threads.
        try (Recording r = new Recording()) {
            r.enable(CPU_EVENT).with("throttle", "1000/s");
            r.start();

            burn();

            r.stop();

            long cpuCount = Events.fromRecording(r).stream()
                    .filter(e -> e.getEventType().getName().equals(CPU_EVENT))
                    .count();

            System.out.println("Phase 2: CPUTimeSample=" + cpuCount);
            Asserts.assertGreaterThanOrEqual((int) cpuCount, MIN_CPU_EVENTS,
                    "phase 2: expected >= " + MIN_CPU_EVENTS
                            + " CPUTimeSample events after second recording");
        }
    }

    private static void burnAndRequest() {
        long deadline = System.nanoTime() + BUSY_DURATION_NANOS;
        int requestsIssued = 0;
        double i = 0;
        while (System.nanoTime() < deadline) {
            for (int j = 0; j < 100_000; j++) {
                i = Math.sqrt(i * Math.pow(Math.sqrt(Math.random()), Math.random()));
            }
            if (requestsIssued < REQUEST_COUNT) {
                int rc = RequestStackTraceHelper.requestStackTrace(USER_DATA);
                Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NONE, rc,
                        "unexpected JVMTI error: " + rc);
                requestsIssued++;
            }
        }
    }

    private static void burn() {
        long deadline = System.nanoTime() + BUSY_DURATION_NANOS;
        double i = 0;
        while (System.nanoTime() < deadline) {
            for (int j = 0; j < 100_000; j++) {
                i = Math.sqrt(i * Math.pow(Math.sqrt(Math.random()), Math.random()));
            }
        }
    }
}
