/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @requires (vm.gc == "ConcMarkSweep" | vm.gc == null) & !vm.graal.enabled
 *           & vm.opt.ExplicitGCInvokesConcurrent != false
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:-UseFastUnorderedTimeStamps -XX:+UseConcMarkSweepGC -XX:+ExplicitGCInvokesConcurrent jdk.jfr.event.gc.heapsummary.TestHeapSummaryEventConcurrentCMS
 */
public class TestHeapSummaryEventConcurrentCMS {

    public static void main(String[] args) throws Exception {
        Recording recording = new Recording();
        recording.enable(EventNames.GarbageCollection).withThreshold(Duration.ofMillis(0));
        recording.enable(EventNames.GCHeapSummary).withThreshold(Duration.ofMillis(0));

        recording.start();
        // Need several GCs to ensure at least one heap summary event from concurrent CMS
        GCHelper.callSystemGc(6, true);
        recording.stop();

        // Remove first and last GCs which can be incomplete
        List<RecordedEvent> events = GCHelper.removeFirstAndLastGC(Events.fromRecording(recording));
        Asserts.assertFalse(events.isEmpty(), "No events found");
        for (RecordedEvent event : events) {
            System.out.println("Event: " + event);
            if (!isCmsGcEvent(event)) {
                continue;
            }
            int gcId = Events.assertField(event, "gcId").getValue();
            verifyHeapSummary(events, gcId, "Before GC");
            verifyHeapSummary(events, gcId, "After GC");
        }
    }

    private static boolean isCmsGcEvent(RecordedEvent event) {
        if (!Events.isEventType(event, EventNames.GarbageCollection)) {
            return false;
        }
        final String gcName = Events.assertField(event, "name").notEmpty().getValue();
        return "ConcurrentMarkSweep".equals(gcName);
    }

    private static void verifyHeapSummary(List<RecordedEvent> events, int gcId, String when) {
        for (RecordedEvent event : events) {
            if (!Events.isEventType(event, EventNames.GCHeapSummary)) {
                continue;
            }
            if (gcId == (int)Events.assertField(event, "gcId").getValue() &&
                    when.equals(Events.assertField(event, "when").getValue())) {
                System.out.printf("Found " + EventNames.GCHeapSummary + " for id=%d, when=%s%n", gcId, when);
                return;
            }
        }
        Asserts.fail(String.format("No " + EventNames.GCHeapSummary + " for id=%d, when=%s", gcId, when));
    }

}
