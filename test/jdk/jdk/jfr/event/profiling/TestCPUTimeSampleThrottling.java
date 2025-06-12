/*
 * Copyright (c) 2025 SAP SE. All rights reserved.
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
import java.lang.management.ManagementFactory;
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
 * @requires vm.hasJFR & os.family == "linux"
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.profiling.TestCPUTimeSampleThrottling
 */
public class TestCPUTimeSampleThrottling {

    public static void main(String[] args) throws Exception {
        testZeroPerSecond();
        testThrottleSettings();
        testThrottleSettingsPeriod();
    }

    private static void testZeroPerSecond() throws Exception {
        Asserts.assertTrue(0L == countEvents(1000, "0/s").count());
    }

    private static void testThrottleSettings() throws Exception {
        long count = countEvents(1000,
            Runtime.getRuntime().availableProcessors() * 2 + "/s").count();
        Asserts.assertTrue(count > 0 && count < 3,
            "Expected between 0 and 3 events, got " + count);
    }

    private static void testThrottleSettingsPeriod() throws Exception {
        float rate = countEvents(1000, "10ms").rate();
        Asserts.assertTrue(rate > 75 && rate < 110, "Expected around 100 events per second, got " + rate);
    }

    private record EventCount(long count, float cpuTime) {
        float rate() {
            return count / cpuTime;
        }
    }

    /**
     * Counting the events that are emitted for a given throttle in a given time.
     * <p>
     * The result is wall-clock independent; it only records the CPU-time and the number of
     * emitted events. The result, therefore, does not depend on the load of the machine.
     * And because failed events are counted too, the result is not affected by the thread
     * doing other in-JVM work (like garbage collection).
     */
    private static EventCount countEvents(int timeMs, String throttle) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable(EventNames.CPUTimeSample)
                    .with("throttle", throttle);

            var bean = ManagementFactory.getThreadMXBean();

            recording.start();

            long startThreadCpuTime = bean.getCurrentThreadCpuTime();

            wasteCPU(timeMs);

            long spendCPUTime = bean.getCurrentThreadCpuTime() - startThreadCpuTime;

            recording.stop();

            long eventCount = Events.fromRecording(recording).stream()
                    .filter(e -> e.getThread().getJavaName()
                                .equals(Thread.currentThread().getName()))
                    .count();

            return new EventCount(eventCount, spendCPUTime / 1_000_000_000f);
        }
    }

    private static void wasteCPU(int durationMs) {
        long start = System.currentTimeMillis();
        double i = 0;
        while (System.currentTimeMillis() - start < durationMs) {
            for (int j = 0; j < 100000; j++) {
                i = Math.sqrt(i * Math.pow(Math.sqrt(Math.random()), Math.random()));
            }
        }
    }

}
