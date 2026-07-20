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

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.EventNames;
/**
 * @test
 * @summary Tests that method tracing can be used with multiple recordings.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm
 *           -Xlog:jfr+methodtrace=debug
 *           jdk.jfr.event.tracing.TestMultipleRecordings
 **/
public class TestMultipleRecordings {
    private static final String METHOD_TRACE = "jdk.MethodTrace";
    private static final String METHOD_TIMING = "jdk.MethodTiming";
    private static final String CLASS_NAME = TestMultipleRecordings.class.getName();

    public static void main(String... args) throws Exception {
        testNestedMethodTrace();
        testNestedMethodTiming();
    }

    private static void testNestedMethodTiming() throws Exception {
        List<RecordedEvent> outerEvents = new ArrayList<>();
        List<RecordedEvent> innerEvents = new ArrayList<>();

        runNested(METHOD_TIMING, outerEvents, innerEvents);
        var outerBatches = groupByEndTime(outerEvents);
        System.out.println("Number of outer batches: " + outerBatches.size());
        // Outer started
        assertTimingBatch("outer: started", outerBatches.get(0), Map.of("foo", 0, "baz", 0));
        assertTimingBatch("outer: initial to bestarted", outerBatches.get(1), Map.of("foo", 1, "baz", 1));
        // Inner started
        assertTimingBatch("outer: inner started", outerBatches.get(2), Map.of("foo", 1, "baz", 1, "bar", 0));
        assertTimingBatch("outer: inner ended", outerBatches.get(3), Map.of("foo", 2, "baz", 2, "bar", 1));
        // Inner stopped
        assertTimingBatch("outer: only outer", outerBatches.get(4), Map.of("foo", 2, "baz", 2));
        assertTimingBatch("outer: ending", outerBatches.get(5), Map.of("foo", 3, "baz", 3));
        // Outer stopped

        var innerBatches = groupByEndTime(innerEvents);
        System.out.println("Number of inner batches: " + innerBatches.size());
        assertTimingBatch("inner: started", innerBatches.get(0), Map.of("foo", 1, "baz", 1, "bar", 0));
        assertTimingBatch("inner: ended", innerBatches.get(1), Map.of("foo", 2, "baz", 2, "bar", 1));
    }

    private static void assertTimingBatch(String batchName, List<RecordedEvent> events, Map<String, Integer> expected) throws Exception {
        Map<String, Integer> map = new HashMap<>();
        for (RecordedEvent e : events) {
            RecordedMethod m = e.getValue("method");
            String name = m.getName();
            int invocations = (int) e.getLong("invocations");
            map.put(name, invocations);
        }
        if (!map.equals(expected)) {
            printBatch("Expected:", expected);
            printBatch("Was:", map);
            throw new Exception("Batch '" + batchName + "' not as expected");
        }
    }

    private static void printBatch(String name, Map<String, Integer> batch) {
        System.out.println(name);
        for (var entry : batch.entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
    }

    private static List<List<RecordedEvent>> groupByEndTime(List<RecordedEvent> events) {
        var listList = new ArrayList<List<RecordedEvent>>();
        List<RecordedEvent> list = null;
        Instant last = null;
        while (!events.isEmpty()) {
            RecordedEvent event = removeEarliest(events);
            Instant timestamp = event.getEndTime();
            if (last == null || !timestamp.equals(last)) {
                list = new ArrayList<RecordedEvent>();
                listList.add(list);
            }
            list.add(event);
            last = event.getEndTime();
        }
        return listList;
    }

    private static RecordedEvent removeEarliest(List<RecordedEvent> events) {
        RecordedEvent earliest = null;
        for (RecordedEvent event : events) {
            if (earliest == null || event.getEndTime().isBefore(earliest.getEndTime())) {
                earliest = event;
            }
        }
        events.remove(earliest);
        return earliest;
    }

    private static void testNestedMethodTrace() throws Exception {
        List<RecordedEvent> outerEvents = new ArrayList<>();
        List<RecordedEvent> innerEvents = new ArrayList<>();

        runNested(METHOD_TRACE, outerEvents, innerEvents);

        assertMethodTraceEvents(outerEvents, "Outer", "foo", 3);
        assertMethodTraceEvents(outerEvents, "Outer", "bar", 1);
        assertMethodTraceEvents(outerEvents, "Outer", "baz", 3);
        assertMethodTraceEvents(innerEvents, "Inner", "foo", 1);
        assertMethodTraceEvents(innerEvents, "Inner", "bar", 1);
        assertMethodTraceEvents(innerEvents, "Inner", "baz", 1);
    }

    private static void runNested(String eventName, List<RecordedEvent> outerEvents, List<RecordedEvent> innerEvents)
            throws IOException {
        try (Recording outer = new Recording()) {
            outer.enable(eventName).with("filter",
               CLASS_NAME + "::foo;" +
               CLASS_NAME + "::baz");
            outer.start();
            foo();
            bar();
            baz();
            nap();
            try (Recording inner = new Recording()) {
                inner.enable(eventName).with("filter",
                    CLASS_NAME + "::foo;" +
                    CLASS_NAME + "::bar");
                inner.start();
                foo();
                bar();
                baz();
                inner.stop();
                innerEvents.addAll(Events.fromRecording(inner));
                nap();
            }
            foo();
            bar();
            baz();
            nap();
            outer.stop();
            outerEvents.addAll(Events.fromRecording(outer));
        }
    }

    // Ensure that periodic events at endChunk get a different
    // timestamp than periodic events at beginChunk
    private static void nap() {
        Instant time = Instant.now();
        do {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // ignore
            }
        } while (time.plus(Duration.ofMillis(10)).isAfter(Instant.now()));
    }

    private static void assertMethodTraceEvents(List<RecordedEvent> events, String context, String methodName, int expected) throws Exception {
        int actual = 0;
        for (RecordedEvent event : events) {
            RecordedMethod method = event.getValue("method");
            if (method.getName().equals(methodName)) {
                actual++;
            }
        }
        if (actual != expected) {
            System.out.println(context);
            for (RecordedEvent event : events) {
                System.out.println(event);
            }
            throw new Exception(context + ": expected " + expected + " events for method " + methodName + ", got actual " + actual);
        }
    }

    private static void foo() {
        System.out.println("Executing: foo()");
    }

    private static void bar() {
        System.out.println("Executing: bar()");
    }

    private static void baz() {
        System.out.println("Executing: baz()");
    }
}
