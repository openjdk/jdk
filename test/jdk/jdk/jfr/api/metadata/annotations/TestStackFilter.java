/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.metadata.annotations;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jdk.jfr.api.metadata.annotations.UnloadableClass;
import jdk.jfr.Event;
import jdk.jfr.AnnotationElement;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.events.StackFilter;
import jdk.jfr.Recording;
import jdk.jfr.Name;
import jdk.jfr.EventType;
import jdk.jfr.EventFactory;
import jdk.jfr.FlightRecorder;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @modules jdk.jfr/jdk.jfr.events
 * @library /test/lib /test/jdk
 * @run main/othervm -Xlog:jfr=warning jdk.jfr.api.metadata.annotations.TestStackFilter
 */
public class TestStackFilter {
    private static class Quux {
        private static void one() throws Exception {
            two();
        }
        private static void two() throws Exception {
            three();
        }

        private static void three() throws Exception {
            TestStackFilter.qux();
        }
    }
    private final static String PACKAGE = "jdk.jfr.api.metadata.annotations.TestStackFilter";
    private final static String M1 = PACKAGE + "::foo";
    private final static String M2 = PACKAGE + "::baz";
    private final static String C1 = PACKAGE + "$Quux";

    @StackFilter({ M1, M2 })
    @Name("MethodFilter")
    public static class MethodFilterEvent extends Event {
    }

    @StackFilter(C1)
    @Name("ClassFilter")
    public static class ClassFilterEvent extends Event {
    }

    @StackFilter({})
    @Name("Empty")
    public static class EmptyEvent extends Event {
    }

    @StackFilter(PACKAGE + "::testUnload")
    @Name("Unload")
    public static class UnloadEvent extends Event {
    }

    @StackFilter(PACKAGE + "::emitCommitter")
    @Name("Reuse")
    public static class ReuseEvent extends Event {
    }

    @StackFilter(PACKAGE + "::emitCommitter")
    @Name("Max")
    public static class ExceedMaxEvent extends Event {
    }

    public static void main(String[] args) throws Exception {
        testMethodFilter();
        testClassFilter();
        testUnload();
        testReuse();
        testExceedMax();
    }

    // Use more stack filters than there is capacity for
    private static void testExceedMax() throws Exception {
        try (Recording r = new Recording()) {
            r.start();
            // Maximum number of simultaneous event classes that can
            // use a filter is 4096. Additional filters will be ignored.
            var classes = new ArrayList<>();
            for (int i = 0; i < 4200; i++) {
                Class<ExceedMaxEvent> eventClass = UnloadableClass.load(ExceedMaxEvent.class);
                emitCommitter(eventClass);
                classes.add(eventClass);
            }
            List<RecordedEvent> events = Events.fromRecording(r);
            if (events.size() != 4200) {
                throw new Exception("Expected 4200 'Max' events");
            }
            int emitCommitterCount = 0;
            int textExceedMaxCount = 0;
            for (RecordedEvent event : events) {
                RecordedStackTrace s = event.getStackTrace();
                if (s == null) {
                    System.out.println(event);
                    throw new Exception("Expected stack trace for 'Max' event");
                }

                RecordedFrame f = s.getFrames().get(0);
                if (!f.isJavaFrame()) {
                    throw new Exception("Expected Java frame for 'Max' event");
                }
                String methodName = f.getMethod().getName();
                switch (methodName) {
                    case "emitCommitter":
                        emitCommitterCount++;
                        break;
                    case "testExceedMax":
                        textExceedMaxCount++;
                        break;
                    default:
                        System.out.println(event);
                        throw new Exception("Unexpected top frame " + methodName + " for 'Max' event");
                }
            }
            // Can't match exact because filters from previous tests may be in use
            // or because filters added by JDK events filters
            if (emitCommitterCount == 0) {
                throw new Exception("Expected at least some events with emitCommitter() as top frame, found " + emitCommitterCount);
            }
            if (textExceedMaxCount < 500) {
                throw new Exception("Expected at least 500 events with testExceedMax() as top frame, found " + textExceedMaxCount);
            }
        }
    }

    // Tests that event classes with @StackFilter that are unloaded
    // reuses the memory slot used to bookkeep things in native
    private static void testReuse() throws Exception {
        try (Recording r = new Recording()) {
            r.enable("Reuse");
            r.start();
            for (int i = 0; i < 48; i++) {
                Class<ReuseEvent> eventClass = UnloadableClass.load(ReuseEvent.class);
                emitCommitter(eventClass);
                if (i % 16 == 0) {
                    System.gc();
                    rotate();
                }
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            if (events.size() != 48) {
                throw new Exception("Expected 48 'Reuse' events");
            }
            for (RecordedEvent event : events) {
                assertTopFrame(event, "testReuse");
            }
        }

    }

    // This test registers a stack filter, emits an event with the filter
    // and unregisters it. While this is happening, another
    // filter is being used.
    private static void testUnload() throws Exception {
        try (Recording r = new Recording()) {
            r.start();
            Class<UnloadEvent> eventClass = UnloadableClass.load(UnloadEvent.class);
            emitCommitter(eventClass);
            EventType type = getType("Unload");
            if (type == null) {
                throw new Exception("Expected event type named 'Unload'");
            }
            eventClass = null;
            while (true) {
                System.gc();
                rotate();
                type = getType("Unload");
                if (type == null) {
                    return;
                }
                System.out.println("Unload class not unloaded. Retrying ...");
            }
        }
    }

    private static void testMethodFilter() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(MethodFilterEvent.class);
            r.start();
            foo();
            bar();
            empty();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            if (events.isEmpty()) {
                throw new Exception("Excected events");
            }

            RecordedEvent e1 = events.get(0);
            assertTopFrame(e1, "testMethodFilter");

            RecordedEvent e2 = events.get(1);
            assertTopFrame(e2, "bar");

            RecordedEvent e3 = events.get(2);
            assertTopFrame(e3, "empty");
        }
    }

    private static void testClassFilter() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(MethodFilterEvent.class);
            r.start();
            Quux.one();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            if (events.isEmpty()) {
                throw new Exception("Excected events");
            }

            RecordedEvent e = events.get(0);
            assertTopFrame(e, "qux");
            for (RecordedFrame f : e.getStackTrace().getFrames()) {
                if (f.getMethod().getType().getName().contains("Quux")) {
                    System.out.println(e);
                    throw new Exception("Didn't expect Quux class in stack trace");
                }
            }
        }
    }

    private static void empty() {
        EmptyEvent event = new EmptyEvent();
        event.commit();
    }

    static void foo() {
        baz();
    }

    static void bar() {
        baz();
    }

    static void baz() {
        MethodFilterEvent event = new MethodFilterEvent();
        event.commit();
    }

    static void qux() {
        ClassFilterEvent event = new ClassFilterEvent();
        event.commit();
    }

    private static void rotate() {
        try (Recording r = new Recording()) {
            r.start();
        }
    }

    private static EventType getType(String name) {
        for (EventType et : FlightRecorder.getFlightRecorder().getEventTypes()) {
            if (et.getName().equals(name)) {
                return et;
            }

        }
        return null;
    }

    private static void emitCommitter(Class<? extends Event> eventClass) throws Exception {
        Event event = eventClass.getConstructor().newInstance();
        event.commit();
    }

    private static void assertTopFrame(RecordedEvent event, String methodName) throws Exception {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            System.out.println(event);
            throw new Exception("No stack trace found when looking for top frame '" + methodName + "'");
        }
        RecordedFrame frame = stackTrace.getFrames().get(0);
        RecordedMethod method = frame.getMethod();
        if (!methodName.equals(method.getName())) {
            System.out.println(event);
            throw new Exception("Expected top frame '" + methodName + "'");
        }
    }
}
