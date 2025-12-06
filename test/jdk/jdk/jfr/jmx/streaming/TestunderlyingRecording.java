/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordingStream;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServerConnection;
import jdk.management.jfr.RemoteRecordingStream;
/**
 * @test
 * @summary Tests RemoteRecordingStream behavior when the underlying recording is stopped/closed.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.jmx.streaming.TestUnderlyingRecording
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
        try (Recording outer = new Recording()) {
            outer.setName("Outer");
            outer.start();
            try (var rs = new RemoteRecordingStream(ManagementFactory.getPlatformMBeanServer())) {
                Recording underlying = getUnderlyingRecording();
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
                    throw new Exception("Unexpected close when underlying recording was stopped");
                }
            }
        }
    }

    // Closing the underlying recording should stop the stream of events, but not clear the replicated directory
    private static void testClose() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        CountDownLatch latch = new CountDownLatch(1);
        Path repository = Path.of("./chunks");
        Files.createDirectories(repository);
        try (var rs = new RemoteRecordingStream(ManagementFactory.getPlatformMBeanServer(), repository)) {
            Recording underlying = getUnderlyingRecording();
            rs.onEvent(e -> {
                underlying.close();
                latch.countDown();
            });
            rs.startAsync();
            while (!hasChunks(repository)) {
                Thread.sleep(10);
            }
            SomeEvent event = new SomeEvent();
            event.commit();
            latch.await();
            if (!hasChunks(repository)) {
                throw new Exception("Looks like stream was close when underlying recording was closed");
            }
        }
        if (hasChunks(repository)) {
            throw new Exception("Files should be removed when stream is closed");
        }
    }

    private static boolean hasChunks(Path repository) throws IOException {
        try (var files = Files.list(repository)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".jfr"));
        }
    }

    private static Recording getUnderlyingRecording() throws Exception {
        for (Recording r : FlightRecorder.getFlightRecorder().getRecordings()) {
            if (!r.getName().equals("Outer")) {
                return r;
            }
        }
        throw new Exception("Could not find underlying recording.");
    }
}
