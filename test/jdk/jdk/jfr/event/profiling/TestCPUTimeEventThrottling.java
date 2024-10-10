/*
 * Copyright (c) 2024, SAP SE. All rights reserved.
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

package jdk.jfr.event.profiling;


import java.time.Duration;

import jdk.jfr.Recording;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR & os.family == "linux"
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.profiling.TestCPUTimeEventThrottling
 */
public class TestCPUTimeEventThrottling {

    public static void main(String[] args) throws Exception {
        testZeroPerSecond();
        testThrottleSettings();
        testThrottleSettingsOff();
    }

    private static void testZeroPerSecond() throws Exception {
        Asserts.assertEquals(0, countEvents(1000, "0/s"));
    }

    private static void testThrottleSettings() throws Exception {
        int count = countEvents(1000,
            Runtime.getRuntime().availableProcessors() * 2 + "/s");
        Asserts.assertTrue(count > 0 && count < 3,
            "Expected between 0 and 3 events, got " + count);
    }

    private static void testThrottleSettingsOff() throws Exception {
        int count = countEvents(1000, "");
        Asserts.assertTrue(count > 0, "Expected events, got " + count);
    }


    private static int countEvents(int timeMs, String throttle) throws Exception {
        Recording recording = new Recording();
        recording.enable(EventNames.CPUTimeSample)
            .withPeriod(Duration.ofMillis(100))
            .with("throttle", throttle);

        recording.start();

        wasteCPU(timeMs);

        recording.stop();

        return Events.fromRecording(recording).size();
    }

    private static void wasteCPU(int durationMs) {
        long start = System.currentTimeMillis();
        double i = 0;
        while (System.currentTimeMillis() - start < durationMs) {
            i = i * Math.pow(Math.random(), Math.random());
        }
    }

}
