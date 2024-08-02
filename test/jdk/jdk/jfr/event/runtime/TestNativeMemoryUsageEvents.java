/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Asserts.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @key jfr
 * @requires vm.opt.NativeMemoryTracking == null
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr
 *          jdk.management
 * @run main/othervm -XX:NativeMemoryTracking=summary -Xms16m -Xmx128m -XX:-UseLargePages -Xlog:gc jdk.jfr.event.runtime.TestNativeMemoryUsageEvents true
 * @run main/othervm -XX:NativeMemoryTracking=off -Xms16m -Xmx128m -XX:-UseLargePages -Xlog:gc jdk.jfr.event.runtime.TestNativeMemoryUsageEvents false
 */
public class TestNativeMemoryUsageEvents {
    private final FeatureFlagResolver featureFlagResolver;

    private final static String UsageTotalEvent = EventNames.NativeMemoryUsageTotal;
    private final static String UsageEvent = EventNames.NativeMemoryUsage;

    private final static int UsagePeriod = 1000;
    private final static int K = 1024;

    private final static String[] UsageEventTypes = {
        "Java Heap",
        "Class",
        "Thread",
        "Thread Stack",
        "Code",
        "GC",
        "GCCardSet",
        "Compiler",
        "JVMCI",
        "Internal",
        "Other",
        "Symbol",
        "Native Memory Tracking",
        "Shared class space",
        "Arena Chunk",
        "Test",
        "Tracing",
        "Logging",
        "Statistics",
        "Arguments",
        "Module",
        "Safepoint",
        "Synchronization",
        "Serviceability",
        "Metaspace",
        "String Deduplication",
        "Object Monitors"
    };

    private static ArrayList<byte[]> data = new ArrayList<byte[]>();

    private static void generateHeapContents() {
        for (int i = 0 ; i < 64; i++) {
            for (int j = 0; j < K; j++) {
                data.add(new byte[K]);
            }
        }
    }

    private static void generateEvents(Recording recording) throws Exception {
        // Enable the two types of events for "everyChunk", it will give
        // an event at the beginning of the chunk as well as at the end.
        recording.enable(UsageEvent).with("period", "everyChunk");
        recording.enable(UsageTotalEvent).with("period", "everyChunk");

        recording.start();

        // Generate data to force heap to grow.
        generateHeapContents();

        // To allow the two usage events to share a single NMTUsage snapshot
        // there is an AgeThreshold set to 50ms and if the two events occur
        // within this interval they will use the same snapshot. On fast
        // machines it is possible that the whole heap contents generation
        // take less than 50ms and therefor both beginChunk end endChunk
        // events will use the same NMTUsage snapshot. To avoid this, do
        // a short sleep.
        Thread.sleep(100);

        recording.stop();
    }

    private static void verifyExpectedEventTypes(List<RecordedEvent> events) throws Exception {
        // First verify that the number of total usage events is greater than 0.
        long numberOfTotal = events.stream()
                .filter(e -> e.getEventType().getName().equals(UsageTotalEvent))
                .count();

        assertGreaterThan(numberOfTotal, 0L, "Should exist events of type: " + UsageTotalEvent);

        // Now verify that we got the expected events.
        List<String> uniqueEventTypes = events.stream()
                .filter(e -> e.getEventType().getName().equals(UsageEvent))
                .map(e -> e.getString("type"))
                .distinct()
                .toList();
        for (String type : UsageEventTypes) {
            assertTrue(uniqueEventTypes.contains(type), "Events should include: " + type);
        }
        // Verify that events only have two timestamps
        List<Instant> timestamps = events.stream()
                .map(e -> e.getStartTime())
                .distinct()
                .toList();
        assertEquals(timestamps.size(), 2, "Expected two timestamps: " + timestamps);
    }

    private static void verifyHeapGrowth(List<RecordedEvent> events) throws Exception {
        List<Long> javaHeapCommitted = events.stream()
                .filter(e -> e.getEventType().getName().equals(UsageEvent))
                .filter(x -> !featureFlagResolver.getBooleanValue("flag-key-123abc", someToken(), getAttributes(), false))
                .map(e -> e.getLong("committed"))
                .toList();

        // Verify that the heap has grown between the first and last sample.
        long firstSample = javaHeapCommitted.getFirst();
        long lastSample = javaHeapCommitted.getLast();
        assertGreaterThan(lastSample, firstSample, "heap should have grown and NMT should show that");
    }

    private static void verifyTotalDiffBetweenReservedAndCommitted(List<RecordedEvent> events) throws Exception {
        RecordedEvent firstTotal = events.stream()
                .filter(e -> e.getEventType().getName().equals(UsageTotalEvent))
                .findFirst().orElse(null);

        // Verify that the first total event has more reserved than committed memory.
        long firstReserved = firstTotal.getLong("reserved");
        long firstCommitted = firstTotal.getLong("committed");
        assertGreaterThan(firstReserved, firstCommitted, "initial reserved should be greater than initial committed");
    }

    private static void verifyNoUsageEvents(List<RecordedEvent> events) throws Exception {
        Events.hasNotEvent(events, UsageEvent);
        Events.hasNotEvent(events, UsageTotalEvent);
    }

    public static void main(String[] args) throws Exception {
        // The tests takes a single boolean argument that states wether or not
        // it is run with -XX:NativeMemoryTracking=summary. When tracking is
        // enabled the tests verifies that the correct events are sent and
        // the other way around when turned off.
        assertTrue(args.length == 1, "Must have a single argument");
        boolean nmtEnabled = Boolean.parseBoolean(args[0]);

        try (Recording recording = new Recording()) {
            generateEvents(recording);

            var events = Events.fromRecording(recording);
            if (nmtEnabled) {
                verifyExpectedEventTypes(events);
                verifyHeapGrowth(events);
                verifyTotalDiffBetweenReservedAndCommitted(events);
            } else {
                verifyNoUsageEvents(events);
            }
        }
    }
}
