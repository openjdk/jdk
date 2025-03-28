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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.event.tracing.Apple;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that the union of annotation-based, class-based and
 *          method-based filters can be used simultaneously.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @build jdk.jfr.event.tracing.Apple
 * @run main/othervm -Xlog:jfr+methodtrace=trace
 *      jdk.jfr.event.tracing.TestCombinedFilters
 **/
public class TestCombinedFilters {
    private static final String APPLE_ANNOTATION = Apple.class.getName();
    private static final String TRACE_EVENT = "jdk.MethodTrace";
    private static final String TIMING_EVENT = "jdk.MethodTiming";
    private static final String FOO_CLASS = Foo.class.getName();

    public static class Foo {
        @Apple
        static void bar() {
            System.out.println("Executing Foo:bar");
        }

        static void baz() {
            System.out.println("Executing Foo:baz");
        }

        static void qux() {
            System.out.println("Executing Foo:qux");
        }
    }

    record TestEvent(String event, String type, String method) {
    }

    public static void main(String... args) throws Exception {
        String traceFilter = "@" + APPLE_ANNOTATION + ";" + FOO_CLASS + "::bar";
        String timingFilter = Foo.class.getName();
        try (Recording r = new Recording()) {
            r.enable(TRACE_EVENT).with("filter", traceFilter);
            r.enable(TIMING_EVENT).with("filter", timingFilter).with("period", "endChunk");
            for (var entry : r.getSettings().entrySet()) {
                System.out.println(entry.getKey() + "=" + entry.getValue());
            }
            r.start();
            Foo.bar();
            Foo.baz();
            Foo.qux();
            r.stop();
            var list = List.of(new TestEvent(TRACE_EVENT, FOO_CLASS, "bar"),
                               new TestEvent(TIMING_EVENT, FOO_CLASS, "<init>"),
                               new TestEvent(TIMING_EVENT, FOO_CLASS, "bar"),
                               new TestEvent(TIMING_EVENT, FOO_CLASS, "baz"),
                               new TestEvent(TIMING_EVENT, FOO_CLASS, "qux"));
            List<TestEvent> expected = new ArrayList<>(list);
            List<RecordedEvent> events = Events.fromRecording(r);
            for (RecordedEvent event : events) {
                System.out.println(event);
            }
            Events.hasEvents(events);
            for (RecordedEvent e : events) {
                RecordedMethod method = e.getValue("method");
                String className = method.getType().getName();
                String eventTypeName = e.getEventType().getName();
                TestEvent testEvent = new TestEvent(eventTypeName, className, method.getName());
                if (!expected.remove(testEvent)) {
                    throw new Exception("Unexpected event " + testEvent);
                }
            }
            if (!expected.isEmpty()) {
                throw new Exception("Missing events " + expected);
            }
        }
    }
}
