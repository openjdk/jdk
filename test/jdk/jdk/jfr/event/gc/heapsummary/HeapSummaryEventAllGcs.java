/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.gc.heapsummary;

import java.time.Duration;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.GCHelper;

public class HeapSummaryEventAllGcs {

    public static void test(String expectedYoungCollector, String expectedOldCollector) throws Exception {
        Recording recording = new Recording();
        recording.enable(EventNames.GCConfiguration);
        recording.enable(EventNames.GCHeapSummary);
        recording.enable(EventNames.PSHeapSummary);
        recording.enable(EventNames.MetaspaceSummary).withThreshold(Duration.ofMillis(0));

        recording.start();
        // To eliminate the risk of being in the middle of a GC when the recording starts/stops,
        // we run 5 System.gc() and ignores the first and last GC.
        GCHelper.callSystemGc(5, true);
        recording.stop();

        List<RecordedEvent> allEvents = Events.fromRecording(recording);
        if (!checkCollectors(allEvents, expectedYoungCollector, expectedOldCollector)) {
            return;
        }
        List<RecordedEvent> events = GCHelper.removeFirstAndLastGC(allEvents);
        for (RecordedEvent event : events) {
            System.out.println("Event:" + event);
        }

        Asserts.assertFalse(events.isEmpty(), "Expected at least one event.");
        Asserts.assertEquals(events.size() % 2, 0, "Events should come in pairs");

        int lastHeapGcId = -1;
        int lastPSGcId = -1;
        int lastMetaspaceGcId = -1;

        for (RecordedEvent event : events) {
            final String eventName = event.getEventType().getName();
            switch (eventName) {
                case EventNames.GCHeapSummary:
                    lastHeapGcId = checkGcId(event, lastHeapGcId);
                    checkHeapEventContent(event);
                    break;
                case EventNames.PSHeapSummary:
                    lastPSGcId = checkGcId(event, lastPSGcId);
                    checkPSEventContent(event);
                    break;
                case EventNames.MetaspaceSummary:
                    lastMetaspaceGcId = checkGcId(event, lastMetaspaceGcId);
                    checkMetaspaceEventContent(event);
                    break;
                default:
                    System.out.println("Failed event: " + event);
                    Asserts.fail("Unknown event type: " + eventName);
            }
        }

        // Sanity check. Not complete.
        Asserts.assertEquals(lastHeapGcId, lastMetaspaceGcId, "Should have gotten perm gen events for all GCs");
    }

    private static void checkMetaspaceEventContent(RecordedEvent event) {
        long totalUsed = Events.assertField(event, "metaspace.used").atLeast(0L).getValue();
        long totalCommitted = Events.assertField(event, "metaspace.committed").atLeast(totalUsed).getValue();
        long totalReserved = Events.assertField(event, "metaspace.reserved").atLeast(totalCommitted).getValue();

        long dataUsed = Events.assertField(event, "dataSpace.used").atLeast(0L).getValue();
        long dataCommitted = Events.assertField(event, "dataSpace.committed").atLeast(dataUsed).getValue();
        long dataReserved = Events.assertField(event, "dataSpace.reserved").atLeast(dataCommitted).getValue();

        long classUsed = Events.assertField(event, "classSpace.used").atLeast(0L).getValue();
        long classCommitted = Events.assertField(event, "classSpace.committed").atLeast(classUsed).getValue();
        long classReserved = Events.assertField(event, "classSpace.reserved").atLeast(classCommitted).getValue();

        Asserts.assertEquals(dataCommitted + classCommitted, totalCommitted, "Wrong committed memory");
        Asserts.assertEquals(dataUsed + classUsed, totalUsed, "Wrong used memory");
        Asserts.assertEquals(dataReserved + classReserved, totalReserved, "Wrong reserved memory");
    }

    private static int checkGcId(RecordedEvent event, int currGcId) {
        int gcId = Events.assertField(event, "gcId").getValue();
        String when = Events.assertField(event, "when").notEmpty().getValue();
        if ("Before GC".equals(when)) {
            Asserts.assertGreaterThan(gcId, currGcId, "gcId should be increasing");
        } else {
            Asserts.assertEquals(gcId, currGcId, "After should have same gcId as last Before event");
        }
        return gcId;
    }

    private static void checkHeapEventContent(RecordedEvent event) {
        checkVirtualSpace(event, "heapSpace");
        long heapUsed = Events.assertField(event, "heapUsed").atLeast(0L).getValue();
        long start = Events.assertField(event, "heapSpace.start").atLeast(0L).getValue();
        long committedEnd = Events.assertField(event, "heapSpace.committedEnd").above(start).getValue();
        Asserts.assertLessThanOrEqual(heapUsed, committedEnd- start, "used can not exceed size");
    }

    private static void checkPSEventContent(RecordedEvent event) {
        checkVirtualSpace(event, "oldSpace");
        checkVirtualSpace(event, "youngSpace");
        checkSpace(event, "oldObjectSpace");
        checkSpace(event, "edenSpace");
        checkSpace(event, "fromSpace");
        checkSpace(event, "toSpace");

        checkPSYoungSizes(event);
        checkPSYoungStartEnd(event);
    }

    private static void checkPSYoungSizes(RecordedEvent event) {
        long youngSize = (long)Events.assertField(event, "youngSpace.committedEnd").getValue() -
                        (long)Events.assertField(event, "youngSpace.start").getValue();
        long edenSize = (long)Events.assertField(event, "edenSpace.end").getValue() -
                        (long)Events.assertField(event, "edenSpace.start").getValue();
        long fromSize = (long)Events.assertField(event, "fromSpace.end").getValue() -
                        (long)Events.assertField(event, "fromSpace.start").getValue();
        long toSize = (long)Events.assertField(event, "toSpace.end").getValue() -
                        (long)Events.assertField(event, "toSpace.start").getValue();
        Asserts.assertGreaterThanOrEqual(youngSize, edenSize + fromSize + toSize, "Young sizes don't match");
    }

    private static void checkPSYoungStartEnd(RecordedEvent event) {
        long oldEnd = Events.assertField(event, "oldSpace.reservedEnd").getValue();
        long youngStart = Events.assertField(event, "youngSpace.start").getValue();
        long youngEnd = Events.assertField(event, "youngSpace.committedEnd").getValue();
        long edenStart = Events.assertField(event, "edenSpace.start").getValue();
        long edenEnd = Events.assertField(event, "edenSpace.end").getValue();
        long fromStart = Events.assertField(event, "fromSpace.start").getValue();
        long fromEnd = Events.assertField(event, "fromSpace.end").getValue();
        long toStart = Events.assertField(event, "toSpace.start").getValue();
        long toEnd = Events.assertField(event, "toSpace.end").getValue();
        Asserts.assertEquals(oldEnd, youngStart, "Young should start where old ends");
        if (fromStart < toStart) {
            // [from][to][eden]
            Asserts.assertEquals(youngStart, fromStart, "From should be placed first in young");
            Asserts.assertLessThanOrEqual(fromEnd, toStart, "To should start after From");
            Asserts.assertLessThanOrEqual(toEnd, edenStart, "Eden should start after To");
        } else {
            // [to][from][eden]
            Asserts.assertEquals(youngStart, toStart, "To should be placed first in young");
            Asserts.assertLessThanOrEqual(toEnd, fromStart, "From should start after to");
            Asserts.assertLessThanOrEqual(fromEnd, edenStart, "Eden should start after From");
        }
        Asserts.assertEquals(edenEnd, youngEnd, "Eden should be last of young");
    }

    private static void checkVirtualSpace(RecordedEvent event, String structName) {
        long start = Events.assertField(event, structName + ".start").atLeast(0L).getValue();
        long committedEnd = Events.assertField(event, structName + ".committedEnd").above(start).getValue();
        Events.assertField(event, structName + ".reservedEnd").atLeast(committedEnd);
        long committedSize = Events.assertField(event, structName + ".committedSize").atLeast(0L).getValue();
        Events.assertField(event, structName + ".reservedSize").atLeast(committedSize);
    }

    private static void checkSpace(RecordedEvent event, String structName) {
        long start = Events.assertField(event, structName + ".start").atLeast(0L).getValue();
        long end = Events.assertField(event, structName + ".end").above(start).getValue();
        long used =  Events.assertField(event, structName + ".used").atLeast(0L).getValue();
        long size = Events.assertField(event, structName + ".size").atLeast(used).getValue();
        Asserts.assertEquals(size, end - start, "Size mismatch");
    }

    private static boolean checkCollectors(List<RecordedEvent> events, String expectedYoung, String expectedOld) throws Exception {
        for (RecordedEvent event : events) {
            if (Events.isEventType(event, EventNames.GCConfiguration)) {
                final String young = Events.assertField(event, "youngCollector").notEmpty().getValue();
                final String old = Events.assertField(event, "oldCollector").notEmpty().getValue();
                if (young.equals(expectedYoung) && old.equals(expectedOld)) {
                    return true;
                }
                // TODO: We treat wrong collector types as an error. Old test only warned. Not sure what is correct.
                Asserts.fail(String.format("Wrong collector types: got('%s','%s'), expected('%s','%s')",
                young, old, expectedYoung, expectedOld));
            }
        }
        Asserts.fail("Missing event type " + EventNames.GCConfiguration);
        return false;
    }
}
