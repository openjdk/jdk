/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.consumer.recordingstream;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import jdk.jfr.Event;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Tests RecordingStream::stop()
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @build jdk.jfr.api.consumer.recordingstream.EventProducer
 * @run main/othervm -Xlog:system+parser+jfr=info jdk.jfr.api.consumer.recordingstream.TestStop
 */
public class TestStop {
    static class StopEvent extends Event {
    }

    static class MarkEvent extends Event {
        String id;
    }

    public static void main(String... args) throws Exception {
        testStopUnstarted();
        testStop();
        testStopFromOtherThread();
        testNestedStop();
        testStopClosed();
    }

    private static void testStopUnstarted() {
        try (RecordingStream rs = new RecordingStream()) {
            try {
                rs.stop();
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException ise) {
                // OK, as expected.
            }
        }
    }

    static void testStop() throws Exception {
        // Check that all events emitted prior to
        // stop() can be consumed
        // Check that events are not consumer after stop()
        List<RecordedEvent> events = new ArrayList<>();
        try (RecordingStream rs = new RecordingStream()) {
            rs.onEvent(events::add);
            rs.startAsync();
            for (int i = 0; i < 100; i++) {
                StopEvent s = new StopEvent();
                s.commit();
            }
            rs.stop();
            if (events.size() != 100) {
                throw new AssertionError("Expected 100 events");
            }
            for (int i = 0; i < 100; i++) {
                StopEvent s = new StopEvent();
                s.commit();
            }
            if (events.size() != 100) {
                throw new AssertionError("Expected 100 events");
            }
        }
    }

    private static void testStopFromOtherThread() throws Exception {
        try (RecordingStream rs = new RecordingStream()) {
            Thread t = new Thread(() -> rs.stop());
            rs.startAsync();
            t.start();
            rs.awaitTermination();
            t.join();
        }
    }

    private static void testNestedStop() throws Exception {
        List<String> outerStream = Collections.synchronizedList(new ArrayList<>());
        List<String> innerStream = Collections.synchronizedList(new ArrayList<>());
        try (RecordingStream outer = new RecordingStream()) {
            outer.onEvent(e -> outerStream.add(eventToText(e)));
            outer.setMaxSize(100_000_000);
            outer.startAsync();

            MarkEvent a = new MarkEvent();
            a.id = "a";
            a.commit();

            try (RecordingStream inner = new RecordingStream()) {
                inner.setMaxSize(100_000_000);
                inner.onEvent(e -> innerStream.add(eventToText(e)));
                inner.startAsync();

                MarkEvent b = new MarkEvent();
                b.id = "b";
                b.commit();

                inner.stop();

                MarkEvent c = new MarkEvent();
                c.id = "c";
                c.commit();

                outer.stop();

                Path fileOuter = Path.of("outer.jfr");
                Path fileInner = Path.of("inner.jfr");
                inner.dump(fileInner);
                outer.dump(fileOuter);
                var dumpOuter = RecordingFile.readAllEvents(fileOuter);
                var dumpInner = RecordingFile.readAllEvents(fileInner);

                if (dumpOuter.size() != 3) {
                    log(outerStream, innerStream, dumpOuter, dumpInner);
                    throw new AssertionError("Expected outer dump to have 3 events");
                }
                if (outerStream.size() != 3) {
                    log(outerStream, innerStream, dumpOuter, dumpInner);
                    throw new AssertionError("Expected outer stream to have 3 events");
                }
                if (dumpInner.size() != 1) {
                    log(outerStream, innerStream, dumpOuter, dumpInner);
                    throw new AssertionError("Expected inner dump to have 1 event");
                }
                if (innerStream.size() != 1) {
                    log(outerStream, innerStream, dumpOuter, dumpInner);
                    throw new AssertionError("Expected inner stream to have 1 event");
                }
            }
        }
    }

    private static void log(List<String> outerStream, List<String> innerStream, List<RecordedEvent> dumpOuter,
            List<RecordedEvent> dumpInner) {
        System.out.println("Outer dump:");
        for (RecordedEvent e : dumpOuter) {
            System.out.println(eventToText(e));
        }
        System.out.println("Inner dump:");
        for (RecordedEvent e : dumpInner) {
            System.out.println(eventToText(e));
        }
        System.out.println();
        System.out.println("Outer stream:");
        for (String s : outerStream) {
            System.out.println(s);
        }
        System.out.println("Inner stream:");
        for (String s : innerStream) {
            System.out.println(s);
        }
    }

    private static String eventToText(RecordedEvent event) {
        Instant timestamp = event.getEndTime();
        long s = timestamp.getEpochSecond();
        int n = timestamp.getNano();
        String id = event.getString("id");
        return id + ": n=" + n + " s=" + s + " t=" + timestamp;
    }

    static void testStopClosed() {
        try (RecordingStream rs = new RecordingStream()) {
            rs.close();
            try {
                rs.stop();
                throw new AssertionError("Expected IllegalStateException");
            } catch (IllegalStateException ise) {
                // OK, as expected.
            }
        }
    }
}
