/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Tests RecordingStream behavior when underlying recording is
 *          stopped/closed.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm
 *      jdk.jfr.api.consumer.recordingstream.TestUnderlyingRecording
 */
public class TestUnderlyingRecording {

    static final class SomeEvent extends Event {
        long id;
    }

    public static void main(String... args) throws Exception {
        testStop();
        testClose();
    }

    // Stopping the underlying recording should stop the stream of events.
    private static void testStop() throws Exception {
        Collection<Long> ids = ConcurrentHashMap.newKeySet();
        AtomicBoolean closed = new AtomicBoolean();
        Runnable closeAction = () -> closed.set(true);
        try (var rs = new RecordingStream()) {
            Recording underlying = FlightRecorder.getFlightRecorder().getRecordings().getFirst();
            rs.onClose(closeAction);
            rs.onEvent(e -> ids.add(e.getLong("id")));
            rs.onFlush(() -> {
                System.out.println("Flushed events: " + ids.size());
                if (ids.size() == 50) {
                    rs.remove(closeAction);
                    rs.close();
                }
            });
            rs.startAsync();
            for (int i = 0; i < 100; i++) {
                if (i == 50) {
                    underlying.stop();
                }
                SomeEvent event = new SomeEvent();
                event.id = i;
                event.commit();
            }
            rs.awaitTermination();
            if (closed.get()) {
                throw new Exception("Unexpected close when stream was stopped.");
            }
        }
    }

    // Closing the underlying recording should close the stream
    private static void testClose() throws Exception {
        try (var rs = new RecordingStream()) {
            Recording underlying = FlightRecorder.getFlightRecorder().getRecordings().getFirst();
            rs.onEvent(e -> underlying.close());
            rs.startAsync();
            SomeEvent event = new SomeEvent();
            event.commit();
            rs.awaitTermination();
        }
    }
}
