/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertGreaterThan;
import static jdk.test.lib.Asserts.assertLessThanOrEqual;

import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr
 *          jdk.management
 * @run main/othervm -Xms16m -Xmx128m -Xlog:gc jdk.jfr.event.runtime.TestResidentSetSizeEvent true
 */
public class TestResidentSetSizeEvent {
    private final static String ResidentSetSizeEvent = EventNames.ResidentSetSize;

    private final static int Period = 1000;
    private final static int K = 1024;

  private static ArrayList<byte[]> data = new ArrayList<byte[]>();

    private static void generateHeapContents() {
        for (int i = 0 ; i < 64; i++) {
            for (int j = 0; j < K; j++) {
                data.add(new byte[K]);
            }
        }
    }

    private static void generateEvents(Recording recording) throws Exception {
        recording.enable(ResidentSetSizeEvent).with("period", "everyChunk");

        recording.start();

        // Generate data to force heap to grow.
        generateHeapContents();

        recording.stop();
    }

    private static void verifyExpectedEventTypes(List<RecordedEvent> events) throws Exception {
        List<RecordedEvent> filteredEvents = events.stream().filter(e -> e.getEventType().getName().equals(ResidentSetSizeEvent)).toList();

        assertGreaterThan(filteredEvents.size(), 0, "Should exist events of type: " + ResidentSetSizeEvent);

        for (RecordedEvent event : filteredEvents) {
            long size = event.getLong("size");
            long peak = event.getLong("peak");
            assertGreaterThan(size, 0L, "Should be non-zero");
            assertGreaterThan(peak, 0L, "Should be non-zero");
            assertLessThanOrEqual(size, peak, "The size should be less than or equal to peak");
        }
    }

    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            generateEvents(recording);

            var events = Events.fromRecording(recording);
            verifyExpectedEventTypes(events);
        }
    }
}
