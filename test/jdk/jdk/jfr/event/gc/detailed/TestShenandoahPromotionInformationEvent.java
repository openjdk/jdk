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

/**
 * @test
 * @requires vm.hasJFR & vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib /test/jdk
 * @run main/othervm -Xmx64m -XX:+UnlockExperimentalVMOptions -XX:ShenandoahRegionSize=1m -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -XX:ShenandoahGenerationalMinPIPUsage=100 -XX:ShenandoahImmediateThreshold=100 jdk.jfr.event.gc.detailed.TestShenandoahPromotionInformationEvent
 */

public class TestShenandoahPromotionInformationEvent {
    private final static String EVENT_NAME = EventNames.ShenandoahPromotionInformation;

    public static void main(String[] args) throws Exception {
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

            Events.assertField(event, "gcId").getValue();
            Events.assertField(event, "collectedOld").atLeast(0L).getValue();
            Events.assertField(event, "collectedPromoted").atLeast(0L).getValue();
            Events.assertField(event, "collectedYoung").atLeast(0L).getValue();
            Events.assertField(event, "regionsPromotedHumongous").atLeast(0L).getValue();
            Events.assertField(event, "humongousPromotedGarbage").atLeast(0L).getValue();
            Events.assertField(event, "humongousPromotedFree").atLeast(0L).getValue();
            Events.assertField(event, "regionsPromotedRegular").atLeast(0L).getValue();
            Events.assertField(event, "regularPromotedGarbage").atLeast(0L).getValue();
            Events.assertField(event, "regularPromotedFree").atLeast(0L).getValue();
        }
    }

    private static void allocate() {
        DummyObject[] dummys = new DummyObject[6000];

        Random r = new Random(0);
        long bytesToAllocate = 256 * 1024 * 1024;
        int currPos = 0;
        while (bytesToAllocate > 0) {
            int allocSize = 1000 + (r.nextInt(4000));
            bytesToAllocate -= allocSize;
            dummys[currPos] = new DummyObject(allocSize);
            currPos = (currPos + r.nextInt(20)) % dummys.length;
        }
        for (int c = 0; c < dummys.length; c++) {
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
