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
package jdk.jfr.event.tracing;

import java.time.Duration;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.Event;
import jdk.jfr.StackTrace;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Basic test of the MethodTiming event.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.tracing.TestMethodTiming
 **/
public class TestMethodTiming {
    private static final String EVENT_NAME = "jdk.MethodTiming";

    @StackTrace(false)
    static class TimeMeasureEvent extends Event {
        public String id;
    }

    public static void main(String... args) throws Exception {
        testCount();
        testDuration();
    }

    private static void testDuration() throws Exception {
        try (Recording r = new Recording()) {
            String filter = TestMethodTiming.class.getName() + "::takeNap";
            r.enable(EVENT_NAME).with("period", "endChunk").with("filter", filter);
            r.start();

            TimeMeasureEvent maxEvent = new TimeMeasureEvent();
            maxEvent.id = "max";
            maxEvent.begin();
            takeNap();
            maxEvent.commit();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            for (var e : events) {
                System.out.println(e);
            }
            if (events.size() != 3) {
                throw new Exception("Expected three events: TimeMeasureEvent::id=max, TimeMeasureEventid=min and MethodTiming::method=takeNap()");
            }
            RecordedEvent max = findWitdId(events, "max");
            RecordedEvent min = findWitdId(events, "min");

            events.remove(min);
            events.remove(max);
            Duration minDuration = min.getDuration();
            Duration maxDuration = max.getDuration();
            RecordedEvent timingEvent = events.get(0);
            Duration d = timingEvent.getDuration("average");
            if (d.compareTo(min.getDuration()) < 0) {
                throw new Exception("Expected duration to be at least " + minDuration + ", was " + d);
            }
            if (d.compareTo(max.getDuration()) > 0) {
                throw new Exception("Expected duration to be at most " + maxDuration + ", was " + d);
            }
            RecordedMethod method = timingEvent.getValue("method");
            String methodName = method.getType().getName() + "::" + method.getName() + " " + method.getDescriptor();
            String expected = TestMethodTiming.class.getName() + "::takeNap ()V";
            if (!methodName.equals(expected)) {
                System.out.println(expected);
                throw new Exception("Expected method " + expected + " in event, but was " +methodName);
            }
            if (timingEvent.getLong("invocations") != 1) {
                throw new Exception("Expected one invocation");
            }
        }
    }

    private static RecordedEvent findWitdId(List<RecordedEvent> events, String id) throws Exception {
        for (RecordedEvent event : events) {
            if (event.hasField("id")) {
                if (event.getString("id").equals(id)) {
                    return event;
                }
            }
        }
        throw new Exception("Could not find event with ID " + id);
    }

    private static void takeNap() throws Exception {
        TimeMeasureEvent minEvent = new TimeMeasureEvent();
        minEvent.begin();
        minEvent.id = "min";
        Thread.sleep(10);
        minEvent.commit();
    }

    private static void testCount() throws Exception {
        long invocations = 100_000;
        try (Recording r = new Recording()) {
            zebra();
            String filter = TestMethodTiming.class.getName() + "::zebra";
            r.enable(EVENT_NAME).with("period", "endChunk").with("filter", filter);
            r.start();
            for (int i = 0; i < invocations; i++) {
                zebra();
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            Events.hasEvents(events);
            for (RecordedEvent event : events) {
                Events.assertField(event, "invocations").equal(invocations);
            }
        }
    }

    private static void zebra() {
    }
}
