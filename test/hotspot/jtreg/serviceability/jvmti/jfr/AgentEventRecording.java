/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;

final class AgentEventRecording implements AutoCloseable {
    private final Recording recording = new Recording();

    AgentEventRecording() {
        recording.enable(EventNames.NativeAgent).with("period", "endChunk");
        recording.start();
    }

    // Read the current events without stopping the recording.
    Results snapshot() throws Exception {
        Path file = Files.createTempFile(Path.of("."), "agent-events-", ".jfr");
        try {
            recording.dump(file);
            return new Results(RecordingFile.readAllEvents(file).stream().filter(
                e -> e.getEventType().getName().equals(EventNames.NativeAgent)).toList());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    Results stop() throws Exception {
        recording.stop();
        return snapshot();
    }

    @Override
    public void close() {
        recording.close();
    }

    // A fresh recording must contain no events for these libraries.
    static void assertNoEventsInNewRecording(String... libraries) throws Exception {
        try (AgentEventRecording recording = new AgentEventRecording()) {
            var results = recording.stop();
            for (String library : libraries) {
                results.assertNoEvents(library);
            }
        }
    }

    record Results(List<RecordedEvent> events) {
        FilteredList forLibrary(String library) {
            return new FilteredList(events.stream().filter(e -> library.equals(e.getString("name"))).toList());
        }

        void assertNoEvents(String library) {
            Asserts.assertTrue(forLibrary(library).events().isEmpty(), "Unexpected events for " + library + "\n" + events);
        }
    }

    // Filtering preserves event order and duplicates. Property assertions require a nonempty list.
    record FilteredList(List<RecordedEvent> events) {
        FilteredList forOptions(String options) {
            return new FilteredList(events.stream().filter(e -> options.equals(e.getString("options"))).toList());
        }

        // Assert the exact set of distinct options, not merely that the expected values occur.
        FilteredList assertOptions(String... options) {
            assertNonEmpty();
            List<String> actual = events.stream().map(e -> e.getString("options")).distinct().sorted().toList();
            List<String> expected = Arrays.stream(options).sorted().toList();
            Asserts.assertEquals(actual, expected, "Event options\n" + events);
            return this;
        }

        FilteredList assertDynamic(boolean dynamic) {
            assertNonEmpty();
            for (RecordedEvent event : events) {
                Asserts.assertEquals(event.getBoolean("dynamic"), dynamic, event.toString());
            }
            return this;
        }

        FilteredList assertInitializationTiming() {
            assertNonEmpty();
            for (RecordedEvent event : events) {
                String description = event.toString();
                Asserts.assertNE(event.getLong("initializationTime"), Long.MIN_VALUE, description);
                Asserts.assertGTE(event.getLong("initializationDuration"), 0L, description);
                Asserts.assertFalse(event.getInstant("initializationTime").isAfter(event.getStartTime()), description);
            }
            return this;
        }

        FilteredList assertNoInitializationTiming() {
            assertNonEmpty();
            for (RecordedEvent event : events) {
                String description = event.toString();
                Asserts.assertEquals(event.getLong("initializationTime"), Long.MIN_VALUE, description);
                Asserts.assertEquals(event.getLong("initializationDuration"), Long.MIN_VALUE, description);
            }
            return this;
        }

        FilteredList assertLive() {
            return assertDisposalCount(0);
        }

        FilteredList assertDisposed() {
            return assertDisposalCount(1);
        }

        private void assertNonEmpty() {
            Asserts.assertFalse(events.isEmpty(), "Empty filtered event list");
        }

        // Validate each instance separately, including its disposal-event count.
        private FilteredList assertDisposalCount(int expected) {
            assertNonEmpty();
            for (String options : events.stream().map(e -> e.getString("options")).distinct().toList()) {
                List<RecordedEvent> instanceEvents = forOptions(options).events();
                int disposals = 0;
                for (RecordedEvent event : instanceEvents) {
                    String description = event.toString();
                    String path = event.getString("path");
                    Asserts.assertTrue(path != null && !path.isEmpty(), description);
                    Asserts.assertEquals(event.getStartTime(), event.getEndTime(), description);
                    if (event.getLong("disposalTime") != Long.MIN_VALUE) {
                        ++disposals;
                        Asserts.assertEquals(event.getInstant("disposalTime"), event.getStartTime(), description);
                    }
                }
                Asserts.assertEquals(disposals, expected, "Disposal events for " + options + "\n" + instanceEvents);
            }
            return this;
        }
    }
}
