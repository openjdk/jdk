/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jmx.streaming;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jdk.jfr.Event;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordingStream;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServerConnection;
import jdk.management.jfr.RemoteRecordingStream;
/**
 * @test
 * @summary Tests RemoteRecordingStream::stop()
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @build jdk.jfr.api.consumer.recordingstream.EventProducer
 * @run main/othervm jdk.jfr.jmx.streaming.TestStop
 */
public class TestStop {

    private static final MBeanServerConnection CONNECTION = ManagementFactory.getPlatformMBeanServer();
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

    private static void testStopUnstarted() throws Exception {
        try (var rs = new RemoteRecordingStream(CONNECTION)) {
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
        // Check that events are not consumed after stop()
        List<RecordedEvent> events = new ArrayList<>();
        try (var rs = new RemoteRecordingStream(CONNECTION)) {
            rs.onEvent(e -> {
                events.add(e);
            });
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
        try (var rs = new RemoteRecordingStream(CONNECTION)) {
            Thread t = new Thread(() -> rs.stop());
            rs.startAsync();
            t.start();
            rs.awaitTermination();
            t.join();
        }
    }

    private static void testNestedStop() throws Exception {
        AtomicLong outerCount = new AtomicLong();
        AtomicLong innerCount = new AtomicLong();
        try (var outer = new RemoteRecordingStream(CONNECTION)) {
            outer.onEvent(e -> outerCount.incrementAndGet());
            outer.setMaxSize(100_000_000);
            outer.startAsync();

            MarkEvent a = new MarkEvent();
            a.id = "a";
            a.commit();

            try (var inner = new RemoteRecordingStream(CONNECTION)) {
                inner.setMaxSize(100_000_000);
                inner.onEvent(e -> innerCount.incrementAndGet());
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
                System.out.println("RecordingStream outer:");
                var dumpOuter = RecordingFile.readAllEvents(fileOuter);
                System.out.println(dumpOuter);
                System.out.println("RecordingStream inner:");
                var dumpInner = RecordingFile.readAllEvents(fileInner);
                System.out.println(dumpInner);
                System.out.println("Outer count: " + outerCount);
                System.out.println("Inner count: " + innerCount);
                if (dumpOuter.size() != 3) {
                    throw new AssertionError("Expected outer dump to have 3 events");
                }
                if (outerCount.get() != 3) {
                    throw new AssertionError("Expected outer stream to have 3 events");
                }
                if (dumpInner.size() != 1) {
                    throw new AssertionError("Expected inner dump to have 1 event");
                }
                if (innerCount.get() != 1) {
                    throw new AssertionError("Expected inner stream to have 1 event");
                }
            }
        }
    }

    static void testStopClosed() throws Exception {
        try (var rs = new RemoteRecordingStream(CONNECTION)) {
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
