/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.startupargs;

import java.time.Duration;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.CommonHelper;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @summary Start a recording with duration. Verify recording stops.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main jdk.jfr.startupargs.TestStartDuration
 */
public class TestStartDuration {
    public static final String RECORDING_NAME = "TestStartDuration";
    public static final String WAIT_FOR_RUNNING = "wait-for-running";
    public static final String WAIT_FOR_CLOSED = "wait-for-closed";

    public static class TestValues {
        public static void main(String[] args) throws Exception {
            String action = args[0];
            Duration duration = Duration.parse(args[1]);
            if (action.equals(WAIT_FOR_RUNNING)) {
                Recording r = StartupHelper.getRecording("TestStartDuration");
                Asserts.assertEquals(r.getDuration(), duration);
                CommonHelper.waitForRecordingState(r, RecordingState.RUNNING);
                return;
            }
            if (action.equals(WAIT_FOR_CLOSED)) {
                while (!FlightRecorder.getFlightRecorder().getRecordings().isEmpty()) {
                    Thread.sleep(200);
                    System.out.println("A recording still running");
                }
                return;
            }
            System.out.println("Unknown action: " + action);
            System.exit(1);
        }
    }

    private static void testDurationInRange(String durationText, Duration duration, String action) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:StartFlightRecording:name=" + RECORDING_NAME + ",duration=" + durationText,
            TestValues.class.getName(),
            action,
            duration.toString());
        OutputAnalyzer out = ProcessTools.executeProcess(pb);

        out.shouldHaveExitValue(0);
    }


    private static void testDurationJavaVersion(String duration, boolean inRange) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
            "-XX:StartFlightRecording:name=TestStartDuration,duration=" + duration, "-version");
        OutputAnalyzer out = ProcessTools.executeProcess(pb);

        if (inRange) {
            out.shouldHaveExitValue(0);
        } else {
            out.shouldContain("Could not start recording, duration must be at least 1 second.");
            out.shouldHaveExitValue(1);
        }
    }

    private static void testDurationInRangeAccept(String duration) throws Exception {
        testDurationJavaVersion(duration, true);
    }

    private static void testDurationOutOfRange(String duration) throws Exception {
        testDurationJavaVersion(duration, false);
    }

    public static void main(String[] args) throws Exception {
        testDurationInRange("1s", Duration.ofSeconds(1), WAIT_FOR_CLOSED);
        testDurationInRange("1234003005ns", Duration.ofNanos(1234003005L), WAIT_FOR_CLOSED);
        testDurationInRange("1034ms", Duration.ofMillis(1034), WAIT_FOR_CLOSED);
        testDurationInRange("3500s", Duration.ofSeconds(3500), WAIT_FOR_RUNNING);
        testDurationInRange("59m", Duration.ofMinutes(59), WAIT_FOR_RUNNING);
        testDurationInRange("65h", Duration.ofHours(65), WAIT_FOR_RUNNING);
        testDurationInRange("354d", Duration.ofDays(354), WAIT_FOR_RUNNING);

        // additional test for corner values, verify that JVM accepts following durations
        testDurationInRangeAccept("1000000000ns");
        testDurationInRangeAccept("1000ms");
        testDurationInRangeAccept("1m");
        testDurationInRangeAccept("1h");
        testDurationInRangeAccept("1d");

        // out-of-range durations
        testDurationOutOfRange("0s");
        testDurationOutOfRange("999ms");
        testDurationOutOfRange("999999999ns");
        testDurationOutOfRange("0m");
        testDurationOutOfRange("0h");
        testDurationOutOfRange("0d");
    }
}
