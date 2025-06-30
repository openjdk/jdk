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

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanServerConnection;

import jdk.jfr.Event;
import jdk.jfr.StackTrace;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.management.jfr.RemoteRecordingStream;

/**
 * @test
 * @requires vm.flagless
 * @summary Tests that chunks arrive in the same order they were committed
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.jmx.streaming.TestDumpOrder
 */
public class TestDumpOrder {

    private static final MBeanServerConnection CONNECTION = ManagementFactory.getPlatformMBeanServer();

    @StackTrace(false)
    static class Ant extends Event {
        long id;
    }

    public static void main(String... args) throws Exception {
        // Set up the test so half of the events have been consumed
        // when the dump occurs.
        AtomicLong eventCount = new AtomicLong();
        CountDownLatch halfLatch = new CountDownLatch(1);
        CountDownLatch dumpLatch = new CountDownLatch(1);
        Path directory = Path.of("chunks");
        Files.createDirectory(directory);
        try (var rs = new RemoteRecordingStream(CONNECTION, directory)) {
            rs.setMaxSize(100_000_000); // keep all data
            rs.onEvent(event -> {
                try {
                    eventCount.incrementAndGet();
                    if (eventCount.get() == 10) {
                        halfLatch.countDown();
                        dumpLatch.await();
                    }
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            });
            rs.startAsync();
            long counter = 0;
            for (int i = 0; i < 10; i++) {
                try (Recording r = new Recording()) {
                    r.start();
                    Ant a = new Ant();
                    a.id = counter++;
                    a.commit();
                    Ant b = new Ant();
                    b.id = counter++;
                    b.commit();
                }
                if (counter == 10) {
                    halfLatch.await();
                }
            }
            Path file = Path.of("events.jfr");
            // Wait for most (but not all) chunk files to be downloaded
            // before invoking dump()
            awaitChunkFiles(directory);
            // To stress the implementation, release consumer thread
            // during the dump
            dumpLatch.countDown();
            rs.dump(file);
            List<RecordedEvent> events = RecordingFile.readAllEvents(file);
            if (events.isEmpty()) {
                throw new AssertionError("No events found");
            }
            // Print events for debugging purposes
            events.forEach(System.out::println);
            long expected = 0;
            for (var event : events) {
                long value = event.getLong("id");
                if (value != expected) {
                    throw new Exception("Expected " + expected + ", got " + value);
                }
                expected++;
            }
            if (expected != counter) {
                throw new Exception("Not all events found");
            }
        }
    }

    private static void awaitChunkFiles(Path directory) throws Exception {
        while (Files.list(directory).count() < 7) {
            Thread.sleep(10);
        }
    }
}
