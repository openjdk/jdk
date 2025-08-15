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
import jdk.test.lib.Platform;
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
 * @requires (os.family=="linux") | (os.family=="mac")
 * @library /test/lib
 * @modules jdk.jfr
 *          jdk.management
 * @run main/othervm -Xmx64m -XX:+UnlockDiagnosticVMOptions -XX:+PrintVMInfoAtExit jdk.jfr.event.runtime.TestProcessSizeEvent
 */
public class TestProcessSizeEvent {
    private final static String ProcessSizeEventName = EventNames.ProcessSize;
    private final static String LibcStatisticsEventName = EventNames.LibcStatistics;

    private final static long K = 1024;
    private final static long M = K * K;
    private final static long G = M * K;
    private final static long toAllocate = M * 16;
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
        recording.enable(ProcessSizeEventName).with("period", "250ms");

        recording.start();

        doNoisyThings();

        recording.stop();
    }

    final static long reasonableVsizeHigh = G * 10000;
    final static long reasonableVsizeLow = 100 * M;
    final static long reasonableRSSHigh = G;
    // We may run on undersized test machines, in which case the test may be heavily swapping. This in turn
    // can falsify the reported RSS (which is why we report Swap as a metric, too).
    // To avoid false positives, RSS low is chosen to be a very defensive low number.
    final static long reasonableRSSLow = 5 * M;

    private static void verifyExpectedEventCommon(RecordedEvent event) throws Exception {
        long vsize = Events.assertField(event, "vsize").
                atLeast(reasonableVsizeLow).atMost(reasonableVsizeHigh).getValue();
        long rss = Events.assertField(event, "rss").
                atLeast(reasonableRSSLow).atMost(reasonableRSSHigh).atMost(vsize).getValue();
        Events.assertField(event, "rssPeak").
                atLeast(rss).atMost(reasonableRSSHigh);
    }

    private static void verifyExpectedEventLinux(RecordedEvent event) throws Exception {
        verifyExpectedEventCommon(event);
        long vsize = event.getValue("vsize");
        long rss = event.getValue("rss");
        Events.assertField(event, "rssPeak").
                atLeast(rss).atMost(reasonableRSSHigh);
        Events.assertField(event, "rssAnon").
                atLeast(reasonableRSSLow / 8).atMost(reasonableRSSHigh).atMost(rss);
        Events.assertField(event, "rssFile").
                atLeast(0L).atMost(reasonableRSSHigh).atMost(rss);
        Events.assertField(event, "rssShm").
                atLeast(0L).atMost(reasonableRSSHigh).atMost(rss);
        Events.assertField(event, "swap").
                atLeast(0L).atMost(vsize);
        Events.assertField(event, "pagetable").
                atLeast(0L).atMost(vsize / 8);
    }

    private static void verifyExpectedEventMacOS(RecordedEvent event) throws Exception {
        verifyExpectedEventCommon(event);
    }

    private static void verifyExpectedEvents(List<RecordedEvent> events) throws Exception {
        List<RecordedEvent> filteredEvents = events.stream().filter(e -> e.getEventType().getName().equals(ProcessSizeEventName)).toList();
        assertGreaterThan(filteredEvents.size(), 0, "Should exist events of type: " + ProcessSizeEventName);
        for (RecordedEvent event : filteredEvents) {
            System.out.println(event);
            if (Platform.isLinux()) {
                verifyExpectedEventLinux(event);
            } else if (Platform.isOSX()) {
                verifyExpectedEventMacOS(event);
            } else {
                throw new RuntimeException("Unsupported");
            }
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
