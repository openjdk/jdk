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

package jdk.jfr.event.allocation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that the VM maintains proper initialization state for ObjectAllocationSampleEvent.
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -XX:+UseTLAB -XX:TLABSize=2k -XX:-ResizeTLAB jdk.jfr.event.allocation.TestObjectAllocationSampleEventInitialWeight
 */
public class TestObjectAllocationSampleEventInitialWeight {
    private static final String EVENT_NAME = EventNames.ObjectAllocationSample;
    private static final int OBJECT_SIZE = 4 * 1024;
    private static final int OBJECTS_TO_ALLOCATE = 16;
    private static final int OBJECTS_TO_ALLOCATE_BEFORE_RECORDING = 1024;
    private static final long BEFORE_RECORDING_SAMPLE_WEIGHT = OBJECT_SIZE * OBJECTS_TO_ALLOCATE_BEFORE_RECORDING;

    // Make sure allocation isn't dead code eliminated.
    public static byte[] tmp;

    public static void main(String... args) throws Exception {
        test();
        // Test again to ensure reset logic works correctly for subsequent physical recordings.
        test();
    }

    private static void test() throws Exception {
        long currentThreadId = Thread.currentThread().threadId();
        allocate(OBJECTS_TO_ALLOCATE_BEFORE_RECORDING);
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME);
            r.start();
            allocate(OBJECTS_TO_ALLOCATE);
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            Events.hasEvents(events);
            for (RecordedEvent event : events) {
                if (currentThreadId == event.getThread().getJavaThreadId()) {
                    if (event.getLong("weight") >= BEFORE_RECORDING_SAMPLE_WEIGHT) {
                        throw new RuntimeException("Sample weight is not below " + BEFORE_RECORDING_SAMPLE_WEIGHT);
                    }
                }
            }
        }
    }

    private static void allocate(int number) throws Exception {
        for (int i = 0; i < number; ++i) {
            tmp = new byte[OBJECT_SIZE];
        }
    }
}
