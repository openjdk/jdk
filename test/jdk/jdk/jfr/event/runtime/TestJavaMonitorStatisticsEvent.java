/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.runtime;

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.thread.TestThread;
import jdk.test.lib.thread.XRun;

/**
 * @test TestJavaMonitorStatisticsEvent
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.runtime.TestJavaMonitorStatisticsEvent
 */
public class TestJavaMonitorStatisticsEvent {

    private static final String FIELD_COUNT = "count";

    private static final String EVENT_NAME = EventNames.JavaMonitorStatistics;
    private static final int NUM_LOCKS = 512;

    static class Lock {
    }

    static final Lock[] LOCKS = new Lock[NUM_LOCKS];

    static void lockNext(int idx, Runnable action) throws InterruptedException {
        if (idx >= NUM_LOCKS) {
            action.run();
            return;
        }
        synchronized (LOCKS[idx]) {
            LOCKS[idx].wait(1);
            lockNext(idx + 1, action);
        }
    }

    public static void main(String[] args) throws Exception {
        List<RecordedEvent> events = new CopyOnWriteArrayList<>();
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(EVENT_NAME).with("period", "everyChunk");
            rs.onEvent(EVENT_NAME, e -> events.add(e));
            rs.startAsync();

            // Recursively lock all, causing NUM_LOCKS monitors to exist.
            // Stop the recording when holding all the locks, so that we
            // get at least one event with NUM_LOCKS max.
            for (int c = 0; c < NUM_LOCKS; c++) {
                LOCKS[c] = new Lock();
            }
            lockNext(0, () -> rs.stop());

            System.out.println(events);
            assertFalse(events.isEmpty());

            long globalCount = Long.MIN_VALUE;
            for (RecordedEvent ev : events) {
                long evCount = Events.assertField(ev, FIELD_COUNT).getValue();
                assertTrue(evCount >= 0, "Count should be non-negative: " + evCount);
                globalCount = Math.max(globalCount, evCount);
            }

            assertTrue(globalCount >= NUM_LOCKS, "Global count should be at least " + NUM_LOCKS + ": " + globalCount);
        }
    }
}
