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

package jdk.jfr.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jdk.jfr.Contextual;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.tool.TestPrintContextual
 */
public class TestPrintContextual {
    private static final int WINDOW_SIZE = 1_000_000;
    private static final String CONTEXT_MARKER = "Context: ";

    private static final class PrintedEvent {
        private final List<Map.Entry<String, String>> contextValues = new ArrayList<>();
        private final List<Map.Entry<String, String>> values = new ArrayList<>();
        private final String name;

        public PrintedEvent(String name) {
            this.name = name;
        }

        public void addValue(String key, String value) {
            values.add(new AbstractMap.SimpleEntry<>(key, value));
        }

        public void addContextValue(String key, String value) {
            contextValues.add(new AbstractMap.SimpleEntry<>(key, value));
        }

        public List<Map.Entry<String, String>> getContextValues() {
            return contextValues;
        }

        public String getContextValue(String key) {
            for (var entry : contextValues) {
                if (entry.getKey().equals(key)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }

    @Name("Span")
    static class SpanEvent extends Event {
        @Contextual
        String name;
        @Contextual
        long spanId;
    }

    @Name("Trace")
    static class TraceEvent extends Event {
        long traceId;
        @Contextual
        String name;
    }

    @Name("Filler")
    @StackTrace(false)
    static class FillerEvent extends Event {
    }


    public static void main(String[] args) throws Exception {
        testContextValues();
        testInterleaved();
        testDeepContext();
        testThreadedContext();
        testFiltered();
    }

    // Tests that context values are injected into non-contextual events
    private static void testContextValues() throws Exception {
        try (Recording r = new Recording()) {
            r.enable("Trace").withoutStackTrace();
            r.enable("Span").withoutStackTrace();
            r.enable("jdk.SystemGC").withoutStackTrace();
            r.start();

            SpanEvent span = new SpanEvent();
            span.name = "span";
            span.spanId = 4711;
            span.begin();
            System.gc();
            span.commit();

            TraceEvent trace = new TraceEvent();
            trace.name = "trace";
            trace.traceId = 17;
            trace.begin();
            System.gc();
            trace.commit();

            r.stop();

            List<PrintedEvent> events = dumpPrintedEvents(r, Path.of("context-values.jfr"));

            PrintedEvent e0 = events.get(0);
            assertName(e0, "jdk.SystemGC");
            assertContextValue(e0, "Span.name", "span");
            assertContextValue(e0, "Span.spanId", "4711");

            PrintedEvent e1 = events.get(1);
            assertName(e1, "Span");
            assertMissingContextValues(e1);

            PrintedEvent e2 = events.get(2);
            assertName(e2, "jdk.SystemGC");
            assertContextValue(e2, "Trace.name", "trace");
            assertMissingContextValue(e2, "Trace.traceId");

            PrintedEvent e3 = events.get(3);
            assertName(e3, "Trace");
            assertMissingContextValues(e3);
        }
    }

    // Tests that two contexts can interleave and injection still works
    private static void testInterleaved() throws Exception {
        try (Recording r = new Recording()) {
            r.enable("Trace").withoutStackTrace();
            r.enable("Span").withoutStackTrace();
            r.enable("jdk.SystemGC").withoutStackTrace();
            r.start();

            System.gc(); // Event 0
            SpanEvent span = new SpanEvent();
            span.name = "span";
            span.spanId = 56;
            span.begin();
            System.gc(); // Event 1
            TraceEvent trace = new TraceEvent();
            trace.name = "trace";
            trace.traceId = 58;
            trace.begin();
            System.gc(); // Event 2
            span.commit(); // Event 3
            System.gc(); // Event 4
            trace.commit(); // Event 5
            System.gc(); // Event 6

            r.stop();

            List<PrintedEvent> events = dumpPrintedEvents(r, Path.of("interleaved.jfr"));

            PrintedEvent e0 = events.get(0);
            assertName(e0, "jdk.SystemGC");
            assertMissingContextValues(e0);

            PrintedEvent e1 = events.get(1);
            assertName(e1, "jdk.SystemGC");
            assertContextValue(e1, "Span.name", "span");
            assertContextValue(e1, "Span.spanId", "56");
            assertMissingContextValue(e1, "trace.name");

            PrintedEvent e2 = events.get(2);
            assertName(e2, "jdk.SystemGC");
            assertContextValue(e2, "Span.name", "span");
            assertContextValue(e2, "Span.spanId", "56");
            assertContextValue(e2, "Trace.name", "trace");

            PrintedEvent e3 = events.get(3);
            assertName(e3, "Span");

            PrintedEvent e4 = events.get(4);
            assertName(e4, "jdk.SystemGC");
            assertMissingContextValue(e4, "Span.name");
            assertMissingContextValue(e4, "Span.spanId");
            assertContextValue(e4, "Trace.name", "trace");

            PrintedEvent e5 = events.get(5);
            assertName(e5, "Trace");

            PrintedEvent e6 = events.get(6);
            assertName(e6, "jdk.SystemGC");
            assertMissingContextValues(e6);
        }
    }

    // Tests hundred nested contexts in one event
    private static void testDeepContext() throws Exception {
        try (Recording r = new Recording()) {
            r.enable("Trace").withoutStackTrace();
            r.enable("Span").withoutStackTrace();
            r.enable("jdk.SystemGC").withoutStackTrace();
            r.start();
            TraceEvent trace = new TraceEvent();
            trace.name = "trace";
            trace.traceId = 58;
            trace.begin();
            span(99);
            trace.commit();
            r.stop();
            List<PrintedEvent> events = dumpPrintedEvents(r, Path.of("deep-context.jfr"));

            PrintedEvent e0 = events.get(0);
            assertName(e0, "jdk.SystemGC");
            int counter = 100;
            for (var e : e0.getContextValues()) {
                String key = e.getKey();
                String value = e.getValue();
                if (counter == 100) {
                    if (!key.equals("Trace.name")) {
                        throw new Exception("Expected trace context to be printed first, but name was " + key);
                    }
                    if ("name".equals(value)) {
                        throw new Exception("Expected trace context name to be 'trace', but was " + value);
                    }
                    counter--;
                    continue;
                }
                if (key.equals("Span.spanId")) {
                    if (!String.valueOf(counter).equals(value)) {
                        throw new Exception("Expected spanId to be " + counter + ", but was " + value);
                    }
                    counter--;
                    continue;
                }
                if (!key.equals("Span.name")) {
                    throw new Exception("Expected span context name, but was " + key);
                }
            }
        }
    }

    private static void span(int depth) {
        SpanEvent span = new SpanEvent();
        span.name = "span";
        span.spanId = depth;
        span.begin();
        if (depth == 0) {
            System.gc();
            return;
        }
        span(depth - 1);
        span.commit();
    }

    // Tests that context values are only inhjected into events in the same thread.
    private static void testThreadedContext() throws Exception {
        try (Recording r = new Recording()) {
            r.enable("Trace").withoutStackTrace();
            r.enable("jdk.SystemGC").withoutStackTrace();
            r.start();
            TraceEvent trace = new TraceEvent();
            trace.name = "trace";
            trace.traceId = 42;
            trace.begin();
            Thread t = Thread.ofPlatform().name("not-main").start(() -> {
                System.gc();
            });
            t.join();
            System.gc();
            trace.commit();
            r.stop();

            List<PrintedEvent> events = dumpPrintedEvents(r, Path.of("threaded-context.jfr"));

            PrintedEvent e0 = events.get(0);
            assertName(e0, "jdk.SystemGC");
            assertMissingContextValues(e0);

            PrintedEvent e1 = events.get(1);
            assertName(e1, "jdk.SystemGC");
            assertContextValue(e1, "Trace.name", "trace");

            PrintedEvent e2 = events.get(2);
            assertName(e2, "Trace");
            assertMissingContextValues(e2);
        }
    }

    // Tests that context values are injected when context events are filtered out
    private static void testFiltered() throws Exception {
        try (Recording r = new Recording()) {
            r.enable("Trace").withoutStackTrace();
            r.enable("jdk.SystemGC").withoutStackTrace();
            r.start();

            TraceEvent trace = new TraceEvent();
            trace.name = "trace";
            trace.traceId = 22;
            trace.begin();
            SpanEvent span = new SpanEvent();
            span.spanId = 11;
            span.name = "span";
            span.begin();

            System.gc();

            span.commit();
            trace.commit();

            r.stop();
            Path file = Path.of("filtered.jfr");
            r.dump(file);
            List<PrintedEvent> events = parseEvents(readPrintedLines(file, "--events", "jdk.SystemGC"));
            if (events.size() != 1) {
                throw new Exception("Only expected one event");
            }
            PrintedEvent e0 = events.get(0);
            assertName(e0, "jdk.SystemGC");
            assertContextValue(e0, "Trace.name", "trace");
            assertContextValue(e0, "Span.name", "span");
            assertContextValue(e0, "Span.spanId", "11");
        }
    }

    private static void assertName(PrintedEvent event, String name) throws Exception {
        if (!event.name.equals(name)) {
            throw new Exception("Expected event name " + name + ", but was " + event.name);
        }
    }

    private static void assertContextValue(PrintedEvent event, String field, String expectedValue) throws Exception {
        String value = event.getContextValue(field);
        if (value == null) {
            throw new Exception("No value found for field " + field + " in event " + event.name);
        }
        if (!expectedValue.equals(value)) {
            throw new Exception("Expected context value " + expectedValue + " for " + field + ", it was " + value);
        }
    }

    private static void assertMissingContextValue(PrintedEvent event, String field) throws Exception {
        if (event.getContextValue(field) != null) {
            throw new Exception("Didn't expect to find context field " + field);
        }
    }

    private static void assertMissingContextValues(PrintedEvent event) throws Exception {
        if (!event.contextValues.isEmpty()) {
            throw new Exception("Didn't expect context values in event " + event.name);
        }
    }

    private static List<PrintedEvent> dumpPrintedEvents(Recording r, Path file) throws Exception {
        r.dump(file);
        return parseEvents(readPrintedLines(file));
    }

    private static List<PrintedEvent> parseEvents(List<String> lines) {
        List<PrintedEvent> events = new ArrayList<>();
        PrintedEvent pe = null;
        for (String line : lines) {
            if (line.endsWith("{")) {
                String[] texts = line.split(" ");
                pe = new PrintedEvent(texts[0]);
                events.add(pe);
            } else if (line.startsWith("}")) {
                pe = null;
            } else if (pe != null) {
                int index = line.indexOf("=");
                String field = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (field.startsWith(CONTEXT_MARKER)) {
                    pe.addContextValue(field.substring(CONTEXT_MARKER.length()), value);
                } else {
                    pe.addValue(field, value);
                }
            }
        }
        return events;
    }

    private static List<String> readPrintedLines(Path file, String... options) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jfr");
        launcher.addToolArg("print");
        for (String option : options) {
            launcher.addToolArg(option);
        }
        launcher.addToolArg(file.toAbsolutePath().toString());
        OutputAnalyzer output = ProcessTools.executeCommand(launcher.getCommand());
        return output.asLines();
    }
}
