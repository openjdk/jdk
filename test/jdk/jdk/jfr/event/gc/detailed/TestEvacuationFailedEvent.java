/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

import jdk.test.whitebox.WhiteBox;

/**
 * @test
 * @key jfr
 * @bug 8263461
 * @requires vm.hasJFR
 *
 * @library /test/lib /test/jdk
 * @requires vm.gc == "G1" | vm.gc == null
 * @requires vm.debug
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *          -Xmx32m -Xms32m -XX:+UnlockExperimentalVMOptions -XX:+G1GCAllocationFailureALot
 *          -XX:G1GCAllocationFailureALotCount=100 -XX:G1GCAllocationFailureALotInterval=1
 *          -Xlog:gc=debug -XX:+UseG1GC jdk.jfr.event.gc.detailed.TestEvacuationFailedEvent
 */

public class TestEvacuationFailedEvent {

    private final static String EVENT_NAME = EventNames.EvacuationFailed;

    public static void main(String[] args) throws Exception {
        Recording recording = new Recording();
        // activate the event we are interested in and start recording
        recording.enable(EVENT_NAME);
        recording.start();

        Object[] data = new Object[1024];

        for (int i = 0; i < data.length; i++) {
            data[i] = new byte[5 * 1024];
        }
        // Guarantee one young gc.
        WhiteBox.getWhiteBox().youngGC();
        // Keep alive data.
        Reference.reachabilityFence(data);

        recording.stop();

        // Verify recording
        List<RecordedEvent> events = Events.fromRecording(recording);
        int minObjectAlignment = 8;

        Events.hasEvents(events);
        for (RecordedEvent event : events) {
            long objectCount = Events.assertField(event, "evacuationFailed.objectCount").atLeast(1L).getValue();
            long smallestSize = Events.assertField(event, "evacuationFailed.smallestSize").atLeast(1L).getValue();
            Asserts.assertTrue((smallestSize % minObjectAlignment) == 0, "smallestSize " + smallestSize + " is not a valid size.");
            long firstSize = Events.assertField(event, "evacuationFailed.firstSize").atLeast(smallestSize).getValue();
            Asserts.assertTrue((firstSize % minObjectAlignment) == 0, "firstSize " + firstSize + " is not a valid size.");
            long totalSize = Events.assertField(event, "evacuationFailed.totalSize").atLeast(firstSize).getValue();
            Asserts.assertTrue((totalSize % minObjectAlignment) == 0, "totalSize " + totalSize + " is not a valid size.");
            Asserts.assertLessThanOrEqual(smallestSize * objectCount, totalSize, "smallestSize * objectCount <= totalSize");
        }
        recording.close();
    }
}
