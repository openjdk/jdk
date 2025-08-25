/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, IBM Corporation. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static jdk.test.lib.Asserts.*;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @requires (os.family=="linux") & !vm.musl
 * @library /test/lib
 * @modules jdk.jfr
 *          jdk.management
 * @run main/othervm -Xmx64m -XX:TrimNativeHeapInterval=250 -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics jdk.jfr.event.runtime.TestLibcStatisticsEvent
 */
public class TestLibcStatisticsEvent {
    private final static String LibcStatisticsEventName = EventNames.LibcStatistics;

    private final static long K = 1024;
    private final static long M = K * K;
    private final static long G = M * K;
    private final static long toAllocate = M * 64;
    private final static long sleepTime = 3000;

    private static ArrayList<byte[]> data = new ArrayList<byte[]>();

    private static void doNoisyThings() throws InterruptedException {
        // Allocate some memory in the C-Heap (using DBBs).
        // Touch that memory to drive up RSS.
        long bufferSize = M;
        long numBuffers = toAllocate / bufferSize;
        ByteBuffer[] list = new ByteBuffer[(int)numBuffers];
        for (int i = 0; i < list.length; i++) {
            list[i] = ByteBuffer.allocateDirect((int)bufferSize);
        }
        for (ByteBuffer b : list) {
            while (b.position() < b.capacity()) {
                b.put((byte) 'A');
                if (b.position() < (b.capacity() - K)) {
                    b.position(b.position() + (int)K);
                }
            }
        }
        Thread.sleep(sleepTime);
    }

    private static void generateEvents(Recording recording) throws Exception {
        recording.enable(LibcStatisticsEventName).with("period", "250ms");

        recording.start();

        doNoisyThings();

        recording.stop();
    }

    private static void verifyExpectedEvents(List<RecordedEvent> events) throws Exception {
        List<RecordedEvent> filteredEvents = events.stream().filter(e -> e.getEventType().getName().equals(LibcStatisticsEventName)).toList();

        assertGreaterThan(filteredEvents.size(), 0, "Should exist events of type: " + LibcStatisticsEventName);

        long mallocOutstanding = 0, mallocRetained = 0;
        long trims = 0;

        RecordedEvent last = null;
        for (RecordedEvent event : filteredEvents) {
            System.out.println(event);
            mallocOutstanding = event.getLong("mallocOutstanding");
            mallocRetained = event.getLong("mallocRetained");
            trims = event.getLong("trims");

            long reasonableLow = K; // probably a lot more, but the very first events may not show much yet
            long reasonableLimit = G; // probably a lot less
            assertGreaterThan(mallocOutstanding, reasonableLow);
            assertLessThan(mallocOutstanding, reasonableLimit);

            assertGreaterThan(mallocRetained, 0L);
            assertLessThan(mallocRetained, reasonableLimit);

            last = event;
        }

        assertNotNull(last);
        // we activated periodic trims and by the time the last event was taken we should see them
        assertGreaterThan(trims, 0L, "Must be");
        // by this time we also should have allocated the majority of what we allocate
        assertGreaterThan(mallocOutstanding, toAllocate / 2, "Must be");
    }

    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            generateEvents(recording);
            var events = Events.fromRecording(recording);
            verifyExpectedEvents(events);
        }
    }
}
