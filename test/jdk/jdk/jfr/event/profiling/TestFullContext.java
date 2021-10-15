/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.profiling;

import static jdk.test.lib.Asserts.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jdk.jfr.Category;
import jdk.jfr.Context;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.RecordingContext;
import jdk.jfr.RecordingContextKey;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.RecordedContext;
import jdk.jfr.consumer.RecordedContextEntry;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.profiling.TestFullContext
 */
public class TestFullContext {

    private final static RecordingContextKey contextKey1 =
        RecordingContextKey.inheritableForName("Key1");
    private final static RecordingContextKey contextKey2 =
        RecordingContextKey.inheritableForName("Key2");

    @Name("TestEvent")
    @Label("TestEvent")
    @Description("Test Event")
    @Category("Test")
    @Context(true)
    @StackTrace(false)
    private static class TestEvent extends Event {
    }

    private static interface ThrowingPredicate<T, E extends Throwable> {
        boolean test(T t) throws E;
    }

    public static void main(String[] args) throws Throwable {
        runTestWithContexts(
            () ->
                RecordingContextHolder.of(
                    RecordingContext
                        .where(contextKey1, "Key1Value1")
                        .build()),
            events -> {
                events.forEach(System.out::println);
                assertEquals(events.size(), 1);
                assertContext(events.get(0), Map.of(
                    contextKey1.name(), "Key1Value1"));
                return true;
            }
        );

        runTestWithContexts(
            () ->
                RecordingContextHolder.of(
                    RecordingContext
                        .where(contextKey1, "Key1Value2")
                        .where(contextKey2, "Key2Value2")
                        .build()),
            events -> {
                events.forEach(System.out::println);
                assertEquals(events.size(), 1);
                assertContext(events.get(0), Map.of(
                    contextKey1.name(), "Key1Value2",
                    contextKey2.name(), "Key2Value2"));
                return true;
            }
        );

        runTestWithContexts(
            () ->
                RecordingContextHolder.of(
                    RecordingContext
                        .where(contextKey1, "Key1Value3/1")
                        .where(contextKey1, "Key1Value3/2")
                        .build()),
            events -> {
                events.forEach(System.out::println);
                assertEquals(events.size(), 1);
                assertContext(events.get(0), Map.of(
                    contextKey1.name(), "Key1Value3/2"));
                return true;
            }
        );

        runTestWithContexts(
            () ->
                RecordingContextHolder.of(
                    RecordingContext
                        .where(contextKey1, "Key1Value4")
                        .build(),
                    RecordingContext
                        .where(contextKey2, "Key2Value4")
                        .build()),
            events -> {
                events.forEach(System.out::println);
                assertEquals(events.size(), 1);
                assertContext(events.get(0), Map.of(
                    contextKey1.name(), "Key1Value4",
                    contextKey2.name(), "Key2Value4"));
                return true;
            }
        );

        runTestWithContexts(
            () ->
                RecordingContextHolder.of(
                    RecordingContext
                        .where(contextKey1, "Key1Value5/1")
                        .build(),
                    RecordingContext
                        .where(contextKey1, "Key1Value5/2")
                        .build()),
            events -> {
                events.forEach(System.out::println);
                assertEquals(events.size(), 1);
                assertContext(events.get(0), Map.of(
                    contextKey1.name(), "Key1Value5/2"));
                return true;
            }
        );
    }

    private static void runTestWithContexts(
            Supplier<RecordingContextHolder> contextsFactory,
            ThrowingPredicate<List<RecordedEvent>, Throwable> validation) throws Throwable {
        try (Recording recording = new Recording()) {
            recording.enable("TestEvent");
            recording.start();

            try (RecordingContextHolder contexts = contextsFactory.get()) {
                new TestEvent().commit();
            }

            recording.stop();

            if (!validation.test(fromRecording(recording))) {
                throw new RuntimeException("failed");
            }
        }
    }

    private static record RecordingContextHolder(List<RecordingContext> contexts) implements AutoCloseable {

        public static RecordingContextHolder of(RecordingContext... contexts) {
            return new RecordingContextHolder(List.of(contexts));
        }

        @Override
        public void close() {
            for (int i = contexts.size() - 1; i >= 0; i--) {
                contexts.get(i).close();
            }
        }
    }

    private static List<RecordedContextEntry> getContextEntries(RecordedEvent event) throws Throwable {
        RecordedContext context = event.getContext();
        if (context == null) throw new Exception("no context on " + event);
        List<RecordedContextEntry> entries = context.getEntries();
        if (entries.isEmpty()) throw new Exception("empty context on " + event);
        return entries;
    }

    private static RecordedContextEntry getFirstContextEntry(RecordedEvent event) throws Throwable {
        return getContextEntries(event).get(0);
    }

    private static List<RecordedEvent> fromRecording(Recording recording) throws Throwable {
        return RecordingFile.readAllEvents(makeCopy(recording));
    }

    private static Path makeCopy(Recording recording) throws Throwable {
        Path p = recording.getDestination();
        if (p == null) {
            File directory = new File(".");
            // FIXME: Must come up with a way to give human-readable name
            // this will at least not clash when running parallel.
            ProcessHandle h = ProcessHandle.current();
            p = new File(directory.getAbsolutePath(), "recording-" + recording.getId() + "-pid" + h.pid() + ".jfr").toPath();
            recording.dump(p);
        }
        return p;
    }

    private static void assertContext(RecordedEvent event, Map<String, String> expected) throws Throwable {
        expected = new HashMap<>(expected);
        for (RecordedContextEntry entry : getContextEntries(event)) {
            if (!expected.remove(entry.getName(), entry.getValue())) {
                fail(String.format("unexpected entry %s -> %s", entry.getName(), entry.getValue()));
            }
        }
        if (!expected.isEmpty()) {
            fail(String.format("expected entries %s",
                expected.entrySet().stream()
                    .map(e -> String.format("%s -> %s", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(", "))));
        }
    }
}
