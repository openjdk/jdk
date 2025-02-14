/*
 * Copyright Amazon.com Inc. All rights reserved.
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
 *
 */

package jdk.jfr.event.gc.detailed;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.GCHelper;

/**
 * @test
 * @bug 8221507
 * @requires vm.hasJFR & vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib /test/jdk
 * @run main/othervm -Xmx64m -XX:+UnlockExperimentalVMOptions -XX:ShenandoahRegionSize=1m -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational jdk.jfr.event.gc.detailed.TestShenandoahEvacuationInformationEvent
 */

public class TestShenandoahEvacuationInformationEvent {
    private final static String EVENT_NAME = EventNames.ShenandoahEvacuationInformation;

    public static void main(String[] args) throws Exception {
        final long shenandoahHeapRegionSize = 1024 * 1024;
        final long shenandoahMaxHeapRegionCount = 64;
        Recording recording = new Recording();
        recording.enable(EVENT_NAME).withThreshold(Duration.ofMillis(0));
        recording.start();
        allocate();
        recording.stop();

        List<RecordedEvent> events = Events.fromRecording(recording);
        Asserts.assertFalse(events.isEmpty(), "No events found");
        for (RecordedEvent event : events) {
            if (!Events.isEventType(event, EVENT_NAME)) {
                continue;
            }
            System.out.println("Event: " + event);

            long cSetRegions = Events.assertField(event, "cSetRegions").atLeast(0L).getValue();
            long setUsedAfter = Events.assertField(event, "cSetUsedAfter").atLeast(0L).getValue();
            long setUsedBefore = Events.assertField(event, "cSetUsedBefore").atLeast(setUsedAfter).getValue();
            long freeRegions = Events.assertField(event, "freeRegions").atLeast(0L).getValue();
            Events.assertField(event, "collectedOld").atLeast(0L).getValue();
            Events.assertField(event, "collectedYoung").atLeast(0L).getValue();

            Asserts.assertGreaterThanOrEqual(shenandoahMaxHeapRegionCount, freeRegions + cSetRegions, "numRegions >= freeRegions + cSetRegions");
            Asserts.assertGreaterThanOrEqual(shenandoahHeapRegionSize * cSetRegions, setUsedAfter, "ShenandoahHeapRegionSize * cSetRegions >= setUsedAfter");
            Asserts.assertGreaterThanOrEqual(shenandoahHeapRegionSize * cSetRegions, setUsedBefore, "ShenandoahHeapRegionSize * cSetRegions >= setUsedBefore");

            int gcId = Events.assertField(event, "gcId").getValue();
        }
    }

    /**
     * Allocate memory to trigger garbage collections.
     * We want the allocated objects to have different life time, because we want both "young" and "old" objects.
     * This is done by keeping the objects in an array and step the current index by a small random number in the loop.
     * The loop will continue until we have allocated a fixed number of bytes.
     */
    private static void allocate() {
        DummyObject[] dummys = new DummyObject[6000];

        Random r = new Random(0);
        long bytesToAllocate = 256 * 1024 * 1024;
        int currPos = 0;
        while (bytesToAllocate > 0) {
            int allocSize = 1000 + (r.nextInt(4000));
            bytesToAllocate -= allocSize;
            dummys[currPos] = new DummyObject(allocSize);

            // Skip a few positions to get different duration on the objects.
            currPos = (currPos + r.nextInt(20)) % dummys.length;
        }
        for (int c=0; c<dummys.length; c++) {
            dummys[c] = null;
        }
        System.gc();
    }

    public static class DummyObject {
        public byte[] payload;
        DummyObject(int size) {
            payload = new byte[size];
        }
    }
}
