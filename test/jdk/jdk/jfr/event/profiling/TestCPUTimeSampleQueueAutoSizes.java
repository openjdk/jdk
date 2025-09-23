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
import java.time.Instant;
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
 *
 * The test starts CPU time sampling with a short interval (1ms), disabling
 * out-of-stack sample processing for the duration of the test.
 * It now runs in native for one second, to cause queue overflows,
 * then it comes back into Java to trigger the queue walking.
 * Repeats the cycle 5 times and verifies that the loss decreases from the first
 * to the last iteration.
 * @test
 * @requires vm.hasJFR & os.family == "linux" & vm.debug & vm.flagless
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @build  jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:StartFlightRecording:dumponexit=true jdk.jfr.event.profiling.TestCPUTimeSampleQueueAutoSizes
 */
public class TestCPUTimeSampleQueueAutoSizes {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    record LossEvent(long relativeTimeMillis, long lostSamples) {}

    /** A data collection from the CPUTimeSampleLost events for the burst thread */
    static class LossEventCollection {
        private final List<LossEvent> events = new ArrayList<>();
        private final List<Long> sampleEventsInTimeBox = new ArrayList<>();
        private final List<Long> timeBoxEnds = new ArrayList<>();

        public synchronized void addEvent(LossEvent event) {
            events.add(event);
        }

        public synchronized List<LossEvent> getSortedEvents() {
            return events.stream()
                         .sorted(Comparator.comparingLong(e -> e.relativeTimeMillis))
                         .collect(Collectors.toList());
        }

        public synchronized List<LossEvent> getEventsPerTimeBox() {
            List<LossEvent> ret = new ArrayList<>();
            AtomicLong previousEnd = new AtomicLong(0);
            for (Long timeBoxEnd : timeBoxEnds) {
                long lostSamples = events.stream()
                                          .filter(e -> e.relativeTimeMillis >= previousEnd.get() && e.relativeTimeMillis <= timeBoxEnd)
                                          .mapToLong(e -> e.lostSamples)
                                          .sum();
                ret.add(new LossEvent(previousEnd.get(), lostSamples));
                previousEnd.set(timeBoxEnd);
            }
            return ret;
        }

        public synchronized void addTimeBoxEnd(long timeBoxEnd, long sampleEvents) {
            timeBoxEnds.add(timeBoxEnd);
            sampleEventsInTimeBox.add(sampleEvents);
        }

        public synchronized void print() {
            System.out.println("Loss event information:");
            for (int i = 0; i < timeBoxEnds.size(); i++) {
                System.out.println("  Time box end: " + timeBoxEnds.get(i) + ", sample events: " + sampleEventsInTimeBox.get(i));
            }
            for (LossEvent e : events) {
                System.out.println("  Lost samples event: " + e.lostSamples + " at " + e.relativeTimeMillis);
            }
            for (LossEvent e : getEventsPerTimeBox()) {
                System.out.println("  Lost samples in time box ending at " + e.relativeTimeMillis + ": " + e.lostSamples);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (RecordingStream rs = new RecordingStream()) {
            // setup recording
            long burstThreadId = Thread.currentThread().threadId();
            final long startTimeMillis = Instant.now().toEpochMilli();
            LossEventCollection lossEvents = new LossEventCollection();
            AtomicLong sampleEventCountInTimeBox = new AtomicLong(0);
            rs.enable(EventNames.CPUTimeSample).with("throttle", "1ms");
            rs.enable(EventNames.CPUTimeSamplesLost);
            rs.onEvent(EventNames.CPUTimeSamplesLost, e -> {
                if (e.getThread("eventThread").getJavaThreadId() == burstThreadId) {
                    long eventTime = e.getStartTime().toEpochMilli();
                    long relativeTime = eventTime - startTimeMillis;
                    System.out.println("Lost samples: " + e.getLong("lostSamples") + " at " + relativeTime + " start time " + startTimeMillis);
                    lossEvents.addEvent(new LossEvent(relativeTime, e.getLong("lostSamples")));
                }
            });
            rs.onEvent(EventNames.CPUTimeSample, e -> {
                if (e.getThread("eventThread").getJavaThreadId() == burstThreadId) {
                    sampleEventCountInTimeBox.incrementAndGet();
                }
            });
            rs.startAsync();
            // we disable the out-of-stack walking so that the queue fills up and overflows
            // while we are in native code
            disableOutOfStackWalking();


            for (int i = 0; i < 5; i++) {
                // run in native for one second
                WHITE_BOX.busyWaitCPUTime(1000);
                // going out-of-native at the end of the previous call should have triggered
                // the safepoint handler, thereby also triggering the stack walking and creation
                // of the loss event
                WHITE_BOX.forceSafepoint(); // just to be sure
                lossEvents.addTimeBoxEnd(Instant.now().toEpochMilli() - startTimeMillis, sampleEventCountInTimeBox.get());
                sampleEventCountInTimeBox.set(0);
            }

            rs.stop();
            rs.close();

            enableOutOfStackWalking();

            checkThatLossDecreased(lossEvents);
        }
    }

    static void disableOutOfStackWalking() {
        Asserts.assertTrue(WHITE_BOX.cpuSamplerSetOutOfStackWalking(false), "Out-of-stack-walking not supported");
    }

    static void enableOutOfStackWalking() {
        WHITE_BOX.cpuSamplerSetOutOfStackWalking(true);
    }

    static void checkThatLossDecreased(LossEventCollection lossEvents) {
        lossEvents.print();
        List<LossEvent> timeBoxedLosses = lossEvents.getEventsPerTimeBox();
        // check that the last time box has far fewer lost samples than the first
        Asserts.assertTrue(timeBoxedLosses.get(timeBoxedLosses.size() - 1).lostSamples <=
                           timeBoxedLosses.get(0).lostSamples / 2);
    }
}
