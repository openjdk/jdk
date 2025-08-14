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

package jdk.jfr.event.runtime;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static jdk.test.lib.Asserts.*;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires (os.family=="linux") & !vm.musl
 * @library /test/lib
 * @modules jdk.jfr
 *          jdk.management
 * @run main/othervm -Xmx64m -Xlog:trimnative -XX:TrimNativeHeapInterval=200 jdk.jfr.event.runtime.TestLibcHeapTrimEvent
 */
public class TestLibcHeapTrimEvent {
    private final static long K = 1024;
    private final static long M = K * K;
    private final static long G = M * K;
    private final static long sleepTime = 5000; // Time enough for the trimmer thread to get started and engaged

    private static ArrayList<byte[]> data = new ArrayList<byte[]>();

    private static void generateEvents(Recording recording) throws Exception {
        recording.enable(EventNames.LibcHeapTrim);

        recording.start();

        Thread.sleep(sleepTime);

        recording.stop();
    }

    private static void verifyExpectedEvents(List<RecordedEvent> events) throws Exception {
        List<RecordedEvent> filteredEvents = events.stream().filter(e -> e.getEventType().getName().equals(EventNames.LibcHeapTrim)).toList();

System.out.println("COUNT " + events.stream().count());
System.out.println(events.stream().collect(Collectors.toList()));

        assertGreaterThan(filteredEvents.size(), 0, "Should exist events of type: " + EventNames.LibcHeapTrim);

        for (RecordedEvent event : filteredEvents) {
            System.out.println(event);
            double duration = event.getLong("duration");
            long rssPre = event.getLong("rssPre");
            long rssPost = event.getLong("rssPost");
            long rssRecovered = event.getLong("rssRecovered");

            long reasonableHigh = G;
            assertGreaterThan(rssPre, 0L, "Must be");
            assertLessThan(rssPre, reasonableHigh, "Must be");

            assertGreaterThan(rssPost, 0L, "Must be");
            assertLessThan(rssPost, reasonableHigh, "Must be");

            assertGreaterThanOrEqual(rssRecovered, 0L, "Must be");
            assertLessThan(rssRecovered, rssPre, "Must be");
        }
    }

    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            generateEvents(recording);
            var events = Events.fromRecording(recording);
            verifyExpectedEvents(events);
        }
    }
}
