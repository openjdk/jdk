/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.api.recording.event;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Test event period.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.recording.event.TestPeriod
 */
public class TestPeriod {
    private static final String EVENT_PATH = EventNames.ThreadAllocationStatistics;
    private static final long ERROR_MARGIN = 20; // 186 ms has been measured, when period was set to 200 ms

    public static void main(String[] args) throws Throwable {
        long[] periods = { 100, 200 };
        int eventCount = 4;
        int deltaCount;
        for (long period : periods) {
            List<Long> deltaBetweenEvents;
            do {
                deltaBetweenEvents = createPeriodicEvents(period, eventCount);
                deltaCount = deltaBetweenEvents.size();
                if (deltaCount < eventCount - 1) {
                    System.out.println("Didn't get sufficent number of events. Retrying...");
                    System.out.println();
                }
            } while (deltaCount < eventCount - 1);
            for (int i = 0; i < eventCount - 1; i++) {
                verifyDelta(deltaBetweenEvents.get(i), period);
            }
            System.out.println();
        }
    }

    private static List<Long> createPeriodicEvents(long period, int eventCount) throws Exception, IOException {
        System.out.println("Provoking events with period " + period + " ms");
        Recording r = new Recording();
        r.start();
        runWithPeriod(r, period, eventCount + 1);
        r.stop();

        long prevTime = -1;
        List<Long> deltas = new ArrayList<>();
        for (RecordedEvent event : Events.fromRecording(r)) {
            if (Events.isEventType(event, EVENT_PATH) && isMyThread(event)) {
                long timeMillis = event.getEndTime().toEpochMilli();
                if (prevTime != -1) {
                    long delta = timeMillis - prevTime;
                    deltas.add(delta);
                    System.out.printf("event: time=%d, delta=%d%n", timeMillis, delta);
                }
                prevTime = timeMillis;
            }
        }
        r.close();
        return deltas;
    }

    // We only check that time is at least as expected.
    // We ignore if time is much longer than expected, since anything can happen
    // during heavy load,
    private static void verifyDelta(long actual, long expected) {
        System.out.printf("verifyDelta: actaul=%d, expected=%d (errorMargin=%d)%n", actual, expected, ERROR_MARGIN);
        Asserts.assertGreaterThan(actual, expected - ERROR_MARGIN, "period delta too short");
    }

    private static boolean isMyThread(RecordedEvent event) {
        Object o = event.getValue("thread");
        if (o instanceof RecordedThread) {
            RecordedThread rt = (RecordedThread) o;
            return Thread.currentThread().getId() == rt.getJavaThreadId();
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static byte[] dummy = null;

    // Generate at least minEvents event with given period
    private static void runWithPeriod(Recording r, long period, int minEventCount) throws Exception {
        r.enable(EVENT_PATH).withPeriod(Duration.ofMillis(period));
        long endTime = System.currentTimeMillis() + period * minEventCount;
        while (System.currentTimeMillis() < endTime) {
            dummy = new byte[100];
            Thread.sleep(1);
        }
    }

}
