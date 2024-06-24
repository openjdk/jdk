/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.event.gc.detailed;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.GCHelper;

/**
 * @test
 * @bug 8330577
 * @requires vm.hasJFR
 * @requires vm.gc == "G1" | vm.gc == null
 * @requires vm.debug
 * @key jfr
 * @library /test/lib /test/jdk
 * @summary Make sure that there are no Old->Old and Free->Free events sent.
 * @run main/othervm -XX:+G1GCAllocationFailureALot -XX:NewSize=2m -XX:MaxNewSize=2m -XX:MaxTenuringThreshold=1
 *                   -Xmx32m -XX:G1HeapRegionSize=1m -XX:+UseG1GC -Xlog:gc jdk.jfr.event.gc.detailed.TestG1InvalidHeapRegionTypeChangeEvent
 */

public class TestG1InvalidHeapRegionTypeChangeEvent {
    private final static String EVENT_NAME = EventNames.G1HeapRegionTypeChange;

    public static void main(String[] args) throws Exception {
        Recording recording = null;
        try {
            recording = new Recording();
            // activate the event we are interested in and start recording
            recording.enable(EVENT_NAME).withThreshold(Duration.ofMillis(0));
            recording.start();

            // Compact the heap, creating some Old regions. Previously this sent
            // Free->Free transitions too.
            System.gc();

            // Setting NewSize and MaxNewSize will limit eden, so
            // allocating 1024 20k byte arrays should trigger at
            // least a few Young GCs.
            // This fragments the heap a little (together with
            // G1GCAllocationFailureALot), so that the next Full GC
            // will generate Free -> Old transitions that were incorrectly
            // sent as Old -> Old.
            // Note that an Old -> Old transition is actually valid in case
            // of evacuation failure in an old region, but this is no
            // change of region and should not be sent either.

            byte[][] array = new byte[1024][];
            for (int i = 0; i < array.length; i++) {
                array[i] = new byte[20 * 1024];
            }

            System.gc();
            recording.stop();

            // Verify recording
            List<RecordedEvent> events = Events.fromRecording(recording);
            Asserts.assertFalse(events.isEmpty(), "No events found");

            for (RecordedEvent event : events) {
                Events.assertField(event, "index").notEqual(-1);
                Asserts.assertTrue(GCHelper.isValidG1HeapRegionType(Events.assertField(event, "from").getValue()));
                Asserts.assertTrue(GCHelper.isValidG1HeapRegionType(Events.assertField(event, "to").getValue()));
                Events.assertField(event, "used").atMost(1L*1024*1024);
                // There should be no Old->Old and Free->Free "changes".
                Asserts.assertFalse(Events.assertField(event, "from").getValue().equals("Old") && Events.assertField(event, "to").getValue().equals("Old"));
                Asserts.assertFalse(Events.assertField(event, "from").getValue().equals("Free") && Events.assertField(event, "to").getValue().equals("Free"));
            }
        } catch (Throwable t) {
            if (recording != null) {
                recording.dump(Paths.get("TestG1HeapRegionTypeChangeEvent.jfr"));
            }
            throw t;
        } finally {
            if (recording != null) {
                recording.close();
            }
        }
    }
}
