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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.JVM;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.whitebox.WhiteBox;


/*
 * Tests the sample queues increase in size as needed, when loss is recorded.
 * @test
 * @requires vm.hasJFR & os.family == "linux" & vm.debug
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @build  jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:StartFlightRecording:dumponexit=true jdk.jfr.event.profiling.TestCPUTimeSampleQueueAutoSizes
 */
public class TestCPUTimeSampleQueueAutoSizes {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static final String BURST_THREAD_NAME = "Burst-Thread-1";

    static volatile boolean alive = true;

    record LossEvent(long relativeTimeMillis, long lostSamples) {}

    /** A data collection from the CPUTimeSampleLost events for the burst thread */
    static class LossEventCollection {
        private final List<LossEvent> events = new ArrayList<>();

        public synchronized void addEvent(LossEvent event) {
            events.add(event);
        }

        public synchronized List<LossEvent> getSortedEvents() {
            return events.stream()
                         .sorted(Comparator.comparingLong(e -> e.relativeTimeMillis))
                         .collect(Collectors.toList());
        }

        public List<LossEvent> getEventsPerInterval(long widthMillis, long stopTimeMillis) {
            List<LossEvent> ret = new ArrayList<>();
            for (long start = 0; start < stopTimeMillis; start += widthMillis) {
                long actualStart = Math.min(start, stopTimeMillis - widthMillis);
                long lostSamples = events.stream()
                                          .filter(e -> e.relativeTimeMillis >= actualStart && e.relativeTimeMillis < actualStart + widthMillis)
                                          .mapToLong(e -> e.lostSamples)
                                          .sum();
                ret.add(new LossEvent(actualStart, lostSamples));
            }
            return ret;
        }

    }

    public static void main(String[] args) throws Exception {
        try (RecordingStream rs = new RecordingStream()) {
            // setup recording
            AtomicLong firstSampleTimeMillis = new AtomicLong(0);
            AtomicLong lastSampleTimeMillis = new AtomicLong(0);
            LossEventCollection lossEvents = new LossEventCollection();
            rs.enable(EventNames.CPUTimeSample).with("throttle", "1ms");
            rs.onEvent(EventNames.CPUTimeSample, e -> {
                if (firstSampleTimeMillis.get() == 0 && e.getThread("eventThread").getJavaName().equals(BURST_THREAD_NAME)) {
                    firstSampleTimeMillis.set(e.getStartTime().toEpochMilli());
                }
                if (e.getThread("eventThread").getJavaName().equals(BURST_THREAD_NAME)) {
                    lastSampleTimeMillis.set(e.getStartTime().toEpochMilli());
                }
            });
            rs.enable(EventNames.CPUTimeSamplesLost);
            rs.onEvent(EventNames.CPUTimeSamplesLost, e -> {
                if (e.getThread("eventThread").getJavaName().equals(BURST_THREAD_NAME)) {
                    long eventTime = e.getStartTime().toEpochMilli();
                    long relativeTime = firstSampleTimeMillis.get() > 0 ? (eventTime - firstSampleTimeMillis.get()) : eventTime;
                    System.out.println("Lost samples: " + e.getLong("lostSamples") + " at " + relativeTime);
                    lossEvents.addEvent(new LossEvent(relativeTime, e.getLong("lostSamples")));
                }
            });
            WHITE_BOX.cpuSamplerSetOutOfStackWalking(false);
            rs.startAsync();
            // this thread runs all along
            Thread burstThread = new Thread(() -> WHITE_BOX.busyWait(11000));
            burstThread.setName(BURST_THREAD_NAME);
            burstThread.start();
            // now we toggle out-of-stack-walking off, wait 1 second and then turn it on for 500ms a few times
            for (int i = 0; i < 5; i++) {
                boolean supported = WHITE_BOX.cpuSamplerSetOutOfStackWalking(false);
                if (!supported) {
                    System.out.println("Out-of-stack-walking not supported, skipping test");
                    Asserts.assertFalse(true);
                    return;
                }
                Thread.sleep(700);
                long iterations = WHITE_BOX.cpuSamplerOutOfStackWalkingIterations();
                WHITE_BOX.cpuSamplerSetOutOfStackWalking(true);
                Thread.sleep(300);
                while (WHITE_BOX.cpuSamplerOutOfStackWalkingIterations() == iterations) {
                    Thread.sleep(50); // just to make sure the stack walking really ran
                }
            }
            rs.close();
            checkThatLossDecreased(lossEvents, lastSampleTimeMillis.get() - firstSampleTimeMillis.get());
        }
    }

    static void checkThatLossDecreased(LossEventCollection lossEvents, long lastSampleTimeMillis) {
        List<LossEvent> intervalLosses = lossEvents.getEventsPerInterval(1000, lastSampleTimeMillis);
        for (LossEvent interval : intervalLosses) {
            System.out.println("Lost samples in interval " + interval.relativeTimeMillis + ": " + interval.lostSamples);
        }
        // check that there are at least 3 intervals
        Asserts.assertTrue(intervalLosses.size() > 2);
        // check that the second to last interval has far fewer lost samples than the first
        Asserts.assertTrue(intervalLosses.get(intervalLosses.size() - 2).lostSamples <
                           intervalLosses.get(0).lostSamples / 2);
    }
}
