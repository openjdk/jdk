/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/othervm -Xms16m -Xmx128m -Xlog:gc -Xlog:trimnative -XX:TrimNativeHeapInterval=500 jdk.jfr.event.runtime.TestProcessSizeEvent
 */
public class TestProcessSizeEvent {
    private final static String ProcessSizeEventName = EventNames.ProcessSize;
    private final static String LibcStatisticsEventName = EventNames.LibcStatistics;

    private final static long K = 1024;
    private final static long M = K * K;
    private final static long G = M * K;
    private final static long toAllocate = M * 64;
    private final static long sleepTime = 6000;

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
        recording.enable(ProcessSizeEventName).with("period", "500ms");
        recording.enable(LibcStatisticsEventName).with("period", "500ms");

        recording.start();

        doNoisyThings();

        recording.stop();
    }

    private static void verifyExpectedProcessSizeEvents(List<RecordedEvent> events) throws Exception {
        List<RecordedEvent> filteredEvents = events.stream().filter(e -> e.getEventType().getName().equals(ProcessSizeEventName)).toList();

        assertGreaterThan(filteredEvents.size(), 0, "Should exist events of type: " + ProcessSizeEventName);

        for (RecordedEvent event : filteredEvents) {
            System.out.println(event);
            long vsize = event.getLong("vsize");
            long rss = event.getLong("rss");
            long rssPeak = event.getLong("rssPeak");
            long rssAnon = event.getLong("rssAnon");
            long rssFile = event.getLong("rssFile");
            long rssShmem = event.getLong("rssShmem");
            long swap = event.getLong("swap");
            long pagetable = event.getLong("pagetable");

            long reasonableVsizeHigh = G * K; // vsize can get very large
            long reasonableVsizeLow = 100 * M;
            assertGreaterThan(vsize, reasonableVsizeLow, "Must be");
            assertLessThan(vsize, reasonableVsizeHigh, "Must be");

            long reasonableRSSHigh = G; // probably a lot less
            long reasonableRSSLow = 10 * M; // probably a lot less
            assertGreaterThan(rss, reasonableRSSLow, "Must be");
            assertLessThan(rss, reasonableRSSHigh, "Must be");

            assertGreaterThan(rssPeak, reasonableRSSLow, "Must be");
            assertLessThan(rssPeak, reasonableRSSHigh, "Must be");

            assertGreaterThan(rssAnon, reasonableRSSLow / 2, "Must be");
            assertLessThanOrEqual(rssAnon, rss, "Must be");

            assertGreaterThanOrEqual(rssFile, 0L, "Must be");
            assertLessThanOrEqual(rssFile, rss, "Must be");

            assertGreaterThanOrEqual(rssShmem, 0L, "Must be");
            assertLessThanOrEqual(rssShmem, rss, "Must be");

            assertGreaterThan(pagetable, 0L, "Must be");

            assertGreaterThanOrEqual(swap, 0L, "Must be");
        }
    }

    private static void verifyExpectedLibcStatisticsEvents(List<RecordedEvent> events) throws Exception {
        List<RecordedEvent> filteredEvents = events.stream().filter(e -> e.getEventType().getName().equals(LibcStatisticsEventName)).toList();

        assertGreaterThan(filteredEvents.size(), 0, "Should exist events of type: " + LibcStatisticsEventName);

        for (RecordedEvent event : filteredEvents) {
            System.out.println(event);
            long mallocOutstanding = event.getLong("mallocOutstanding");
            long mallocRetained = event.getLong("mallocRetained");
            long trims = event.getLong("trims");

            assertGreaterThan(mallocOutstanding, 0L, "Must be");
            long reasonableLimit = G; // vsize can get very large
            assertLessThan(mallocOutstanding, reasonableLimit, "Must be");

            assertGreaterThan(mallocRetained, 0L, "Must be");
            assertLessThan(mallocRetained, reasonableLimit, "Must be");

            // we activated periodic trims and should see them
            assertGreaterThan(trims, 0L, "Must be");
        }
    }

    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            generateEvents(recording);
            var events = Events.fromRecording(recording);
            verifyExpectedProcessSizeEvents(events);
            verifyExpectedLibcStatisticsEvents(events);
        }
    }
}
