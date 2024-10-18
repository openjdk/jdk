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
import java.time.Instant;
import java.util.List;
import java.util.Comparator;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
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
        testThrottleSettingsPeriod();
    }

    private static void testZeroPerSecond() throws Exception {
        Asserts.assertEquals(0, countEvents(1000, "0/s").count());
    }

    private static void testThrottleSettings() throws Exception {
        int count = countEvents(1000,
            Runtime.getRuntime().availableProcessors() * 2 + "/s").count();
        Asserts.assertTrue(count > 0 && count < 3,
            "Expected between 0 and 3 events, got " + count);
    }

    private static void testThrottleSettingsPeriod() throws Exception {
        float rate = countEvents(1000, "1ms").rate();
        Asserts.assertTrue(rate > 950 && rate < 1050, "Expected around 1000 events per second, got " + rate);
    }

    private record EventCount(int count, int timeMs) {
        float rate() {
            return (float) count / timeMs * 1000;
        }
    }

    private static EventCount countEvents(int timeMs, String rate) throws Exception {
        Recording recording = new Recording();
        recording.enable(EventNames.CPUTimeSample)
                 .with("throttle", rate);

        recording.start();

        wasteCPU(timeMs);

        recording.stop();

        List<RecordedEvent> events = Events.fromRecording(recording).stream()
                .filter(e -> e.getThread().getJavaName()
                              .equals(Thread.currentThread().getName()))
                .sorted(Comparator.comparing(RecordedEvent::getStartTime))
                .toList();
        if (events.size() < 2) {
            return new EventCount(events.size(), 0);
        }

        Instant start = events.get(0).getStartTime();
        Instant end = events.get(events.size() - 1).getStartTime();
        return new EventCount(events.size(), (int) Duration.between(start, end).toMillis());
    }

    private static void wasteCPU(int durationMs) {
        long start = System.currentTimeMillis();
        double i = 0;
        while (System.currentTimeMillis() - start < durationMs) {
            i = i * Math.pow(Math.random(), Math.random());
        }
    }

}
