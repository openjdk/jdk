/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import javax.management.MBeanServerConnection;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.management.jfr.RemoteRecordingStream;

/**
 * @test
 * @summary Tests retention with RemoteRecordingStream::dump(Path)
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.jmx.streaming.TestDumpRetention
 */
public class TestDumpRetention {

    private static final MBeanServerConnection CONNECTION = ManagementFactory.getPlatformMBeanServer();

    @Name("TestDumpRetention")
    static class DumpEvent extends Event {
    }

    public static void main(String... args) throws Exception {
        testSetNoPolicy();
        testSetMaxAge();
        testSetMaxSize();
    }

    private static List<RecordedEvent> recordWithPolicy(String filename, Consumer<RemoteRecordingStream> policy) throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(2);
        CountDownLatch latch3 = new CountDownLatch(3);
        try (var rs = new RemoteRecordingStream(CONNECTION)) {
            policy.accept(rs);
            rs.onEvent(e -> {
                latch1.countDown();
                latch2.countDown();
                latch3.countDown();
            });
            rs.startAsync();
            DumpEvent e1 = new DumpEvent();
            e1.commit();
            latch1.await();
            // Force chunk rotation
            try (Recording r = new Recording()) {
                r.start();
                DumpEvent e2 = new DumpEvent();
                e2.commit();
            }
            latch2.await();
            DumpEvent e3 = new DumpEvent();
            e3.commit();
            latch3.await();
            Path p = Path.of(filename);
            rs.dump(p);
            return RecordingFile.readAllEvents(p);
        }
    }

    private static void testSetNoPolicy() throws Exception {
        var events = recordWithPolicy("no-policy.jfr", rs -> {
            // use default policy, remove after consumption
        });
        // Since latch3 have been triggered at least two events/chunks
        // before must have been consumed, possibly 3, but it's a race.
        if (events.size() > 1) {
            throw new Exception("Expected at most one event to not be consumed");
        }
    }

    private static void testSetMaxAge() throws Exception {
        var events = recordWithPolicy("max-age.jfr", rs -> {
            // keeps all events for the dump
            rs.setMaxAge(Duration.ofDays(1));
        });
        if (events.size() != 3) {
            throw new Exception("Expected all 3 events to be in dump after setMaxAge");
        }
    }

    private static void testSetMaxSize() throws Exception {
        var events = recordWithPolicy("max-size.jfr", rs -> {
            // keeps all events for the dump
            rs.setMaxSize(100_000_000);
        });
        if (events.size() != 3) {
            throw new Exception("Expected all 3 events to be in dump after setMaxSize");
        }
    }
}
