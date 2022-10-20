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

package jdk.jfr.api.consumer.recordingstream;

import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Event;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Tests RecordingStream::stop()
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @build jdk.jfr.api.consumer.recordingstream.EventProducer
 * @run main/othervm jdk.jfr.api.consumer.recordingstream.TestStop
 */
public class TestStop {
    static class StopEvent extends Event {
    }

    public static void main(String... args) throws Exception {
        testStopUnstarted();
        testStop();
        testStopOnEvent();
        testStopFromOtherThread();
        testStopTwice();
        testStopClosed();
        testDumpStopped();
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
            rs.start();
            t.start();
            rs.awaitTermination();
            t.join();
        }
    }

    private static void testStopTwice() throws Exception {
        try (RecordingStream rs = new RecordingStream()) {
            rs.startAsync();
            EventProducer t = new EventProducer();
            t.start();
            rs.stop();
            rs.stop(); // should not throw exception
        }
    }

    static void testStopOnEvent() throws Exception {
        try (RecordingStream rs = new RecordingStream()) {
            rs.onEvent(e -> {
                rs.stop(); // must not deadlock
            });
            rs.startAsync();
            EventProducer t = new EventProducer();
            t.start();
            rs.awaitTermination(); // should wake up after stop()
            t.kill();
        }
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
